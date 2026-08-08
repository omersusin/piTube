package com.omersusin.pitube.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Calendar

data class PlayHistoryEntry(val videoId: String, val title: String, val channelName: String, val timestamp: Long, val duration: Long, val thumbnailUrl: String? = null)
data class VideoStats(val videoId: String, val title: String, val channelName: String, val thumbnailUrl: String?, val playCount: Int, val totalPlayTime: Long)
data class ChannelStats(val name: String, val playCount: Int, val videoCount: Int)
data class GlobalStats(val totalPlays: Int = 0, val totalPlayTimeSeconds: Long = 0, val topVideos: List<VideoStats> = emptyList(), val topChannels: List<ChannelStats> = emptyList(), val uniqueChannels: Int = 0, val uniqueVideos: Int = 0, val currentStreakDays: Int = 0, val longestStreakDays: Int = 0)

class StatsRepository(private val context: Context) {
    private val file = File(context.filesDir, "play_history.json")
    private val gson = com.google.gson.Gson()
    private val MAX = 5000
    private val mutex = Mutex()

    suspend fun addPlayEvent(video: VideoItem, durationMs: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val h = loadHistory().toMutableList()
                val last = h.firstOrNull()
                if (last != null && last.videoId == video.videoId && (System.currentTimeMillis() - last.timestamp) < 10000L) return@withLock
                h.add(0, PlayHistoryEntry(video.videoId, video.title, video.uploaderName, System.currentTimeMillis(), durationMs, video.thumbnailUrl))
                file.writeText(gson.toJson(h.take(MAX)))
            } catch (e: Exception) {}
        }
    }

    suspend fun getGlobalStats(): GlobalStats = withContext(Dispatchers.Default) {
        val h = loadHistory()
        if (h.isEmpty()) return@withContext GlobalStats()
        val vs = h.groupBy { it.videoId }.map { (id, es) ->
            val f = es.first()
            VideoStats(id, f.title, f.channelName, f.thumbnailUrl, es.size, es.sumOf { it.duration })
        }.sortedByDescending { it.playCount }
        val cs = h.groupBy { it.channelName }.map { (n, es) ->
            ChannelStats(n, es.size, es.distinctBy { it.videoId }.size)
        }.sortedByDescending { it.playCount }
        val days = h.map { day(it.timestamp) }.toSortedSet()
        var longest = 0; var run = 0; var prev: Long? = null
        for (d in days) {
            run = if (prev != null && d == prev + 1) run + 1 else 1
            if (run > longest) longest = run
            prev = d
        }
        val today = day(System.currentTimeMillis())
        var cur = 0; var c = if (today in days) today else today - 1
        while (c in days) { cur++; c-- }
        GlobalStats(h.size, h.sumOf { it.duration / 1000 }, vs.take(10), cs.take(10), cs.size, vs.size, cur, longest)
    }

    fun loadHistory(): List<PlayHistoryEntry> {
        if (!file.exists()) return emptyList()
        return try {
            gson.fromJson(file.readText(), object : com.google.gson.reflect.TypeToken<List<PlayHistoryEntry>>() {}.type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun day(ts: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ts
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis / 86_400_000L
    }
}
