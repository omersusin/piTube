package com.omersusin.pitube.innertube.models.body

import com.omersusin.pitube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class ReelBody(
    val context: Context,
    val params: String? = null,
    val sequenceParams: String? = "CA8%3D", // Default param often used for initial reels fetch
    val continuation: String? = null, // Pagination token for load-more pages
    // Seedless Shorts navigation (Koda pattern for signed-in initial fetch):
    // reel/reel_item_watch requires inputType + disablePlayerResponse.
    // Nulls are omitted (explicitNulls=false), so existing sequence calls are unaffected.
    val inputType: String? = null,
    val disablePlayerResponse: Boolean? = null,
)
