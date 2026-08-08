package com.omersusin.pitube.data

data class VideoItem(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploaderName: String,
    val uploaderAvatar: String?,
    val uploaderUrl: String? = null,
    val duration: Int,
    val views: Long,
    val uploadedDate: String?,
    val isShort: Boolean = false
) {
    val videoId: String get() = url.substringAfter("watch?v=").substringBefore("&")
    val id: String get() = videoId
    val safeThumb: String get() = thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
}
