package com.omersusin.pitube.recognition

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.RecognitionFailureType
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fallback voice path per spec: Android's built-in `SpeechRecognizer`.
 *
 * The engine is picked device-agnostically instead of trusting the on-device
 * flag alone:
 *
 *  - The network recognizer (`createSpeechRecognizer()`, the system default —
 *    Google TTS on most devices) is used unless the on-device engine is both
 *    advertised AND genuinely usable.
 *  - On Android 13+ a support query
 *    (`checkRecognitionSupport`) verifies the current locale's model is really
 *    installed before the on-device engine is used. An engine that is merely
 *    *advertised* — rate-limited, or without the downloaded language model —
 *    fails instantly with `ERROR_TOO_MANY_REQUESTS` / `ERROR_LANGUAGE_UNAVAILABLE`,
 *    which is exactly the failure seen across devices while Gboard works (it
 *    uses the network service). Google's own apps rely on the network
 *    recognizer and let the service negotiate; so do we when on-device is not
 *    truly available.
 *  - Once the on-device engine has proven broken on this device it is skipped
 *    for the rest of the process (each session otherwise repeats the failure).
 *  - A session that still fails mid-flight is retried once on the network
 *    recognizer after a full teardown (rate-limited requests wait ~3 s first —
 *    retrying in <1 s makes `ERROR_TOO_MANY_REQUESTS` worse).
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
 */
class OnDeviceVoiceRecognizer(
    private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }

    /** The recognizer of the in-flight session, if any. Destroyed on teardown. */
    private var current: SpeechRecognizer? = null
    private val bound = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Set once the on-device engine has proven unusable on this device
     * (rate-limited, language model missing, or support query failing). Later
     * sessions skip it and go straight to the network recognizer instead of
     * repeating the failure on every attempt.
     */
    @Volatile
    private var onDeviceEngineBroken = false

    private enum class Engine {
        ON_DEVICE,
        NETWORK,
    }

    companion object {
        /** Hard cap so a stuck recognizer can never leave the UI hanging. */
        private const val MAX_LISTEN_MS = 20_000L

        /** How many silent milliseconds after speech ends the session completes. */
        private const val COMPLETE_SILENCE_MS = 900L

        /** Short pause allowed mid-speech before it may be considered done. */
        private const val POSSIBLY_COMPLETE_SILENCE_MS = 400L

        /** How long to wait after a busy/language teardown before retrying. */
        private const val RETRY_DELAY_MS = 400L

        /**
         * `ERROR_TOO_MANY_REQUESTS` needs a real backoff: retrying in under a
         * second makes the rate limiting worse, ~3 s lets it settle.
         */
        private const val TOO_MANY_REQUESTS_RETRY_DELAY_MS = 3_000L

        /** How long to wait for the on-device support query before falling back. */
        private const val SUPPORT_CHECK_TIMEOUT_MS = 3_000L

        /** A transient failure is retried once on the network engine, never in a loop. */
        private const val MAX_ENGINE_RETRIES = 1
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
        val intent = buildIntent()

        var settled = false
        var cancelled = false
        var sessionRecognizer: SpeechRecognizer? = null
        var currentEngine = Engine.NETWORK
        var engineRetriesLeft = MAX_ENGINE_RETRIES

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

        /**
         * Binds [recognizer] for the session and starts listening. Only reached
         * on the main thread, after engine resolution.
         */
        fun proceedWithSession(recognizer: SpeechRecognizer, engine: Engine) {
            // The support query or a delayed retry can land after the user
            // dismissed/cancelled the session — never start listening on it then.
            if (settled || cancelled) {
                destroyOnMain(recognizer)
                return
            }
            currentEngine = engine
            sessionRecognizer = recognizer
            current = recognizer
            recognizer.setRecognitionListener(
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
                        // Once the on-device engine proves broken (rate-limited,
                        // model missing, or refusing to bind) it is skipped for the
                        // rest of the process so every future session does not repeat
                        // the failure before falling back to network.
                        if (currentEngine == Engine.ON_DEVICE &&
                            (error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ||
                                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                                error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
                        ) {
                            Log.w("STT", "OnDeviceVoice on-device engine broken (error=$error); skipping it for future sessions")
                            onDeviceEngineBroken = true
                        }
                        // A transient busy, a rate limit, or (on the on-device
                        // engine) a missing language model gets one retry on the
                        // network recognizer after a full teardown. Rate-limited
                        // requests wait ~3 s first; busy/language retries are quick.
                        val retriable =
                            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                                error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ||
                                (currentEngine == Engine.ON_DEVICE &&
                                    (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                                        error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
                        if (retriable && engineRetriesLeft > 0) {
                            engineRetriesLeft -= 1
                            val delay =
                                if (error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS) {
                                    TOO_MANY_REQUESTS_RETRY_DELAY_MS
                                } else {
                                    RETRY_DELAY_MS
                                }
                            Log.w("STT", "OnDeviceVoice error=$error; tearing down and retrying on network recognizer once (delay=${delay}ms)")
                            teardownPending()
                            watchdog.let { mainHandler.removeCallbacks(it) }
                            mainHandler.postDelayed(
                                {
                                    if (!settled && !cancelled) {
                                        val recognizer = createNetwork()
                                        if (recognizer == null) {
                                            finishError(context.getString(R.string.recognition_error_unavailable))
                                        } else {
                                            proceedWithSession(recognizer, Engine.NETWORK)
                                        }
                                    }
                                },
                                delay,
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
            bound.set(true)
            mainHandler.postDelayed(watchdog, MAX_LISTEN_MS)
            try {
                recognizer.startListening(intent)
                Log.d("STT", "OnDeviceVoice startListening OK ($engine)")
            } catch (e: Exception) {
                Log.w("STT", "OnDeviceVoice startListening failed", e)
                bound.set(false)
                current = null
                finishError(e.message ?: context.getString(R.string.recognition_error_start_failed))
            }
        }

        fun startNetwork(engine: Engine = Engine.NETWORK) {
            createNetwork()?.let { proceedWithSession(it, engine) }
                ?: finishError(context.getString(R.string.recognition_error_unavailable))
        }

        /**
         * Resolves the engine and starts the session. Must run on the main
         * thread: engine resolution may trigger a (guarded, API 33+)
         * `checkRecognitionSupport` support query that is itself dispatched on
         * the main looper.
         */
        fun startSession(forceNetwork: Boolean) {
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
            // The network recognizer is the default unless the on-device engine is
            // advertised, not known-broken, and — on Android 13+ — verified to
            // actually have the requested locale installed.
            if (forceNetwork || onDeviceEngineBroken ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                Log.d("STT", "OnDeviceVoice using network recognizer")
                startNetwork()
                return
            }
            val probe =
                try {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } catch (e: Exception) {
                    Log.w("STT", "OnDeviceVoice create on-device recognizer failed", e)
                    null
                }
            if (probe == null) {
                startNetwork()
                return
            }
            // Pre-13 there is no support query: trust the advertised flag and let
            // the error ladder / broken-engine flag catch failures.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Log.d("STT", "OnDeviceVoice using on-device recognizer (no support query pre-13)")
                proceedWithSession(probe, Engine.ON_DEVICE)
                return
            }
            // Android 13+: verify the requested locale's model is really installed
            // on-device before trusting an engine that is merely advertised. A
            // device with the on-device service configured but unusable (no model
            // downloaded, rate-limited) fails instantly otherwise.
            var supportSettled = false
            val timeout =
                Runnable {
                    if (supportSettled) return@Runnable
                    supportSettled = true
                    Log.w("STT", "OnDeviceVoice support check timed out; using network recognizer")
                    onDeviceEngineBroken = true
                    runCatching { probe.destroy() }
                    startNetwork()
                }
            mainHandler.postDelayed(timeout, SUPPORT_CHECK_TIMEOUT_MS)
            try {
                probe.checkRecognitionSupport(
                    intent,
                    mainExecutor,
                    object : RecognitionSupportCallback {
                        override fun onSupportResult(support: RecognitionSupport) {
                            if (supportSettled) return
                            supportSettled = true
                            mainHandler.removeCallbacks(timeout)
                            val installed =
                                localeInstalled(
                                    intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE),
                                    support.installedOnDeviceLanguages,
                                )
                            if (installed) {
                                Log.d("STT", "OnDeviceVoice on-device truly available for this locale")
                                proceedWithSession(probe, Engine.ON_DEVICE)
                            } else {
                                Log.d("STT", "OnDeviceVoice on-device locale not installed; using network recognizer")
                                onDeviceEngineBroken = true
                                runCatching { probe.destroy() }
                                startNetwork()
                            }
                        }

                        override fun onError(error: Int) {
                            if (supportSettled) return
                            supportSettled = true
                            mainHandler.removeCallbacks(timeout)
                            Log.w("STT", "OnDeviceVoice support check error=$error; using network recognizer")
                            onDeviceEngineBroken = true
                            runCatching { probe.destroy() }
                            startNetwork()
                        }
                    },
                )
            } catch (e: Exception) {
                if (supportSettled) return
                supportSettled = true
                mainHandler.removeCallbacks(timeout)
                Log.w("STT", "OnDeviceVoice support check failed", e)
                onDeviceEngineBroken = true
                runCatching { probe.destroy() }
                startNetwork()
            }
        }

        mainHandler.post { startSession(forceNetwork = false) }

        continuation.invokeOnCancellation {
            cancelled = true
            watchdog.let { mainHandler.removeCallbacks(it) }
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

    private fun buildIntent(): Intent =
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
        }

    private fun createNetwork(): SpeechRecognizer? =
        try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            Log.w("STT", "OnDeviceVoice create network recognizer failed", e)
            null
        }

    /**
     * True when [requested] (a BCP-47 tag such as "tr" or "tr-TR") matches an
     * installed on-device language, either exactly or by language subtag. A
     * null request means "trust the engine" — no filter.
     */
    private fun localeInstalled(requested: String?, installed: List<String>): Boolean {
        if (requested == null) return true
        val language = requested.substringBefore('-')
        return installed.any {
            it.equals(requested, ignoreCase = true) ||
                it.substringBefore('-').equals(language, ignoreCase = true)
        }
    }
}
