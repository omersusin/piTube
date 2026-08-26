package com.omersusin.pitube.data.local

import com.omersusin.pitube.data.model.Video
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

object RssFeedParser {
    fun parse(xml: String, avatarUrl: String?): List<Video> {
        val videos = mutableListOf<Video>()
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        var inEntry = false
        var inAuthor = false
        var videoId: String? = null
        var channelId: String? = null
        var title: String? = null
        var author: String? = null
        var publishedAtMs: Long? = null
        var thumbnailUrl: String? = null
        var description: String? = null
        var viewCount: Long? = null
        fun reset() {
            videoId = null; channelId = null; title = null; author = null
            publishedAtMs = null; thumbnailUrl = null; description = null; viewCount = null
        }
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> { inEntry = true; reset() }
                    "author" -> inAuthor = true
                    "yt:videoId" -> if (inEntry) videoId = runCatching { parser.nextText().trim() }.getOrNull()
                    "yt:channelId" -> if (inEntry) channelId = runCatching { parser.nextText().trim() }.getOrNull()
                    "title" -> if (inEntry && title == null) title = runCatching { parser.nextText().trim() }.getOrNull()
                    "name" -> if (inEntry && inAuthor) author = runCatching { parser.nextText().trim() }.getOrNull()
                    "published" -> if (inEntry) publishedAtMs = runCatching { java.time.OffsetDateTime.parse(parser.nextText().trim()).toInstant().toEpochMilli() }.getOrNull()
                    "media:thumbnail" -> if (inEntry && thumbnailUrl == null) thumbnailUrl = parser.getAttributeValue(null, "url")
                    "media:description" -> if (inEntry) description = runCatching { parser.nextText() }.getOrNull()
                    "media:statistics" -> if (inEntry) viewCount = parser.getAttributeValue(null, "views")?.toLongOrNull()
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "author" -> inAuthor = false
                    "entry" -> {
                        inEntry = false
                        val id = videoId
                        if (!id.isNullOrBlank()) {
                            videos.add(
                                Video(
                                    id = id,
                                    title = title.orEmpty(),
                                    channelName = author.orEmpty(),
                                    channelId = channelId.orEmpty(),
                                    thumbnailUrl = thumbnailUrl.orEmpty(),
                                    duration = 0,
                                    viewCount = viewCount ?: 0L,
                                    uploadDate = "",
                                    timestamp = publishedAtMs ?: 0L,
                                    description = description.orEmpty(),
                                    channelThumbnailUrl = avatarUrl.orEmpty()
                                )
                            )
                        }
                        reset()
                    }
                }
            }
            event = parser.next()
        }
        return videos
    }
}
