package com.omersusin.pitube.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object AuthDebug { val snippet = mutableStateOf(""); val diag = mutableStateOf("") }

object InnerTubeFeed {
    private val client = OkHttpClient()
    private const val WEB_VERSION = "2.20260114.08.00"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val ORIGIN = "https://www.youtube.com"

    data class ItStream(val url: String?, val mime: String, val height: Int, val bitrate: Int)
    data class PlayerData(val hls: String?, val progressive: ItStream?, val videoOnly: ItStream?, val audio: ItStream?)

    private fun sha1(s: String): String = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun tsHash(v: String): String { val ts = System.currentTimeMillis() / 1000; return "${ts}_${sha1("$ts $v $ORIGIN")}" }
    private fun sapisidOf(c: Map<String, String>): String? = c["SAPISID"] ?: c["__Secure-1PAPISID"] ?: c["APISID"] ?: c["__Secure-3PAPISID"]

    private fun applyAuth(rb: Request.Builder, context: Context) {
        val cookies = AuthManager.getCookies(context)
        rb.addHeader("Cookie", AuthManager.getRawCookies(context))
        rb.addHeader("User-Agent", UA)
        rb.addHeader("Origin", ORIGIN)
        rb.addHeader("Referer", "$ORIGIN/")
        rb.addHeader("X-Origin", ORIGIN)
        rb.addHeader("X-YouTube-Client-Name", "1")
        rb.addHeader("X-YouTube-Client-Version", WEB_VERSION)
        sapisidOf(cookies)?.let { rb.addHeader("Authorization", "SAPISIDHASH ${tsHash(it)}") }
    }

    private fun bodyJson(extra: (JSONObject) -> Unit): String {
        val root = JSONObject()
        root.put("context", JSONObject().apply { put("client", JSONObject().apply {
            put("clientName", "WEB"); put("clientVersion", WEB_VERSION); put("hl", "en"); put("gl", "US") }) })
        extra(root)
        return root.toString()
    }

    private fun firstText(node: JSONObject, key: String): String? {
        val o = node.optJSONObject(key) ?: return null
        return o.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: o.optString("simpleText", "").takeIf { it.isNotBlank() }
            ?: o.optString("content", "").takeIf { it.isNotBlank() }
    }

    private fun walkVideos(node: Any?, out: MutableList<VideoItem>) {
        when (node) {
            is JSONObject -> {
                val id = node.optString("videoId", "").takeIf { it.length in 8..15 }
                    ?: node.optString("contentId", "").takeIf { it.length in 8..15 }
                if (id != null) {
                    val title = firstText(node, "title") ?: firstText(node, "headline")
                        ?: node.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")?.optJSONObject("title")?.optString("content")
                    val thumb = node.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
                        ?: node.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
                        ?: node.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel")?.optString("imageUrl")
                    if (title != null || thumb != null) {
                        val by = node.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                            ?: node.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                            ?: node.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")?.optJSONObject("metadata")
                                ?.optJSONObject("contentViewModel")?.optJSONArray("textParts")?.optJSONObject(0)?.optJSONObject("text")?.optString("content")
                        val date = node.optJSONObject("publishedTimeText")?.optString("simpleText")
                        out.add(VideoItem("https://www.youtube.com/watch?v=$id", title ?: "", thumb, by ?: "", null, 0, 0, date, false))
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) walkVideos(node.opt(keys.next()), out)
            }
            is JSONArray -> { for (i in 0 until node.length()) walkVideos(node.opt(i), out) }
        }
    }

    private fun findFirst(node: Any?, key: String): JSONObject? {
        when (node) {
            is JSONObject -> { node.optJSONObject(key)?.let { return it }; val k = node.keys(); while (k.hasNext()) { findFirst(node.opt(k.next()), key)?.let { return it } } }
            is JSONArray -> { for (i in 0 until node.length()) { findFirst(node.opt(i), key)?.let { return it } } }
        }
        return null
    }

    private fun findToken(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                val cr = node.optJSONObject("continuationItemRenderer")
                if (cr != null) { val t = cr.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")?.optString("token"); if (!t.isNullOrBlank()) return t }
                val k = node.keys(); while (k.hasNext()) { findToken(node.opt(k.next()))?.let { return it } }
            }
            is JSONArray -> { for (i in 0 until node.length()) { findToken(node.opt(i))?.let { return it } } }
        }
        return null
    }

    private fun tryClient(videoId: String, clientName: String, clientVersion: String, android: Boolean, ios: Boolean = false): PlayerData? {
        return try {
            val body = JSONObject().apply {
                put("videoId", videoId); put("contentCheckOk", true); put("racyCheckOk", true)
                put("context", JSONObject().apply { put("client", JSONObject().apply {
                    put("clientName", clientName); put("clientVersion", clientVersion); put("hl", "en"); put("gl", "US")
                    if (android) put("androidSdkVersion", 30)
                    if (ios) { put("deviceModel", "iPhone14,5"); put("osVersion", "17.0") }
                }) })
            }
            val ua = when {
                android -> "com.google.android.youtube/$clientVersion (Linux; U; Android 11) gzip"
                ios -> "com.google.ios.youtube/$clientVersion (iPhone; CPU iOS 17_0 like Mac OS X)"
                else -> UA
            }
            val req = Request.Builder().url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .addHeader("Content-Type", "application/json").addHeader("User-Agent", ua)
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            val json = JSONObject(client.newCall(req).execute().body?.string() ?: "{}")
            val sd = json.optJSONObject("streamingData") ?: return null
            fun parse(arr: JSONArray?): List<ItStream> {
                val out = mutableListOf<ItStream>()
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val u = o.optString("url", "").takeIf { it.isNotBlank() } ?: continue
                    out.add(ItStream(u, o.optString("mimeType", ""), o.optInt("height", 0), o.optInt("bitrate", 0)))
                }
                return out
            }
            val formats = parse(sd.optJSONArray("formats"))
            val adaptive = parse(sd.optJSONArray("adaptiveFormats"))
            PlayerData(sd.optString("hlsManifestUrl", "").takeIf { it.isNotBlank() },
                formats.maxByOrNull { it.height },
                adaptive.filter { it.mime.startsWith("video") }.maxByOrNull { it.height },
                adaptive.filter { it.mime.startsWith("audio") }.maxByOrNull { it.bitrate })
        } catch (e: Exception) { null }
    }

    private fun good(p: PlayerData?) = p != null && (p.hls != null || p.progressive != null || p.videoOnly != null)

    suspend fun fetchPlayer(videoId: String): PlayerData? = withContext(Dispatchers.IO) {
        tryClient(videoId, "ANDROID_VR", "1.57.20240101", true)?.takeIf { good(it) }
            ?: tryClient(videoId, "ANDROID", "19.09.37", true)?.takeIf { good(it) }
            ?: tryClient(videoId, "IOS", "19.28.1", false, true)?.takeIf { good(it) }
            ?: tryClient(videoId, "TVHTML5", "7.20260114.12.00", false)
            ?: tryClient(videoId, "WEB_EMBEDDED", WEB_VERSION, false)
    }

    suspend fun fetchAccount(context: Context): AccountFetcher.AccountInfo? = withContext(Dispatchers.IO) {
        try {
            if (!AuthManager.isLoggedIn(context)) return@withContext null
            val rb = Request.Builder().url("https://www.youtube.com/youtubei/v1/account/account_menu?prettyPrint=false").addHeader("Content-Type", "application/json")
            applyAuth(rb, context)
            val resp = client.newCall(rb.post(bodyJson { }.toRequestBody("application/json".toMediaType())).build()).execute()
            KodaAuth.refreshFromResponse(context, resp)
            val bodyStr = resp.body?.string() ?: ""
            AuthDebug.snippet.value = "account_menu ${resp.code}: ${bodyStr.take(300)}"
            val h = findFirst(JSONObject(bodyStr), "activeAccountHeaderRenderer") ?: return@withContext null
            val name = h.optJSONObject("title")?.optString("simpleText") ?: h.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
            val handle = h.optJSONObject("accountHandle")?.optString("simpleText") ?: ""
            val photo = h.optJSONObject("accountPhoto")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
            if (name.isBlank()) null else AccountFetcher.AccountInfo(name, photo, handle)
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    suspend fun fetchFeed(context: Context, browseId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            if (AuthManager.getCookies(context).isEmpty()) return@withContext emptyList()
            val out = mutableListOf<VideoItem>()
            var token: String? = null
            for (page in 0..2) {
                val body = if (token == null) bodyJson { it.put("browseId", browseId) } else bodyJson { it.put("continuation", token) }
                val rb = Request.Builder().url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false").addHeader("Content-Type", "application/json")
                applyAuth(rb, context)
                val resp = client.newCall(rb.post(body.toRequestBody("application/json".toMediaType())).build()).execute()
                KodaAuth.refreshFromResponse(context, resp)
                val bodyStr = resp.body?.string() ?: "{}"
                if (page == 0) {
                    AuthDebug.snippet.value = "browse $browseId ${resp.code}: ${bodyStr.take(300)}"
                    AuthDebug.diag.value = "vr=" + bodyStr.contains("videoRenderer") + " rich=" + bodyStr.contains("richItemRenderer") + " lockup=" + bodyStr.contains("ockupViewModel") + " reel=" + bodyStr.contains("reelItemRenderer")
                }
                val json = JSONObject(bodyStr)
                val before = out.size
                walkVideos(json, out)
                AuthDebug.diag.value = (AuthDebug.diag.value) + " found=" + out.size
                token = findToken(json)
                if (out.size == before || token == null) break
            }
            out.distinctBy { it.videoId }
        } catch (e: Exception) { e.printStackTrace(); emptyList() }
    }

    suspend fun browseChannel(channelId: String): ChannelResolver.ChannelPage? = withContext(Dispatchers.IO) {
        try {
            val rb = Request.Builder().url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .addHeader("Content-Type", "application/json").addHeader("User-Agent", UA)
            val resp = client.newCall(rb.post(bodyJson { it.put("browseId", channelId) }.toRequestBody("application/json".toMediaType())).build()).execute()
            val json = JSONObject(resp.body?.string() ?: "{}")
            val name = json.optJSONObject("header")?.optJSONObject("c4TabbedHeaderRenderer")?.optString("title")
                ?: json.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")?.optString("title") ?: ""
            val avatar = json.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")?.optJSONObject("avatar")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
            val videos = mutableListOf<VideoItem>()
            walkVideos(json, videos)
            ChannelResolver.ChannelPage(name, avatar, videos.distinctBy { it.videoId })
        } catch (e: Exception) { null }
    }
}
