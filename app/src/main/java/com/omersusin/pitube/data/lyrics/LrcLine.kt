package com.omersusin.pitube.data.lyrics

data class LrcContentSpan(val timeMs: Long, val text: String, val durationMs: Long = 0L)
data class LrcLine(val timeMs: Long, val text: String, val contentSpans: List<LrcContentSpan> = emptyList())

sealed interface LyricsFetchResult {
    data class Success(val lines: List<LrcLine>) : LyricsFetchResult
    data object NotFound : LyricsFetchResult
    data class Error(val message: String) : LyricsFetchResult
}
