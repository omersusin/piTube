package com.omersusin.pitube.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.player.GlobalPlayerState
import com.omersusin.pitube.ui.screens.home.HomeViewModel
import com.omersusin.pitube.ui.screens.player.VideoPlayerViewModel
import com.omersusin.pitube.ui.screens.search.SearchViewModel
import com.omersusin.pitube.ui.screens.subscriptions.SubscriptionsViewModel
import com.omersusin.pitube.ui.tv.screens.TvPlayerScreen
import com.omersusin.pitube.ui.tv.theme.TvTheme

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun FlowTvApp(
    deeplinkVideoId: String? = null,
    isShort: Boolean = false,
    onDeeplinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val homeViewModel: HomeViewModel = hiltViewModel(activity)
    val playerViewModel: VideoPlayerViewModel = hiltViewModel(activity)
    val subscriptionsViewModel: SubscriptionsViewModel = hiltViewModel(activity)
    val searchViewModel: SearchViewModel = hiltViewModel(activity)
    val navController = rememberNavController()
    val activeVideo by GlobalPlayerState.currentVideo.collectAsStateWithLifecycle()

    fun play(video: Video) {
        GlobalPlayerState.setCurrentVideo(video)
        playerViewModel.playVideo(video)
    }

    fun playPlaylist(
        videos: List<Video>,
        title: String,
    ) {
        val first = videos.firstOrNull() ?: return
        GlobalPlayerState.setCurrentVideo(first)
        playerViewModel.playPlaylist(videos, startIndex = 0, title = title)
    }

    LaunchedEffect(deeplinkVideoId) {
        val videoId = deeplinkVideoId ?: return@LaunchedEffect
        // Metadata is resolved by playVideo -> loadVideoInfo; the stub only seeds the id.
        play(
            Video(
                id = videoId,
                title = "",
                channelName = "",
                channelId = "",
                thumbnailUrl = "",
                duration = 0,
                viewCount = 0L,
                uploadDate = "",
                isShort = isShort,
            ),
        )
        onDeeplinkConsumed()
    }

    TvTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            val video = activeVideo
            if (video == null) {
                TvShell(
                    navController = navController,
                    homeViewModel = homeViewModel,
                    subscriptionsViewModel = subscriptionsViewModel,
                    searchViewModel = searchViewModel,
                    onPlayVideo = ::play,
                    onPlayPlaylist = ::playPlaylist,
                )
            } else {
                TvPlayerScreen(
                    video = video,
                    viewModel = playerViewModel,
                    onClose = {
                        playerViewModel.clearVideo()
                        GlobalPlayerState.setCurrentVideo(null)
                    },
                )
            }
        }
    }
}
