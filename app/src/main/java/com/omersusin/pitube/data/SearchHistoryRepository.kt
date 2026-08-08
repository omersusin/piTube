package com.omersusin.pitube.data

import android.content.Context

object SearchHistoryRepository {
    private const val MAX_ITEMS = 20
    
    fun addQuery(context: Context, query: String) {
        if (query.isBlank()) return
        val history = getHistory(context).toMutableList()
        history.remove(query)
        history.add(0, query)
        if (history.size > MAX_ITEMS) {
            history.removeAt(history.size - 1)
        }
        context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("queries", history.toSet())
            .putString("order", history.joinToString("|||"))
            .apply()
    }
    
    fun getHistory(context: Context): List<String> {
        val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
        val order = prefs.getString("order", "") ?: return emptyList()
        return order.split("|||").filter { it.isNotBlank() }
    }
    
    fun removeQuery(context: Context, query: String) {
        val history = getHistory(context).toMutableList()
        history.remove(query)
        context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("queries", history.toSet())
            .putString("order", history.joinToString("|||"))
            .apply()
    }
    
    fun clearHistory(context: Context) {
        context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
