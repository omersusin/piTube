package com.omersusin.pitube.data.lyrics

import com.omersusin.pitube.utils.TitleDecorationStripper

object LrcParser {
    private val timeTagPattern = Regex("""\[(\d{1,2}):(\d{2})\.(\d{2,3})\]""")
    private val wordTagPattern = Regex("""<(\d{1,2}):(\d{2})\.(\d{2,3})>""")
    private val offsetTagPattern = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)

    fun parse(lrcContent: String): List<LrcLine> {
        // Standard LRC global shift: positive values shift lyrics EARLIER,
        // negative later (per the spec's convention used by most editors).
        val offsetMs = offsetTagPattern.find(normalize(lrcContent))?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val lines = mutableListOf<LrcLine>()
        for (line in normalize(lrcContent).lines()) {
            val trim = line.trim()
            if (trim.isEmpty() || offsetTagPattern.matchEntire(trim) != null) continue
            val times = mutableListOf<Long>()
            var idx = 0
            while (true) {
                val m = timeTagPattern.matchAt(trim, idx) ?: break
                val (mm, ss, cc) = m.destructured
                times.add(parseTime(mm, ss, cc))
                idx = m.range.last + 1
                while (idx < trim.length && trim[idx] == ' ') idx++
            }
            if (times.isEmpty() || idx >= trim.length) continue
            val content = trim.substring(idx).trim()
            if (content.isEmpty()) continue
            val first = times.first() - offsetMs; val repeats = times.drop(1).map { it - offsetMs }
            if (wordTagPattern.containsMatchIn(content)) {
                val spans = mutableListOf<LrcContentSpan>()
                val wMatches = wordTagPattern.findAll(content).toList()
                if (wMatches.isNotEmpty()) {
                    val f = wMatches[0].range.first
                    if (f > 0) {
                        val seg = content.substring(0, f).trim()
                        if (seg.isNotEmpty()) {
                            val nt = parseTime(wMatches[0].groupValues[1], wMatches[0].groupValues[2], wMatches[0].groupValues[3]) - offsetMs
                            spans.add(LrcContentSpan(first, seg, nt - first))
                        }
                    }
                }
                for (i in wMatches.indices) {
                    val cm = wMatches[i]; val (wm, ws, wc) = cm.destructured
                    val ct = parseTime(wm, ws, wc) - offsetMs
                    val nextStart = if (i + 1 < wMatches.size) wMatches[i + 1].range.first else content.length
                    val ts = cm.range.last + 1
                    if (ts < nextStart) {
                        val seg = content.substring(ts, nextStart).trim()
                        if (seg.isNotEmpty()) {
                            val nt = if (i + 1 < wMatches.size) { val (nm, ns, nc) = wMatches[i + 1].destructured; parseTime(nm, ns, nc) } else 0L
                            spans.add(LrcContentSpan(ct, seg, if (nt > 0) nt - ct else 0L))
                        }
                    }
                }
                val clean = TitleDecorationStripper.stripDecorations(
                    content.replace(wordTagPattern, "").replace(Regex("\\s+"), " ").trim()
                )
                lines.add(LrcLine(first.coerceAtLeast(0L), clean, spans))
                repeats.forEach { lines.add(LrcLine(it.coerceAtLeast(0L), clean)) }
            } else {
                val stripped = TitleDecorationStripper.stripDecorations(content)
                if (stripped.isEmpty()) continue
                lines.add(LrcLine(first.coerceAtLeast(0L), stripped))
                repeats.forEach { lines.add(LrcLine(it.coerceAtLeast(0L), stripped)) }
            }
        }
        val sorted = lines.sortedBy { it.timeMs }
        return sorted.mapIndexed { idx, line ->
            val next = if (idx + 1 < sorted.size) sorted[idx + 1].timeMs else line.timeMs + 5000
            if (line.contentSpans.isNotEmpty()) {
                val ns = line.contentSpans.mapIndexed { si, sp -> if (sp.durationMs == 0L && si == line.contentSpans.lastIndex) sp.copy(durationMs = (next - sp.timeMs).coerceAtLeast(500)) else sp }
                line.copy(contentSpans = ns)
            } else line
        }
    }

    // Musixmatch subtitle/translation bodies occasionally carry HTML entities
    // that would otherwise render as literal "&amp;"-style artifacts.
    // &amp; is decoded LAST so double-encoded input isn't over-decoded.
    private fun normalize(s: String) = s
        .replace("\ufeff", "")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
    private fun parseTime(m: String, s: String, c: String): Long {
        val mm = m.toIntOrNull() ?: 0; val ss = s.toIntOrNull() ?: 0; val cc = c.toIntOrNull() ?: 0
        val ms = if (c.length == 2) cc * 10 else cc
        return mm * 60_000L + ss * 1000L + ms
    }
}
