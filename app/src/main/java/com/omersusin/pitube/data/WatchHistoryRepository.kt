package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class WatchHistoryEntry(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val watchedAt: Long,
    val watchDurationMs: Long,
    val totalDurationMs: Long,
    val completed: Boolean
)

object WatchHistoryRepository {
    private const val MAX_ENTRIES = 200
    private const val FILE_NAME = "watch_history_v2.json"
    private val gson = Gson()

    private fun getHistoryFile(context: Context) = File(context.filesDir, FILE_NAME)

    fun getHistory(context: Context): List<WatchHistoryEntry> {
        val file = getHistoryFile(context)
        if (!file.exists()) return emptyList()
        
        return try {
            val json = file.readText()
            val type = object : TypeToken<List<WatchHistoryEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getRecentWatches(context: Context, limit: Int = 5): List<WatchHistoryEntry> {
        return getHistory(context).take(limit)
    }

    fun addEntry(context: Context, entry: WatchHistoryEntry) {
        val history = getHistory(context).toMutableList()
        history.removeAll { it.videoId == entry.videoId }
        history.add(0, entry)
        
        if (history.size > MAX_ENTRIES) {
            history.removeAt(history.size - 1)
        }
        
        val file = getHistoryFile(context)
        file.writeText(gson.toJson(history))
    }
}

fun WatchHistoryEntry.completionPercentage(): Int {
    return if (totalDurationMs > 0) ((watchDurationMs.toFloat() / totalDurationMs) * 100).toInt() else 0
}
