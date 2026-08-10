/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

/** Behavioral coverage for the home-feed consumer logic (I-1 impressions, quotas, blending). */
class HomeFeedLogicTest {

    @Test
    fun `impression filter drops non-video keys like shelves and loaders`() {
        val visible = listOf("v1", "shorts_shelf", "v2", "loading_indicator")
        val known = setOf("v1", "v2", "v3")
        assertThat(feedImpressionIds(visible, known)).containsExactly("v1", "v2").inOrder()
    }

    private fun v(title: String, duration: Int) = Video(
        id = title, title = title, channelName = "C", channelId = "c",
        thumbnailUrl = "", duration = duration, viewCount = 1000, uploadDate = "1 day ago"
    )

    private fun vc(id: String, channelId: String) = Video(
        id = id, title = id, channelName = channelId, channelId = channelId,
        thumbnailUrl = "", duration = 600, viewCount = 1, uploadDate = "1 day ago"
    )

    private fun adjacentSameChannel(videos: List<Video>) =
        videos.zipWithNext().count { (a, b) -> a.channelId.isNotBlank() && a.channelId == b.channelId }

    @Test
    fun `spaceByChannel separates clustered same-channel items without dropping any`() {
        val clustered = listOf(
            vc("a1", "A"), vc("a2", "A"), vc("a3", "A"), vc("b1", "B"), vc("c1", "C")
        )
        val spaced = spaceByChannel(clustered)
        assertThat(spaced.map { it.id }).containsExactlyElementsIn(clustered.map { it.id })
        assertThat(adjacentSameChannel(spaced)).isEqualTo(0)
    }

    @Test
    fun `spaceByChannel honours the seeded tail to avoid cross-page repeats`() {
        // Prior page ended on channel A; a page that starts with A must not lead with it.
        val page = listOf(vc("a1", "A"), vc("b1", "B"))
        val spaced = spaceByChannel(page, seedRecent = listOf("A"))
        assertThat(spaced.first().channelId).isEqualTo("B")
    }

    @Test
    fun `spaceByChannel ignores blank channel ids`() {
        val related = listOf(vc("r1", ""), vc("r2", ""), vc("r3", ""))
        assertThat(spaceByChannel(related).map { it.id }).containsExactly("r1", "r2", "r3").inOrder()
    }

    @Test
    fun `home feed quotas adapt to no subscription maturing and mature profiles`() {
        assertThat(homeFeedQuotas(40, subCount = 0, totalInteractions = 200))
            .containsExactly(
                FeedSource.SUBS, 0,
                FeedSource.RELATED, 14,
                FeedSource.DISCOVERY, 18,
                FeedSource.VIRAL, 8
            )

        assertThat(homeFeedQuotas(40, subCount = 12, totalInteractions = 20))
            .containsExactly(
                FeedSource.SUBS, 14,
                FeedSource.RELATED, 12,
                FeedSource.DISCOVERY, 10,
                FeedSource.VIRAL, 4
            )

        assertThat(homeFeedQuotas(40, subCount = 12, totalInteractions = 200))
            .containsExactly(
                FeedSource.SUBS, 16,
                FeedSource.RELATED, 10,
                FeedSource.DISCOVERY, 10,
                FeedSource.VIRAL, 4
            )
    }

    @Test
    fun `blendFeedSources reports source distribution`() {
        val result = blendFeedSources(
            lanes = mapOf(
                FeedSource.SUBS to listOf(vc("s1", "S")),
                FeedSource.RELATED to listOf(vc("r1", "R")),
                FeedSource.DISCOVERY to listOf(vc("d1", "D")),
                FeedSource.VIRAL to listOf(vc("v1", "V"))
            ),
            quotas = mapOf(
                FeedSource.SUBS to 1,
                FeedSource.RELATED to 1,
                FeedSource.DISCOVERY to 1,
                FeedSource.VIRAL to 1
            ),
            targetSize = 4
        )

        assertThat(result.videos.map { it.id }).containsExactly("s1", "r1", "d1", "v1").inOrder()
        assertThat(result.sourceCounts)
            .containsExactly(
                FeedSource.SUBS, 1,
                FeedSource.RELATED, 1,
                FeedSource.DISCOVERY, 1,
                FeedSource.VIRAL, 1
            )
    }

    @Test
    fun `blendFeedSources relaxes scarce quota in related discovery subs viral order`() {
        val result = blendFeedSources(
            lanes = mapOf(
                FeedSource.SUBS to listOf(vc("s1", "S"), vc("s2", "S")),
                FeedSource.RELATED to listOf(vc("r1", "R1"), vc("r2", "R2")),
                FeedSource.DISCOVERY to listOf(vc("d1", "D1"), vc("d2", "D2")),
                FeedSource.VIRAL to listOf(vc("v1", "V1"), vc("v2", "V2"))
            ),
            quotas = FeedSource.entries.associateWith { 0 },
            targetSize = 3
        )

        assertThat(result.items.map { it.source })
            .containsExactly(FeedSource.RELATED, FeedSource.RELATED, FeedSource.DISCOVERY)
            .inOrder()
        assertThat(result.videos.map { it.id }).containsExactly("r1", "r2", "d1").inOrder()
    }

    @Test
    fun `addUniquePageVideos fills page without duplicates or channel overflow`() {
        val page = mutableListOf<Video>()
        val channelCounts = mutableMapOf<String, Int>()
        val usedIds = mutableSetOf("existing")
        val candidates = listOf(
            vc("existing", "A"),
            vc("a1", "A"),
            vc("a2", "A"),
            vc("a3", "A"),
            vc("b1", "B")
        )

        val added = addUniquePageVideos(
            candidates = candidates,
            targetList = page,
            channelCounts = channelCounts,
            usedVideoIds = usedIds,
            targetSize = 3,
            maxPerChannel = 2
        )

        assertThat(added).isEqualTo(3)
        assertThat(page.map { it.id }).containsExactly("a1", "a2", "b1").inOrder()
    }
}
