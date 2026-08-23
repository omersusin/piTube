package com.omersusin.pitube.innertube.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryboardFramesetTest {

    // Realistic WEB spec shape: template | L0 (interval 0 -> unusable) |
    // 80x45 grid of 10x10 @2s | 160x90 grid of 5x5 @2s
    private val spec =
        "https://i9.ytimg.com/sb/abcDefGhIjK/storyboard3_L\$L/\$N.jpg?sqp=x|" +
            "48#27#100#10#10#0#default#rs\$Sig0|" +
            "80#45#108#10#10#2000#M\$M#rs\$Sig1|" +
            "160#90#108#5#5#2000#M\$M#rs\$Sig2"

    @Test
    fun `parseSpec skips unusable zero-interval level and parses the rest`() {
        val framesets = StoryboardFrameset.parseSpec(spec)
        assertEquals(2, framesets.size)
        assertEquals(80, framesets[0].frameWidth)
        assertEquals(160, framesets[1].frameWidth)
    }

    @Test
    fun `parseSpec expands M placeholder pages and appends sigh`() {
        val fs = StoryboardFrameset.parseSpec(spec)[1]
        // totalCount=108, 5x5 per page -> ceil(108/25)=5 pages
        assertEquals(5, fs.urls.size)
        assertTrue(fs.urls[0].contains("storyboard3_L2/M0.jpg"))
        assertTrue(fs.urls[4].contains("storyboard3_L2/M4.jpg"))
        assertTrue(fs.urls[0].endsWith("&sigh=rs\$Sig2"))
    }

    @Test
    fun `parseSpec returns empty for blank or single-fragment specs`() {
        assertTrue(StoryboardFrameset.parseSpec("").isEmpty())
        assertTrue(StoryboardFrameset.parseSpec("https://example.com/template").isEmpty())
    }

    @Test
    fun `frameBoundsAt maps position to cell and sheet`() {
        val fs = StoryboardFrameset.parseSpec(spec)[1] // 160x90, 5x5, 2s/frame
        // frame #7 -> row 1, col 2, same page
        val b = fs.frameBoundsAt(positionMs = 7 * 2000L + 500L)
        assertNotNull(b)
        b!!
        assertEquals(0, b.urlIndex)
        assertEquals(2 * 160, b.left)
        assertEquals(1 * 90, b.top)
        assertEquals(3 * 160, b.right)
        assertEquals(2 * 90, b.bottom)
    }

    @Test
    fun `frameBoundsAt advances urlIndex past one full page`() {
        val fs = StoryboardFrameset.parseSpec(spec)[1] // 25 frames per page
        val b = fs.frameBoundsAt(positionMs = 26 * 2000L)
        assertNotNull(b)
        assertEquals(1, b!!.urlIndex)
        assertEquals(1 * 160, b.left) // relative frame #1 -> col 1
    }

    @Test
    fun `frameBoundsAt clamps end position to last valid frame`() {
        val fs = StoryboardFrameset.parseSpec(spec)[1] // totalCount=108 -> last index 107
        val b = fs.frameBoundsAt(positionMs = Long.MAX_VALUE / 2)
        assertNotNull(b)
        b!!
        // 107 % 25 = 7 -> row 1 col 2; page floor(107/25) = 4
        assertEquals(4, b.urlIndex)
        assertEquals(2 * 160, b.left)
        assertEquals(1 * 90, b.top)
    }

    @Test
    fun `frameBoundsAt returns null for empty frameset`() {
        val fs = StoryboardFrameset(urls = listOf("u"), frameWidth = 80, frameHeight = 45,
            totalCount = 0, durationPerFrame = 2000, framesPerPageX = 5, framesPerPageY = 5)
        assertNull(fs.frameBoundsAt(0L))
    }
}
