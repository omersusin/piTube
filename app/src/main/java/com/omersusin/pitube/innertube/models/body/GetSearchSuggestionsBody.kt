package com.omersusin.pitube.innertube.models.body

import com.omersusin.pitube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)
