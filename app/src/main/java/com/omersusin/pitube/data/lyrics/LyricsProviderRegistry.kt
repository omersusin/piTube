package com.omersusin.pitube.data.lyrics

/**
 * Single source of truth for provider id → display name (vivi-music's
 * LyricsProviderRegistry concept). Settings, manual-search results and the
 * lyrics sheet header all render names from here instead of raw ids.
 */
object LyricsProviderRegistry {
    val DISPLAY_NAMES: Map<String, String> = mapOf(
        "lrclib" to "LRCLIB",
        "betterlyrics" to "BetterLyrics",
        "betterlyricsportato" to "BetterLyrics Portato",
        "musixmatch" to "Musixmatch",
        "simpmusic" to "SimpMusic",
        "paxsenix" to "Paxsenix: Apple Music",
        "paxsenix-netease" to "Paxsenix: NetEase",
        "paxsenix-spotify" to "Paxsenix: Spotify",
        "paxsenix-youtube" to "Paxsenix: YouTube",
        "paxsenix-musixmatch" to "Paxsenix: Musixmatch",
        "kugou" to "KuGou",
        "youlyplus" to "YouLyPlus",
        "unison" to "Unison",
        "megalobiz" to "Megalobiz",
        "transcript" to "YouTube Transcript",
    )

    fun displayName(id: String): String = DISPLAY_NAMES[id] ?: id

    /** Catalog in registry order — settings screen listing. */
    val CATALOG: List<Pair<String, String>> =
        DISPLAY_NAMES.entries.map { it.key to it.value }
}
