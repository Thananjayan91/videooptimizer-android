package com.videooptimizer.app

enum class Platform(val label: String) {
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    YOUTUBE("YouTube"),
    YOUTUBE_SHORTS("YouTube Shorts"),
    TWITTER("Twitter / X"),
    WHATSAPP_STORY("WhatsApp Story")
}

data class EncodingProfile(
    val platform: Platform,
    val videoBitrate: String,
    val maxWidth: Int,
    val maxHeight: Int,
    val frameRate: Int,
    val audioBitrate: String,
    val codec: String = "h264",           // "h264" or "h265"
    val videoProfile: String = "high",    // baseline / main / high
    val pixelFormat: String = "yuv420p",  // force 8-bit; prevents platform re-encode of 10-bit HDR
    val colorSpace: String = "bt709",     // tag as Rec.709 SDR; prevents washed-out colors after upload
    val audioSampleRate: Int = 48000,     // platforms expect 48 kHz; 44.1 kHz triggers audio re-encode
    val audioCodec: String = "aac",
    val keyframeInterval: Int = 2         // GOP size in seconds; mismatched GOPs force full re-encodes
)

object PlatformProfiles {

    val all: Map<Platform, EncodingProfile> = mapOf(
        Platform.INSTAGRAM to EncodingProfile(
            platform = Platform.INSTAGRAM,
            videoBitrate = "6000k",
            maxWidth = 1080,
            maxHeight = 1920,
            frameRate = 30,
            audioBitrate = "128k",
            codec = "h265"
        ),
        Platform.TIKTOK to EncodingProfile(
            platform = Platform.TIKTOK,
            videoBitrate = "6000k",
            maxWidth = 1080,
            maxHeight = 1920,
            frameRate = 60,
            audioBitrate = "128k",
            codec = "h265"
        ),
        Platform.YOUTUBE to EncodingProfile(
            platform = Platform.YOUTUBE,
            videoBitrate = "12000k",
            maxWidth = 1920,
            maxHeight = 1080,
            frameRate = 30,
            audioBitrate = "192k",
            codec = "h265"
        ),
        Platform.YOUTUBE_SHORTS to EncodingProfile(
            platform = Platform.YOUTUBE_SHORTS,
            videoBitrate = "8000k",
            maxWidth = 1080,
            maxHeight = 1920,
            frameRate = 30,
            audioBitrate = "192k",
            codec = "h265"
        ),
        Platform.TWITTER to EncodingProfile(
            platform = Platform.TWITTER,
            videoBitrate = "5000k",
            maxWidth = 1920,
            maxHeight = 1080,
            frameRate = 30,
            audioBitrate = "128k",
            codec = "h265"
        ),
        Platform.WHATSAPP_STORY to EncodingProfile(
            platform = Platform.WHATSAPP_STORY,
            videoBitrate = "6000k",
            maxWidth = 1080,
            maxHeight = 1920,
            frameRate = 30,
            audioBitrate = "128k",
            codec = "h265"
        )
    )
}
