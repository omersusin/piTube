package com.omersusin.pitube.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class BlockedChannel(
    val channelId: String,
    val name: String,
    val avatarUrl: String? = null,
    val blockedAt: Long = System.currentTimeMillis()
)

enum class NotInterestedScope { VIDEO, CHANNEL }

class NotInterestedRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        synchronized(LOCK) {
            if (sharedHiddenVideos == null) {
                sharedHiddenVideos = MutableStateFlow(loadHiddenVideos())
                sharedBlockedChannels = MutableStateFlow(loadBlockedChannels())
                sharedLastAction = MutableStateFlow(null)
            }
        }
    }

    private val hiddenState: MutableStateFlow<List<HiddenVideo>> get() = sharedHiddenVideos!!
    private val blockedState: MutableStateFlow<List<BlockedChannel>> get() = sharedBlockedChannels!!

    val hiddenVideos: StateFlow<List<HiddenVideo>> get() = hiddenState.asStateFlow()
    val blockedChannels: StateFlow<List<BlockedChannel>> get() = blockedState.asStateFlow()

    val lastAction: StateFlow<UndoableAction?> get() = sharedLastAction!!.asStateFlow()

    data class UndoableAction(
        val scope: NotInterestedScope,
        val message: String,
        val videoId: String? = null,
        val channelId: String? = null,
        val channelName: String = "",
        val undoToken: String? = null,
        val id: Long = System.nanoTime()
    )

    fun undo(action: UndoableAction) {
        when (action.scope) {
            NotInterestedScope.VIDEO -> action.videoId?.let { unhideVideo(it) }
            NotInterestedScope.CHANNEL -> unblockChannel(action.channelId.orEmpty(), action.channelName)
        }
        sharedLastAction!!.value = null
    }

    fun clearLastAction() {
        sharedLastAction!!.value = null
    }

    data class HiddenVideo(
        val videoId: String,
        val title: String,
        val channelName: String? = null,
        val hiddenAt: Long = System.currentTimeMillis()
    )

    fun isVideoHidden(videoId: String?): Boolean =
        videoId != null && hiddenState.value.any { it.videoId == videoId }

    fun isChannelBlocked(channelId: String?): Boolean =
        channelId != null && blockedState.value.any { it.channelId == channelId }

    fun isFiltered(video: VideoItem): Boolean {
        if (isVideoHidden(video.videoId)) return true
        val blocked = blockedState.value
        if (blocked.isEmpty()) return false
        val channelId = video.uploaderUrl?.substringAfter("/channel/")?.takeIf { it.isNotBlank() }
        val channelName = video.uploaderName.takeIf { it.isNotBlank() }
        return blocked.any { entry ->
            if (entry.channelId.isNotBlank() && channelId != null) {
                entry.channelId == channelId
            } else {
                channelName != null && entry.name.equals(channelName, ignoreCase = true)
            }
        }
    }

    fun filter(videos: List<VideoItem>): List<VideoItem> {
        if (hiddenState.value.isEmpty() && blockedState.value.isEmpty()) return videos
        return videos.filterNot { isFiltered(it) }
    }

    fun hideVideo(video: VideoItem) {
        if (video.videoId.isBlank()) return
        if (isVideoHidden(video.videoId)) return
        val entry = HiddenVideo(
            videoId = video.videoId,
            title = video.title.takeIf { it.isNotBlank() } ?: video.videoId,
            channelName = video.uploaderName.takeIf { it.isNotBlank() }
        )
        saveHidden((listOf(entry) + hiddenState.value).take(MAX_HIDDEN_VIDEOS))
        sharedLastAction!!.value = UndoableAction(
            scope = NotInterestedScope.VIDEO,
            message = "Video hidden",
            videoId = video.videoId
        )
    }

    fun unhideVideo(videoId: String) {
        val next = hiddenState.value.filterNot { it.videoId == videoId }
        if (next.size != hiddenState.value.size) saveHidden(next)
    }

    fun blockChannel(
        channelId: String?,
        name: String,
        avatarUrl: String? = null,
        undoToken: String? = null
    ) {
        val id = channelId.orEmpty()
        if (id.isBlank() && name.isBlank()) return
        val already = blockedState.value.any {
            (id.isNotBlank() && it.channelId == id) || (id.isBlank() && it.name.equals(name, true))
        }
        if (already) return
        saveBlocked(listOf(BlockedChannel(id, name, avatarUrl)) + blockedState.value)
        sharedLastAction!!.value = UndoableAction(
            scope = NotInterestedScope.CHANNEL,
            message = if (name.isNotBlank()) "$name won't be recommended" else "Channel hidden",
            channelId = id,
            channelName = name,
            undoToken = undoToken
        )
    }

    fun unblockChannel(channelId: String, name: String) {
        val next = blockedState.value.filterNot {
            if (channelId.isNotBlank()) it.channelId == channelId else it.name.equals(name, true)
        }
        if (next.size != blockedState.value.size) saveBlocked(next)
    }

    fun clearHiddenVideos() = saveHidden(emptyList())
    fun clearBlockedChannels() = saveBlocked(emptyList())
    fun clearAll() {
        saveHidden(emptyList())
        saveBlocked(emptyList())
    }

    private fun saveHidden(list: List<HiddenVideo>) {
        hiddenState.value = list
        val array = JSONArray()
        list.forEach { entry ->
            array.put(JSONObject().apply {
                put("videoId", entry.videoId)
                put("title", entry.title)
                put("channelName", entry.channelName ?: JSONObject.NULL)
                put("hiddenAt", entry.hiddenAt)
            })
        }
        prefs.edit().putString(KEY_HIDDEN_VIDEOS, array.toString()).apply()
    }

    private fun loadHiddenVideos(): List<HiddenVideo> {
        val raw = prefs.getString(KEY_HIDDEN_VIDEOS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("videoId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                HiddenVideo(
                    videoId = id,
                    title = obj.optString("title").takeIf { it.isNotBlank() } ?: id,
                    channelName = obj.optString("channelName")
                        .takeIf { it.isNotBlank() && it != "null" },
                    hiddenAt = obj.optLong("hiddenAt", 0L)
                )
            }.distinctBy { it.videoId }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveBlocked(list: List<BlockedChannel>) {
        blockedState.value = list
        val array = JSONArray()
        list.forEach { channel ->
            array.put(JSONObject().apply {
                put("channelId", channel.channelId)
                put("name", channel.name)
                put("avatarUrl", channel.avatarUrl ?: JSONObject.NULL)
                put("blockedAt", channel.blockedAt)
            })
        }
        prefs.edit().putString(KEY_BLOCKED_CHANNELS, array.toString()).apply()
    }

    private fun loadBlockedChannels(): List<BlockedChannel> {
        val raw = prefs.getString(KEY_BLOCKED_CHANNELS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name")
                val id = obj.optString("channelId")
                if (name.isBlank() && id.isBlank()) return@mapNotNull null
                BlockedChannel(
                    channelId = id,
                    name = name.takeIf { it.isNotBlank() } ?: id,
                    avatarUrl = obj.optString("avatarUrl")
                        .takeIf { it.isNotBlank() && it != "null" },
                    blockedAt = obj.optLong("blockedAt", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "not_interested"
        private const val KEY_HIDDEN_VIDEOS = "hidden_videos"
        private const val KEY_BLOCKED_CHANNELS = "blocked_channels"
        private const val MAX_HIDDEN_VIDEOS = 1000

        private val LOCK = Any()

        @Volatile
        private var sharedHiddenVideos: MutableStateFlow<List<HiddenVideo>>? = null

        @Volatile
        private var sharedBlockedChannels: MutableStateFlow<List<BlockedChannel>>? = null

        @Volatile
        private var sharedLastAction: MutableStateFlow<UndoableAction?>? = null
    }
}
