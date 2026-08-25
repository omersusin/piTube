package com.omersusin.pitube.ui.lyrics.engine

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.omersusin.pitube.data.local.LyricsAnimationStyle
import com.omersusin.pitube.data.lyrics.LrcLine

/**
 * Word-by-word span styles ported from vivi-music Lyrics.kt:1822-2145.
 * All five share the same smoothstep-driven progress; they differ in how the
 * progress maps to color / shadow / gradient. Only the active line passes a
 * ticking positionProvider — inactive lines get a stable -1 sentinel.
 */
@Composable
fun WordLevelSpanLine(
    style: LyricsAnimationStyle,
    line: LrcLine,
    isActiveLine: Boolean,
    positionMs: Long,
    accent: Color,
    inactiveColor: Color,
    textSizeSp: Float,
    lineHeightFactor: Float,
    textAlign: TextAlign = TextAlign.Center,
) {
    val words = line.toEngineWords() ?: run {
        Text(
            text = line.text,
            fontSize = textSizeSp.sp,
            lineHeight = (textSizeSp * lineHeightFactor).sp,
            color = if (isActiveLine) accent else inactiveColor,
            fontWeight = if (isActiveLine) FontWeight.ExtraBold else FontWeight.Medium,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    val styled = buildAnnotatedString {
        words.forEachIndexed { wordIndex, word ->
            val wordDuration = word.endMs - word.startMs
            val isWordActive = isActiveLine && positionMs >= word.startMs && positionMs <= word.endMs
            val hasWordPassed = isActiveLine && positionMs > word.endMs
            val linear = if (isWordActive && wordDuration > 0) {
                ((positionMs - word.startMs).toFloat() / wordDuration).coerceIn(0f, 1f)
            } else 0f
            val progress = when {
                hasWordPassed -> 1f
                isWordActive -> smoothstep(linear)
                else -> 0f
            }

            when (style) {
                LyricsAnimationStyle.NONE -> {
                    val alpha = when {
                        !isActiveLine -> 0.7f
                        hasWordPassed -> 1f
                        isWordActive -> 0.5f + (0.5f * progress)
                        else -> 0.35f
                    }
                    val weight = when {
                        !isActiveLine || hasWordPassed -> FontWeight.Bold
                        isWordActive -> FontWeight.ExtraBold
                        else -> FontWeight.Medium
                    }
                    withStyle(SpanStyle(color = accent.copy(alpha = alpha), fontWeight = weight)) { append(word.text) }
                }

                LyricsAnimationStyle.FADE -> {
                    val alpha = when {
                        !isActiveLine -> 0.55f
                        hasWordPassed -> 1f
                        isWordActive -> 0.4f + (0.6f * progress)
                        else -> 0.4f
                    }
                    val shadow = when {
                        isWordActive && progress > 0.2f -> Shadow(accent.copy(alpha = 0.35f * progress), Offset.Zero, 10f * progress)
                        hasWordPassed -> Shadow(accent.copy(alpha = 0.15f), Offset.Zero, 6f)
                        else -> null
                    }
                    withStyle(
                        SpanStyle(
                            color = accent.copy(alpha = alpha),
                            fontWeight = if (isWordActive) FontWeight.ExtraBold else FontWeight.Bold,
                            shadow = shadow,
                        )
                    ) { append(word.text) }
                }

                LyricsAnimationStyle.GLOW -> {
                    val glowIntensity = progress * progress
                    val brightness = 0.45f + (0.55f * progress)
                    val color = when {
                        !isActiveLine -> accent.copy(alpha = 0.5f)
                        isWordActive || hasWordPassed -> accent.copy(alpha = brightness)
                        else -> accent.copy(alpha = 0.35f)
                    }
                    val shadow = when {
                        isWordActive && glowIntensity > 0.05f -> Shadow(accent.copy(alpha = 0.5f + (0.3f * glowIntensity)), Offset.Zero, 16f + (12f * glowIntensity))
                        hasWordPassed -> Shadow(accent.copy(alpha = 0.25f), Offset.Zero, 8f)
                        else -> null
                    }
                    withStyle(
                        SpanStyle(
                            color = color,
                            fontWeight = when {
                                !isActiveLine -> FontWeight.Bold
                                isWordActive -> FontWeight.ExtraBold
                                else -> FontWeight.Bold
                            },
                            shadow = shadow,
                        )
                    ) { append(word.text) }
                }

                LyricsAnimationStyle.SLIDE -> {
                    if (isWordActive && wordDuration > 0) {
                        val breatheValue = ((positionMs - word.startMs) % 3000) / 3000f
                        val breatheEffect = (kotlin.math.sin(breatheValue * kotlin.math.PI.toFloat() * 2f) * 0.03f).coerceIn(0f, 0.03f)
                        val glowIntensity = (0.3f + progress * 0.7f + breatheEffect).coerceIn(0f, 1.1f)
                        val slideBrush = Brush.horizontalGradient(
                            0.0f to accent,
                            (progress * 0.95f).coerceIn(0f, 1f) to accent,
                            progress to accent.copy(alpha = 0.9f),
                            (progress + 0.02f).coerceIn(0f, 1f) to accent.copy(alpha = 0.5f),
                            (progress + 0.08f).coerceIn(0f, 1f) to accent.copy(alpha = 0.35f),
                            1.0f to accent.copy(alpha = 0.35f),
                        )
                        withStyle(
                            SpanStyle(
                                brush = slideBrush,
                                fontWeight = FontWeight.ExtraBold,
                                shadow = Shadow(accent.copy(alpha = 0.4f * glowIntensity), Offset.Zero, 14f + (4f * progress)),
                            )
                        ) { append(word.text) }
                    } else if (hasWordPassed) {
                        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold, shadow = Shadow(accent.copy(alpha = 0.4f), Offset.Zero, 12f))) {
                            append(word.text)
                        }
                    } else {
                        withStyle(SpanStyle(color = if (!isActiveLine) inactiveColor else accent.copy(alpha = 0.35f), fontWeight = FontWeight.Medium)) {
                            append(word.text)
                        }
                    }
                }

                LyricsAnimationStyle.APPLE_MUSIC -> {
                    val glowIntensity = progress * progress
                    val alpha = when {
                        !isActiveLine -> 0.55f
                        hasWordPassed -> 1f
                        isWordActive -> 0.55f + (0.45f * progress)
                        else -> 0.4f
                    }
                    val weight = when {
                        !isActiveLine -> FontWeight.SemiBold
                        hasWordPassed -> FontWeight.Bold
                        isWordActive -> FontWeight.ExtraBold
                        else -> FontWeight.Normal
                    }
                    val shadow = when {
                        isWordActive -> Shadow(accent.copy(alpha = 0.2f + (0.4f * glowIntensity)), Offset.Zero, 10f + (12f * glowIntensity))
                        hasWordPassed -> Shadow(accent.copy(alpha = 0.2f), Offset.Zero, 8f)
                        else -> null
                    }
                    withStyle(SpanStyle(color = accent.copy(alpha = alpha), fontWeight = weight, shadow = shadow)) { append(word.text) }
                }

                else -> withStyle(SpanStyle(color = accent)) { append(word.text) }
            }
            if (wordIndex < words.lastIndex) append(" ")
        }
    }

    Text(
        text = styled,
        fontSize = textSizeSp.sp,
        lineHeight = (textSizeSp * lineHeightFactor.coerceAtMost(1.3f)).sp,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth(),
    )
}
