package com.omersusin.pitube.innertube.pages

import com.omersusin.pitube.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
