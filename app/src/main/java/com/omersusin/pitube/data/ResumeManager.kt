package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object ResumeManager {
    private const val FILE_NAME = "resume_positions.json"
    private val gson = Gson()

    fun getResumePosition(context: Context, videoId: String): Long {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return 0L
        return try {
            val type = object : TypeToken<MutableMap<String, Long>>() {}.type
            val map: MutableMap<String, Long> = gson.fromJson(file.readText(), type) ?: mutableMapOf()
            map[videoId] ?: 0L
        } catch (e: Exception) { 0L }
    }

    fun saveResumePosition(context: Context, videoId: String, position: Long) {
        val file = File(context.filesDir, FILE_NAME)
        val map = if (file.exists()) {
            try {
                val type = object : TypeToken<MutableMap<String, Long>>() {}.type
                gson.fromJson<MutableMap<String, Long>>(file.readText(), type) ?: mutableMapOf()
            } catch (e: Exception) { mutableMapOf() }
        } else mutableMapOf()
        map[videoId] = position
        file.writeText(gson.toJson(map))
    }
}
