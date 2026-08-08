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

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun sapisidHash(cookies: Map<String, String>): String? {
        val sapisid = cookies["SAPISID"] ?: cookies["APISID"] ?: return null
        val ts = System.currentTimeMillis() / 1000
        return "${ts}_${sha1("$ts $sapisid https://www.youtube.com")}"
    }

    suspend fun fetchFeed(context: Context, browseId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getCookies(context)
            val hash = sapisidHash(cookies) ?: return@withContext emptyList()
            val cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20240101.00.00","hl":"en","gl":"US"}},"browseId":"$browseId"}"""
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", cookieHeader)
                .addHeader("Authorization", "SAPISIDHASH $hash")
                .addHeader("Origin", "https://www.youtube.com")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            val json = JSONObject(resp.body?.string() ?: "{}")
            val out = mutableListOf<VideoItem>()
            walk(json, out)
            out.distinctBy { it.videoId }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun walk(node: Any?, out: MutableList<VideoItem>) {
        when (node) {
            is JSONObject -> {
                if (node.has("videoRenderer")) {
                    runCatching {
                        val vr = node.getJSONObject("videoRenderer")
                        val id = vr.getString("videoId")
                        val title = vr.getJSONObject("title").getJSONArray("runs").getJSONObject(0).getString("text")
                        val thumb = vr.getJSONObject("thumbnail").getJSONArray("thumbnails").getJSONObject(0).getString("url")
                        val by = vr.getJSONObject("shortBylineText").getJSONArray("runs").getJSONObject(0)
                        val date = runCatching { vr.getJSONObject("publishedTimeText").getString("simpleText") }.getOrNull()
                        out.add(VideoItem(
                            url = "https://www.youtube.com/watch?v=$id", title = title, thumbnailUrl = thumb,
                            uploaderName = by.getString("text"), uploaderAvatar = null, duration = 0,
                            views = 0, uploadedDate = date, isShort = false
                        ))
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()), out)
            }
            is JSONArray -> { for (i in 0 until node.length()) walk(node.opt(i), out) }
        }
    }
}
