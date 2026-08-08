package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.*
import com.omersusin.pitube.ui.components.VideoListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onVideoClick: (VideoItem) -> Unit, onChannelClick: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isGrid by remember { mutableStateOf(false) }

    val refresh = {
        scope.launch {
            isRefreshing = true
            try {
                FlowNeuroEngine.initialize(context)
                val trending = PipedApiService.create().getTrending()
                val ranked = FlowNeuroEngine.rank(context, trending)
                videos = ranked
            } catch (e: Exception) {
                try {
                    videos = PipedApiService.create().getTrending()
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No videos available", style = MaterialTheme.typography.bodyLarge)
            }
        } else if (isGrid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(videos) { video ->
                    VideoGridItem(
                        video = video,
                        onClick = {
                            onVideoClick(video)
                            scope.launch {
                                FlowNeuroEngine.recordInteraction(
                                    context = context,
                                    videoId = video.videoId,
                                    title = video.title,
                                    channelName = video.uploaderName,
                                    channelId = null,
                                    type = InteractionType.CLICK
                                )
                            }
                        }
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(videos) { video ->
                    VideoListItem(
                        video = video,
                        onClick = {
                            onVideoClick(video)
                            scope.launch {
                                FlowNeuroEngine.recordInteraction(
                                    context = context,
                                    videoId = video.videoId,
                                    title = video.title,
                                    channelName = video.uploaderName,
                                    channelId = null,
                                    type = InteractionType.CLICK
                                )
                            }
                        },
                        onChannelClick = { onChannelClick(video.uploaderName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoGridItem(video: VideoItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column {
            AsyncImage(
                model = video.safeThumb,
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    video.uploaderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
