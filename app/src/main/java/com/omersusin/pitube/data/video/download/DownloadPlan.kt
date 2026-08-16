package com.omersusin.pitube.data.video.download

import com.omersusin.pitube.data.model.Video

enum class DownloadMode {
    VIDEO,
    AUDIO
}

/**
 * Immutable, frozen snapshot of every option a download needs. Created by
 * [DownloadPlanner] from the live streams + current preferences and handed to
 * [DownloadLauncher] which persists the last-used choices and starts the
 * foreground [com.omersusin.pitube.data.video.downloader.FlowDownloadService].
 *
 * Keeping all options in one value object (a la Seal's `DownloadPreferences`)
 * makes queueing, scheduling and retries trivial — a retry restarts with the
 * exact same plan.
 */
data class DownloadPlan(
    val video: Video,
    val mode: DownloadMode,
    val qualityLabel: String,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val videoCodec: String? = null,
    val audioExtension: String? = null,
    val audioMimeType: String? = null,
    val threads: Int = 3,
    val fallbackUrl: String? = null,
    val fallbackAudioUrl: String? = null,
    val fallbackCodec: String? = null,
    val fallbackQuality: String? = null,
    val embedSubtitles: Boolean = false,
    val subtitleLanguage: String = "",
    val trimStartMs: Long = 0,
    val trimEndMs: Long = 0,
    val splitChapters: Boolean = false,
    val speedLimitBytesPerSec: Long = 0,
    val scheduledAtMs: Long = 0,
) {
    val isAudioOnly: Boolean get() = mode == DownloadMode.AUDIO
}