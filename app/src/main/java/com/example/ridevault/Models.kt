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
