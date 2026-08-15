package com.omersusin.pitube.recognition

import com.google.gson.JsonParser
import com.omersusin.pitube.BuildConfig
import com.omersusin.pitube.data.local.RecognitionFailureType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * AudD recognition provider. Request shape ported from Audile's
 * `AuddRecognitionService` (GPL-3.0): a multipart POST to `api.audd.io` with
 * the `api_token` and the audio file. The token is injected at build time from
 * `local.properties` (`AUDD_TOKEN`), exactly like Audile — no user-facing
 * credential field. Without a configured token the provider reports
 * [RecognitionFailureType.OTHER] with a clear message.
 */
object AuddRecognizer {
    private const val ENDPOINT = "https://api.audd.io/"

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    suspend fun recognize(wavBytes: ByteArray): TrackMatch = withContext(Dispatchers.IO) {
        val token = BuildConfig.AUDD_TOKEN
        if (token.isBlank()) {
            throw RecognitionException(
                RecognitionFailureType.OTHER,
                "AudD is not configured (missing AUDD_TOKEN in local.properties)",
            )
        }

        val body =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("api_token", token)
                .addFormDataPart("return", "lyrics,spotify,apple_music,deezer,napster,musicbrainz,isrc")
                .addFormDataPart("file", "sample.wav", wavBytes.toRequestBody("audio/wav".toMediaType()))
                .build()

        val request =
            Request.Builder()
                .url(ENDPOINT)
                .post(body)
                .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RecognitionException(
                        RecognitionFailureType.OTHER,
                        "AudD failed (HTTP ${response.code})",
                    )
                }
                parseResponse(response.body?.string().orEmpty())
                    ?: throw RecognitionException(RecognitionFailureType.NO_MATCH, "No match found")
            }
        } catch (e: RecognitionException) {
            throw e
        } catch (e: java.io.IOException) {
            throw RecognitionException(RecognitionFailureType.BAD_CONNECTION, "Network unavailable")
        }
    }

    private fun parseResponse(body: String): TrackMatch? {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
        val status = root.get("status")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        if (status != "success") return null
        val result = root.getAsJsonObject("result") ?: return null
        val title = result.get("title")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val artist = result.get("artist")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        if (title.isBlank() && artist.isBlank()) return null

        return TrackMatch(
            trackId = "audd:${title}|${artist}",
            title = title,
            artist = artist,
            album = result.get("album")?.takeIf { !it.isJsonNull }?.asString,
            releaseDate = result.get("release_date")?.takeIf { !it.isJsonNull }?.asString,
            isrc = result.get("isrc")?.takeIf { !it.isJsonNull }?.asString,
            sourceProvider = "AudD",
        )
    }
}