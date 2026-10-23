package com.example.videosqueeze


/** Encoding presets are requests; device encoders may clamp or ignore a requested bitrate. */
enum class QualityPreset(val title: String, val bitrateFactor: Double, val description: String) {
    SMALL("کمترین حجم", 0.38, "حجم کمتر؛ احتمال افت کیفیت بیشتر"),
    BALANCED("متعادل", 0.55, "تعادل بین کیفیت، حجم و سرعت"),
    HIGH("کیفیت بالاتر", 0.72, "حجم بیشتر با جزئیات بهتر")
}

enum class VideoCodec(val title: String, val mimeType: String) {
    HEVC("H.265 / HEVC", "video/hevc"),
    AVC("H.264 / AVC", "video/avc")
}

data class VideoInfo(
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val mimeType: String?
) {
    val resolutionLabel: String get() = if (width > 0 && height > 0) "${width}×${height}" else "نامشخص"
}

fun estimateVideoBitrate(info: VideoInfo, preset: QualityPreset): Int {
    // Metadata bitrate can include audio. Use a conservative pixel-based fallback if absent.
    val source = if (info.bitrate > 0) info.bitrate else {
        val pixels = (info.width.coerceAtLeast(640).toLong() * info.height.coerceAtLeast(360)).toDouble()
        (pixels * 0.09).toInt().coerceIn(500_000, 20_000_000)
    }
    val floor = when {
        info.height >= 2160 -> 2_500_000
        info.height >= 1080 -> 1_200_000
        info.height >= 720 -> 650_000
        else -> 300_000
    }
    val ceiling = when {
        info.height >= 2160 -> 18_000_000
        info.height >= 1080 -> 8_000_000
        info.height >= 720 -> 4_000_000
        else -> 1_800_000
    }
    return (source * preset.bitrateFactor).toInt().coerceIn(floor.coerceAtMost(ceiling), ceiling)
}

fun estimateOutputBytes(info: VideoInfo, videoBitrate: Int, audioBitrate: Int = 96_000): Long {
    if (info.durationMs <= 0) return 0L
    return ((videoBitrate.toLong() + audioBitrate) * info.durationMs / 8_000L).coerceAtLeast(0L)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return if (unit == 0) "${value.toLong()} ${units[unit]}" else String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
}

fun reductionPercent(original: Long, output: Long): Int? {
    if (original <= 0 || output < 0) return null
    return (((original - output).toDouble() / original) * 100.0).toInt()
}
