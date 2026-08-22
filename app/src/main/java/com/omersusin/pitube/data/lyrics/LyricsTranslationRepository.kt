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

    /** Target language follows the app/system locale (2-letter code). */
    fun targetLanguage(): String = Locale.getDefault().language

    /**
     * Translated lines for the video, or null when unavailable/off.
     * [title]/[artist] come from the playing video metadata.
     *
     * [machineTranslate] is the app-wide translation entry point used as the
     * fallback when Musixmatch has no synced translation for the track: each
     * original line is translated individually and its timestamp preserved.
     */
    suspend fun translate(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        enabled: Boolean,
        machineTranslate: (suspend (text: String, targetLang: String) -> String?)? = null,
        sourceLines: List<LrcLine> = emptyList(),
    ): List<LrcLine>? {
        if (!enabled || title.isBlank() || artist.isBlank()) return null
        val lang = targetLanguage()
        if (lang.isBlank() || lang == "en") return null // source language — nothing to do
        val key = "$videoId|$lang"
        cache[key]?.let { return it.ifEmpty { null } }
        if (!inFlight.add(key)) return null
        try {
            val raw = MusixmatchLyricsProvider().fetchTranslation(title, artist, durationMs, lang)
            val parsed = raw?.let { LrcParser.parse(it) }
            if (!parsed.isNullOrEmpty()) {
                cache[key] = parsed
                return parsed
            }
            // Fallback: line-by-line machine translation of the synced lyrics,
            // keeping every original timestamp so the view can match lines.
            val fallback = machineTranslateFallback(videoId, sourceLines, lang, machineTranslate)
            cache[key] = fallback.orEmpty()
            return fallback
        } catch (e: Exception) {
            Log.d(TAG, "translation failed for $videoId: ${e.message}")
            cache[key] = emptyList()
            return null
        } finally {
            inFlight.remove(key)
        }
    }

    /** Cap the work so a 200-line song cannot fire 200 sequential requests. */
    private const val MACHINE_FALLBACK_MAX_LINES = 60

    private suspend fun machineTranslateFallback(
        videoId: String,
        sourceLines: List<LrcLine>,
        lang: String,
        machineTranslate: (suspend (text: String, targetLang: String) -> String?)?,
    ): List<LrcLine>? {
        val translator = machineTranslate ?: run {
            Log.d(TAG, "no machine translator available for $videoId — giving up")
            return null
        }
        if (sourceLines.isEmpty()) return null
        val out = mutableListOf<LrcLine>()
        for (line in sourceLines.take(MACHINE_FALLBACK_MAX_LINES)) {
            val text = line.text.trim()
            if (text.isEmpty()) continue
            try {
                val translated = translator(text, lang) ?: continue
                out.add(LrcLine(line.timeMs, translated))
            } catch (e: Exception) {
                Log.d(TAG, "machine line translation failed at ${line.timeMs}: ${e.message}")
            }
        }
        Log.d(TAG, "machine-translation fallback for $videoId produced ${out.size}/${sourceLines.size} lines")
        return out.takeIf { it.isNotEmpty() }
    }

    fun clearCache() = cache.clear()
}
