package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

data class PlayHistoryEntry(
    val videoId: String,
    val title: String,
    val channelName: String,
    val timestamp: Long,
    val duration: Long,
    val thumbnailUrl: String? = null
)

data class VideoStats(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
    val playCount: Int,
    val totalPlayTime: Long
)

data class ChannelStats(
    val name: String,
    val playCount: Int,
    val videoCount: Int
)

data class GlobalStats(
    val totalPlays: Int = 0,
    val totalPlayTimeSeconds: Long = 0,
    val topVideos: List<VideoStats> = emptyList(),
    val topChannels: List<ChannelStats> = emptyList(),
    val uniqueChannels: Int = 0,
    val uniqueVideos: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0
)

class StatsRepository(private val context: Context) {
    private val historyFile = File(context.filesDir, "play_history.json")
    private val gson = Gson()
    private val MAX_HISTORY_ENTRIES = 5000

    suspend fun addPlayEvent(video: VideoItem, durationMs: Long) = withContext(Dispatchers.IO) {
        try {
            val history = loadHistory().toMutableList()
            val lastEntry = history.firstOrNull()
            if (lastEntry?.videoId == video.id && (System.currentTimeMillis() - lastEntry.timestamp) < 10000L) {
                return@withContext
            }

            val entry = PlayHistoryEntry(
                videoId = video.id,
                title = video.title,
                channelName = video.uploaderName,
                timestamp = System.currentTimeMillis(),
                duration = durationMs,
                thumbnailUrl = video.thumbnailUrl
            )
            history.add(0, entry)
            val trimmedHistory = if (history.size > MAX_HISTORY_ENTRIES) {
                history.take(MAX_HISTORY_ENTRIES)
            } else history
            historyFile.writeText(gson.toJson(trimmedHistory))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getGlobalStats(): GlobalStats = withContext(Dispatchers.Default) {
        val history = loadHistory()
        if (history.isEmpty()) return@withContext GlobalStats()
        
        val totalPlayTime = history.sumOf { it.duration / 1000 }
        val totalPlays = history.size
        
        val videoStats = history.groupBy { it.videoId }.map { (id, entries) ->
            val first = entries.first()
            VideoStats(
                videoId = id,
                title = first.title,
                channelName = first.channelName,
                thumbnailUrl = first.thumbnailUrl,
                playCount = entries.size,
                totalPlayTime = entries.sumOf { it.duration }
            )
        }.sortedByDescending { it.playCount }
        
        val channelStats = history.groupBy { it.channelName }.map { (name, entries) ->
            ChannelStats(
                name = name,
                playCount = entries.size,
                videoCount = entries.distinctBy { it.videoId }.size
            )
        }.sortedByDescending { it.playCount }
        
        val playDays = history.map { localDayOf(it.timestamp) }.toSortedSet()
        var longestStreak = 0
        var run = 0
        var prevDay: Long? = null
        for (day in playDays) {
            run = if (prevDay != null && day == prevDay + 1) run + 1 else 1
            if (run > longestStreak) longestStreak = run
            prevDay = day
        }
        
        val today = localDayOf(System.currentTimeMillis())
        var currentStreak = 0
        var cursor = if (today in playDays) today else today - 1
        while (cursor in playDays) {
            currentStreak++
            cursor--
        }

        GlobalStats(
            totalPlays = totalPlays,
            totalPlayTimeSeconds = totalPlayTime,
            topVideos = videoStats.take(10),
            topChannels = channelStats.take(10),
            uniqueChannels = channelStats.size,
            uniqueVideos = videoStats.size,
            currentStreakDays = currentStreak,
            longestStreakDays = longestStreak
        )
    }

    private fun loadHistory(): List<PlayHistoryEntry> {
        if (!historyFile.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<PlayHistoryEntry>>() {}.type
            gson.fromJson(historyFile.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun localDayOf(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 86_400_000L
    }
}
