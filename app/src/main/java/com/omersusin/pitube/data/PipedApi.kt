package com.omersusin.pitube.data

data class SearchResult(val items: List<VideoItem>)
data class VideoStream(val url: String, val quality: String, val mimeType: String, val videoOnly: Boolean = false)

data class StreamInfo(
    val title: String,
    val description: String,
    val uploader: String,
    val uploaderUrl: String,
    val hls: String?,
    val dash: String?,
    val videoStreams: List<VideoStream> = emptyList(),
    val audioStreams: List<VideoStream> = emptyList(),
    val relatedStreams: List<VideoItem> = emptyList()
)

data class ChannelInfo(val id: String, val name: String, val avatarUrl: String, val relatedStreams: List<VideoItem> = emptyList())
data class Comment(val author: String, val commentText: String, val likes: Long, val commentedTime: String, val authorThumbnail: String)
data class CommentsResponse(val comments: List<Comment>, val nextpage: String? = null)

