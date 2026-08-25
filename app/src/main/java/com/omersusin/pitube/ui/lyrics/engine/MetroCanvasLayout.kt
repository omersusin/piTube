package com.omersusin.pitube.ui.lyrics.engine

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ANCHOR_RATIO = 0.35f
private const val STAGGER_MS_PER_DISTANCE = 40
private const val STAGGER_MS_MAX = 300
private const val LINE_FALLBACK_HEIGHT_DP = 120f
private const val BREAK_HEIGHT_DP = 72f
private const val LINE_GAP_DP = 16f

/**
 * Custom scroll-surface for METRO_LYRICS, ported from vivi Lyrics.kt:1145-1398:
 * hand-computed Y-position map around the active index, staggered tween
 * offsets, custom drag/fling with clamped overscroll and auto-centering.
 */
@Composable
fun MetroCanvasLayout(
    displayItems: List<LyricsDisplayItem>,
    currentIndex: Int,
    positionProvider: () -> Long,
    autoScroll: Boolean,
    accent: Color,
    textColor: Color,
    textSizeSp: Float,
    lineHeightFactor: Float,
    fadeTopDp: Float,
    fadeBottomDp: Float,
    onTapLine: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxHeightPx = constraints.maxHeight.toFloat()
        val anchorY = maxHeightPx * ANCHOR_RATIO
        val scope = rememberCoroutineScope()

        val activeIndex = currentIndex.coerceAtLeast(0)

        // SnapshotStateMap: layout-measured heights invalidate composition, which
        // re-runs the position maps below — mirrors vivi's toMap() remember keys.
        val itemHeights = remember { mutableStateMapOf<Int, Int>() }

        fun itemFallbackHeight(item: LyricsDisplayItem): Float =
            with(density) {
                (if (item is LyricsDisplayItem.Break) BREAK_HEIGHT_DP else LINE_FALLBACK_HEIGHT_DP).dp.toPx()
            }

        val positions = remember(displayItems.size, activeIndex, itemHeights.toMap()) {
            val map = mutableMapOf<Int, Float>()
            if (displayItems.isEmpty()) return@remember map
            map[activeIndex] = 0f
            var y = 0f
            for (i in activeIndex - 1 downTo 0) {
                val h = itemHeights[i]?.toFloat() ?: itemFallbackHeight(displayItems[i])
                val noGap = i > 0 && displayItems[i - 1] is LyricsDisplayItem.Break
                y -= h + if (noGap) 0f else with(density) { LINE_GAP_DP.dp.toPx() }
                map[i] = y
            }
            y = 0f
            for (i in activeIndex until displayItems.size - 1) {
                val nextNoGap = displayItems[i + 1] is LyricsDisplayItem.Break ||
                    (displayItems.getOrNull(i) is LyricsDisplayItem.Break)
                val h = itemHeights[i]?.toFloat() ?: itemFallbackHeight(displayItems[i])
                y += h + if (nextNoGap) 0f else with(density) { LINE_GAP_DP.dp.toPx() }
                map[i + 1] = y
            }
            map
        }

        val totalBelow = remember(displayItems.size, activeIndex, itemHeights.toMap()) {
            (activeIndex until displayItems.size - 1).sumOf { i ->
                (itemHeights[i]?.toFloat() ?: itemFallbackHeight(displayItems[i])).toDouble() +
                    (if (displayItems.getOrNull(i + 1) !is LyricsDisplayItem.Break) (with(density) { LINE_GAP_DP.dp.toPx() }).toDouble() else 0.0)
            }.toFloat()
        }
        val totalAbove = remember(displayItems.size, activeIndex, itemHeights.toMap()) {
            (0 until activeIndex).sumOf { i ->
                (itemHeights[i]?.toFloat() ?: itemFallbackHeight(displayItems[i])).toDouble() +
                    (if (displayItems.getOrNull(i) !is LyricsDisplayItem.Break) (with(density) { LINE_GAP_DP.dp.toPx() }).toDouble() else 0.0)
            }.toFloat()
        }

        val maxOffset = maxHeightPx - with(density) { 150.dp.toPx() } - anchorY + totalAbove
        val minOffset = with(density) { 100.dp.toPx() } - anchorY - totalBelow -
            (itemHeights[displayItems.lastIndex]?.toFloat() ?: itemFallbackHeight(displayItems.lastOrNull() ?: LyricsDisplayItem.Break(InstrumentalGap(0, 0))))

        val clampMin = minOf(minOffset, maxOffset)
        val clampMax = maxOf(minOffset, maxOffset)

        var userManualOffset by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(clampMin, clampMax) {
            if (userManualOffset < clampMin || userManualOffset > clampMax) {
                userManualOffset = userManualOffset.coerceIn(clampMin, clampMax)
            }
        }

        // Auto-scroll returns manual drift to zero.
        LaunchedEffect(autoScroll, displayItems.size) {
            if (autoScroll) {
                val start = userManualOffset
                if (abs(start) < 1f) {
                    userManualOffset = 0f
                    return@LaunchedEffect
                }
                val anim = Animatable(start)
                var lastValue = start
                anim.animateTo(0f, tween((abs(start) / 4f).toInt().coerceIn(200, 600), easing = FastOutSlowInEasing)) {
                    userManualOffset += (value - lastValue)
                    lastValue = value
                }
                userManualOffset = 0f
            }
        }

        val isInitialLayout = remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(50)
            isInitialLayout.value = false
        }

        var flingJob by remember { mutableStateOf<Job?>(null) }
        val velocityTracker = VelocityTracker()
        val decaySpec = androidx.compose.animation.core.exponentialDecay<Float>(frictionMultiplier = 1.2f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            (fadeTopDp / size.height).coerceIn(0f, 0.5f) to Color.Black,
                            1f - (fadeBottomDp / size.height).coerceIn(0f, 0.5f) to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .pointerInput(displayItems.size, clampMin, clampMax) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        flingJob?.cancel()
                        velocityTracker.resetTracking()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        var lastY = down.position.y
                        verticalDrag(down.id) { change ->
                            val dragAmount = change.position.y - lastY
                            lastY = change.position.y
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            val newOffset = userManualOffset + dragAmount
                            if (newOffset in clampMin..clampMax) {
                                userManualOffset = newOffset
                                change.consume()
                            } else {
                                val overscroll = if (newOffset < clampMin) newOffset - clampMin else newOffset - clampMax
                                val friction = 0.35f / (1f + abs(overscroll) / 300f)
                                userManualOffset = (userManualOffset + dragAmount * friction).coerceIn(clampMin - 150f, clampMax + 150f)
                                change.consume()
                            }
                        }

                        val velocity = velocityTracker.calculateVelocity().y
                        if (abs(velocity) > 100f) {
                            flingJob = scope.launch {
                                val animState = AnimationState(initialValue = userManualOffset, initialVelocity = velocity)
                                animState.animateDecay(decaySpec) {
                                    if (value in clampMin..clampMax) {
                                        userManualOffset = value
                                    } else {
                                        val target = value.coerceIn(clampMin, clampMax)
                                        cancelAnimation()
                                        launch {
                                            Animatable(userManualOffset).animateTo(target, spring()) {
                                                userManualOffset = value
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (userManualOffset < clampMin || userManualOffset > clampMax) {
                            val target = userManualOffset.coerceIn(clampMin, clampMax)
                            flingJob = scope.launch {
                                Animatable(userManualOffset).animateTo(target, tween(300, easing = FastOutSlowInEasing)) {
                                    userManualOffset = value
                                }
                            }
                        }
                    }
                }
        ) {
            displayItems.forEachIndexed { listIndex, item ->
                key(item) {
                    val distance = abs(listIndex - activeIndex)
                    val targetOffset = anchorY + (positions[listIndex] ?: ((listIndex - activeIndex) * with(density) { LINE_FALLBACK_HEIGHT_DP.dp.toPx() }))

                    val animatedOffset by animateFloatAsState(
                        targetValue = targetOffset,
                        animationSpec = if (isInitialLayout.value || !autoScroll) snap()
                        else tween(750, (distance * STAGGER_MS_PER_DISTANCE).coerceAtMost(STAGGER_MS_MAX), FastOutSlowInEasing),
                        label = "metroStagger_$listIndex",
                    )

                    val breakVisible = item is LyricsDisplayItem.Break &&
                        positionProvider() >= item.gap.startMs && positionProvider() <= item.gap.startMs + item.gap.durationMs

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .layout { measurable, mconstraints ->
                                val placeable = measurable.measure(mconstraints.copy(maxHeight = Constraints.Infinity))
                                itemHeights[listIndex] = placeable.height
                                layout(placeable.width, 0) { placeable.place(0, 0) }
                            }
                            .offset { IntOffset(0, (animatedOffset + userManualOffset).roundToInt()) },
                    ) {
                        when (item) {
                            is LyricsDisplayItem.Line -> MetroLineItem(
                                index = item.index,
                                line = item.line,
                                isCurrent = item.index == currentIndex,
                                distance = distance,
                                autoScrollActive = autoScroll,
                                positionProvider = positionProvider,
                                accent = accent,
                                textColor = textColor,
                                textSizeSp = textSizeSp,
                                lineHeightFactor = lineHeightFactor,
                                onTap = { onTapLine(item.index) },
                            )
                            is LyricsDisplayItem.Break -> {
                                if (autoScroll && breakVisible) {
                                    InstrumentalBreakItem(
                                        durationMs = item.gap.durationMs,
                                        currentPositionMs = positionProvider(),
                                        startTimeMs = item.gap.startMs,
                                        textColor = accent,
                                        inactiveAlpha = 0.35f,
                                        modifier = Modifier.size(48.dp).wrapContentSize(Alignment.Center),
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

@Composable
private fun MetroLineItem(
    index: Int,
    line: com.omersusin.pitube.data.lyrics.LrcLine,
    isCurrent: Boolean,
    distance: Int,
    autoScrollActive: Boolean,
    positionProvider: () -> Long,
    accent: Color,
    textColor: Color,
    textSizeSp: Float,
    lineHeightFactor: Float,
    onTap: () -> Unit,
) {
    val baseAlpha = when {
        !autoScrollActive -> 0.2f
        isCurrent -> 1f
        else -> when (distance) {
            1, 2 -> 0.2f
            3 -> 0.15f
            4 -> 0.1f
            else -> 0.08f
        }
    }
    val words = if (isCurrent) line.toEngineWords() else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onTap() }
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        if (words != null) {
            MetroWordLevelLine(
                mainText = line.text,
                words = words,
                isActiveLine = true,
                positionProvider = positionProvider,
                accent = accent,
                baseColor = textColor.copy(alpha = baseAlpha),
                focusedAlpha = 0.3f,
                textSizeSp = textSizeSp,
                lineHeightFactor = lineHeightFactor,
                textAlign = TextAlign.Start,
            )
        } else {
            Text(
                text = line.text,
                fontSize = textSizeSp.sp,
                fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Medium,
                lineHeight = (textSizeSp * lineHeightFactor).sp,
                color = if (isCurrent) accent else textColor.copy(alpha = baseAlpha),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
