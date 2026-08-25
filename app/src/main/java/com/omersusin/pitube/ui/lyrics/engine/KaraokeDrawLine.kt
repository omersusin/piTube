package com.omersusin.pitube.ui.lyrics.engine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Draw-phase karaoke word ported verbatim from ArchiveTune Lyrics.kt:220-403.
 * The current-time lambda is read inside graphicsLayer/drawWithContent, so the
 * fill animates per-frame WITHOUT recomposing composition.
 */
@Composable
private fun KaraokeWord(
    text: String,
    startTime: Long,
    endTime: Long,
    currentTimeProvider: () -> Long,
    isRtl: Boolean,
    fontSize: TextUnit,
    textColor: Color,
    inactiveAlpha: Float,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    nudgeEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val duration = endTime - startTime
    val glowPadding = 10.dp

    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                val glowPaddingPx = glowPadding.roundToPx()
                val looseConstraints = constraints.copy(
                    minWidth = 0,
                    maxWidth = Constraints.Infinity,
                    minHeight = 0,
                    maxHeight = Constraints.Infinity,
                )
                val placeable = measurable.measure(looseConstraints)
                val coreWidth = (placeable.width - glowPaddingPx * 2).coerceAtLeast(0)
                val coreHeight = (placeable.height - glowPaddingPx * 2).coerceAtLeast(0)
                layout(coreWidth, coreHeight) {
                    placeable.place(-glowPaddingPx, -glowPaddingPx)
                }
            }
            .graphicsLayer {
                clip = false
                val currentTime = currentTimeProvider()

                val maxShift = 5f
                val attackDuration = 120L
                val decayDuration = 250L
                val totalImpulseTime = attackDuration + decayDuration

                val shift =
                    if (nudgeEnabled && currentTime >= startTime && currentTime < startTime + totalImpulseTime) {
                        val timeSinceStart = currentTime - startTime
                        if (timeSinceStart < attackDuration) {
                            val progress = timeSinceStart.toFloat() / attackDuration.toFloat()
                            androidx.compose.ui.util.lerp(0f, maxShift, progress)
                        } else {
                            val decayProgress = (timeSinceStart - attackDuration).toFloat() / decayDuration.toFloat()
                            androidx.compose.ui.util.lerp(maxShift, 0f, decayProgress)
                        }
                    } else {
                        0f
                    }

                translationX = if (isRtl) -shift else shift
            },
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            color = textColor.copy(alpha = inactiveAlpha),
            fontWeight = fontWeight,
            modifier = Modifier.padding(glowPadding),
        )

        Text(
            text = text,
            fontSize = fontSize,
            color = textColor,
            fontWeight = fontWeight,
            modifier = Modifier
                .padding(glowPadding)
                .drawWithContent {
                    if (currentTimeProvider() >= endTime) drawContent()
                },
        )

        Box(
            modifier = Modifier
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen

                    val currentTime = currentTimeProvider()
                    val fadeDuration = 200L

                    alpha = if (currentTime >= endTime) {
                        val fadeProgress = ((currentTime - endTime).toFloat() / fadeDuration).coerceIn(0f, 1f)
                        1f - fadeProgress
                    } else {
                        1f
                    }
                }
                .drawWithContent {
                    val currentTime = currentTimeProvider()
                    val progress =
                        if (duration > 0) {
                            ((currentTime - startTime).toFloat() / duration).coerceIn(0f, 1f)
                        } else if (currentTime >= endTime) {
                            1f
                        } else {
                            0f
                        }

                    val fadeDuration = 200L
                    val isFading = currentTime >= endTime && currentTime < (endTime + fadeDuration)

                    if ((progress > 0f && progress < 1f) || isFading) {
                        drawContent()

                        val fadeWidth = 20f
                        val totalWidth = size.width
                        val paddingPx = glowPadding.toPx()
                        val textWidth = totalWidth - (paddingPx * 2)
                        val fillWidth = textWidth * progress

                        val endFraction = (paddingPx + fillWidth + fadeWidth) / totalWidth
                        val solidFraction = (paddingPx + fillWidth) / totalWidth

                        val softFillBrush =
                            if (!isRtl) {
                                Brush.horizontalGradient(
                                    0f to Color.Black,
                                    solidFraction.coerceAtLeast(0f) to Color.Black,
                                    endFraction.coerceAtMost(1f) to Color.Transparent,
                                )
                            } else {
                                val solidStartX = (paddingPx + (textWidth - fillWidth)).coerceIn(0f, totalWidth)
                                val fadeStartX = (solidStartX - fadeWidth).coerceIn(0f, totalWidth)
                                val fadeStartFraction = (fadeStartX / totalWidth).coerceIn(0f, 1f)
                                val solidStartFraction = (solidStartX / totalWidth).coerceIn(0f, 1f)
                                Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    fadeStartFraction to Color.Transparent,
                                    solidStartFraction to Color.Black,
                                    1f to Color.Black,
                                )
                            }

                        drawRect(brush = softFillBrush, blendMode = BlendMode.DstIn)
                    }
                }
                .padding(glowPadding),
        ) {
            Text(
                text = text,
                fontSize = fontSize,
                color = textColor,
                fontWeight = fontWeight,
            )
        }
    }
}

/**
 * Full-line karaoke renderer: a FlowRow of [KaraokeWord]s. Inactive lines render
 * as plain dim text — no draw-phase subscriptions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KaraokeDrawLine(
    lineText: String,
    words: List<EngineWord>,
    isActiveLine: Boolean,
    positionProvider: () -> Long,
    accent: Color,
    inactiveColor: Color,
    textSizeSp: Float,
    lineHeightFactor: Float,
    textAlign: TextAlign = TextAlign.Center,
) {
    if (!isActiveLine) {
        Text(
            text = lineText,
            fontSize = textSizeSp.sp,
            lineHeight = (textSizeSp * lineHeightFactor).sp,
            color = inactiveColor,
            fontWeight = FontWeight.Medium,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    val isRtl = rememberRtl(lineText)

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when (textAlign) {
            TextAlign.Center -> Arrangement.Center
            TextAlign.Right -> Arrangement.End
            else -> Arrangement.Start
        },
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
    ) {
        words.forEachIndexed { index, word ->
            KaraokeWord(
                text = word.text,
                startTime = word.startMs,
                endTime = word.endMs,
                currentTimeProvider = positionProvider,
                isRtl = isRtl,
                fontSize = textSizeSp.sp,
                textColor = accent,
                inactiveAlpha = 0.4f,
            )
            if (index < words.lastIndex) {
                Text(text = " ", fontSize = textSizeSp.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun computeRtl(text: String): Boolean {
    for (c in text) {
        val d = Character.getDirectionality(c)
        if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) return true
    }
    return false
}

@Composable
private fun rememberRtl(text: String): Boolean =
    androidx.compose.runtime.remember(text) { computeRtl(text) }
