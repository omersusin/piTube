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

    private fun getAuthHeader(cookies: Map<String, String>): String? {
        // Google recently changed cookie names, check all possibilities
        val sapisid = cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"] ?: cookies["APISID"] ?: return null
        val ts = System.currentTimeMillis() / 1000
        return "${ts}_${sha1("$ts $sapisid https://www.youtube.com")}"
    }

    private fun buildContext(): JSONObject {
        return JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", "19.09.37")
                    put("androidSdkVersion", 30)
                    put("hl", "en")
                    put("gl", "US")
                })
            })
        }
    }

    suspend fun fetchFeed(context: Context, browseId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getCookies(context)
            val auth = getAuthHeader(cookies)
            val cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            
            val out = mutableListOf<VideoItem>()
            var continuationToken: String? = null
            
            // Fetch up to 3 pages to get a full feed
            for (page in 0..2) {
                val body = buildContext()
                if (continuationToken == null) {
                    body.put("browseId", browseId)
                } else {
                    body.put("continuation", continuationToken)
                }

                val reqBuilder = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))

                if (cookieHeader.isNotBlank()) reqBuilder.addHeader("Cookie", cookieHeader)
                if (auth != null) reqBuilder.addHeader("Authorization", "SAPISIDHASH $auth")
                reqBuilder.addHeader("X-YouTube-Client-Name", "3")
                reqBuilder.addHeader("X-YouTube-Client-Version", "19.09.37")

                val resp = client.newCall(reqBuilder.build()).execute()
                val json = JSONObject(resp.body?.string() ?: "{}")
                
                continuationToken = null
                parseResponse(json, out, page == 0) { continuationToken = it }
                
                if (continuationToken == null) break
            }
            
            out.distinctBy { it.videoId }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseResponse(json: JSONObject, out: MutableList<VideoItem>, isInitial: Boolean, onContinuation: (String) -> Unit) {
        val itemsArray: JSONArray? = if (isInitial) {
            json.optJSONObject("contents")
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("richGridRenderer")
                ?.optJSONArray("contents")
        } else {
            json.optJSONArray("onResponseReceivedActions")
                ?.optJSONObject(0)
                ?.optJSONObject("appendContinuationItemsAction")
                ?.optJSONArray("continuationItems")
        }

        if (itemsArray == null) return

        for (i in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(i) ?: continue
            
            val videoRenderer = item.optJSONObject("richItemRenderer")?.optJSONObject("content")?.optJSONObject("videoRenderer")
                ?: item.optJSONObject("videoRenderer")
                
            if (videoRenderer != null) {
                runCatching {
                    val id = videoRenderer.getString("videoId")
                    val title = videoRenderer.getJSONObject("title").optJSONArray("runs")?.optJSONObject(0)?.optString("text") 
                        ?: videoRenderer.optJSONObject("title")?.optString("simpleText") ?: ""
                    val thumb = videoRenderer.getJSONObject("thumbnail").getJSONArray("thumbnails").optJSONObject(0)?.optString("url") ?: ""
                    val by = videoRenderer.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)
                    val date = videoRenderer.optJSONObject("publishedTimeText")?.optString("simpleText")
                    
                    out.add(VideoItem(
                        url = "https://www.youtube.com/watch?v=$id", title = title, thumbnailUrl = thumb,
                        uploaderName = by?.optString("text") ?: "", uploaderAvatar = null, duration = 0,
                        views = 0, uploadedDate = date, isShort = false
                    ))
                }
            }
            
            val contRenderer = item.optJSONObject("continuationItemRenderer")
            if (contRenderer != null) {
                val token = contRenderer.optJSONObject("continuationEndpoint")
                    ?.optJSONObject("continuationCommand")
                    ?.optString("token")
                if (token != null) onContinuation(token)
            }
        }
    }
}
