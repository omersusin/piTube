package com.omersusin.pitube.data.lyrics

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

/**
 * Lyrics provider abstraction. Providers that need the video id override the 5-arg
 * [fetch]; the rest only implement the metadata-based variant.
 */
abstract class LyricsProvider {
    abstract val id: String
    open suspend fun fetch(title: String, artist: String, album: String, durationMs: Long): String? = null
    open suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, videoId: String): String? =
        fetch(title, artist, album, durationMs)
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

/**
 * KuGou lyrics — ported from vivi-music's KuGou client (OkHttp/org.json adaptation).
 * Search songs by keyword → pick one matching duration → resolve lyrics candidates →
 * download base64-encoded LRC.
 */
class KugouLyricsProvider(private val client: OkHttpClient = defaultClient()) : LyricsProvider() {
    override val id = "kugou"

    private companion object {
        const val PAGE_SIZE = 8
        const val HEAD_CUT_LIMIT = 30
        const val DURATION_TOLERANCE_SEC = 8
        val ACCEPTED_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\].*".toRegex()
        val BANNED_REGEX = ".+].+[:：].+".toRegex()
    }

    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long): String? = withContext(Dispatchers.IO) {
        val normTitle = normalizeTitle(title)
        val normArtist = normalizeArtist(artist)
        if (normTitle.isBlank()) return@withContext null
        val durSec = (durationMs / 1000).toInt()
        val query = buildString {
            append(normTitle); append(" - "); append(normArtist)
            if (album.isNotBlank()) { append(' '); append(album) }
        }

        // 1) song search → per-hash lyric lookup for a duration match
        val songs = try {
            JSONObject(fetchBody("https://mobileservice.kugou.com/api/v3/search/song" +
                "?version=9108&plat=0&pagesize=$PAGE_SIZE&showtype=0&keyword=" + urlEncode(query)))
                .optJSONObject("data")?.optJSONArray("info")
        } catch (_: Exception) { null }
        if (songs != null) {
            for (i in 0 until songs.length()) {
                val song = songs.optJSONObject(i) ?: continue
                if (durSec in 1..Int.MAX_VALUE && abs(song.optInt("duration", -9999) - durSec) > DURATION_TOLERANCE_SEC) continue
                val hash = song.optString("hash")
                if (hash.isBlank()) continue
                downloadFirstCandidate(searchByHash(hash))?.let { return@withContext it }
            }
        }

        // 2) direct keyword lyric search fallback
        val kwCandidates = try {
            searchByUrl("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc" +
                (if (durSec > 0) "&duration=${durSec * 1000L}" else "") + "&keyword=" + urlEncode(query))
        } catch (_: Exception) { null }
        downloadFirstCandidate(kwCandidates)
    }

    private fun searchByHash(hash: String): JSONArray? =
        searchByUrl("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=$hash")

    private fun searchByUrl(url: String): JSONArray? = try {
        JSONObject(fetchBody(url)).optJSONArray("candidates")
    } catch (_: Exception) { null }

    /** Returns normalized LRC text from the first downloadable candidate. */
    private fun downloadFirstCandidate(candidates: JSONArray?): String? {
        if (candidates == null) return null
        for (i in 0 until candidates.length()) {
            val c = candidates.optJSONObject(i) ?: continue
            val id = c.optLong("id"); val key = c.optString("accesskey")
            if (id <= 0L || key.isBlank()) continue
            val content = try {
                JSONObject(fetchBody("https://lyrics.kugou.com/download?fmt=lrc&charset=utf8&client=pc&ver=1&id=$id&accesskey=$key"))
                    .optString("content")
            } catch (_: Exception) { null }
            if (content.isNullOrBlank()) continue
            val decoded = try {
                android.util.Base64.decode(content, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
            } catch (_: IllegalArgumentException) { continue }
            val normalized = normalizeKrc(decoded)
            if (normalized.isNotBlank()) return normalized
        }
        return null
    }

    /** Strip metadata header/footer lines (singer/writer/composer etc.), as vivi-music does. */
    private fun normalizeKrc(raw: String): String {
        val lines = raw.lines().filter { it.matches(ACCEPTED_REGEX) }
        var headCut = 0
        for (i in minOf(HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
            if (lines[i].matches(BANNED_REGEX)) { headCut = i + 1; break }
        }
        var tailCut = 0
        for (i in minOf(lines.size - HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
            if (lines[lines.lastIndex - i].matches(BANNED_REGEX)) { tailCut = i + 1; break }
        }
        return lines.drop(headCut).dropLast(tailCut).joinToString("\n")
    }

    private fun normalizeTitle(t: String) = t
        .replace("\\(.*\\)".toRegex(), "").replace("（.*）".toRegex(), "")
        .replace("「.*」".toRegex(), "").replace("『.*』".toRegex(), "")
        .replace("<.*>".toRegex(), "").replace("《.*》".toRegex(), "")
        .replace("〈.*〉".toRegex(), "").replace("＜.*＞".toRegex(), "")

    private fun normalizeArtist(a: String) = a.replace(", ", "、").replace(" & ", "、")
        .replace(".", "").replace("和", "、")
        .replace("\\(.*\\)".toRegex(), "").replace("（.*）".toRegex(), "")

    private fun urlEncode(v: String) = android.net.Uri.encode(v)

    private fun fetchBody(url: String): String? {
        val r = Request.Builder().url(url).header("User-Agent", "piTube/1.0").build()
        client.newCall(r).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()?.takeIf { it.isNotBlank() }
        }
    }
}

/**
 * BetterLyrics — word-timed TTML from lyrics-api.boidu.dev, converted to enhanced LRC.
 * Ported from vivi-music's BetterLyrics provider.
 */
class BetterLyricsProvider(private val client: OkHttpClient = shortTimeoutClient()) : LyricsProvider() {
    override val id = "betterlyrics"
    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long): String? = withContext(Dispatchers.IO) {
        // Exact title/artist — no normalization, to keep correct sync (vivi-music comment).
        val b = android.net.Uri.parse("https://lyrics-api.boidu.dev/getLyrics").buildUpon()
            .appendQueryParameter("s", title)
            .appendQueryParameter("a", artist)
        val durSec = (durationMs / 1000).toInt()
        if (durSec > 0) b.appendQueryParameter("d", durSec.toString())
        if (album.isNotBlank()) b.appendQueryParameter("al", album)
        val ttml = try {
            val r = Request.Builder().url(b.build().toString()).header("User-Agent", "piTube/1.0").build()
            client.newCall(r).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                JSONObject(resp.body?.string().orEmpty()).optString("ttml").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) { Log.d(TAG, "betterlyrics fetch failed ${e.message}"); null } ?: return@withContext null
        TtmlParser.toEnhancedLrc(ttml)?.takeIf { it.isNotBlank() }
    }
}

/**
 * SimpMusic — videoId-keyed lyrics API (api-lyrics.simpmusic.org) with fallback host.
 * Prefers richSync (enhanced LRC) over plain synced over unsynced text.
 */
class SimpMusicLyricsProvider(private val client: OkHttpClient = defaultClient()) : LyricsProvider() {
    override val id = "simpmusic"
    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        val durSec = (durationMs / 1000).toInt()
        val hosts = listOf("https://api-lyrics.simpmusic.org/v1/", "https://vivi-yt-music-server.onrender.com/v1/")
        for (host in hosts) {
            val data = try {
                val r = Request.Builder().url(host + videoId)
                    .header("Accept", "application/json")
                    .header("User-Agent", "SimpMusicLyrics/1.0")
                    .build()
                client.newCall(r).execute().use { resp ->
                    if (!resp.isSuccessful) null else JSONObject(resp.body?.string().orEmpty())
                        .takeIf { it.optString("type") == "success" }?.optJSONArray("data")
                }
            } catch (e: Exception) { Log.d(TAG, "simpmusic $host failed ${e.message}"); null } ?: continue

            var best: JSONObject? = null
            var bestDiff = Int.MAX_VALUE
            for (i in 0 until data.length()) {
                val t = data.optJSONObject(i) ?: continue
                val diff = if (durSec > 0) abs((t.optInt("durationSeconds", 0)) - durSec) else 0
                if (diff < bestDiff) { bestDiff = diff; best = t }
            }
            val track = best ?: continue
            if (durSec > 0 && bestDiff > 10) continue
            track.optString("richSyncLyrics").takeIf { it.isNotBlank() }?.let { return@withContext it }
            track.optString("syncedLyrics").takeIf { it.isNotBlank() }?.let { return@withContext it }
            track.optString("plainLyric").takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        null
    }
}

/**
 * YouLyPlus — KPoe/LyricsPlus community servers, raced sequentially with the last
 * working server promoted first (vivi-music behaviour, simplified to sequential tries).
 */
class YouLyPlusLyricsProvider(private val client: OkHttpClient = veryShortTimeoutClient()) : LyricsProvider() {
    override val id = "youlyplus"

    private companion object {
        val SERVERS = listOf(
            "https://lyricsplus.prjktla.my.id",
            "https://lyricsplus.atomix.one",
            "https://lyricsplus.binimum.org",
            "https://lyricsplus.prjktla.workers.dev",
            "https://lyricsplus-seven.vercel.app",
            "https://lyrics-plus-backend.vercel.app",
        )
    }

    @Volatile
    private var lastWorkingServer: String? = null

    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long): String? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val ordered = listOfNotNull(lastWorkingServer) + SERVERS.filter { it != lastWorkingServer }
        val durSec = (durationMs / 1000).toInt()
        for (server in ordered) {
            val j = try {
                val b = android.net.Uri.parse("$server/v2/lyrics/get").buildUpon()
                    .appendQueryParameter("title", title)
                    .appendQueryParameter("artist", artist)
                    .appendQueryParameter("duration", durSec.coerceAtLeast(0).toString())
                if (album.isNotBlank()) b.appendQueryParameter("album", album)
                val r = Request.Builder().url(b.build().toString()).header("User-Agent", "piTube/1.0").build()
                client.newCall(r).execute().use { resp ->
                    if (!resp.isSuccessful) null else JSONObject(resp.body?.string().orEmpty())
                }
            } catch (_: Exception) { null } ?: continue

            val lrc = j.optString("syncedLyrics").takeIf { it.isNotBlank() }
                ?: convertKpoeToLrc(j.optJSONArray("lyrics"))
                ?: j.optString("plainLyrics").takeIf { it.isNotBlank() }
            if (!lrc.isNullOrBlank()) {
                lastWorkingServer = server
                return@withContext lrc
            }
        }
        null
    }

    /** KPoe items (ms timestamps + optional syllables) → enhanced LRC text. */
    private fun convertKpoeToLrc(items: JSONArray?): String? {
        if (items == null || items.length() == 0) return null
        return buildString {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val time = item.optLong("time", 0L)
                append(formatTag(time, '<'))
                val syllabus = item.optJSONArray("syllabus")
                if (syllabus != null && syllabus.length() > 0) {
                    for (k in 0 until syllabus.length()) {
                        val syl = syllabus.optJSONObject(k) ?: continue
                        append(formatTag(syl.optLong("time", 0L), '<'))
                        append(syl.optString("text"))
                    }
                } else {
                    append(item.optString("text"))
                }
                append('\n')
            }
        }.trim().takeIf { it.isNotEmpty() }
    }

    private fun formatTag(ms: Long, open: Char): String {
        val totalSec = ms / 1000
        val close = if (open == '<') '>' else ']'
        return "%c%02d:%02d.%03d%c".format(open, totalSec / 60, totalSec % 60, ms % 1000, close)
    }
}

/**
 * YouTube transcript / description-lyrics fallback, wrapped as an orderable provider
 * so users can rank it against the remote ones.
 */
class TranscriptLyricsProvider : LyricsProvider() {
    override val id = "transcript"
    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, videoId: String): String? {
        if (videoId.isBlank()) return null
        val lines = com.omersusin.pitube.innertube.YouTube.transcript(videoId).getOrNull().orEmpty()
        if (lines.isNotEmpty()) {
            return lines.joinToString("\n") { l ->
                val totalSec = l.startMs / 1000
                "[%02d:%02d.%03d]%s".format(totalSec / 60, totalSec % 60, l.startMs % 1000, l.text)
            }
        }
        val ep = com.omersusin.pitube.innertube.YouTube.lyricsEndpoint(videoId).getOrNull() ?: return null
        val text = com.omersusin.pitube.innertube.YouTube.lyrics(ep).getOrNull().orEmpty()
        if (text.isBlank()) return null
        return text.lines().joinToString("\n") { "[00:00.00]$it" }
    }
}

private fun defaultClient() = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()

private fun shortTimeoutClient() = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

private fun veryShortTimeoutClient() = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()

object LyricsProviders {
    /** Canonical default ranking — mirrors vivi-music's registry order where applicable. */
    val DEFAULT_ORDER = "lrclib,betterlyrics,simpmusic,kugou,youlyplus,transcript"

    fun all(): Map<String, () -> LyricsProvider> = mapOf(
        "lrclib" to { LrclibLyricsProvider() },
        "betterlyrics" to { BetterLyricsProvider() },
        "simpmusic" to { SimpMusicLyricsProvider() },
        "kugou" to { KugouLyricsProvider() },
        "youlyplus" to { YouLyPlusLyricsProvider() },
        "transcript" to { TranscriptLyricsProvider() },
    )

    fun ordered(orderCsv: String): List<LyricsProvider> {
        val ids = orderCsv.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val pool = all()
        val ordered = ids.mapNotNull { pool[it]?.invoke() }
        return if (ordered.isEmpty()) DEFAULT_ORDER.split(',').mapNotNull { pool[it]?.invoke() } else ordered
    }
}
