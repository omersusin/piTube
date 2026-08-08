package com.omersusin.pitube.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Captions {
    private const val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val CLIENT_VERSION = "2.20260114.08.00"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    data class Track(val name: String, val lang: String, val url: String)
    data class Cue(val startMs: Long, val endMs: Long, val text: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun tracks(context: Context, videoId: String): List<Track> = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getRawCookies(context)
            val authHeader = if (cookies.isNotBlank()) KodaAuth.authHeader(cookies) else null

            val body = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", CLIENT_VERSION)
                    })
                })
            }
            val reqBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?key=$API_KEY&prettyPrint=false")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", UA)
                .addHeader("X-YouTube-Client-Name", "1")
                .addHeader("X-YouTube-Client-Version", CLIENT_VERSION)
                .addHeader("Origin", "https://www.youtube.com")
                .addHeader("X-Goog-AuthUser", "0")
            if (cookies.isNotBlank()) reqBuilder.addHeader("Cookie", cookies)
            if (authHeader != null) reqBuilder.addHeader("Authorization", authHeader)

            val resp = client.newCall(reqBuilder.build()).execute()
            resp.use { r ->
                KodaAuth.refreshFromResponse(context, r)
                val json = JSONObject(r.body?.string() ?: "{}")
                val caps = json.optJSONObject("captions")
                    ?.optJSONObject("playerCaptionsTracklistRenderer")
                    ?.optJSONArray("captionTracks") ?: return@withContext emptyList()
                val out = mutableListOf<Track>()
                for (i in 0 until caps.length()) {
                    val t = caps.optJSONObject(i) ?: continue
                    out.add(Track(
                        t.optJSONObject("name")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                            ?: t.optString("languageCode"),
                        t.optString("languageCode"),
                        t.optString("baseUrl")
                    ))
                }
                out
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun load(track: Track): List<Cue> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(track.url + "&fmt=vtt")
                .addHeader("User-Agent", UA)
                .build()
            val raw = client.newCall(req).execute().use { it.body?.string() ?: "" }
            parseVtt(raw)
        } catch (e: Exception) { emptyList() }
    }

    fun parseVtt(raw: String): List<Cue> {
        val out = mutableListOf<Cue>()
        val timeRe = Regex("""(\d{2}:)?(\d{2}):(\d{2})[.,](\d{3})""")
        var pending: Pair<Long, Long>? = null
        val text = StringBuilder()
        fun flush() {
            val p = pending
            if (p != null && text.isNotBlank()) out.add(Cue(p.first, p.second, text.toString().replace(Regex("<[^>]*>"), "").trim()))
            pending = null; text.setLength(0)
        }
        for (line in raw.lines()) {
            val m = timeRe.findAll(line).toList()
            if (line.contains("-->") && m.size >= 2) { flush(); pending = parseTs(m[0].value) to parseTs(m[1].value) }
            else if (line.isBlank()) flush()
            else if (pending != null && !line.startsWith("WEBVTT")) { if (text.isNotEmpty()) text.append(" "); text.append(line) }
        }
        flush()
        return out
    }

    private fun parseTs(s: String): Long {
        val p = s.split(":", ".", ",")
        return when (p.size) {
            4 -> p[0].toLong() * 3600_000 + p[1].toLong() * 60_000 + p[2].toLong() * 1000 + p[3].toLong()
            3 -> p[0].toLong() * 60_000 + p[1].toLong() * 1000 + p[2].toLong()
            else -> 0
        }
    }
}
