package com.omersusin.pitube.player

import com.omersusin.pitube.data.model.Video

object PlayerRelatedVideosPolicy {
    fun select(
        videoId: String,
        primary: List<Video>,
        fallback: List<Video>,
        current: List<Video>
    ): List<Video> = sequenceOf(primary, fallback, current)
        .map { candidates -> sanitize(videoId, candidates) }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()

    fun sanitize(videoId: String, candidates: List<Video>): List<Video> =
        candidates
            .filter { it.id.isNotBlank() && it.id != videoId }
            .distinctBy { it.id }
}
