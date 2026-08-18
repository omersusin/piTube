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
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * ACRCloud recognition provider. Request + HMAC-SHA1 signing ported from
 * Audile's `AcrCloudRecognitionService` (GPL-3.0): a multipart POST to
 * `{host}/v1/identify` carrying `timestamp`, `access_key`, `signature_version`,
 * `signature`, `data_type`, `sample_bytes` and the audio `sample`.
 *
 * Credentials are injected at build time from `local.properties`
 * (ACR_CLOUD_HOST / ACR_CLOUD_ACCESS_KEY / ACR_CLOUD_ACCESS_SECRET), exactly
 * like Audile — no user-facing credential field.
 */
object AcrCloudRecognizer {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    suspend fun recognize(wavBytes: ByteArray): TrackMatch = withContext(Dispatchers.IO) {
        val host = BuildConfig.ACR_CLOUD_HOST
        val accessKey = BuildConfig.ACR_CLOUD_ACCESS_KEY
        val accessSecret = BuildConfig.ACR_CLOUD_ACCESS_SECRET
        if (host.isBlank() || accessKey.isBlank() || accessSecret.isBlank()) {
            throw RecognitionException(
                RecognitionFailureType.OTHER,
                "ACRCloud is not configured (missing ACR_CLOUD_* in local.properties)",
            )
        }

        val timestamp = System.currentTimeMillis() / 1000
        val signature =
            hmacSha1(
                key = accessSecret,
                data = "POST\n/v1/identify\n$accessKey\naudio\n1\n$timestamp",
            )

        val body =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("timestamp", timestamp.toString())
                .addFormDataPart("access_key", accessKey)
                .addFormDataPart("signature_version", "1")
                .addFormDataPart("signature", signature)
                .addFormDataPart("data_type", "audio")
                .addFormDataPart("sample_bytes", wavBytes.size.toString())
                .addFormDataPart("sample", "sample.wav", wavBytes.toRequestBody("audio/wav".toMediaType()))
                .build()

        val endpoint = if (host.startsWith("http")) host.trimEnd('/') else "https://$host"

        val request =
            Request.Builder()
                .url("$endpoint/v1/identify")
                .post(body)
                .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RecognitionException(
                        RecognitionFailureType.OTHER,
                        "ACRCloud failed (HTTP ${response.code})",
                    )
                }
                parseResponse(response.body.string())
            }
        } catch (e: RecognitionException) {
            throw e
        } catch (e: java.io.IOException) {
            throw RecognitionException(RecognitionFailureType.BAD_CONNECTION, "Network unavailable")
        }
    }

    private fun hmacSha1(
        key: String,
        data: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)))
    }

    /**
     * ACRCloud status-code mapping (Audile): 0 = success, 1001 = no match,
     * 2000/2004 = bad recording, 3001/3014 = auth, 3003/3015 = usage limit.
     */
    private fun parseResponse(body: String): TrackMatch {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
            ?: throw RecognitionException(RecognitionFailureType.OTHER, "ACRCloud: unparseable response")
        val status = root.getAsJsonObject("status")
        val code = status?.get("code")?.takeIf { !it.isJsonNull }?.asInt ?: -1

        when (code) {
            0 -> Unit
            1001 -> throw RecognitionException(RecognitionFailureType.NO_MATCH, "No match found")
            2000, 2004 -> throw RecognitionException(RecognitionFailureType.OTHER, "Recording was too noisy or too short")
            3001, 3014 -> throw RecognitionException(RecognitionFailureType.OTHER, "ACRCloud authentication failed")
            3003, 3015 -> throw RecognitionException(RecognitionFailureType.OTHER, "ACRCloud usage limit reached")
            else -> throw RecognitionException(RecognitionFailureType.OTHER, "ACRCloud error $code")
        }

        val metadata = root.getAsJsonObject("metadata") ?: root
        val music = metadata.getAsJsonArray("music")?.firstOrNull()?.asJsonObject
            ?: throw RecognitionException(RecognitionFailureType.NO_MATCH, "No match found")
        val title = music.get("title")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val artistObj = music.getAsJsonArray("artists")?.firstOrNull()?.asJsonObject
        val artist = artistObj?.get("name")?.takeIf { !it.isJsonNull }?.asString.orEmpty()

        return TrackMatch(
            trackId = music.get("acrid")?.takeIf { !it.isJsonNull }?.asString ?: "acrcloud:$artist|$title",
            title = title,
            artist = artist,
            album = music.getAsJsonObject("album")?.get("name")?.takeIf { !it.isJsonNull }?.asString,
            releaseDate = music.get("release_date")?.takeIf { !it.isJsonNull }?.asString,
            sourceProvider = "ACRCloud",
        )
    }
}