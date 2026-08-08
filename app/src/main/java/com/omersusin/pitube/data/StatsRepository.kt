package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.TimeUnit

object StatsRepository {
    data class Play(val videoId: String, val channel: String, val ts: Long)
    data class Stats(
        val totalPlays: Int,
        val streak: Int,
        val topChannels: List<Pair<String, Int>>,
        val avgWatchTime: Long,
        val completedVideos: Int
    )
    
    private const val STATS_FILE = "stats.json"
    private val gson = Gson()
    
    private fun load(context: Context): MutableList<Play> {
        val f = File(context.filesDir, STATS_FILE)
        if (!f.exists()) return mutableListOf()
        return try { 
            gson.fromJson(f.readText(), object : TypeToken<MutableList<Play>>() {}.type) ?: mutableListOf() 
        } catch (e: Exception) { 
            mutableListOf() 
        }
    }
    
    fun record(context: Context, videoId: String, channel: String) {
        val l = load(context)
        if (l.none { it.videoId == videoId && System.currentTimeMillis() - it.ts < 60_000 }) {
            l.add(0, Play(videoId, channel, System.currentTimeMillis()))
            if (l.size > 500) l.removeAt(l.size - 1)
            File(context.filesDir, STATS_FILE).writeText(gson.toJson(l))
        }
    }
    
    fun stats(context: Context): Stats {
        val l = load(context)
        val byChannel = l.groupBy { it.channel }.mapValues { it.value.size }.entries.sortedByDescending { it.value }.take(5)
        val days = l.map { TimeUnit.MILLISECONDS.toDays(it.ts) }.distinct().sortedDescending()
        
        var streak = 0
        var expect = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        for (d in days) { 
            if (d == expect) { 
                streak++
                expect-- 
            } else if (d < expect) {
                break
            }
        }
        
        val history = WatchHistoryRepository.getHistory(context)
        val avgWatchTime = if (history.isNotEmpty()) {
            history.map { it.watchDurationMs }.average().toLong()
        } else 0L
        
        val completedVideos = history.count { it.completed }
        
        return Stats(l.size, streak, byChannel.map { it.key to it.value }, avgWatchTime, completedVideos)
    }
    
    fun clearStats(context: Context) {
        File(context.filesDir, STATS_FILE).delete()
    }
}
