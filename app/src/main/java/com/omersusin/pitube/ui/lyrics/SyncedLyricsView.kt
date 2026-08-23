package com.omersusin.pitube.ui.lyrics

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
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

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (lyricsResult) {
            is LyricsFetchResult.Success -> LyricsContent(
                lines = lyricsResult.lines, currentPositionMs = currentPositionMs + syncOffsetMs, onSeekTo = onSeekTo,
                anim = anim, glow = glow, textSize = textSize, spacing = spacing, blurVal = blurVal,
                autoScroll = autoScroll, textPos = textPos, swipeEnabled = swipeEnabled, onSwipeNext = onSwipeNext, onSwipePrev = onSwipePrev,
                changeOnClick = changeOnClick, translations = translations, isPlaying = isPlaying
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
    isPlaying: Boolean
) {
    val listState = rememberLazyListState()
    // VIVI-STYLE DEAD RECKONING (port of vivi Lyrics.kt:769-791): the external
    // position ticker only fires every 250ms-1s; between ticks we advance an
    // interpolated clock every frame so line changes never lag. A >350ms
    // divergence means a seek or fresh tick — snap to it. While paused the
    // external value wins.
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
    // +250ms lookahead when lines carry no word timings — vivi Lyrics.kt:798.
    val hasWordTimings = remember(lines) { lines.any { it.contentSpans.isNotEmpty() } }
    val currentIndex by remember(lines) {
        derivedStateOf {
            val pos = smoothPosition.longValue + if (hasWordTimings) 0L else 250L
            lines.indexOfLast { it.timeMs <= pos }
        }
    }
    LaunchedEffect(currentIndex, autoScroll) {
        if (!autoScroll) return@LaunchedEffect
        try { listState.animateScrollToItem(index = currentIndex.coerceAtLeast(0), scrollOffset = 0) } catch (_: Exception) {}
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
        val isFluid = anim == LyricsAnimationStyle.VIVIMUSIC_FLUID || anim == LyricsAnimationStyle.LYRICS_V2_FLUID
        val spacingDp = when (anim) {
            LyricsAnimationStyle.LYRICS_V2_FLUID -> (24 * spacing).dp
            LyricsAnimationStyle.METRO_LYRICS -> (16 * spacing).dp
            LyricsAnimationStyle.APPLE_MUSIC, LyricsAnimationStyle.APPLE_MUSIC_V2_LETTER -> (36 * spacing).dp
            else -> (28 * spacing).dp
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().then(swipeMod)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (isFluid || anim == LyricsAnimationStyle.APPLE_MUSIC || anim == LyricsAnimationStyle.APPLE_MUSIC_V2_LETTER) {
                        drawRect(brush = Brush.verticalGradient(0f to Color.Transparent, 0.15f to Color.Black, 0.85f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                    } else {
                        drawRect(brush = Brush.verticalGradient(0f to Color.Transparent, 0.18f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                    }
                },
            contentPadding = PaddingValues(top = centerPadding, bottom = centerPadding, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(spacingDp),
            horizontalAlignment = if (anim == LyricsAnimationStyle.METRO_LYRICS) Alignment.Start else Alignment.CenterHorizontally
        ) {
            itemsIndexed(lines, key = { i, l -> "${i}_${l.timeMs}" }) { idx, line ->
                val nextTime = lines.getOrNull(idx + 1)?.timeMs ?: (line.timeMs + 4000L)
                val duration = (nextTime - line.timeMs).coerceAtLeast(800L)
                // Only the active line reads the ticking clock — every other item
                // stays skipped between line changes instead of recomposing per tick.
                LyricLine(line = line, lineDurationMs = duration, isCurrent = idx == currentIndex, isPast = idx < currentIndex, positionProvider = { smoothPosition.longValue }, anim = anim, glow = glow, textSize = textSize, spacing = spacing, blurVal = blurVal, onTap = { if (changeOnClick) onSeekTo(line.timeMs) }, translatedText = translations[line.timeMs].takeIf { idx == currentIndex })
            }
        }
    }
}

@Composable
private fun LyricLine(line: LrcLine, lineDurationMs: Long, isCurrent: Boolean, isPast: Boolean, positionProvider: () -> Long, anim: LyricsAnimationStyle, glow: Boolean, textSize: Float, spacing: Float, blurVal: Float, onTap: () -> Unit, translatedText: String? = null) {
    // Reading the clock only while this line is active scopes per-tick recomposition
    // to exactly one list item; inactive lines keep their stable -1 sentinel.
    val currentPositionMs = if (isCurrent) positionProvider() else -1L
    val scaleTarget = if (!isCurrent) 1f else when (anim) {
        LyricsAnimationStyle.NONE -> 1f
        LyricsAnimationStyle.FADE -> 1f
        LyricsAnimationStyle.GLOW -> 1.10f
        LyricsAnimationStyle.SLIDE -> 1.06f
        LyricsAnimationStyle.KARAOKE -> 1.14f
        LyricsAnimationStyle.APPLE_MUSIC -> 1.22f
        LyricsAnimationStyle.APPLE_MUSIC_V2_LETTER -> 1.22f
        LyricsAnimationStyle.VIVIMUSIC_FLUID -> 1.26f
        LyricsAnimationStyle.LYRICS_V2_FLUID -> 1.20f
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
        !isCurrent && anim == LyricsAnimationStyle.APPLE_MUSIC -> Modifier.blur(10.dp)
        !isCurrent && anim == LyricsAnimationStyle.APPLE_MUSIC_V2_LETTER -> Modifier.blur(10.dp)
        blurVal > 0 && !isCurrent && (anim == LyricsAnimationStyle.FADE || anim == LyricsAnimationStyle.NONE) -> Modifier.blur((blurVal * 8).dp)
        else -> Modifier
    }

    Box(
        modifier = Modifier.fillMaxWidth()
            .graphicsLayer { translationY = offset; scaleX = scale; scaleY = scale; this.alpha = alpha }
            .then(blurMod)
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onTap() }
            .padding(vertical = 6.dp),
        contentAlignment = if (anim == LyricsAnimationStyle.METRO_LYRICS) Alignment.CenterStart else Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = if (anim == LyricsAnimationStyle.METRO_LYRICS) Alignment.Start else Alignment.CenterHorizontally,
        ) {
        when (anim) {
            LyricsAnimationStyle.KARAOKE, LyricsAnimationStyle.VIVIMUSIC_FLUID, LyricsAnimationStyle.LYRICS_V2_FLUID -> {
                if (isCurrent && line.contentSpans.isNotEmpty()) {
                    val ann = buildAnnotatedString {
                        line.contentSpans.forEach { sp ->
                            val passed = currentPositionMs >= sp.timeMs
                            val color = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            val shadow = if (passed && glow) androidx.compose.ui.graphics.Shadow(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), blurRadius = if (anim == LyricsAnimationStyle.VIVIMUSIC_FLUID) 22f else 18f) else null
                            withStyle(SpanStyle(color = color, fontSize = textSize.sp, fontWeight = if (passed) FontWeight.ExtraBold else FontWeight.Bold, shadow = shadow)) { append(sp.text) }
                            append(" ")
                        }
                    }
                    Text(text = ann, style = MaterialTheme.typography.headlineSmall.copy(fontSize = textSize.sp, lineHeight = (textSize * spacing).sp), textAlign = TextAlign.Center)
                } else if (isCurrent) {
                    Text(text = line.text, style = MaterialTheme.typography.headlineSmall.copy(fontSize = (textSize + 2).sp, lineHeight = (textSize * spacing).sp, fontWeight = FontWeight.ExtraBold, shadow = if (glow) androidx.compose.ui.graphics.Shadow(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), blurRadius = 18f) else null), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                } else {
                    Text(text = line.text, style = MaterialTheme.typography.titleLarge.copy(fontSize = textSize.sp, lineHeight = (textSize * spacing).sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                }
            }
            LyricsAnimationStyle.APPLE_MUSIC -> {
                if (isCurrent) {
                    Text(text = line.text, style = MaterialTheme.typography.headlineMedium.copy(fontSize = (textSize + 4).sp, lineHeight = (textSize * spacing * 1.1f).sp, fontWeight = FontWeight.ExtraBold, shadow = if (glow) androidx.compose.ui.graphics.Shadow(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), blurRadius = 20f) else null), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                } else {
                    Text(text = line.text, style = MaterialTheme.typography.titleLarge.copy(fontSize = textSize.sp, lineHeight = (textSize * spacing).sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f), textAlign = TextAlign.Center)
                }
            }
            LyricsAnimationStyle.APPLE_MUSIC_V2_LETTER -> {
                if (isCurrent) {
                    val ann = buildAnnotatedString {
                        if (line.contentSpans.isNotEmpty()) {
                            line.contentSpans.forEach { sp ->
                                val wordLen = sp.text.length.coerceAtLeast(1)
                                val perLetter = (sp.durationMs.coerceAtLeast(300L).toFloat() / wordLen)
                                sp.text.forEachIndexed { li, ch ->
                                    val letterTime = sp.timeMs + (perLetter * li).toLong()
                                    val passed = currentPositionMs >= letterTime
                                    val shadow = if (passed && glow) androidx.compose.ui.graphics.Shadow(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), blurRadius = 14f) else null
                                    withStyle(SpanStyle(color = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), fontWeight = if (passed) FontWeight.ExtraBold else FontWeight.SemiBold, shadow = shadow)) { append(ch.toString()) }
                                }
                                append(" ")
                            }
                        } else {
                            val letters = line.text.toList()
                            val perLetter = lineDurationMs.toFloat() / letters.size.coerceAtLeast(1)
                            letters.forEachIndexed { i, ch ->
                                if (ch == ' ') { append(" "); return@forEachIndexed }
                                val letterTime = line.timeMs + (perLetter * i).toLong()
                                val passed = currentPositionMs >= letterTime
                                val shadow = if (passed && glow) androidx.compose.ui.graphics.Shadow(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), blurRadius = 12f) else null
                                withStyle(SpanStyle(color = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), fontWeight = if (passed) FontWeight.ExtraBold else FontWeight.SemiBold, shadow = shadow)) { append(ch.toString()) }
                            }
                        }
                    }
                    Text(text = ann, style = MaterialTheme.typography.headlineSmall.copy(fontSize = textSize.sp, lineHeight = (textSize * spacing).sp), textAlign = TextAlign.Center)
                } else {
                    Text(text = line.text, style = MaterialTheme.typography.titleLarge.copy(fontSize = textSize.sp, lineHeight = (textSize * spacing).sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                }
            }
            LyricsAnimationStyle.GLOW -> {
                if (isCurrent) {
                    Text(text = line.text, style = MaterialTheme.typography.headlineSmall.copy(fontSize = (textSize + 2).sp, lineHeight = (textSize * spacing).sp, fontWeight = FontWeight.ExtraBold, shadow = androidx.compose.ui.graphics.Shadow(MaterialTheme.colorScheme.primary.copy(alpha = if (glow) 0.65f else 0.0f), blurRadius = 26f)), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                } else {
                    Text(text = line.text, style = MaterialTheme.typography.titleLarge.copy(fontSize = textSize.sp, lineHeight = (textSize * spacing).sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f), textAlign = TextAlign.Center)
                }
            }
            LyricsAnimationStyle.SLIDE -> {
                if (isCurrent) {
                    Text(text = line.text, style = MaterialTheme.typography.headlineSmall.copy(fontSize = (textSize + 2).sp, lineHeight = (textSize * spacing).sp, fontWeight = FontWeight.Bold, shadow = if (glow) androidx.compose.ui.graphics.Shadow(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), blurRadius = 16f) else null), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                } else {
                    Text(text = line.text, style = MaterialTheme.typography.titleLarge.copy(fontSize = textSize.sp, lineHeight = (textSize * spacing).sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f), textAlign = TextAlign.Center)
                }
            }
            LyricsAnimationStyle.METRO_LYRICS -> {
                Text(text = line.text.uppercase(), style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontSize = textSize.sp, lineHeight = (textSize * spacing).sp, fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Medium), color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
            }
            LyricsAnimationStyle.FADE, LyricsAnimationStyle.NONE -> {
                if (isCurrent) {
                    Text(text = line.text, style = MaterialTheme.typography.headlineSmall.copy(fontSize = (textSize + 1).sp, lineHeight = (textSize * spacing).sp, fontWeight = FontWeight.Bold, shadow = if (glow && anim == LyricsAnimationStyle.FADE) androidx.compose.ui.graphics.Shadow(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), blurRadius = 12f) else null), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                } else {
                    Text(text = line.text, style = MaterialTheme.typography.titleLarge.copy(fontSize = textSize.sp, lineHeight = (textSize * spacing).sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                }
            }
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
