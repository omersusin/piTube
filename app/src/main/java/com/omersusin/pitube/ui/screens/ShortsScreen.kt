package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.omersusin.pitube.data.PipedApiService
import com.omersusin.pitube.data.VideoItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen() {
    val context = LocalContext.current
    var shorts by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val allVideos = PipedApiService.create().getTrending()
                shorts = allVideos.filter { it.isShort || it.duration < 60 }
                if (shorts.isEmpty()) shorts = allVideos.take(10)
            } catch (e: Exception) { e.printStackTrace() } finally { isLoading = false }
        }
    }

    if (isLoading) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
    else if (shorts.isEmpty()) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No shorts found") } }
    else {
        val pagerState = rememberPagerState(pageCount = { shorts.size })
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize(), pageSize = PageSize.Fill) { page -> ShortVideoPlayer(shorts[page]) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortVideoPlayer(video: VideoItem) {
    val context = LocalContext.current
    var streamUrl by remember { mutableStateOf<String?>(null) }
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(video.videoId) {
        try {
            val streamInfo = PipedApiService.create().getStreams(video.videoId)
            streamUrl = streamInfo.hls ?: streamInfo.dash
            streamUrl?.let {
                exoPlayer.setMediaItem(MediaItem.fromUri(it))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ONE
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(video.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(video.uploaderName, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
        }
    }
}
