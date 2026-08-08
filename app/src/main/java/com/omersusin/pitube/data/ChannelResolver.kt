package com.omersusin.pitube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChannelPage(val name: String, val avatarUrl: String?, val bannerUrl: String? = null, val videos: List<VideoItem>)

object ChannelResolver {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun resolve(channelIdOrUrl: String): ChannelPage? = withContext(Dispatchers.IO) {
        try {
            val url = toUrl(channelIdOrUrl)
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null

            val html = resp.body?.string() ?: return@withContext null
            
            // Extract initial data from YouTube page
            val match = Regex("var ytInitialData = (.+?);</script>").find(html)
            if (match == null) return@withContext null

            val json = JSONObject(match.groupValues[1])
            val metadata = json.optJSONObject("metadata")
                ?.optJSONObject("channelMetadataRenderer")
            val name = metadata?.optString("title") ?: ""
            val avatar = metadata?.optJSONObject("avatar")
                ?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
            val banner = metadata?.optJSONObject("banner")
                ?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")

            val videos = mutableListOf<VideoItem>()
            
            // Parse video grid
            val tabs = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
            
            tabs?.let {
                for (i in 0 until it.length()) {
                    it.optJSONObject(i)
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
                                            val duration = vr.optJSONObject("lengthText")?.optString("simpleText") ?: "0:00"
                                            val durationSec = parseDuration(duration)

                                            videos.add(VideoItem(
                                                url = "https://www.youtube.com/watch?v=$videoId",
                                                title = title,
                                                thumbnailUrl = thumb,
                                                uploaderName = name,
                                                uploaderAvatar = avatar,
                                                uploaderUrl = url,
                                                duration = durationSec,
                                                views = 0L,
                                                uploadedDate = null,
                                                isShort = false
                                            ))
                                        }
                                    }
                            }
                        }
                }
            }

            ChannelPage(name, avatar, banner, videos)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun toUrl(input: String): String = when {
        input.startsWith("http") -> input
        input.startsWith("@") || input.startsWith("user/") || input.startsWith("c/") -> "https://www.youtube.com/$input"
        else -> "https://www.youtube.com/channel/$input"
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
