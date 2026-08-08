package com.omersusin.pitube.utils

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object FormatUtils {
    
    fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }
    
    fun formatDurationMs(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
    
    fun formatViewCount(count: Long): String {
        return when {
            count >= 1_000_000_000 -> DecimalFormat("#.##B").format(count / 1_000_000_000.0)
            count >= 1_000_000 -> DecimalFormat("#.##M").format(count / 1_000_000.0)
            count >= 1_000 -> DecimalFormat("#.##K").format(count / 1_000.0)
            else -> count.toString()
        }
    }
    
    fun formatLikeCount(count: Int): String {
        return when {
            count >= 1_000_000 -> DecimalFormat("#.##M").format(count / 1_000_000.0)
            count >= 1_000 -> DecimalFormat("#.##K").format(count / 1_000.0)
            else -> count.toString()
        }
    }
    
    fun formatRelativeTime(timestampMillis: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestampMillis
        
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
            diff < TimeUnit.DAYS.toMillis(30) -> "${TimeUnit.MILLISECONDS.toDays(diff) / 7}w ago"
            diff < TimeUnit.DAYS.toMillis(365) -> "${TimeUnit.MILLISECONDS.toDays(diff) / 30}mo ago"
            else -> "${TimeUnit.MILLISECONDS.toDays(diff) / 365}y ago"
        }
    }
    
    fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            date?.let { outputFormat.format(it) } ?: dateString
        } catch (e: Exception) {
            dateString
        }
    }
    
    fun formatNumber(number: Number): String {
        return DecimalFormat("#,###").format(number)
    }
}
