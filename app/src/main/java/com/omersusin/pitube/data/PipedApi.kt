package com.omersusin.pitube.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

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

interface PipedApiService {
    @GET("trending") suspend fun getTrending(@Query("region") region: String = "US"): List<VideoItem>
    @GET("trending/shorts") suspend fun getShorts(@Query("region") region: String = "US"): List<VideoItem>
    @GET("search") suspend fun search(@Query("q") query: String, @Query("filter") filter: String = "videos"): SearchResult
    @GET("streams/{videoId}") suspend fun getStreams(@Path("videoId") videoId: String): StreamInfo
    @GET("channel/{channelId}") suspend fun getChannel(@Path("channelId") channelId: String): ChannelInfo
    @GET("comments/{videoId}") suspend fun getComments(@Path("videoId") videoId: String): CommentsResponse
    @GET("nextpage/comments/{videoId}") suspend fun getNextComments(@Path("videoId") videoId: String, @Query("nextpage") nextPage: String): CommentsResponse

    companion object {
        private const val BASE_URL = "https://pipedapi.adminforge.de/"
        private val client by lazy {
            OkHttpClient.Builder().addInterceptor(PipedFailoverInterceptor())
                .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
        }
        fun create(): PipedApiService = Retrofit.Builder().baseUrl(BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(PipedApiService::class.java)
    }
}
