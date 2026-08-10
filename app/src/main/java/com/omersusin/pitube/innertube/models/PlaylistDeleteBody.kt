package com.omersusin.pitube.innertube.models.body

import com.omersusin.pitube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistDeleteBody(
    val context: Context,
    val playlistId: String
)
