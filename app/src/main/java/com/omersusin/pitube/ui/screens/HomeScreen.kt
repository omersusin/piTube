package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import com.omersusin.pitube.ui.components.pressScale
import com.omersusin.pitube.ui.components.thumbnailGradientOverlay
import com.omersusin.pitube.ui.components.rememberFeedGridLayout
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
    var feedPage by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val isLoggedIn = remember { AuthManager.isLoggedIn(context) }
    val density = LocalContext.current.resources.displayMetrics.density
    val feedLayout = rememberFeedGridLayout((LocalContext.current.resources.displayMetrics.widthPixels / density).dp)

    val loadMore = {
        if (!isLoadingMore) {
            scope.launch {
                isLoadingMore = true
                try {
                    delay(500)
                    if (isLoggedIn) {
                        // Load more from personalized feed — InnerTubeFeed doesn't support pages yet,
                        // so we shuffle to give some variety on subsequent loads
                        val feedVideos = InnerTubeFeed.fetchFeed(context, "FEwhat_to_watch")
                        val currentIds = videos.map { it.videoId }.toSet()
                        val newVideos = feedVideos.filter { it.videoId !in currentIds }
                        if (newVideos.isNotEmpty()) {
                            videos = videos + newVideos
                        }
                    } else {
                        // Load more from home feed (trending browseId is dead since 2025)
                        val moreVideos = InnerTubeFeed.fetchTrending(context)
                        val currentIds = videos.map { it.videoId }.toSet()
                        val newVideos = moreVideos.filter { it.videoId !in currentIds }
                        if (newVideos.isNotEmpty()) {
                            videos = videos + newVideos.shuffled()
                        }
                    }
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
            feedPage = 0
            try {
                if (isLoggedIn) {
                    // Fetch personalized feed from YouTube
                    val feedVideos = InnerTubeFeed.fetchFeed(context, "FEwhat_to_watch")
                    if (feedVideos.isNotEmpty()) {
                        videos = feedVideos
                    } else {
                        // Fallback to home feed if personalized feed is empty
                        videos = InnerTubeFeed.fetchTrending(context)
                    }
                } else {
                    // Not logged in - use home feed
                    FlowNeuroEngine.initialize(context)
                    val trending = InnerTubeFeed.fetchTrending(context)
                    val ranked = FlowNeuroEngine.rank(context, trending)
                    videos = ranked
                }
            } catch (e: Exception) {
                try {
                    videos = InnerTubeFeed.fetchTrending(context)
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

    // Infinite scroll trigger for both list and grid
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
    LaunchedEffect(gridState) {
        snapshotFlow {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
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
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(feedLayout.columns),
                state = gridState,
                contentPadding = PaddingValues(feedLayout.contentPadding),
                verticalArrangement = Arrangement.spacedBy(feedLayout.cardSpacing),
                horizontalArrangement = Arrangement.spacedBy(feedLayout.cardSpacing)
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
                        onChannelClick = { onChannelClick(video.channelId ?: video.uploaderUrl?.substringAfter("/channel/")?.substringBefore("/") ?: "") }
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
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource),
        interactionSource = interactionSource,
        onClick = onClick
    ) {
        Column {
            AsyncImage(
                model = video.safeThumb,
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .thumbnailGradientOverlay(),
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
