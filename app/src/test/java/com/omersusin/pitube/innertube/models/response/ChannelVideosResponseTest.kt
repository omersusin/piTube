package com.omersusin.pitube.innertube.models.response

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelVideosResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reads total video count from channel header metadata`() {
        val response = json.decodeFromString<ChannelVideosResponse>(
            """
            {
              "header": {
                "pageHeaderRenderer": {
                  "content": {
                    "pageHeaderViewModel": {
                      "metadata": {
                        "contentMetadataViewModel": {
                          "metadataRows": [
                            { "metadataParts": [{ "text": { "content": "@channel" } }] },
                            {
                              "metadataParts": [
                                { "text": { "content": "2M subscribers" } },
                                { "text": { "content": "1.8K videos" } }
                              ]
                            }
                          ]
                        }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("1.8K videos", response.channelVideoCountText())
    }

    // ── Shorts detection on the signed feeds ────────────────────────────────
    // The feed grids (FEsubscriptions / FEwhat_to_watch) do not expose a single
    // reliable shorts boolean, so three independent signals are checked. Each
    // gets its own case, plus the negative cases that must stay videos.

    private fun lockup(body: String): ChannelVideosResponse.LockupViewModel =
        json.decodeFromString(body)

    @Test
    fun `explicit shorts contentType marks the lockup as a short`() {
        val lockup = lockup(
            """
            {
              "contentId": "abcdefghij1",
              "contentType": "LOCKUP_CONTENT_TYPE_SHORTS"
            }
            """.trimIndent(),
        )

        assertTrue(lockup.isShortsLockup(hasDurationBadge = false))
        // Even with a duration badge present, the explicit marker wins.
        assertTrue(lockup.isShortsLockup(hasDurationBadge = true))
    }

    @Test
    fun `shorts tap url marks the lockup as a short`() {
        val lockup = lockup(
            """
            {
              "contentId": "abcdefghij1",
              "contentType": "LOCKUP_CONTENT_TYPE_VIDEO",
              "rendererContext": {
                "commandContext": {
                  "onTap": {
                    "innertubeCommand": {
                      "commandMetadata": {
                        "webCommandMetadata": { "url": "/shorts/abcdefghij1" }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("/shorts/abcdefghij1", lockup.tapUrl())
        assertTrue(lockup.isShortsLockup(hasDurationBadge = false))
    }

    @Test
    fun `legacy top level onTap reel endpoint marks the lockup as a short`() {
        val lockup = lockup(
            """
            {
              "contentId": "abcdefghij1",
              "onTap": {
                "innertubeCommand": {
                  "reelWatchEndpoint": { "videoId": "abcdefghij1" }
                }
              }
            }
            """.trimIndent(),
        )

        assertTrue(lockup.hasReelEndpoint())
        assertTrue(lockup.isShortsLockup(hasDurationBadge = false))
    }

    @Test
    fun `portrait thumbnail without a duration badge falls back to short`() {
        val lockup = lockup(
            """
            {
              "contentId": "abcdefghij1",
              "contentImage": {
                "thumbnailViewModel": {
                  "image": {
                    "sources": [
                      { "url": "https://i.ytimg.com/a.jpg", "width": 405, "height": 720 }
                    ]
                  }
                }
              }
            }
            """.trimIndent(),
        )

        assertTrue(lockup.hasPortraitThumbnail())
        assertTrue(lockup.isShortsLockup(hasDurationBadge = false))
        // A duration badge means it is a real video, so the fallback must not fire.
        assertFalse(lockup.isShortsLockup(hasDurationBadge = true))
    }

    @Test
    fun `landscape thumbnail without a duration badge stays a video`() {
        val lockup = lockup(
            """
            {
              "contentId": "abcdefghij1",
              "contentImage": {
                "thumbnailViewModel": {
                  "image": {
                    "sources": [
                      { "url": "https://i.ytimg.com/a.jpg", "width": 1280, "height": 720 }
                    ]
                  }
                }
              }
            }
            """.trimIndent(),
        )

        assertFalse(lockup.hasPortraitThumbnail())
        assertFalse(lockup.isShortsLockup(hasDurationBadge = false))
    }

    @Test
    fun `playlist lockup is never treated as a short`() {
        // A playlist lockup has no duration badge and can have portrait art;
        // the explicit non-video contentType must short-circuit the fallback.
        val lockup = lockup(
            """
            {
              "contentId": "PL1234567890",
              "contentType": "LOCKUP_CONTENT_TYPE_PLAYLIST",
              "contentImage": {
                "thumbnailViewModel": {
                  "image": {
                    "sources": [
                      { "url": "https://i.ytimg.com/a.jpg", "width": 405, "height": 720 }
                    ]
                  }
                }
              }
            }
            """.trimIndent(),
        )

        assertFalse(lockup.isShortsLockup(hasDurationBadge = false))
    }

    @Test
    fun `regular video lockup with a duration badge is not a short`() {
        val lockup = lockup(
            """
            {
              "contentId": "abcdefghij1",
              "contentType": "LOCKUP_CONTENT_TYPE_VIDEO",
              "rendererContext": {
                "commandContext": {
                  "onTap": {
                    "innertubeCommand": {
                      "commandMetadata": {
                        "webCommandMetadata": { "url": "/watch?v=abcdefghij1" }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        assertFalse(lockup.isShortsLockup(hasDurationBadge = true))
    }

    // ── shortsLockupViewModel id extraction ─────────────────────────────────

    @Test
    fun `shorts lockup resolves the video id from the reel endpoint`() {
        val shorts = json.decodeFromString<ChannelVideosResponse.ShortsLockupViewModel>(
            """
            {
              "onTap": {
                "innertubeCommand": {
                  "reelWatchEndpoint": { "videoId": "abcdefghij1" }
                }
              },
              "overlayMetadata": {
                "primaryText": { "content": "A short" },
                "secondaryText": { "content": "1.2M views" }
              }
            }
            """.trimIndent(),
        )

        assertEquals("abcdefghij1", shorts.videoId())
        assertEquals("A short", shorts.overlayMetadata?.primaryText?.content)
    }

    @Test
    fun `shorts lockup falls back to the shorts tap url then the entity id`() {
        val fromUrl = json.decodeFromString<ChannelVideosResponse.ShortsLockupViewModel>(
            """
            {
              "onTap": {
                "innertubeCommand": {
                  "commandMetadata": {
                    "webCommandMetadata": { "url": "/shorts/abcdefghij1?feature=x" }
                  }
                }
              }
            }
            """.trimIndent(),
        )
        assertEquals("abcdefghij1", fromUrl.videoId())

        val fromEntity = json.decodeFromString<ChannelVideosResponse.ShortsLockupViewModel>(
            """{ "entityId": "shorts-video-abcdefghij1" }""",
        )
        assertEquals("abcdefghij1", fromEntity.videoId())

        val unresolvable = json.decodeFromString<ChannelVideosResponse.ShortsLockupViewModel>("{}")
        assertNull(unresolvable.videoId())
    }

    @Test
    fun `reel shelf items are parsed off a rich section renderer`() {
        val response = json.decodeFromString<ChannelVideosResponse>(
            """
            {
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "tabs": [
                    {
                      "tabRenderer": {
                        "selected": true,
                        "content": {
                          "richGridRenderer": {
                            "contents": [
                              {
                                "richSectionRenderer": {
                                  "content": {
                                    "reelShelfRenderer": {
                                      "items": [
                                        {
                                          "shortsLockupViewModel": {
                                            "onTap": {
                                              "innertubeCommand": {
                                                "reelWatchEndpoint": { "videoId": "abcdefghij1" }
                                              }
                                            }
                                          }
                                        }
                                      ]
                                    }
                                  }
                                }
                              }
                            ]
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        val items = response.contents
            ?.twoColumnBrowseResultsRenderer
            ?.tabs
            ?.first()
            ?.tabRenderer
            ?.content
            ?.richGridRenderer
            ?.contents
            .orEmpty()

        val shelfItems = items.first().richSectionRenderer?.content?.reelShelfRenderer?.items.orEmpty()
        assertEquals(1, shelfItems.size)
        assertEquals("abcdefghij1", shelfItems.first().shortsLockupViewModel?.videoId())
    }
}
