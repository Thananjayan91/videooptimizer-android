package com.videooptimizer.app

data class ExportedItem(
    val id: String,
    val originalName: String,
    val originalUriString: String,
    val platform: String,
    val uriString: String,
    val originalFileSize: Long,
    val outputFileSize: Long,
    val dateExported: Long
)
