package io.github.aedev.flow.ui.screens.library

import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.DownloadedVideo

internal const val LIBRARY_SHELF_ITEM_LIMIT = 20

internal sealed interface LibraryMediaItem {
    val key: String

    data class VideoItem(val video: Video) : LibraryMediaItem {
        override val key: String = "video:${video.id}"
    }

    data class DownloadedVideoItem(val download: DownloadedVideo) : LibraryMediaItem {
        override val key: String = "downloaded-video:${download.video.id}"
    }
}

internal fun VideoHistoryEntry.toLibraryMediaItem(): LibraryMediaItem =
    LibraryMediaItem.VideoItem(
        Video(
            id = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnailUrl,
            duration = (duration / 1_000L).toInt(),
            viewCount = -1L,
            uploadDate = "",
            timestamp = timestamp
        )
    )

internal fun LikedVideoInfo.toLibraryMediaItem(): LibraryMediaItem =
    LibraryMediaItem.VideoItem(
        Video(
            id = videoId,
            title = title,
            channelName = channelName,
            channelId = "",
            thumbnailUrl = thumbnail,
            duration = 0,
            viewCount = -1L,
            uploadDate = "",
            timestamp = likedAt
        )
    )
