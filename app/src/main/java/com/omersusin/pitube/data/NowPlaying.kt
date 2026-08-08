package com.omersusin.pitube.data

import androidx.compose.runtime.mutableStateOf

object NowPlaying {
    val current = mutableStateOf<VideoItem?>(null)
    val showMini = mutableStateOf(false)
}
