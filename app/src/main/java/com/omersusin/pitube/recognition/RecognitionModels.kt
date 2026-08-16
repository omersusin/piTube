package com.omersusin.pitube.recognition

import com.omersusin.pitube.data.local.RecognitionFailureType

/**
 * A track identified by a song-recognition provider (Shazam/AudD/ACRCloud).
 * Only the fields piTube actually uses are surfaced.
 */
data class TrackMatch(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val coverArtUrl: String? = null,
    val isrc: String? = null,
    val youtubeVideoId: String? = null,
    val appleMusicUrl: String? = null,
    val spotifyUrl: String? = null,
    val sourceProvider: String,
) {
    /** Query fed to piTube's search when the user taps "Ara". */
    val searchQuery: String
        get() = listOf(artist, title).filter { it.isNotBlank() }.distinct().joinToString(" ")
}

class RecognitionException(
    val type: RecognitionFailureType,
    message: String,
) : Exception(message)

/**
 * Result of a song-recognition attempt after the fallback policy has been
 * applied by [SongRecognitionRepository].
 */
sealed interface SongRecognitionOutcome {
    data class Matched(val track: TrackMatch) : SongRecognitionOutcome

    data class NoMatch(val recordingSaved: Boolean) : SongRecognitionOutcome

    data class Failed(
        val type: RecognitionFailureType,
        val message: String,
        val recordingSaved: Boolean = false,
        val retryScheduled: Boolean = false,
    ) : SongRecognitionOutcome
}

/**
 * Continuously recorded PCM (16 kHz, mono, 16-bit) plus a WAV-encoded copy and
 * a live amplitude history for the waveform UI.
 */
data class CapturedAudio(
    val pcm: ShortArray,
    val wavBytes: ByteArray,
    val durationMs: Long,
    val levels: List<Float>,
)

/**
 * How a voice-mode transcript was obtained (for the debug log required by the
 * spec: which path served each request). On-device is the zero-config default
 * and the automatic fallback; the cloud providers are selected in Settings.
 */
enum class VoiceRecognitionSource {
    ON_DEVICE,
    GROQ,
    IBM_WATSON,
    AZURE,
    GOOGLE_CLOUD,
}