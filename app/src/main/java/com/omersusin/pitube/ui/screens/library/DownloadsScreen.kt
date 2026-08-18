package com.omersusin.pitube.ui.screens.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.entity.DownloadItemStatus
import com.omersusin.pitube.data.local.entity.DownloadWithItems
import com.omersusin.pitube.data.video.DownloadedVideo
import com.omersusin.pitube.utils.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onVideoClick: (videos: List<DownloadedVideo>, startIndex: Int) -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    var showRemoveIncompleteDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val context = LocalContext.current

    val permissionsToRequest =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            if (results.values.any { it }) viewModel.rescan()
        }

    LaunchedEffect(Unit) {
        val anyMissing =
            permissionsToRequest.any { perm ->
                ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
            }
        if (anyMissing) {
            permissionLauncher.launch(permissionsToRequest)
        } else {
            viewModel.rescan()
        }
    }

    fun requestDelete(id: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.deleteVideoDownload(id)
    }

    val selectionMode = selectedIds.isNotEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
            ) {
                if (selectionMode) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                        Text(
                            text = stringResource(R.string.downloads_selected_count, selectedIds.size),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        val canPause = selectedIds.any { id ->
                            uiState.incompleteVideoDownloads.any {
                                it.download.videoId == id &&
                                    (it.overallStatus == DownloadItemStatus.PENDING ||
                                        it.overallStatus == DownloadItemStatus.DOWNLOADING)
                            }
                        }
                        val canResume = selectedIds.any { id ->
                            uiState.incompleteVideoDownloads.any {
                                it.download.videoId == id && it.overallStatus == DownloadItemStatus.PAUSED
                            }
                        }
                        val canRetry = selectedIds.any { id ->
                            uiState.incompleteVideoDownloads.any {
                                it.download.videoId == id &&
                                    (it.overallStatus == DownloadItemStatus.FAILED ||
                                        it.overallStatus == DownloadItemStatus.CANCELLED)
                            }
                        }
                        if (canPause) {
                            IconButton(onClick = { viewModel.pauseSelected() }) {
                                Icon(
                                    imageVector = Icons.Filled.Pause,
                                    contentDescription = stringResource(R.string.download_action_pause_all),
                                )
                            }
                        }
                        if (canResume) {
                            IconButton(onClick = { viewModel.resumeSelected() }) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.download_action_resume_all),
                                )
                            }
                        }
                        if (canRetry) {
                            IconButton(onClick = { viewModel.retrySelected() }) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.download_action_retry_selected),
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.download_action_delete_selected),
                            )
                        }
                    }
                } else {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                        Text(
                            text = stringResource(R.string.downloads_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (uiState.incompleteDownloadCount > 0) {
                            IconButton(onClick = { showRemoveIncompleteDialog = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.remove_incomplete_downloads),
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            VideosDownloadsList(
                videos = uiState.downloadedVideos,
                incompleteDownloads = uiState.incompleteVideoDownloads,
                progressMap = uiState.downloadProgressMap,
                mergingVideoIds = uiState.mergingVideoIds,
                isRefreshing = uiState.isScanning,
                selectedIds = selectedIds,
                onRefresh = { viewModel.rescan() },
                onVideoClick = { videos, index -> onVideoClick(videos, index) },
                onDeleteClick = { id -> requestDelete(id) },
                onPauseClick = { id -> viewModel.pauseVideoDownload(id) },
                onResumeClick = { id -> viewModel.resumeVideoDownload(id) },
                onRetryClick = { id -> viewModel.retryDownload(id) },
                onToggleSelect = { id -> viewModel.toggleSelection(id) },
                onHomeClick = onHomeClick,
            )
        }
    }

    if (showRemoveIncompleteDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveIncompleteDialog = false },
            title = { Text(stringResource(R.string.remove_incomplete_downloads)) },
            text = {
                Text(
                    stringResource(
                        R.string.remove_incomplete_downloads_message,
                        uiState.incompleteDownloadCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveIncompleteDialog = false
                        viewModel.removeIncompleteDownloads()
                    },
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveIncompleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════
// VIDEO DOWNLOADS LIST
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideosDownloadsList(
    videos: List<DownloadedVideo>,
    incompleteDownloads: List<DownloadWithItems>,
    progressMap: Map<String, Float>,
    mergingVideoIds: Set<String>,
    isRefreshing: Boolean,
    selectedIds: Set<String>,
    onRefresh: () -> Unit,
    onVideoClick: (List<DownloadedVideo>, Int) -> Unit,
    onDeleteClick: (String) -> Unit,
    onPauseClick: (String) -> Unit,
    onResumeClick: (String) -> Unit,
    onRetryClick: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (videos.isEmpty() && incompleteDownloads.isEmpty()) {
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
            ) {
                EmptyDownloadsState(
                    type = stringResource(R.string.tab_videos),
                    icon = Icons.Outlined.VideoLibrary,
                    onHomeClick = onHomeClick,
                )
            }
        }
    } else {
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (incompleteDownloads.isNotEmpty()) {
                    item(key = "section_active") {
                        Text(
                            text = stringResource(R.string.section_incomplete_downloads),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 6.dp,
                                ),
                        )
                    }
                    items(
                        items = incompleteDownloads,
                        key = { "active_${it.download.videoId}" },
                    ) { dl ->
                        ActiveVideoDownloadCard(
                            download = dl,
                            progressMap = progressMap,
                            isMerging = dl.download.videoId in mergingVideoIds,
                            isSelected = dl.download.videoId in selectedIds,
                            selectionMode = selectedIds.isNotEmpty(),
                            onPauseClick = { onPauseClick(dl.download.videoId) },
                            onResumeClick = { onResumeClick(dl.download.videoId) },
                            onRetryClick = { onRetryClick(dl.download.videoId) },
                            onDeleteClick = { onDeleteClick(dl.download.videoId) },
                            onToggleSelect = { onToggleSelect(dl.download.videoId) },
                            modifier =
                                Modifier.animateItem(
                                    fadeInSpec = tween(300, easing = EaseOutCubic),
                                    fadeOutSpec = tween(200, easing = EaseInCubic),
                                    placementSpec =
                                        spring(
                                            dampingRatio = 0.8f,
                                            stiffness = Spring.StiffnessLow,
                                        ),
                                ),
                        )
                    }
                }
                if (videos.isNotEmpty()) {
                    item(key = "section_completed") {
                        Text(
                            text = stringResource(R.string.section_completed),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 6.dp,
                                ),
                        )
                    }
                }
                itemsIndexed(
                    items = videos,
                    key = { _, video -> video.video.id },
                ) { index, video ->
                    VideoDownloadCard(
                        video = video,
                        isSelected = video.video.id in selectedIds,
                        selectionMode = selectedIds.isNotEmpty(),
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                onToggleSelect(video.video.id)
                            } else {
                                onVideoClick(videos, index)
                            }
                        },
                        onLongClick = { onToggleSelect(video.video.id) },
                        onDeleteClick = { onDeleteClick(video.video.id) },
                        modifier =
                            Modifier.animateItem(
                                fadeInSpec = tween(300, easing = EaseOutCubic),
                                fadeOutSpec = tween(200, easing = EaseInCubic),
                                placementSpec =
                                    spring(
                                        dampingRatio = 0.8f,
                                        stiffness = Spring.StiffnessLow,
                                    ),
                            ),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// VIDEO CARD
// ═══════════════════════════════════════════════════════

@Composable
private fun VideoDownloadCard(
    video: DownloadedVideo,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deleteDesc =
        stringResource(
            R.string.cd_delete_download,
            video.video.title,
        )
    val selectDesc =
        stringResource(
            R.string.download_action_select,
            video.video.title,
        )
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    role = Role.Button,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Icon(
                imageVector =
                    if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = selectDesc,
                tint =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Box(
            modifier =
                Modifier
                    .width(152.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = video.video.thumbnailUrl,
                contentDescription =
                    stringResource(
                        R.string.cd_video_thumbnail,
                        video.video.title,
                    ),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            Surface(
                color =
                    MaterialTheme.colorScheme.inverseSurface
                        .copy(alpha = 0.85f),
                shape = RoundedCornerShape(4.dp),
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
            ) {
                Text(
                    text = formatDuration(video.video.duration),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    modifier =
                        Modifier.padding(
                            horizontal = 4.dp,
                            vertical = 2.dp,
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.video.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = video.video.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open)) },
                    onClick = {
                        menuExpanded = false
                        onClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = {
                        menuExpanded = false
                        onDeleteClick()
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription = deleteDesc
                        },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// ACTIVE DOWNLOAD CARD (in-progress)
// ═══════════════════════════════════════════════════════

@Composable
private fun ActiveVideoDownloadCard(
    download: DownloadWithItems,
    progressMap: Map<String, Float>,
    isMerging: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = (progressMap[download.download.videoId] ?: download.progress).coerceIn(0f, 1f)
    val pct = (progress * 100).toInt()
    val deleteDesc =
        stringResource(
            R.string.cd_delete_download,
            download.download.title,
        )
    val selectDesc =
        stringResource(
            R.string.download_action_select,
            download.download.title,
        )
    val status = download.overallStatus
    val isTerminal = status == DownloadItemStatus.FAILED || status == DownloadItemStatus.CANCELLED
    val isPaused = status == DownloadItemStatus.PAUSED
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (selectionMode) onToggleSelect() else Unit
                    },
                    onLongClick = onToggleSelect,
                    role = Role.Button,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Icon(
                imageVector =
                    if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = selectDesc,
                tint =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Box(
            modifier =
                Modifier
                    .width(152.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = download.download.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Dimming overlay
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.50f)),
            )
            // Red progress fill from left
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(Color(0xFFCD2027).copy(alpha = 0.35f)),
            )
            // Percentage label centered
            Text(
                text = "$pct%",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
            // Thin progress bar at the bottom edge
            LinearProgressIndicator(
                progress = { progress },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = download.download.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = download.download.uploader,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            val statusText =
                when {
                    isMerging -> {
                        stringResource(R.string.download_merging_audio_video)
                    }

                    else -> {
                        when (status) {
                            DownloadItemStatus.PENDING -> stringResource(R.string.download_status_queued)
                            DownloadItemStatus.DOWNLOADING -> "$pct% \u00b7 ${stringResource(R.string.download_status_downloading)}"
                            DownloadItemStatus.PAUSED -> "$pct% \u00b7 ${stringResource(R.string.download_status_paused)}"
                            DownloadItemStatus.FAILED -> stringResource(R.string.download_status_failed)
                            DownloadItemStatus.CANCELLED -> stringResource(R.string.download_status_cancelled)
                            else -> "$pct%"
                        }
                    }
                }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (!isMerging && !isTerminal) {
            IconButton(
                onClick = if (isPaused) onResumeClick else onPauseClick,
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription =
                        if (isPaused) {
                            stringResource(R.string.resume)
                        } else {
                            stringResource(R.string.pause)
                        },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (!isMerging && isTerminal) {
            IconButton(onClick = onRetryClick) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.retry),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (!isTerminal) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isPaused) R.string.resume else R.string.pause)) },
                        onClick = {
                            menuExpanded = false
                            if (isPaused) onResumeClick() else onPauseClick()
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.retry)) },
                        onClick = {
                            menuExpanded = false
                            onRetryClick()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = {
                        menuExpanded = false
                        onDeleteClick()
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription = deleteDesc
                        },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// EMPTY STATE
// ═══════════════════════════════════════════════════════

@Composable
private fun EmptyDownloadsState(
    type: String,
    icon: ImageVector,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400, easing = EaseOutCubic)),
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color =
                    MaterialTheme.colorScheme.surfaceContainerHighest
                        .copy(alpha = 0.6f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint =
                            MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = 0.4f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text =
                    stringResource(
                        R.string.empty_offline_title,
                        type,
                    ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text =
                    stringResource(
                        R.string.empty_offline_body,
                        type,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )

            Spacer(modifier = Modifier.height(36.dp))

            FilledTonalButton(
                onClick = onHomeClick,
                shape = RoundedCornerShape(12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(48.dp),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor =
                            MaterialTheme.colorScheme
                                .primary,
                        contentColor =
                            MaterialTheme.colorScheme
                                .onPrimary,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.action_go_to_home),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
