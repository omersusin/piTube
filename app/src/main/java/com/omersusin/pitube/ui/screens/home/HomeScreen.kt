package com.omersusin.pitube.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omersusin.pitube.R
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.ui.components.*
import com.omersusin.pitube.ui.screens.notifications.NotificationViewModel

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp
import com.omersusin.pitube.ui.TabScrollEventBus

private data class HomeLayoutConfig(
    val columns: Int,
    val contentPadding: Dp,
    val cardSpacing: Dp,
    val shortsShelfAfterIndex: Int,
    val shimmerColumns: Int
)

@Composable
private fun rememberHomeLayoutConfig(maxWidth: Dp): HomeLayoutConfig {
    val base = rememberFeedGridLayout(maxWidth)
    return remember(base, maxWidth) {
        val shortsShelfAfterIndex = when {
            maxWidth < 480.dp -> 1
            maxWidth < 700.dp -> 2
            maxWidth < 900.dp -> 2
            maxWidth < 1200.dp -> 3
            else -> 4
        }
        HomeLayoutConfig(
            columns = base.columns,
            contentPadding = base.contentPadding,
            cardSpacing = base.cardSpacing,
            shortsShelfAfterIndex = shortsShelfAfterIndex,
            shimmerColumns = base.columns
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun HomeScreen(
    onVideoClick: (Video) -> Unit,
    onShortClick: (Video) -> Unit,
    onNotificationClick: () -> Unit,
    onChannelClick: (String) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onOpenShortsFeed: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    notificationViewModel: NotificationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val unreadNotifications by notificationViewModel.unreadCount.collectAsStateWithLifecycle()
    val preferences = remember { com.omersusin.pitube.data.local.PlayerPreferences(context) }
    val homeViewMode by preferences.homeViewMode.collectAsStateWithLifecycle(
        initialValue = com.omersusin.pitube.data.local.HomeViewMode.GRID
    )
    val homeFeedEnabled by preferences.homeFeedEnabled.collectAsStateWithLifecycle(initialValue = true)
    val refreshHomeOnReselect by preferences.refreshHomeOnReselect.collectAsStateWithLifecycle(initialValue = true)
    val showAppLogoIcon by preferences.showAppLogoIcon.collectAsStateWithLifecycle(initialValue = true)

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    LifecycleStartEffect(viewModel, homeFeedEnabled) {
        if (homeFeedEnabled) {
            viewModel.onHomeVisible()
        } else {
            viewModel.onHomeHidden()
        }
        onStopOrDispose { viewModel.onHomeHidden() }
    }

    val videoIndexById = remember(uiState.videos) {
        buildMap(uiState.videos.size) {
            uiState.videos.forEachIndexed { index, video -> put(video.id, index) }
        }
    }
    LaunchedEffect(gridState, videoIndexById) {
        snapshotFlow {
            var lastVisibleVideoIndex = -1
            gridState.layoutInfo.visibleItemsInfo.forEach { item ->
                val index = videoIndexById[item.key as? String] ?: return@forEach
                if (index > lastVisibleVideoIndex) lastVisibleVideoIndex = index
            }
            lastVisibleVideoIndex
        }
            .distinctUntilChanged()
            .collect(viewModel::onHomeViewportChanged)
    }

    // Viewport impressions: only items dwelt in view are recorded as "shown".
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
            .debounce(500)
            .collect { viewModel.recordImpressions(it) }
    }

    LaunchedEffect(refreshHomeOnReselect) {
        TabScrollEventBus.scrollToTopEvents
            .filter { it == "home" }
            .collectLatest {
                gridState.animateScrollToItem(0)
                if (refreshHomeOnReselect) {
                    viewModel.refreshFeed()
                }
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (showAppLogoIcon) {
                            Icon(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            stringResource(R.string.app_name_uppercase),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNotificationClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = stringResource(R.string.notifications),
                                    modifier = Modifier.size(24.dp)
                                )
                                if (unreadNotifications > 0) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = 4.dp, y = (-2).dp)
                                            .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                                            .size(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (unreadNotifications > 9) stringResource(R.string.notification_badge_9_plus) else unreadNotifications.toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 9.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        ResettableHomePullToRefreshBox(
            resetKey = uiState.isLoading && uiState.videos.isEmpty(),
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshFeed() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isListView = homeViewMode == com.omersusin.pitube.data.local.HomeViewMode.LIST
            val layoutConfig = rememberHomeLayoutConfig(maxWidth)
            val gridCells = if (isListView) GridCells.Fixed(1) else GridCells.Fixed(layoutConfig.columns)

            when {
                !homeFeedEnabled -> {
                    FeedDisabledState(modifier = Modifier.fillMaxSize())
                }

                uiState.isLoading && uiState.videos.isEmpty() -> {
                    // Initial loading state — matches grid layout
                    LazyVerticalGrid(
                        columns = if (isListView) GridCells.Fixed(1) else GridCells.Fixed(layoutConfig.shimmerColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = if (isListView) 0.dp else layoutConfig.contentPadding,
                            end = if (isListView) 0.dp else layoutConfig.contentPadding,
                            top = 8.dp,
                            bottom = 80.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing),
                        verticalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing),
                        userScrollEnabled = false
                    ) {
                        items(12) {
                            if (isListView) {
                                ShimmerVideoCardHorizontal()
                            } else if (layoutConfig.shimmerColumns == 1) {
                                ShimmerVideoCardFullWidth()
                            } else {
                                ShimmerGridVideoCard()
                            }
                        }
                    }
                }

                uiState.error != null && uiState.videos.isEmpty() -> {
                    ErrorState(
                        message = uiState.error ?: stringResource(R.string.error_occurred),
                        onRetry = { viewModel.retry() }
                    )
                }

                uiState.videos.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.VideoLibrary,
                        title = stringResource(R.string.empty_feed_title),
                        body = stringResource(R.string.empty_feed_body),
                        actionLabel = stringResource(R.string.retry),
                        onAction = { viewModel.refreshFeed() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = gridCells,
                        modifier = Modifier.fillMaxSize(),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = if (isListView) 0.dp else layoutConfig.contentPadding,
                            end = if (isListView) 0.dp else layoutConfig.contentPadding,
                            top = 4.dp,
                            bottom = 80.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing),
                        verticalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing)
                    ) {
                        val videos = uiState.videos
                        if (videos.isNotEmpty()) {
                            val insertShortsAfter = layoutConfig.shortsShelfAfterIndex.coerceAtMost(videos.size)

                            // ── Videos before shelves ──
                            val videosBeforeShorts = videos.take(insertShortsAfter)
                            items(
                                items = videosBeforeShorts,
                                key = { it.id }
                            ) { video ->
                                LaunchedEffect(
                                    video.id,
                                    video.channelId,
                                    video.channelThumbnailUrl
                                ) {
                                    viewModel.enrichChannelMetadataIfMissing(video.id)
                                }
                                if (isListView) {
                                    VideoCardHorizontal(
                                        video = video,
                                        onClick = { onVideoClick(video) },
                                        onChannelClick = { channelId -> onChannelClick(channelId) }
                                    )
                                } else {
                                    VideoCardFullWidth(
                                        video = video,
                                        onClick = { onVideoClick(video) },
                                        onChannelClick = { channelId -> onChannelClick(channelId) },
                                        useInternalPadding = false
                                    )
                                }
                            }

                            // ── Continue Watching Shelf (between first videos and shorts) ──
                            if (uiState.continueWatchingVideos.isNotEmpty()) {
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "continue_watching_shelf"
                                ) {
                                    ContinueWatchingShelf(
                                        entries = uiState.continueWatchingVideos,
                                        onVideoClick = { videoId ->
                                            val entry = uiState.continueWatchingVideos.find { it.videoId == videoId }
                                            if (entry != null) {
                                                onVideoClick(
                                                    Video(
                                                        id = entry.videoId,
                                                        title = entry.title,
                                                        channelName = entry.channelName,
                                                        channelId = entry.channelId,
                                                        thumbnailUrl = entry.thumbnailUrl,
                                                        duration = (entry.duration / 1000).toInt(),
                                                        viewCount = 0L,
                                                        uploadDate = ""
                                                    )
                                                )
                                            }
                                        },
                                        onRemove = { videoId ->
                                            viewModel.removeContinueWatchingEntry(videoId)
                                        },
                                        onSeeAllClick = onNavigateToHistory
                                    )
                                }
                            }

                            // ── Shorts Shelf ──
                            if (uiState.shorts.isNotEmpty()) {
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "shorts_shelf"
                                ) {
                                    ShortsShelf(
                                        shorts = uiState.shorts,
                                        onShortClick = { onShortClick(it) },
                                        onSeeAllClick = onOpenShortsFeed
                                    )
                                }
                            }

                            // ── Remaining Videos ──
                            val videosAfterShorts = videos.drop(insertShortsAfter)
                            items(
                                items = videosAfterShorts,
                                key = { it.id }
                            ) { video ->
                                LaunchedEffect(
                                    video.id,
                                    video.channelId,
                                    video.channelThumbnailUrl
                                ) {
                                    viewModel.enrichChannelMetadataIfMissing(video.id)
                                }
                                if (isListView) {
                                    VideoCardHorizontal(
                                        video = video,
                                        onClick = { onVideoClick(video) },
                                        onChannelClick = { channelId -> onChannelClick(channelId) }
                                    )
                                } else {
                                    VideoCardFullWidth(
                                        video = video,
                                        onClick = { onVideoClick(video) },
                                        onChannelClick = { channelId -> onChannelClick(channelId) },
                                        useInternalPadding = false
                                    )
                                }
                            }
                        }

                        if (uiState.isLoadingMore) {
                            item(
                                key = "loading_indicator",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.loading_more),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // End of feed indicator
                        if (!uiState.hasMorePages && uiState.videos.size > 100 && !uiState.isLoadingMore) {
                            item(
                                key = "feed_footer",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                FlowFeedFooter(
                                    videoCount = uiState.videos.size,
                                    onRefresh = { viewModel.refreshFeed() }
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResettableHomePullToRefreshBox(
    resetKey: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    key(resetKey) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            content = content
        )
    }
}

@Composable
private fun FeedDisabledState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartDisplay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = stringResource(R.string.content_settings_home_feed_disabled_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.content_settings_home_feed_disabled_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun FlowFeedFooter(
    videoCount: Int,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = stringResource(R.string.personalized_feed),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.videos_curated_template, videoCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(androidx.compose.ui.res.stringResource(R.string.home_refresh_feed))
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onRetry) {
                Text(androidx.compose.ui.res.stringResource(R.string.retry))
            }
        }
    }
}

private const val FLOW_LOGO_BG_PATH = "M21.58 7.16C21.33 6.22 20.59 5.48 19.65 5.23C17.96 4.77 12 4.77 12 4.77C12 4.77 6.04 4.77 4.35 5.23C3.41 5.48 2.67 6.22 2.42 7.16C1.96 8.85 1.96 12.38 1.96 12.38C1.96 12.38 1.96 15.91 2.42 17.6C2.67 18.54 3.41 19.28 4.35 19.53C6.04 19.99 12 19.99 12 19.99C12 19.99 17.96 19.99 19.65 19.53C20.59 19.28 21.33 18.54 21.58 17.6C22.04 15.91 22.04 12.38 22.04 12.38C22.04 12.38 22.04 8.85 21.58 7.16Z"
