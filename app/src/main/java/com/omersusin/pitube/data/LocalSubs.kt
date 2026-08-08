package com.omersusin.pitube.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object LocalSubs {
    data class Sub(val channelId: String, val name: String, val avatar: String?)
    private val gson = Gson()

    private fun load(context: Context): MutableList<Sub> {
        val f = File(context.filesDir, "local_subs.json")
        if (!f.exists()) return mutableListOf()
        return try { gson.fromJson(f.readText(), object : TypeToken<MutableList<Sub>>() {}.type) ?: mutableListOf() } catch (e: Exception) { mutableListOf() }
    }
    fun all(context: Context) = load(context)
    fun add(context: Context, s: Sub) { val l = load(context); if (l.none { it.channelId == s.channelId }) { l.add(s); File(context.filesDir, "local_subs.json").writeText(gson.toJson(l)) } }
    fun remove(context: Context, id: String) { File(context.filesDir, "local_subs.json").writeText(gson.toJson(load(context).filterNot { it.channelId == id })) }

    fun import(context: Context, text: String): Int {
        var added = 0
        try {
            val arr = org.json.JSONArray(text)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url", "")
                val id = url.substringAfter("channel/").substringAfter("user/").trim('/')
                if (id.isNotBlank()) { add(context, Sub(id, o.optString("name", id), null)); added++ }
            }
        } catch (e: Exception) {
            if (text.contains("<opml")) {
                Regex("""xmlUrl="([^"]+channel/[^"]+)"""").findAll(text).forEach { m ->
                    val id = m.groupValues[1].substringAfter("channel/").trim('/')
                    add(context, Sub(id, id, null)); added++
                }
            } else {
                text.lines().forEach { line ->
                    val cols = line.split(",")
                    if (cols.size >= 3 && cols[2].trim().startsWith("UC")) { add(context, Sub(cols[2].trim(), cols[1].trim().replace("\"", ""), null)); added++ }
                }
            }
        }
        return added
    }

    fun exportNewPipe(context: Context): String {
        val arr = org.json.JSONArray()
        load(context).forEach { s ->
            arr.put(org.json.JSONObject().apply {
                put("url", "https://www.youtube.com/channel/${s.channelId}"); put("name", s.name); put("service_id", 0)
            })
        }
        return arr.toString(2)
    }
}
