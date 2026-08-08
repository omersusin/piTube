package com.omersusin.pitube.data

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

data class ChannelRssEntry(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String?,
    val publishedAtMillis: Long,
    val viewCount: Long,
    val description: String?
)

data class ChannelRssFeed(
    val channelName: String?,
    val entries: List<ChannelRssEntry>
) {
    companion object {
        val EMPTY = ChannelRssFeed(null, emptyList())
    }
}

object ChannelRssParser {
    private const val TAG = "ChannelRssParser"
    const val MAX_NEW_PER_CHANNEL = 5

    fun parse(xml: String): ChannelRssFeed =
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            readFeed(parser)
        } catch (e: Exception) {
            Log.e(TAG, "Could not create the RSS parser", e)
            ChannelRssFeed.EMPTY
        }

    internal fun readFeed(parser: XmlPullParser): ChannelRssFeed {
        val entries = mutableListOf<ChannelRssEntry>()
        var channelName: String? = null
        try {
            var insideEntry = false
            var videoId: String? = null
            var title: String? = null
            var thumbnail: String? = null
            var description: String? = null
            var publishedAt = 0L
            var viewCount = 0L

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("entry", ignoreCase = true)) {
                            insideEntry = true
                            videoId = null
                            title = null
                            thumbnail = null
                            description = null
                            publishedAt = 0L
                            viewCount = 0L
                        } else if (insideEntry) {
                            when {
                                tagName.equals("videoId", ignoreCase = true) -> videoId = parser.nextText()
                                tagName.equals("title", ignoreCase = true) && title == null -> title = parser.nextText()
                                tagName.equals("thumbnail", ignoreCase = true) && thumbnail == null -> thumbnail = parser.getAttributeValue(null, "url")
                                tagName.equals("description", ignoreCase = true) && description == null -> description = parser.nextText()
                                tagName.equals("published", ignoreCase = true) && publishedAt == 0L -> publishedAt = parseTimestamp(parser.nextText())
                                tagName.equals("statistics", ignoreCase = true) && viewCount == 0L -> viewCount = parser.getAttributeValue(null, "views")?.toLongOrNull() ?: 0L
                            }
                        } else if (tagName.equals("name", ignoreCase = true) && channelName == null) {
                            channelName = parser.nextText()
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("entry", ignoreCase = true)) {
                            insideEntry = false
                            val id = videoId
                            val entryTitle = title
                            if (!id.isNullOrEmpty() && !entryTitle.isNullOrEmpty()) {
                                entries += ChannelRssEntry(
                                    videoId = id,
                                    title = entryTitle,
                                    thumbnailUrl = thumbnail,
                                    publishedAtMillis = publishedAt,
                                    viewCount = viewCount,
                                    description = description,
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing RSS XML", e)
        }
        return ChannelRssFeed(channelName = channelName?.takeIf { it.isNotBlank() }, entries = entries)
    }

    private fun parseTimestamp(value: String?): Long {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return 0L
        return try {
            OffsetDateTime.parse(text).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            0L
        }
    }

    fun newEntriesSince(entries: List<ChannelRssEntry>, lastVideoId: String?): List<ChannelRssEntry> {
        if (entries.isEmpty()) return emptyList()
        if (lastVideoId == null) return entries.take(MAX_NEW_PER_CHANNEL)
        val knownIndex = entries.indexOfFirst { it.videoId == lastVideoId }
        return when {
            knownIndex == 0 -> emptyList()
            knownIndex > 0 -> entries.take(minOf(knownIndex, MAX_NEW_PER_CHANNEL))
            else -> entries.take(MAX_NEW_PER_CHANNEL)
        }
    }
}
