package com.omersusin.pitube.ui.lyrics.engine

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private data class MetroWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val hasTrailingSpace: Boolean,
)

internal fun containsRtl(text: String): Boolean {
    for (c in text) {
        val d = Character.getDirectionality(c)
        if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) return true
    }
    return false
}

internal fun containsComplexScript(text: String): Boolean {
    for (c in text) {
        when (c.code) {
            in 0x0900..0x0FFF, in 0x1000..0x109F, in 0x1780..0x17FF,
            in 0x0600..0x06FF, in 0x0750..0x077F, in 0x08A0..0x08FF,
            in 0xFB50..0xFDFF, in 0xFE70..0xFEFF -> return true
        }
    }
    return false
}

internal fun toGraphemeClusters(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    val it = java.text.BreakIterator.getCharacterInstance()
    it.setText(text)
    var start = it.first()
    var end = it.next()
    while (end != java.text.BreakIterator.DONE) {
        result.add(text.substring(start, end))
        start = end
        end = it.next()
    }
    return result
}

private data class HyphenGroupWord(
    val pos: Int,
    val isLast: Boolean,
    val groupStartMs: Long,
    val groupEndMs: Long,
)

/**
 * Canvas word-level engine ported from vivi MetroLyrics.kt WordLevelCanvasLyrics.
 * Reads the position provider inside the draw scope — per-frame redraws with
 * zero recomposition while the line is active. Includes RTL and complex-script
 * branches, hyphen-group spring math, wobble and BlurMaskFilter glow.
 */
@Composable
fun MetroWordLevelLine(
    mainText: String,
    words: List<EngineWord>,
    isActiveLine: Boolean,
    positionProvider: () -> Long,
    accent: Color,
    baseColor: Color,
    focusedAlpha: Float,
    textSizeSp: Float,
    lineHeightFactor: Float,
    textAlign: TextAlign,
) {
    val textMeasurer = rememberTextMeasurer()
    val glowPaint = remember { android.graphics.Paint().apply { isAntiAlias = true } }

    val lyricStyle = TextStyle(
        fontSize = textSizeSp.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = (textSizeSp * lineHeightFactor).sp,
        letterSpacing = (-0.5).sp,
        textAlign = textAlign,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )

    val metroWords = remember(words) {
        words.mapIndexed { idx, w ->
            MetroWord(w.text, w.startMs, w.endMs, hasTrailingSpace = idx < words.lastIndex)
        }
    }

    val effectiveToOriginalIdx: List<Int> = remember(metroWords) {
        buildList {
            metroWords.forEachIndexed { originalIdx, word ->
                val shouldSplit = word.text.contains('-') && word.text.length > 1 &&
                    (!word.hasTrailingSpace || metroWords.size == 1)
                if (shouldSplit && word.text.count { it == '-' } > 0) {
                    var segments = 0
                    for (i in word.text.indices) if (word.text[i] == '-') segments++
                    if (word.text.last() != '-') segments++
                    repeat(segments.coerceAtLeast(1)) { add(originalIdx) }
                } else add(originalIdx)
            }
        }
    }

    val effectiveWords: List<MetroWord> = remember(metroWords) {
        metroWords.flatMapIndexed { _, word ->
            val shouldSplit = word.text.contains('-') && word.text.length > 1 &&
                (!word.hasTrailingSpace || metroWords.size == 1)
            if (shouldSplit) {
                val segments = mutableListOf<String>()
                var start = 0
                for (i in 0 until word.text.length) {
                    if (word.text[i] == '-') {
                        segments.add(word.text.substring(start, i + 1))
                        start = i + 1
                    }
                }
                if (start < word.text.length) segments.add(word.text.substring(start))

                if (segments.size > 1) {
                    val totalDuration = word.endMs - word.startMs
                    val segmentDuration = totalDuration / segments.size
                    segments.mapIndexed { index, segmentText ->
                        MetroWord(
                            segmentText,
                            word.startMs + index * segmentDuration,
                            word.startMs + (index + 1) * segmentDuration,
                            hasTrailingSpace = if (index == segments.size - 1) word.hasTrailingSpace else false,
                        )
                    }
                } else listOf(word)
            } else listOf(word)
        }
    }

    val graphemeClusters = remember(mainText) { toGraphemeClusters(mainText) }
    val clusterCount = graphemeClusters.size

    val clusterCharOffsets = remember(mainText) {
        IntArray(clusterCount).also { offsets ->
            var charOffset = 0
            graphemeClusters.forEachIndexed { i, cluster ->
                offsets[i] = charOffset
                charOffset += cluster.length
            }
        }
    }

    val charToWordData = remember(mainText, effectiveWords, graphemeClusters, clusterCharOffsets) {
        val wordIdxMap = IntArray(clusterCount) { -1 }
        val charInWordMap = IntArray(clusterCount)
        val wordLenMap = IntArray(clusterCount) { 1 }
        var currentPos = 0
        var clCursor = 0
        effectiveWords.forEachIndexed { wordIdx, word ->
            val indexInMain = mainText.indexOf(word.text, currentPos)
            if (indexInMain != -1) {
                val wordEndInMain = indexInMain + word.text.length
                while (clCursor < clusterCount && clusterCharOffsets[clCursor] < indexInMain) clCursor++
                val wordClusterIndices = mutableListOf<Int>()
                while (clCursor < clusterCount && clusterCharOffsets[clCursor] < wordEndInMain) {
                    wordClusterIndices.add(clCursor)
                    clCursor++
                }
                val wordClusterLen = wordClusterIndices.size
                wordClusterIndices.forEachIndexed { posInWord, clIdx ->
                    wordIdxMap[clIdx] = wordIdx
                    charInWordMap[clIdx] = posInWord
                    wordLenMap[clIdx] = wordClusterLen
                }
                if (clCursor < clusterCount && clusterCharOffsets[clCursor] == wordEndInMain &&
                    wordEndInMain < mainText.length && mainText[wordEndInMain] == ' '
                ) {
                    val spaceClIdx = clCursor
                    wordIdxMap[spaceClIdx] = wordIdx
                    charInWordMap[spaceClIdx] = wordClusterLen
                    wordLenMap[spaceClIdx] = wordClusterLen + 1
                    clCursor++
                }
                currentPos = wordEndInMain
            }
        }
        Triple(wordIdxMap, charInWordMap, wordLenMap)
    }

    val hyphenGroupData = remember(effectiveWords) {
        val map = mutableMapOf<Int, HyphenGroupWord>()
        var currentGroup = mutableListOf<Int>()
        effectiveWords.forEachIndexed { wordIdx, word ->
            currentGroup.add(wordIdx)
            if (!word.text.endsWith("-")) {
                if (currentGroup.size > 1) {
                    val groupStartMs = effectiveWords[currentGroup.first()].startMs
                    val groupEndMs = word.endMs
                    currentGroup.forEachIndexed { pos, idx ->
                        map[idx] = HyphenGroupWord(pos, pos == currentGroup.size - 1, groupStartMs, groupEndMs)
                    }
                }
                currentGroup = mutableListOf()
            }
        }
        map
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        val layoutResult = remember(mainText, maxWidthPx, lyricStyle) {
            textMeasurer.measure(
                text = mainText,
                style = lyricStyle,
                constraints = Constraints(minWidth = maxWidthPx, maxWidth = maxWidthPx),
                softWrap = true,
            )
        }

        val letterLayouts = remember(graphemeClusters, lyricStyle) {
            graphemeClusters.map { cluster -> textMeasurer.measure(cluster, lyricStyle) }
        }

        val isRtlText = remember(mainText) { containsRtl(mainText) }
        val isComplexScript = remember(mainText) { containsComplexScript(mainText) }
        val density = LocalDensity.current

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { layoutResult.size.height.toDp() })
                .graphicsLayer(
                    clip = false,
                    compositingStrategy = CompositingStrategy.Offscreen,
                )
        ) {
            if (mainText.isEmpty()) return@Canvas
            if (!isActiveLine) {
                drawText(layoutResult, color = baseColor)
                return@Canvas
            }

            val smoothPosition = positionProvider()

            val wordFactors = effectiveWords.map { word ->
                val isWordSung = smoothPosition > word.endMs
                val isWordActive = smoothPosition in word.startMs..word.endMs
                val sungFactor = if (isWordSung) 1f
                else if (isWordActive) ((smoothPosition - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                else 0f
                Triple(sungFactor, word, isWordSung)
            }

            if (isRtlText || isComplexScript) {
                val (wordIdxMap, _, _) = charToWordData
                drawText(layoutResult, color = baseColor.copy(alpha = focusedAlpha))

                effectiveWords.indices.forEach { wIdx ->
                    val (sungFactor, _, isWordSung) = wordFactors[wIdx]

                    var left = Float.MAX_VALUE
                    var right = -Float.MAX_VALUE
                    var top = Float.MAX_VALUE
                    var bottom = -Float.MAX_VALUE
                    var found = false
                    for (i in 0 until clusterCount) {
                        if (wordIdxMap[i] == wIdx) {
                            val charOffset = clusterCharOffsets[i]
                            val bounds = layoutResult.getBoundingBox(charOffset)
                            left = minOf(left, bounds.left)
                            right = maxOf(right, bounds.right)
                            top = minOf(top, bounds.top)
                            bottom = maxOf(bottom, bounds.bottom)
                            found = true
                        }
                    }
                    if (found) {
                        if (isWordSung) {
                            clipRect(left = left, top = top, right = right, bottom = bottom) {
                                drawText(layoutResult, color = accent)
                            }
                        } else if (sungFactor > 0f) {
                            if (!isRtlText) {
                                val sweepX = left + (right - left) * sungFactor
                                clipRect(left = left, top = top, right = sweepX, bottom = bottom) {
                                    drawText(layoutResult, color = accent)
                                }
                                clipRect(left = sweepX, top = top, right = right, bottom = bottom) {
                                    drawText(layoutResult, color = baseColor.copy(alpha = focusedAlpha))
                                }
                            } else {
                                clipRect(left = left, top = top, right = right, bottom = bottom) {
                                    drawText(layoutResult, color = accent.copy(alpha = focusedAlpha + (1f - focusedAlpha) * sungFactor))
                                }
                            }
                        }
                    }
                }
                return@Canvas
            }

            val (wordIdxMap, charInWordMap, wordLenMap) = charToWordData

            val wordWobbles = FloatArray(metroWords.size)
            metroWords.forEachIndexed { wordIdx, word ->
                val timeSinceStart = (smoothPosition - word.startMs).toFloat()
                wordWobbles[wordIdx] = if (timeSinceStart in 0f..750f) {
                    if (timeSinceStart < 125f) timeSinceStart / 125f
                    else (1f - (timeSinceStart - 125f) / 625f).coerceAtLeast(0f)
                } else 0f
            }

            val lineCurrentPushes = FloatArray(layoutResult.lineCount)
            val lineTotalPushes = FloatArray(layoutResult.lineCount)

            for (i in 0 until clusterCount) {
                val charOffset = clusterCharOffsets[i]
                val lineIdx = layoutResult.getLineForOffset(charOffset)
                val wordIdx = wordIdxMap[i]
                val originalWordIdx = if (wordIdx != -1) effectiveToOriginalIdx.getOrElse(wordIdx) { -1 } else -1
                val (sungFactor, wordItem, isWordSung) = if (wordIdx != -1) wordFactors[wordIdx] else Triple(0f, null, false)
                val wobble = if (originalWordIdx != -1) wordWobbles[originalWordIdx] else 0f
                var crescendoDeltaX = 0f
                val groupWord = if (wordIdx != -1) hyphenGroupData[wordIdx] else null
                if (groupWord != null) {
                    val p = sungFactor
                    val pOut = ((smoothPosition - groupWord.groupEndMs).toFloat() / 600f).coerceIn(0f, 1f)
                    val peakScale = 0.06f
                    val baseScalePerSegment = 0.012f
                    if (pOut > 0f) {
                        crescendoDeltaX = (groupWord.pos * baseScalePerSegment + peakScale) *
                            exp(-2.5f * pOut) * cos(10f * pOut * PI.toFloat()) * (1f - pOut)
                    } else if (groupWord.isLast) {
                        val base = groupWord.pos * baseScalePerSegment
                        crescendoDeltaX = base + peakScale * (1f - exp(-2.5f * p) * cos(10f * p * PI.toFloat()) * (1f - p))
                    } else {
                        val boost = if (p > 0f) 0.02f * (1f - p) else 0f
                        crescendoDeltaX = (groupWord.pos * baseScalePerSegment) + boost
                    }
                }

                val charLp = if (wordItem != null) {
                    val dur = (wordItem.endMs - wordItem.startMs).coerceAtLeast(100L).toFloat()
                    val wProg = (smoothPosition - wordItem.startMs) / dur
                    ((wProg - charInWordMap[i].toFloat() / wordLenMap[i]) * wordLenMap[i]).coerceIn(0f, 1f)
                } else 0f

                val nudgeScale = if (wordItem != null && !isWordSung && sungFactor > 0f) {
                    0.038f * sin(charLp * PI.toFloat()) * exp(-3f * charLp)
                } else 0f

                val charScaleX = 1f + (wobble * 0.025f) + crescendoDeltaX + (nudgeScale * 0.3f)
                val charBounds = layoutResult.getBoundingBox(charOffset)
                lineTotalPushes[lineIdx] += charBounds.width * (charScaleX - 1f)
            }

            for (i in 0 until clusterCount) {
                val charOffset = clusterCharOffsets[i]
                val lineIdx = layoutResult.getLineForOffset(charOffset)
                val charBounds = layoutResult.getBoundingBox(charOffset)
                val wordIdx = wordIdxMap[i]
                val originalWordIdx = if (wordIdx != -1) effectiveToOriginalIdx.getOrElse(wordIdx) { -1 } else -1

                val alignShift = when (textAlign) {
                    TextAlign.Center -> -lineTotalPushes[lineIdx] / 2f
                    TextAlign.Right -> -lineTotalPushes[lineIdx]
                    else -> 0f
                }

                val (sungFactor, wordItem, isWordSung) = if (wordIdx != -1) wordFactors[wordIdx] else Triple(0f, null, false)
                val wobble = if (originalWordIdx != -1) wordWobbles[originalWordIdx] else 0f
                val wobbleX = wobble * 0.025f
                val wobbleY = wobble * 0.015f

                val charLp = if (wordItem != null) {
                    val dur = (wordItem.endMs - wordItem.startMs).coerceAtLeast(100L).toFloat()
                    val wProg = (smoothPosition - wordItem.startMs) / dur
                    ((wProg - charInWordMap[i].toFloat() / wordLenMap[i]) * wordLenMap[i]).coerceIn(0f, 1f)
                } else 0f

                val shouldGlow = wordItem != null && !isWordSung && sungFactor > 0.001f

                var crescendoDeltaX = 0f
                var crescendoDeltaY = 0f
                val groupWord = if (wordIdx != -1) hyphenGroupData[wordIdx] else null
                if (groupWord != null) {
                    val p = sungFactor
                    val pOut = ((smoothPosition - groupWord.groupEndMs).toFloat() / 600f).coerceIn(0f, 1f)
                    val peakScale = 0.06f
                    val baseScalePerSegment = 0.012f
                    if (pOut > 0f) {
                        val springOut = (groupWord.pos * baseScalePerSegment + peakScale) *
                            exp(-3.5f * pOut) * cos(5f * pOut * PI.toFloat()) * (1f - pOut)
                        crescendoDeltaX = springOut
                        crescendoDeltaY = springOut
                    } else if (groupWord.isLast) {
                        val base = groupWord.pos * baseScalePerSegment
                        val springPart = peakScale * (1f - exp(-3.5f * p) * cos(5f * p * PI.toFloat()) * (1f - p))
                        crescendoDeltaX = base + springPart
                        crescendoDeltaY = base + springPart
                    } else {
                        val boost = if (p > 0f) 0.02f * (1f - p) else 0f
                        val base = (groupWord.pos * baseScalePerSegment) + boost
                        crescendoDeltaX = base
                        crescendoDeltaY = base
                    }
                }

                val nudgeScale = if (wordItem != null && !isWordSung && sungFactor > 0f) {
                    0.038f * sin(charLp * PI.toFloat()) * exp(-3f * charLp)
                } else 0f

                val charScaleX = 1f + wobbleX + crescendoDeltaX + nudgeScale * 0.3f
                val charScaleY = 1f + wobbleY + crescendoDeltaY + nudgeScale

                withTransform({
                    var waveOffset = 0f
                    if (groupWord != null) {
                        val wallTime = System.currentTimeMillis()
                        val timeInGroup = (smoothPosition - groupWord.groupStartMs).toFloat()
                        val timeToGroupEnd = (groupWord.groupEndMs - smoothPosition).toFloat()
                        val waveFade = (timeInGroup / 200f).coerceIn(0f, 1f) * (timeToGroupEnd / 200f).coerceIn(0f, 1f)
                        if (waveFade > 0.01f) {
                            waveOffset = sin(wallTime * 0.006f + i * 0.4f) * 3.24f * waveFade
                        }
                    }

                    translate(left = alignShift + lineCurrentPushes[lineIdx] + charBounds.left, top = charBounds.top + waveOffset)
                    if (wordIdx != -1) {
                        scale(charScaleX, charScaleY, pivot = Offset(charBounds.width / 2f, charBounds.height))
                    }
                }) {
                    if (shouldGlow && wordItem != null) {
                        val dur = wordItem.endMs - wordItem.startMs
                        val wordLenText = wordItem.text.length.coerceAtLeast(1)
                        val impactRatio = dur.toFloat() / wordLenText
                        val fadeFactor = (sungFactor * 5f).coerceIn(0f, 1f) * ((1f - sungFactor) * 8f).coerceIn(0f, 1f)
                        val impactFactor = (((impactRatio - 100f) / 250f).coerceIn(0f, 1f) * 0.6f +
                            ((dur - 300f) / 1500f).coerceIn(0f, 1f) * 0.4f).coerceIn(0f, 1f) * fadeFactor
                        if (impactFactor > 0.01f) {
                            val glowAlpha = (0.35f * impactFactor).coerceIn(0f, 0.4f)
                            val baseGlowRadius = 12.dp.toPx() * impactFactor
                            drawIntoCanvas { canvas ->
                                glowPaint.maskFilter = BlurMaskFilter(baseGlowRadius, BlurMaskFilter.Blur.NORMAL)
                                glowPaint.color = accent.copy(alpha = glowAlpha).toArgb()
                                glowPaint.textSize = lyricStyle.fontSize.toPx()
                                glowPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                canvas.nativeCanvas.drawText(letterLayouts[i].layoutInput.text.text, 0f, letterLayouts[i].firstBaseline, glowPaint)
                            }
                        }
                    }
                    val baseAlpha = if (isWordSung || charLp > 0.99f) 1f else (focusedAlpha + (1f - focusedAlpha) * sungFactor)
                    drawText(letterLayouts[i], color = accent.copy(alpha = if (wordIdx == -1) focusedAlpha else baseAlpha))
                    if (!isWordSung && charLp > 0f && charLp < 1f) {
                        val fillEdgeLeft = charBounds.width * charLp
                        val edgeWidth = (charBounds.width * 0.45f).coerceAtLeast(1f)
                        val solidWidthLeft = (fillEdgeLeft - edgeWidth).coerceAtLeast(0f)
                        if (solidWidthLeft > 0f) {
                            clipRect(left = 0f, top = 0f, right = solidWidthLeft, bottom = charBounds.height) { drawText(letterLayouts[i], color = accent) }
                        }
                        for (j in 0 until 12) {
                            val start = solidWidthLeft + (j * edgeWidth / 12f)
                            val end = (solidWidthLeft + ((j + 1) * edgeWidth / 12f) + 0.5f).coerceAtMost(fillEdgeLeft)
                            if (end > start) {
                                clipRect(left = start, top = 0f, right = end, bottom = charBounds.height) {
                                    drawText(letterLayouts[i], color = accent.copy(alpha = 1f - (j + 0.5f) / 12f))
                                }
                            }
                        }
                    }
                }
                lineCurrentPushes[lineIdx] += charBounds.width * (charScaleX - 1f)
            }
        }
    }
}
