package com.omersusin.pitube.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class SponsorSegment(
    val category: String,
    val segment: List<Double>,
    val UUID: String
)

interface SponsorBlockService {
    @GET("api/skipSegments/{videoId}")
    suspend fun getSegments(
        @Path("videoId") videoId: String,
        @Query("categories") categories: String
    ): List<SponsorSegment>

    companion object {
        fun create(): SponsorBlockService = Retrofit.Builder()
            .baseUrl("https://sponsor.ajay.app/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SponsorBlockService::class.java)
    }
}
