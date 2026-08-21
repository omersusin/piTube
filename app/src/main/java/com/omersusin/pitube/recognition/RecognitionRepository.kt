package com.omersusin.pitube.recognition

import android.content.Context
import android.util.Log
import com.omersusin.pitube.data.local.RecognitionPreferences
import com.omersusin.pitube.data.local.RecognitionProvider
import com.omersusin.pitube.data.local.RecognitionFailureType
import com.omersusin.pitube.data.local.SttApiKeyStore
import com.omersusin.pitube.data.local.SttProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Orchestrates both recognition modes:
 *
 * - Voice: the selected STT provider (Cihaz STT by default — Android's
 *   on-device `SpeechRecognizer`, zero configuration). The cloud providers
 *   (Groq / IBM Watson / Azure / Google Cloud) each use the person's own API
 *   key from Settings; if any of them fails the request falls back to Cihaz
 *   STT for that attempt. "Which path served each request" is logged.
 *
 * - Song: records ~12 s, fingerprints it (Shazam format), sends it to the
 *   configured provider (Shazam default, then AudD/ACRCloud with the
 *   build-time keys from local.properties), and applies the fallback
 *   policy — recording saved locally on failure, with automatic retry on
 *   reconnect when the policy says so.
 */
class RecognitionRepository(
    private val context: Context,
) {
    private val preferences = RecognitionPreferences(context)
    private val capturer = MicAudioCapturer()

    /**
     * The single on-device recognizer, held for the process and reused across
     * voice sessions. Ownership is what lets us serialize/tear down: a fresh
     * recognizer per call can never cooperate with a previous session that is
     * still bound to the recognition service, which is how
     * `ERROR_RECOGNIZER_BUSY` leaks across reopen.
     */
    private val voiceRecognizer = OnDeviceVoiceRecognizer(context)

    companion object {
        const val VOICE_RECORDING_MS = 8_000L
        const val SONG_RECORDING_MS = 12_000L
        const val SONG_PROGRESSIVE_WINDOW_MS = 4_000L
        const val SONG_PROGRESSIVE_WINDOWS = 3

        /**
         * After the person stops talking, end the voice capture this quickly
         * instead of recording the full [VOICE_RECORDING_MS]. Long enough to
         * survive a mid-sentence pause, short enough that a finished query is
         * transcribed (and the modal resolves) almost immediately.
         */
        const val VOICE_STOP_AFTER_SILENCE_MS = 700L
    }

    /**
     * Picks and runs the voice path that will actually work:
     *
     * 1. Cihaz STT: the on-device `SpeechRecognizer` runs the live listening
     *    session directly — its live RMS levels are forwarded through
     *    [onLevel] so the talking face keeps animating.
     * 2. Cloud STT (when selected in Settings): the 12-second capture is
     *    recorded (levels forwarded live) and sent as a WAV to the provider.
     *    On failure, the request automatically falls back to the on-device
     *    recognizer so the person is never dead-ended.
     *
     * Both paths may throw [RecognitionException] (empty transcript, no
     * speech, recognizer unavailable, network failure). The caller decides
     * whether to surface the error or retry. The returned [VoiceRecognitionSource]
     * records which service actually produced the transcript.
     */
    suspend fun recognizeVoice(
        interrupted: () -> Boolean = { false },
        onLevel: (Float) -> Unit = {},
        onProcessing: () -> Unit = {},
    ): Pair<String, VoiceRecognitionSource> = withContext(Dispatchers.Default) {
        val provider = preferences.sttProvider.first()
        var cloudFailure: RecognitionException? = null
        if (provider.isCloud) {
            try {
                val captured = capturer.record(VOICE_RECORDING_MS, interrupted, onLevel, VOICE_STOP_AFTER_SILENCE_MS)
                if (interrupted()) {
                    throw CancellationException("Voice recognition cancelled")
                }
                onProcessing()
                val transcript = CloudSpeechToText.transcribe(context, provider, captured.wavBytes)
                Log.d("STT", "Voice served by cloud STT ($provider)")
                return@withContext transcript to provider.toSource()
            } catch (e: RecognitionException) {
                cloudFailure = e
                Log.w("STT", "Cloud STT ($provider) failed (${e.type}), trying on-device fallback: ${e.message}")
            }
        } else {
            Log.d("STT", "Using on-device speech recognizer (sttProvider=$provider)")
        }
        val transcript =
            try {
                voiceRecognizer.listen(onLevel)
            } catch (e: RecognitionException) {
                // If the cloud attempt failed too, surface the cloud error as the
                // root cause so the UI shows e.g. "Groq: HTTP 401" rather than a
                // generic busy/offline message from the fallback engine.
                if (cloudFailure != null) {
                    Log.w("STT", "On-device fallback also failed (${e.message}); surfacing cloud root cause")
                    throw cloudFailure
                }
                // Cihaz STT: the on-device engine (and its network fallback) failed.
                // When a Groq key is configured, retry the request through Groq as a
                // last resort so a device with an unusable system recognizer is never
                // dead-ended — otherwise surface the recognizer error.
                if (provider == SttProvider.CIHAZ &&
                    !SttApiKeyStore(context).getApiKey(SttProvider.GROQ).isNullOrBlank()
                ) {
                    Log.w("STT", "On-device STT failed (${e.message}); falling back to Groq")
                    if (interrupted()) throw CancellationException("Voice recognition cancelled")
                    val captured = capturer.record(VOICE_RECORDING_MS, interrupted, onLevel, VOICE_STOP_AFTER_SILENCE_MS)
                    if (interrupted()) throw CancellationException("Voice recognition cancelled")
                    onProcessing()
                    val groqTranscript = CloudSpeechToText.transcribe(context, SttProvider.GROQ, captured.wavBytes)
                    Log.d("STT", "Voice served by Groq fallback after on-device STT failure")
                    return@withContext groqTranscript to VoiceRecognitionSource.GROQ
                }
                throw e
            }
        transcript to VoiceRecognitionSource.ON_DEVICE
    }

    /** Full song-recognition pass withFallback policy applied — progressive like Audile. */
    suspend fun recognizeSong(
        interrupted: () -> Boolean = { false },
        onLevel: (Float) -> Unit = {},
    ): SongRecognitionOutcome = withContext(Dispatchers.IO) {
        val provider = preferences.provider.first()
        val allPcm = mutableListOf<Short>()
        val allLevels = mutableListOf<Float>()
        var capturedWav: ByteArray? = null
        var lastError: RecognitionException? = null

        for (window in 0 until SONG_PROGRESSIVE_WINDOWS) {
            if (interrupted()) break
            val captured = capturer.record(SONG_PROGRESSIVE_WINDOW_MS, interrupted, onLevel)
            if (captured.pcm.isEmpty()) continue
            allPcm.addAll(captured.pcm.toList())
            allLevels.addAll(captured.levels)
            capturedWav = captured.wavBytes

            val pcmSlice = allPcm.toShortArray()
            val attempt: Result<TrackMatch> = runCatching {
                when (provider) {
                    RecognitionProvider.SHAZAM -> {
                        val g = ShazamSignatureGenerator()
                        g.feedPcm16Mono(pcmSlice)
                        val sig = g.nextSignatureOrNull()
                            ?: throw RecognitionException(RecognitionFailureType.NO_MATCH, "No signature")
                        ShazamRecognizer.recognize(sig.uri, sig.sampleDurationMs)
                    }
                    RecognitionProvider.AUDD -> {
                        val wav = encodeWavFromPcm(pcmSlice)
                        AuddRecognizer.recognize(wav)
                    }
                    RecognitionProvider.ACRCLOUD -> {
                        val wav = encodeWavFromPcm(pcmSlice)
                        AcrCloudRecognizer.recognize(wav)
                    }
                }
            }
            val match = attempt.getOrNull()
            if (match != null) {
                Log.i("Recognition", "Song matched at window ${window + 1}/${SONG_PROGRESSIVE_WINDOWS} via $provider: ${match.title} — ${match.artist}")
                return@withContext SongRecognitionOutcome.Matched(match)
            }
            val err = attempt.exceptionOrNull() as? RecognitionException
            if (err != null) {
                if (err.type == RecognitionFailureType.BAD_CONNECTION) {
                    lastError = err
                    break
                }
                lastError = err
            }
            if (interrupted()) break
        }

        if (allPcm.isEmpty()) {
            return@withContext handleFailure(provider, lastError ?: RecognitionException(RecognitionFailureType.NO_MATCH, "No audio captured"), ByteArray(0))
        }
        val fullPcm = allPcm.toShortArray()
        val fullWav = capturedWav ?: encodeWavFromPcm(fullPcm)
        if (lastError?.type == RecognitionFailureType.BAD_CONNECTION) {
            return@withContext handleFailure(provider, lastError!!, fullWav)
        }
        val final = runCatching {
            when (provider) {
                RecognitionProvider.SHAZAM -> recognizeWithShazam(fullPcm)
                RecognitionProvider.AUDD -> AuddRecognizer.recognize(fullWav)
                RecognitionProvider.ACRCLOUD -> AcrCloudRecognizer.recognize(fullWav)
            }
        }
        val m = final.getOrNull()
        if (m != null) {
            Log.i("Recognition", "Song matched on final pass via $provider: ${m.title} — ${m.artist}")
            return@withContext SongRecognitionOutcome.Matched(m)
        }
        val e = (final.exceptionOrNull() as? RecognitionException) ?: lastError ?: RecognitionException(RecognitionFailureType.NO_MATCH, "No match found")
        handleFailure(provider, e, fullWav)
    }

    private fun encodeWavFromPcm(pcm: ShortArray): ByteArray {
        val pcmBytes = ByteArray(pcm.size * 2)
        val bb = java.nio.ByteBuffer.wrap(pcmBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (s in pcm) bb.putShort(s)
        return encodeWav(pcmBytes, 16000)
    }

    private fun encodeWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(pcm.size + 44)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.writeLittleInt(36 + pcm.size)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.writeLittleInt(16)
        out.writeLittleShort(1)
        out.writeLittleShort(1)
        out.writeLittleInt(sampleRate)
        out.writeLittleInt(sampleRate * 2)
        out.writeLittleShort(2)
        out.writeLittleShort(16)
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.writeLittleInt(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }

    private fun java.io.ByteArrayOutputStream.writeLittleInt(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private fun java.io.ByteArrayOutputStream.writeLittleShort(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    /** Recognition for an already-captured clip (used by offline retry too). */
    suspend fun recognizeCapturedSong(captured: CapturedAudio): SongRecognitionOutcome {
        val provider = preferences.provider.first()
        try {
            val match =
                when (provider) {
                    RecognitionProvider.SHAZAM -> recognizeWithShazam(captured.pcm)
                    RecognitionProvider.AUDD -> AuddRecognizer.recognize(captured.wavBytes)
                    RecognitionProvider.ACRCLOUD -> AcrCloudRecognizer.recognize(captured.wavBytes)
                }
            Log.i("Recognition", "Song matched via ${provider}: ${match.title} — ${match.artist}")
            return SongRecognitionOutcome.Matched(match)
        } catch (e: RecognitionException) {
            return handleFailure(provider, e, captured.wavBytes)
        }
    }

    private suspend fun recognizeWithShazam(pcm: ShortArray): TrackMatch {
        val generator = ShazamSignatureGenerator()
        generator.feedPcm16Mono(pcm)
        val signature =
            generator.nextSignatureOrNull()
                ?: throw RecognitionException(RecognitionFailureType.NO_MATCH, "No signature generated")
        return ShazamRecognizer.recognize(signature.uri, signature.sampleDurationMs)
    }

    private suspend fun handleFailure(
        provider: RecognitionProvider,
        e: RecognitionException,
        wavBytes: ByteArray,
    ): SongRecognitionOutcome {
        val policy = preferences.fallbackState().forType(e.type)
        val saved = if (policy.savesRecording) {
            RecognitionSamplesStore.saveSample(context, provider, wavBytes) != null
        } else {
            false
        }
        return when (e.type) {
            RecognitionFailureType.NO_MATCH ->
                SongRecognitionOutcome.NoMatch(recordingSaved = saved)

            RecognitionFailureType.BAD_CONNECTION -> {
                val retryScheduled = policy.retries && saved
                Log.i("Recognition", "Bad connection: saved=$saved retryScheduled=$retryScheduled")
                SongRecognitionOutcome.Failed(
                    type = e.type,
                    message = e.message ?: "Network unavailable",
                    recordingSaved = saved,
                    retryScheduled = retryScheduled,
                )
            }

            RecognitionFailureType.OTHER ->
                SongRecognitionOutcome.Failed(
                    type = e.type,
                    message = e.message ?: "Recognition failed",
                    recordingSaved = saved,
                )
        }
    }

    fun stopRecording() {
        capturer.stop()
    }

    /**
     * Stops the recording and tears down any in-flight on-device recognizer so
     * no session is left bound to the recognition service. Call this whenever
     * a voice session is cancelled, dismissed or restarted — otherwise the next
     * session binds while the old one still owns the service and gets
     * `ERROR_RECOGNIZER_BUSY`.
     */
    fun stopVoiceRecognition() {
        stopRecording()
        voiceRecognizer.cancel()
    }
}