package com.omersusin.pitube.data

import android.content.Context

object SearchHistoryRepository {
    private const val PREFS_NAME = "search_history"
    private const val KEY_HISTORY = "history_list"

    fun getHistory(context: Context): List<String> {
        val s = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_HISTORY, "") ?: ""
        return if (s.isEmpty()) emptyList() else s.split("|")
    }
    fun addQuery(context: Context, query: String) {
        if (query.isBlank()) return
        val current = getHistory(context).toMutableList()
        current.remove(query); current.add(0, query)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_HISTORY, current.take(15).joinToString("|")).apply()
    }
    fun removeQuery(context: Context, query: String) {
        val current = getHistory(context).toMutableList(); current.remove(query)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_HISTORY, current.joinToString("|")).apply()
    }
    fun clearHistory(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY_HISTORY).apply()
}
