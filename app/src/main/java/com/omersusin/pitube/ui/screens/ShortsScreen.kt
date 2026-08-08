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
import java.util.concurrent.ConcurrentHashMap

private object ShortsPrefetchCache {
    private val cache = ConcurrentHashMap<String, StreamResolver.Resolved?>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    suspend fun prefetch(videoId: String, context: android.content.Context) {
        if (cache.containsKey(videoId) || !inFlight.add(videoId)) return
        cache[videoId] = StreamResolver.resolve(videoId, context)
        inFlight.remove(videoId)
    }
    fun take(videoId: String): StreamResolver.Resolved? = cache.remove(videoId)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen() {
    val context = LocalContext.current
    var shorts by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val api = PipedApiService.create()
                // Try dedicated shorts endpoint
                try {
                    val shortsList = api.getShorts()
                    // Only keep actual shorts (vertical videos, typically < 60s)
                    shorts = shortsList.filter { video ->
                        video.url.contains("/shorts/") || 
                        (video.duration in 1..180 && video.isShort)
                    }
                } catch (e: Exception) {
                    // Fallback: get trending and filter strictly for shorts
                    try {
                        val trending = api.getTrending()
                        shorts = trending.filter { video ->
                            video.url.contains("/shorts/") ||
                            (video.isShort && video.duration in 1..180)
                        }
                    } catch (e2: Exception) {
                        error = "Failed to load shorts"
                    }
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        error != null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error ?: "Error loading shorts",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        isLoading = true
                        error = null
                        scope.launch {
                            try {
                                val api = PipedApiService.create()
                                val shortsList = api.getShorts()
                                shorts = shortsList.filter { it.url.contains("/shorts/") || it.duration in 1..180 }
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                isLoading = false
                            }
                        }
                    }) {
                        Text("Retry")
                    }
                }
            }
        }
        shorts.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No shorts available",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Check back later for new shorts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        else -> {
            val pagerState = rememberPagerState(pageCount = { shorts.size })
            LaunchedEffect(pagerState.currentPage) {
                val nextIndex = pagerState.currentPage + 1
                if (nextIndex < shorts.size) ShortsPrefetchCache.prefetch(shorts[nextIndex].videoId, context)
            }
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSize = PageSize.Fill
            ) { page ->
                ShortVideoPlayer(
                    video = shorts[page],
                    isActive = page == pagerState.currentPage
                )
            }
        }
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
            val resolved = ShortsPrefetchCache.take(video.videoId)
                ?: StreamResolver.resolve(video.videoId, context)
            val source = resolved?.let { StreamResolver.buildMediaSource(context, it) }
            if (source != null) {
                exoPlayer.setMediaSource(source)
                exoPlayer.prepare()
                exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ONE
                mediaReady = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(isActive, mediaReady) {
        exoPlayer.playWhenReady = isActive && mediaReady
        if (!isActive) exoPlayer.pause()
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .padding(bottom = 48.dp)
        ) {
            Text(
                video.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                video.uploaderName,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
