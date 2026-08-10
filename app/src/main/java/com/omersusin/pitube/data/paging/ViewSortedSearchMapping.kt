package com.omersusin.pitube.data.paging

import com.omersusin.pitube.data.local.ContentType
import com.omersusin.pitube.data.local.Duration
import com.omersusin.pitube.data.local.SearchFilter
import com.omersusin.pitube.data.local.UploadDate
import com.omersusin.pitube.data.model.Channel
import com.omersusin.pitube.data.model.Playlist
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.innertube.YouTubeSearchParams
import com.omersusin.pitube.innertube.pages.SearchChannelItem
import com.omersusin.pitube.innertube.pages.SearchPlaylistItem
import com.omersusin.pitube.innertube.pages.SearchVideoItem
import com.omersusin.pitube.innertube.pages.WebSearchItem
import com.omersusin.pitube.utils.ThumbnailUrlResolver
import com.omersusin.pitube.utils.avatarImageIdentityKey

internal fun SearchFilter.toViewSortedSearchParams(): String =
    YouTubeSearchParams.sortedByViewCount(
        contentType = when (contentType) {
            ContentType.ALL -> null
            ContentType.VIDEOS, ContentType.SHORTS, ContentType.LIVE ->
                YouTubeSearchParams.ContentType.VIDEO
            ContentType.CHANNELS -> YouTubeSearchParams.ContentType.CHANNEL
            ContentType.PLAYLISTS -> YouTubeSearchParams.ContentType.PLAYLIST
        },
        duration = if (contentType.supportsVideoFilters()) {
            when (duration) {
                Duration.ANY -> null
                Duration.UNDER_4_MINUTES -> YouTubeSearchParams.Duration.SHORT
                Duration.FROM_4_TO_20_MINUTES -> YouTubeSearchParams.Duration.MEDIUM
                Duration.OVER_20_MINUTES -> YouTubeSearchParams.Duration.LONG
            }
        } else {
            null
        },
        uploadDate = if (contentType.supportsVideoFilters()) {
            when (uploadDate) {
                UploadDate.ANY -> null
                UploadDate.TODAY -> YouTubeSearchParams.UploadDate.TODAY
                UploadDate.THIS_WEEK -> YouTubeSearchParams.UploadDate.THIS_WEEK
                UploadDate.THIS_MONTH -> YouTubeSearchParams.UploadDate.THIS_MONTH
                UploadDate.THIS_YEAR -> YouTubeSearchParams.UploadDate.THIS_YEAR
            }
        } else {
            null
        },
        liveOnly = contentType == ContentType.LIVE,
    )

private fun ContentType.supportsVideoFilters(): Boolean =
    this != ContentType.CHANNELS && this != ContentType.PLAYLISTS

internal fun WebSearchItem.toSearchResultItem(): SearchResultItem =
    when (this) {
        is SearchVideoItem -> {
            val channelThumbnailUrls = this.channelThumbnailUrls
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinctBy { it.avatarImageIdentityKey() }
                .take(2)
            SearchResultItem.VideoResult(
                Video(
                    id = id,
                    title = title,
                    channelName = channelName,
                    channelId = channelId,
                    thumbnailUrl = ThumbnailUrlResolver.normalizeVideoThumbnail(id, thumbnailUrl),
                    duration = duration,
                    viewCount = viewCount,
                    uploadDate = uploadDate,
                    channelThumbnailUrl = channelThumbnailUrls.firstOrNull().orEmpty(),
                    channelThumbnailUrls = channelThumbnailUrls,
                    isShort = duration in 1..60,
                    isLive = isLive,
                )
            )
        }

        is SearchChannelItem -> SearchResultItem.ChannelResult(
            Channel(
                id = id,
                name = name,
                thumbnailUrl = thumbnailUrl,
                subscriberCount = subscriberCount,
                description = description,
                url = url,
            )
        )

        is SearchPlaylistItem -> SearchResultItem.PlaylistResult(
            Playlist(
                id = id,
                name = name,
                thumbnailUrl = thumbnailUrl,
                videoCount = videoCount,
            )
        )
    }
