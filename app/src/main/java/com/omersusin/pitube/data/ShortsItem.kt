package com.omersusin.pitube.data

data class ShortsItem(
    val videoId: String,
    val title: String = "",
    val viewCount: String = "",
    val thumbnailUrl: String? = null,
    val sequenceParams: String? = null
) {
    val portraitThumbnailUrl: String
        get() = thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/frame0.jpg"
}

data class ShortsFeedPage(
    val items: List<ShortsItem>,
    val continuation: String?
)
