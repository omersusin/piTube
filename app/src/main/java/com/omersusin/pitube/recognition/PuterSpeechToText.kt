package com.omersusin.pitube.recognition

import android.util.Log
import com.omersusin.pitube.data.local.RecognitionFailureType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Kotlin port of puter.js `puter.ai.speech2txt` (Whisper transcription):
 *
 * 1. A guest ("temp") account is created silently via `POST /signup` with
 *    `{"is_temp": true}` — allowed for non-browser clients that send no
 *    `Origin` header (puter.com's origin gate passes them through).
 * 2. The returned `token` is sent in the JSON body of
 *    `POST /drivers/call` (`auth_token`), with the audio as a base64 data-URI
 *    in `args.file`, provider `openai`, model `whisper-1`.
 *
 * No API key, no user-facing credential field — same porting style as the
 * Shazam integration. See HeyPuter/puter (MIT) — src/puter-js/src/modules/ai/stt.js
 * and src/puter-js/src/lib/networkUtils.js.
 */
object PuterSpeechToText {
    private const val TAG = "PuterSTT"
    private const val SIGNUP_URL = "https://puter.com/signup"
    private const val DRIVERS_CALL_URL = "https://api.puter.com/drivers/call"
    private const val DRIVER_CONTENT_TYPE = "text/plain;actually=json"
    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 2_000L

    private val json = Json { ignoreUnknownKeys = true }

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    @Serializable
    private data class SignupResponse(
        @SerialName("token") val token: String? = null,
    )

    @Serializable
    private data class DriverCallResponse(
        @SerialName("success") val success: Boolean = false,
        @SerialName("result") val result: DriverResult? = null,
        @SerialName("code") val code: String? = null,
    )

    @Serializable
    private data class DriverResult(
        @SerialName("text") val text: String? = null,
    )

    @Serializable
    private data class DriverCallBody(
        @SerialName("interface") val interfaceName: String,
        @SerialName("driver") val driver: String,
        @SerialName("method") val method: String,
        @SerialName("test_mode") val testMode: Boolean = false,
        @SerialName("args") val args: Map<String, String>,
        @SerialName("auth_token") val authToken: String,
    )

    @Volatile
    private var guestToken: String? = null

    private sealed interface AttemptResult {
        data class Text(val value: String) : AttemptResult
        data class Retryable(val httpCode: Int) : AttemptResult
        data class HardError(val type: RecognitionFailureType, val message: String) : AttemptResult
    }

    /**
     * Transcribes the captured WAV (16 kHz mono PCM) to text. Throws
     * [RecognitionException] with BAD_CONNECTION for network failures and
     * OTHER for schema change / upstream rejection, so the caller can fall
     * back to the on-device recognizer as required.
     */
    suspend fun transcribe(wavBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val dataUri =
            "data:audio/wav;base64," +
                Base64.getEncoder().encodeToString(wavBytes)

        var attempt = 0
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            val token = guestToken ?: acquireGuestToken()
            when (val result = transcribeOnce(token, dataUri)) {
                is AttemptResult.Text -> return@withContext result.value
                is AttemptResult.Retryable -> {
                    if (result.httpCode == 401) guestToken = null
                    if (attempt < MAX_ATTEMPTS) {
                        delay(RETRY_DELAY_MS)
                        continue
                    }
                    throw RecognitionException(RecognitionFailureType.OTHER, "Transcription failed (HTTP ${result.httpCode})")
                }
                is AttemptResult.HardError ->
                    throw RecognitionException(result.type, result.message)
            }
        }
        throw RecognitionException(RecognitionFailureType.OTHER, "Transcription failed")
    }

    private fun transcribeOnce(
        token: String,
        dataUri: String,
    ): AttemptResult {
        return try {
            val body =
                DriverCallBody(
                    interfaceName = "puter-speech2txt",
                    driver = "ai-speech2txt",
                    method = "transcribe",
                    args =
                        mapOf(
                            "file" to dataUri,
                            "model" to "whisper-1",
                        ),
                    authToken = token,
                )
            val request =
                Request.Builder()
                    .url(DRIVERS_CALL_URL)
                    .header("Content-Type", DRIVER_CONTENT_TYPE)
                    .post(json.encodeToString(DriverCallBody.serializer(), body).toRequestBody(DRIVER_CONTENT_TYPE.toMediaType()))
                    .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return AttemptResult.Retryable(response.code)
                }
                val parsed = runCatching { json.decodeFromString(DriverCallResponse.serializer(), bodyText) }.getOrNull()
                val text = parsed?.result?.text?.trim().orEmpty()
                if (parsed?.success == true && text.isNotEmpty()) {
                    AttemptResult.Text(text)
                } else {
                    AttemptResult.HardError(
                        RecognitionFailureType.OTHER,
                        "Transcription result empty: ${parsed?.code ?: "unknown"}",
                    )
                }
            }
        } catch (e: RecognitionException) {
            AttemptResult.HardError(e.type, e.message ?: "Transcription failed")
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Network failure while transcribing", e)
            AttemptResult.HardError(RecognitionFailureType.BAD_CONNECTION, "Network unavailable")
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected transcription failure", e)
            AttemptResult.HardError(RecognitionFailureType.OTHER, "Transcription failed")
        }
    }

    private fun acquireGuestToken(): String {
        val body = """{"is_temp":true,"referrer":"/"}"""
        val request =
            Request.Builder()
                .url(SIGNUP_URL)
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RecognitionException(
                        RecognitionFailureType.OTHER,
                        "Guest auth failed (HTTP ${response.code})",
                    )
                }
                val parsed = json.decodeFromString(SignupResponse.serializer(), response.body?.string().orEmpty())
                val token = parsed.token?.takeIf { it.isNotBlank() }
                if (token == null) {
                    throw RecognitionException(RecognitionFailureType.OTHER, "Guest auth: no token")
                }
                guestToken = token
                token
            }
        } catch (e: RecognitionException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Guest auth failed", e)
            throw RecognitionException(RecognitionFailureType.BAD_CONNECTION, "Guest auth: network failure")
        }
    }
}