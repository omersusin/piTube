package com.omersusin.pitube.utils

import com.omersusin.pitube.data.model.Video

object ShortsDetector {
    const val SHORTS_MAX = 60
    const val EXTENDED_MAX = 180

    fun isShort(video: Video): Boolean {
        if (video.isLive) return false
        if (video.isShort) return true
        return video.duration in 1..SHORTS_MAX
    }
}
