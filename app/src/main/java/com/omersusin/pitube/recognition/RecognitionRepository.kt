package com.omersusin.pitube.recognition

import android.content.Context
import android.util.Log
import com.omersusin.pitube.data.local.RecognitionPreferences
import com.omersusin.pitube.data.local.RecognitionProvider
import com.omersusin.pitube.data.local.RecognitionFailureType
import com.omersusin.pitube.data.local.VoiceSourcePreference
import kotlinx.coroutines.CancellationException
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

    /**
     * Picks and runs the voice path that will actually work:
     *
     * 1. Puter guest whisper is used when a guest token can be obtained (a
     *    no-key, no-login cloud transcription). The 12-second capture is
     *    recorded and sent off as a WAV.
     * 2. Otherwise the Android `SpeechRecognizer` runs the live listening
     *    session directly — its live RMS levels are forwarded through
     *    [onLevel] so the talking face keeps animating on this path too.
     *
     * Both paths may throw [RecognitionException] (empty transcript, no
     * speech, recognizer unavailable, network failure). The caller decides
     * whether to surface the error or retry.
     */
    suspend fun recognizeVoice(
        interrupted: () -> Boolean = { false },
        onLevel: (Float) -> Unit = {},
    ): Pair<String, VoiceRecognitionSource> = withContext(Dispatchers.Default) {
        val voiceSource = preferences.voiceSource.first()
        val usePuter =
            voiceSource == VoiceSourcePreference.AUTO && PuterSpeechToText.isGuestAuthAvailable()

        if (usePuter) {
            try {
                val captured = capturer.record(VOICE_RECORDING_MS, interrupted, onLevel)
                if (interrupted()) {
                    throw CancellationException("Voice recognition cancelled")
                }
                val transcript = PuterSpeechToText.transcribe(captured.wavBytes)
                Log.d("Recognition", "Voice served by Puter (whisper-1)")
                return@withContext transcript to VoiceRecognitionSource.PUTER
            } catch (e: RecognitionException) {
                Log.w("Recognition", "Puter failed (${e.type}), falling back to on-device: ${e.message}")
            }
        } else {
            Log.d(
                "Recognition",
                "Using on-device speech recognizer (voiceSource=$voiceSource, puter=${
                    if (voiceSource == VoiceSourcePreference.AUTO) "unavailable" else "disabled"
                })",
            )
        }
        val transcript = OnDeviceVoiceRecognizer(context).listen(onLevel)
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