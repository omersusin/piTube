package com.omersusin.pitube.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omersusin.pitube.R
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.data.video.DownloadedVideo

@Composable
fun LibraryScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToLikedVideos: () -> Unit,
    onNavigateToWatchLater: () -> Unit,
    onNavigateToSavedShorts: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToSubscriptions: () -> Unit = {},
    onVideoClick: (Video) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onDownloadedVideoClick: (List<DownloadedVideo>, Int) -> Unit,
    onSavedShortClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val historyTitle = stringResource(R.string.library_history_label)
    val playlistsTitle = stringResource(R.string.library_playlists_label)
    val likesTitle = stringResource(R.string.library_liked_videos_label)
    val downloadsTitle = stringResource(R.string.library_downloads_label)
    val watchLaterTitle = stringResource(R.string.library_watch_later_label)
    val savedShortsTitle = stringResource(R.string.library_saved_shorts_label)

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { LibraryTopBar() }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(key = "subscriptions", contentType = "nav-row") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onNavigateToSubscriptions),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Subscriptions,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Abonelikler",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item(key = "history", contentType = "media-shelf") {
                LibraryMediaShelfRoute(
                    title = historyTitle,
                    itemsFlow = viewModel.history,
                    sourceName = historyTitle,
                    onTitleClick = onNavigateToHistory,
                    onVideoClick = onVideoClick,
                    onDownloadedVideoClick = onDownloadedVideoClick
                )
            }

            item(key = "playlists", contentType = "playlist-shelf") {
                LibraryPlaylistsShelf(
                    title = playlistsTitle,
                    videoPlaylistsFlow = viewModel.playlists,
                    onTitleClick = onNavigateToPlaylists,
                    onVideoPlaylistClick = onPlaylistClick
                )
            }

            item(key = "watch-later", contentType = "video-shelf") {
                LibraryVideoShelf(
                    title = watchLaterTitle,
                    videosFlow = viewModel.watchLater,
                    onTitleClick = onNavigateToWatchLater,
                    onVideoClick = onVideoClick
                )
            }

            item(key = "likes", contentType = "media-shelf") {
                LibraryMediaShelfRoute(
                    title = likesTitle,
                    itemsFlow = viewModel.likes,
                    sourceName = likesTitle,
                    onTitleClick = onNavigateToLikedVideos,
                    onVideoClick = onVideoClick,
                    onDownloadedVideoClick = onDownloadedVideoClick
                )
            }

            item(key = "downloads", contentType = "media-shelf") {
                LibraryMediaShelfRoute(
                    title = downloadsTitle,
                    itemsFlow = viewModel.downloads,
                    sourceName = downloadsTitle,
                    onTitleClick = onNavigateToDownloads,
                    onVideoClick = onVideoClick,
                    onDownloadedVideoClick = onDownloadedVideoClick
                )
            }

            item(key = "saved-shorts", contentType = "shorts-shelf") {
                LibraryShortsShelfRoute(
                    title = savedShortsTitle,
                    shortsFlow = viewModel.savedShorts,
                    onTitleClick = onNavigateToSavedShorts,
                    onShortClick = onSavedShortClick
                )
            }
        }
    }
}

@Composable
private fun LibraryTopBar() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.library),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
