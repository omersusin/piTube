package com.omersusin.pitube.innertube.pages

import com.omersusin.pitube.innertube.models.Album
import com.omersusin.pitube.innertube.models.AlbumItem
import com.omersusin.pitube.innertube.models.Artist
import com.omersusin.pitube.innertube.models.ArtistItem
import com.omersusin.pitube.innertube.models.MusicResponsiveListItemRenderer
import com.omersusin.pitube.innertube.models.MusicTwoRowItemRenderer
import com.omersusin.pitube.innertube.models.PlaylistItem
import com.omersusin.pitube.innertube.models.SongItem
import com.omersusin.pitube.innertube.models.YTItem
import com.omersusin.pitube.innertube.models.oddElements
import com.omersusin.pitube.innertube.utils.parseTime

data class LibraryAlbumsPage(
    val albums: List<AlbumItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
            return AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint?.playlistId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = null,
                        year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null
                    )
        }
    }
}
