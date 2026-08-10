package com.omersusin.pitube.data.model

data class ShortsFeedResponse(
    val videos: List<ShortItem>,
    val nextContinuationToken: String?
)
