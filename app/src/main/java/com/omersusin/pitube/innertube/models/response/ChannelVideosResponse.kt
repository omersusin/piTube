package com.omersusin.pitube.innertube.models.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model for YouTube WEB channel videos/live tabs.
 *
 * YouTube currently returns channel grid items as lockupViewModel, while older
 * payloads may still contain videoRenderer.
 */
@Serializable
data class ChannelVideosResponse(
    @SerialName("header")
    val header: Header? = null,
    @SerialName("contents")
    val contents: Contents? = null,
    @SerialName("metadata")
    val metadata: Metadata? = null,
    @SerialName("continuationContents")
    val continuationContents: ContinuationContents? = null,
    @SerialName("onResponseReceivedActions")
    val onResponseReceivedActions: List<OnResponseReceivedAction>? = null,
) {
    @Serializable
    data class Header(
        @SerialName("pageHeaderRenderer")
        val pageHeaderRenderer: PageHeaderRenderer? = null,
    ) {
        @Serializable
        data class PageHeaderRenderer(
            @SerialName("content")
            val content: PageHeaderContent? = null,
        )

        @Serializable
        data class PageHeaderContent(
            @SerialName("pageHeaderViewModel")
            val pageHeaderViewModel: PageHeaderViewModel? = null,
        )

        @Serializable
        data class PageHeaderViewModel(
            @SerialName("metadata")
            val metadata: HeaderMetadata? = null,
        )

        @Serializable
        data class HeaderMetadata(
            @SerialName("contentMetadataViewModel")
            val contentMetadataViewModel: HeaderContentMetadata? = null,
        )

        @Serializable
        data class HeaderContentMetadata(
            @SerialName("metadataRows")
            val metadataRows: List<LockupViewModel.MetadataRow>? = null,
        )
    }

    @Serializable
    data class Metadata(
        @SerialName("channelMetadataRenderer")
        val channelMetadataRenderer: ChannelMetadataRenderer? = null,
    ) {
        @Serializable
        data class ChannelMetadataRenderer(
            @SerialName("externalId")
            val externalId: String? = null,
            @SerialName("externalChannelId")
            val externalChannelId: String? = null,
            @SerialName("title")
            val title: String? = null,
            @SerialName("avatar")
            val avatar: ThumbnailContainer? = null,
        )
    }

    @Serializable
    data class ThumbnailContainer(
        @SerialName("thumbnails")
        val thumbnails: List<ThumbnailItem>? = null,
    )

    @Serializable
    data class ThumbnailItem(
        @SerialName("url")
        val url: String? = null,
        @SerialName("width")
        val width: Int? = null,
        @SerialName("height")
        val height: Int? = null,
    )

    @Serializable
    data class SimpleText(
        @SerialName("simpleText")
        val simpleText: String? = null,
        @SerialName("runs")
        val runs: List<Run>? = null,
    ) {
        @Serializable
        data class Run(
            @SerialName("text")
            val text: String? = null,
        )
    }

    @Serializable
    data class VideoRenderer(
        @SerialName("videoId")
        val videoId: String? = null,
        @SerialName("thumbnail")
        val thumbnail: ThumbnailContainer? = null,
        @SerialName("title")
        val title: SimpleText? = null,
        @SerialName("publishedTimeText")
        val publishedTimeText: SimpleText? = null,
        @SerialName("viewCountText")
        val viewCountText: SimpleText? = null,
        @SerialName("lengthText")
        val lengthText: SimpleText? = null,
        @SerialName("channelThumbnailSupportedRenderers")
        val channelThumbnailSupportedRenderers: ChannelThumbnailSupportedRenderers? = null,
        @SerialName("avatarStackViewModel")
        val avatarStackViewModel: AvatarStackViewModel? = null,
        @SerialName("ownerText")
        val ownerText: SimpleText? = null,
    ) {
        @Serializable
        data class ChannelThumbnailSupportedRenderers(
            @SerialName("channelThumbnailWithLinkRenderer")
            val channelThumbnailWithLinkRenderer: ChannelThumbnailWithLinkRenderer? = null,
            @SerialName("avatarStackViewModel")
            val avatarStackViewModel: AvatarStackViewModel? = null,
        )

        @Serializable
        data class ChannelThumbnailWithLinkRenderer(
            @SerialName("thumbnail")
            val thumbnail: ThumbnailContainer? = null,
            @SerialName("avatarStack")
            val avatarStack: AvatarStack? = null,
        ) {
            @Serializable
            data class AvatarStack(
                @SerialName("avatarStackViewModel")
                val avatarStackViewModel: AvatarStackViewModel? = null,
            )
        }

        @Serializable
        data class AvatarStackViewModel(
            @SerialName("avatars")
            val avatars: List<Avatar>? = null,
        ) {
            @Serializable
            data class Avatar(
                @SerialName("avatarViewModel")
                val avatarViewModel: AvatarViewModel? = null,
            )

            @Serializable
            data class AvatarViewModel(
                @SerialName("image")
                val image: Image? = null,
            )

            @Serializable
            data class Image(
                @SerialName("sources")
                val sources: List<Source>? = null,
            )

            @Serializable
            data class Source(
                @SerialName("url")
                val url: String? = null,
                @SerialName("width")
                val width: Int? = null,
                @SerialName("height")
                val height: Int? = null,
            )
        }
    }

    @Serializable
    data class LockupViewModel(
        @SerialName("contentId")
        val contentId: String? = null,
        @SerialName("contentType")
        val contentType: String? = null,
        @SerialName("metadata")
        val metadata: MetadataContainer? = null,
        @SerialName("contentImage")
        val contentImage: ContentImage? = null,
        @SerialName("rendererContext")
        val rendererContext: RendererContext? = null,
        @SerialName("onTap")
        val onTap: TapCommand? = null,
    ) {
        @Serializable
        data class MetadataContainer(
            @SerialName("lockupMetadataViewModel")
            val lockupMetadataViewModel: LockupMetadataViewModel? = null,
        )

        @Serializable
        data class LockupMetadataViewModel(
            @SerialName("title")
            val title: TextContent? = null,
            @SerialName("metadata")
            val metadata: ContentMetadata? = null,
            @SerialName("image")
            val image: LockupImage? = null,
        )

        @Serializable
        data class LockupImage(
            @SerialName("decoratedAvatarViewModel")
            val decoratedAvatarViewModel: DecoratedAvatarViewModel? = null,
        )

        @Serializable
        data class DecoratedAvatarViewModel(
            @SerialName("avatar")
            val avatar: Avatar? = null,
        )

        @Serializable
        data class Avatar(
            @SerialName("avatarViewModel")
            val avatarViewModel: AvatarViewModel? = null,
        )

        @Serializable
        data class AvatarViewModel(
            @SerialName("image")
            val image: Image? = null,
        )

        @Serializable
        data class TextContent(
            @SerialName("content")
            val content: String? = null,
        )

        @Serializable
        data class ContentMetadata(
            @SerialName("contentMetadataViewModel")
            val contentMetadataViewModel: ContentMetadataViewModel? = null,
        )

        @Serializable
        data class ContentMetadataViewModel(
            @SerialName("metadataRows")
            val metadataRows: List<MetadataRow>? = null,
        )

        @Serializable
        data class MetadataRow(
            @SerialName("metadataParts")
            val metadataParts: List<MetadataPart>? = null,
        )

        @Serializable
        data class MetadataPart(
            @SerialName("text")
            val text: TextContent? = null,
            @SerialName("accessibilityLabel")
            val accessibilityLabel: String? = null,
            @SerialName("runs")
            val runs: List<TextRun>? = null,
        )

        @Serializable
        data class TextRun(
            @SerialName("text")
            val text: String? = null,
            @SerialName("navigationEndpoint")
            val navigationEndpoint: RunEndpoint? = null,
        )

        @Serializable
        data class RunEndpoint(
            @SerialName("browseEndpoint")
            val browseEndpoint: RunBrowseEndpoint? = null,
        )

        @Serializable
        data class RunBrowseEndpoint(
            @SerialName("browseId")
            val browseId: String? = null,
        )

        @Serializable
        data class ContentImage(
            @SerialName("thumbnailViewModel")
            val thumbnailViewModel: ThumbnailViewModel? = null,
        )

        @Serializable
        data class ThumbnailViewModel(
            @SerialName("image")
            val image: Image? = null,
            @SerialName("overlays")
            val overlays: List<Overlay>? = null,
        )

        @Serializable
        data class Image(
            @SerialName("sources")
            val sources: List<ImageSource>? = null,
        )

        @Serializable
        data class ImageSource(
            @SerialName("url")
            val url: String? = null,
            @SerialName("width")
            val width: Int? = null,
            @SerialName("height")
            val height: Int? = null,
        )

        @Serializable
        data class Overlay(
            @SerialName("thumbnailBottomOverlayViewModel")
            val thumbnailBottomOverlayViewModel: ThumbnailBottomOverlayViewModel? = null,
            @SerialName("thumbnailOverlayBadgeViewModel")
            val thumbnailOverlayBadgeViewModel: ThumbnailOverlayBadgeViewModel? = null,
        )

        @Serializable
        data class ThumbnailBottomOverlayViewModel(
            @SerialName("badges")
            val badges: List<Badge>? = null,
        )

        @Serializable
        data class ThumbnailOverlayBadgeViewModel(
            @SerialName("thumbnailBadges")
            val thumbnailBadges: List<Badge>? = null,
        )

        @Serializable
        data class Badge(
            @SerialName("thumbnailBadgeViewModel")
            val thumbnailBadgeViewModel: ThumbnailBadgeViewModel? = null,
        )

        @Serializable
        data class ThumbnailBadgeViewModel(
            @SerialName("text")
            val text: String? = null,
            @SerialName("badgeStyle")
            val badgeStyle: String? = null,
            @SerialName("animatedText")
            val animatedText: AnimatedText? = null,
        ) {
            @Serializable
            data class AnimatedText(
                @SerialName("text")
                val text: String? = null,
            )
        }

        /**
         * Navigation wrapper. Modern lockups put the tap command under
         * `rendererContext.commandContext.onTap`; older payloads keep a
         * top-level `onTap`. Both eventually reach the same
         * `commandMetadata.webCommandMetadata.url`, which is `/shorts/<id>`
         * for Shorts and `/watch?v=<id>` for regular videos.
         */
        @Serializable
        data class RendererContext(
            @SerialName("commandContext")
            val commandContext: CommandContext? = null,
        )

        @Serializable
        data class CommandContext(
            @SerialName("onTap")
            val onTap: TapCommand? = null,
        )

        @Serializable
        data class TapCommand(
            @SerialName("innertubeCommand")
            val innertubeCommand: InnertubeCommand? = null,
        )

        @Serializable
        data class InnertubeCommand(
            @SerialName("commandMetadata")
            val commandMetadata: CommandMetadata? = null,
            @SerialName("reelWatchEndpoint")
            val reelWatchEndpoint: ReelWatchEndpoint? = null,
        )

        @Serializable
        data class CommandMetadata(
            @SerialName("webCommandMetadata")
            val webCommandMetadata: WebCommandMetadata? = null,
        )

        @Serializable
        data class WebCommandMetadata(
            @SerialName("url")
            val url: String? = null,
        )

        @Serializable
        data class ReelWatchEndpoint(
            @SerialName("videoId")
            val videoId: String? = null,
        )

        /**
         * The tap URL for this lockup, from either the modern
         * `rendererContext.commandContext.onTap` path or the legacy
         * top-level `onTap`.
         */
        fun tapUrl(): String? =
            (
                rendererContext?.commandContext?.onTap
                    ?: onTap
            )?.innertubeCommand?.commandMetadata?.webCommandMetadata?.url
                ?.takeIf { it.isNotBlank() }

        /** True when the tap command is a Shorts (reel) watch endpoint. */
        fun hasReelEndpoint(): Boolean =
            (
                rendererContext?.commandContext?.onTap
                    ?: onTap
            )?.innertubeCommand?.reelWatchEndpoint != null
    }

    /**
     * Shorts delivered under their own renderer key. YouTube emits these for
     * Shorts in the subscriptions / what-to-watch grids (and inside
     * `reelShelfRenderer`), and the shape is completely different from
     * `lockupViewModel`: the video id lives in the tap URL, and the title /
     * view count in `overlayMetadata`. Anything parsed from this key is a
     * Short by definition.
     */
    @Serializable
    data class ShortsLockupViewModel(
        @SerialName("entityId")
        val entityId: String? = null,
        @SerialName("onTap")
        val onTap: LockupViewModel.TapCommand? = null,
        @SerialName("overlayMetadata")
        val overlayMetadata: ShortsOverlayMetadata? = null,
        @SerialName("thumbnail")
        val thumbnail: LockupViewModel.Image? = null,
    ) {
        @Serializable
        data class ShortsOverlayMetadata(
            @SerialName("primaryText")
            val primaryText: LockupViewModel.TextContent? = null,
            @SerialName("secondaryText")
            val secondaryText: LockupViewModel.TextContent? = null,
        )

        /**
         * Video id, preferring the reel endpoint, then the `/shorts/<id>` tap
         * URL, then the `shorts-<id>` entity id.
         */
        fun videoId(): String? {
            val command = onTap?.innertubeCommand
            command?.reelWatchEndpoint?.videoId?.takeIf { it.length == 11 }?.let { return it }
            command?.commandMetadata?.webCommandMetadata?.url
                ?.substringAfter("/shorts/", "")
                ?.substringBefore("?")
                ?.substringBefore("/")
                ?.takeIf { it.length == 11 }
                ?.let { return it }
            return entityId?.substringAfterLast("-")?.takeIf { it.length == 11 }
        }
    }

    @Serializable
    data class RichItem(
        @SerialName("richItemRenderer")
        val richItemRenderer: RichItemRenderer? = null,
        @SerialName("richSectionRenderer")
        val richSectionRenderer: RichSectionRenderer? = null,
        @SerialName("continuationItemRenderer")
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    ) {
        @Serializable
        data class RichItemRenderer(
            @SerialName("content")
            val content: RichItemContent? = null,
        )

        @Serializable
        data class RichItemContent(
            @SerialName("lockupViewModel")
            val lockupViewModel: LockupViewModel? = null,
            @SerialName("videoRenderer")
            val videoRenderer: VideoRenderer? = null,
            @SerialName("shortsLockupViewModel")
            val shortsLockupViewModel: ShortsLockupViewModel? = null,
        )

        /**
         * Shelf wrapper. The Shorts shelf on the subscriptions / what-to-watch
         * grids arrives as `richSectionRenderer > reelShelfRenderer > items[] >
         * shortsLockupViewModel`, which the plain rich-item path never sees.
         */
        @Serializable
        data class RichSectionRenderer(
            @SerialName("content")
            val content: RichSectionContent? = null,
        )

        @Serializable
        data class RichSectionContent(
            @SerialName("reelShelfRenderer")
            val reelShelfRenderer: ReelShelfRenderer? = null,
            @SerialName("richShelfRenderer")
            val richShelfRenderer: RichShelfRenderer? = null,
        )

        @Serializable
        data class ReelShelfRenderer(
            @SerialName("items")
            val items: List<ReelShelfItem>? = null,
        )

        @Serializable
        data class RichShelfRenderer(
            @SerialName("contents")
            val contents: List<RichItem>? = null,
        )

        @Serializable
        data class ReelShelfItem(
            @SerialName("shortsLockupViewModel")
            val shortsLockupViewModel: ShortsLockupViewModel? = null,
            @SerialName("reelItemRenderer")
            val reelItemRenderer: ReelItemRenderer? = null,
        )

        @Serializable
        data class ReelItemRenderer(
            @SerialName("videoId")
            val videoId: String? = null,
            @SerialName("headline")
            val headline: SimpleText? = null,
            @SerialName("viewCountText")
            val viewCountText: SimpleText? = null,
            @SerialName("thumbnail")
            val thumbnail: ThumbnailContainer? = null,
        )
    }

    @Serializable
    data class ContinuationItemRenderer(
        @SerialName("continuationEndpoint")
        val continuationEndpoint: ContinuationEndpoint? = null,
    ) {
        @Serializable
        data class ContinuationEndpoint(
            @SerialName("continuationCommand")
            val continuationCommand: ContinuationCommand? = null,
        )

        @Serializable
        data class ContinuationCommand(
            @SerialName("token")
            val token: String? = null,
        )
    }

    @Serializable
    data class Contents(
        @SerialName("twoColumnBrowseResultsRenderer")
        val twoColumnBrowseResultsRenderer: TwoColumnBrowseResultsRenderer? = null,
    ) {
        @Serializable
        data class TwoColumnBrowseResultsRenderer(
            @SerialName("tabs")
            val tabs: List<Tab>? = null,
        )

        @Serializable
        data class Tab(
            @SerialName("tabRenderer")
            val tabRenderer: TabRenderer? = null,
            @SerialName("expandableTabRenderer")
            val expandableTabRenderer: TabRenderer? = null,
        )

        @Serializable
        data class TabRenderer(
            @SerialName("selected")
            val selected: Boolean? = null,
            @SerialName("title")
            val title: String? = null,
            @SerialName("content")
            val content: Content? = null,
        )

        @Serializable
        data class Content(
            @SerialName("richGridRenderer")
            val richGridRenderer: RichGridRenderer? = null,
            @SerialName("sectionListRenderer")
            val sectionListRenderer: SectionListRenderer? = null,
        )

        @Serializable
        data class SectionListRenderer(
            @SerialName("contents")
            val contents: List<SectionItem>? = null,
        )

        @Serializable
        data class SectionItem(
            @SerialName("itemSectionRenderer")
            val itemSectionRenderer: ItemSectionRenderer? = null,
        )

        @Serializable
        data class ItemSectionRenderer(
            @SerialName("contents")
            val contents: List<ItemSectionContent>? = null,
        )

        @Serializable
        data class ItemSectionContent(
            @SerialName("richGridRenderer")
            val richGridRenderer: RichGridRenderer? = null,
        )

        @Serializable
        data class RichGridRenderer(
            @SerialName("contents")
            val contents: List<RichItem>? = null,
        )
    }

    @Serializable
    data class ContinuationContents(
        @SerialName("richGridContinuation")
        val richGridContinuation: RichGridContinuation? = null,
    ) {
        @Serializable
        data class RichGridContinuation(
            @SerialName("contents")
            val contents: List<RichItem>? = null,
        )
    }

    @Serializable
    data class OnResponseReceivedAction(
        @SerialName("appendContinuationItemsAction")
        val appendContinuationItemsAction: AppendContinuationItemsAction? = null,
    ) {
        @Serializable
        data class AppendContinuationItemsAction(
            @SerialName("continuationItems")
            val continuationItems: List<RichItem>? = null,
        )
    }
}

internal fun ChannelVideosResponse.channelVideoCountText(): String? {
    val metadataRows = header
        ?.pageHeaderRenderer
        ?.content
        ?.pageHeaderViewModel
        ?.metadata
        ?.contentMetadataViewModel
        ?.metadataRows
        .orEmpty()
    val statsRow = metadataRows.firstOrNull { it.metadataParts.orEmpty().size > 1 } ?: return null
    return statsRow.metadataParts
        ?.lastOrNull()
        ?.text
        ?.content
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

/** InnerTube's `contentType` marker for a Shorts lockup. */
internal const val LOCKUP_CONTENT_TYPE_SHORTS = "LOCKUP_CONTENT_TYPE_SHORTS"

/**
 * Whether this feed lockup is a Short.
 *
 * YouTube does not expose a single reliable "isShort" boolean on the
 * FEsubscriptions / FEwhat_to_watch grids, and the wire shape has changed more
 * than once, so three independent signals are checked in order of reliability
 * (Koda and NewPipe both rely on the first two):
 *
 *  1. `contentType == LOCKUP_CONTENT_TYPE_SHORTS` — the explicit marker.
 *  2. The tap command is a reel endpoint, or its URL is `/shorts/<id>`.
 *  3. Fallback: no duration badge **and** a portrait thumbnail. Regular video
 *     thumbnails are always 16:9 landscape and always carry a duration badge;
 *     Shorts carry neither. Used only when 1 and 2 are absent, so a normal
 *     video can never be misclassified by it while a badge is present.
 *
 * [hasDurationBadge] is supplied by the caller because badge extraction lives
 * in the parser (it must distinguish a duration badge from a LIVE/MIX badge).
 */
internal fun ChannelVideosResponse.LockupViewModel.isShortsLockup(
    hasDurationBadge: Boolean,
): Boolean {
    if (contentType == LOCKUP_CONTENT_TYPE_SHORTS) return true
    if (contentType != null && contentType != "LOCKUP_CONTENT_TYPE_VIDEO") {
        // An explicitly non-video, non-shorts lockup (playlist/channel/podcast)
        // is not a Short — bail out before the heuristic below.
        return false
    }
    if (hasReelEndpoint()) return true
    if (tapUrl()?.contains("/shorts/") == true) return true
    return !hasDurationBadge && hasPortraitThumbnail()
}

/**
 * True when the largest thumbnail source is taller than it is wide. Shorts use
 * portrait art; regular videos are always 16:9 landscape.
 */
internal fun ChannelVideosResponse.LockupViewModel.hasPortraitThumbnail(): Boolean {
    val largest = contentImage?.thumbnailViewModel?.image?.sources
        ?.filter { (it.width ?: 0) > 0 && (it.height ?: 0) > 0 }
        ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
        ?: return false
    return (largest.height ?: 0) > (largest.width ?: 0)
}
