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
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.RecognitionFailureType
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Fallback voice path per spec: Android's built-in `SpeechRecognizer` —
 * `createOnDeviceSpeechRecognizer()` on Android 12+, otherwise
 * `createSpeechRecognizer()`. No model download, no API key.
 *
 * Every `SpeechRecognizer` operation below must run on the application's main
 * thread (the platform throws `checkIsCalledFromMainThread` otherwise), so the
 * whole create/set-listener/listen/destroy lifecycle is posted to the main
 * looper even when this class is invoked from a background coroutine.
 */
class OnDeviceVoiceRecognizer(
    private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        /** Hard cap so a stuck recognizer can never leave the UI hanging. */
        private const val MAX_LISTEN_MS = 20_000L
    }

    /**
     * Runs one listening session. Resolves with the final transcript or throws
     * [RecognitionException] (BAD_CONNECTION for the network-dependent
     * recognizer, OTHER for no-speech/errors). Live RMS levels measured by the
     * recognizer are forwarded through [onLevel] so the listening visual stays
     * animated on this fallback path too. A watchdog caps the session at
     * [MAX_LISTEN_MS] so it can always settle.
     */
    suspend fun listen(onLevel: (Float) -> Unit = {}): String = suspendCancellableCoroutine { continuation ->
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

        var settled = false
        var watchdog: Runnable? = null
        var recognizer: SpeechRecognizer? = null

        fun destroyOnMain(r: SpeechRecognizer) {
            mainHandler.post { runCatching { r.destroy() } }
        }

        fun finishSuccess(text: String) {
            if (settled) return
            settled = true
            watchdog?.let { mainHandler.removeCallbacks(it) }
            recognizer?.let { destroyOnMain(it) }
            continuation.resume(text)
        }

        fun finishError(message: String) {
            if (settled) return
            settled = true
            watchdog?.let { mainHandler.removeCallbacks(it) }
            recognizer?.let { destroyOnMain(it) }
            continuation.resumeWith(Result.failure(RecognitionException(RecognitionFailureType.OTHER, message)))
        }

        watchdog =
            Runnable {
                Log.w("OnDeviceVoice", "Watchdog: no result within ${MAX_LISTEN_MS}ms")
                finishError(context.getString(R.string.recognition_error_no_speech))
            }

        mainHandler.post {
            val created =
                createRecognizer().also { created ->
                    if (created != null) {
                        recognizer = created
                        created.setRecognitionListener(
                            object : RecognitionListener {
                                override fun onReadyForSpeech(params: Bundle?) = Unit

                                override fun onBeginningOfSpeech() = Unit

                                override fun onRmsChanged(rmsdB: Float) {
                                    // SpeechRecognizer reports RMS in dB, roughly [-20, 0];
                                    // map it into the same 0..1 amplitude space the
                                    // AudioRecord path feeds the listening visual with.
                                    onLevel(((rmsdB.coerceAtLeast(-18f) + 18f) / 18f).coerceIn(0f, 1f))
                                }

                                override fun onBufferReceived(buffer: ByteArray?) = Unit

                                override fun onEndOfSpeech() = Unit

                                override fun onError(error: Int) {
                                    val message: String =
                                        when (error) {
                                            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
                                                context.getString(R.string.recognition_error_too_many_requests)

                                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                                                context.getString(R.string.recognition_error_language_not_supported)

                                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                                                context.getString(R.string.recognition_error_language_unavailable)

                                            SpeechRecognizer.ERROR_NO_MATCH,
                                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                                context.getString(R.string.recognition_error_no_speech)

                                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                                            SpeechRecognizer.ERROR_NETWORK ->
                                                context.getString(R.string.recognition_error_network)

                                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                                context.getString(R.string.recognition_error_busy)

                                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                                context.getString(R.string.recognition_error_permission)

                                            else ->
                                                context.getString(R.string.recognition_error_generic) +
                                                    " (code $error)"
                                        }
                                    Log.w("OnDeviceVoice", "SpeechRecognizer error=$error ($message)")
                                    finishError(message)
                                }

                                override fun onResults(results: Bundle?) {
                                    val text =
                                        results
                                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                            ?.firstOrNull()
                                            ?.trim()
                                            .orEmpty()
                                    if (text.isEmpty()) {
                                        finishError(context.getString(R.string.recognition_error_no_speech))
                                    } else {
                                        Log.d("OnDeviceVoice", "On-device transcript: $text")
                                        finishSuccess(text)
                                    }
                                }

                                override fun onPartialResults(partialResults: Bundle?) = Unit

                                override fun onEvent(
                                    eventType: Int,
                                    params: Bundle?,
                                ) = Unit
                            },
                        )
                    }
                }

            if (created == null) {
                Log.w("OnDeviceVoice", "Speech recognition unavailable")
                finishError(context.getString(R.string.recognition_error_unavailable))
                return@post
            }

            mainHandler.postDelayed(watchdog, MAX_LISTEN_MS)
            try {
                created.startListening(intent)
            } catch (e: Exception) {
                Log.w("OnDeviceVoice", "startListening failed", e)
                finishError(e.message ?: context.getString(R.string.recognition_error_start_failed))
            }
        }

        continuation.invokeOnCancellation {
            watchdog?.let { mainHandler.removeCallbacks(it) }
            recognizer?.let { destroyOnMain(it) }
        }
    }

    private fun createRecognizer(): SpeechRecognizer? {
    // Prefer a genuine on-device engine on Android 12+ (offline, no Google quota).
    // The network recognizer (createSpeechRecognizer) streams to Google's cloud,
    // which can rate-limit with ERROR_TOO_MANY_REQUESTS (code 10), so it is only
    // used as a last resort and logged so the serving path is visible.
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    ) {
        return try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                ?: SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            Log.w("OnDeviceVoice", "create on-device recognizer failed", e)
            null
        }
    }
    Log.w("OnDeviceVoice", "No on-device recognition available; falling back to network recognizer")
    return try {
        SpeechRecognizer.createSpeechRecognizer(context)
    } catch (e: Exception) {
        Log.w("OnDeviceVoice", "create network recognizer failed", e)
        null
    }
}
}