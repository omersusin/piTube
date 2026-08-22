package com.omersusin.pitube.data.lyrics

data class LrcContentSpan(val timeMs: Long, val text: String, val durationMs: Long = 0L)
data class LrcLine(val timeMs: Long, val text: String, val contentSpans: List<LrcContentSpan> = emptyList())

sealed interface LyricsFetchResult {
    data class Success(val lines: List<LrcLine>) : LyricsFetchResult

    /** Unsynced text: usable for display, but has no per-line timing. */
    data class Plain(val text: String) : LyricsFetchResult
    data object NotFound : LyricsFetchResult
    data class Error(val message: String) : LyricsFetchResult
}
