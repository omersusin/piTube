package com.omersusin.pitube.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object VideoEngagement {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun subscribe(context: Context, channelId: String, subscribe: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getRawCookies(context)
            if (cookies.isBlank() || channelId.isBlank()) return@withContext false
            val ep = if (subscribe) "subscription/subscribe" else "subscription/unsubscribe"
            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20240101.00.00"}},"channelIds":["$channelId"]}"""
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/$ep")
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", cookies)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            KodaAuth.refreshFromResponse(context, resp)
            resp.isSuccessful
        } catch (e: Exception) { false }
    }

    suspend fun like(context: Context, videoId: String, like: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getRawCookies(context)
            if (cookies.isBlank() || videoId.isBlank()) return@withContext false
            val rating = when {
                like -> "LIKE"
                else -> "INDIFFERENT"
            }
            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20240101.00.00"}},"target":{"videoId":"$videoId"},"rating":"$rating"}"""
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/next")
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", cookies)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            KodaAuth.refreshFromResponse(context, resp)
            resp.isSuccessful
        } catch (e: Exception) { false }
    }

    suspend fun sendFeedback(context: Context, token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getRawCookies(context)
            if (cookies.isBlank() || token.isBlank()) return@withContext false
            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20240101.00.00"}},"feedbackTokens":["$token"]}"""
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/feedback")
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", cookies)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            KodaAuth.refreshFromResponse(context, resp)
            resp.isSuccessful
        } catch (e: Exception) { false }
    }
}
