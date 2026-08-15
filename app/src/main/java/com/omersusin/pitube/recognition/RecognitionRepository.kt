package com.omersusin.pitube.recognition

import android.content.Context
import android.util.Log
import com.omersusin.pitube.data.local.RecognitionPreferences
import com.omersusin.pitube.data.local.RecognitionProvider
import com.omersusin.pitube.data.local.RecognitionFailureType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Orchestrates both recognition modes:
 *
 * - Voice: Puter guest Whisper transcription first; falls back to the
 *   on-device `SpeechRecognizer` automatically ("which path served each
 *   request" is logged).
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

    companion object {
        const val VOICE_RECORDING_MS = 12_000L
        const val SONG_RECORDING_MS = 12_000L
    }

    /** Records live levels via [onLevel] as it captures. */
    suspend fun recordVoice(
        interrupted: () -> Boolean = { false },
        onLevel: (Float) -> Unit = {},
    ): CapturedAudio = withContext(Dispatchers.IO) {
        capturer.record(VOICE_RECORDING_MS, interrupted, onLevel)
    }

    /**
     * Transcript via Puter if online, else (or on failure) the on-device
     * recognizer. Returns the transcript and which path served it (logged).
     * Live levels from the on-device fallback session are forwarded through
     * [onFallbackLevel] so the listening visual stays animated on that path.
     */
    suspend fun recognizeVoice(
        captured: CapturedAudio,
        onFallbackLevel: (Float) -> Unit = {},
    ): Pair<String, VoiceRecognitionSource> =
        withContext(Dispatchers.Default) {
            val online = hasInternetConnection(context)
            if (online) {
                try {
                    val transcript = PuterSpeechToText.transcribe(captured.wavBytes)
                    Log.d("Recognition", "Voice served by Puter (whisper-1)")
                    return@withContext transcript to VoiceRecognitionSource.PUTER
                } catch (e: RecognitionException) {
                    Log.w("Recognition", "Puter failed (${e.type}), falling back to on-device: ${e.message}")
                }
            } else {
                Log.d("Recognition", "Offline — using on-device speech recognizer")
            }
            val transcript = OnDeviceVoiceRecognizer(context).listen(onFallbackLevel)
            transcript to VoiceRecognitionSource.ON_DEVICE
        }

    /** Full song-recognition pass with fallback policy applied. */
    suspend fun recognizeSong(
        interrupted: () -> Boolean = { false },
        onLevel: (Float) -> Unit = {},
    ): SongRecognitionOutcome = withContext(Dispatchers.IO) {
        val captured = capturer.record(SONG_RECORDING_MS, interrupted, onLevel)
        recognizeCapturedSong(captured)
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
}