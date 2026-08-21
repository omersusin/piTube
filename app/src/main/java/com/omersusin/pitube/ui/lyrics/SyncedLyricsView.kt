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

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (lyricsResult) {
            is LyricsFetchResult.Success -> LyricsContent(
                lines = lyricsResult.lines, currentPositionMs = currentPositionMs, onSeekTo = onSeekTo,
                anim = anim, glow = glow, textSize = textSize, spacing = spacing, blurVal = blurVal,
                autoScroll = autoScroll, textPos = textPos, swipeEnabled = swipeEnabled, onSwipeNext = onSwipeNext, onSwipePrev = onSwipePrev
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
    onSwipeNext: (() -> Unit)?, onSwipePrev: (() -> Unit)?
) {
    val listState = rememberLazyListState()
    val currentIndex by remember(currentPositionMs, lines) { derivedStateOf { lines.indexOfLast { it.timeMs <= currentPositionMs } } }
    LaunchedEffect(currentIndex, autoScroll) {
        if (!autoScroll) return@LaunchedEffect
        try { listState.animateScrollToItem(index = currentIndex.coerceAtLeast(0), scrollOffset = 0) } catch (_: Exception) {}
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val centerPadding = when {
            textPos == LyricsTextPosition.TOP -> 24.dp
            textPos == LyricsTextPosition.BOTTOM -> {
                if (maxHeight.isInfinite() || maxHeight.isNaN() || maxHeight < 72.dp) 24.dp
                else (maxHeight - 120.dp).coerceAtLeast(24.dp)
            }
            else -> if (maxHeight.isInfinite() || maxHeight.isNaN() || maxHeight < 72.dp) 120.dp else maxHeight / 2
        }
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
                LyricLine(line = line, lineDurationMs = duration, isCurrent = idx == currentIndex, isPast = idx < currentIndex, currentPositionMs = currentPositionMs, anim = anim, glow = glow, textSize = textSize, spacing = spacing, blurVal = blurVal, onTap = { onSeekTo(line.timeMs) })
            }
        }
    }
}

@Composable
private fun LyricLine(line: LrcLine, lineDurationMs: Long, isCurrent: Boolean, isPast: Boolean, currentPositionMs: Long, anim: LyricsAnimationStyle, glow: Boolean, textSize: Float, spacing: Float, blurVal: Float, onTap: () -> Unit) {
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
    }
}
