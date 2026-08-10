package com.omersusin.pitube.innertube.pages

import com.omersusin.pitube.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
