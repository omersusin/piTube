package com.omersusin.pitube.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omersusin.pitube.utils.ThumbnailUrlResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.subscriptionsDataStore: DataStore<Preferences> by safePreferencesDataStore(name = "subscriptions")

/**
 * Local subscription list, isolated per profile.
 *
 * Every key is namespaced by the *active* profile's id
 * (`<profileId>|<channelId>`, `<profileId>|order`), so each stored YouTube
 * account (and the signed-out local profile) has its own set of subscriptions,
 * and switching accounts does not leak one account's channels into another's.
 * The profile id is resolved from [ProfileManager.activeProfileId] on every
 * call, which is the single source of truth the rest of the app already
 * observes - a switch repoints these reads automatically.
 *
 * The read flows combine the underlying DataStore with [ProfileManager.activeProfileId],
 * so a profile switch re-emits immediately and the UI's subscription screen
 * refreshes without any per-switch cache work of its own.
 */
class SubscriptionRepository private constructor(
    private val context: Context,
) {
    private val profileManager = ProfileManager(context)

    companion object {
        @Volatile
        private var instance: SubscriptionRepository? = null

        fun getInstance(context: Context): SubscriptionRepository =
            instance ?: synchronized(this) {
                instance ?: SubscriptionRepository(context.applicationContext).also { instance = it }
            }

        private const val LEGACY_ORDER_KEY = "subscriptions_order"
        private const val LEGACY_CHANNEL_PREFIX = "channel_"
        private const val MIGRATED_KEY = "subscriptions_scoped_v1"

        private fun channelKey(profileId: String, channelId: String) =
            stringPreferencesKey("$profileId|channel_$channelId")

        private fun orderKey(profileId: String) = stringPreferencesKey("$profileId|order")
    }

    /**
     * One-time migration of the pre-profile install's global subscription rows
     * into the current (single) active profile's namespace.
     *
     * Runs once, guarded by a flag in the same DataStore. Idempotent and safe
     * to call repeatedly from startup; after this the store only ever holds
     * namespaced keys (row reads are resolved by the active profile id, so
     * legacy keys could otherwise never be addressed again).
     */
    suspend fun ensureScopeMigration() {
        context.subscriptionsDataStore.edit { preferences ->
            if (preferences[booleanPreferencesKey(MIGRATED_KEY)] == true) return@edit
            val profileId = profileManager.activeProfileId.value
            if (profileId.isBlank()) return@edit

            // Legacy keys were flat: `channel_<id>` and `subscriptions_order`.
            // Copy any that still exist into the active profile's namespace,
            // then drop them so only scoped keys remain.
            val legacyOrder = preferences[stringPreferencesKey(LEGACY_ORDER_KEY)]
            if (!legacyOrder.isNullOrEmpty()) {
                preferences[orderKey(profileId)] = legacyOrder
                preferences.remove(stringPreferencesKey(LEGACY_ORDER_KEY))
            }
            val legacyKeys = preferences.asMap().keys.mapNotNull { key ->
                key.name.takeIf { it.startsWith(LEGACY_CHANNEL_PREFIX) }
            }
            legacyKeys.forEach { legacyKey ->
                val channelId = legacyKey.removePrefix(LEGACY_CHANNEL_PREFIX)
                val channelData = preferences[stringPreferencesKey(legacyKey)]
                if (!channelData.isNullOrEmpty()) {
                    preferences[channelKey(profileId, channelId)] = channelData
                }
                preferences.remove(stringPreferencesKey(legacyKey))
            }
            preferences[booleanPreferencesKey(MIGRATED_KEY)] = true
        }
    }

    /**
     * Copy subscriptions from a previous profile into the currently active
     * one. Used when the user signs into YouTube for the first time: the
     * device's subscriptions live under the local profile's namespace, and
     * without a copy every followed channel would flip back to "Subscribe"
     * the moment the fresh YouTube profile becomes active.
     *
     * Rows that already exist in the target are never overwritten; the source
     * rows are left in place. Idempotent and safe to call repeatedly.
     */
    suspend fun migrateSubscriptionsFromProfile(sourceProfileId: String) {
        if (sourceProfileId.isBlank()) return
        context.subscriptionsDataStore.edit { preferences ->
            val targetProfileId = profileManager.activeProfileId.value
            if (targetProfileId.isBlank() || targetProfileId == sourceProfileId) return@edit

            val prefix = "$sourceProfileId|"
            val sourceRows = preferences.asMap().entries.mapNotNull { (key, value) ->
                val name = key.name
                if (!name.startsWith(prefix)) return@mapNotNull null
                val channelId = name.removePrefix(prefix).takeIf { it.startsWith("channel_") }
                    ?.removePrefix("channel_")
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val data = value as? String ?: return@mapNotNull null
                if (data.isBlank()) null else channelId to data
            }
            if (sourceRows.isEmpty()) return@edit

            val migratedIds = mutableSetOf<String>()
            sourceRows.forEach { (channelId, data) ->
                val targetKey = channelKey(targetProfileId, channelId)
                if (preferences[targetKey] == null) {
                    preferences[targetKey] = data
                    migratedIds += channelId
                }
            }
            if (migratedIds.isEmpty()) return@edit

            val sourceOrder = preferences[orderKey(sourceProfileId)]
                .orEmpty().split(",").filter { it.isNotEmpty() }
            val targetOrder = preferences[orderKey(targetProfileId)]
                .orEmpty().split(",").filter { it.isNotEmpty() }
            val mergedOrder = (sourceOrder.filter { it in migratedIds } + targetOrder).distinct()
            preferences[orderKey(targetProfileId)] = mergedOrder.joinToString(",")
        }
    }

    /**
     * Subscribe to a channel
     */
    suspend fun subscribe(channel: ChannelSubscription) {
        context.subscriptionsDataStore.edit { preferences ->
            val profileId = profileManager.activeProfileId.value
            if (profileId.isBlank()) return@edit
            val safeChannel = channel.withPreservedThumbnail(preferences, profileId)

            // Save channel data
            preferences[channelKey(profileId, safeChannel.channelId)] = serializeChannel(safeChannel)

            // Update order list
            val currentOrder = preferences[orderKey(profileId)] ?: ""
            val orderList =
                if (currentOrder.isEmpty()) {
                    mutableListOf()
                } else {
                    currentOrder.split(",").toMutableList()
                }

            if (!orderList.contains(safeChannel.channelId)) {
                orderList.add(0, safeChannel.channelId)
                preferences[orderKey(profileId)] = orderList.joinToString(",")
            }
        }
    }

    suspend fun subscribeAll(channels: Collection<ChannelSubscription>) {
        if (channels.isEmpty()) return

        context.subscriptionsDataStore.edit { preferences ->
            val profileId = profileManager.activeProfileId.value
            if (profileId.isBlank()) return@edit
            val currentOrder =
                preferences[orderKey(profileId)]
                    .orEmpty()
                    .split(",")
                    .filter { it.isNotEmpty() }
            val knownIds = currentOrder.toMutableSet()
            val newIds = mutableListOf<String>()

            channels.forEach { channel ->
                val safeChannel = channel.withPreservedThumbnail(preferences, profileId)
                preferences[channelKey(profileId, safeChannel.channelId)] = serializeChannel(safeChannel)
                if (knownIds.add(safeChannel.channelId)) {
                    newIds += safeChannel.channelId
                }
            }

            if (newIds.isNotEmpty()) {
                preferences[orderKey(profileId)] =
                    (newIds.asReversed() + currentOrder).joinToString(",")
            }
        }
    }

    /**
     * Unsubscribe from a channel
     */
    suspend fun unsubscribe(channelId: String) {
        context.subscriptionsDataStore.edit { preferences ->
            val profileId = profileManager.activeProfileId.value
            if (profileId.isBlank()) return@edit
            preferences.remove(channelKey(profileId, channelId))

            // Update order list
            val currentOrder = preferences[orderKey(profileId)] ?: ""
            if (currentOrder.isNotEmpty()) {
                val orderList = currentOrder.split(",").toMutableList()
                orderList.remove(channelId)
                preferences[orderKey(profileId)] = orderList.joinToString(",")
            }
        }
    }

    /**
     * Check if subscribed to a channel
     */
    fun isSubscribed(channelId: String): Flow<Boolean> =
        combine(profileManager.activeProfileId, context.subscriptionsDataStore.data) { profileId, preferences ->
            profileId.isNotBlank() && preferences.contains(channelKey(profileId, channelId))
        }

    /**
     * Get all subscriptions
     */
    fun getAllSubscriptions(): Flow<List<ChannelSubscription>> =
        combine(profileManager.activeProfileId, context.subscriptionsDataStore.data) { profileId, preferences ->
            val orderString = preferences[orderKey(profileId)] ?: ""
            if (orderString.isEmpty()) {
                emptyList()
            } else {
                val orderList = orderString.split(",")
                orderList.mapNotNull { channelId ->
                    val channelData = preferences[channelKey(profileId, channelId)]
                    channelData?.let { deserializeChannel(it) }
                }
            }
        }

    /**
     * Get all subscription IDs as a Set
     */
    suspend fun getAllSubscriptionIds(): Set<String> {
        val profileId = profileManager.activeProfileId.value
        if (profileId.isBlank()) return emptySet()
        val orderString =
            context.subscriptionsDataStore.data
                .map { preferences ->
                    preferences[orderKey(profileId)] ?: ""
                }.first()

        return if (orderString.isEmpty()) {
            emptySet()
        } else {
            orderString.split(",").toSet()
        }
    }

    /**
     * Get subscription by channel ID
     */
    fun getSubscription(channelId: String): Flow<ChannelSubscription?> =
        combine(profileManager.activeProfileId, context.subscriptionsDataStore.data) { profileId, preferences ->
            val channelData = preferences[channelKey(profileId, channelId)]
            channelData?.let { deserializeChannel(it) }
        }

    suspend fun repairVideoThumbnailSubscriptions(fetchChannelThumbnail: suspend (String) -> String): Int {
        val subscriptions = getAllSubscriptions().first()
        val repairs =
            subscriptions
                .filter { ThumbnailUrlResolver.isYoutubeVideoThumbnail(it.channelThumbnail) }
                .mapNotNull { subscription ->
                    val avatar = fetchChannelThumbnail(subscription.channelId).trim()
                    if (avatar.isNotEmpty() && !ThumbnailUrlResolver.isYoutubeVideoThumbnail(avatar)) {
                        subscription.channelId to subscription.copy(channelThumbnail = avatar)
                    } else {
                        null
                    }
                }.toMap()

        if (repairs.isEmpty()) return 0

        context.subscriptionsDataStore.edit { preferences ->
            val profileId = profileManager.activeProfileId.value
            if (profileId.isBlank()) return@edit
            repairs.forEach { (channelId, subscription) ->
                preferences[channelKey(profileId, channelId)] = serializeChannel(subscription)
            }
        }
        return repairs.size
    }

    private fun serializeChannel(channel: ChannelSubscription): String =
        "${channel.channelId}|${channel.channelName}|${channel.channelThumbnail}|${channel.subscribedAt}|${channel.lastVideoId ?: ""}|${channel.lastCheckTime}|${channel.isNotificationEnabled}|${channel.isMusic}|${channel.lastFeedFetchAt}"

    private fun ChannelSubscription.withPreservedThumbnail(preferences: Preferences, profileId: String): ChannelSubscription {
        val existing = preferences[channelKey(profileId, channelId)]?.let { deserializeChannel(it) }
        return if (
            ThumbnailUrlResolver.isYoutubeVideoThumbnail(channelThumbnail) &&
            existing?.channelThumbnail?.isNotBlank() == true &&
            !ThumbnailUrlResolver.isYoutubeVideoThumbnail(existing.channelThumbnail)
        ) {
            copy(channelThumbnail = existing.channelThumbnail)
        } else {
            this
        }
    }

    private fun deserializeChannel(data: String): ChannelSubscription? =
        try {
            val parts = data.split("|")
            if (parts.size >= 4) {
                ChannelSubscription(
                    channelId = parts[0],
                    channelName = parts[1],
                    channelThumbnail = parts[2],
                    subscribedAt = parts[3].toLong(),
                    lastVideoId = if (parts.size > 4 && parts[4].isNotEmpty()) parts[4] else null,
                    lastCheckTime = if (parts.size > 5 && parts[5].isNotEmpty()) parts[5].toLong() else 0L,
                    isNotificationEnabled = if (parts.size > 6 && parts[6].isNotEmpty()) parts[6].toBoolean() else false,
                    isMusic = if (parts.size > 7 && parts[7].isNotEmpty()) parts[7].toBoolean() else false,
                    lastFeedFetchAt = if (parts.size > 8 && parts[8].isNotEmpty()) parts[8].toLong() else 0L,
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

    /**
     * Update the notification state for a channel
     */
    suspend fun updateNotificationState(
        channelId: String,
        enabled: Boolean,
    ) {
        context.subscriptionsDataStore.edit { preferences ->
            val profileId = profileManager.activeProfileId.value
            if (profileId.isBlank()) return@edit
            val channelData = preferences[channelKey(profileId, channelId)]
            if (channelData != null) {
                val subscription = deserializeChannel(channelData)
                if (subscription != null) {
                    val updated = subscription.copy(isNotificationEnabled = enabled)
                    preferences[channelKey(profileId, channelId)] = serializeChannel(updated)
                }
            }
        }
    }

    /**
     * Record that the subscription feed has just fetched these channels.
     *
     * Written in one [DataStore] transaction so a refresh over hundreds of channels does not
     * produce hundreds of preference commits.
     */
    suspend fun markFeedFetched(
        channelIds: Collection<String>,
        fetchedAt: Long,
    ) {
        if (channelIds.isEmpty()) return

        context.subscriptionsDataStore.edit { preferences ->
            val profileId = profileManager.activeProfileId.value
            if (profileId.isBlank()) return@edit
            channelIds.forEach { channelId ->
                val subscription = preferences[channelKey(profileId, channelId)]?.let { deserializeChannel(it) }
                if (subscription != null) {
                    preferences[channelKey(profileId, channelId)] =
                        serializeChannel(subscription.copy(lastFeedFetchAt = fetchedAt))
                }
            }
        }
    }

    /**
     * Update the last seen video for a channel
     */
    suspend fun updateChannelLatestVideo(
        channelId: String,
        videoId: String,
    ) {
        context.subscriptionsDataStore.edit { preferences ->
            val profileId = profileManager.activeProfileId.value
            if (profileId.isBlank()) return@edit
            preferences[channelKey(profileId, channelId)]?.let { channelData ->
                val subscription = deserializeChannel(channelData) ?: return@let
                val updated =
                    subscription.copy(
                        lastVideoId = videoId,
                        lastCheckTime = System.currentTimeMillis(),
                    )
                preferences[channelKey(profileId, channelId)] = serializeChannel(updated)
            }
        }
    }
}

data class ChannelSubscription(
    val channelId: String,
    val channelName: String,
    val channelThumbnail: String,
    val subscribedAt: Long = System.currentTimeMillis(),
    val lastVideoId: String? = null,
    /** When the background new-upload check last saw a *new* video for this channel. */
    val lastCheckTime: Long = 0L,
    val isNotificationEnabled: Boolean = false,
    val isMusic: Boolean = false,
    /** When the subscription feed last fetched this channel; 0 means never. */
    val lastFeedFetchAt: Long = 0L,
)