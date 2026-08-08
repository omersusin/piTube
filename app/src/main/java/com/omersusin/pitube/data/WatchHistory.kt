package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class WatchHistoryItem(
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val watchedAt: Long
)

fun addWatchHistory(context: Context, videoId: String, title: String, uploader: String, thumbnail: String?) {
    val prefs = context.getSharedPreferences("watch_history", Context.MODE_PRIVATE)
    val gson = Gson()
    val type = object : TypeToken<MutableList<WatchHistoryItem>>() {}.type
    val history: MutableList<WatchHistoryItem> = try {
        gson.fromJson(prefs.getString("items", "[]"), type) ?: mutableListOf()
    } catch (e: Exception) { mutableListOf() }
    
    history.removeAll { it.videoId == videoId }
    history.add(0, WatchHistoryItem(
        videoId = videoId,
        title = title,
        uploader = uploader,
        thumbnailUrl = thumbnail,
        watchedAt = System.currentTimeMillis()
    ))
    
    if (history.size > 100) {
        history.removeAt(history.size - 1)
    }
    
    prefs.edit().putString("items", gson.toJson(history)).apply()
}

fun clearWatchHistory(context: Context) {
    context.getSharedPreferences("watch_history", Context.MODE_PRIVATE).edit().remove("items").apply()
}
