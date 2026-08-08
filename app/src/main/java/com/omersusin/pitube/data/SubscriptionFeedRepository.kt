package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class CachedVideo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String?,
    val thumbnailUrl: String?,
    val duration: Int,
    val viewCount: Long,
    val uploadDate: String,
    val timestamp: Long,
    val cachedAt: Long = System.currentTimeMillis()
)

class SubscriptionFeedRepository(private val context: Context) {
    private val cacheFile = File(context.filesDir, "subscription_feed_cache.json")
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _feed = MutableStateFlow<List<CachedVideo>>(emptyList())
    val feed: StateFlow<List<CachedVideo>> = _feed.asStateFlow()

    init {
        _feed.value = loadCache()
    }

    suspend fun fetchFeed(channelIds: List<String>): List<CachedVideo> = withContext(Dispatchers.IO) {
        val allEntries = mutableListOf<ChannelRssEntry>()
        
        for (channelId in channelIds) {
            try {
                val rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
                val request = Request.Builder().url(rssUrl).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val feed = ChannelRssParser.parse(body)
                        allEntries.addAll(feed.entries)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val videos = allEntries.map { entry ->
            CachedVideo(
                videoId = entry.videoId,
                title = entry.title,
                channelName = "",
                channelId = null,
                thumbnailUrl = entry.thumbnailUrl,
                duration = 0,
                viewCount = entry.viewCount,
                uploadDate = "",
                timestamp = entry.publishedAtMillis,
                cachedAt = System.currentTimeMillis()
            )
        }.sortedByDescending { it.timestamp }

        saveCache(videos)
        _feed.value = videos
        videos
    }

    private fun loadCache(): List<CachedVideo> {
        if (!cacheFile.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<CachedVideo>>() {}.type
            gson.fromJson(cacheFile.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCache(videos: List<CachedVideo>) {
        cacheFile.writeText(gson.toJson(videos))
    }

    fun clearCache() {
        cacheFile.delete()
        _feed.value = emptyList()
    }
}
