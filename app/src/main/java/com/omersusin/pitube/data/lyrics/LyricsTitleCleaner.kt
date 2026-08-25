package com.omersusin.pitube.data.lyrics

import com.omersusin.pitube.utils.TitleDecorationStripper

/**
 * Song-title/artist cleaner for lyrics fetching and manual-search prefill.
 * Patterns ported from vivi-music's LrcLib.kt:39-73 (keyword-gated bracket
 * stripping) + Paxsenix's year-tag regex + an "A, B - C" video-title splitter
 * so plain-YouTube titles resolve to (title="Science", artist="deadmau5").
 */
object LyricsTitleCleaner {

    private val titleCleanupPatterns = listOf(
        Regex("\\s*\\(.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\\)", RegexOption.IGNORE_CASE),
        Regex("\\s*\\[.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?]", RegexOption.IGNORE_CASE),
        Regex("\\s*【.*?】"),
        Regex("\\s*\\|.*$"),
        Regex("\\s*-\\s*(official|video|audio|lyrics|lyric|visualizer).*$", RegexOption.IGNORE_CASE),
        Regex("\\s*\\(feat\\..*?\\)", RegexOption.IGNORE_CASE),
        Regex("\\s*\\(ft\\..*?\\)", RegexOption.IGNORE_CASE),
        Regex("\\s*feat\\..*$", RegexOption.IGNORE_CASE),
        Regex("\\s*ft\\..*$", RegexOption.IGNORE_CASE),
        // Year-tag extras (Paxsenix): "(...2024...)" and bare "(Official Audio 2021)"
        Regex("\\s*\\([^)]*\\d{4}[^)]*\\)", RegexOption.IGNORE_CASE),
    )

    private val artistSeparators = listOf(
        " & ", " and ", ", ", " x ", " X ", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " with ",
    )

    private val DASH_SPLIT = Regex("^(.+?)\\s*[-\u2013\u2014]\\s*(.+)$")

    fun cleanTitle(title: String): String {
        var cleaned = title.trim()
        for (pattern in titleCleanupPatterns) cleaned = cleaned.replace(pattern, "")
        return cleaned.trim()
    }

    /** Lead artist only — LRCLIB indexes primary artists, not feat. chains. */
    fun primaryArtist(artist: String): String {
        var cleaned = artist.trim()
        for (separator in artistSeparators) {
            if (cleaned.contains(separator, ignoreCase = true)) {
                cleaned = cleaned.split(Regex(Regex.escape(separator), RegexOption.IGNORE_CASE), limit = 2)[0]
                break
            }
        }
        return cleaned.trim()
    }

    /**
     * Split a raw YouTube video title into (cleanTitle, primaryArtist).
     * "deadmau5, Stevie Appleton - Science [Official Video]" → ("Science", "deadmau5").
     * Titles without an artist dash fall back to the cleaned full title with
     * an empty artist part (caller substitutes the channel name).
     */
    fun splitVideoTitle(rawTitle: String): Pair<String, String> {
        val stripped = TitleDecorationStripper.stripAll(rawTitle)
        val m = DASH_SPLIT.find(stripped)
        if (m != null) {
            val left = m.groupValues[1].trim()
            val right = cleanTitle(m.groupValues[2].trim())
            if (right.isNotBlank() && left.isNotBlank()) {
                return right to primaryArtist(left)
            }
        }
        return cleanTitle(stripped) to ""
    }
}
