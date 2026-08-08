package com.omersusin.pitube.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class VoteInfo(val likes: Int, val dislikes: Int)

interface RydService {
    @GET("votes")
    suspend fun getVotes(@Query("videoId") videoId: String): VoteInfo

    companion object {
        fun create(): RydService = Retrofit.Builder()
            .baseUrl("https://returnyoutubedislikeapi.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RydService::class.java)
    }
}
