package com.omersusin.pitube.data

import android.content.Context

object SearchHistoryRepository {
    private const val PREFS_NAME = "search_history"
    private const val KEY_HISTORY = "history_list"
    private const val MAX_HISTORY = 20

    fun getHistory(context: Context): List<String> {
        val s = context.getSharedPreferences(PREFS_NAME, 0).getString(KEY_HISTORY, "") ?: ""
        return if (s.isEmpty()) emptyList() else s.split("|")
    }

    fun addQuery(context: Context, q: String) {
        if (q.isBlank()) return
        val c = getHistory(context).toMutableList()
        c.remove(q)
        c.add(0, q)
        context.getSharedPreferences(PREFS_NAME, 0).edit().putString(KEY_HISTORY, c.take(MAX_HISTORY).joinToString("|")).apply()
    }

    fun removeQuery(context: Context, q: String) {
        val c = getHistory(context).toMutableList()
        c.remove(q)
        context.getSharedPreferences(PREFS_NAME, 0).edit().putString(KEY_HISTORY, c.joinToString("|")).apply()
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREFS_NAME, 0).edit().remove(KEY_HISTORY).apply()
    }
}
