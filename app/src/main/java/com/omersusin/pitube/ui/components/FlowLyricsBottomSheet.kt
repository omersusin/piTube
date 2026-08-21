package com.omersusin.pitube.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.omersusin.pitube.data.lyrics.LrcContentSpan
import com.omersusin.pitube.ui.lyrics.SyncedLyricsView
import com.omersusin.pitube.ui.screens.player.LyricsUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowLyricsBottomSheet(
    lyricsState: LyricsUiState,
    currentPosition: Long = 0L,
    onLyricsLineClick: (Long) -> Unit = {},
    onRequestLyrics: () -> Unit = {},
    onDismiss: () -> Unit,
    expandedHeight: Dp? = null,
    collapsedHeight: Dp = 0.dp,
    enableVerticalDismiss: Boolean = true,
    onSheetProgressChange: (Float) -> Unit = {},
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
    LaunchedEffect(Unit) { onRequestLyrics() }
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
                        if (lyricsState is LyricsUiState.Synced && lyricsState.lines.isNotEmpty()) {
                            Text(text = stringResource(R.string.lyrics_synced), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        val mapped = remember(lyricsState) {
                            lyricsState.lines.map { tl ->
                                com.omersusin.pitube.data.lyrics.LrcLine(tl.startMs, tl.text, emptyList())
                            }
                        }
                        val injected = remember(mapped, lyricsState) {
                            val hasWordSpans = mapped.any { it.contentSpans.isNotEmpty() }
                            if (hasWordSpans) mapped else mapped
                        }
                        SyncedLyricsView(
                            lyricsResult = com.omersusin.pitube.data.lyrics.LyricsFetchResult.Success(injected),
                            currentPositionMs = currentPosition,
                            onSeekTo = onLyricsLineClick,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
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
