package com.omersusin.pitube.player

import android.content.Context
import com.omersusin.pitube.data.model.Video
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists the playback queue (and the current position in it) so a "Now
 * playing" session survives process death. Saved on every queue change
 * (debounced by the manager) and restored on app start without auto-playing —
 * the mini-player reappears with the last video and tapping play resumes.
 */
object QueuePersistence {

    @Serializable
    data class Item(
        val id: String,
        val title: String = "",
        val channelName: String = "",
        val channelId: String = "",
        val thumbnailUrl: String = "",
        val duration: Int = 0,
        val isMusic: Boolean = false,
        val isShort: Boolean = false,
        val isLive: Boolean = false,
        val isUpcoming: Boolean = false,
    ) {
        fun toVideo(): Video = Video(
            id = id,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnailUrl,
            duration = duration,
            viewCount = 0L,
            uploadDate = "",
            isMusic = isMusic,
            isShort = isShort,
            isLive = isLive,
            isUpcoming = isUpcoming,
        )
    }

    @Serializable
    data class Snapshot(
        val index: Int = 0,
        val items: List<Item> = emptyList(),
        val title: String? = null,
        val savedAtMs: Long = 0L,
    )

    private const val PREFS_NAME = "pitube_queue_persistence"
    private const val SNAPSHOT_KEY = "queue_snapshot_v1"

    private val json = Json { ignoreUnknownKeys = true }

    fun save(context: Context, index: Int, items: List<Video>, title: String?) {
        if (items.isEmpty()) return
        runCatching {
            val snapshot = Snapshot(
                index = index,
                items = items.map { video ->
                    Item(
                        id = video.id,
                        title = video.title,
                        channelName = video.channelName,
                        channelId = video.channelId,
                        thumbnailUrl = video.thumbnailUrl,
                        duration = video.duration,
                        isMusic = video.isMusic,
                        isShort = video.isShort,
                        isLive = video.isLive,
                        isUpcoming = video.isUpcoming,
                    )
                },
                title = title,
                savedAtMs = System.currentTimeMillis(),
            )
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(SNAPSHOT_KEY, json.encodeToString(snapshot))
                .apply()
        }
    }

    fun load(context: Context): Snapshot? = runCatching {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(SNAPSHOT_KEY, null)
            ?: return null
        json.decodeFromString<Snapshot>(raw)
    }.getOrNull()

    fun clear(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(SNAPSHOT_KEY)
                .apply()
        }
    }
}
