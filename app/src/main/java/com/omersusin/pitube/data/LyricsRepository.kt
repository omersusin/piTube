package com.omersusin.pitube.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LyricsLine(val timestampMs: Long, val text: String)
data class LyricsResult(
    val source: String,
    val trackName: String,
    val artistName: String,
    val synced: List<LyricsLine>,
    val plainText: String
)

class LyricsRepository {
    private val TAG = "LyricsRepository"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getLyrics(videoTitle: String, artist: String): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            val track = videoTitle
                .replace(Regex("\\(.*?\\)"), "")
                .replace(Regex("\\[.*?]"), "")
                .trim()
            val searchUrl = "https://lrclib.net/api/search?track_name=${java.net.URLEncoder.encode(track, "UTF-8")}" +
                "&artist_name=${java.net.URLEncoder.encode(artist, "UTF-8")}"
            val searchRequest = Request.Builder().url(searchUrl)
                .header("User-Agent", "piTube/1.0 (github.com/omersusin/pitube)")
                .build()
            val searchResponse = client.newCall(searchRequest).execute()
            if (searchResponse.code != 200) return@withContext null

            val searchJson = searchResponse.body?.string() ?: return@withContext null
            val results = org.json.JSONArray(searchJson)
            if (results.length() == 0) return@withContext null

            val best = results.getJSONObject(0)
            val syncedLyrics = best.optString("syncedLyrics", "")
            val plainLyrics = best.optString("plainLyrics", "")

            val syncedLines = if (syncedLyrics.isNotBlank()) {
                parseSyncedLyrics(syncedLyrics)
            } else {
                plainLyrics.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
                    LyricsLine(i * 5000L, line)
                }
            }

            LyricsResult(
                source = best.optString("source", "lrclib"),
                trackName = best.optString("trackName", track),
                artistName = best.optString("artistName", artist),
                synced = syncedLines,
                plainText = plainLyrics
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics", e)
            null
        }
    }

    private fun parseSyncedLyrics(lrc: String): List<LyricsLine> {
        val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")
        return lrc.lines().mapNotNull { line ->
            regex.matchEntire(line.trim())?.let { m ->
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val msStr = m.groupValues[3]
                val ms = if (msStr.length == 2) msStr.toLongOrNull()!! * 10 else msStr.toLongOrNull() ?: 0L
                val text = m.groupValues[4].trim()
                if (text.isNotBlank()) LyricsLine(min * 60_000 + sec * 1000 + ms, text) else null
            }
        }
    }
}
