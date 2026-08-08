package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
    var isLoadingMore by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isGrid by remember { mutableStateOf(false) }
    var nextPageToken by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    val loadMore = {
        if (!isLoadingMore && nextPageToken != null) {
            scope.launch {
                isLoadingMore = true
                try {
                    val api = PipedApiService.create()
                    // Piped doesn't have pagination for trending, so we just shuffle for variety
                    val moreVideos = api.getTrending()
                    val newVideos = moreVideos.filter { new ->
                        videos.none { it.videoId == new.videoId }
                    }
                    videos = videos + newVideos.shuffled()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoadingMore = false
                }
            }
        }
    }

    val refresh = {
        scope.launch {
            isRefreshing = true
            try {
                FlowNeuroEngine.initialize(context)
                val api = PipedApiService.create()
                val trending = api.getTrending()
                val ranked = FlowNeuroEngine.rank(context, trending)
                videos = ranked
                nextPageToken = "next" // Enable load more
            } catch (e: Exception) {
                try {
                    videos = PipedApiService.create().getTrending()
                    nextPageToken = "next"
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

    // Infinite scroll trigger
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 5
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && !isLoadingMore && !isRefreshing) {
                loadMore()
            }
        }
    }

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
                if (isLoadingMore) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                if (isLoadingMore) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
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
