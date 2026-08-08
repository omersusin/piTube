package com.omersusin.pitube.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omersusin.pitube.data.StreamInfo
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescriptionBottomSheet(
    streamInfo: StreamInfo,
    currentPositionMs: Long,
    onTimestampClick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header with share button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, streamInfo.title)
                            putExtra(Intent.EXTRA_TEXT, buildString {
                                appendLine(streamInfo.title)
                                appendLine()
                                appendLine(streamInfo.description)
                                appendLine()
                                appendLine("https://www.youtube.com/watch?v=${streamInfo.hls?.substringAfter("v=")}")
                            })
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            }

            // Description text with clickable timestamps

            // Description text with clickable timestamps
            item {
                val description = streamInfo.description
                if (description.isNotBlank()) {
                    val annotatedString = buildAnnotatedString {
                        val lines = description.lines()
                        lines.forEachIndexed { lineIndex, line ->
                            val trimmedLine = line.trim()
                            if (trimmedLine.isNotBlank()) {
                                // Parse links and timestamps
                                val parsed = parseDescriptionLine(trimmedLine)
                                parsed.forEach { segment ->
                                    when {
                                        segment.isTimestamp -> {
                                            pushStringAnnotation(tag = "TIMESTAMP", annotation = segment.timestampMs.toString())
                                            withStyle(
                                                SpanStyle(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    textDecoration = TextDecoration.Underline,
                                                    fontSize = 14.sp
                                                )
                                            ) {
                                                append(segment.text)
                                            }
                                            pop()
                                        }
                                        segment.isLink -> {
                                            pushStringAnnotation(tag = "URL", annotation = segment.url)
                                            withStyle(
                                                SpanStyle(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    textDecoration = TextDecoration.Underline,
                                                    fontSize = 14.sp
                                                )
                                            ) {
                                                append(segment.text)
                                            }
                                            pop()
                                        }
                                        else -> {
                                            withStyle(SpanStyle(fontSize = 14.sp)) {
                                                append(segment.text)
                                            }
                                        }
                                    }
                                }
                                if (lineIndex < lines.size - 1) {
                                    append("\n")
                                }
                            }
                        }
                    }

                    Text(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clickable { offset ->
                                // Handle timestamp clicks
                                annotatedString.getStringAnnotations("TIMESTAMP", offset, offset)
                                    .firstOrNull()?.let { annotation ->
                                        val timestampMs = annotation.item.toLongOrNull() ?: 0L
                                        onTimestampClick(timestampMs)
                                        onDismiss()
                                    }
                                // Handle URL clicks
                                annotatedString.getStringAnnotations("URL", offset, offset)
                                    .firstOrNull()?.let { annotation ->
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                            }
                    )
                }
            }

            // Hashtags
            item {
                val hashtags = extractHashtags(streamInfo.description)
                if (hashtags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        hashtags.take(5).forEach { hashtag ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = hashtag,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Copy button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("description", streamInfo.description))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text("Copy Description")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private data class DescriptionSegment(
    val text: String,
    val isTimestamp: Boolean = false,
    val isLink: Boolean = false,
    val timestampMs: Long = 0,
    val url: String = ""
)

private fun parseDescriptionLine(line: String): List<DescriptionSegment> {
    val segments = mutableListOf<DescriptionSegment>()
    val timestampPattern = Regex("""\b(\d{1,2}:)?\d{1,2}:\d{2}\b""")
    val urlPattern = Regex("""https?://[^\s]+""")

    var lastIndex = 0
    val matches = mutableListOf<Pair<Int, MatchResult>>()

    timestampPattern.findAll(line).forEach { matches.add(it.range.first to it) }
    urlPattern.findAll(line).forEach { matches.add(it.range.first to it) }

    matches.sortBy { it.first }

    for ((_, match) in matches) {
        if (match.range.first > lastIndex) {
            segments.add(DescriptionSegment(line.substring(lastIndex, match.range.first)))
        }
        val matchText = match.value
        when {
            timestampPattern.matches(matchText) -> {
                segments.add(DescriptionSegment(
                    text = matchText,
                    isTimestamp = true,
                    timestampMs = parseTimestampToMs(matchText)
                ))
            }
            urlPattern.matches(matchText) -> {
                segments.add(DescriptionSegment(
                    text = matchText,
                    isLink = true,
                    url = matchText
                ))
            }
        }
        lastIndex = match.range.last + 1
    }

    if (lastIndex < line.length) {
        segments.add(DescriptionSegment(line.substring(lastIndex)))
    }

    return if (segments.isEmpty()) listOf(DescriptionSegment(line)) else segments
}

private fun parseTimestampToMs(timestamp: String): Long {
    val parts = timestamp.split(":")
    return when (parts.size) {
        3 -> {
            val hours = parts[0].toLongOrNull() ?: 0
            val minutes = parts[1].toLongOrNull() ?: 0
            val seconds = parts[2].toLongOrNull() ?: 0
            (hours * 3600 + minutes * 60 + seconds) * 1000
        }
        2 -> {
            val minutes = parts[0].toLongOrNull() ?: 0
            val seconds = parts[1].toLongOrNull() ?: 0
            (minutes * 60 + seconds) * 1000
        }
        else -> 0
    }
}

private fun extractHashtags(description: String): List<String> {
    return Regex("""#\w+""").findAll(description).map { it.value }.toList()
}

private fun formatViewCount(views: Long): String {
    return when {
        views >= 1_000_000_000 -> String.format("%.1fB", views / 1_000_000_000.0)
        views >= 1_000_000 -> String.format("%.1fM", views / 1_000_000.0)
        views >= 1_000 -> String.format("%.1fK", views / 1_000.0)
        else -> views.toString()
    }
}
