package com.omersusin.pitube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object Captions {
    data class Track(val name: String, val lang: String, val url: String)
    data class Cue(val startMs: Long, val endMs: Long, val text: String)
    private val client = OkHttpClient()

    suspend fun tracks(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply { put("client", JSONObject().apply {
                    put("clientName", "WEB"); put("clientVersion", "2.20260114.08.00") }) })
            }
            val req = Request.Builder().url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .addHeader("Content-Type", "application/json").post(okhttp3.RequestBody.create(null, body.toString())).build()
            val json = JSONObject(client.newCall(req).execute().body?.string() ?: "{}")
            val caps = json.optJSONObject("captions")?.optJSONObject("playerCaptionsTracklistRenderer")?.optJSONArray("captionTracks") ?: return@withContext emptyList()
            val out = mutableListOf<Track>()
            for (i in 0 until caps.length()) {
                val t = caps.optJSONObject(i) ?: continue
                out.add(Track(t.optJSONObject("name")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: t.optString("languageCode"), t.optString("languageCode"), t.optString("baseUrl")))
            }
            out
        } catch (e: Exception) { emptyList() }
    }

    suspend fun load(track: Track): List<Cue> = withContext(Dispatchers.IO) {
        try {
            val raw = client.newCall(Request.Builder().url(track.url + "&fmt=vtt").build()).execute().body?.string() ?: ""
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
