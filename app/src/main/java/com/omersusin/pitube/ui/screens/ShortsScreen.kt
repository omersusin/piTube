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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.omersusin.pitube.data.PipedApiService
import com.omersusin.pitube.data.StreamResolver
import com.omersusin.pitube.data.VideoItem
import kotlinx.coroutines.launch

private object ShortsPrefetchCache {
    private val cache = mutableMapOf<String, StreamResolver.Resolved?>()
    private val inFlight = mutableSetOf<String>()
    suspend fun prefetch(videoId: String, context: android.content.Context) { if (cache.containsKey(videoId) || !inFlight.add(videoId)) return; cache[videoId] = StreamResolver.resolve(videoId, context); inFlight.remove(videoId) }
    fun take(videoId: String): StreamResolver.Resolved? = cache.remove(videoId)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen() {
    var shorts by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { scope.launch { try { val allVideos = PipedApiService.create().getTrending(); shorts = allVideos.filter { it.isShort || it.duration in 1..60 }; if (shorts.isEmpty()) shorts = allVideos.take(10) } catch (e: Exception) { e.printStackTrace() } finally { isLoading = false } } }
    if (isLoading) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
    else if (shorts.isEmpty()) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No shorts found") } }
    else {
        val pagerState = rememberPagerState(pageCount = { shorts.size })
        LaunchedEffect(pagerState.currentPage) { val nextIndex = pagerState.currentPage + 1; if (nextIndex < shorts.size) ShortsPrefetchCache.prefetch(shorts[nextIndex].videoId, context) }
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize(), pageSize = PageSize.Fill) { page -> ShortVideoPlayer(video = shorts[page], isActive = page == pagerState.currentPage) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortVideoPlayer(video: VideoItem, isActive: Boolean) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    var mediaReady by remember { mutableStateOf(false) }
    LaunchedEffect(video.videoId) {
        mediaReady = false
        try {
            val resolved = ShortsPrefetchCache.take(video.videoId) ?: StreamResolver.resolve(video.videoId, context)
            val source = resolved?.let { StreamResolver.buildMediaSource(context, it) }
            if (source != null) { exoPlayer.setMediaSource(source); exoPlayer.prepare(); exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ONE; mediaReady = true }
        } catch (e: Exception) { e.printStackTrace() }
    }
    LaunchedEffect(isActive, mediaReady) { exoPlayer.playWhenReady = isActive && mediaReady; if (!isActive) exoPlayer.pause() }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = false; resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM } }, modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) { Text(video.title, color = Color.White, style = MaterialTheme.typography.titleMedium); Spacer(modifier = Modifier.height(4.dp)); Text(video.uploaderName, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall) }
    }
}
