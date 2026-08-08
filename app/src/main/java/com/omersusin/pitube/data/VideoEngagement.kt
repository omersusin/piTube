package com.omersusin.pitube.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest

object VideoEngagement {
    private val client = OkHttpClient()
    private const val ORIGIN = "https://www.youtube.com"
    
    private fun sha1(s: String): String = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun tsHash(v: String): String { val ts = System.currentTimeMillis() / 1000; return "${ts}_${sha1("$ts $v $ORIGIN")}" }
    private fun sapisidOf(c: Map<String, String>): String? = c["SAPISID"] ?: c["__Secure-1PAPISID"] ?: c["APISID"] ?: c["__Secure-3PAPISID"]
    
    suspend fun like(context: Context, videoId: String, targetLikeStatus: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getCookies(context)
            if (cookies.isEmpty()) return@withContext false
            val sapisid = sapisidOf(cookies) ?: return@withContext false
            val body = JSONObject().apply {
                put("target", JSONObject().apply { put("videoId", videoId) })
                put("context", JSONObject().apply { put("client", JSONObject().apply {
                    put("clientName", "WEB"); put("clientVersion", "2.20260114.08.00")
                }) })
            }
            val req = Request.Builder().url("https://www.youtube.com/youtubei/v1/like/like?prettyPrint=false")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "SAPISIDHASH ${tsHash(sapisid)}")
                .addHeader("Origin", ORIGIN)
                .addHeader("Cookie", AuthManager.getRawCookies(context))
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }
    
    suspend fun dislike(context: Context, videoId: String): Boolean = like(context, videoId, false)
    
    suspend fun subscribe(context: Context, channelId: String, subscribe: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getCookies(context)
            if (cookies.isEmpty()) return@withContext false
            val sapisid = sapisidOf(cookies) ?: return@withContext false
            val endpoint = if (subscribe) "subscription/subscribe" else "subscription/unsubscribe"
            val body = JSONObject().apply {
                put("channelIds", listOf(channelId))
                put("params", if (subscribe) "EgIIAhgA" else "CgIIAhgA")
                put("context", JSONObject().apply { put("client", JSONObject().apply {
                    put("clientName", "WEB"); put("clientVersion", "2.20260114.08.00")
                }) })
            }
            val req = Request.Builder().url("https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "SAPISIDHASH ${tsHash(sapisid)}")
                .addHeader("Origin", ORIGIN)
                .addHeader("Cookie", AuthManager.getRawCookies(context))
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }
}
