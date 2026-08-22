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

@Singleton
class LyricsRepository @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val playerPreferences: PlayerPreferences,
    private val context: android.content.Context
) {
    private val memCache = java.util.Collections.synchronizedMap(mutableMapOf<String, List<LrcLine>>())

    suspend fun fetchLyrics(videoId: String, title: String, artist: String, album: String = "", durationMs: Long = 0L): LyricsFetchResult = withContext(Dispatchers.IO) {
        memCache[videoId]?.let { return@withContext LyricsFetchResult.Success(it) }
        loadDiskCache(videoId)?.let { memCache[videoId] = it; return@withContext LyricsFetchResult.Success(it) }
        val order = try { playerPreferences.lyricsProviderOrder.first() } catch (_: Exception) { LyricsProviders.DEFAULT_ORDER }
        // 1) try external providers (transcript included as an orderable provider)
        for (p in LyricsProviders.ordered(order)) {
            try {
                val raw = p.fetch(title, artist, album, durationMs, videoId) ?: continue
                val parsed = LrcParser.parse(raw)
                if (parsed.isNotEmpty()) { memCache[videoId] = parsed; saveDiskCache(videoId, raw); return@withContext LyricsFetchResult.Success(parsed) }
            } catch (e: Exception) { Log.d("LyricsRepository", "provider ${p.id} failed ${e.message}") }
        }
        LyricsFetchResult.NotFound
    }

    /** Persist a manually searched/selected lyric so requestLyrics returns it. */
    fun cacheManual(videoId: String, raw: String) {
        val parsed = LrcParser.parse(raw)
        if (parsed.isNotEmpty()) {
            memCache[videoId] = parsed
            saveDiskCache(videoId, raw)
        }
    }

    fun clearCache() { memCache.clear() }

    private fun cacheFile(videoId: String) = File(File(context.cacheDir, "lyrics"), "$videoId.lrc")
    private fun loadDiskCache(videoId: String): List<LrcLine>? = try {
        val f = cacheFile(videoId); if (!f.exists()) return null; if (System.currentTimeMillis() - f.lastModified() > 30L*24*60*60*1000) return null
        val txt = f.readText(); if (txt.isBlank()) return null; LrcParser.parse(txt).takeIf { it.isNotEmpty() }
    } catch (_: Exception) { null }
    private fun saveDiskCache(videoId: String, raw: String) = try { val f = cacheFile(videoId); f.parentFile?.mkdirs(); f.writeText(raw) } catch (_: Exception) {}
}
