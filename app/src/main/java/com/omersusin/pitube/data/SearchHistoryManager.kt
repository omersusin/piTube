package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object SearchHistoryManager {
    private const val FILE_NAME = "search_history.json"
    private val gson = Gson()

    fun getHistory(context: Context): MutableList<String> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<String>>() {}.type
            gson.fromJson(file.readText(), type) ?: mutableListOf()
        } catch (e: Exception) { mutableListOf() }
    }

    fun addToHistory(context: Context, query: String) {
        val history = getHistory(context)
        history.removeAll { it == query }
        history.add(0, query)
        if (history.size > 20) history.removeAt(history.size - 1)
        File(context.filesDir, FILE_NAME).writeText(gson.toJson(history))
    }

    fun removeFromHistory(context: Context, query: String) {
        val history = getHistory(context)
        history.removeAll { it == query }
        File(context.filesDir, FILE_NAME).writeText(gson.toJson(history))
    }

    fun clearHistory(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
