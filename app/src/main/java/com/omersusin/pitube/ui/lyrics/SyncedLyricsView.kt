package com.omersusin.pitube.ui.lyrics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omersusin.pitube.data.local.LyricsAnimationStyle
import com.omersusin.pitube.data.local.LyricsTextPosition
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.lyrics.LrcLine
import com.omersusin.pitube.data.lyrics.LyricsFetchResult
import com.omersusin.pitube.ui.lyrics.engine.*

@Composable
fun SyncedLyricsView(
    lyricsResult: LyricsFetchResult,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onSwipeNext: (() -> Unit)? = null,
    onSwipePrev: (() -> Unit)? = null,
    translations: Map<Long, String> = emptyMap(),
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val animName by prefs.lyricsAnimation.collectAsState(initial = LyricsAnimationStyle.VIVIMUSIC_FLUID.name)
    val anim = remember(animName) { LyricsAnimationStyle.fromString(animName) }
    val glow by prefs.lyricsGlowEnabled.collectAsState(initial = true)
    val textPosName by prefs.lyricsTextPosition.collectAsState(initial = LyricsTextPosition.CENTER.name)
    val textPos = remember(textPosName) { LyricsTextPosition.fromString(textPosName) }
    val textSize by prefs.lyricsTextSize.collectAsState(initial = 20f)
    val spacing by prefs.lyricsLineSpacing.collectAsState(initial = 1.4f)
    val blurVal by prefs.lyricsStandardBlur.collectAsState(initial = 0f)
    val autoScroll by prefs.lyricsAutoScroll.collectAsState(initial = true)
    val swipeEnabled by prefs.lyricsSwipeToChangeSong.collectAsState(initial = false)
    val changeOnClick by prefs.lyricsChangeOnClick.collectAsState(initial = false)
    val syncOffsetMs by prefs.lyricsSyncOffsetMs.collectAsState(initial = 0)
    val noteSize by prefs.lyricsNoteSize.collectAsState(initial = 48f)

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (lyricsResult) {
            is LyricsFetchResult.Success -> LyricsContent(
                lines = lyricsResult.lines, currentPositionMs = currentPositionMs + syncOffsetMs, onSeekTo = onSeekTo,
                anim = anim, glow = glow, textSize = textSize, spacing = spacing, blurVal = blurVal,
                autoScroll = autoScroll, textPos = textPos, swipeEnabled = swipeEnabled, onSwipeNext = onSwipeNext, onSwipePrev = onSwipePrev,
                changeOnClick = changeOnClick, translations = translations, isPlaying = isPlaying, noteSizeDp = noteSize
            )
            is LyricsFetchResult.Plain -> Text(
                text = lyricsResult.text,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp),
            )
            is LyricsFetchResult.NotFound -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Rounded.MusicOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp)); Text("No lyrics available", style = MaterialTheme.typography.titleMedium)
            }
            is LyricsFetchResult.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Rounded.WifiOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp)); Text(lyricsResult.message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun LyricsContent(
    lines: List<LrcLine>, currentPositionMs: Long, onSeekTo: (Long) -> Unit,
    anim: LyricsAnimationStyle, glow: Boolean, textSize: Float, spacing: Float, blurVal: Float,
    autoScroll: Boolean, textPos: LyricsTextPosition, swipeEnabled: Boolean,
    onSwipeNext: (() -> Unit)?, onSwipePrev: (() -> Unit)?,
    changeOnClick: Boolean,
    translations: Map<Long, String>,
    isPlaying: Boolean,
    noteSizeDp: Float
) {
    // Frame-anchored dead reckoning: the external position ticker fires every
    // 250ms-1s; between ticks an interpolated clock advances every frame so
    // word fills never lag. Divergence >350ms means seek or fresh tick — snap.
    val positionState = rememberUpdatedState(currentPositionMs)
    val smoothPosition = remember { mutableLongStateOf(currentPositionMs) }
    LaunchedEffect(isPlaying) {
        var anchor = positionState.value
        var anchorAt = 0L
        withFrameNanos { now ->
            anchor = positionState.value
            anchorAt = now
            smoothPosition.longValue = anchor
        }
        while (true) {
            withFrameNanos { now ->
                val ext = positionState.value
                val predicted = anchor + (now - anchorAt) / 1_000_000L
                if (!isPlaying || kotlin.math.abs(ext - predicted) > 350L) {
                    anchor = ext
                    anchorAt = now
                    smoothPosition.longValue = ext
                } else {
                    smoothPosition.longValue = predicted
                }
            }
        }
    }

    val hasWordTimings = remember(lines) { lines.any { it.contentSpans.isNotEmpty() } }
    val currentIndex by remember(lines) {
        derivedStateOf {
            val pos = smoothPosition.longValue + if (hasWordTimings) 0L else 250L
            lines.indexOfLast { it.timeMs <= pos }
        }
    }

    val displayItems = remember(lines) { buildDisplayItems(lines) }
    val lineToDisplay = remember(displayItems) {
        buildMap {
            displayItems.forEachIndexed { i, item -> if (item is LyricsDisplayItem.Line) put(item.index, i) }
        }
    }

    val accent = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface

    fun handleTap(line: LrcLine) {
        if (changeOnClick) {
            onSeekTo(line.timeMs)
        } else {
            android.util.Log.w("SyncedLyricsView", "Lyric tap ignored — 'Change on tap' setting is off")
        }
    }

    if (anim == LyricsAnimationStyle.METRO_LYRICS) {
        MetroCanvasLayout(
            displayItems = displayItems,
            // Positions map is in DISPLAY-item space (lines + breaks); the line
            // index must be translated or every item shifts when breaks exist.
            currentDisplayIndex = lineToDisplay[currentIndex] ?: -1,
            currentLineIndex = currentIndex,
            translations = translations,
            noteSizeDp = noteSizeDp,
            positionProvider = { smoothPosition.longValue },
            autoScroll = autoScroll,
            accent = accent,
            textColor = textColor,
            textSizeSp = textSize,
            lineHeightFactor = spacing.coerceIn(1.15f, 1.5f),
            fadeTopDp = 96f,
            fadeBottomDp = 96f,
            onTapLine = { idx -> lines.getOrNull(idx)?.let(::handleTap) },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val listState = rememberLazyListState()

    // Centered smooth page-scroll (vivi performSmoothPageScroll, Lyrics.kt:886-910).
    suspend fun centerOnItem(dispIndex: Int, duration: Int = 700) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == dispIndex }
        if (info != null) {
            val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
            val center = listState.layoutInfo.viewportStartOffset + viewportHeight / 2
            val offset = (info.offset + info.size / 2) - center
            if (kotlin.math.abs(offset) > 10) {
                listState.animateScrollBy(offset.toFloat(), tween(duration, easing = FastOutSlowInEasing))
            }
        } else {
            listState.scrollToItem(dispIndex)
        }
    }

    LaunchedEffect(currentIndex, autoScroll) {
        if (!autoScroll || currentIndex < 0) return@LaunchedEffect
        try {
            lineToDisplay[currentIndex]?.let { centerOnItem(it) }
        } catch (_: Exception) {}
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val centerPadding = when (textPos) {
            LyricsTextPosition.TOP -> 24.dp
            LyricsTextPosition.BOTTOM -> {
                val hVal = maxHeight.value
                if (hVal.isFinite() && hVal > 120f) maxHeight - 120.dp else 24.dp
            }
            else -> {
                val hVal = maxHeight.value
                if (hVal.isFinite() && hVal > 0f) maxHeight / 2 else 24.dp
            }
        }.let { pad -> if (pad.value < 0f) 24.dp else pad }
        val swipeMod = if (swipeEnabled && onSwipeNext != null) Modifier.pointerInput(Unit) {
            var drag = 0f
            detectHorizontalDragGestures(onDragEnd = {
                if (drag > 120) onSwipePrev?.invoke() else if (drag < -120) onSwipeNext.invoke()
                drag = 0f
            }) { _, d -> drag += d }
        } else Modifier

        val spacingDp = when (anim) {
            LyricsAnimationStyle.LYRICS_V2_FLUID -> (24 * spacing).dp
            LyricsAnimationStyle.APPLE_MUSIC, LyricsAnimationStyle.APPLE_MUSIC_V2_LETTER -> (36 * spacing).dp
            else -> (28 * spacing).dp
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().then(swipeMod)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.15f to Color.Black,
                            0.85f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentPadding = PaddingValues(top = centerPadding, bottom = centerPadding, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(spacingDp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(displayItems, key = { i, item ->
                when (item) {
                    is LyricsDisplayItem.Line -> "${item.index}_${item.line.timeMs}"
                    is LyricsDisplayItem.Break -> "brk_${item.gap.startMs}"
                }
            }) { _, item ->
                when (item) {
                    is LyricsDisplayItem.Line -> LyricLine(
                        line = item.line,
                        lines = lines,
                        isCurrent = item.index == currentIndex,
                        isPast = item.index < currentIndex,
                        positionProvider = { smoothPosition.longValue },
                        anim = anim, glow = glow, textSize = textSize, spacing = spacing, blurVal = blurVal,
                        accent = accent, textColor = textColor,
                        onTap = { handleTap(item.line) },
                        translatedText = translations[item.line.timeMs].takeIf { item.index == currentIndex },
                    )
                    is LyricsDisplayItem.Break -> {
                        // Note appears ONLY while the instrumental is actually
                        // playing; outside the window the slot stays reserved
                        // (fixed height) but empty so scroll offsets don't jump.
                        val pos = smoothPosition.longValue
                        val noteVisible = pos >= item.gap.startMs &&
                            pos < item.gap.startMs + item.gap.durationMs
                        Box(
                            modifier = Modifier.fillMaxWidth().height(72.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (noteVisible) {
                                InstrumentalBreakItem(
                                    durationMs = item.gap.durationMs,
                                    currentPositionMs = pos,
                                    startTimeMs = item.gap.startMs,
                                    textColor = accent,
                                    inactiveAlpha = 0.35f,
                                    modifier = Modifier.size(noteSizeDp.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricLine(
    line: LrcLine,
    lines: List<LrcLine>,
    isCurrent: Boolean,
    isPast: Boolean,
    positionProvider: () -> Long,
    anim: LyricsAnimationStyle,
    glow: Boolean,
    textSize: Float,
    spacing: Float,
    blurVal: Float,
    accent: Color,
    textColor: Color,
    onTap: () -> Unit,
    translatedText: String? = null,
) {
    // Only the active line reads the ticking clock — other items stay skipped.
    val currentPositionMs = if (isCurrent) positionProvider() else -1L

    val scaleTarget = if (!isCurrent) 1f else when (anim) {
        LyricsAnimationStyle.NONE -> 1f
        LyricsAnimationStyle.FADE -> 1f
        LyricsAnimationStyle.GLOW -> 1.10f
        LyricsAnimationStyle.SLIDE -> 1.06f
        LyricsAnimationStyle.KARAOKE -> 1.14f
        LyricsAnimationStyle.APPLE_MUSIC -> 1.22f
        LyricsAnimationStyle.APPLE_MUSIC_V2_LETTER -> 1.22f
        LyricsAnimationStyle.VIVIMUSIC_FLUID -> 1.05f
        LyricsAnimationStyle.LYRICS_V2_FLUID -> 1f
        LyricsAnimationStyle.METRO_LYRICS -> 1f
    }
    val alphaTarget = if (isCurrent) 1f else when (anim) {
        LyricsAnimationStyle.NONE -> 0.55f
        LyricsAnimationStyle.FADE -> if (isPast) 0.22f else 0.38f
        LyricsAnimationStyle.GLOW -> 0.42f
        LyricsAnimationStyle.SLIDE -> 0.30f
        LyricsAnimationStyle.KARAOKE -> 0.38f
        LyricsAnimationStyle.APPLE_MUSIC -> 0.32f
        LyricsAnimationStyle.APPLE_MUSIC_V2_LETTER -> 0.32f
        LyricsAnimationStyle.VIVIMUSIC_FLUID -> 0.34f
        LyricsAnimationStyle.LYRICS_V2_FLUID -> 0.36f
        LyricsAnimationStyle.METRO_LYRICS -> 0.60f
    }
    val offsetTarget = if (isCurrent) 0f else when (anim) {
        LyricsAnimationStyle.SLIDE -> 18f
        LyricsAnimationStyle.VIVIMUSIC_FLUID -> 10f
        LyricsAnimationStyle.LYRICS_V2_FLUID -> 6f
        LyricsAnimationStyle.APPLE_MUSIC, LyricsAnimationStyle.APPLE_MUSIC_V2_LETTER -> 12f
        else -> 0f
    }
    val scale by animateFloatAsState(targetValue = scaleTarget, animationSpec = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow), label = "scale")
    val alpha by animateFloatAsState(targetValue = alphaTarget, animationSpec = spring<Float>(stiffness = Spring.StiffnessLow), label = "alpha")
    val offset by animateFloatAsState(targetValue = offsetTarget, animationSpec = spring<Float>(stiffness = Spring.StiffnessLow), label = "offset")

    val blurMod = when {
        !isCurrent && (anim == LyricsAnimationStyle.APPLE_MUSIC || anim == LyricsAnimationStyle.APPLE_MUSIC_V2_LETTER) -> Modifier.blur(10.dp)
        blurVal > 0 && !isCurrent && (anim == LyricsAnimationStyle.FADE || anim == LyricsAnimationStyle.NONE) -> Modifier.blur((blurVal * 8).dp)
        else -> Modifier
    }

    val inactiveColor = textColor.copy(alpha = 0.85f)
    val lineHeightFactor = spacing

    // High-scale styles grow the rendered layer beyond layout bounds; the extra
    // padding keeps the scaled content inside the item so LazyColumn edges
    // never clip it (APPLE/V2 were visibly cut top+bottom).
    val verticalInset = when (anim) {
        LyricsAnimationStyle.APPLE_MUSIC, LyricsAnimationStyle.APPLE_MUSIC_V2_LETTER -> 18.dp
        LyricsAnimationStyle.KARAOKE -> 12.dp
        LyricsAnimationStyle.VIVIMUSIC_FLUID -> 8.dp
        else -> 6.dp
    }

    Box(
        modifier = Modifier.fillMaxWidth()
            .graphicsLayer { translationY = offset; scaleX = scale; scaleY = scale; this.alpha = alpha }
            .then(blurMod)
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onTap() }
            .padding(vertical = verticalInset),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (anim) {
                LyricsAnimationStyle.KARAOKE -> {
                    val words = if (isCurrent) line.toEngineWords() else emptyList()
                    if (words.isNullOrEmpty()) {
                        PlainStyledLine(line, isCurrent, isPast, glow, textSize, spacing, accent, textColor)
                    } else {
                        KaraokeDrawLine(
                            lineText = line.text,
                            words = words,
                            isActiveLine = true,
                            positionProvider = positionProvider,
                            accent = accent,
                            inactiveColor = inactiveColor,
                            textSizeSp = textSize,
                            lineHeightFactor = lineHeightFactor,
                        )
                    }
                }

                LyricsAnimationStyle.VIVIMUSIC_FLUID -> ViviFluidLine(
                    line = line,
                    lineDurationMs = lineDurationMs(lines, line),
                    isActiveLine = isCurrent,
                    positionMs = currentPositionMs,
                    accent = accent,
                    inactiveColor = inactiveColor,
                    textSizeSp = textSize,
                    lineHeightFactor = lineHeightFactor,
                )

                LyricsAnimationStyle.LYRICS_V2_FLUID -> LyricsV2FillLine(
                    line = line,
                    isActiveLine = isCurrent,
                    isPast = isPast,
                    positionMs = currentPositionMs,
                    accent = accent,
                    inactiveColor = textColor,
                    inactiveAlpha = 0.45f,
                    textSizeSp = textSize,
                    lineHeightFactor = lineHeightFactor,
                )

                LyricsAnimationStyle.APPLE_MUSIC_V2_LETTER -> AppleV2LetterLine(
                    line = line,
                    isActiveLine = isCurrent,
                    positionMs = currentPositionMs,
                    accent = accent,
                    inactiveColor = inactiveColor,
                    textSizeSp = textSize,
                    lineHeightFactor = lineHeightFactor,
                )

                LyricsAnimationStyle.NONE, LyricsAnimationStyle.FADE, LyricsAnimationStyle.GLOW,
                LyricsAnimationStyle.SLIDE, LyricsAnimationStyle.APPLE_MUSIC -> {
                    val words = line.toEngineWords()
                    if (words != null && isCurrent) {
                        WordLevelSpanLine(
                            style = anim,
                            line = line,
                            isActiveLine = true,
                            positionMs = currentPositionMs,
                            accent = accent,
                            inactiveColor = inactiveColor,
                            textSizeSp = textSize,
                            lineHeightFactor = lineHeightFactor,
                        )
                    } else {
                        PlainStyledLine(line, isCurrent, isPast, glow, textSize, spacing, accent, textColor)
                    }
                }

                LyricsAnimationStyle.METRO_LYRICS -> PlainStyledLine(line, isCurrent, isPast, glow, textSize, spacing, accent, textColor)
            }

            if (isCurrent && !translatedText.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = translatedText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = (textSize * 0.62f).sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun lineDurationMs(lines: List<LrcLine>, line: LrcLine): Long {
    val nextTime = lines.getOrNull(lines.indexOf(line) + 1)?.timeMs ?: (line.timeMs + 4000L)
    return (nextTime - line.timeMs).coerceAtLeast(800L)
}

/** Fallback rendering for styles without word timings or engines needing none. */
@Composable
private fun PlainStyledLine(
    line: LrcLine,
    isCurrent: Boolean,
    isPast: Boolean,
    glow: Boolean,
    textSize: Float,
    spacing: Float,
    accent: Color,
    textColor: Color,
) {
    if (isCurrent) {
        Text(
            text = line.text,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = (textSize + 2).sp,
                lineHeight = (textSize * spacing).sp,
                fontWeight = FontWeight.ExtraBold,
                shadow = if (glow) androidx.compose.ui.graphics.Shadow(accent.copy(alpha = 0.45f), blurRadius = 18f) else null,
            ),
            color = accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Text(
            text = line.text,
            fontSize = textSize.sp,
            lineHeight = (textSize * spacing).sp,
            fontWeight = FontWeight.Medium,
            color = if (isPast) textColor.copy(alpha = 0.55f) else textColor.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
