package com.omersusin.pitube.ui.lyrics.engine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omersusin.pitube.data.lyrics.LrcLine

/**
 * Letter-by-letter engine ported from vivi-music Lyrics.kt:2146-2251.
 * Word timings drive per-char interpolation; char-count estimation when absent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppleV2LetterLine(
    line: LrcLine,
    isActiveLine: Boolean,
    positionMs: Long,
    accent: Color,
    inactiveColor: Color,
    textSizeSp: Float,
    lineHeightFactor: Float,
    textAlign: TextAlign = TextAlign.Center,
) {
    val nextEstimate = 4000L
    val activeDuration = (nextEstimate * 0.95).toLong().coerceAtLeast(300L)

    val wordData = remember(line, activeDuration) {
        line.toEngineWords() ?: estimateWords(line, activeDuration)
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
        wordData.forEachIndexed { wordIndex, word ->
            val lineRelTime = (positionMs - line.timeMs).coerceAtLeast(0L)
            val relStart = word.startMs - line.timeMs
            val relEnd = word.endMs - line.timeMs
            val charDuration = if (word.text.isNotEmpty()) (relEnd - relStart) / word.text.length else 0L

            Row(verticalAlignment = Alignment.Bottom) {
                word.text.forEachIndexed { charIndex, ch ->
                    val charStart = relStart + (charIndex * charDuration)
                    val charEnd = charStart + charDuration
                    val charProgress = when {
                        !isActiveLine -> 0f
                        lineRelTime >= charEnd -> 1f
                        lineRelTime < charStart -> 0f
                        charDuration <= 0L -> 1f
                        else -> (lineRelTime - charStart).toFloat() / charDuration
                    }
                    Text(
                        text = ch.toString(),
                        fontSize = textSizeSp.sp,
                        color = if (!isActiveLine) inactiveColor
                        else accent.copy(alpha = if (charProgress >= 1f) 1f else 0.3f + (0.7f * charProgress)),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    )
                }
                if (wordIndex < wordData.lastIndex) {
                    Text(text = " ", fontSize = textSizeSp.sp, letterSpacing = (-0.5).sp)
                }
            }
        }
    }
}
