package com.omersusin.pitube.innertube.pages

import com.omersusin.pitube.data.model.Channel
import com.omersusin.pitube.innertube.models.MusicTwoRowItemRenderer
import com.omersusin.pitube.innertube.models.SectionListRenderer
import kotlinx.serialization.Serializable

/**
 * Typed slice of the WEB_REMIX /browse artist page — only the part needed to
 * extract the "Fans might also like" related-artists carousel. The shelf is
 * found structurally (the one carousel whose items are mostly artists), never
 * by its localized title.
 */
@Serializable
data class MusicArtistResponse(
    val contents: Contents?,
) {
    @Serializable
    data class Contents(val singleColumnBrowseResultsRenderer: SingleColumn?)

    @Serializable
    data class SingleColumn(val tabs: List<Tab>?)

    @Serializable
    data class Tab(val tabRenderer: TabRenderer?)

    @Serializable
    data class TabRenderer(val content: Content?)

    @Serializable
    data class Content(val sectionListRenderer: SectionListRenderer?)
}

fun MusicArtistResponse.toRelatedArtists(): List<Channel> {
    val sections =
        contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?: emptyList()

    var best: List<Channel> = emptyList()
    for (section in sections) {
        val carousel = section.musicCarouselShelfRenderer ?: continue
        val artistItems = carousel.contents.mapNotNull { it.musicTwoRowItemRenderer }.filter { it.isArtist }
        if (artistItems.size < 3) continue
        val channels =
            artistItems.mapNotNull { renderer ->
                val browseId =
                    renderer.navigationEndpoint.browseEndpoint?.browseId
                        ?.takeIf { it.startsWith("UC") }
                        ?: return@mapNotNull null
                Channel(
                    id = browseId,
                    name = renderer.title.runs?.firstOrNull()?.text.orEmpty(),
                    thumbnailUrl =
                        renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                            ?: renderer.thumbnailRenderer.croppedSquareThumbnailRenderer?.getThumbnailUrl()
                            ?: "",
                    subscriberCount = 0,
                    description =
                        renderer.subtitle?.runs?.joinToString(separator = "") { it.text }.orEmpty(),
                    url = "https://www.youtube.com/channel/$browseId",
                )
            }
        if (channels.size > best.size) best = channels
    }
    return best
}

private fun MusicTwoRowItemRenderer.titleText(): String = title.runs?.firstOrNull()?.text.orEmpty()
