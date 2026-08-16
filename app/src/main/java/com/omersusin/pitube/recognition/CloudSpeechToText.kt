package com.omersusin.pitube.recognition

import android.content.Context
import android.util.Log
import com.omersusin.pitube.data.local.RecognitionFailureType
import com.omersusin.pitube.data.local.SttApiKeyStore
import com.omersusin.pitube.data.local.SttProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Cloud speech-to-text backends for voice recognition. Each provider uses the
 * person's own credentials from [SttApiKeyStore] (set in Settings → Sesten
 * Yazıya):
 *
 *  - Groq Cloud: OpenAI-compatible Whisper endpoint, Bearer token.
 *  - IBM Watson Speech to Text: IAM bearer token + instance URL.
 *  - Azure Speech (F0): region-dependent endpoint, `Ocp-Apim-Subscription-Key`.
 *  - Google Cloud Speech-to-Text V1: inline base64 + write-mask API key.
 *
 * On any failure a [RecognitionException] is thrown; `RecognitionRepository`
 * then falls back to the on-device recognizer so the person is never
 * dead-ended.
 */
object CloudSpeechToText {
    private const val TAG = "CloudSTT"

    private val json = Json { ignoreUnknownKeys = true }

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    @Serializable
    private data class GroqResponse(
        @SerialName("text") val text: String? = null,
    )

    @Serializable
    private data class IbmTokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
    )

    @Serializable
    private data class RecognitionAlternative(
        @SerialName("transcript") val transcript: String? = null,
    )

    @Serializable
    private data class RecognitionResult(
        @SerialName("alternatives") val alternatives: List<RecognitionAlternative>? = null,
    )

    @Serializable
    private data class GoogleResponse(
        @SerialName("results") val results: List<RecognitionResult>? = null,
    )

    @Serializable
    private data class IbmResponse(
        @SerialName("results") val results: List<RecognitionResult>? = null,
    )

    @Serializable
    private data class AzureResponse(
        @SerialName("DisplayText") val displayText: String? = null,
        @SerialName("RecognitionStatus") val status: String? = null,
    )

    @Serializable
    private data class GoogleRequestConfig(
        @SerialName("encoding") val encoding: String = "LINEAR16",
        @SerialName("sampleRateHertz") val sampleRateHertz: Int = 16000,
        @SerialName("languageCode") val languageCode: String = "en-US",
    )

    @Serializable
    private data class GoogleRequestAudio(
        @SerialName("content") val content: String,
    )

    @Serializable
    private data class GoogleRequestBody(
        @SerialName("config") val config: GoogleRequestConfig = GoogleRequestConfig(),
        @SerialName("audio") val audio: GoogleRequestAudio,
    )

    suspend fun transcribe(
        context: Context,
        provider: SttProvider,
        wavBytes: ByteArray,
    ): String = withContext(Dispatchers.IO) {
        val keys = SttApiKeyStore(context)
        val apiKey = keys.getApiKey(provider)
        if (apiKey.isNullOrBlank()) {
            throw RecognitionException(
                RecognitionFailureType.OTHER,
                "$provider is not configured (missing API key in Settings)",
            )
        }

        try {
            when (provider) {
                SttProvider.GROQ -> transcribeGroq(apiKey, wavBytes)
                SttProvider.GOOGLE_CLOUD -> transcribeGoogle(apiKey, wavBytes)
                SttProvider.AZURE -> {
                    val region = keys.getAzureRegion()
                    if (region.isNullOrBlank()) {
                        throw RecognitionException(
                            RecognitionFailureType.OTHER,
                            "Azure is not configured (missing region in Settings)",
                        )
                    }
                    transcribeAzure(apiKey, region, wavBytes)
                }
                SttProvider.IBM_WATSON -> {
                    val instanceUrl = keys.getIbmInstanceUrl()
                    if (instanceUrl.isNullOrBlank()) {
                        throw RecognitionException(
                            RecognitionFailureType.OTHER,
                            "IBM Watson is not configured (missing instance URL in Settings)",
                        )
                    }
                    transcribeIbm(apiKey, instanceUrl, wavBytes)
                }
                SttProvider.CIHAZ -> throw RecognitionException(
                    RecognitionFailureType.OTHER,
                    "Cihaz STT does not use cloud transcription",
                )
            }
        } catch (e: RecognitionException) {
            throw e
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Network failure while transcribing", e)
            throw RecognitionException(RecognitionFailureType.BAD_CONNECTION, "Network unavailable")
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected transcription failure", e)
            throw RecognitionException(RecognitionFailureType.OTHER, "Transcription failed")
        }
    }

    private fun transcribeGroq(apiKey: String, wavBytes: ByteArray): String {
        val body =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-large-v3-turbo")
                .addFormDataPart("response_format", "json")
                .addFormDataPart("language", "en")
                .addFormDataPart("file", "sample.wav", wavBytes.toRequestBody("audio/wav".toMediaType()))
                .build()

        val request =
            Request.Builder()
                .url("https://api.groq.com/openai/v1/audio/transcriptions")
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RecognitionException(
                    RecognitionFailureType.OTHER,
                    "Groq failed (HTTP ${response.code})",
                )
            }
            val text = json.decodeFromString(GroqResponse.serializer(), response.body?.string().orEmpty()).text.orEmpty().trim()
            transcriptOrThrow(text, "Groq")
        }
    }

    private fun transcribeGoogle(apiKey: String, wavBytes: ByteArray): String {
        val bodyText =
            json.encodeToString(
                GoogleRequestBody.serializer(),
                GoogleRequestBody(
                    audio = GoogleRequestAudio(content = Base64.getEncoder().encodeToString(wavBytes)),
                ),
            )

        val request =
            Request.Builder()
                .url("https://speech.googleapis.com/v1/speech:recognize?key=$apiKey")
                .header("Content-Type", "application/json")
                .post(bodyText.toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RecognitionException(
                    RecognitionFailureType.OTHER,
                    "Google Cloud failed (HTTP ${response.code})",
                )
            }
            val parsed = json.decodeFromString(GoogleResponse.serializer(), response.body?.string().orEmpty())
            val text = parsed.results?.firstOrNull()?.alternatives?.firstOrNull()?.transcript.orEmpty().trim()
            transcriptOrThrow(text, "Google Cloud")
        }
    }

    private fun transcribeAzure(apiKey: String, region: String, wavBytes: ByteArray): String {
        val request =
            Request.Builder()
                .url("https://$region.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=en-US&format=detailed")
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .header("Content-Type", "audio/wav")
                .post(wavBytes.toRequestBody("audio/wav".toMediaType()))
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RecognitionException(
                    RecognitionFailureType.OTHER,
                    "Azure failed (HTTP ${response.code})",
                )
            }
            val parsed = json.decodeFromString(AzureResponse.serializer(), response.body?.string().orEmpty())
            if (parsed.status != "Success") {
                throw RecognitionException(
                    RecognitionFailureType.OTHER,
                    "Azure recognition status: ${parsed.status ?: "unknown"}",
                )
            }
            transcriptOrThrow(parsed.displayText.orEmpty().trim(), "Azure")
        }
    }

    private fun transcribeIbm(apiKey: String, instanceUrl: String, wavBytes: ByteArray): String {
        val token = acquireIbmToken(apiKey)

        val request =
            Request.Builder()
                .url("$instanceUrl/v1/recognize?model=en-US_BroadbandModel")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "audio/wav")
                .post(wavBytes.toRequestBody("audio/wav".toMediaType()))
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RecognitionException(
                    RecognitionFailureType.OTHER,
                    "IBM Watson failed (HTTP ${response.code})",
                )
            }
            val parsed = json.decodeFromString(IbmResponse.serializer(), response.body?.string().orEmpty())
            val text = parsed.results?.firstOrNull()?.alternatives?.firstOrNull()?.transcript.orEmpty().trim()
            transcriptOrThrow(text, "IBM Watson")
        }
    }

    private fun acquireIbmToken(apiKey: String): String {
        val form =
            "grant_type=urn%3Aibm%3Aparams%3Aoauth%3Agrant-type%3Aapikey&apikey=$apiKey"
        val request =
            Request.Builder()
                .url("https://iam.cloud.ibm.com/identity/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RecognitionException(
                    RecognitionFailureType.OTHER,
                    "IBM Watson auth failed (HTTP ${response.code})",
                )
            }
            val token = json.decodeFromString(IbmTokenResponse.serializer(), response.body?.string().orEmpty()).accessToken
            if (token.isNullOrBlank()) {
                throw RecognitionException(RecognitionFailureType.OTHER, "IBM Watson auth: no token")
            }
            token
        }
    }

    private fun transcriptOrThrow(text: String, provider: String): String {
        if (text.isBlank()) {
            throw RecognitionException(
                RecognitionFailureType.OTHER,
                "$provider transcription result empty",
            )
        }
        return text
    }
}