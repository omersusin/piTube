package com.omersusin.pitube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object InnerTubeFeed {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val INNERTUBE_URL = "https://www.youtube.com/youtubei/v1/browse"

    suspend fun fetchFeed(context: android.content.Context, browseId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getRawCookies(context)
            if (cookies.isBlank()) return@withContext emptyList()

            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20240101.00.00"}},"browseId":"$browseId"}"""
            val req = Request.Builder()
                .url(INNERTUBE_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", cookies)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val json = JSONObject(resp.body?.string() ?: return@withContext emptyList())
            val videos = mutableListOf<VideoItem>()

            json.optJSONObject("contents")
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.let { tabs ->
                    for (i in 0 until tabs.length()) {
                        tabs.optJSONObject(i)
                            ?.optJSONObject("tabRenderer")
                            ?.optJSONObject("content")
                            ?.optJSONObject("richGridRenderer")
                            ?.optJSONArray("contents")
                            ?.let { grid ->
                                for (j in 0 until grid.length()) {
                                    grid.optJSONObject(j)
                                        ?.optJSONObject("richItemRenderer")
                                        ?.optJSONObject("content")
                                        ?.optJSONObject("videoRenderer")
                                        ?.let { vr ->
                                            val videoId = vr.optString("videoId")
                                            if (videoId.isNotBlank()) {
                                                val title = vr.optJSONObject("title")?.optJSONArray("runs")
                                                    ?.optJSONObject(0)?.optString("text") ?: ""
                                                val thumb = vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                                    ?.optJSONObject(0)?.optString("url")
                                                val channelName = vr.optJSONObject("ownerText")?.optJSONArray("runs")
                                                    ?.optJSONObject(0)?.optString("text") ?: ""
                                                val channelAvatar = vr.optJSONObject("channelThumbnailSupportedRenderers")
                                                    ?.optJSONObject("channelThumbnailWithLinkRenderer")
                                                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                                    ?.optJSONObject(0)?.optString("url")
                                                val duration = vr.optJSONObject("lengthText")?.optString("simpleText") ?: "0:00"
                                                val durationSec = parseDuration(duration)
                                                val views = vr.optJSONObject("viewCountText")?.optString("simpleText")?.replace(Regex("[^0-9]"), "")?.toLongOrNull() ?: 0L

                                                videos.add(VideoItem(
                                                    url = "https://www.youtube.com/watch?v=$videoId",
                                                    title = title,
                                                    thumbnailUrl = thumb,
                                                    uploaderName = channelName,
                                                    uploaderAvatar = channelAvatar,
                                                    uploaderUrl = "",
                                                    duration = durationSec,
                                                    views = views,
                                                    uploadedDate = null,
                                                    isShort = false
                                                ))
                                            }
                                        }
                                }
                            }
                    }
                }

            videos
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseDuration(text: String): Int {
        val parts = text.split(":")
        return when (parts.size) {
            2 -> parts[0].toInt() * 60 + parts[1].toInt()
            3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
            else -> 0
        }
    }
}
