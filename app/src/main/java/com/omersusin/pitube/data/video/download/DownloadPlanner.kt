package com.omersusin.pitube.data.video.download

import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.player.stream.VideoCodecUtils
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Builds [DownloadPlan]s from the live streams and current preferences.
 *
 * Every download path (player dialog, compact dialog, home-feed quick actions,
 * playlist, SABR retry) must go through this planner so the default selection is
 * deterministic and CDN-gating-safe.
 *
 * Default rule (fixes the "failed at 0% with 403" bug): prefer the best
 * compatible *muxed* (progressive) MP4 — h264 video + aac audio in one stream
 * — whenever one exists at or below the target height. DASH (video-only) is
 * only used as a fallback, ordered AV1 dead-last because YouTube CDN-gates
 * AV1 itags.
 */
object DownloadPlanner {

    const val CODEC_H264 = "h264"
    const val CODEC_VP9 = "vp9"
    const val CODEC_AV1 = "av1"
    const val CODEC_VP8 = "vp8"
    const val CODEC_HEVC = "hevc"

    /** Lower rank = preferred when CDN-gating is a risk. */
    val CODEC_PRIORITY = mapOf(
        CODEC_H264 to 0,
        CODEC_VP9 to 1,
        CODEC_HEVC to 2,
        CODEC_VP8 to 3,
        CODEC_AV1 to 4,
    )

    data class VideoCandidate(
        val stream: VideoStream,
        val height: Int,
        val codecKey: String,
        val isMuxed: Boolean,
    )

    data class PlannerInput(
        val videoStreams: List<VideoStream>,
        val audioStreams: List<AudioStream>,
        val preferredAudioLanguage: String? = null,
        val targetHeight: Int,
    ) {
        private val candidates: List<VideoCandidate> by lazy(LazyThreadSafetyMode.NONE) {
            videoStreams
                .filter { it.getContent().isNotBlank() }
                .map { s ->
                    VideoCandidate(
                        stream = s,
                        height = VideoCodecUtils.qualityHeightFromStream(s),
                        codecKey = VideoCodecUtils.codecKeyFromStream(s),
                        isMuxed = !s.isVideoOnly(),
                    )
                }
        }

        val allCandidates: List<VideoCandidate> get() = candidates
    }

    /**
     * Picks the default video stream.
     *
     * 1. Muxed progressive MP4 (h264, not video-only) at or below target.
     * 2. Any muxed stream at or below target (prefer h264 > hevc > vp9 > vp8 > av1).
     * 3. DASH video-only at or below target with a compatible audio stream
     *    (prefer h264 container; avoid AV1).
     * 4. Lowest height muxed stream, or the lowest video-only with compatible audio.
     */
    fun defaultVideoPick(input: PlannerInput): VideoCandidate? {
        val candidates = input.allCandidates
        if (candidates.isEmpty()) return null

        fun atOrBelowTarget(c: VideoCandidate) =
            input.targetHeight <= 0 || c.height <= input.targetHeight

        val progressiveMp4 = candidates.filter { it.isMuxed && it.codecKey == CODEC_H264 && atOrBelowTarget(it) }
        if (progressiveMp4.isNotEmpty()) {
            return progressiveMp4.maxByOrNull { it.height }
        }

        val anyMuxed = candidates.filter { it.isMuxed && atOrBelowTarget(it) }
        if (anyMuxed.isNotEmpty()) {
            return anyMuxed.sortedWith(
                compareByDescending<VideoCandidate> { it.height }
                    .thenBy { CODEC_PRIORITY[it.codecKey] ?: 99 }
            ).first()
        }

        val dash = candidates.filter { !it.isMuxed && atOrBelowTarget(it) }
        if (dash.isNotEmpty()) {
            val withAudio = dash.filter {
                pickAudio(input.audioStreams, it.codecKey, input.preferredAudioLanguage) != null
            }
            val pool = withAudio.ifEmpty { dash }
            return pool.sortedWith(
                compareByDescending<VideoCandidate> { it.height }
                    .thenBy { CODEC_PRIORITY[it.codecKey] ?: 99 }
            ).first()
        }

        return candidates.minWithOrNull(
            compareBy<VideoCandidate> { it.height }
                .thenBy { CODEC_PRIORITY[it.codecKey] ?: 99 }
        )?.takeIf {
            it.isMuxed || pickAudio(input.audioStreams, it.codecKey, input.preferredAudioLanguage) != null
        }
    }

    /**
     * Picks a compatible audio stream for a given container/codec.
     * MP4 containers (h264/hevc) want AAC; webm (vp9/av1/vp8) wants OPUS.
     */
    fun pickAudio(
        allAudio: List<AudioStream>,
        videoCodecKey: String,
        preferredLang: String?,
    ): AudioStream? {
        if (allAudio.isEmpty()) return null
        val isMp4Container = videoCodecKey == CODEC_H264 || videoCodecKey == CODEC_HEVC

        val langFiltered = filterLanguage(allAudio, preferredLang)

        return if (isMp4Container) {
            langFiltered.filter { isAacCompatible(it) }.maxByOrNull { it.bitrate }
                ?: allAudio.filter { isAacCompatible(it) }.maxByOrNull { it.bitrate }
        } else {
            langFiltered.filter { isOpusCompatible(it) }.maxByOrNull { it.bitrate }
                ?: allAudio.filter { isOpusCompatible(it) }.maxByOrNull { it.bitrate }
                ?: langFiltered.maxByOrNull { it.bitrate }
                ?: allAudio.maxByOrNull { it.bitrate }
        }
    }

    /**
     * Picks the best audio-only stream for an audio-only download.
     */
    fun defaultAudioPick(
        allAudio: List<AudioStream>,
        preferredLang: String? = null,
    ): AudioStream? {
        if (allAudio.isEmpty()) return null
        val langFiltered = filterLanguage(allAudio, preferredLang)
        return langFiltered.maxByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }
            ?: allAudio.maxByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }
    }

    /**
     * Builds a [DownloadPlan] for the chosen video stream, attaching a
     * compatible audio stream when the video is video-only (DASH).
     * For AV1, a non-AV1 fallback at the same height is attached so the service
     * can re-run without the CDN 403 gate.
     */
    fun videoPlan(
        video: Video,
        input: PlannerInput,
        candidate: VideoCandidate,
        threads: Int = 3,
    ): DownloadPlan {
        val stream = candidate.stream
        val codecKey = candidate.codecKey
        val qualityLabel = "${VideoCodecUtils.codecLabelFromKey(codecKey)} ${candidate.height}p"

        var audioUrl: String? = null
        if (!candidate.isMuxed) {
            audioUrl = pickAudio(input.audioStreams, codecKey, input.preferredAudioLanguage)
                ?.getContent()?.takeIf { it.isNotBlank() }
        }

        val fallback = fallbackForCandidate(input, candidate)

        return DownloadPlan(
            video = video,
            mode = DownloadMode.VIDEO,
            qualityLabel = qualityLabel,
            videoUrl = stream.getContent().takeIf { it.isNotBlank() },
            audioUrl = audioUrl,
            videoCodec = if (codecKey == CODEC_AV1 || codecKey == CODEC_VP9 || codecKey == CODEC_VP8) codecKey else null,
            threads = threads,
            fallbackUrl = fallback?.first?.getContent()?.takeIf { it.isNotBlank() },
            fallbackAudioUrl = fallback?.second?.getContent()?.takeIf { it.isNotBlank() },
            fallbackCodec = fallback?.third,
            fallbackQuality = fallback?.let {
                "${VideoCodecUtils.codecLabelFromKey(it.third)} ${candidate.height}p"
            },
        )
    }

    private fun fallbackForCandidate(
        input: PlannerInput,
        candidate: VideoCandidate,
    ): Triple<VideoStream, AudioStream?, String>? {
        if (candidate.codecKey != CODEC_AV1) return null

        val sameHeight = input.allCandidates.filter {
            it.codecKey != CODEC_AV1 && it.height == candidate.height
        }.sortedWith(
            compareBy { CODEC_PRIORITY[it.codecKey] ?: 99 }
        )
        val fbCandidate = sameHeight.firstOrNull() ?: return null
        val fbAudio = if (fbCandidate.isMuxed) null
        else pickAudio(input.audioStreams, fbCandidate.codecKey, input.preferredAudioLanguage)
            ?.takeIf { it.getContent().isNotBlank() }
        if (!fbCandidate.isMuxed && fbAudio == null) return null
        return Triple(fbCandidate.stream, fbAudio, fbCandidate.codecKey)
    }

    private fun filterLanguage(
        allAudio: List<AudioStream>,
        preferredLang: String?,
    ): List<AudioStream> {
        if (preferredLang.isNullOrEmpty() || preferredLang == "original") {
            val originals = allAudio.filter { it.audioTrackType == AudioTrackType.ORIGINAL }
            if (originals.isNotEmpty()) return originals
            val nonDubbed = allAudio.filter { it.audioTrackType != AudioTrackType.DUBBED }
            return nonDubbed.ifEmpty { allAudio }
        }
        val matches = allAudio.filter {
            it.audioLocale?.language.equals(preferredLang, ignoreCase = true) ||
                it.audioLocale?.toLanguageTag().equals(preferredLang, ignoreCase = true)
        }
        return matches.ifEmpty { allAudio }
    }

    private fun isAacCompatible(a: AudioStream): Boolean {
        val fmt = (a.format?.name ?: "").lowercase()
        val mime = (a.format?.mimeType ?: "").lowercase()
        return !fmt.contains("opus") && !fmt.contains("vorbis") &&
            !fmt.contains("webm") && !mime.contains("opus") &&
            !mime.contains("vorbis") && !mime.contains("webm")
    }

    private fun isOpusCompatible(a: AudioStream): Boolean {
        val fmt = a.format?.name ?: ""
        val mime = a.format?.mimeType ?: ""
        return fmt.contains("webm", true) || mime.contains("audio/webm", true) ||
            fmt.contains("opus", true) || mime.contains("opus", true)
    }

    /** Extract an integer height from a quality label like "h264 720p" or "720p". */
    fun parseHeightFromQuality(quality: String): Int? =
        Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE)
            .find(quality)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
}