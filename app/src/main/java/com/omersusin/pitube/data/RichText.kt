package com.omersusin.pitube.data

import org.json.JSONObject

data class RichLink(val text: String, val url: String?, val timestamp: Long?, val hashtag: Boolean = false)

object RichTextParser {
    fun parse(node: JSONObject): List<RichLink> {
        val runs = node.optJSONArray("runs") ?: return emptyList()
        val out = mutableListOf<RichLink>()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val text = run.optString("text", "")
            val nav = run.optJSONObject("navigationEndpoint")
            val url = nav?.optJSONObject("urlEndpoint")?.optString("url")
                ?: nav?.optJSONObject("commandMetadata")?.optJSONObject("webCommandMetadata")?.optString("url")
            val ts = nav?.optJSONObject("watchEndpoint")?.optLong("startTimeSeconds", 0L)
            val hashtag = nav?.optJSONObject("commandMetadata")?.optJSONObject("webCommandMetadata")?.optString("url")?.startsWith("/hashtag/") == true
            out.add(RichLink(text, url, if (ts != 0L) ts * 1000 else null, hashtag))
        }
        return out
    }
    
    fun toPlainText(links: List<RichLink>): String = links.joinToString("") { it.text }
}
