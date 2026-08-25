package com.omersusin.pitube.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omersusin.pitube.R
import com.omersusin.pitube.ui.lyrics.SyncedLyricsView
import com.omersusin.pitube.ui.screens.player.LyricsUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowLyricsBottomSheet(
    videoId: String,
    lyricsState: LyricsUiState,
    currentPosition: Long = 0L,
    onLyricsLineClick: (Long) -> Unit = {},
    onRequestLyrics: () -> Unit = {},
    onRefreshLyrics: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    expandedHeight: Dp? = null,
    collapsedHeight: Dp = 0.dp,
    enableVerticalDismiss: Boolean = true,
    onSheetProgressChange: (Float) -> Unit = {},
    onSwipeNextTrack: (() -> Unit)? = null,
    onSwipePrevTrack: (() -> Unit)? = null,
    translations: Map<Long, String> = emptyMap(),
    onPickedManualLyrics: (String) -> Unit = {},
    initialSearchTitle: String = "",
    initialSearchArtist: String = "",
    onManualSearch: ((title: String, artist: String, onResult: (List<Pair<String, String>>) -> Unit) -> Unit)? = null,
    showPlayPauseControl: Boolean = false,
    isPlaying: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val sheetExpandedHeight = expandedHeight ?: (configuration.screenHeightDp.dp * 0.75f)
    val expandedHeightPx = with(density) { sheetExpandedHeight.toPx() }
    val collapsedHeightPx = with(density) { collapsedHeight.toPx() }.coerceIn(0f, expandedHeightPx)
    val sheetProgressRangePx = (expandedHeightPx - collapsedHeightPx).coerceAtLeast(1f)
    val dismissThresholdPx = collapsedHeightPx + sheetProgressRangePx * 0.55f
    val sheetHeightPx = remember { Animatable(0f) }
    var isAnimatingOut by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showOffsetDialog by remember { mutableStateOf(false) }
    val prefs = remember { com.omersusin.pitube.data.local.PlayerPreferences(LocalContext.current) }
    val currentSyncOffsetMs by prefs.lyricsSyncOffsetMs.collectAsState(initial = 0)
    val sheetProgress = if (expandedHeightPx > 0f) ((sheetHeightPx.value - collapsedHeightPx) / sheetProgressRangePx).coerceIn(0f, 1f) else 0f
    SideEffect { onSheetProgressChange(sheetProgress) }

    fun animateToExpanded() {
        if (!enableVerticalDismiss) { coroutineScope.launch { sheetHeightPx.snapTo(expandedHeightPx) }; return }
        coroutineScope.launch { sheetHeightPx.animateTo(expandedHeightPx, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)) }
    }
    fun animateToDismiss() {
        if (isAnimatingOut) return
        if (!enableVerticalDismiss) { latestOnDismiss(); return }
        isAnimatingOut = true
        coroutineScope.launch { sheetHeightPx.animateTo(collapsedHeightPx, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)); latestOnDismiss() }
    }

    LaunchedEffect(expandedHeightPx, collapsedHeightPx) {
        isAnimatingOut = false
        sheetHeightPx.updateBounds(lowerBound = collapsedHeightPx, upperBound = expandedHeightPx)
        if (!enableVerticalDismiss) { sheetHeightPx.snapTo(expandedHeightPx); return@LaunchedEffect }
        if (sheetHeightPx.value == 0f || sheetHeightPx.value < collapsedHeightPx) sheetHeightPx.snapTo(collapsedHeightPx)
        sheetHeightPx.animateTo(expandedHeightPx, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
    }
    // Re-request whenever the playing video changes — keyed on videoId, not
    // Unit (a sheet left open across a track switch must swap lyrics).
    LaunchedEffect(videoId) { onRequestLyrics() }

    if (showSearchDialog && onManualSearch != null) {
        LyricsSearchDialog(
            initialTitle = initialSearchTitle,
            initialArtist = initialSearchArtist,
            onDismiss = { showSearchDialog = false },
            onSearch = { title, artist, callback -> onManualSearch(title, artist, callback) },
            onPicked = onPickedManualLyrics,
        )
    }
    if (showOffsetDialog) {
        LyricsSyncOffsetDialog(
            currentOffsetMs = currentSyncOffsetMs,
            onDismiss = { showOffsetDialog = false },
            onApply = { v ->
                showOffsetDialog = false
                coroutineScope.launch { prefs.setLyricsSyncOffsetMs(v) }
            },
        )
    }
    BackHandler(onBack = ::animateToDismiss)

    val headerDragModifier = if (enableVerticalDismiss) {
        Modifier.pointerInput(expandedHeightPx, collapsedHeightPx, dismissThresholdPx, isAnimatingOut) {
            val velocityTracker = VelocityTracker()
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    if (isAnimatingOut) return@detectVerticalDragGestures
                    velocityTracker.addPointerInputChange(change)
                    coroutineScope.launch { sheetHeightPx.snapTo((sheetHeightPx.value - dragAmount).coerceIn(collapsedHeightPx, expandedHeightPx)) }
                },
                onDragCancel = { velocityTracker.resetTracking(); if (!isAnimatingOut) animateToExpanded() },
                onDragEnd = {
                    val velocityY = velocityTracker.calculateVelocity().y; velocityTracker.resetTracking()
                    when { velocityY > 1200f || sheetHeightPx.value < dismissThresholdPx -> animateToDismiss(); else -> animateToExpanded() }
                },
            )
        }
    } else Modifier

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(modifier = Modifier.fillMaxWidth().height(with(density) { sheetHeightPx.value.toDp() }), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).then(headerDragModifier), contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() }
                Row(modifier = Modifier.fillMaxWidth().then(headerDragModifier).padding(horizontal = 16.dp).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::animateToDismiss, modifier = Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close)) }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.lyrics), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp), color = MaterialTheme.colorScheme.onSurface)
                        if ((lyricsState is LyricsUiState.Synced && lyricsState.lines.isNotEmpty()) || (lyricsState is LyricsUiState.SyncedWithWords && lyricsState.lines.isNotEmpty())) {
                            Text(text = stringResource(R.string.lyrics_synced), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (onRefreshLyrics != null && lyricsState !is LyricsUiState.Loading && lyricsState !is LyricsUiState.Idle) {
                        IconButton(onClick = onRefreshLyrics, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh lyrics",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (onManualSearch != null) {
                        IconButton(onClick = { showSearchDialog = true }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.search),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    IconButton(onClick = { showOffsetDialog = true }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = "Sync offset",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (showPlayPauseControl) {
                        IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
                when (lyricsState) {
                    LyricsUiState.Idle, LyricsUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    LyricsUiState.Unavailable -> {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(text = stringResource(R.string.lyrics_not_available), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                    is LyricsUiState.Synced -> {
                        val injected = remember(lyricsState) { lyricsState.lines.map { tl -> com.omersusin.pitube.data.lyrics.LrcLine(tl.startMs, tl.text) } }
                        SyncedLyricsView(lyricsResult = com.omersusin.pitube.data.lyrics.LyricsFetchResult.Success(injected), currentPositionMs = currentPosition, onSeekTo = onLyricsLineClick, onSwipeNext = onSwipeNextTrack, onSwipePrev = onSwipePrevTrack, translations = translations, isPlaying = isPlaying, modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                    is LyricsUiState.SyncedWithWords -> {
                        val withWords = remember(lyricsState) { lyricsState.lines.map { tl -> com.omersusin.pitube.data.lyrics.LrcLine(tl.startMs, tl.text, lyricsState.wordSpans[tl.startMs].orEmpty()) } }
                        SyncedLyricsView(lyricsResult = com.omersusin.pitube.data.lyrics.LyricsFetchResult.Success(withWords), currentPositionMs = currentPosition, onSeekTo = onLyricsLineClick, onSwipeNext = onSwipeNextTrack, onSwipePrev = onSwipePrevTrack, translations = translations, isPlaying = isPlaying, modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                    is LyricsUiState.Plain -> {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp), contentAlignment = Alignment.TopStart) {
                            Text(text = lyricsState.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}


/**
 * Manual lyrics search: queries every enabled provider for the typed
 * title/artist and lets the user pick a candidate. Picked LRC is cached and
 * swapped into the visible lyrics immediately.
 */
@Composable
private fun LyricsSearchDialog(
    initialTitle: String,
    initialArtist: String,
    onDismiss: () -> Unit,
    onSearch: (title: String, artist: String, onResult: (List<Pair<String, String>>) -> Unit) -> Unit,
    onPicked: (String) -> Unit,
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var artist by rememberSaveable { mutableStateOf(initialArtist) }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        title = { Text(stringResource(R.string.search)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.song_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(stringResource(R.string.artist)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        searching = true
                        results = emptyList()
                        onSearch(title, artist) { found ->
                            searching = false
                            results = found
                        }
                    },
                    enabled = !searching && title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (searching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.search))
                }
                results.forEach { (provider, lrc) ->
                    val preview = lrc.lineSequence()
                        .firstOrNull { !it.startsWith("[") }?.take(48)
                        ?: provider
                    Surface(
                        onClick = { onPicked(lrc); onDismiss() },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(preview, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(com.omersusin.pitube.data.lyrics.LyricsProviderRegistry.displayName(provider), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    )
}

/**
 * Live sync-offset dialog ported from Metrolist/vivi's ShowOffsetDialog:
 * slider across ±3000 ms with 50 ms steps; applies instantly through the
 * shared lyricsSyncOffsetMs preference (SyncedLyricsView re-collects it).
 */
@Composable
private fun LyricsSyncOffsetDialog(
    currentOffsetMs: Int,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    var value by rememberSaveable(currentOffsetMs) { mutableIntStateOf((currentOffsetMs / 50) * 50) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lyrics sync offset") },
        text = {
            Column {
                Text(
                    text = "${if (value >= 0) "+" else ""}${value} ms",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = value.toFloat(),
                    onValueChange = { v -> value = ((v / 50).toInt() * 50) },
                    valueRange = -3000f..3000f,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { value = (value - 50).coerceAtLeast(-3000) }) {
                        Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = "-50ms")
                    }
                    TextButton(onClick = { value = 0 }) { Text("Reset") }
                    IconButton(onClick = { value = (value + 50).coerceAtMost(3000) }) {
                        Icon(Icons.Outlined.AddCircleOutline, contentDescription = "+50ms")
                    }
                }
                Text(
                    "Positive values make lyrics highlight earlier",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(value) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
