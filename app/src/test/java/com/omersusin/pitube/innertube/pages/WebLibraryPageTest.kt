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
    fun `collects grid behind unexpected wrapper keys while skipping you-may-like shelves`() {
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
            setOf("UCrealsubscription1", "UCrealsubscription2", "UCrealsubscription3"),
            channels.map { it.id }.toSet(),
        )
        assertTrue(channels.none { it.id == "UCsuggestedchannel" })
        assertEquals("Real Channel One", channels.first { it.id == "UCrealsubscription1" }.name)
    }

    @Test
    fun `grid sharing an object with a shelf is still walked`() {
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
        assertEquals(listOf("UCrealgridchannel"), channels.map { it.id })
        assertEquals("Real Grid Channel", channels.single().name)
    }
}