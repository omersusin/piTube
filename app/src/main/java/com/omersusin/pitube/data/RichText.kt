package com.omersusin.pitube.data

import org.json.JSONObject

data class RichLink(val text: String, val url: String?, val timestampMs: Long?, val hashtag: Boolean = false)

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
            val watchEp = nav?.optJSONObject("watchEndpoint")
            val rawTs: Long? = watchEp?.let {
                val v = it.optLong("startTimeSeconds", -1L)
                if (v >= 0) v else null
            }
            val tsMs: Long? = rawTs?.let { if (it > 0) it * 1000L else null }
            val isHashtag = nav?.optJSONObject("commandMetadata")?.optJSONObject("webCommandMetadata")?.optString("url")?.startsWith("/hashtag/") == true
            out.add(RichLink(text, url, tsMs, isHashtag))
        }
        return out
    }

    fun toPlainText(links: List<RichLink>): String = links.joinToString("") { it.text }

    fun parseFromRuns(runs: org.json.JSONArray?): List<RichLink> {
        if (runs == null) return emptyList()
        val out = mutableListOf<RichLink>()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val text = run.optString("text", "")
            val nav = run.optJSONObject("navigationEndpoint")
            val url = nav?.optJSONObject("urlEndpoint")?.optString("url")
                ?: nav?.optJSONObject("commandMetadata")?.optJSONObject("webCommandMetadata")?.optString("url")
            val watchEp = nav?.optJSONObject("watchEndpoint")
            val rawTs: Long? = watchEp?.let {
                val v = it.optLong("startTimeSeconds", -1L)
                if (v >= 0) v else null
            }
            val tsMs: Long? = rawTs?.let { if (it > 0) it * 1000L else null }
            val isHashtag = nav?.optJSONObject("commandMetadata")?.optJSONObject("webCommandMetadata")?.optString("url")?.startsWith("/hashtag/") == true
            out.add(RichLink(text, url, tsMs, isHashtag))
        }
        return out
    }
}
