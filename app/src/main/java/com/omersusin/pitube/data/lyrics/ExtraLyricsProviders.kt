package com.omersusin.pitube.data.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Provider batch ported from ArchiveTune (lyrics/* modules, GPL-3.0) rewritten
 * on OkHttp/org.json to match piTube's provider style - no Ktor dependency.
 * Sources: MegalobizLyricsProvider, UnisonLyricsProvider,
 * BetterLyricsPortatoProvider and the Paxsenix source-specific variants
 * (Apple Music variant already lives in PaxsenixLyricsProvider as "paxsenix").
 */

internal fun extraLyricsClient() = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

private const val EXTRA_TAG = "ExtraLyrics"

private inline fun httpGetString(client: OkHttpClient, url: String, configure: Request.Builder.() -> Unit = {}): String? = try {
    val b = Request.Builder().url(url)
    b.configure()
    client.newCall(b.build()).execute().use { resp ->
        if (!resp.isSuccessful) null else resp.body?.string()?.takeIf { it.isNotBlank() }
    }
} catch (e: Exception) {
    Log.d(EXTRA_TAG, "GET failed $url: ${e.message}")
    null
}

private fun enc(v: String): String = URLEncoder.encode(v, StandardCharsets.UTF_8.name())

/** Recursive extraction of lyric text from arbitrary paxsenix JSON payloads. */
private val LYRIC_CONTENT_KEYS = listOf("lyrics", "lrc", "content", "text", "plainLyrics", "syncedLyrics", "line", "lyric")

private fun extractLyricText(element: Any?): String? {
    when (element) {
        null -> return null
        is String -> {
            val t = element.trim()
            if (t.isEmpty()) return null
            val nested = try { JSONObject(t) } catch (_: Exception) { try { JSONArray(t) } catch (_: Exception) { null } }
            return if (nested != null) extractLyricText(nested) else t
        }
        is JSONObject -> {
            if (element.optBoolean("isError", false)) return null
            val err = element.optJSONObject("error")
            if (err != null && err.length() > 0) return null
            for (key in LYRIC_CONTENT_KEYS) {
                element.opt(key)?.let { extractLyricText(it)?.let { txt -> return txt } }
            }
            element.optJSONObject("metadata")?.let { meta ->
                for (key in LYRIC_CONTENT_KEYS) meta.opt(key)?.let { extractLyricText(it)?.let { txt -> return txt } }
            }
            return null
        }
        is JSONArray -> {
            val parts = mutableListOf<String>()
            for (i in 0 until element.length()) {
                extractLyricText(element.opt(i))?.let { parts.add(it) }
            }
            return parts.joinToString("\n").trim().takeIf { it.isNotEmpty() }
        }
        else -> return null
    }
}

private fun resolveDurationSeconds(durationMs: Long): Int =
    when {
        durationMs <= 0L -> 0
        // >6min values are likely already seconds (ArchiveTune heuristic)
        durationMs > 360_000L -> (durationMs / 1000L).toInt()
        else -> durationMs.toInt()
    }

private fun looksLikeLineSynced(text: String): Boolean {
    var tags = 0
    val seenTimes = HashSet<String>()
    for (line in text.lines()) {
        val m = Regex("\\[(\\d{1,2}):(\\d{2})[.,]\\d{2,3}\\]").find(line) ?: continue
        tags++
        seenTimes.add(m.groupValues[1] + ":" + m.groupValues[2])
    }
    return tags > 4 && seenTimes.size > (tags / 4).coerceAtLeast(1)
}

// ---------------------------- Megalobiz ----------------------------

class MegalobizLyricsProvider(private val client: OkHttpClient = extraLyricsClient()) : LyricsProvider() {
    override val id = "megalobiz"
    private companion object { const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)" }

    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, videoId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val searchUrl = "https://www.megalobiz.com/searchall?qry=${enc("$artist $title".trim())}"
                val searchHtml = httpGetString(client, searchUrl) { header("User-Agent", UA) } ?: return@withContext null
                val lrcPath = Regex("href=[\"'](/lrc/maker/download/[^\"']+)[\"']").find(searchHtml)?.groupValues?.get(1)
                    ?: return@withContext null
                val detailHtml = httpGetString(client, "https://www.megalobiz.com$lrcPath") { header("User-Agent", UA) }
                    ?: return@withContext null
                val raw = Regex("id=[\"']lrc_[^\"']*_details[\"'][^>]*>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
                    .find(detailHtml)?.groupValues?.get(1) ?: detailHtml
                val cleaned = raw
                    .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                    .replace(Regex("<br\\s*/?>"), "\n")
                    .replace(Regex("<[^>]+>"), "")
                    .trim()
                cleaned.takeIf { looksLikeLineSynced(it) }
            } catch (e: Exception) {
                Log.d(EXTRA_TAG, "megalobiz failed: ${e.message}")
                null
            }
        }
}

// ---------------------------- Unison ----------------------------

class UnisonLyricsProvider(private val client: OkHttpClient = extraLyricsClient()) : LyricsProvider() {
    override val id = "unison"
    private companion object { const val BASE = "https://unison.boidu.dev/" }

    private fun parseEntry(body: String): String? = try {
        val root = JSONObject(body)
        if (root.optBoolean("success", false)) {
            root.optJSONObject("data")?.optString("lyrics")?.takeIf { it.isNotBlank() }
        } else null
    } catch (_: Exception) { null }

    private fun byVideoId(vId: String): String? =
        httpGetString(client, "${BASE}lyrics?v=${enc(vId)}")?.let(::parseEntry)

    private fun byNumericId(numericId: Long): String? =
        httpGetString(client, "${BASE}lyrics/$numericId")?.let(::parseEntry)

    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, videoId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val cleanTitle = title.trim(); val cleanArtist = artist.trim()
                if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
                    var url = "${BASE}lyrics/search?song=${enc(cleanTitle)}&artist=${enc(cleanArtist)}&limit=5"
                    if (album.isNotBlank()) url += "&album=${enc(album.trim())}"
                    val durSec = resolveDurationSeconds(durationMs)
                    if (durSec > 0) url += "&duration=$durSec"
                    val body = httpGetString(client, url)
                    if (body != null) {
                        val arr = try { JSONObject(body).optJSONArray("data") } catch (_: Exception) { null }
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val summary = arr.optJSONObject(i) ?: continue
                                val vId = summary.optString("videoId").takeIf { it.isNotBlank() }
                                val lyrics = vId?.let(::byVideoId)
                                    ?: summary.optLong("id", -1L).takeIf { it > 0 }?.let(::byNumericId)
                                if (lyrics != null) return@withContext lyrics
                            }
                        }
                    }
                }
                if (videoId.isNotBlank()) byVideoId(videoId) else null
            } catch (e: Exception) {
                Log.d(EXTRA_TAG, "unison failed: ${e.message}")
                null
            }
        }
}

// ---------------------- BetterLyrics Portato ----------------------

class BetterLyricsPortatoProvider(private val client: OkHttpClient = extraLyricsClient()) : LyricsProvider() {
    override val id = "betterlyricsportato"
    private companion object { const val ENDPOINT = "https://lyrics-api.boidu.dev/qq/getLyrics" }

    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, videoId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val cleanTitle = title.trim(); val cleanArtist = artist.trim()
                if (cleanTitle.isBlank() || cleanArtist.isBlank()) return@withContext null
                var url = "$ENDPOINT?s=${enc(cleanTitle)}&a=${enc(cleanArtist)}"
                if (album.isNotBlank()) url += "&al=${enc(album.trim())}"
                val durSec = resolveDurationSeconds(durationMs)
                if (durSec > 0) url += "&d=$durSec"
                val body = httpGetString(client, url) ?: return@withContext null
                val ttml = if (body.trimStart().startsWith("<")) {
                    body
                } else {
                    try { JSONObject(body).optString("ttml") } catch (_: Exception) { "" }
                }
                when {
                    ttml.contains("<tt") || ttml.contains("<?xml") ->
                        TtmlParser.toEnhancedLrc(ttml)?.takeIf { it.isNotBlank() }
                    else -> null
                }
            } catch (e: Exception) {
                Log.d(EXTRA_TAG, "betterlyricsportato failed: ${e.message}")
                null
            }
        }
}

// --------------- Paxsenix source-specific variants ---------------

/** Shared paxsenix.org plumbing for the non-Apple-Music sources. */
abstract class PaxsenixSourceProvider : LyricsProvider() {
    protected val client: OkHttpClient = extraLyricsClient()
    protected companion object { const val PAX_BASE = "https://lyrics.paxsenix.org" }

    protected fun extract(body: String): String? = try {
        extractLyricText(JSONObject(body))
    } catch (_: Exception) {
        try { extractLyricText(JSONArray(body)) } catch (_: Exception) { null }
    }

    protected fun pickBestIndex(rawDurations: List<Int>, durationMs: Long): Int {
        if (rawDurations.isEmpty()) return -1
        fun norm(v: Int): Int = if (v >= 100_000) v / 1000 else v // ms→s heuristic
        val target = norm(resolveDurationSeconds(durationMs))
        if (target <= 0) return 0
        var best = -1; var bestDiff = Int.MAX_VALUE
        rawDurations.forEachIndexed { i, d ->
            val diff = abs(norm(d) - target)
            if (diff < bestDiff) { bestDiff = diff; best = i }
        }
        return if (bestDiff <= 10 || rawDurations.size == 1) best else -1
    }
        return if (bestDiff < 10) best else if (sizes.size == 1) 0 else -1
    }
}

class PaxsenixNeteaseLyricsProvider : PaxsenixSourceProvider() {
    override val id = "paxsenix-netease"

    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, videoId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val searchBody = httpGetString(client, "$PAX_BASE/netease/search?q=${enc("$title $artist".trim())}") ?: return@withContext null
                val songs = JSONObject(searchBody).optJSONObject("result")?.optJSONArray("songs")
                    ?: return@withContext null
                val ids = mutableListOf<String>(); val durs = mutableListOf<Int>()
                for (i in 0 until songs.length()) {
                    val s = songs.optJSONObject(i) ?: continue
                    ids.add(s.optString("id")); durs.add(s.optInt("duration", 0))
                }
                val idx = pickBestIndex(durs, durationMs)
                if (idx < 0) return@withContext null
                val lyricsBody = httpGetString(client, "$PAX_BASE/netease/lyrics?id=${enc(ids[idx])}&word=true") ?: return@withContext null
                val root = JSONObject(lyricsBody)
                // piTube's parser consumes LRC text; prefer the plain lrc block,
                // fall back to the karaoke klyric only when no lrc exists.
                root.optJSONObject("lrc")?.optString("lyric")?.takeIf { it.isNotBlank() }
                    ?: root.optJSONObject("klyric")?.optString("lyric")?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.d(EXTRA_TAG, "paxsenix-netease failed: ${e.message}")
                null
            }
        }
}

private fun JSONArray.asNameDurationPairs(): Pair<List<String>, List<Int>> {
    val names = mutableListOf<String>(); val durs = mutableListOf<Int>()
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        names.add(o.optString("name", o.optString("title")))
        durs.add(o.optInt("durationMs", o.optInt("duration", 0)))
    }
    return names to durs
}

class PaxsenixSpotifyLyricsProvider : PaxsenixSourceProvider() {
    override val id = "paxsenix-spotify"

    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, videoId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val searchBody = httpGetString(client, "$PAX_BASE/spotify/search?q=${enc("$title $artist".trim())}") ?: return@withContext null
                val items = JSONArray(searchBody)
                val (_, durs) = items.asNameDurationPairs()
                val idx = pickBestIndex(durs, durationMs)
                if (idx < 0) return@withContext null
                val trackId = items.optJSONObject(idx)?.optString("realId")?.takeIf { it.isNotBlank() }
                    ?: return@withContext null
                val lyricsBody = httpGetString(client, "$PAX_BASE/spotify/lyrics?id=${enc(trackId)}") ?: return@withContext null
                extract(lyricsBody)
            } catch (e: Exception) {
                Log.d(EXTRA_TAG, "paxsenix-spotify failed: ${e.message}")
                null
            }
        }
}

class PaxsenixYouTubeLyricsProvider : PaxsenixSourceProvider() {
    override val id = "paxsenix-youtube"

    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, videoId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val searchBody = httpGetString(client, "$PAX_BASE/youtube/search?q=${enc("$title $artist".trim())}") ?: return@withContext null
                val items = JSONArray(searchBody)
                val (_, durs) = items.asNameDurationPairs()
                val idx = pickBestIndex(durs, durationMs)
                if (idx < 0) return@withContext null
                val trackId = items.optJSONObject(idx)?.optString("realId")?.takeIf { it.isNotBlank() }
                    ?: return@withContext null
                val lyricsBody = httpGetString(client, "$PAX_BASE/youtube/lyrics?id=${enc(trackId)}") ?: return@withContext null
                extract(lyricsBody)
            } catch (e: Exception) {
                Log.d(EXTRA_TAG, "paxsenix-youtube failed: ${e.message}")
                null
            }
        }
}

class PaxsenixMusixmatchLyricsProvider : PaxsenixSourceProvider() {
    override val id = "paxsenix-musixmatch"

    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long, videoId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val query = enc("$title $artist".trim())
                val t = enc(title); val a = enc(artist)
                val durSec = resolveDurationSeconds(durationMs)
                val wordBody = httpGetString(
                    client,
                    "$PAX_BASE/musixmatch/lyrics?q=$query&t=$t&a=$a&d=$durSec&type=word"
                )
                wordBody?.let { extract(it) }?.let { return@withContext it }
                val plainBody = httpGetString(
                    client,
                    "$PAX_BASE/musixmatch/lyrics?q=$query&t=$t&a=$a&d=$durSec"
                ) ?: return@withContext null
                extract(plainBody)
            } catch (e: Exception) {
                Log.d(EXTRA_TAG, "paxsenix-musixmatch failed: ${e.message}")
                null
            }
        }
}
