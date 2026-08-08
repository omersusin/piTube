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

object AuthDebug { val snippet = mutableStateOf("") }

object InnerTubeFeed {
    private val client = OkHttpClient()
    private const val WEB_VERSION = "2.20260114.08.00"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val ORIGIN = "https://www.youtube.com"

    data class ItStream(val url: String?, val mime: String, val height: Int, val bitrate: Int)
    data class PlayerData(val hls: String?, val progressive: ItStream?, val videoOnly: ItStream?, val audio: ItStream?)

    private fun sha1(s: String): String = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun tsHash(cookieValue: String): String {
        val ts = System.currentTimeMillis() / 1000
        return "${ts}_${sha1("$ts $cookieValue $ORIGIN")}"
    }

    private fun sapisidOf(cookies: Map<String, String>): String? =
        cookies["SAPISID"] ?: cookies["__Secure-1PAPISID"] ?: cookies["APISID"] ?: cookies["__Secure-3PAPISID"]

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
        root.put("context", JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB"); put("clientVersion", WEB_VERSION); put("hl", "en"); put("gl", "US")
            })
        })
        extra(root)
        return root.toString()
    }

    suspend fun fetchPlayer(videoId: String): PlayerData? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("videoId", videoId)
                put("contentCheckOk", true); put("racyCheckOk", true)
                put("context", JSONObject().apply { put("client", JSONObject().apply {
                    put("clientName", "ANDROID"); put("clientVersion", "19.09.37"); put("androidSdkVersion", 30); put("hl", "en"); put("gl", "US")
                }) })
            }
            val req = Request.Builder().url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            val json = JSONObject(client.newCall(req).execute().body?.string() ?: "{}")
            val sd = json.optJSONObject("streamingData") ?: return@withContext null
            val hls = sd.optString("hlsManifestUrl", "").takeIf { it.isNotBlank() }
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
            PlayerData(
                hls = hls,
                progressive = formats.maxByOrNull { it.height },
                videoOnly = adaptive.filter { it.mime.startsWith("video") }.maxByOrNull { it.height },
                audio = adaptive.filter { it.mime.startsWith("audio") }.maxByOrNull { it.bitrate }
            )
        } catch (e: Exception) { null }
    }

    suspend fun fetchAccount(context: Context): AccountFetcher.AccountInfo? = withContext(Dispatchers.IO) {
        try {
            if (!AuthManager.isLoggedIn(context)) return@withContext null
            val rb = Request.Builder().url("https://www.youtube.com/youtubei/v1/account/account_menu?prettyPrint=false")
                .addHeader("Content-Type", "application/json")
            applyAuth(rb, context)
            val resp = client.newCall(rb.post(bodyJson { }.toRequestBody("application/json".toMediaType())).build()).execute()
            val bodyStr = resp.body?.string() ?: ""
            AuthDebug.snippet.value = "account_menu ${resp.code}: ${bodyStr.take(300)}"
            val h = JSONObject(bodyStr).optJSONObject("header")?.optJSONObject("activeAccountHeaderRenderer") ?: return@withContext null
            val name = h.optJSONObject("title")?.optString("simpleText")
                ?: h.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
            val handle = h.optJSONObject("accountHandle")?.optString("simpleText") ?: ""
            val photo = h.optJSONObject("accountPhoto")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
            if (name.isBlank()) null else AccountFetcher.AccountInfo(name, photo, handle)
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    suspend fun fetchFeed(context: Context, browseId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getCookies(context)
            if (cookies.isEmpty()) return@withContext emptyList()
            val out = mutableListOf<VideoItem>()
            var token: String? = null
            for (page in 0..2) {
                val body = if (token == null) bodyJson { it.put("browseId", browseId) } else bodyJson { it.put("continuation", token) }
                val rb = Request.Builder().url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                    .addHeader("Content-Type", "application/json")
                applyAuth(rb, context)
                val resp = client.newCall(rb.post(body.toRequestBody("application/json".toMediaType())).build()).execute()
                val bodyStr = resp.body?.string() ?: "{}"
                if (page == 0) AuthDebug.snippet.value = "browse $browseId ${resp.code}: ${bodyStr.take(300)}"
                val json = JSONObject(bodyStr)
                val before = out.size
                walkVideos(json, out)
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

    private fun walkVideos(node: Any?, out: MutableList<VideoItem>) {
        when (node) {
            is JSONObject -> {
                val vr = node.optJSONObject("videoRenderer")
                    ?: node.optJSONObject("richItemRenderer")?.optJSONObject("content")?.optJSONObject("videoRenderer")
                if (vr != null) runCatching {
                    val id = vr.getString("videoId")
                    val title = vr.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: vr.optJSONObject("title")?.optString("simpleText") ?: ""
                    val thumb = vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url") ?: ""
                    val by = vr.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)
                    val date = vr.optJSONObject("publishedTimeText")?.optString("simpleText")
                    out.add(VideoItem(url = "https://www.youtube.com/watch?v=$id", title = title, thumbnailUrl = thumb,
                        uploaderName = by?.optString("text") ?: "", uploaderAvatar = null, duration = 0, views = 0,
                        uploadedDate = date, isShort = false))
                }
                val keys = node.keys()
                while (keys.hasNext()) walkVideos(node.opt(keys.next()), out)
            }
            is JSONArray -> { for (i in 0 until node.length()) walkVideos(node.opt(i), out) }
        }
    }

    private fun findToken(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                val cr = node.optJSONObject("continuationItemRenderer")
                if (cr != null) {
                    val t = cr.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")?.optString("token")
                    if (!t.isNullOrBlank()) return t
                }
                val keys = node.keys()
                while (keys.hasNext()) { findToken(node.opt(keys.next()))?.let { return it } }
            }
            is JSONArray -> { for (i in 0 until node.length()) { findToken(node.opt(i))?.let { return it } } }
        }
        return null
    }
}
