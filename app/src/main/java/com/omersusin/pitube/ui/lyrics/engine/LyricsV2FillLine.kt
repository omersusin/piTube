package com.omersusin.pitube.ui.lyrics.engine

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omersusin.pitube.data.lyrics.LrcLine
import kotlin.math.PI
import kotlin.math.sin

/**
 * Liquid-fill engine ported from vivi LyricsV2.kt (itself ported from
 * ArchiveTune-dev): dim base word + bright overlay masked by an offscreen
 * DstIn soft-edge gradient at the fill frontier, with sine bounce and float.
 */
@Composable
fun LyricsV2FillLine(
    line: LrcLine,
    isActiveLine: Boolean,
    isPast: Boolean,
    positionMs: Long,
    accent: Color,
    inactiveColor: Color,
    inactiveAlpha: Float,
    textSizeSp: Float,
    lineHeightFactor: Float,
    textAlign: TextAlign = TextAlign.Center,
) {
    val words = line.toEngineWords()

    if (words.isNullOrEmpty()) {
        Text(
            text = line.text,
            fontSize = textSizeSp.sp,
            fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Medium,
            lineHeight = (textSizeSp * lineHeightFactor.coerceAtMost(1.3f)).sp,
            color = accent.copy(alpha = if (isActiveLine) 1f else inactiveAlpha),
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when (textAlign) {
            TextAlign.Center -> Arrangement.Center
            TextAlign.Right -> Arrangement.End
            else -> Arrangement.Start
        },
        verticalArrangement = Arrangement.spacedBy(
            with(LocalDensity.current) { (textSizeSp * (lineHeightFactor.coerceAtMost(1.3f) - 1f)).sp.toDp() }
        )
    ) {
        words.forEachIndexed { index, word ->
            AnimatedWordV2(
                word = word,
                isLineActive = isActiveLine,
                isLinePast = isPast,
                positionMs = positionMs,
                accent = accent,
                inactiveColor = inactiveColor,
                inactiveAlpha = inactiveAlpha,
                fontSize = textSizeSp,
                lineHeight = (textSizeSp * lineHeightFactor).coerceAtMost(textSizeSp * 1.3f),
            )
            if (index < words.lastIndex) {
                Text(
                    text = " ",
                    fontSize = textSizeSp.sp,
                    lineHeight = (textSizeSp * lineHeightFactor.coerceAtMost(1.3f)).sp,
                )
            }
        }
    }
}

@Composable
private fun AnimatedWordV2(
    word: EngineWord,
    isLineActive: Boolean,
    isLinePast: Boolean,
    positionMs: Long,
    accent: Color,
    inactiveColor: Color,
    inactiveAlpha: Float,
    fontSize: Float,
    lineHeight: Float,
) {
    val wordDuration = (word.endMs - word.startMs).coerceAtLeast(1L)

    val isWordComplete = isLinePast || positionMs >= word.endMs
    val isWordActive = isLineActive && positionMs in word.startMs until word.endMs

    val progress = when {
        isWordComplete -> 1f
        !isLineActive || positionMs <= word.startMs -> 0f
        else -> ((positionMs - word.startMs).toFloat() / wordDuration).coerceIn(0f, 1f)
    }

    val sinProgress = sin(progress * PI).toFloat()
    val wordScale = 1f + (0.015f * sinProgress)

    val targetFloat = if (isWordActive) -4f * sinProgress else 0f
    val floatOffset by animateFloatAsState(
        targetValue = targetFloat,
        animationSpec = tween(durationMillis = if (isWordActive) 50 else 350, easing = FastOutSlowInEasing),
        label = "wordFloatOffset",
    )

    val glowProgress = (progress * 2f).coerceAtMost(1f)
    val glowAlpha = if (isWordActive) glowProgress * 0.45f else 0f
    val glowRadius = if (isWordActive) glowProgress * 12f else 0f

    val density = LocalDensity.current
    val fontWeight = if (isLineActive) FontWeight.Bold else FontWeight.SemiBold

    Box(
        modifier = Modifier.graphicsLayer {
            translationY = floatOffset * density.density
            scaleX = wordScale
            scaleY = wordScale
        }
    ) {
        Text(
            text = word.text,
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            lineHeight = lineHeight.sp,
            color = inactiveColor.copy(alpha = inactiveAlpha),
        )

        if (isWordComplete || isWordActive) {
            Text(
                text = word.text,
                fontSize = fontSize.sp,
                fontWeight = fontWeight,
                lineHeight = lineHeight.sp,
                color = accent,
                style = androidx.compose.ui.text.TextStyle(shadow = if (glowAlpha > 0f) Shadow(accent.copy(alpha = glowAlpha), Offset.Zero, glowRadius.coerceAtLeast(1f)) else null),
                modifier = if (isWordActive) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val edgeWidth = 8.dp.toPx()
                            val center = (size.width + edgeWidth * 2) * progress - edgeWidth
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startX = center - edgeWidth,
                                    endX = center + edgeWidth,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                } else {
                    Modifier
                }
            )
        }
    }
}
