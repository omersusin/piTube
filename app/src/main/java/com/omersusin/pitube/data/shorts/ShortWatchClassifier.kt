/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package com.omersusin.pitube.data.shorts

/** Classified watch outcome for a Short. */
data class ShortWatchSignal(
    val position: Long,
    val safeDuration: Long,
    val percent: Float,
)

/** Pure watch-signal classification for Shorts, extracted from the ViewModel for testability. */
object ShortWatchClassifier {
    // Quick flicks below this count as skips, not watches.
    const val MIN_SHORT_WATCH_MS = 2_000L

    fun classify(positionMs: Long, durationMs: Long, videoDurationSec: Int): ShortWatchSignal {
        val safeDuration = when {
            durationMs > 0L -> durationMs
            videoDurationSec > 0 -> videoDurationSec * 1000L
            else -> positionMs.coerceAtLeast(1_000L)
        }
        val position = positionMs.coerceIn(0L, safeDuration)
        val percent = (position.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
        return ShortWatchSignal(position, safeDuration, percent)
    }
}
