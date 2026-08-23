package com.omersusin.pitube.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

fun Player.togglePlayPause() {
    if (!playWhenReady && playbackState == Player.STATE_IDLE) {
        prepare()
    }
    playWhenReady = !playWhenReady
}

val Player.mediaItems: List<MediaItem>
    get() = object : AbstractList<MediaItem>() {
        override val size: Int
            get() = mediaItemCount

        override fun get(index: Int): MediaItem = getMediaItemAt(index)
    }

val Player.isPlayable: Boolean
    get() = playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED
