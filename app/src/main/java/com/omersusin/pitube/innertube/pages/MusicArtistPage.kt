package com.omersusin.pitube.innertube.pages

import com.omersusin.pitube.data.model.Channel
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.innertube.models.MusicTwoRowItemRenderer
import com.omersusin.pitube.innertube.models.SectionListRenderer
import kotlinx.serialization.Serializable

/**
 * Typed slice of the WEB_REMIX /browse artist page — extracts the "Fans might
 * also like" related-artists carousel and the music-videos carousel. Shelves
 * are located structurally (by their item types), never by localized titles,
 * so parsing works in any display language. This is ALSO the only content
 * source for auto-generated "- Topic" channels, which have no www tabs at all.
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

data class MusicArtistContent(
    val relatedArtists: List<Channel>,
    val videos: List<Video>,
)

fun MusicArtistResponse.toMusicArtistContent(): MusicArtistContent {
    val sections =
        contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?: emptyList()

    var bestRelated: List<Channel> = emptyList()
    var bestVideos: List<Video> = emptyList()
    for (section in sections) {
        val carousel = section.musicCarouselShelfRenderer ?: continue
        val twoRows = carousel.contents.mapNotNull { it.musicTwoRowItemRenderer }
        if (twoRows.isEmpty()) continue

        val artists =
            twoRows.mapNotNull { renderer ->
                val browseId =
                    renderer.navigationEndpoint.browseEndpoint?.browseId
                        ?.takeIf { it.startsWith("UC") }
                        ?: return@mapNotNull null
                if (!renderer.isArtist) return@mapNotNull null
                Channel(
                    id = browseId,
                    name = renderer.title.runs?.firstOrNull()?.text.orEmpty(),
                    thumbnailUrl = renderer.thumbnailUrl(),
                    subscriberCount = 0,
                    description =
                        renderer.subtitle?.runs?.joinToString(separator = "") { it.text }.orEmpty(),
                    url = "https://www.youtube.com/channel/$browseId",
                )
            }
        // "Fans might also like" is the one carousel dominated by artists.
        if (artists.size > bestRelated.size && artists.size >= twoRows.size / 2) {
            bestRelated = artists
        }

        val videos =
            twoRows.mapNotNull { renderer ->
                val videoId =
                    renderer.navigationEndpoint.watchEndpoint?.videoId
                        ?: return@mapNotNull null
                Video(
                    id = videoId,
                    title = renderer.title.runs?.firstOrNull()?.text.orEmpty(),
                    channelName = "",
                    channelId = "",
                    thumbnailUrl = renderer.thumbnailUrl(),
                    duration = 0,
                    viewCount = 0L,
                    uploadDate =
                        renderer.subtitle?.runs?.joinToString(separator = "") { it.text }.orEmpty(),
                )
            }
        if (videos.size > bestVideos.size) bestVideos = videos
    }
    return MusicArtistContent(relatedArtists = bestRelated, videos = bestVideos)
}

private fun MusicTwoRowItemRenderer.thumbnailUrl(): String =
    thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
        ?: thumbnailRenderer.croppedSquareThumbnailRenderer?.getThumbnailUrl()
        ?: ""
