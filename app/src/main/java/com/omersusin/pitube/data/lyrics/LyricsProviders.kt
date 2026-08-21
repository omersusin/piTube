package com.omersusin.pitube.data.lyrics

import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val TAG = "LyricsProviders"

abstract class LyricsProvider {
    abstract val id: String
    abstract val priority: Int
    abstract suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, songId: String = ""): String?
}

class BetterLyricsProvider(private val client: OkHttpClient = defaultClient()) : LyricsProvider() {
    override val id = "betterlyrics"; override val priority = 0
    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, songId: String): String? = withContext(Dispatchers.IO) {
        if (artist.isBlank()) return@withContext null
        val params = mapOf("s" to title, "a" to artist, "al" to album.takeIf { it.isNotBlank() }, "d" to (durationMs/1000).toInt().takeIf { it > 0 }?.toString())
        for (ep in listOf("https://lyrics-api.boidu.dev/getLyrics", "https://lyrics-api.boidu.dev/qq/getLyrics", "https://lyrics-api.boidu.dev/kugou/getLyrics")) {
            val body = fetchBody(ep, params) ?: continue
            val decoded = decodeWrapped(body) ?: continue
            if (decoded.isNotBlank()) return@withContext decoded
        }; null
    }
}

class YouLyPlusProvider(private val client: OkHttpClient = defaultClient()) : LyricsProvider() {
    override val id = "youlyplus"; override val priority = 1
    private val mirrors = listOf("https://lyricsplus.binimum.org/", "https://lyricsplus.prjktla.my.id/", "https://lyricsplus.prjktla.workers.dev/", "https://lyricsplus.atomix.one/", "https://lyricsplus-seven.vercel.app/")
    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, songId: String): String? = withContext(Dispatchers.IO) {
        if (artist.isBlank()) return@withContext null
        val params = mapOf("title" to title, "artist" to artist, "album" to album.takeIf { it.isNotBlank() }, "duration" to (durationMs/1000).toInt().takeIf { it > 0 }?.toString())
        for (m in mirrors) {
            val body = fetchBody(m + "v1/ttml/get", params) ?: continue
            val decoded = decodeWrapped(body) ?: continue
            if (decoded.isNotBlank()) return@withContext decoded
        }
        for (m in mirrors) {
            val body = fetchBody(m + "v2/lyrics/get", params) ?: continue
            val structured = decodeStructured(body) ?: continue
            if (structured.isNotBlank()) return@withContext structured
        }; null
    }
    private fun decodeStructured(body: String): String? { val root = runCatching { JSONObject(body) }.getOrNull() ?: return null; val arr = root.optJSONArray("lyrics") ?: return null; val sb = StringBuilder(); for (i in 0 until arr.length()) { val o = arr.optJSONObject(i) ?: continue; val t = o.optLong("time", -1); val text = o.optString("text"); if (t >= 0) sb.appendLine("[${String.format("%02d:%02d.%02d", (t/60000).toInt(), ((t%60000)/1000).toInt(), ((t%1000)/10).toInt())}]$text") else sb.appendLine(text) }; return sb.toString().takeIf { it.isNotBlank() } }
}

class NeteaseLyricsProvider(private val client: OkHttpClient = defaultClient()) : LyricsProvider() {
    override val id = "netease"; override val priority = 2
    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, songId: String): String? = withContext(Dispatchers.IO) {
        if (artist.isBlank()) return@withContext null
        val searchBody = fetchBody("https://lyrics.paxsenix.org/netease/search", mapOf("q" to "$title $artist")) ?: return@withContext null
        val songs = runCatching { JSONObject(searchBody).optJSONObject("result")?.optJSONArray("songs") }.getOrNull() ?: return@withContext null
        val best = closestJson(songs, durationMs, "duration", 10000L) ?: return@withContext null
        val id = best.optLong("id", -1); if (id < 0) return@withContext null
        val lyricBody = fetchBody("https://lyrics.paxsenix.org/netease/lyrics", mapOf("id" to id.toString(), "word" to "true")) ?: return@withContext null
        val root = runCatching { JSONObject(lyricBody) }.getOrNull() ?: return@withContext null
        root.optJSONObject("klyric")?.optString("lyric")?.takeIf { it.isNotBlank() } ?: root.optJSONObject("lrc")?.optString("lyric")
    }
}

class SimpMusicLyricsProvider(private val client: OkHttpClient = defaultClient()) : LyricsProvider() {
    override val id = "simpmusic"; override val priority = 3
    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, songId: String): String? = withContext(Dispatchers.IO) {
        if (songId.isBlank()) return@withContext null
        val body = fetchBody("https://api-lyrics.simpmusic.org/v1/${Uri.encode(songId)}", emptyMap()) ?: return@withContext null
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext null
        if (!root.optString("type").equals("success", ignoreCase = true)) return@withContext null
        val data = root.optJSONArray("data") ?: return@withContext null
        val best = closestJson(data, durationMs, "durationSeconds", 12000L, valueIsSeconds = true) ?: data.optJSONObject(0) ?: return@withContext null
        best.optString("richSyncLyrics").takeIf { it.isNotBlank() } ?: best.optString("syncedLyrics").takeIf { it.isNotBlank() } ?: best.optString("plainLyric")
    }
}

class LrclibLyricsProvider(private val client: OkHttpClient = defaultClient()) : LyricsProvider() {
    override val id = "lrclib"; override val priority = 4
    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, songId: String): String? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val durSec = (durationMs / 1000).toInt()
        if (artist.isNotBlank() && durSec > 0) {
            val b = Uri.parse("https://lrclib.net/api/get").buildUpon().appendQueryParameter("track_name", title).appendQueryParameter("artist_name", artist).appendQueryParameter("duration", durSec.toString())
            if (album.isNotBlank()) b.appendQueryParameter("album_name", album)
            val j = fetchJson(b.build().toString())
            if (j != null && !j.optBoolean("instrumental", false)) { syncedOf(j)?.let { return@withContext it } }
        }
        val b2 = Uri.parse("https://lrclib.net/api/search").buildUpon().appendQueryParameter("q", "$title $artist")
        val arr = fetchJsonArray(b2.build().toString()) ?: return@withContext null
        val ranked = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.sortedByDescending { score(it, title, artist, album, durationMs) }
        for (item in ranked) {
            val candMs = (item.optDouble("duration", 0.0) * 1000).toLong()
            if (durationMs > 0 && candMs > 0 && abs(candMs - durationMs) > 10000L) continue
            syncedOf(item)?.let { return@withContext it } ?: item.optString("plainLyrics").takeIf { it.isNotBlank() }?.let { return@withContext it }
        }; null
    }
    private fun syncedOf(j: JSONObject): String? { if (j.isNull("syncedLyrics")) return null; return j.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" } }
    private fun score(item: JSONObject, title: String, artist: String, album: String, durMs: Long): Int { var s = 0; val t = item.optString("trackName"); val a = item.optString("artistName"); if (t.equals(title, ignoreCase = true)) s += 40 else if (t.contains(title, ignoreCase = true)) s += 18; if (a.equals(artist, ignoreCase = true)) s += 30 else if (a.contains(artist, ignoreCase = true)) s += 12; if (album.isNotBlank() && item.optString("albumName").equals(album, ignoreCase = true)) s += 10; val d = (item.optDouble("duration", 0.0)*1000).toLong(); if (durMs > 0 && d > 0) { val diff = abs(d - durMs); if (diff <= 2000) s += 20 else if (diff <= 10000) s += 8 }; if (item.optString("syncedLyrics").isNotBlank()) s += 5; return s }
    private fun fetchJson(url: String): JSONObject? = try { val r = Request.Builder().url(url).header("User-Agent", "piTube/1.0").build(); client.newCall(r).execute().use { resp -> if (resp.code == 404) return null; val b = resp.body?.string(); if (!resp.isSuccessful || b.isNullOrBlank()) return null; JSONObject(b) } } catch (_: Exception) { null }
    private fun fetchJsonArray(url: String): JSONArray? = try { val r = Request.Builder().url(url).header("User-Agent", "piTube/1.0").build(); client.newCall(r).execute().use { resp -> val b = resp.body?.string(); if (!resp.isSuccessful || b.isNullOrBlank()) return null; JSONArray(b) } } catch (_: Exception) { null }
}

class KuGouLyricsProvider(private val client: OkHttpClient = defaultClient()) : LyricsProvider() {
    override val id = "kugou"; override val priority = 5
    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, songId: String): String? = withContext(Dispatchers.IO) {
        val keyword = "${cleanTitle(title)} - ${cleanArtist(artist)}"
        val songBody = fetchBody("https://mobileservice.kugou.com/api/v3/search/song", mapOf("version" to "9108", "plat" to "0", "pagesize" to "8", "showtype" to "0", "keyword" to keyword))
        if (songBody != null) {
            val songs = runCatching { JSONObject(songBody).optJSONObject("data")?.optJSONArray("info") }.getOrNull()
            if (songs != null) for (i in 0 until songs.length()) { val s = songs.optJSONObject(i) ?: continue; val dur = s.optLong("duration", 0); if (durationMs > 0 && abs(dur - durationMs/1000) > 8) continue; val hash = s.optString("hash").takeIf { it.isNotBlank() } ?: continue; findCandidate(mapOf("hash" to hash))?.let { c -> download(c)?.let { return@withContext it } } }
        }
        findCandidate(mapOf("duration" to (durationMs/1000).toInt().takeIf { it > 0 }, "keyword" to keyword))?.let { download(it) }
    }
    private suspend fun findCandidate(params: Map<String, Any?>): JSONObject? { val body = fetchBody("https://lyrics.kugou.com/search", mapOf("ver" to 1, "man" to "yes", "client" to "pc") + params) ?: return null; return runCatching { JSONObject(body).optJSONArray("candidates")?.optJSONObject(0) }.getOrNull() }
    private suspend fun download(candidate: JSONObject): String? { val id = candidate.optLong("id", -1); if (id < 0) return null; val key = candidate.optString("accesskey").takeIf { it.isNotBlank() } ?: return null; val body = fetchBody("https://lyrics.kugou.com/download", mapOf("fmt" to "lrc", "charset" to "utf8", "client" to "pc", "ver" to 1, "id" to id.toString(), "accesskey" to key)) ?: return null; val enc = runCatching { JSONObject(body).optString("content") }.getOrNull() ?: return null; return runCatching { String(Base64.decode(enc, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull() }
    private fun cleanTitle(t: String) = t.replace(Regex("""[（(「『<《〈＜].*?[）)」』>》〉＞]"""), "").trim()
    private fun cleanArtist(a: String) = a.replace(", ", "、").replace(" & ", "、").replace(".", "").trim()
}

class UnisonLyricsProvider(private val client: OkHttpClient = defaultClient()) : LyricsProvider() {
    override val id = "unison"; override val priority = 6
    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, songId: String): String? = withContext(Dispatchers.IO) {
        if (artist.isNotBlank()) { val body = fetchBody("https://unison.boidu.dev/lyrics/search", mapOf("song" to title, "artist" to artist, "album" to album.takeIf { it.isNotBlank() }, "duration" to (durationMs/1000).toInt().takeIf { it > 0 }?.toString(), "limit" to "5")); if (body != null) { val arr = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull(); if (arr != null) for (i in 0 until arr.length()) { val item = arr.optJSONObject(i) ?: continue; item.optString("lyrics").takeIf { it.isNotBlank() }?.let { return@withContext it }; val id = item.optLong("id", -1); if (id >= 0) { fetchBody("https://unison.boidu.dev/lyrics/$id", emptyMap())?.let { b2 -> runCatching { JSONObject(b2).optJSONObject("data")?.optString("lyrics") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it } } } } }
        if (songId.isBlank()) return@withContext null
        val body = fetchBody("https://unison.boidu.dev/lyrics", mapOf("v" to songId)) ?: return@withContext null
        runCatching { JSONObject(body).optJSONObject("data")?.optString("lyrics") }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}

class TranscriptLyricsProvider : LyricsProvider() {
    override val id = "transcript"; override val priority = 99
    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, songId: String): String? = null
}

private fun defaultClient() = OkHttpClient.Builder().connectTimeout(7, TimeUnit.SECONDS).readTimeout(7, TimeUnit.SECONDS).callTimeout(9, TimeUnit.SECONDS).build()
private suspend fun fetchBody(url: String, params: Map<String, Any?>): String? = withContext(Dispatchers.IO) {
    val b = Uri.parse(url).buildUpon(); params.forEach { (k, v) -> if (v != null) b.appendQueryParameter(k, v.toString()) }
    val req = Request.Builder().url(b.build().toString()).header("Accept", "application/json, */*").header("User-Agent", "Koda Android (https://github.com/Ivorisnoob/Koda)").build()
    try { OkHttpClient.Builder().build().newCall(req).execute().use { r -> if (!r.isSuccessful) return@withContext null; r.body?.string()?.takeIf { it.isNotBlank() } } } catch (_: Exception) { null }
}
private fun decodeWrapped(body: String): String? { val t = body.trim(); if (t.startsWith("<")) return t; val root = runCatching { JSONObject(t) }.getOrNull() ?: return null; return sequenceOf("ttml","lyrics","lrc","content","text").mapNotNull { root.optString(it).takeIf { s -> s.isNotBlank() && s != "null" } }.firstOrNull() }
private fun closestJson(array: JSONArray, targetMs: Long, key: String, toleranceMs: Long, valueIsSeconds: Boolean = false): JSONObject? { val items = (0 until array.length()).mapNotNull { array.optJSONObject(it) }; if (items.isEmpty()) return null; if (targetMs <= 0) return items.first(); return items.minByOrNull { val raw = it.optDouble(key, 0.0); val d = if (valueIsSeconds) (raw*1000).toLong() else raw.toLong(); abs(d - targetMs) }?.takeIf { val raw = it.optDouble(key, 0.0); val d = if (valueIsSeconds) (raw*1000).toLong() else raw.toLong(); d <= 0 || abs(d - targetMs) <= toleranceMs } }

object LyricsProviders {
    fun ordered(orderCsv: String): List<LyricsProvider> {
        val ids = orderCsv.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val pool: Map<String, LyricsProvider> = mapOf("betterlyrics" to BetterLyricsProvider(), "youlyplus" to YouLyPlusProvider(), "netease" to NeteaseLyricsProvider(), "simpmusic" to SimpMusicLyricsProvider(), "lrclib" to LrclibLyricsProvider(), "kugou" to KuGouLyricsProvider(), "unison" to UnisonLyricsProvider(), "transcript" to TranscriptLyricsProvider())
        val ordered = ids.mapNotNull { pool[it] }
        return if (ordered.isEmpty()) listOf(BetterLyricsProvider(), YouLyPlusProvider(), NeteaseLyricsProvider(), LrclibLyricsProvider()) else ordered
    }
    fun defaultOrderCsv(): String = "betterlyrics,youlyplus,netease,simpmusic,lrclib,kugou,unison"
}
