package com.omersusin.pitube.service

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.omersusin.pitube.data.PlayerHolder

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        val player = PlayerHolder.getPlayer(this)
        mediaSession = MediaSession.Builder(this, player).setId("piTube_Media_Session").build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
    override fun onDestroy() {
        mediaSession?.run { release() }
        super.onDestroy()
    }
}
