package com.omersusin.pitube.data

data class ArtistItem(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val subscriberCount: String? = null,
    val description: String? = null,
    val isVerified: Boolean = false
)

data class PlaylistDisplayItem(
    val name: String,
    val url: String,
    val uploaderName: String,
    val itemCount: Int = -1,
    val thumbnailUrl: String? = null,
    val description: String? = null
) {
    val id: String
        get() = when {
            url.contains("list=") -> url.substringAfter("list=").substringBefore("&")
            url.contains("/browse/") -> url.substringAfter("/browse/").substringBefore("?")
            else -> url
        }
}
