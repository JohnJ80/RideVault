package com.example.ridevault

data class FitFileInfo(
    val handle: Int,
    val name: String,
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long
)

data class DownloadSummary(
    val files: List<FitFileInfo>,
    val verifiedCount: Int
)

data class CourseFileInfo(
    val handle: Int,
    val name: String,
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long
)

data class CourseBackupSummary(
    val files: List<CourseFileInfo>,
    val verifiedCount: Int
) {
    val totalSizeBytes: Long =
        files.sumOf { it.sizeBytes }
}
