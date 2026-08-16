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
import kotlin.coroutines.resumeWithException

/**
 * Fallback voice path per spec: Android's built-in `SpeechRecognizer` —
 * `createOnDeviceSpeechRecognizer()` on Android 12+, otherwise
 * `createSpeechRecognizer()`. No model download, no API key.
 *
 * Every `SpeechRecognizer` operation below must run on the application's main
 * thread (the platform throws `checkIsCalledFromMainThread` otherwise), so the
 * whole create/set-listener/listen/destroy lifecycle is posted to the main
 * looper even when this class is invoked from a background coroutine.
 *
 * Sessions are serialized: a single recognizer is held for a session and fully
 * torn down (cancel + destroy) BEFORE the next session creates a new one. This
 * avoids `ERROR_RECOGNIZER_BUSY`, which the singleton-bound recognition service
 * returns when a previous recognizer is still holding the session while a new
 * `startListening()` binds.
 *
 * Two guards make the failure mode visible instead of a misleading "busy":
 * no recognition service on the device fails fast with the "unavailable"
 * message, and a transient `ERROR_RECOGNIZER_BUSY` is retried once after a
 * short teardown delay before the error is surfaced.
 */
class OnDeviceVoiceRecognizer(
    private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The recognizer of the in-flight session, if any. Destroyed on teardown. */
    private var current: SpeechRecognizer? = null
    private val bound = java.util.concurrent.atomic.AtomicBoolean(false)

    companion object {
        /** Hard cap so a stuck recognizer can never leave the UI hanging. */
        private const val MAX_LISTEN_MS = 20_000L

        /** How many silent milliseconds after speech ends the session completes. */
        private const val COMPLETE_SILENCE_MS = 900L

        /** Short pause allowed mid-speech before it may be considered done. */
        private const val POSSIBLY_COMPLETE_SILENCE_MS = 400L

        /** How long the mic waits for the first word before giving up. */
        private const val SPEAK_TIMEOUT_MS = 8_000L

        /** How long to wait after a busy teardown before retrying. */
        private const val BUSY_RETRY_DELAY_MS = 400L

        /** A transient busy is retried once, never infinitely. */
        private const val MAX_BUSY_RETRIES = 1
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
                // End the session shortly after the person stops talking instead of
                // waiting out the recognizer's long default silence window. The
                // on-device engine honors these; speak-timeout bounds how long the
                // mic waits for the first word before giving up.
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    COMPLETE_SILENCE_MS,
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    POSSIBLY_COMPLETE_SILENCE_MS,
                )
                putExtra(RecognizerIntent.EXTRA_SPEAK_TIMEOUT, SPEAK_TIMEOUT_MS)
            }

        var settled = false
        var sessionRecognizer: SpeechRecognizer? = null
        var busyRetriesLeft = MAX_BUSY_RETRIES

        // Late-initialized: the watchdog references finishError() below, which in
        // turn references this same Runnable to unschedule itself.
        var watchdog: Runnable? = null

        fun destroyOnMain(r: SpeechRecognizer) {
            mainHandler.post { runCatching { r.cancel() }; runCatching { r.destroy() } }
        }

        fun finishSuccess(text: String) {
            if (settled) return
            settled = true
            watchdog?.let { mainHandler.removeCallbacks(it) }
            sessionRecognizer?.let { destroyOnMain(it) }
            bound.set(false)
            current = null
            Log.d("STT", "on-device transcript: $text")
            continuation.resume(text)
        }

        fun finishError(message: String, type: RecognitionFailureType = RecognitionFailureType.OTHER) {
            if (settled) return
            settled = true
            watchdog?.let { mainHandler.removeCallbacks(it) }
            sessionRecognizer?.let { destroyOnMain(it) }
            bound.set(false)
            current = null
            continuation.resumeWithException(RecognitionException(type, message))
        }

        watchdog =
            Runnable {
                Log.w("STT", "OnDeviceVoice watchdog: no result within ${MAX_LISTEN_MS}ms")
                finishError(context.getString(R.string.recognition_error_no_speech))
            }

        fun startSession(forceNetwork: Boolean = false) {
            // Serialize: if a previous session's recognizer is still bound on the
            // same looper, tear it down before creating a fresh one so the
            // recognition service is never handed two active sessions.
            teardownPending()
            if (bound.get()) {
                Log.w("STT", "OnDeviceVoice already listening; dropping duplicate start")
                return
            }
            // Fail fast when the device has no recognition service at all, instead
            // of letting startListening surface a misleading "busy" later.
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w("STT", "OnDeviceVoice no recognition service on this device")
                finishError(context.getString(R.string.recognition_error_unavailable))
                return
            }
            val created =
                createRecognizer(forceNetwork).also { created ->
                    if (created != null) {
                        sessionRecognizer = created
                        current = created
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
                                    // A transient busy (service briefly holds the session) is
                                    // retried once after a full teardown. The retry uses the
                                    // network recognizer: a device where the on-device engine is
                                    // *advertised* but unusable (no model downloaded) can report
                                    // busy forever, while the network recognizer actually works.
                                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && busyRetriesLeft > 0) {
                                        busyRetriesLeft -= 1
                                        Log.w("STT", "OnDeviceVoice busy; tearing down and retrying with network recognizer once")
                                        teardownPending()
                                        watchdog?.let { mainHandler.removeCallbacks(it) }
                                        mainHandler.postDelayed(
                                            { startSession(forceNetwork = true) },
                                            BUSY_RETRY_DELAY_MS,
                                        )
                                        return
                                    }
                                    Log.w("STT", "OnDeviceVoice SpeechRecognizer error=$error ($message)")
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
                Log.w("STT", "OnDeviceVoice speech recognition unavailable")
                finishError(context.getString(R.string.recognition_error_unavailable))
                return
            }

            bound.set(true)
            mainHandler.postDelayed(watchdog, MAX_LISTEN_MS)
            try {
                created.startListening(intent)
                Log.d("STT", "OnDeviceVoice startListening OK")
            } catch (e: Exception) {
                Log.w("STT", "OnDeviceVoice startListening failed", e)
                bound.set(false)
                current = null
                finishError(e.message ?: context.getString(R.string.recognition_error_start_failed))
            }
        }

        mainHandler.post { startSession() }

        continuation.invokeOnCancellation {
            watchdog?.let { mainHandler.removeCallbacks(it) }
            mainHandler.post { teardownPending() }
        }
    }

    /**
     * Cancels and destroys any recognizer currently bound to the main looper.
     * Safe to call from any thread: the teardown is posted to the main thread.
     * A cancelled session's continuation simply never resumes — the coroutine
     * is already cancelled, so no state is left behind for the next session.
     */
    fun cancel() {
        mainHandler.post { teardownPending() }
    }

    /**
     * Cancels and destroys any recognizer bound to the main looper. Must be
     * invoked on the main thread.
     */
    private fun teardownPending() {
        current?.let { previous ->
            runCatching { previous.cancel() }
            runCatching { previous.destroy() }
        }
        current = null
        bound.set(false)
    }

    private fun createRecognizer(forceNetwork: Boolean = false): SpeechRecognizer? {
        // A forced network attempt comes from the busy-retry path: a device where
        // the on-device engine is configured but unusable needs the network
        // recognizer instead, so on-device preference is bypassed.
        if (!forceNetwork &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            return try {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    ?: SpeechRecognizer.createSpeechRecognizer(context).also {
                        Log.d("STT", "OnDeviceVoice on-device unavailable; using network recognizer")
                    }
            } catch (e: Exception) {
                Log.w("STT", "OnDeviceVoice create on-device recognizer failed", e)
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        }
        Log.d("STT", "OnDeviceVoice no on-device recognition available; using network recognizer")
        return try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            Log.w("STT", "OnDeviceVoice create network recognizer failed", e)
            null
        }
    }
}