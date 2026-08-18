package com.omersusin.pitube.innertube.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class YouTubeClientTest {
    @Test
    fun webContextMatchesDesktopClientShape() {
        val context = YouTubeClient.WEB.toContext(
            locale = YouTubeLocale(gl = "LB", hl = "en-US"),
            visitorData = null,
            dataSyncId = null,
        )

        assertEquals("https://www.youtube.com", context.client.originalUrl)
        assertEquals("DESKTOP", context.client.platform)
        assertEquals(0, context.client.utcOffsetMinutes)
        assertEquals("LB", context.client.gl)
        assertEquals("en-US", context.client.hl)
    }

    @Test
    fun signedContext_neverAttachesOnBehalfOfUser() {
        // Regression guard: a datasyncId/onBehalfOfUser on signed WEB requests
        // makes YouTube answer personal surfaces with HTTP 401 / empty bodies
        // when the value is stale (verified live Aug 2026). The cookie is the
        // account authority — the user block must stay null regardless of the
        // datasyncId bookkeeping value.
        val context = YouTubeClient.WEB.toContext(
            locale = YouTubeLocale(gl = "LB", hl = "en-US"),
            visitorData = null,
            dataSyncId = "stale-marker",
        )

        assertFalse(Json.encodeToString(context).contains("onBehalfOfUser"))
    }
}
