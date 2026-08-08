package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class LocalSub(val channelId: String, val name: String, val avatarUrl: String? = null)

object LocalSubs {
    private val gson = Gson()
    private fun file(context: Context) = File(context.filesDir, "local_subs.json")

    fun getAll(context: Context): List<LocalSub> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            val t = object : TypeToken<List<LocalSub>>() {}.type
            gson.fromJson(f.readText(), t) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun subscribe(context: Context, sub: LocalSub) {
        val list = getAll(context).toMutableList()
        if (list.none { it.channelId == sub.channelId }) {
            list.add(0, sub)
            file(context).writeText(gson.toJson(list))
        }
    }

    fun unsubscribe(context: Context, channelId: String) {
        val list = getAll(context).filterNot { it.channelId == channelId }
        file(context).writeText(gson.toJson(list))
    }

    fun isSubscribed(context: Context, channelId: String): Boolean =
        getAll(context).any { it.channelId == channelId }
}
