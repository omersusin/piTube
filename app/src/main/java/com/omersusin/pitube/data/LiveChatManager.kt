package com.omersusin.pitube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object LiveChatManager {
    private val client = OkHttpClient.Builder().readTimeout(15, TimeUnit.SECONDS).build()
    private const val UA = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L) gzip"
    private const val KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

    private fun contextJson(): JSONObject = JSONObject().apply {
        put("context", JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "ANDROID_VR"); put("clientVersion", "1.65.10"); put("androidSdkVersion", 32)
            })
        })
    }

    private fun post(url: String, body: JSONObject): JSONObject? = try {
        val req = Request.Builder().url(url)
            .addHeader("Content-Type", "application/json").addHeader("User-Agent", UA)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        JSONObject(client.newCall(req).execute().body?.string() ?: "{}")
    } catch (e: Exception) { null }

    suspend fun getInitialContinuation(videoId: String): String? = withContext(Dispatchers.IO) {
        val json = post("https://www.youtube.com/youtubei/v1/next?key=$KEY", contextJson().put("videoId", videoId)) ?: return@withContext null
        findToken(json)
    }

    suspend fun poll(token: String): Pair<List<ChatMessage>, String?> = withContext(Dispatchers.IO) {
        val json = post("https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?key=$KEY", contextJson().put("continuation", token)) ?: return@withContext emptyList<ChatMessage>() to null
        val msgs = mutableListOf<ChatMessage>()
        val cont = json.optJSONObject("continuationContents")?.optJSONObject("liveChatContinuation")
        val actions = cont?.optJSONArray("actions")
        if (actions != null) for (i in 0 until actions.length()) {
            val renderer = actions.optJSONObject(i)?.optJSONObject("addChatItemAction")?.optJSONObject("item")?.optJSONObject("liveChatTextMessageRenderer") ?: continue
            val author = renderer.optJSONObject("authorName")?.optString("simpleText") ?: ""
            val runs = renderer.optJSONObject("message")?.optJSONArray("runs")
            val text = StringBuilder()
            if (runs != null) for (j in 0 until runs.length()) {
                val r = runs.optJSONObject(j) ?: continue
                text.append(r.optString("text", ""))
            }
            if (text.isNotBlank()) msgs.add(ChatMessage(author, text.toString(), ""))
        }
        var next: String? = null
        val conts = cont?.optJSONArray("continuations")
        if (conts != null && conts.length() > 0) {
            val c0 = conts.optJSONObject(0)
            next = c0?.optJSONObject("timedContinuationData")?.optString("continuation")
                ?: c0?.optJSONObject("invalidationContinuationData")?.optString("continuation")
                ?: c0?.optJSONObject("reloadContinuationData")?.optString("continuation")
        }
        msgs to next
    }

    private fun findToken(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                val lr = node.optJSONObject("liveChatRenderer")
                if (lr != null) {
                    val conts = lr.optJSONArray("continuations")
                    if (conts != null && conts.length() > 0) {
                        val t = conts.optJSONObject(0)?.optJSONObject("reloadContinuationData")?.optString("continuation")
                        if (!t.isNullOrBlank()) return t
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) { findToken(node.opt(keys.next()))?.let { return it } }
            }
            is JSONArray -> { for (i in 0 until node.length()) { findToken(node.opt(i))?.let { return it } } }
        }
        return null
    }
}
