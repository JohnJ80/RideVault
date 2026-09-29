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

fun normalizeCourseFitFilename(filename: String): String {
    var baseName = filename

    while (baseName.endsWith(".fit", ignoreCase = true)) {
        baseName = baseName.substring(0, baseName.length - 4)
    }

    return "$baseName.fit"
}

fun normalizeDownloadRelativePath(path: String): String {
    val trimmed = path.trim()

    if (trimmed.isEmpty()) {
        return trimmed
    }

    return trimmed.trimEnd('/') + "/"
}

fun downloadRelativePathQueryValues(path: String): List<String> {
    val normalized = normalizeDownloadRelativePath(path)
    val withoutTrailingSlash = normalized.trimEnd('/')

    return if (withoutTrailingSlash == normalized) {
        listOf(normalized)
    } else {
        listOf(normalized, withoutTrailingSlash)
    }
}
