package com.omersusin.pitube.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

data class DeArrowTitle(val title: String, val votes: Int)
data class BrandingInfo(val titles: List<DeArrowTitle> = emptyList())

interface DeArrowService {
    @GET("api/branding/{videoId}")
    suspend fun getBranding(@Path("videoId") videoId: String): BrandingInfo

    companion object {
        fun create(): DeArrowService = Retrofit.Builder()
            .baseUrl("https://sponsor.ajay.app/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeArrowService::class.java)
    }
}
