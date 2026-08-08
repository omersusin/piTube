package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object HistoryManager {
    private const val FILE_NAME = "history.json"
    private val gson = Gson()

    fun getHistory(context: Context): MutableList<VideoItem> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<VideoItem>>() {}.type
            gson.fromJson(file.readText(), type) ?: mutableListOf()
        } catch (e: Exception) { mutableListOf() }
    }

    fun addToHistory(context: Context, video: VideoItem) {
        val history = getHistory(context)
        history.removeAll { it.url == video.url } // Remove duplicate if exists
        history.add(0, video) // Add to top
        if (history.size > 100) history.removeAt(history.size - 1) // Limit size
        File(context.filesDir, FILE_NAME).writeText(gson.toJson(history))
    }
}
