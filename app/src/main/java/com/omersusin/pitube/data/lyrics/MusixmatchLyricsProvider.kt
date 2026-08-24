package com.omersusin.pitube.data.lyrics

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Musixmatch lyrics provider — ported from vivi-music's client (OkHttp/org.json
 * adaptation). Uses the web-desktop-app flow: dynamically scraped signing
 * secret -> HmacSHA256 URL signature -> usertoken.
 *
 * Also exposes [fetchTranslation] for `track.subtitle.translation.get`, which
 * returns translated synced lines used by the lyrics view's translation layer.
 */
class MusixmatchLyricsProvider(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()) : LyricsProvider() {
    override val id = "musixmatch"

    private val secretCache = AtomicReference<String?>(null)
    private val tokenCache = AtomicReference<String?>(null)

    private companion object {
        const val TAG = "Musixmatch"
        const val BASE_URL = "https://apic.musixmatch.com/ws/1.1/"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
        const val FALLBACK_SECRET = "b3dc8788299f5806a70a6a20a0cb0ffc"
        const val APP_ID = "web-desktop-app-v1.0"
    }

    // ── Auth / signing ───────────────────────────────────────────────────────

    private fun getSecret(): String {
        secretCache.get()?.let { return it }
        val secret = try {
            val searchPage = fetchBody("https://www.musixmatch.com/search") {
                header("User-Agent", USER_AGENT)
                header("Cookie", "mxm_bab=AB")
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                header("Accept-Language", "en-US,en;q=0.9")
            } ?: throw IllegalStateException("search page unavailable")
            val appJsUrl = Regex("""src="([^"]*/_next/static/chunks/pages/_app-[^"]+\.js)"""")
                .find(searchPage)?.groupValues?.get(1)
                ?: throw IllegalStateException("_app JS not found")
            val js = fetchBody(appJsUrl) {
                header("User-Agent", USER_AGENT)
                header("Accept", "*/*")
            } ?: throw IllegalStateException("app JS unavailable")
            val encoded = Regex("""from\(\s*"(.*?)"\s*\.split""").find(js)?.groupValues?.get(1)
                ?: throw IllegalStateException("secret not found in JS")
            String(Base64.decode(encoded.reversed(), Base64.DEFAULT), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.d(TAG, "dynamic secret failed, using fallback: ${e.message}")
            FALLBACK_SECRET
        }
        secretCache.set(secret)
        Log.d(TAG, "secret resolved via ${if (secret == FALLBACK_SECRET) "FALLBACK" else "dynamic scrape"}")
        return secret
    }

    private fun sign(url: String, secret: String): String {
        val normalized = url.replace("%20", "+").replace(" ", "+")
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        }
        val sig = URLEncoder.encode(
            Base64.encodeToString(mac.doFinal((normalized + date).toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP),
            StandardCharsets.UTF_8.name(),
        )
        return "$normalized&signature=$sig&signature_protocol=sha256"
    }

    private fun getUserToken(secret: String): String {
        tokenCache.get()?.let { return it }
        val url = sign("${BASE_URL}token.get?app_id=$APP_ID&format=json", secret)
        val body = fetchBody(url) {
            header("User-Agent", USER_AGENT)
            header("Accept", "application/json, text/plain, */*")
        } ?: throw IllegalStateException("token endpoint unreachable")
        val root = JSONObject(body)
        val status = root.optJSONObject("message")?.optJSONObject("header")?.optInt("status_code") ?: 0
        if (status != 200) {
            // Non-200 from the MINT endpoint means our signing secret is stale
            // or flagged (not merely an expired user-token) — callers should
            // re-scrape the secret before giving up.
            throw TokenMintException("token status $status")
        }
        val token = root.optJSONObject("message")?.optJSONObject("body")
            ?.optString("user_token").orEmpty()
        if (token.isBlank()) throw IllegalStateException("empty user_token")
        tokenCache.set(token)
        return token
    }

    /** Token retry wrapper: 401 means the token expired — mint once and retry.
     *  A [TokenMintException] means the mint endpoint itself rejected us — the
     *  cached secret (often the static fallback) is stale: re-scrape once. */
    private inline fun <T> withTokenRetry(block: (secret: String, token: String) -> T): T {
        val secret = getSecret()
        try {
            return try {
                block(secret, getUserToken(secret))
            } catch (e: TokenExpiredException) {
                tokenCache.set(null)
                block(secret, getUserToken(secret))
            } catch (e: TokenMintException) {
                Log.w(TAG, "token mint failed (${e.message}) — re-scraping secret")
                secretCache.set(null)
                val fresh = getSecret()
                block(fresh, getUserToken(fresh))
            }
        } finally {
            if (secretCache.get() == null) {
                // Both attempts failed to produce a usable secret/token chain;
                // restore fallback so the next session has a starting point.
                secretCache.compareAndSet(null, FALLBACK_SECRET)
            }
        }
    }

    private class TokenExpiredException : RuntimeException()

    private class TokenMintException(message: String) : RuntimeException(message)

    // ── Lyrics fetch ─────────────────────────────────────────────────────────

    override suspend fun fetch(title: String, artist: String, album: String, durationMs: Long): String? =
        withContext(Dispatchers.IO) {
            if (title.isBlank() || artist.isBlank()) return@withContext null
            try {
                withTokenRetry { secret, token ->
                    val trackId = searchTrack(title, artist, (durationMs / 1000).toInt(), secret, token)
                        ?: throw IllegalStateException("track not found")
                    // Tier chain: richsync (word-level) → subtitle (line-synced) → plain
                    richsyncLrc(trackId, secret, token)
                        ?: subtitleLrc(trackId, secret, token)
                        ?: plainLyrics(trackId, secret, token)
                }
            } catch (e: Exception) {
                Log.d(TAG, "fetch failed: ${e.message}")
                null
            }
        }

    /**
     * Translated synced lines for the given track in [targetLang]
     * (BCP-47-ish, e.g. "tr"). Returns LRC text parseable by [LrcParser].
     */
    suspend fun fetchTranslation(
        title: String,
        artist: String,
        durationMs: Long,
        targetLang: String,
    ): String? = withContext(Dispatchers.IO) {
        if (targetLang.isBlank() || targetLang.startsWith("en")) return@withContext null
        try {
            withTokenRetry { secret, token ->
                val durSec = (durationMs / 1000).toInt()
                var trackId = searchTrack(title, artist, durSec, secret, token)
                if (trackId == null && durSec > 0) {
                    trackId = searchTrack(title, artist, 0, secret, token)
                }
                trackId ?: throw IllegalStateException("track not found for translation")
                val lang = targetLang.take(2).lowercase()
                val url = "${BASE_URL}track.subtitle.translation.get?app_id=$APP_ID&format=json" +
                    "&track_id=$trackId&translation_list_source=auto&lang=$lang&usertoken=$token"
                val body = fetchBody(sign(url, secret)) {
                    header("User-Agent", USER_AGENT)
                    header("Accept", "application/json, text/plain, */*")
                } ?: throw IllegalStateException("translation endpoint unreachable")
                val headerStatus = JSONObject(body).optJSONObject("message")
                    ?.optJSONObject("header")?.optInt("status_code") ?: 0
                if (headerStatus == 401) throw TokenExpiredException()
                if (headerStatus != 200) throw IllegalStateException("translation status $headerStatus")
                val messageBody = JSONObject(body).optJSONObject("message")?.optJSONObject("body")
                translationResponseToLrc(messageBody)
                    ?: run {
                        // Unknown schema: surface the actual keys so the next
                        // diagnostics report shows what the endpoint returned.
                        Log.w(TAG, "translation body keys=${messageBody?.keys()} — unrecognized shape")
                        throw IllegalStateException("unrecognized translation body shape")
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "translation failed: ${e.message}")
            null
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun searchTrack(title: String, artist: String, durationSec: Int, secret: String, token: String): Long? {
        val encTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
        val encArtist = URLEncoder.encode(artist, StandardCharsets.UTF_8.name())
        val url = "${BASE_URL}track.search?app_id=$APP_ID&format=json&q_track=$encTitle" +
            "&q_artist=$encArtist&f_has_lyrics=true&page_size=10&usertoken=$token"
        val body = fetchBody(sign(url, secret)) {
            header("User-Agent", USER_AGENT)
            header("Accept", "application/json, text/plain, */*")
        } ?: return null
        val root = JSONObject(body)
        val status = root.optJSONObject("message")?.optJSONObject("header")?.optInt("status_code") ?: 0
        if (status == 401) throw TokenExpiredException()
        val trackList = root.optJSONObject("message")?.optJSONObject("body")
            ?.optJSONArray("track_list") ?: return null
        if (trackList.length() == 0) return null

        val normQuery = cleanText(title)
        data class Scored(val id: Long, val textScore: Int, val durDelta: Int)
        var best: Scored? = null
        for (i in 0 until trackList.length()) {
            val track = trackList.optJSONObject(i)?.optJSONObject("track") ?: continue
            val id = track.optLong("track_id"); if (id <= 0) continue
            val name = cleanText(track.optString("track_name"))
            val textScore = when {
                name == normQuery -> 0
                name.contains(normQuery) || normQuery.contains(name) -> 1
                else -> 2
            }
            val trackDur = track.optInt("track_length", 0)
            val durDelta = when {
                durationSec > 0 && trackDur > 0 -> abs(trackDur - durationSec)
                trackDur == 0 -> 999
                else -> Int.MAX_VALUE
            }
            val candidate = Scored(id, textScore, durDelta)
            if (best == null ||
                (candidate.textScore.toLong() shl 32) + candidate.durDelta <
                (best.textScore.toLong() shl 32) + best.durDelta
            ) best = candidate
        }
        return best?.id
    }

    private fun richsyncLrc(trackId: Long, secret: String, token: String): String? {
        val url = "${BASE_URL}track.richsync.get?app_id=$APP_ID&format=json&track_id=$trackId&usertoken=$token"
        val body = fetchBody(sign(url, secret)) {
            header("User-Agent", USER_AGENT); header("Accept", "application/json, text/plain, */*")
        } ?: return null
        val headerStatus = JSONObject(body).optJSONObject("message")?.optJSONObject("header")?.optInt("status_code") ?: 0
        if (headerStatus == 401) throw TokenExpiredException()
        val richsyncBody = JSONObject(body).optJSONObject("message")?.optJSONObject("body")
            ?.optJSONObject("richsync")?.optString("richsync_body").orEmpty()
        if (richsyncBody.isBlank()) return null
        val entries = JSONArray(richsyncBody)
        return buildString {
            for (i in 0 until entries.length()) {
                val e = entries.optJSONObject(i) ?: continue
                val lineText = e.optString("x").trim()
                if (lineText.isEmpty() && e.optJSONArray("l") == null) continue
                val lineTimeMs = (e.optDouble("ts", 0.0) * 1000).toLong()
                append(formatTag(lineTimeMs, '[')); 
                val words = e.optJSONArray("l")
                if (words != null && words.length() > 0) {
                    for (k in 0 until words.length()) {
                        val w = words.optJSONObject(k) ?: continue
                        val c = w.optString("c")
                        val offset = w.optDouble("o", 0.0)
                        if (c.isBlank()) {
                            append(c)
                        } else {
                            append(formatTag(((e.optDouble("ts", 0.0) + offset) * 1000).toLong(), '<'))
                            append(c)
                        }
                    }
                } else {
                    append(lineText)
                }
                append('\n')
            }
        }.trim().takeIf { it.isNotBlank() }
    }

    private fun subtitleLrc(trackId: Long, secret: String, token: String): String? {
        val url = "${BASE_URL}track.subtitle.get?app_id=$APP_ID&format=json&track_id=$trackId&usertoken=$token"
        val body = fetchBody(sign(url, secret)) {
            header("User-Agent", USER_AGENT); header("Accept", "application/json, text/plain, */*")
        } ?: return null
        val headerStatus = JSONObject(body).optJSONObject("message")?.optJSONObject("header")?.optInt("status_code") ?: 0
        if (headerStatus == 401) throw TokenExpiredException()
        val subtitleBody = JSONObject(body).optJSONObject("message")?.optJSONObject("body")
            ?.optJSONObject("subtitle")?.optString("subtitle_body").orEmpty()
        if (subtitleBody.isBlank()) return null
        return translationOrSubtitleJsonToLrc(subtitleBody)
    }

    private fun plainLyrics(trackId: Long, secret: String, token: String): String? {
        val url = "${BASE_URL}track.lyrics.get?app_id=$APP_ID&format=json&track_id=$trackId&usertoken=$token"
        val body = fetchBody(sign(url, secret)) {
            header("User-Agent", USER_AGENT); header("Accept", "application/json, text/plain, */*")
        } ?: return null
        val headerStatus = JSONObject(body).optJSONObject("message")?.optJSONObject("header")?.optInt("status_code") ?: 0
        if (headerStatus == 401) throw TokenExpiredException()
        val lyricsBody = JSONObject(body).optJSONObject("message")?.optJSONObject("body")
            ?.optJSONObject("lyrics")?.optString("lyrics_body").orEmpty()
        if (lyricsBody.isBlank()) return null
        return lyricsBody.lines().joinToString("\n") { "[00:00.00]$it" }
    }

    /**
     * `track.subtitle.translation.get` has been observed in several shapes
     * across deployments. Try each defensively:
     *  1. body.subtitle.subtitle_body — subtitle-format JSON string
     *  2. body.translations_list — [{time:{total}, text|translation|match_line}]
     *  3. body.translations / body.translation_list — same entry shape
     */
    private fun translationResponseToLrc(messageBody: JSONObject?): String? {
        if (messageBody == null) return null
        messageBody.optJSONObject("subtitle")?.let { sub ->
            val body = if (sub.has("subtitle_body")) sub.optString("subtitle_body") else sub.toString()
            translationOrSubtitleJsonToLrc(body)?.let { return it }
        }
        for (key in listOf("translations_list", "translations", "translation_list")) {
            val arr = messageBody.optJSONArray(key) ?: continue
            val lrc = buildString {
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val total = e.optJSONObject("time")?.optDouble("total")
                    val text = e.optString("text")
                        .ifBlank { e.optString("translation") }
                        .ifBlank { e.optString("match_line") }
                        .ifBlank { e.optString("subtitle_line") }
                    if (total == null || text.isBlank()) continue
                    append(formatTag((total * 1000).toLong(), '['))
                    append(text)
                    append('\n')
                }
            }.trim()
            if (lrc.isNotBlank()) return lrc
        }
        return null
    }

    /** Subtitle JSON (`[{time:{total,...},text},...]`) → standard LRC text. */
    private fun translationOrSubtitleJsonToLrc(body: String): String? = try {
        val entries = JSONArray(body)
        buildString {
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val total = entry.optJSONObject("time")?.optDouble("total") ?: continue
                append(formatTag((total * 1000).toLong(), '['))
                append(entry.optString("text"))
                append('\n')
            }
        }.trim().takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun cleanText(text: String): String =
        text.replace(",", " ").replace("&", " ")
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun formatTag(ms: Long, open: Char): String {
        val totalSec = ms / 1000
        val close = if (open == '<') '>' else ']'
        return "%c%02d:%02d.%03d%c".format(open, totalSec / 60, totalSec % 60, ms % 1000, close)
    }

    private inline fun fetchBody(url: String, configure: Request.Builder.() -> Unit): String? {
        val builder = Request.Builder().url(url)
        builder.configure()
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()?.takeIf { it.isNotBlank() }
        }
    }
}
