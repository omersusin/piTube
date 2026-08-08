package com.omersusin.pitube.data

data class VttCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

object WebVttParser {
    private val TIMESTAMP = Regex("""(\d+):(\d{2})(?::(\d{2}))?.,""")
    private val TAG = Regex("<[^>]*>")

    fun parse(vtt: String): List<VttCue> {
        val lines = vtt.lines()
        val cues = mutableListOf<VttCue>()
        var i = 0

        while (i < lines.size) {
            val arrow = lines[i].indexOf("-->")
            if (arrow < 0) {
                i++
                continue
            }
            val start = parseTimestamp(lines[i].substring(0, arrow))
            val end = parseTimestamp(lines[i].substring(arrow + 3))
            i++

            val payload = buildString {
                while (i < lines.size && lines[i].isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(lines[i])
                    i++
                }
            }
            if (start == null || end == null || end <= start) continue

            val text = cleanCueText(payload)
            if (text.isEmpty()) continue

            val previous = cues.lastOrNull()
            if (previous != null && previous.text == text && start <= previous.endMs) {
                cues[cues.lastIndex] = previous.copy(endMs = maxOf(previous.endMs, end))
            } else {
                cues.add(VttCue(start, end, text))
            }
        }

        return cues.sortedBy { it.startMs }
    }

    private fun parseTimestamp(raw: String): Long? {
        val match = TIMESTAMP.find(raw) ?: return null
        val (first, second, third, fraction) = match.destructured
        val millis = fraction.padEnd(3, '0').toLong()
        return if (third.isEmpty()) {
            first.toLong() * 60_000 + second.toLong() * 1_000 + millis
        } else {
            first.toLong() * 3_600_000 + second.toLong() * 60_000 + third.toLong() * 1_000 + millis
        }
    }

    private fun cleanCueText(raw: String): String =
        TAG.replace(raw, "")
            .replace("&lrm;", "")
            .replace("&rlm;", "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    fun cueAt(cues: List<VttCue>, positionMs: Long): VttCue? =
        cues.firstOrNull { positionMs in it.startMs until it.endMs }
}
