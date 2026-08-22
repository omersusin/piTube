package com.omersusin.pitube.data.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Paxsenix lyrics provider (Apple Music word-synced lyrics via the community
 * paxsenix.org bridge) — ported from vivi-music's client, OkHttp/org.json.
 *
 * Flow: scrape an Apple Music developer token from beta.music.apple.com →
 * search the Apple Music catalog → ask paxsenix for that track's lyrics →
 * TTML/word-timed content converted to enhanced inline LRC.
 */
class PaxsenixLyricsProvider(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()) : LyricsProvider() {
    override val id = "paxsenix"

    private val appleToken = AtomicReference<String?>(null)
    private val tokenMutex = Mutex()

    private companion object {
        const val TAG = "Paxsenix"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
        const val PAX_BASE = "https://lyrics.paxsenix.org"
        const val APPLE_API = "https://amp-api.music.apple.com/v1/catalog/us"
    }

    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long): String? =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext null
            try {
                val durSec = (durationMs / 1000).toInt()
                val cleanedTitle = clean(title)
                val queries = buildList {
                    add("$cleanedTitle ${clean(artist)}")
                    add(cleanedTitle)
                    if (album.isNotBlank()) add("$cleanedTitle ${clean(artist)} $album")
                }
                var results: List<JSONObject> = emptyList()
                for (query in queries) {
                    if (results.isNotEmpty()) break
                    results = appleSearch(query)
                }
                // Score: title containment first, then duration closeness.
                val normQuery = clean(title)
                val ranked = results
                    .mapNotNull { obj ->
                        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val name = clean(obj.optString("trackName", obj.optString("songName")))
                        val artistName = clean(obj.optString("artistName"))
                        val textScore = when {
                            name == normQuery -> 0
                            name.contains(normQuery) || normQuery.contains(name) -> 1
                            else -> 2
                        }
                        val d = obj.optInt("duration", 0)
                        val durDelta = if (durSec > 0 && d > 0) abs(d - durSec) else Int.MAX_VALUE / 2
                        Triple(id, textScore, durDelta)
                    }
                    .sortedWith(compareBy({ it.second }, { it.third }))
                    .take(10)

                for ((id, _, _) in ranked) {
                    fetchLyricsForId(id)?.let { return@withContext it }
                }
                null
            } catch (e: Exception) {
                Log.d(TAG, "fetch failed: ${e.message}")
                null
            }
        }

    /** Apple Music catalog search using a dynamically scraped developer token. */
    private fun appleSearch(query: String): List<JSONObject> {
        val token = appleToken() ?: return emptyList()
        val url = "$APPLE_API/search?term=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name()) +
            "&types=songs&limit=25&l=en-US&platform=web&format%5Bresources%5D=map"
        val body = httpGet(url) {
            header("Authorization", "Bearer $token")
            header("Origin", "https://music.apple.com")
            header("Referer", "https://music.apple.com/")
            header("User-Agent", USER_AGENT)
        } ?: run {
            // 401 → token expired; refresh once and retry.
            appleToken.set(null)
            val fresh = appleToken() ?: return emptyList()
            httpGet(url) {
                header("Authorization", "Bearer $fresh")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", USER_AGENT)
            } ?: return emptyList()
        }
        val out = mutableListOf<JSONObject>()
        try {
            val root = JSONObject(body)
            root.optJSONObject("results")?.optJSONArray("songs")?.let { songsArr ->
                for (i in 0 until songsArr.length()) {
                    songsArr.optJSONObject(i)?.let { out.add(it) }
                }
            }
        } catch (_: Exception) { /* non-JSON body */ }
        return out
    }

    /** paxsenix word-synced lyrics for an Apple Music track id. */
    private fun fetchLyricsForId(id: String): String? {
        val body = httpGet("$PAX_BASE/apple-music/lyrics?id=" + URLEncoder.encode(id, StandardCharsets.UTF_8.name())) {
            header("Accept", "application/json")
            header("User-Agent", USER_AGENT)
        } ?: return null
        val root = try { JSONObject(body) } catch (_: Exception) { return null }
        // Prefer TTML content — reuse the shared TtmlParser.
        root.optString("ttmlContent").takeIf { it.isNotBlank() }?.let { ttml ->
            TtmlParser.toEnhancedLrc(ttml)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        // Fallback: timestamped content lines with per-word text[] entries.
        val content = root.optJSONArray("content") ?: return root.optString("plain")
            .takeIf { it.isNotBlank() }?.lines()?.joinToString("\n") { "[00:00.00]$it" }
        return buildString {
            for (i in 0 until content.length()) {
                val line = content.optJSONObject(i) ?: continue
                val ts = line.optLong("timestamp", 0L)
                append(formatTag(ts, '['))
                val words = line.optJSONArray("text")
                if (words != null && words.length() > 0) {
                    for (k in 0 until words.length()) {
                        val w = words.optJSONObject(k) ?: continue
                        append(formatTag(w.optLong("timestamp", ts), '<'))
                        append(w.optString("text"))
                    }
                } else {
                    append(line.optString("text").ifBlank {
                        words?.optJSONObject(0)?.optString("text").orEmpty()
                    })
                }
                append('\n')
            }
        }.trim().takeIf { it.isNotBlank() }
    }

    /** Scrape the Apple Music JWT from the web player's index bundle. */
    private fun appleToken(): String? {
        appleToken.get()?.let { return it }
        return kotlinx.coroutines.runBlocking {
            tokenMutex.withLock {
                appleToken.get()?.let { return@withLock it }
                try {
                    val mainPage = httpGet("https://beta.music.apple.com") {
                        header("User-Agent", USER_AGENT)
                    } ?: throw IllegalStateException("apple main page unavailable")
                    val jsUri = Regex("""/assets/index~[^/]+\.js""").find(mainPage)?.value
                        ?: throw IllegalStateException("index JS not found")
                    val jsBody = httpGet("https://beta.music.apple.com$jsUri") {
                        header("User-Agent", USER_AGENT)
                    } ?: throw IllegalStateException("index JS unavailable")
                    val token = Regex("""eyJ[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+""")
                        .find(jsBody)?.value ?: throw IllegalStateException("JWT not found")
                    appleToken.set(token)
                    token
                } catch (e: Exception) {
                    Log.d(TAG, "apple token failed: ${e.message}")
                    null
                }
            }
        }
    }

    private fun clean(text: String): String =
        text.replace(Regex("\\(.*?\\)"), "").replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\s+"), " ").trim()

    private fun formatTag(ms: Long, open: Char): String {
        val totalSec = ms / 1000
        val close = if (open == '<') '>' else ']'
        return "%c%02d:%02d.%03d%c".format(open, totalSec / 60, totalSec % 60, ms % 1000, close)
    }

    private inline fun httpGet(url: String, configure: Request.Builder.() -> Unit): String? {
        val builder = Request.Builder().url(url)
        builder.configure()
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()?.takeIf { it.isNotBlank() }
        }
    }
}
