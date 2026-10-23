package com.example.videosqueeze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionModelsTest {
    private val source = VideoInfo(
        displayName = "sample.mp4",
        sizeBytes = 100_000_000,
        durationMs = 60_000,
        width = 1920,
        height = 1080,
        bitrate = 8_000_000,
        mimeType = "video/avc"
    )

    @Test fun smallPresetRequestsLowerBitrateThanBalanced() {
        assertTrue(estimateVideoBitrate(source, QualityPreset.SMALL) < estimateVideoBitrate(source, QualityPreset.BALANCED))
    }

    @Test fun highPresetRequestsLowerOrEqualSourceBitrate() {
        assertTrue(estimateVideoBitrate(source, QualityPreset.HIGH) <= source.bitrate)
    }

    @Test fun outputEstimateUsesDurationAndBitrate() {
        assertEquals(8_220_000L, estimateOutputBytes(source.copy(durationMs = 60_000), 1_000_000, 96_000))
    }

    @Test fun reductionPercentIsCorrect() {
        assertEquals(65, reductionPercent(1_000, 350))
        assertEquals(null, reductionPercent(0, 350))
    }

    @Test fun formatBytesHandlesMegabytes() {
        assertEquals("1.0 MB", formatBytes(1_048_576))
    }
}
