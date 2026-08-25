package com.omersusin.pitube.innertube.pages

import com.omersusin.pitube.data.model.Channel
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ARTIST
import com.omersusin.pitube.innertube.models.Continuation
import com.omersusin.pitube.innertube.models.MusicCardShelfRenderer
import com.omersusin.pitube.innertube.models.MusicResponsiveListItemRenderer
import com.omersusin.pitube.innertube.models.MusicShelfRenderer
import com.omersusin.pitube.innertube.models.SectionListRenderer
import com.omersusin.pitube.innertube.utils.parseTime
import kotlinx.serialization.Serializable

/**
 * Typed model of the music.youtube.com /search response (WEB_REMIX).
 * Songs and artists are classified structurally (watch endpoint vs
 * MUSIC_PAGE_TYPE_ARTIST browse config), never by shelf title, so the
 * parse is independent of the account's display language.
 */
@Serializable
data class MusicSearchResponse(
    val contents: MusicSearchContents?,
    val continuationContents: MusicSearchContinuationContents?,
) {
    @Serializable
    data class MusicSearchContents(val tabbedSearchResultsRenderer: TabbedResults?)

    @Serializable
    data class TabbedResults(val tabs: List<Tab>?)

    @Serializable
    data class Tab(val tabRenderer: TabRenderer?)

    @Serializable
    data class TabRenderer(val content: Content?)

    @Serializable
    data class Content(val sectionListRenderer: SectionListRenderer?)

    @Serializable
    data class MusicSearchContinuationContents(val sectionListContinuation: SectionListWrapper?)

    @Serializable
    data class SectionListWrapper(
        val contents: List<SectionListRenderer.Content>?,
        val continuations: List<Continuation>?,
    )
}

data class MusicSearchPage(
    val songs: List<Video>,
    val artists: List<Channel>,
    val continuation: String?,
    /** The searched entity itself when it is an artist (Top-result card). */
    val mainArtist: Channel? = null,
)

fun MusicSearchResponse.toMusicSearchPage(): MusicSearchPage {
    val sections: List<SectionListRenderer.Content> =
        contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents
            ?: continuationContents?.sectionListContinuation?.contents
            ?: emptyList()

    val renderers = mutableListOf<MusicResponsiveListItemRenderer>()
    for (section in sections) {
        section.musicShelfRenderer?.contents?.getItems()?.let { renderers += it }
        section.musicCardShelfRenderer?.contents
            ?.mapNotNull { it.musicResponsiveListItemRenderer }
            ?.let { renderers += it }
        // Flat anonymous-response shape: one item per itemSectionRenderer.
        section.itemSectionRenderer?.contents
            ?.mapNotNull { it.musicResponsiveListItemRenderer }
            ?.let { renderers += it }
    }

    var continuation: String? = null
    for (section in sections) {
        continuation = section.musicShelfRenderer?.contents?.shelfContinuation()
        if (continuation != null) break
    }
    if (continuation == null) {
        continuation =
            contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer
                ?.let { it.continuations?.token() }
        if (continuation == null) {
            continuation = continuationContents?.sectionListContinuation?.continuations?.token()
        }
    }

    val songs = mutableListOf<Video>()
    val artists = mutableListOf<Channel>()
    val seenSongIds = mutableSetOf<String>()
    val seenArtistIds = mutableSetOf<String>()

    // The "Top result" card carries the searched artist/song itself (it never
    // appears in the shelves below) — parse it first so it leads the lists.
    var mainArtist: Channel? = null
    for (section in sections) {
        section.musicCardShelfRenderer?.toTopResult()?.let { channel ->
            mainArtist = channel
            if (seenArtistIds.add(channel.id)) artists += channel
        }
    }

    for (renderer in renderers) {
        if (renderer.isArtist) {
            toChannel(renderer)?.let { channel ->
                if (seenArtistIds.add(channel.id)) artists += channel
            }
        } else {
            toSongVideo(renderer)?.let { video ->
                if (seenSongIds.add(video.id)) songs += video
            }
        }
    }

    return MusicSearchPage(
        songs = songs,
        artists = artists,
        continuation = continuation,
        mainArtist = mainArtist,
    )
}

private fun flexText(renderer: MusicResponsiveListItemRenderer, column: Int): String? =
    renderer.flexColumns.getOrNull(column)
        ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
        ?.firstOrNull()?.text

private fun thumbUrl(renderer: MusicResponsiveListItemRenderer): String =
    renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
        ?: renderer.thumbnail?.croppedSquareThumbnailRenderer?.getThumbnailUrl()
        ?: ""

/** Song item → the shared [Video] card model so existing UI renders it unchanged. */
private fun toSongVideo(renderer: MusicResponsiveListItemRenderer): Video? {
    val videoId =
        renderer.playlistItemData?.videoId
            ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
            ?: return null
    if (videoId.isBlank()) return null
    val artistChannelId =
        renderer.flexColumns.getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId
            .orEmpty()
            .takeIf { it.startsWith("UC") }
            .orEmpty()
    return Video(
        id = videoId,
        title = flexText(renderer, 0).orEmpty(),
        channelName = flexText(renderer, 1).orEmpty(),
        channelId = artistChannelId,
        thumbnailUrl = thumbUrl(renderer),
        duration = renderer.fixedColumns?.firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
            .let { text -> text?.parseTime() ?: 0 },
        // Music rows have no view/date metadata; negative viewCount is the
        // card's "hide views" sentinel (timestamp 0 avoids a bogus "now").
        viewCount = -1L,
        uploadDate = "",
        timestamp = 0L,
        channelThumbnailUrl = "",
    )
}

/** Artist item → the shared [Channel] card model. */
private fun toChannel(renderer: MusicResponsiveListItemRenderer): Channel? {
    val browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null
    if (!browseId.startsWith("UC")) return null
    return Channel(
        id = browseId,
        name = flexText(renderer, 0).orEmpty(),
        thumbnailUrl = thumbUrl(renderer),
        subscriberCount = 0,
        description = flexText(renderer, 1).orEmpty(),
        url = "https://www.youtube.com/channel/$browseId",
    )
}

/**
 * The Top-result card only represents the searched entity itself (its
 * [MusicCardShelfRenderer.onTap] points at it); when that entity is an
 * artist, surface it as the first artist result.
 */
private fun MusicCardShelfRenderer.toTopResult(): Channel? {
    val browse = onTap?.browseEndpoint ?: return null
    val pageType =
        browse.browseEndpointContextSupportedConfigs
            ?.browseEndpointContextMusicConfig?.pageType
    if (pageType != MUSIC_PAGE_TYPE_ARTIST) return null
    val browseId = browse.browseId
    if (!browseId.startsWith("UC")) return null
    val name = title.runs?.firstOrNull()?.text.orEmpty()
    if (name.isBlank()) return null
    return Channel(
        id = browseId,
        name = name,
        thumbnailUrl =
            thumbnail.musicThumbnailRenderer?.getThumbnailUrl()
                ?: thumbnail.croppedSquareThumbnailRenderer?.getThumbnailUrl()
                ?: "",
        subscriberCount = 0,
        description = subtitle.runs?.joinToString(separator = "") { it.text }.orEmpty(),
        url = "https://www.youtube.com/channel/$browseId",
    )
}

private fun List<MusicShelfRenderer.Content>.getItems(): List<MusicResponsiveListItemRenderer> =
    mapNotNull { it.musicResponsiveListItemRenderer }

private fun List<MusicShelfRenderer.Content>.shelfContinuation(): String? =
    firstOrNull { it.continuationItemRenderer != null }
        ?.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token

private fun List<Continuation>?.token(): String? =
    this?.firstOrNull()?.nextContinuationData?.continuation
