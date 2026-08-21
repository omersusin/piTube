package com.omersusin.pitube.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.omersusin.pitube.data.local.VideoHistoryEntry
import com.omersusin.pitube.data.model.Video

/**
 * Room entity that replaces the previous DataStore-based watch history.
 *
 * The old DataStore approach stored ~8 preference keys per video, meaning
 * 46 000 watched videos → ~370 000 entries in a single serialised protobuf
 * file that was fully deserialized on every read.  SQLite handles millions of
 * rows efficiently with proper indexing.
 */
@Entity(
    tableName = "watch_history",
    primaryKeys = ["videoId", "profileId"],
    indices = [
        Index(value = ["videoId"]),
        Index(value = ["timestamp"]),
        Index(value = ["isMusic"]),
        Index(value = ["isShort"]),
        Index(value = ["isLocal"])
    ]
)
data class WatchHistoryEntity(
    val videoId: String,
    val position: Long,
    val duration: Long,
    val timestamp: Long,
    val title: String,
    val thumbnailUrl: String,
    val channelName: String,
    val channelId: String,
    val isMusic: Boolean,
    val isShort: Boolean = false,
    val isLocal: Boolean = false,
    /** Owning profile/account. Empty = legacy device-wide row (pre-scope migration). */
    val profileId: String = ""
) {
    fun toDomain() = VideoHistoryEntry(
        videoId = videoId,
        position = position,
        duration = duration,
        timestamp = timestamp,
        title = title,
        thumbnailUrl = thumbnailUrl,
        channelName = channelName,
        channelId = channelId,
        isMusic = isMusic,
        isShort = isShort,
        isLocal = isLocal,
        profileId = profileId
    )

    /** Reconstruct a lightweight [Video] from history metadata (no stream info). */
    fun toVideo() = Video(
        id = videoId,
        title = title,
        channelName = channelName,
        channelId = channelId,
        thumbnailUrl = thumbnailUrl,
        duration = if (duration > 0) (duration / 1000).toInt() else 0,
        viewCount = 0,
        uploadDate = "",
        isShort = isShort
    )

    companion object {
        fun fromDomain(entry: VideoHistoryEntry, profileId: String = "") = WatchHistoryEntity(
            videoId      = entry.videoId,
            position     = entry.position,
            duration     = entry.duration,
            timestamp    = entry.timestamp,
            title        = entry.title,
            thumbnailUrl = entry.thumbnailUrl,
            channelName  = entry.channelName,
            channelId    = entry.channelId,
            isMusic      = entry.isMusic,
            isShort      = entry.isShort,
            isLocal      = entry.isLocal,
            profileId    = profileId
        )
    }
}
