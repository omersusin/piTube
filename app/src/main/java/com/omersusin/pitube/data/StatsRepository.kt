package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class PlayStat(
    val videoId: String,
    val channelName: String,
    val playedAt: Long
)

data class StatsData(
    val totalPlays: Int,
    val dayStreak: Int,
    val topChannels: List<Pair<String, Int>>,
    val averageWatchTimeMs: Long,
    val completionRate: Double
)

object StatsRepository {
    private const val STATS_FILE = "stats_v2.json"
    private val gson = Gson()

    private fun getStatsFile(context: Context) = File(context.filesDir, STATS_FILE)

    fun getStats(context: Context): StatsData {
        val file = getStatsFile(context)
        if (!file.exists()) return StatsData(0, 0, emptyList(), 0L, 0.0)
        
        return try {
            val json = file.readText()
            gson.fromJson(json, StatsData::class.java) ?: StatsData(0, 0, emptyList(), 0L, 0.0)
        } catch (e: Exception) {
            e.printStackTrace()
            StatsData(0, 0, emptyList(), 0L, 0.0)
        }
    }

    fun recordPlay(context: Context, videoId: String, channelName: String) {
        val stats = getStats(context)
        val newStats = stats.copy(
            totalPlays = stats.totalPlays + 1,
            dayStreak = calculateStreak(context) + 1
        )
        
        val file = getStatsFile(context)
        file.writeText(gson.toJson(newStats))
    }

    private fun calculateStreak(context: Context): Int {
        // Simple streak calculation - just return current for now
        return getStats(context).dayStreak
    }
}
