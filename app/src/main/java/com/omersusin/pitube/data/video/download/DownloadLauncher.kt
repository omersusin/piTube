package com.omersusin.pitube.data.video.download

import android.content.Context
import android.util.Log
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.video.downloader.FlowDownloadService
import com.omersusin.pitube.ui.screens.player.util.VideoPlayerUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Single entry point for every download start. Because ALL download surfaces
 * route through this launcher, the "sheet-first" rule (the only way to start a
 * download is to open the sheet and confirm) and last-choice persistence stay
 * consistent app-wide.
 *
 * Pure JVM-compilable shim over [FlowDownloadService]; keeps the stream-heavy
 * selection logic out of the Android Service.
 */
object DownloadLauncher {

    private const val TAG = "DownloadLauncher"
    private val prefsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Starts a foreground download for the given frozen [plan].
     * Persists the last-used choices (height/codec/type/audio label) for the
     * next sheet's defaults.
     */
    fun start(context: Context, plan: DownloadPlan) {
        try {
            VideoPlayerUtils.promptStoragePermissionIfNeeded(context)
        } catch (e: Exception) {
            Log.w(TAG, "storage permission prompt failed (non-fatal)", e)
        }

        val video = plan.video
        val label = plan.qualityLabel

        if (plan.isAudioOnly) {
            FlowDownloadService.startDownload(
                context = context,
                video = video,
                url = plan.videoUrl ?: "",
                quality = label,
                audioOnly = true,
                audioExtension = plan.audioExtension,
                audioMimeType = plan.audioMimeType,
                userAgent = null,
                videoCodec = null,
                threads = plan.threads,
            )
        } else {
            FlowDownloadService.startDownload(
                context = context,
                video = video,
                url = plan.videoUrl ?: "",
                quality = label,
                audioUrl = plan.audioUrl,
                audioOnly = false,
                userAgent = null,
                videoCodec = plan.videoCodec,
                audioExtension = plan.audioExtension,
                audioMimeType = plan.audioMimeType,
                threads = plan.threads,
                fallbackUrl = plan.fallbackUrl,
                fallbackAudioUrl = plan.fallbackAudioUrl,
                fallbackCodec = plan.fallbackCodec,
                fallbackQuality = plan.fallbackQuality,
            )
        }

        persistLastChoice(context, plan)
    }

    private fun persistLastChoice(context: Context, plan: DownloadPlan) {
        prefsScope.launch {
            try {
                val prefs = PlayerPreferences(context)
                if (plan.isAudioOnly) {
                    prefs.setLastDownloadAudioChoice(plan.qualityLabel)
                } else {
                    DownloadPlanner.parseHeightFromQuality(plan.qualityLabel)?.let { height ->
                        prefs.setLastDownloadVideoChoice(height, plan.videoCodec ?: "h264")
                    }
                }
                prefs.setDownloadThreads(plan.threads)
            } catch (e: Exception) {
                Log.w(TAG, "failed to persist last download choice", e)
            }
        }
    }
}