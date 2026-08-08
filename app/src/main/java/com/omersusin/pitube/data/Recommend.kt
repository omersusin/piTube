package com.omersusin.pitube.data

import android.content.Context


import java.io.File

object Recommend {
    fun topChannels(context: Context, limit: Int = 5): List<String> {
        val halfLife = 14.0 * 24 * 3600_000
        val now = System.currentTimeMillis()
        val scores = mutableMapOf<String, Double>()
        StatsRepo.stats(context)
        val f = File(context.filesDir, "stats.json")
        if (f.exists()) {
            try {
                val plays = com.google.gson.Gson().fromJson(f.readText(), Array<StatsRepo.Play>::class.java)
                plays.forEach { p ->
                    val w = Math.pow(2.0, -(now - p.ts) / halfLife)
                    scores[p.channel] = (scores[p.channel] ?: 0.0) + w
                }
            } catch (e: Exception) { }
        }
        return scores.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }
}

