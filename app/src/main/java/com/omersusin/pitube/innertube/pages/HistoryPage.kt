package com.omersusin.pitube.innertube.pages

import com.omersusin.pitube.data.model.Video

data class HistoryPage(
    val videos: List<Video>,
    val continuation: String?,
)