package com.omersusin.pitube.data

import android.content.Context
import com.omersusin.pitube.data.VideoItem
import com.omersusin.pitube.data.HistoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.pow
import kotlin.math.ln

object RecommendationEngine {
    private val client = OkHttpClient()
    
    data class TasteProfile(
        val channels: Map<String, Double>,
        val videos: Map<String, Double>,
        val lastUpdated: Long
    )
    
    fun buildTasteProfile(context: Context): TasteProfile {
        val history = HistoryManager.getHistory(context)
        val channelScores = mutableMapOf<String, Double>()
        val videoScores = mutableMapOf<String, Double>()
        val now = System.currentTimeMillis()
        
        history.take(50).forEachIndexed { idx, video ->
            val recencyWeight = 1.0 / (1.0 + idx * 0.1)
            val channelKey = video.uploaderName
            channelScores[channelKey] = (channelScores[channelKey] ?: 0.0) + recencyWeight
            
            val videoKey = video.videoId
            videoScores[videoKey] = (videoScores[videoKey] ?: 0.0) + recencyWeight
        }
        
        return TasteProfile(channelScores, videoScores, now)
    }
    
    suspend fun getRecommendations(context: Context, seedVideo: VideoItem, count: Int = 10): List<VideoItem> = withContext(Dispatchers.IO) {
        val taste = buildTasteProfile(context)
        
        try {
            val body = JSONObject().apply {
                put("videoId", seedVideo.videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20240101.00.00")
                    })
                })
            }
            
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/next?prettyPrint=false")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")
            
            val items = mutableListOf<VideoItem>()
            val results = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnWatchNextResults")
                ?.optJSONObject("secondaryResults")
                ?.optJSONObject("secondaryResults")
                ?.optJSONArray("results")
            
            results?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i)
                    val renderer = item?.optJSONObject("compactVideoRenderer")
                    if (renderer != null) {
                        val videoId = renderer.optString("videoId", "")
                        val title = renderer.optJSONObject("title")?.optString("simpleText", "") ?: ""
                        val thumbnail = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url", "") ?: ""
                        val channel = renderer.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
                        
                        items.add(VideoItem(
                            videoId = videoId,
                            title = title,
                            thumbnailUrl = thumbnail,
                            uploaderName = channel,
                            isShort = false
                        ))
                    }
                }
            }
            
            items.take(count)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
