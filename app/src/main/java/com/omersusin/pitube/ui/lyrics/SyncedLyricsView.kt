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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
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
        val centerPadding = when (textPos) { LyricsTextPosition.TOP -> 24.dp; LyricsTextPosition.BOTTOM -> maxHeight - 120.dp; else -> maxHeight / 2 }
        val swipeMod = if (swipeEnabled && onSwipeNext != null) Modifier.pointerInput(Unit) {
            var drag = 0f
            detectHorizontalDragGestures(onDragEnd = {
                if (drag > 120) onSwipePrev?.invoke() else if (drag < -120) onSwipeNext.invoke()
                drag = 0f
            }) { _, d -> drag += d }
        } else Modifier
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().then(swipeMod)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(brush = Brush.verticalGradient(0f to Color.Transparent, 0.18f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                },
            contentPadding = PaddingValues(top = centerPadding, bottom = centerPadding, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy((28 * spacing).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lines, key = { i, l -> "${i}_${l.timeMs}" }) { idx, line ->
                LyricLine(line = line, isCurrent = idx == currentIndex, isPast = idx < currentIndex, currentPositionMs = currentPositionMs, anim = anim, glow = glow, textSize = textSize, spacing = spacing, blurVal = blurVal, onTap = { onSeekTo(line.timeMs) })
            }
        }
    }
}

@Composable
private fun LyricLine(line: LrcLine, isCurrent: Boolean, isPast: Boolean, currentPositionMs: Long, anim: LyricsAnimationStyle, glow: Boolean, textSize: Float, spacing: Float, blurVal: Float, onTap: () -> Unit) {
    val scale by animateFloatAsState(targetValue = if (isCurrent) scaleFor(anim) else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow), label = "scale")
    val alpha by animateFloatAsState(targetValue = if (isCurrent) 1f else alphaFor(anim, isPast), animationSpec = spring(stiffness = Spring.StiffnessLow), label = "alpha")
    val offset by animateFloatAsState(targetValue = if (isCurrent) 0f else offsetFor(anim), animationSpec = spring(stiffness = Spring.StiffnessLow), label = "offset")

    Box(
        modifier = Modifier.fillMaxWidth()
            .graphicsLayer { translationY = offset; scaleX = scale; scaleY = scale; this.alpha = alpha }
            .then(if (blurVal > 0 && !isCurrent && (anim == LyricsAnimationStyle.FADE || anim == LyricsAnimationStyle.NONE)) Modifier.blur((blurVal * 8).dp) else Modifier)
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onTap() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isCurrent && line.contentSpans.isNotEmpty() && anim in karaokeAnims -> {
                val ann = buildAnnotatedString {
                    line.contentSpans.forEach { sp ->
                        val passed = currentPositionMs >= sp.timeMs
                        val color = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        withStyle(SpanStyle(color = color, fontSize = textSize.sp, fontWeight = if (passed) FontWeight.ExtraBold else FontWeight.Bold, shadow = if (passed && glow) androidx.compose.ui.graphics.Shadow(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), blurRadius = 20f) else null)) { append(sp.text) }
                        append(" ")
                    }
                }
                Text(text = ann, style = MaterialTheme.typography.headlineSmall.copy(fontSize = textSize.sp, lineHeight = (textSize * spacing).sp), textAlign = TextAlign.Center)
            }
            anim == LyricsAnimationStyle.APPLE_MUSIC_V2_LETTER && isCurrent -> {
                val letters = line.text.toList()
                val ann = buildAnnotatedString {
                    letters.forEachIndexed { i, ch ->
                        val frac = if (letters.isEmpty()) 1f else i.toFloat() / letters.size
                        val passed = currentPositionMs >= line.timeMs + (3000 * frac).toLong()
                        withStyle(SpanStyle(color = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = if (passed) FontWeight.ExtraBold else FontWeight.SemiBold, shadow = if (passed && glow) androidx.compose.ui.graphics.Shadow(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), blurRadius = 16f) else null)) { append(ch.toString()) }
                    }
                }
                Text(text = ann, style = MaterialTheme.typography.headlineSmall.copy(fontSize = textSize.sp, lineHeight = (textSize * spacing).sp), textAlign = TextAlign.Center)
            }
            anim == LyricsAnimationStyle.METRO_LYRICS -> {
                Text(text = line.text.uppercase(), style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontSize = textSize.sp, lineHeight = (textSize * spacing).sp, fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Medium), color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
            }
            isCurrent -> {
                Text(text = line.text, style = MaterialTheme.typography.headlineSmall.copy(fontSize = (textSize + 2).sp, lineHeight = (textSize * spacing).sp, fontWeight = FontWeight.ExtraBold, shadow = if (glow) androidx.compose.ui.graphics.Shadow(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), blurRadius = 18f) else null), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            }
            else -> {
                Text(text = line.text, style = MaterialTheme.typography.titleLarge.copy(fontSize = textSize.sp, lineHeight = (textSize * spacing).sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), textAlign = TextAlign.Center)
            }
        }
    }
}

private val karaokeAnims = setOf(LyricsAnimationStyle.KARAOKE, LyricsAnimationStyle.VIVIMUSIC_FLUID, LyricsAnimationStyle.LYRICS_V2_FLUID, LyricsAnimationStyle.APPLE_MUSIC, LyricsAnimationStyle.APPLE_MUSIC_V2_LETTER)
private fun scaleFor(a: LyricsAnimationStyle) = when (a) { LyricsAnimationStyle.NONE -> 1f; LyricsAnimationStyle.FADE -> 1f; LyricsAnimationStyle.GLOW -> 1.12f; LyricsAnimationStyle.SLIDE -> 1.08f; LyricsAnimationStyle.KARAOKE -> 1.18f; LyricsAnimationStyle.APPLE_MUSIC -> 1.2f; LyricsAnimationStyle.APPLE_MUSIC_V2_LETTER -> 1.2f; LyricsAnimationStyle.VIVIMUSIC_FLUID -> 1.25f; LyricsAnimationStyle.LYRICS_V2_FLUID -> 1.22f; LyricsAnimationStyle.METRO_LYRICS -> 1f }
private fun alphaFor(a: LyricsAnimationStyle, isPast: Boolean) = when (a) { LyricsAnimationStyle.NONE -> 1f; LyricsAnimationStyle.FADE -> if (isPast) 0.25f else 0.4f; LyricsAnimationStyle.GLOW -> 0.5f; LyricsAnimationStyle.SLIDE -> 0.35f; else -> 0.35f }
private fun offsetFor(a: LyricsAnimationStyle) = when (a) { LyricsAnimationStyle.SLIDE -> 16f; LyricsAnimationStyle.VIVIMUSIC_FLUID -> 8f; LyricsAnimationStyle.LYRICS_V2_FLUID -> 6f; else -> 0f }
