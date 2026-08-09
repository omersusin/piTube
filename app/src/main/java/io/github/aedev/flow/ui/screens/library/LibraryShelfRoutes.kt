package io.github.aedev.flow.ui.screens.library

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.ui.components.PlaylistCard
import io.github.aedev.flow.ui.components.PlaylistCardLayout
import io.github.aedev.flow.ui.screens.playlists.PlaylistInfo
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun LibraryMediaShelfRoute(
    title: String,
    itemsFlow: StateFlow<List<LibraryMediaItem>>,
    sourceName: String,
    onTitleClick: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onDownloadedVideoClick: (List<DownloadedVideo>, Int) -> Unit
) {
    val items by itemsFlow.collectAsStateWithLifecycle()
    LibraryMediaShelf(
        title = title,
        items = items,
        sourceName = sourceName,
        onTitleClick = onTitleClick,
        onVideoClick = onVideoClick,
        onDownloadedVideoClick = onDownloadedVideoClick
    )
}

@Composable
internal fun LibraryPlaylistsShelf(
    title: String,
    videoPlaylistsFlow: StateFlow<List<PlaylistInfo>>,
    onTitleClick: () -> Unit,
    onVideoPlaylistClick: (String) -> Unit
) {
    val videoPlaylists by videoPlaylistsFlow.collectAsStateWithLifecycle()
    LibraryShelf(title = title, onTitleClick = onTitleClick) {
        items(
            items = videoPlaylists,
            key = { "video-${it.id}" },
            contentType = { "video-playlist" }
        ) { playlist ->
            PlaylistCard(
                playlist = playlist,
                onClick = { onVideoPlaylistClick(playlist.id) },
                layout = PlaylistCardLayout.SHELF,
                modifier = Modifier.width(LibraryShelfCardWidth)
            )
        }
    }
}

@Composable
internal fun LibraryVideoShelf(
    title: String,
    videosFlow: StateFlow<List<Video>>,
    onTitleClick: () -> Unit,
    onVideoClick: (Video) -> Unit
) {
    val videos by videosFlow.collectAsStateWithLifecycle()
    LibraryShelf(title = title, onTitleClick = onTitleClick) {
        items(
            items = videos,
            key = Video::id,
            contentType = { "video" }
        ) { video ->
            LibraryVideoCard(
                video = video,
                onClick = { onVideoClick(video) }
            )
        }
    }
}

@Composable
internal fun LibraryShortsShelfRoute(
    title: String,
    shortsFlow: StateFlow<List<Video>>,
    onTitleClick: () -> Unit,
    onShortClick: (Video) -> Unit
) {
    val shorts by shortsFlow.collectAsStateWithLifecycle()
    LibraryShortsShelf(
        title = title,
        shorts = shorts,
        onTitleClick = onTitleClick,
        onShortClick = onShortClick
    )
}
