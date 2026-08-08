package com.omersusin.pitube.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object InnerTubeFeed {
    private val client = OkHttpClient()
    private const val WEB_VERSION = "2.20250311.00.00"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun sapisidHash(cookies: Map<String, String>): String? {
        val s = cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"] ?: cookies["APISID"] ?: return null
        val ts = System.currentTimeMillis() / 1000
        return "${ts}_${sha1("$ts $s https://www.youtube.com")}"
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

    suspend fun fetchFeed(context: Context, browseId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getCookies(context)
            if (cookies.isEmpty()) return@withContext emptyList()
            val raw = AuthManager.getRawCookies(context)
            val auth = sapisidHash(cookies)

            val out = mutableListOf<VideoItem>()
            var token: String? = null

            for (page in 0..2) {
                val body = if (token == null) bodyJson { it.put("browseId", browseId) } else bodyJson { it.put("continuation", token) }
                val rb = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", UA)
                    .addHeader("Cookie", raw)
                    .addHeader("Origin", "https://www.youtube.com")
                    .addHeader("Referer", "https://www.youtube.com/")
                    .addHeader("X-YouTube-Client-Name", "1")
                    .addHeader("X-YouTube-Client-Version", WEB_VERSION)
                if (auth != null) rb.addHeader("Authorization", "SAPISIDHASH $auth")
                val resp = client.newCall(rb.post(body.toRequestBody("application/json".toMediaType())).build()).execute()
                val json = JSONObject(resp.body?.string() ?: "{}")

                val before = out.size
                walkVideos(json, out)
                token = findToken(json)
                if (out.size == before || token == null) break
            }
            out.distinctBy { it.videoId }
        } catch (e: Exception) { e.printStackTrace(); emptyList() }
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
