package com.example.ridevault

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000L ->
            String.format("%.1f MB", bytes / 1_000_000.0)

        bytes >= 1_000L ->
            String.format("%.1f KB", bytes / 1_000.0)

        else -> "$bytes B"
    }
}

fun formatFitTimestamp(filename: String): String {
    return try {
        val timestamp = filename.removeSuffix(".fit")
        val parsed = LocalDateTime.parse(
            timestamp,
            DateTimeFormatter.ofPattern(
                "yyyy-MM-dd-HH-mm-ss",
                Locale.US
            )
        )

        parsed.format(
            DateTimeFormatter.ofPattern(
                "yyyy-MM-dd HH:mm:ss",
                Locale.US
            )
        )
    } catch (_: Exception) {
        filename.removeSuffix(".fit")
    }
}
