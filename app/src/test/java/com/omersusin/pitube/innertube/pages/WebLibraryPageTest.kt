package com.omersusin.pitube.innertube.pages

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLibraryPageTest {

    private fun channelRendererMember(id: String, name: String): String =
        """"channelRenderer":{"channelId":"$id","title":{"simpleText":"$name"},"thumbnail":{"thumbnails":[{"url":"//$id","width":80,"height":80}]}}"""

    private fun channelRendererObject(id: String, name: String): String = "{${channelRendererMember(id, name)}}"

    @Test
    fun `collects channels from every wrapper, including shelves`() {
        val fixture = """
            {
              "contents": {
                "sectionListRenderer": {
                  "contents": [{
                    "richGridRenderer": {
                      "contents": [
                        {"richShelfRenderer": {"content": {"horizontalListRenderer": {"items": [__SUGGESTED__]}}}},
                        {"carouselLockupContainer": {"spacing": {"value": "8"}, __REAL1__}},
                        {"gridRenderer": {"items": [__REAL2__, {"gridChannelRenderer": {"channelId": "UCrealsubscription3", "title": {"simpleText": "Real Channel Three"}}}]}}
                      ]
                    }
                  }]
                }
              }
            }
        """.trimIndent()
            .replace("__SUGGESTED__", channelRendererObject("UCsuggestedchannel", "Suggested Channel"))
            .replace("__REAL1__", channelRendererMember("UCrealsubscription1", "Real Channel One"))
            .replace("__REAL2__", channelRendererObject("UCrealsubscription2", "Real Channel Two"))
        val channels = Json.parseToJsonElement(fixture).toRemoteChannels()
        assertEquals(
            setOf(
                "UCsuggestedchannel",
                "UCrealsubscription1",
                "UCrealsubscription2",
                "UCrealsubscription3",
            ),
            channels.map { it.id }.toSet(),
        )
        assertEquals("Real Channel One", channels.first { it.id == "UCrealsubscription1" }.name)
    }

    @Test
    fun `channels inside a shelfRenderer are collected`() {
        val fixture = """
            {
              "contents": [{
                "sectionListRenderer": {
                  "contents": [{
                    "carouselLockupContainer": {
                      "shelfRenderer": {
                        "title": {"simpleText": "Channels you may like"},
                        "content": {
                          "horizontalListRenderer": {
                            "items": [__SHELF__]
                          }
                        }
                      },
                      "gridRenderer": {
                        "items": [__REALGRID__]
                      }
                    }
                  }]
                }
              }]
            }
        """.trimIndent()
            .replace("__SHELF__", channelRendererObject("UCshelfchannelaa", "Shelf Channel"))
            .replace("__REALGRID__", channelRendererObject("UCrealgridchannel", "Real Grid Channel"))
        val channels = Json.parseToJsonElement(fixture).toRemoteChannels()
        assertEquals(
            setOf("UCshelfchannelaa", "UCrealgridchannel"),
            channels.map { it.id }.toSet(),
        )
    }

    @Test
    fun `collects the signed FEchannels grid shape from a live response`() {
        val fixture = """
            {
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [
                            {
                              "itemSectionRenderer": {
                                "contents": [{
                                  "shelfRenderer": {
                                    "content": {
                                      "expandedShelfContentsRenderer": {
                                        "items": [
                                          __CHANNEL1__,
                                          __CHANNEL2__
                                        ]
                                      }
                                    }
                                  }
                                }]
                              }
                            },
                            {"continuationItemRenderer": {"continuationEndpoint": {"continuationCommand": {"token": "tok"}}}}
                          ]
                        }
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()
            .replace("__CHANNEL1__", channelRendererObject("UCUnwimZlOXfAedrmRfguS1g", "+90"))
            .replace("__CHANNEL2__", channelRendererObject("UCSeY5HzX4Pi3S8D88XFl3uA", "1Echer"))
        val channels = Json.parseToJsonElement(fixture).toRemoteChannels()
        assertEquals(
            listOf("UCUnwimZlOXfAedrmRfguS1g", "UCSeY5HzX4Pi3S8D88XFl3uA"),
            channels.map { it.id },
        )
        assertEquals("+90", channels[0].name)
        assertEquals("https://UCUnwimZlOXfAedrmRfguS1g", channels[0].thumbnail)
        assertTrue(channels.all { it.thumbnail.startsWith("https://") })
    }
}