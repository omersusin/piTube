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
    private const val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val CLIENT_VERSION = "2.20260114.08.00"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun baseRequest(url: String, cookies: String, authHeader: String?): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Cookie", cookies)
            .addHeader("User-Agent", UA)
            .addHeader("X-YouTube-Client-Name", "1")
            .addHeader("X-YouTube-Client-Version", CLIENT_VERSION)
            .addHeader("Origin", "https://www.youtube.com")
            .addHeader("X-Goog-AuthUser", "0")
        if (authHeader != null) builder.addHeader("Authorization", authHeader)
        return builder
    }

    suspend fun subscribe(context: Context, channelId: String, subscribe: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getRawCookies(context)
            if (cookies.isBlank() || channelId.isBlank()) return@withContext false
            val ep = if (subscribe) "subscription/subscribe" else "subscription/unsubscribe"
            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"$CLIENT_VERSION"}},"channelIds":["$channelId"]}"""
            val authHeader = KodaAuth.authHeader(cookies)
            val req = baseRequest("https://www.youtube.com/youtubei/v1/$ep?key=$API_KEY", cookies, authHeader)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            KodaAuth.refreshFromResponse(context, resp)
            resp.use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    suspend fun like(context: Context, videoId: String, like: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getRawCookies(context)
            if (cookies.isBlank() || videoId.isBlank()) return@withContext false
            val rating = if (like) "LIKE" else "INDIFFERENT"
            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"$CLIENT_VERSION"}},"target":{"videoId":"$videoId"},"rating":"$rating"}"""
            val authHeader = KodaAuth.authHeader(cookies)
            val req = baseRequest("https://www.youtube.com/youtubei/v1/like/like?key=$API_KEY", cookies, authHeader)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            KodaAuth.refreshFromResponse(context, resp)
            resp.use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    suspend fun sendFeedback(context: Context, token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getRawCookies(context)
            if (cookies.isBlank() || token.isBlank()) return@withContext false
            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"$CLIENT_VERSION"}},"feedbackTokens":["$token"]}"""
            val authHeader = KodaAuth.authHeader(cookies)
            val req = baseRequest("https://www.youtube.com/youtubei/v1/feedback?key=$API_KEY", cookies, authHeader)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            KodaAuth.refreshFromResponse(context, resp)
            resp.use { it.isSuccessful }
        } catch (e: Exception) { false }
    }
}
