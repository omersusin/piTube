package com.omersusin.pitube.innertube.models

import kotlin.math.ceil
import kotlin.math.min

/**
 * A storyboard frameset (sprite sheet) for a video, parsed from YouTube's
 * `playerStoryboardSpecRenderer.spec` field.
 *
 * Ported from NewPipe's Frameset/StreamInfo parser: the spec is
 * `urlTemplate|#w#h#count#cols#rows#...|#...` where each fragment after the
 * URL describes one resolution level of the storyboard.
 */
data class StoryboardFrameset(
    val urls: List<String>,
    val frameWidth: Int,
    val frameHeight: Int,
    val totalCount: Int,
    val durationPerFrame: Int,
    val framesPerPageX: Int,
    val framesPerPageY: Int,
) {
    data class FrameBounds(
        val urlIndex: Int,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    val framesPerStoryboard: Int
        get() = framesPerPageX * framesPerPageY

    /**
     * Returns the sprite-sheet URL index and pixel bounds of the frame that
     * covers [positionMs], or null when the position is out of range.
     */
    fun frameBoundsAt(positionMs: Long): FrameBounds? {
        if (totalCount <= 0 || durationPerFrame <= 0) return null
        val absoluteFrameNumber =
            min((positionMs / durationPerFrame).toInt(), totalCount)
        val relativeFrameNumber = absoluteFrameNumber % framesPerStoryboard
        val rowIndex = relativeFrameNumber / framesPerPageX
        val columnIndex = relativeFrameNumber % framesPerPageX
        return FrameBounds(
            urlIndex = absoluteFrameNumber / framesPerStoryboard,
            left = columnIndex * frameWidth,
            top = rowIndex * frameHeight,
            right = columnIndex * frameWidth + frameWidth,
            bottom = rowIndex * frameHeight + frameHeight,
        )
    }

    companion object {
        /**
         * Parses a `playerStoryboardSpecRenderer.spec` string into resolved
         * framesets. Returns an empty list when no usable spec is present.
         */
        fun parseSpec(spec: String): List<StoryboardFrameset> {
            val fragments = spec.split("|")
            if (fragments.size < 2) return emptyList()
            val urlTemplate = fragments[0]
            if (urlTemplate.isBlank()) return emptyList()

            val result = mutableListOf<StoryboardFrameset>()
            for (index in 1 until fragments.size) {
                val fields = fragments[index]
                    .split("#")
                    .mapNotNull { it.takeIf(String::isNotBlank) }
                if (fields.size < 7) continue
                val width = fields[0].toIntOrNull() ?: continue
                val height = fields[1].toIntOrNull() ?: continue
                val totalCount = fields[2].toIntOrNull() ?: continue
                if (totalCount <= 0) continue
                val cols = fields[3].toIntOrNull() ?: continue
                val rows = fields[4].toIntOrNull() ?: continue
                if (cols <= 0 || rows <= 0) continue
                val durationPerFrame = fields[5].toIntOrNull() ?: continue
                if (durationPerFrame <= 0) continue
                val pageName = fields[6]
                val sigh = fields[7].takeIf { it != "default" } ?: ""

                val baseUrl =
                    urlTemplate
                        .replace("\$L", (index - 1).toString())
                        .replace("\$N", pageName)
                val urls =
                    if (baseUrl.contains("\$M")) {
                        val totalPages =
                            ceil(totalCount.toDouble() / (cols * rows).toDouble())
                                .toInt()
                        (0 until totalPages).map { page ->
                            baseUrl.replace("\$M", page.toString()) + sighUrlSuffix(sigh)
                        }
                    } else {
                        listOf(baseUrl + sighUrlSuffix(sigh))
                    }

                result.add(
                    StoryboardFrameset(
                        urls = urls,
                        frameWidth = width,
                        frameHeight = height,
                        totalCount = totalCount,
                        durationPerFrame = durationPerFrame,
                        framesPerPageX = cols,
                        framesPerPageY = rows,
                    ),
                )
            }
            return result
        }

        private fun sighUrlSuffix(sigh: String): String =
            if (sigh.isBlank()) "" else "&sigh=$sigh"
    }
}