package com.videooptimizer.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

sealed class OptimizeResult {
    data class Success(val outputFile: File) : OptimizeResult()
    data class Error(val message: String) : OptimizeResult()
}

object VideoOptimizer {

    private const val TAG = "VideoOptimizer"

    private data class SourceInfo(
        val width: Int,
        val height: Int,
        val fps: Float,
        val bitrateKbps: Long,
        val videoCodec: String,      // "h264", "hevc", or "" = unknown
        val isHdr: Boolean,
        val audioSampleRateHz: Int,
        val durationMs: Long
    )

    fun optimize(
        context: Context,
        inputUri: Uri,
        profile: EncodingProfile,
        qualityMultiplier: Float = 1.0f,
        onProgress: (Int) -> Unit,
        onComplete: (OptimizeResult) -> Unit
    ) {
        val inputPath = getRealPathFromUri(context, inputUri)
        if (inputPath == null) {
            onComplete(OptimizeResult.Error("Could not read input file"))
            return
        }

        val outputDir = File(context.cacheDir, "optimized").also { it.mkdirs() }
        val outputFile = File(outputDir, "optimized_${System.currentTimeMillis()}.mov")

        val src = getSourceInfo(context, inputUri)
        Log.d(TAG, "Source: ${src.width}x${src.height} ${src.fps}fps " +
            "${src.bitrateKbps}kbps codec=${src.videoCodec} " +
            "HDR=${src.isHdr} audio=${src.audioSampleRateHz}Hz")

        val ffmpegCmd = buildFFmpegCommand(inputPath, outputFile.absolutePath, profile, src, qualityMultiplier)
        Log.d(TAG, "FFmpeg: $ffmpegCmd")

        FFmpegKit.executeAsync(
            ffmpegCmd,
            { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    onComplete(OptimizeResult.Success(outputFile))
                } else {
                    val logs = session.allLogsAsString
                    Log.e(TAG, "FFmpeg failed: $logs")
                    onComplete(OptimizeResult.Error("Encoding failed. Check logs."))
                }
            },
            { log -> Log.d(TAG, log.message) },
            { stats ->
                val durationMs = src.durationMs
                val progress = if (durationMs > 0) {
                    ((stats.time / durationMs.toFloat()) * 100).toInt().coerceIn(0, 100)
                } else {
                    (stats.videoFrameNumber % 100).toInt()
                }
                onProgress(progress)
            }
        )
    }

    private fun buildFFmpegCommand(
        input: String,
        output: String,
        profile: EncodingProfile,
        src: SourceInfo,
        qualityMultiplier: Float
    ): String {
        val targetKbps = (profile.videoBitrate.removeSuffix("k").toLong() * qualityMultiplier).toLong()
        val effectiveBitrate = if (src.bitrateKbps in 1 until targetKbps) "${src.bitrateKbps}k" else "${targetKbps}k"

        // Only skip video encode when we can confirm source is H.264 (API 34+), not HDR,
        // and already within the profile's resolution/fps/bitrate limits.
        val videoFitsProfile = src.width > 0
            && !src.isHdr
            && src.width <= profile.maxWidth
            && src.height <= profile.maxHeight
            && (src.fps <= 0f || src.fps <= profile.frameRate + 1)
            && (src.bitrateKbps <= 0L || src.bitrateKbps <= targetKbps)
        val skipVideoEncode = videoFitsProfile && src.videoCodec == profile.codec

        // Copy audio if sample rate already matches the profile (no re-encode needed).
        val skipAudioEncode = src.audioSampleRateHz > 0 && src.audioSampleRateHz == profile.audioSampleRate

        val gopSize = profile.frameRate * profile.keyframeInterval

        return buildString {
            append("-i \"$input\" ")

            if (skipVideoEncode) {
                Log.d(TAG, "Video: copy (H.264 SDR, fits profile)")
                append("-c:v copy ")
            } else {
                Log.d(TAG, "Video: transcode → HDR=${src.isHdr} codec=${src.videoCodec} fits=$videoFitsProfile")
                val scale = "scale=" +
                    "'if(gt(iw,ih),min(${profile.maxWidth},iw),-2)':" +
                    "'if(gt(iw,ih),-2,min(${profile.maxHeight},ih))':" +
                    "flags=lanczos"
                append("-vf \"$scale,format=${profile.pixelFormat}\" ")
                val isHevc = profile.codec == "h265"
                val encoder = if (isHevc) "hevc_mediacodec" else "h264_mediacodec"
                append("-c:v $encoder ")
                if (isHevc) append("-tag:v hvc1 ")  // required for Apple/platform compatibility
                append("-b:v $effectiveBitrate ")
                append("-r ${profile.frameRate} ")
                append("-g $gopSize ")
                append("-color_primaries ${profile.colorSpace} ")
                append("-color_trc ${profile.colorSpace} ")
                append("-colorspace ${profile.colorSpace} ")
                append("-color_range tv ")
            }

            if (skipAudioEncode) {
                Log.d(TAG, "Audio: copy (already ${src.audioSampleRateHz}Hz)")
                append("-c:a copy ")
            } else {
                Log.d(TAG, "Audio: transcode ${src.audioSampleRateHz}Hz → ${profile.audioSampleRate}Hz")
                append("-c:a ${profile.audioCodec} ")
                append("-profile:a aac_low ")
                append("-b:a ${profile.audioBitrate} ")
                append("-ar ${profile.audioSampleRate} ")
                append("-ac 2 ")
            }

            // Apple device metadata — social platforms apply less aggressive re-compression
            // to content identified as coming from an iPhone.
            append("-metadata \"com.apple.quicktime.make=Apple\" ")
            append("-metadata \"com.apple.quicktime.model=iPhone 15 Pro\" ")
            append("-metadata \"com.apple.quicktime.software=17.5\" ")
            if (profile.codec == "h265") append("-metadata:s:v:0 \"encoder=HEVC\" ")

            append("-movflags +faststart+use_metadata_tags ")
            append("-f mov ")
            append("-y \"$output\"")
        }
    }

    private fun getSourceInfo(context: Context, uri: Uri): SourceInfo {
        return try {
            val r = MediaMetadataRetriever()
            r.setDataSource(context, uri)

            val width = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f
            val bps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            val audioHz = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            val durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

            val colorStandard = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_STANDARD)?.toIntOrNull() ?: 0
            val colorTransfer = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)?.toIntOrNull() ?: 0
            // Raw values: BT.2020 primaries = 6, ST2084/HDR10 transfer = 6, HLG transfer = 7
            val isHdr = colorStandard == 6 || colorTransfer == 6 || colorTransfer == 7

            // METADATA_KEY_MIMETYPE returns the codec MIME type on many devices ("video/avc", "video/hevc").
            // Falls back to "" (always transcode) when only the container type is returned.
            val mimeType = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            val videoCodec = when {
                mimeType.contains("avc", ignoreCase = true) -> "h264"
                mimeType.contains("hevc", ignoreCase = true) -> "hevc"
                else -> ""
            }

            r.release()
            SourceInfo(width, height, fps, bps / 1000L, videoCodec, isHdr, audioHz, durationMs)
        } catch (e: Exception) {
            Log.w(TAG, "Source info read failed: ${e.message}")
            SourceInfo(0, 0, 0f, 0L, "", false, 0, 0L)
        }
    }

    private fun getRealPathFromUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "input_${System.currentTimeMillis()}.mp4")
            tempFile.outputStream().use { output -> inputStream.copyTo(output) }
            tempFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve URI: ${e.message}")
            null
        }
    }
}
