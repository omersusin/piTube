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
            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20260114.08.00"}},"channelIds":["$channelId"]}"""
            val sapisidhash = KodaAuth.authHeader(cookies)
            val reqBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/$ep?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8")
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", cookies)
                .addHeader("X-YouTube-Client-Name", "1")
                .addHeader("X-YouTube-Client-Version", "2.20260114.08.00")
                .addHeader("Origin", "https://www.youtube.com")
                .addHeader("X-Goog-AuthUser", "0")
                .post(body.toRequestBody("application/json".toMediaType()))
            if (sapisidhash != null) reqBuilder.addHeader("Authorization", sapisidhash)
            val req = reqBuilder.build()
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
            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20260114.08.00"}},"target":{"videoId":"$videoId"},"rating":"$rating"}"""
            val sapisidhash = KodaAuth.authHeader(cookies)
            val reqBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/like/like?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8")
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", cookies)
                .addHeader("X-YouTube-Client-Name", "1")
                .addHeader("X-YouTube-Client-Version", "2.20260114.08.00")
                .addHeader("Origin", "https://www.youtube.com")
                .addHeader("X-Goog-AuthUser", "0")
                .post(body.toRequestBody("application/json".toMediaType()))
            if (sapisidhash != null) reqBuilder.addHeader("Authorization", sapisidhash)
            val req = reqBuilder.build()
            val resp = client.newCall(req).execute()
            KodaAuth.refreshFromResponse(context, resp)
            resp.isSuccessful
        } catch (e: Exception) { false }
    }

    suspend fun sendFeedback(context: Context, token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cookies = AuthManager.getRawCookies(context)
            if (cookies.isBlank() || token.isBlank()) return@withContext false
            val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20260114.08.00"}},"feedbackTokens":["$token"]}"""
            val sapisidhash = KodaAuth.authHeader(cookies)
            val reqBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/feedback?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8")
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", cookies)
                .addHeader("X-YouTube-Client-Name", "1")
                .addHeader("X-YouTube-Client-Version", "2.20260114.08.00")
                .addHeader("Origin", "https://www.youtube.com")
                .addHeader("X-Goog-AuthUser", "0")
                .post(body.toRequestBody("application/json".toMediaType()))
            if (sapisidhash != null) reqBuilder.addHeader("Authorization", sapisidhash)
            val req = reqBuilder.build()
            val resp = client.newCall(req).execute()
            KodaAuth.refreshFromResponse(context, resp)
            resp.isSuccessful
        } catch (e: Exception) { false }
    }
}
