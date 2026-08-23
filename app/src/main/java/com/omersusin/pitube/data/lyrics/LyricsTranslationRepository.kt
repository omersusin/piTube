package com.omersusin.pitube.data.lyrics

import android.content.Context
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

    /**
     * Disk sidecar for translations ("$videoId.$lang.lrc") so a translation
     * survives process death — the in-memory [cache] alone forgets everything.
     */
    private fun translationCacheFile(context: Context?, videoId: String, lang: String): java.io.File? {
        if (context == null) return null
        return try {
            val dir = java.io.File(context.cacheDir, "lyrics")
            dir.mkdirs()
            java.io.File(dir, "$videoId.$lang.lrc")
        } catch (_: Exception) {
            null
        }
    }

    private fun loadTranslationDisk(context: Context?, videoId: String, lang: String): List<LrcLine>? = try {
        val f = translationCacheFile(context, videoId, lang) ?: return null
        if (!f.exists()) return null
        val txt = f.readText()
        if (txt.isBlank()) null else LrcParser.parse(txt).takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    private fun saveTranslationDisk(context: Context?, videoId: String, lang: String, lines: List<LrcLine>) {
        try {
            val f = translationCacheFile(context, videoId, lang) ?: return
            f.writeText(lines.joinToString("\n") { line ->
                val totalSec = line.timeMs / 1000
                "[%02d:%02d.%03d]%s".format(totalSec / 60, totalSec % 60, line.timeMs % 1000, line.text)
            })
        } catch (_: Exception) {
        }
    }

    /** Target language follows the app/system locale (2-letter code). */
    fun targetLanguage(): String = Locale.getDefault().language

    /**
     * Translated lines for the video, or null when unavailable/off.
     * [title]/[artist] come from the playing video metadata.
     *
     * [targetLangOverride] lets a settings preference pin the translation
     * language; blank/null follows the app/system locale.
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
        targetLangOverride: String? = null,
        machineTranslate: (suspend (text: String, targetLang: String) -> String?)? = null,
        sourceLines: List<LrcLine> = emptyList(),
        context: android.content.Context? = null,
    ): List<LrcLine>? {
        if (!enabled || title.isBlank() || artist.isBlank()) return null
        val lang = targetLangOverride?.trim()?.takeIf { it.isNotBlank() } ?: targetLanguage()
        if (lang.isBlank() || lang == "en") return null // source language — nothing to do
        // Disk sidecar first: translations survive process death.
        val key = "$videoId|$lang"
        loadTranslationDisk(context, videoId, lang)?.let { cached ->
            cache[key] = cached
            return cached
        }
        cache[key]?.let { return it.ifEmpty { null } }
        if (!inFlight.add(key)) return null
        try {
            val raw = MusixmatchLyricsProvider().fetchTranslation(title, artist, durationMs, lang)
            val parsed = raw?.let { LrcParser.parse(it) }
            // Musixmatch sometimes answers a missing translation with the
            // SOURCE subtitle — which used to be shown as a fake "translation"
            // identical to the lyrics. Reject anything that overlaps the
            // original lines too much and fall through to machine translation.
            val usable = if (!parsed.isNullOrEmpty() && looksLikeSourceCopy(parsed, sourceLines)) {
                Log.d(TAG, "musixmatch returned the source lyrics as 'translation' — rejecting")
                null
            } else {
                parsed
            }
            if (!usable.isNullOrEmpty()) {
                cache[key] = usable
                saveTranslationDisk(context, videoId, lang, usable)
                return usable
            }
            // Fallback: line-by-line machine translation of the synced lyrics,
            // keeping every original timestamp so the view can match lines.
            val fallback = machineTranslateFallback(videoId, sourceLines, lang, machineTranslate)
            cache[key] = fallback.orEmpty()
            fallback?.let { saveTranslationDisk(context, videoId, lang, it) }
            return fallback
        } catch (e: Exception) {
            Log.d(TAG, "translation failed for $videoId: ${e.message}")
            cache[key] = emptyList()
            return null
        } finally {
            inFlight.remove(key)
        }
    }

    /**
     * True when [candidate]'s texts substantially duplicate [sourceLines]
     * (same timestamps AND same text on most shared lines) — i.e. it is the
     * original lyric, not a translation.
     */
    private fun looksLikeSourceCopy(
        candidate: List<LrcLine>,
        sourceLines: List<LrcLine>,
    ): Boolean {
        if (sourceLines.isEmpty()) return false
        val sourceByText = sourceLines.associate { it.timeMs to it.text.trim() }
        var shared = 0
        var identical = 0
        candidate.forEach { line ->
            val original = sourceByText[line.timeMs] ?: return@forEach
            shared++
            if (original.equals(line.text.trim(), ignoreCase = true)) identical++
        }
        return shared > 0 && identical >= (shared * 4) / 5
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
                val translated = translator(text, lang)?.trim() ?: continue
                // An engine echoing its input is not a translation.
                if (translated.equals(text, ignoreCase = true)) continue
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
