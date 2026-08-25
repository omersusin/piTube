package com.omersusin.pitube.ui.lyrics.engine

import com.omersusin.pitube.data.lyrics.LrcLine

/** Word timing in absolute song milliseconds — mapped from [com.omersusin.pitube.data.lyrics.LrcContentSpan]. */
data class EngineWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

fun smoothstep(linear: Float): Float = linear * linear * (3f - 2f * linear)

/** Words with real timings, or null when the line carries none. */
fun LrcLine.toEngineWords(): List<EngineWord>? {
    if (contentSpans.isEmpty()) return null
    return contentSpans.map { sp ->
        EngineWord(
            text = sp.text,
            startMs = sp.timeMs,
            endMs = sp.timeMs + sp.durationMs.coerceAtLeast(50L),
        )
    }
}

/** Char-count estimation mirroring vivi APPLE_V2 fallback (vivi Lyrics.kt:2162-2181). */
fun estimateWords(line: LrcLine, activeDurationMs: Long): List<EngineWord> {
    val words = line.text.split(" ").filter { it.isNotEmpty() }
    if (words.isEmpty()) return listOf(EngineWord(line.text, line.timeMs, line.timeMs + activeDurationMs))
    val totalChars = line.text.length.coerceAtLeast(1)
    var accumulated = line.timeMs
    return words.mapIndexed { i, word ->
        val includeSpace = i < words.lastIndex
        val charCount = word.length + if (includeSpace) 1 else 0
        val dur = (activeDurationMs * charCount.toFloat() / totalChars).toLong()
        val w = EngineWord(word, accumulated, accumulated + dur)
        accumulated += dur
        w
    }
}

/**
 * Inter-line instrumental gaps (vivi Lyrics.kt:2879-2888 uses 4000ms; intro/outro
 * follow ArchiveTune LyricsUtils.insertInstrumentalBreaks thresholds).
 */
data class InstrumentalGap(
    val startMs: Long,
    val durationMs: Long,
)

private const val INTER_LINE_GAP_THRESHOLD_MS = 4000L
private const val INTRO_OUTRO_GAP_THRESHOLD_MS = 5000L
private const val INTRO_START_MS = 1000L
private const val OUTRO_VOCAL_TAIL_MS = 2500L

sealed interface LyricsDisplayItem {
    data class Line(val index: Int, val line: LrcLine) : LyricsDisplayItem
    data class Break(val gap: InstrumentalGap) : LyricsDisplayItem
}

fun buildDisplayItems(lines: List<LrcLine>, songDurationMs: Long = 0L): List<LyricsDisplayItem> {
    val items = mutableListOf<LyricsDisplayItem>()
    if (lines.isEmpty()) return items

    val firstVocal = lines.firstOrNull { it.text.isNotBlank() } ?: lines.first()
    val introGap = firstVocal.timeMs - INTRO_START_MS
    if (introGap >= INTRO_OUTRO_GAP_THRESHOLD_MS) {
        items.add(LyricsDisplayItem.Break(InstrumentalGap(INTRO_START_MS, introGap)))
    }

    lines.forEachIndexed { i, line ->
        items.add(LyricsDisplayItem.Line(i, line))
        val next = lines.getOrNull(i + 1)
        if (next != null) {
            val gap = next.timeMs - line.timeMs
            if (gap >= INTER_LINE_GAP_THRESHOLD_MS) {
                // Hand the stage to the note when the previous vocal ends.
                // CAUTION: LrcParser stretches a missing LAST span duration to
                // the NEXT line's time, so raw lastWordEnd can equal next.timeMs
                // and would erase the note window — clamp both ends:
                //   ≥ fallback hold (don't pop the note mid-singing)
                //   ≤ next - 2000ms (guarantee the note a visible window)
                val minNoteWindow = 2000L
                val maxStart = next.timeMs - minNoteWindow
                val wordEnd = line.contentSpans.maxOfOrNull { it.timeMs + it.durationMs }
                val fallback = line.timeMs + minOf(3500L, gap / 2)
                val startMs = (wordEnd ?: fallback).coerceAtLeast(fallback).coerceAtMost(maxStart)
                val noteWindow = next.timeMs - startMs
                if (noteWindow >= minNoteWindow) {
                    items.add(LyricsDisplayItem.Break(InstrumentalGap(startMs, noteWindow)))
                }
            }
        }
    }

    val lastVocal = lines.lastOrNull { it.text.isNotBlank() }
    if (songDurationMs > 0L && lastVocal != null) {
        val outroStart = lastVocal.timeMs + OUTRO_VOCAL_TAIL_MS
        val outroDur = songDurationMs - outroStart
        if (outroDur >= INTRO_OUTRO_GAP_THRESHOLD_MS) {
            items.add(LyricsDisplayItem.Break(InstrumentalGap(outroStart, outroDur)))
        }
    }
    return items
}
