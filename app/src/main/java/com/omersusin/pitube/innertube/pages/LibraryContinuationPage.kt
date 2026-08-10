package com.omersusin.pitube.innertube.pages

import com.omersusin.pitube.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
