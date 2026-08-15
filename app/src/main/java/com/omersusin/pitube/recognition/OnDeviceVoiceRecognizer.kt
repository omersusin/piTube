package com.omersusin.pitube.recognition

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.omersusin.pitube.data.local.RecognitionFailureType
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Fallback voice path per spec: Android's built-in `SpeechRecognizer` —
 * `createOnDeviceSpeechRecognizer()` on Android 12+, otherwise
 * `createSpeechRecognizer()`. No model download, no API key.
 */
class OnDeviceVoiceRecognizer(
    private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Runs one listening session. Resolves with the final transcript or throws
     * [RecognitionException] (BAD_CONNECTION for the network-dependent
     * recognizer, OTHER for no-speech/errors).
     */
    suspend fun listen(): String = suspendCancellableCoroutine { continuation ->
        val recognizer =
            createRecognizer() ?: run {
                continuation.resumeWith(Result.failure(RecognitionException(RecognitionFailureType.OTHER, "Speech recognition unavailable")))
                return@suspendCancellableCoroutine
            }

        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

        var settled = false

        fun finishSuccess(text: String, r: SpeechRecognizer) {
            if (settled) return
            settled = true
            runCatching { r.destroy() }
            continuation.resume(text)
        }

        fun finishError(message: String, r: SpeechRecognizer) {
            if (settled) return
            settled = true
            runCatching { r.destroy() }
            continuation.resumeWith(Result.failure(RecognitionException(RecognitionFailureType.OTHER, message)))
        }

        val listener =
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    val message: String =
                        when {
                            error == SpeechRecognizer.ERROR_NO_MATCH ||
                                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"

                            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                                error == SpeechRecognizer.ERROR_NETWORK -> "Speech recognition network error"

                            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy"
                            error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing"
                            else -> "Speech recognition error (code $error)"
                        }
                    Log.w("OnDeviceVoice", "SpeechRecognizer error=$error ($message)")
                    finishError(message, recognizer)
                }

                override fun onResults(results: Bundle?) {
                    val text =
                        results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                    if (text.isEmpty()) {
                        finishError("No speech detected", recognizer)
                    } else {
                        Log.d("OnDeviceVoice", "On-device transcript: $text")
                        finishSuccess(text, recognizer)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?,
                ) = Unit
            }

        recognizer.setRecognitionListener(listener)
        continuation.invokeOnCancellation {
            runCatching { recognizer.destroy() }
        }
        mainHandler.post { recognizer.startListening(intent) }
    }

    private fun createRecognizer(): SpeechRecognizer? =
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    ?: SpeechRecognizer.createSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (e: Exception) {
            Log.w("OnDeviceVoice", "create on-device recognizer failed", e)
            SpeechRecognizer.createSpeechRecognizer(context)
        }
}