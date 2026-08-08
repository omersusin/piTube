package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

object LocalSubscriptionsRepository {
    private const val FILE_NAME = "local_subscriptions.json"
    private const val GROUPS_FILE_NAME = "subscription_groups.json"
    private val gson = Gson()

    private val _subscriptions = MutableStateFlow<List<LocalSubscription>>(emptyList())
    val subscriptions: StateFlow<List<LocalSubscription>> = _subscriptions.asStateFlow()

    private val _groups = MutableStateFlow<List<SubscriptionGroup>>(emptyList())
    val groups: StateFlow<List<SubscriptionGroup>> = _groups.asStateFlow()

    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        _subscriptions.value = loadSubscriptions(context)
        _groups.value = loadGroups(context)
        initialized = true
    }

    fun isSubscribed(context: Context, channelId: String?): Boolean {
        if (channelId.isNullOrBlank()) return false
        initialize(context)
        return _subscriptions.value.any { it.channelId == channelId }
    }

    fun get(context: Context, channelId: String): LocalSubscription? {
        initialize(context)
        return _subscriptions.value.find { it.channelId == channelId }
    }

    fun getAll(context: Context): List<LocalSubscription> {
        initialize(context)
        return _subscriptions.value
    }

    fun getAllSortedByName(context: Context): List<LocalSubscription> {
        initialize(context)
        return _subscriptions.value.sortedBy { it.name.lowercase() }
    }

    fun subscribe(context: Context, subscription: LocalSubscription) {
        if (subscription.channelId.isBlank()) return
        initialize(context)
        val current = _subscriptions.value
        val existing = current.find { it.channelId == subscription.channelId }
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
        saveSubscriptions(context, next)
    }

    fun unsubscribe(context: Context, channelId: String) {
        initialize(context)
        val next = _subscriptions.value.filterNot { it.channelId == channelId }
        saveSubscriptions(context, next)
        val prunedGroups = _groups.value.map { group ->
            if (channelId in group.channelIds) {
                group.copy(channelIds = group.channelIds - channelId)
            } else group
        }
        if (prunedGroups != _groups.value) saveGroups(context, prunedGroups)
    }

    fun toggle(context: Context, subscription: LocalSubscription): Boolean {
        return if (isSubscribed(context, subscription.channelId)) {
            unsubscribe(context, subscription.channelId)
            false
        } else {
            subscribe(context, subscription)
            true
        }
    }

    fun importAll(context: Context, channels: List<LocalSubscription>): Int {
        if (channels.isEmpty()) return 0
        initialize(context)
        val current = _subscriptions.value
        val known = current.map { it.channelId }.toMutableSet()
        val additions = mutableListOf<LocalSubscription>()
        for (channel in channels) {
            if (channel.channelId.isBlank()) continue
            if (known.add(channel.channelId)) additions.add(channel)
        }
        if (additions.isEmpty()) return 0
        saveSubscriptions(context, additions + current)
        return additions.size
    }

    fun clearAll(context: Context) {
        saveSubscriptions(context, emptyList())
        saveGroups(context, emptyList())
    }

    fun createGroup(context: Context, name: String, channelIds: List<String> = emptyList()): String {
        initialize(context)
        val id = UUID.randomUUID().toString()
        saveGroups(context, _groups.value + SubscriptionGroup(id, name.trim(), channelIds))
        return id
    }

    fun renameGroup(context: Context, groupId: String, name: String) {
        initialize(context)
        saveGroups(context, _groups.value.map {
            if (it.id == groupId) it.copy(name = name.trim()) else it
        })
    }

    fun deleteGroup(context: Context, groupId: String) {
        initialize(context)
        saveGroups(context, _groups.value.filterNot { it.id == groupId })
    }

    fun setGroupChannels(context: Context, groupId: String, channelIds: List<String>) {
        initialize(context)
        saveGroups(context, _groups.value.map {
            if (it.id == groupId) it.copy(channelIds = channelIds.distinct()) else it
        })
    }

    fun toggleChannelInGroup(context: Context, groupId: String, channelId: String) {
        initialize(context)
        saveGroups(context, _groups.value.map { group ->
            if (group.id != groupId) group
            else if (channelId in group.channelIds) group.copy(channelIds = group.channelIds - channelId)
            else group.copy(channelIds = group.channelIds + channelId)
        })
    }

    fun channelsInGroup(context: Context, groupId: String?): List<LocalSubscription> {
        initialize(context)
        val group = groupId?.let { _groups.value.find { g -> g.id == it } } ?: return getAll(context)
        val ids = group.channelIds.toSet()
        return getAll(context).filter { it.channelId in ids }
    }

    private fun loadSubscriptions(context: Context): List<LocalSubscription> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val raw = file.readText()
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val channelId = obj.optString("channelId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                LocalSubscription(
                    channelId = channelId,
                    name = obj.optString("name").takeIf { it.isNotBlank() } ?: channelId,
                    avatarUrl = obj.optString("avatarUrl").takeIf { it.isNotBlank() },
                    handle = obj.optString("handle").takeIf { it.isNotBlank() },
                    subscribedAt = obj.optLong("subscribedAt", 0L)
                )
            }.distinctBy { it.channelId }
        } catch (e: Exception) { emptyList() }
    }

    private fun saveSubscriptions(context: Context, list: List<LocalSubscription>) {
        _subscriptions.value = list
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
        File(context.filesDir, FILE_NAME).writeText(array.toString())
    }

    private fun loadGroups(context: Context): List<SubscriptionGroup> {
        val file = File(context.filesDir, GROUPS_FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val raw = file.readText()
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
        } catch (e: Exception) { emptyList() }
    }

    private fun saveGroups(context: Context, list: List<SubscriptionGroup>) {
        _groups.value = list
        val array = JSONArray()
        list.forEach { group ->
            array.put(JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("channelIds", JSONArray().also { ids -> group.channelIds.forEach(ids::put) })
            })
        }
        File(context.filesDir, GROUPS_FILE_NAME).writeText(array.toString())
    }
}
