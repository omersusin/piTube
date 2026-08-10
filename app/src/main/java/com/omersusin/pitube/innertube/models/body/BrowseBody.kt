package com.omersusin.pitube.innertube.models.body

import com.omersusin.pitube.innertube.models.Context
import com.omersusin.pitube.innertube.models.Continuation
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: String?,
    val query: String? = null,
    val canonicalBaseUrl: String? = null,
)
