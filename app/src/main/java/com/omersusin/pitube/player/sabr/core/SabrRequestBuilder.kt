package com.omersusin.pitube.player.sabr.core

import com.omersusin.pitube.player.sabr.proto.ClientAbrState
import com.omersusin.pitube.player.sabr.proto.ClientInfo
import com.omersusin.pitube.player.sabr.proto.StreamerContext
import com.omersusin.pitube.player.sabr.proto.VideoPlaybackAbrRequest

object SabrRequestBuilder {

    fun buildInitialRequest(state: SabrSessionState): ByteArray =
        buildRequest(state, isInitial = true)

    fun buildFollowUpRequest(state: SabrSessionState): ByteArray =
        buildRequest(state, isInitial = false)

    private fun buildRequest(state: SabrSessionState, isInitial: Boolean): ByteArray {
        state.requestSequence++

        val playheadMs = state.playheadPositionMs
        // Selected formats MUST go out on every request, including the initial
        // one and the first post-seek follow-up (formats are known from the
        // player response long before FormatInitialization arrives). Gating
        // them behind initializedFormats sent an empty selection, which the
        // server reads as a fresh session and answers with a stream starting
        // at t=0 regardless of playerTimeMs — the lyric-tap / quality-switch /
        // reload-resume "rewinds to beginning" root cause.
        val selected = listOfNotNull(
            state.selectedVideoFormatId.takeIf { state.selectedVideoItag > 0 },
            state.selectedAudioFormatId.takeIf { state.selectedAudioItag > 0 }
        )
        val buffered = if (isInitial) emptyList() else (state.videoBufferedRanges + state.audioBufferedRanges)
        val timeSinceSeekMs = if (state.lastSeekAtMs > 0) {
            (System.currentTimeMillis() - state.lastSeekAtMs).coerceAtLeast(0)
        } else 0L

        val includeFollowUpState = playheadMs > 0 || buffered.isNotEmpty()

        val effectiveResolution =
            (if (state.stickyResolution > 0) state.stickyResolution else state.selectedVideoHeight)
                .coerceAtLeast(360)

        val request = VideoPlaybackAbrRequest(
            clientAbrState = ClientAbrState(
                playerTimeMs = playheadMs,
                bandwidthEstimateBps = if (includeFollowUpState) state.estimatedBandwidthBps else 0L,
                viewportWidthPx = if (includeFollowUpState) state.screenWidthPixels else 0,
                viewportHeightPx = if (includeFollowUpState) state.screenHeightPixels else 0,
                lastManualSelectedResolution = if (state.stickyResolution > 0) state.stickyResolution else 0,
                stickyResolution = effectiveResolution,
                timeSinceLastSeekMs = timeSinceSeekMs,
                visibility = state.visibility,
                playbackRate = state.playbackRate,
                enabledTrackTypesBitfield = state.enabledTrackTypes,
                audioTrackId = state.audioTrackId
            ),
            selectedFormatIds = selected,
            bufferedRanges = buffered,
            playerTimeMs = playheadMs,
            videoPlaybackUstreamerConfig = state.ustreamerConfig,
            preferredAudioFormatIds = listOfNotNull(state.selectedAudioFormatId.takeIf { state.selectedAudioItag > 0 }),
            preferredVideoFormatIds = listOfNotNull(state.selectedVideoFormatId.takeIf { state.selectedVideoItag > 0 }),
            streamerContext = StreamerContext(
                clientInfo = ClientInfo(
                    clientName = state.clientNameId,
                    clientVersion = state.clientVersion,
                    osName = state.osName,
                    osVersion = state.osVersion
                ),
                poToken = state.poTokenBytes(),
                playbackCookie = state.playbackCookie,
                sabrContexts = state.activeSabrContexts(),
                unsentSabrContextTypes = state.unsentSabrContextTypes()
            )
        )
        return request.encode()
    }
}
