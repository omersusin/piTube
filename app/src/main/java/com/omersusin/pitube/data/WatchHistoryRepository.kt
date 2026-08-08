package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.*

object WatchHistoryRepository {
    private const val HISTORY_FILE = "watch_history_v2.json"
    private const val MAX_HISTORY = 200
    private val gson = Gson()
    
    data class WatchEntry(
        val videoId: String,
        val title: String,
        val channelName: String,
        val thumbnailUrl: String,
        val watchedAt: Long,
        val watchDurationMs: Long,
        val totalDurationMs: Long,
        val completed: Boolean
    ) {
        fun completionPercentage(): Double {
            if (totalDurationMs <= 0) return 0.0
            return (watchDurationMs.toDouble() / totalDurationMs) * 100.0
        }
    }
    
    fun addToHistory(context: Context, video: VideoItem, watchDurationMs: Long, totalDurationMs: Long) {
        val history = getHistory(context).toMutableList()
        val completed = totalDurationMs > 0 && watchDurationMs >= totalDurationMs * 0.9
        
        val entry = WatchEntry(
            videoId = video.videoId,
            title = video.title,
            channelName = video.uploaderName,
            thumbnailUrl = video.thumbnailUrl,
            watchedAt = System.currentTimeMillis(),
            watchDurationMs = watchDurationMs,
            totalDurationMs = totalDurationMs,
            completed = completed
        )
        
        history.removeIf { it.videoId == video.videoId }
        history.add(0, entry)
        
        if (history.size > MAX_HISTORY) {
            history.removeAt(history.size - 1)
        }
        
        saveHistory(context, history)
    }
    
    fun getHistory(context: Context): List<WatchEntry> {
        val file = File(context.filesDir, HISTORY_FILE)
        if (!file.exists()) return emptyList()
        
        return try {
            val type = object : TypeToken<List<WatchEntry>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getRecentWatches(context: Context, limit: Int = 20): List<WatchEntry> {
        return getHistory(context).take(limit)
    }
    
    fun getMostWatched(context: Context, limit: Int = 10): List<Pair<String, Int>> {
        val counts = mutableMapOf<String, Int>()
        getHistory(context).forEach { entry ->
            counts[entry.channelName] = (counts[entry.channelName] ?: 0) + 1
        }
        return counts.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }
    }
    
    fun clearHistory(context: Context) {
        File(context.filesDir, HISTORY_FILE).delete()
    }
    
    private fun saveHistory(context: Context, history: List<WatchEntry>) {
        File(context.filesDir, HISTORY_FILE).writeText(gson.toJson(history))
    }
}
