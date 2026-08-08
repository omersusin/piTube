package com.omersusin.pitube.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LocalSubscription(
    val channelId: String,
    val name: String,
    val avatarUrl: String? = null,
    val handle: String? = null,
    val subscribedAt: Long = System.currentTimeMillis()
)

data class SubscriptionGroup(
    val id: String,
    val name: String,
    val channelIds: List<String> = emptyList()
)

class LocalSubscriptionsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        synchronized(LOCK) {
            if (sharedSubscriptions == null) {
                sharedSubscriptions = MutableStateFlow(loadSubscriptions())
                sharedGroups = MutableStateFlow(loadGroups())
            }
        }
    }

    private val subscriptionsState: MutableStateFlow<List<LocalSubscription>>
        get() = sharedSubscriptions!!

    private val groupsState: MutableStateFlow<List<SubscriptionGroup>>
        get() = sharedGroups!!

    val subscriptions: StateFlow<List<LocalSubscription>> get() = subscriptionsState.asStateFlow()
    val groups: StateFlow<List<SubscriptionGroup>> get() = groupsState.asStateFlow()

    fun getAll(): List<LocalSubscription> = subscriptionsState.value

    fun getAllSortedByName(): List<LocalSubscription> =
        subscriptionsState.value.sortedBy { it.name.lowercase() }

    fun isSubscribed(channelId: String?): Boolean {
        if (channelId.isNullOrBlank()) return false
        return subscriptionsState.value.any { it.channelId == channelId }
    }

    fun get(channelId: String): LocalSubscription? =
        subscriptionsState.value.firstOrNull { it.channelId == channelId }

    fun getGroup(groupId: String): SubscriptionGroup? =
        groupsState.value.firstOrNull { it.id == groupId }

    fun channelsInGroup(groupId: String?): List<LocalSubscription> {
        val group = groupId?.let { getGroup(it) } ?: return getAll()
        val ids = group.channelIds.toSet()
        return getAll().filter { it.channelId in ids }
    }

    fun subscribe(subscription: LocalSubscription) {
        if (subscription.channelId.isBlank()) return
        val current = subscriptionsState.value
        val existing = current.firstOrNull { it.channelId == subscription.channelId }
        val next = if (existing == null) {
            listOf(subscription) + current
        } else {
            current.map {
                if (it.channelId == subscription.channelId) {
                    it.copy(
                        name = subscription.name.takeIf { n -> n.isNotBlank() } ?: it.name,
                        avatarUrl = subscription.avatarUrl ?: it.avatarUrl,
                        handle = subscription.handle ?: it.handle
                    )
                } else it
            }
        }
        saveSubscriptions(next)
    }

    fun unsubscribe(channelId: String) {
        val next = subscriptionsState.value.filterNot { it.channelId == channelId }
        if (next.size == subscriptionsState.value.size) return
        saveSubscriptions(next)
        val prunedGroups = groupsState.value.map { group ->
            if (channelId in group.channelIds) {
                group.copy(channelIds = group.channelIds - channelId)
            } else group
        }
        if (prunedGroups != groupsState.value) saveGroups(prunedGroups)
    }

    fun toggle(subscription: LocalSubscription): Boolean {
        return if (isSubscribed(subscription.channelId)) {
            unsubscribe(subscription.channelId)
            false
        } else {
            subscribe(subscription)
            true
        }
    }

    fun importAll(channels: List<LocalSubscription>): Int {
        if (channels.isEmpty()) return 0
        val current = subscriptionsState.value
        val known = current.map { it.channelId }.toMutableSet()
        val additions = mutableListOf<LocalSubscription>()
        for (channel in channels) {
            if (channel.channelId.isBlank()) continue
            if (known.add(channel.channelId)) additions.add(channel)
        }
        if (additions.isEmpty()) return 0
        saveSubscriptions(additions + current)
        return additions.size
    }

    fun clearAll() {
        saveSubscriptions(emptyList())
        saveGroups(emptyList())
    }

    fun createGroup(name: String, channelIds: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        saveGroups(groupsState.value + SubscriptionGroup(id, name.trim(), channelIds))
        return id
    }

    fun renameGroup(groupId: String, name: String) {
        saveGroups(groupsState.value.map {
            if (it.id == groupId) it.copy(name = name.trim()) else it
        })
    }

    fun deleteGroup(groupId: String) {
        saveGroups(groupsState.value.filterNot { it.id == groupId })
    }

    fun setGroupChannels(groupId: String, channelIds: List<String>) {
        saveGroups(groupsState.value.map {
            if (it.id == groupId) it.copy(channelIds = channelIds.distinct()) else it
        })
    }

    fun toggleChannelInGroup(groupId: String, channelId: String) {
        saveGroups(groupsState.value.map { group ->
            if (group.id != groupId) group
            else if (channelId in group.channelIds) group.copy(channelIds = group.channelIds - channelId)
            else group.copy(channelIds = group.channelIds + channelId)
        })
    }

    private fun saveSubscriptions(list: List<LocalSubscription>) {
        subscriptionsState.value = list
        val array = JSONArray()
        list.forEach { sub ->
            array.put(JSONObject().apply {
                put("channelId", sub.channelId)
                put("name", sub.name)
                put("avatarUrl", sub.avatarUrl ?: JSONObject.NULL)
                put("handle", sub.handle ?: JSONObject.NULL)
                put("subscribedAt", sub.subscribedAt)
            })
        }
        prefs.edit().putString(KEY_SUBSCRIPTIONS, array.toString()).apply()
    }

    private fun loadSubscriptions(): List<LocalSubscription> {
        val raw = prefs.getString(KEY_SUBSCRIPTIONS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val channelId = obj.optString("channelId").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                LocalSubscription(
                    channelId = channelId,
                    name = obj.optString("name").takeIf { it.isNotBlank() } ?: channelId,
                    avatarUrl = obj.optString("avatarUrl").takeIf { it.isNotBlank() && it != "null" },
                    handle = obj.optString("handle").takeIf { it.isNotBlank() && it != "null" },
                    subscribedAt = obj.optLong("subscribedAt", 0L)
                )
            }.distinctBy { it.channelId }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveGroups(list: List<SubscriptionGroup>) {
        groupsState.value = list
        val array = JSONArray()
        list.forEach { group ->
            array.put(JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("channelIds", JSONArray().also { ids -> group.channelIds.forEach(ids::put) })
            })
        }
        prefs.edit().putString(KEY_GROUPS, array.toString()).apply()
    }

    private fun loadGroups(): List<SubscriptionGroup> {
        val raw = prefs.getString(KEY_GROUPS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val ids = obj.optJSONArray("channelIds")
                SubscriptionGroup(
                    id = id,
                    name = obj.optString("name").takeIf { it.isNotBlank() } ?: "Group",
                    channelIds = (0 until (ids?.length() ?: 0)).mapNotNull { j ->
                        ids?.optString(j)?.takeIf { it.isNotBlank() }
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "local_subscriptions"
        private const val KEY_SUBSCRIPTIONS = "subscriptions"
        private const val KEY_GROUPS = "groups"

        private val LOCK = Any()

        @Volatile
        private var sharedSubscriptions: MutableStateFlow<List<LocalSubscription>>? = null

        @Volatile
        private var sharedGroups: MutableStateFlow<List<SubscriptionGroup>>? = null
    }
}
