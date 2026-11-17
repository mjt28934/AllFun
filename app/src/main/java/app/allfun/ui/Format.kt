package app.allfun.ui

import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDuration(ms: Long?): String {
    if (ms == null || ms <= 0L) return ""
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatClock(ms: Long): String = formatDuration(ms)

/**
 * "1 video", "4 episodes". Counts are shown in a lot of places, so pluralising once
 * here keeps the shelf, detail page and source lists from disagreeing.
 */
fun countLabel(count: Int, unit: String): String {
    val word = unit.lowercase(Locale.US)
    return if (count == 1) "1 $word" else "$count ${word}s"
}

fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

/** "just now", "12m ago", "3h ago", "2d ago" — used by Continue and Recently opened. */
fun relativeTime(millis: Long?, now: Long = System.currentTimeMillis()): String {
    if (millis == null || millis <= 0L) return ""
    val delta = (now - millis).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 30 -> "${days}d ago"
        days < 365 -> "${days / 30}mo ago"
        else -> "${days / 365}y ago"
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "${value.toInt()} ${units[index]}"
    else String.format(Locale.US, "%.1f %s", value, units[index])
}

/** "42%" for a 0..1 fraction; empty when there is no progress. */
fun percentLabel(fraction: Float?): String =
    if (fraction == null || fraction <= 0f) "" else "${(fraction * 100).toInt().coerceIn(1, 100)}%"
