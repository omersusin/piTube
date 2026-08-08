package com.omersusin.pitube.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object VideoEngagement {
    private val client = OkHttpClient()

    suspend fun subscribe(context: Context, channelId: String, subscribe: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getRawCookies(context)
            if (cookies.isBlank() || channelId.isBlank()) return@withContext false
            val endpoint = if (subscribe) "subscription/subscribe" else "subscription/unsubscribe"
            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20240101.00.00"}},"channelIds":["$channelId"]}"""
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/$endpoint")
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", cookies)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }
}
