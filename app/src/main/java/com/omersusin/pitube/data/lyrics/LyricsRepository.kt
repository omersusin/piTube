package com.omersusin.pitube.data.lyrics

import android.util.Log
import com.omersusin.pitube.data.repository.YouTubeRepository
import com.omersusin.pitube.data.local.PlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LyricsRepository"

@Singleton
class LyricsRepository @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val playerPreferences: PlayerPreferences,
    private val context: android.content.Context
) {
    private val memCache = java.util.Collections.synchronizedMap(mutableMapOf<String, List<LrcLine>>())
    private val plainMemCache = java.util.Collections.synchronizedMap(mutableMapOf<String, String>())

    suspend fun fetchLyrics(videoId: String, title: String, artist: String, album: String = "", durationMs: Long = 0L): LyricsFetchResult = withContext(Dispatchers.IO) {
        memCache[videoId]?.let { return@withContext LyricsFetchResult.Success(it) }
        plainMemCache[videoId]?.let { return@withContext LyricsFetchResult.Plain(it) }
        loadDiskCache(videoId)?.let { memCache[videoId] = it; return@withContext LyricsFetchResult.Success(it) }
        val order = try { playerPreferences.lyricsProviderOrder.first() } catch (_: Exception) { LyricsProviders.DEFAULT_ORDER }
        // Fallback ladder (vivi LrcLib strategy, adapted): pass 1 queries with
        // the CLEANED title + lead artist — plain-YouTube titles like
        // "deadmau5, Stevie Appleton - Science [Official Video]" never match
        // LRCLIB verbatim; pass 2 retries with the raw strings when they differ.
        val cleanedTitle = LyricsTitleCleaner.cleanTitle(title).ifBlank { title }
        val cleanedArtist = LyricsTitleCleaner.primaryArtist(artist).ifBlank { artist }
        val attempts = mutableListOf(cleanedTitle to cleanedArtist)
        if (title.isNotBlank() && (title to artist) != (cleanedTitle to cleanedArtist)) attempts.add(title to artist)
        var lastResort: LyricsFetchResult? = null
        for ((t, a) in attempts) {
            val res = fetchFromProviders(order, t, a, album, durationMs, videoId)
            if (res !is LyricsFetchResult.NotFound && res !is LyricsFetchResult.Error) return@withContext res
            if (lastResort == null) lastResort = res
        }
        lastResort ?: LyricsFetchResult.NotFound
    }

    private suspend fun fetchFromProviders(
        order: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        videoId: String,
    ): LyricsFetchResult {
        for (p in LyricsProviders.ordered(order)) {
            try {
                val raw = p.fetch(title, artist, album, durationMs, videoId) ?: continue
                val parsed = LrcParser.parse(raw)
                if (parsed.isNotEmpty()) {
                    if (hasUsableSync(parsed)) {
                        memCache[videoId] = parsed; saveDiskCache(videoId, raw)
                        return LyricsFetchResult.Success(parsed)
                    } else {
                        // Degenerate "sync" (e.g. description lyrics stamped all at
                        // 00:00.00) would freeze the view on the last line — show
                        // it as plain text instead.
                        Log.d(TAG, "provider ${p.id} produced unsynced lyrics for $videoId")
                        val text = parsed.joinToString("\n") { it.text }
                        plainMemCache[videoId] = text; saveDiskCache(videoId, raw)
                        return LyricsFetchResult.Plain(text)
                    }
                }
            } catch (e: Exception) { Log.d(TAG, "provider ${p.id} failed ${e.message}") }
        }
        return LyricsFetchResult.NotFound
    }

    /**
     * True when the parsed lines carry a real timeline. A provider that stamps
     * every line at the same instant (typically 00:00.00) yields a perfectly
     * sorted list that always highlights the LAST line — useless as sync.
     */
    private fun hasUsableSync(lines: List<LrcLine>): Boolean {
        if (lines.size <= 1) return false
        val distinctTimes = lines.map { it.timeMs }.toSet()
        return distinctTimes.size > (lines.size / 4).coerceAtLeast(1)
    }

    /** Persist a manually searched/selected lyric so requestLyrics returns it. */
    fun cacheManual(videoId: String, raw: String) {
        val parsed = LrcParser.parse(raw)
        if (parsed.isEmpty()) return
        saveDiskCache(videoId, raw)
        if (hasUsableSync(parsed)) {
            memCache[videoId] = parsed
            plainMemCache.remove(videoId)
        } else {
            memCache.remove(videoId)
            plainMemCache[videoId] = parsed.joinToString("\n") { it.text }
        }
    }

    fun clearCache() { memCache.clear(); plainMemCache.clear() }

    /** Drop every cached copy of one song so a forced refetch hits providers. */
    fun clearCacheFor(videoId: String) {
        memCache.remove(videoId); plainMemCache.remove(videoId)
        try { cacheFile(videoId).delete() } catch (_: Exception) {}
    }

    private fun cacheFile(videoId: String) = File(File(context.cacheDir, "lyrics"), "$videoId.lrc")
    private fun loadDiskCache(videoId: String): List<LrcLine>? = try {
        val f = cacheFile(videoId); if (!f.exists()) return null; if (System.currentTimeMillis() - f.lastModified() > 30L*24*60*60*1000) return null
        val txt = f.readText(); if (txt.isBlank()) return null; LrcParser.parse(txt).takeIf { it.isNotEmpty() }
    } catch (_: Exception) { null }
    private fun saveDiskCache(videoId: String, raw: String) = try { val f = cacheFile(videoId); f.parentFile?.mkdirs(); f.writeText(raw) } catch (_: Exception) {}
}
