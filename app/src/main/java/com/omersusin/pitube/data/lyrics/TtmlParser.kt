package com.omersusin.pitube.data.lyrics

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

/**
 * TTML (word-timed) lyrics parser — adapted from vivi-music's TTMLParser.
 * Produces standard enhanced-LRC text (`[mm:ss.SSS]<mm:ss.SSS>word ...`) that
 * [LrcParser] understands, enabling word-level karaoke rendering.
 */
object TtmlParser {

    private data class SpanInfo(
        val text: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val hasTrailingSpace: Boolean,
    )

    private data class ParsedWord(val text: String, val startMs: Long, val endMs: Long)

    private data class ParsedLine(val startMs: Long, val words: List<ParsedWord>, val fallbackText: String)

    fun toEnhancedLrc(ttml: String): String? {
        val lines = parseLines(ttml)
        if (lines.isEmpty()) return null
        return buildString {
            lines.forEach { line ->
                append(formatLineTag(line.startMs))
                if (line.words.isNotEmpty()) {
                    line.words.forEach { w ->
                        append(formatWordTag(w.startMs))
                        append(w.text)
                        append(' ')
                    }
                } else {
                    append(line.fallbackText)
                }
                append('\n')
            }
        }.trim()
    }

    private fun parseLines(ttml: String): List<ParsedLine> = try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val doc = factory.newDocumentBuilder().parse(ttml.byteInputStream())
        val result = mutableListOf<ParsedLine>()
        val pElements = doc.getElementsByTagName("p")
        for (i in 0 until pElements.length) {
            val p = pElements.item(i) as? Element ?: continue
            val begin = p.getAttribute("begin")
            if (begin.isNullOrEmpty()) continue
            val startMs = parseTimeMs(begin)
            val spans = mutableListOf<SpanInfo>()
            var directText = StringBuilder()
            val children = p.childNodes
            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeType) {
                    Node.TEXT_NODE -> directText.append(node.textContent)
                    Node.ELEMENT_NODE -> {
                        val span = node as? Element
                        if (span?.tagName?.lowercase() == "span") {
                            val role = attributeByLocalName(span, "role")
                            if (role == "x-bg" || role == "x-translation" || role == "x-roman") continue
                            val wb = span.getAttribute("begin")
                            val we = span.getAttribute("end")
                            val wt = span.textContent?.trim().orEmpty()
                            if (wt.isNotEmpty() && wb.isNotEmpty() && we.isNotEmpty()) {
                                val nextSibling = node.nextSibling
                                val trailingSpace = nextSibling?.nodeType == Node.TEXT_NODE &&
                                    nextSibling.textContent?.contains(Regex("\\s")) == true
                                spans.add(SpanInfo(wt, parseTimeMs(wb), parseTimeMs(we), trailingSpace))
                            }
                        }
                    }
                }
            }
            val words = mergeSpans(spans)
            val lineText = words.joinToString(" ") { it.text }.ifBlank { directText.toString().trim() }
            if (lineText.isNotEmpty()) result.add(ParsedLine(startMs, words, lineText))
        }
        result
    } catch (_: Exception) {
        emptyList()
    }

    /** Namespace-tolerant attribute lookup (ttm:agent etc.). */
    private fun attributeByLocalName(el: Element, localName: String): String {
        val ns = el.getAttributeNS("http://www.w3.org/ns/ttml#metadata", localName)
        if (ns.isNotEmpty()) return ns
        val attrs = el.attributes ?: return ""
        for (i in 0 until attrs.length) {
            val name = attrs.item(i)?.nodeName ?: continue
            if (name == localName || name.endsWith(":$localName")) return attrs.item(i).nodeValue.orEmpty()
        }
        return ""
    }

    /** Merge syllable spans (no whitespace between them) into whole words. */
    private fun mergeSpans(spans: List<SpanInfo>): List<ParsedWord> {
        if (spans.isEmpty()) return emptyList()
        val words = mutableListOf<ParsedWord>()
        val text = StringBuilder()
        var start = 0L
        var end = 0L
        spans.forEachIndexed { i, s ->
            if (i == 0) {
                text.append(s.text); start = s.startTimeMs; end = s.endTimeMs
            } else if (spans[i - 1].hasTrailingSpace) {
                if (text.isNotBlank()) words.add(ParsedWord(text.toString().trim(), start, end))
                text.clear(); text.append(s.text); start = s.startTimeMs; end = s.endTimeMs
            } else {
                text.append(s.text); end = s.endTimeMs
            }
        }
        if (text.isNotBlank()) words.add(ParsedWord(text.toString().trim(), start, end))
        return words
    }

    private fun parseTimeMs(raw: String): Long = try {
        if (raw.contains(':')) {
            val parts = raw.split(':')
            val h = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
            val m = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val sec = parts.getOrNull(2)?.toDoubleOrNull() ?: parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val seconds = if (parts.size >= 3) m * 60 + sec else h * 60 + m
            (seconds * 1000).toLong()
        } else {
            ((raw.toDoubleOrNull() ?: 0.0) * 1000).toLong()
        }
    } catch (_: Exception) {
        0L
    }

    private fun formatLineTag(ms: Long): String {
        val totalSec = ms / 1000
        return "[%02d:%02d.%03d]".format(totalSec / 60, totalSec % 60, ms % 1000)
    }

    private fun formatWordTag(ms: Long): String {
        val totalSec = ms / 1000
        return "<%02d:%02d.%03d>".format(totalSec / 60, totalSec % 60, ms % 1000)
    }
}
