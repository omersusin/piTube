package com.omersusin.pitube.data

import com.google.gson.annotations.SerializedName

data class VideoItem(
    @SerializedName("url") val url: String,
    @SerializedName("title") val title: String,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String?,
    @SerializedName("uploaderName") val uploaderName: String,
    @SerializedName("uploaderAvatar") val uploaderAvatar: String?,
    @SerializedName("duration") val duration: Int,
    @SerializedName("views") val views: Long,
    @SerializedName("uploadedDate") val uploadedDate: String?,
    @SerializedName("isShort") val isShort: Boolean = false
) {
    val videoId: String get() = url.substringAfter("v=")
    val safeThumb: String get() = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
}
