package com.omersusin.pitube.data.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val TAG = "LyricsProviders"

abstract class LyricsProvider {
    abstract val id: String
    abstract suspend fun fetch(title: String, artist: String, album: String, durationMs: Long): String?
}

class LrclibLyricsProvider(private val client: OkHttpClient = defaultClient()) : LyricsProvider() {
    override val id = "lrclib"
    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long): String? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val durSec = (durationMs / 1000).toInt()
        getExact(title, artist, album, durSec) ?: search(title, artist, durSec)
    }
    private fun getExact(title: String, artist: String, album: String, durSec: Int): String? {
        if (title.isBlank() || artist.isBlank() || durSec <= 0) return null
        val b = android.net.Uri.parse("https://lrclib.net/api/get").buildUpon()
            .appendQueryParameter("track_name", title)
            .appendQueryParameter("artist_name", artist)
            .appendQueryParameter("duration", durSec.toString())
        if (album.isNotBlank()) b.appendQueryParameter("album_name", album)
        val j = fetchJson(b.build().toString()) ?: return null
        if (j.optBoolean("instrumental", false)) return null
        return syncedOf(j)
    }
    private fun search(title: String, artist: String, durSec: Int): String? {
        val url = android.net.Uri.parse("https://lrclib.net/api/search").buildUpon().appendQueryParameter("q", "$title $artist").build().toString()
        val arr = fetchJsonArray(url) ?: return null
        var best: JSONObject? = null; var bestDiff = Int.MAX_VALUE
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val synced = syncedOf(item) ?: continue
            val d = abs(item.optInt("duration", 0) - durSec)
            if (d <= 3) return synced
            if (d < bestDiff) { bestDiff = d; best = item }
        }
        return if (best != null && bestDiff <= 10) syncedOf(best) else null
    }
    private fun syncedOf(j: JSONObject): String? { if (j.isNull("syncedLyrics")) return null; return j.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" } }
    private fun fetchJson(url: String): JSONObject? = try {
        val r = Request.Builder().url(url).header("User-Agent", "piTube/1.0").build()
        client.newCall(r).execute().use { resp -> if (resp.code == 404) return null; val b = resp.body?.string(); if (!resp.isSuccessful || b.isNullOrBlank()) return null; JSONObject(b) }
    } catch (e: Exception) { Log.d(TAG, "lrclib fetchJson failed $url ${e.message}"); null }
    private fun fetchJsonArray(url: String): JSONArray? = try {
        val r = Request.Builder().url(url).header("User-Agent", "piTube/1.0").build()
        client.newCall(r).execute().use { resp -> val b = resp.body?.string(); if (!resp.isSuccessful || b.isNullOrBlank()) return null; JSONArray(b) }
    } catch (e: Exception) { Log.d(TAG, "lrclib fetchArray failed $url"); null }
}

class KugouLyricsProvider(private val client: OkHttpClient = defaultClient()) : LyricsProvider() {
    override val id = "kugou"
    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long): String? = withContext(Dispatchers.IO) {
        // Lightweight KuGou via lrclib-compatible fallback would need API key; for now delegate to lrclib exact with album hint
        // Kept as separate provider so order toggle has effect and future krc decode can plug here.
        null
    }
}

class TranscriptLyricsProvider : LyricsProvider() {
    override val id = "transcript"
    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long): String? = null
}

private fun defaultClient() = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()

object LyricsProviders {
    fun ordered(orderCsv: String): List<LyricsProvider> {
        val ids = orderCsv.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val pool = mapOf("lrclib" to LrclibLyricsProvider(), "kugou" to KugouLyricsProvider(), "transcript" to TranscriptLyricsProvider())
        val ordered = ids.mapNotNull { pool[it] }
        return if (ordered.isEmpty()) listOf(LrclibLyricsProvider(), TranscriptLyricsProvider()) else ordered
    }
}
