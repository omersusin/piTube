package com.omersusin.pitube.data
import android.content.Context
object SearchHistoryRepository {
    private const val P = "search_history"; private const val K = "history_list"
    fun getHistory(context: Context): List<String> { val s = context.getSharedPreferences(P,0).getString(K,"") ?: ""; return if (s.isEmpty()) emptyList() else s.split("|") }
    fun addQuery(context: Context, q: String) { if (q.isBlank()) return; val c = getHistory(context).toMutableList(); c.remove(q); c.add(0,q); context.getSharedPreferences(P,0).edit().putString(K, c.take(15).joinToString("|")).apply() }
    fun removeQuery(context: Context, q: String) { val c = getHistory(context).toMutableList(); c.remove(q); context.getSharedPreferences(P,0).edit().putString(K, c.joinToString("|")).apply() }
    fun clearHistory(context: Context) = context.getSharedPreferences(P,0).edit().remove(K).apply()
}
