package com.omersusin.pitube.ui.lyrics.engine

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.omersusin.pitube.data.lyrics.LrcLine

/**
 * "Ocean Wave" engine ported from vivi ViviMusicLyrics.kt (VIVIMUSIC_1 style).
 * ONE global wave progress sweeps from first word to last; each word maps the
 * wave front into its own span with a trailing feather gradient.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ViviFluidLine(
    line: LrcLine,
    lineDurationMs: Long,
    isActiveLine: Boolean,
    positionMs: Long,
    accent: Color,
    inactiveColor: Color,
    textSizeSp: Float,
    lineHeightFactor: Float,
    textAlign: TextAlign = TextAlign.Center,
) {
    val words = remember(line) { line.toEngineWords() }

    // Sentence linger: hold active styling 180ms after deactivation so the last
    // word finishes its fill before the cross-fade (vivi ViviMusicLyrics.kt:88-96).
    val lingeredActive = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(isActiveLine) {
        if (isActiveLine) {
            lingeredActive.value = true
        } else {
            kotlinx.coroutines.delay(180L)
            lingeredActive.value = false
        }
    }

    val targetAlpha = when {
        !isActiveLine && !lingeredActive.value -> 0.45f
        else -> 1f
    }
    val animatedAlpha by animateFloatAsState(targetValue = targetAlpha, animationSpec = tween(250, easing = FastOutSlowInEasing), label = "fluidAlpha")
    val scale by animateFloatAsState(
        targetValue = if (isActiveLine) 1.05f else 1f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "fluidScale",
    )

    val columnModifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {
            alpha = animatedAlpha
            scaleX = scale
            scaleY = scale
        }

    if (words.isNullOrEmpty()) {
        // Sentence-level fallback: whole sentence sweeps at once.
        val sentenceAlpha by animateFloatAsState(
            targetValue = if (isActiveLine || lingeredActive.value) 1f else 0.45f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "sentenceAlpha",
        )
        Text(
            text = line.text,
            fontSize = textSizeSp.sp,
            color = accent.copy(alpha = sentenceAlpha),
            fontWeight = if (isActiveLine) FontWeight.ExtraBold else FontWeight.Bold,
            lineHeight = (textSizeSp * lineHeightFactor.coerceAtMost(1.3f)).sp,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    val globalEnd = words.last().endMs.coerceAtLeast(line.timeMs + 1L)
    val lineRelTime = (positionMs - line.timeMs).coerceAtLeast(0L)
    val rawGlobalWave = (lineRelTime.toFloat() / (globalEnd - line.timeMs).toFloat()).coerceIn(0f, 1f)

    val globalWave by animateFloatAsState(
        targetValue = rawGlobalWave,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "globalWaveProgress",
    )

    val waveFeather = 0.12f

    androidx.compose.foundation.layout.Column(modifier = columnModifier, horizontalAlignment = Alignment.CenterHorizontally) {
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
                val wordStartFrac = (word.startMs - line.timeMs).coerceAtLeast(0L).toFloat() / (globalEnd - line.timeMs)
                val wordEndFrac = (word.endMs - line.timeMs).coerceAtLeast(1L).toFloat() / (globalEnd - line.timeMs)
                val wordSpan = (wordEndFrac - wordStartFrac).coerceAtLeast(0.001f)
                val wordLocalProgress = ((globalWave - wordStartFrac) / wordSpan).coerceIn(0f, 1f)

                val glowAlpha = 0.6f * wordLocalProgress
                val glowRadius = (12f * wordLocalProgress).coerceAtLeast(0.1f)
                val finalFontWeight = if (isActiveLine) FontWeight.ExtraBold else FontWeight.Bold

                val waveFront = wordLocalProgress
                val waveTail = (wordLocalProgress + waveFeather).coerceAtMost(1f)

                val wordBrush = when {
                    wordLocalProgress <= 0f -> Brush.horizontalGradient(listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.45f)))
                    wordLocalProgress >= 1f -> Brush.horizontalGradient(listOf(accent, accent))
                    else -> Brush.horizontalGradient(
                        0f to accent,
                        waveFront to accent,
                        waveTail to accent.copy(alpha = 0.45f),
                        1f to accent.copy(alpha = 0.45f),
                    )
                }

                Text(
                    text = word.text,
                    fontSize = textSizeSp.sp,
                    style = TextStyle(
                        brush = wordBrush,
                        fontWeight = finalFontWeight,
                        lineHeight = (textSizeSp * lineHeightFactor.coerceAtMost(1.3f)).sp,
                        textAlign = textAlign,
                        shadow = Shadow(color = accent.copy(alpha = glowAlpha), offset = Offset.Zero, blurRadius = glowRadius),
                    )
                )

                if (index != words.lastIndex) {
                    val spaceAlpha = (0.45f + 0.55f * wordLocalProgress).coerceIn(0.45f, 1f)
                    Text(
                        text = " ",
                        fontSize = textSizeSp.sp,
                        color = accent.copy(alpha = spaceAlpha),
                        lineHeight = (textSizeSp * lineHeightFactor.coerceAtMost(1.3f)).sp,
                    )
                }
            }
        }
    }
}
