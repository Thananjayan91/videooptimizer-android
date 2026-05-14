package com.videooptimizer.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object FileManager {

    fun saveToVideoOptimizer(context: Context, sourceFile: File, filename: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.MIME_TYPE, "video/quicktime")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoOptimizer")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            context.contentResolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "VideoOptimizer"
            ).also { it.mkdirs() }
            val dest = File(dir, filename)
            sourceFile.copyTo(dest, overwrite = true)
            Uri.fromFile(dest)
        }
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (e: Exception) { 0L }
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }

    fun buildOutputFilename(originalName: String, platform: Platform): String {
        val base = originalName.substringBeforeLast(".")
        val suffix = platform.label.lowercase().replace(" / ", "_").replace(" ", "_")
        return "${base}_${suffix}.mov"
    }
}
