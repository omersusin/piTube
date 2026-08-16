package com.omersusin.pitube.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omersusin.pitube.data.local.entity.DownloadItemStatus
import com.omersusin.pitube.data.local.entity.DownloadWithItems
import com.omersusin.pitube.data.video.VideoDownloadManager
import com.omersusin.pitube.data.video.DownloadedVideo
import com.omersusin.pitube.data.video.download.DownloadLauncher
import com.omersusin.pitube.data.video.download.DownloadMode
import com.omersusin.pitube.data.video.download.DownloadPlan
import com.omersusin.pitube.data.video.download.DownloadPlanner
import com.omersusin.pitube.data.video.downloader.FlowDownloadService
import com.omersusin.pitube.player.stream.InnerTubeStreamBridge
import com.omersusin.pitube.player.stream.InnerTubeVideoStreamExtractor
import com.omersusin.pitube.ui.screens.player.components.DownloadStreamHelpers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val videoDownloadManager: VideoDownloadManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    /**
     * IDs of items currently being deleted (optimistically hidden from the list).
     */
    private val _pendingDeleteIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * IDs selected via long-press (queue management mode).
     */
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    init {
        observeDownloads()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            combine(
                videoDownloadManager.downloadedVideos,
                _pendingDeleteIds
            ) { videos, pending ->
                videos.filter { it.video.id !in pending }
            }.collect { videos ->
                _uiState.update { it.copy(downloadedVideos = videos) }
            }
        }

        viewModelScope.launch {
            combine(
                videoDownloadManager.allDownloads,
                _pendingDeleteIds
            ) { downloads, pending ->
                val incomplete = downloads.filter { download ->
                    download.download.videoId !in pending &&
                        download.overallStatus != com.omersusin.pitube.data.local.entity.DownloadItemStatus.COMPLETED
                }
                incomplete.filter { !it.isAudioOnly } to incomplete.size
            }.collect { (incomplete, incompleteCount) ->
                _uiState.update { state ->
                    // Auto-clear merging flags for downloads that are no longer active
                    val activeIds = incomplete.map { it.download.videoId }.toSet()
                    state.copy(
                        incompleteVideoDownloads = incomplete,
                        incompleteDownloadCount = incompleteCount,
                        mergingVideoIds = state.mergingVideoIds.intersect(activeIds)
                    )
                }
            }
        }

        viewModelScope.launch {
            videoDownloadManager.progressUpdates.collect { update ->
                _uiState.update { state ->
                    val newMerging = if (update.isMerging) {
                        state.mergingVideoIds + update.videoId
                    } else {
                        state.mergingVideoIds - update.videoId
                    }
                    state.copy(
                        downloadProgressMap = state.downloadProgressMap + (update.videoId to update.progress),
                        mergingVideoIds = newMerging
                    )
                }
            }
        }
    }

    fun deleteVideoDownload(videoId: String) {
        _pendingDeleteIds.update { it + videoId }
        viewModelScope.launch(Dispatchers.IO) {
            val download = videoDownloadManager.getDownloadWithItems(videoId)
            if (download?.overallStatus != com.omersusin.pitube.data.local.entity.DownloadItemStatus.COMPLETED) {
                FlowDownloadService.cancelDownload(appContext, videoId)
                delay(500L)
            }
            videoDownloadManager.deleteDownload(videoId)
            _pendingDeleteIds.update { it - videoId }
        }
    }

    fun pauseVideoDownload(videoId: String) {
        FlowDownloadService.pauseDownload(appContext, videoId)
    }

    fun resumeVideoDownload(videoId: String) {
        FlowDownloadService.resumeDownload(appContext, videoId)
    }

    fun removeIncompleteDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = videoDownloadManager.allDownloads.first()
                .filter { it.overallStatus != com.omersusin.pitube.data.local.entity.DownloadItemStatus.COMPLETED }
                .map { it.download.videoId }
            if (ids.isEmpty()) return@launch

            _pendingDeleteIds.update { it + ids }
            ids.forEach { videoId -> FlowDownloadService.cancelDownload(appContext, videoId) }
            delay(500L)
            videoDownloadManager.deleteIncompleteDownloads()
            _pendingDeleteIds.update { it - ids.toSet() }
        }
    }

    fun rescan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            videoDownloadManager.scanAndRecoverDownloads()
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Queue management (multi-select + bulk actions)
    // ═══════════════════════════════════════════════════════

    fun toggleSelection(videoId: String) {
        _selectedIds.update { if (videoId in it) it - videoId else it + videoId }
    }

    fun clearSelection() {
        _selectedIds.update { emptySet() }
    }

    fun pauseSelected() {
        _selectedIds.value.forEach { pauseVideoDownload(it) }
    }

    fun resumeSelected() {
        _selectedIds.value.forEach { resumeVideoDownload(it) }
    }

    fun cancelSelected() {
        _selectedIds.value.forEach { videoId ->
            FlowDownloadService.cancelDownload(appContext, videoId)
        }
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        clearSelection()
        ids.forEach { deleteVideoDownload(it) }
    }

    fun retrySelected() {
        val ids = _selectedIds.value.toList()
        clearSelection()
        ids.forEach { retryDownload(it) }
    }

    /**
     * Re-resolves the streams for a failed/cancelled download and restarts it
     * with the planner's default pick (URLs may have expired — that is the most
     * common failure cause — so the stored URL is never reused blindly).
     */
    fun retryDownload(videoId: String) {
        viewModelScope.launch {
            val dwi = videoDownloadManager.getDownloadWithItems(videoId) ?: return@launch
            val video = videoDownloadManager.toDownloadedVideo(dwi).video
            val result = withContext(Dispatchers.IO) {
                runCatching { InnerTubeVideoStreamExtractor.extract(videoId) }.getOrNull()
            }
            val videoStreams = InnerTubeStreamBridge.convertVideoFormats(result?.videoFormats.orEmpty())
            val audioStreams = DownloadStreamHelpers.mergeAudioDownloadStreams(
                InnerTubeStreamBridge.convertAudioFormats(result?.audioFormats.orEmpty()),
                emptyList()
            )
            val input = DownloadPlanner.PlannerInput(
                videoStreams = videoStreams,
                audioStreams = audioStreams,
                preferredAudioLanguage = null,
                targetHeight = 0,
            )
            val plan = if (dwi.isAudioOnly) {
                val audio = DownloadPlanner.pickAudio(input.audioStreams, DownloadPlanner.CODEC_H264, null)
                    ?: return@launch
                DownloadPlan(
                    video = video,
                    mode = DownloadMode.AUDIO,
                    qualityLabel = "${DownloadStreamHelpers.audioBitrateKbps(audio)}kbps",
                    videoUrl = audio.getContent().takeIf { it.isNotBlank() },
                    audioExtension = DownloadStreamHelpers.audioFileExtension(audio),
                    audioMimeType = audio.format?.mimeType,
                )
            } else {
                val candidate = DownloadPlanner.defaultVideoPick(input) ?: return@launch
                DownloadPlanner.videoPlan(video, input, candidate, 3)
            }
            DownloadLauncher.start(appContext, plan)
        }
    }
}

data class DownloadsUiState(
    val downloadedVideos: List<DownloadedVideo> = emptyList(),
    val incompleteVideoDownloads: List<DownloadWithItems> = emptyList(),
    val downloadProgressMap: Map<String, Float> = emptyMap(),
    val mergingVideoIds: Set<String> = emptySet(),
    val incompleteDownloadCount: Int = 0,
    val isLoading: Boolean = false,
    val isScanning: Boolean = false
)
