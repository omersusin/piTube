package com.omersusin.pitube.data.lyrics

import android.util.Log
import java.util.Collections
import java.util.Locale

/**
 * Song-lyrics translation layer. Resolves a track on Musixmatch and pulls its
 * synced translation for the target language (defaults to the app/system
 * locale). Parsed with the same [LrcParser] so the lyrics view can match each
 * translated line by timeMs against the original.
 */
object LyricsTranslationRepository {
    private const val TAG = "LyricsTranslation"

    /** cache key = "$videoId|$lang" → parsed translated lines */
    private val cache = Collections.synchronizedMap(mutableMapOf<String, List<LrcLine>>())
    private val inFlight = Collections.synchronizedSet(mutableSetOf<String>())

    fun targetLanguage(): String = runCatching {
        val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) locales[0]!!.language else Locale.getDefault().language
    }.getOrDefault(Locale.getDefault().language)

    /**
     * Translated lines for the video, or null when unavailable/off.
     * [title]/[artist] come from the playing video metadata.
     */
    suspend fun translate(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        enabled: Boolean,
    ): List<LrcLine>? {
        if (!enabled || title.isBlank() || artist.isBlank()) return null
        val lang = targetLanguage()
        if (lang.isBlank() || lang == "en") return null // source language — nothing to do
        val key = "$videoId|$lang"
        cache[key]?.let { return it.ifEmpty { null } }
        if (!inFlight.add(key)) return null
        try {
            val raw = MusixmatchLyricsProvider().fetchTranslation(title, artist, durationMs, lang)
                ?: return null
            val parsed = LrcParser.parse(raw)
            cache[key] = parsed
            return parsed.ifEmpty { null }
        } catch (e: Exception) {
            Log.d(TAG, "translation failed for $videoId: ${e.message}")
            cache[key] = emptyList()
            return null
        } finally {
            inFlight.remove(key)
        }
    }

    fun clearCache() = cache.clear()
}
