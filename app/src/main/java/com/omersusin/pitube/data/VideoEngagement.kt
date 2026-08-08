package com.omersusin.pitube.data

data class VideoEngagement(
    val videoId: String,
    val likeCount: String?,
    val likeStatus: LikeStatus,
    val channelId: String?,
    val isSubscribed: Boolean,
    val subscriberCountText: String?,
    val commentsToken: String?
)

enum class LikeStatus { LIKE, DISLIKE, INDIFFERENT }
