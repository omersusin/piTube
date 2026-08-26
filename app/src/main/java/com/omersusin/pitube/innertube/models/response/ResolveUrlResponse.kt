package com.omersusin.pitube.innertube.models.response

import kotlinx.serialization.Serializable

/**
 * `/navigation/resolve_url` — turns a handle/vanity URL into the canonical
 * channel id. Adapted from Koda's resolveChannelId (WEB client, anonymous).
 */
@Serializable
data class ResolveUrlResponse(
    val endpoint: Endpoint?,
) {
    @Serializable
    data class Endpoint(val browseEndpoint: BrowseEndpoint?)

    @Serializable
    data class BrowseEndpoint(val browseId: String? = null)
}
