package com.posecoach.app.livecoach.camera

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Image Snapshot models and data structures
 */
class ImageSnapshotModelsTest {

    @Test
    fun snapshotConfig_hasDefaultValues() {
        val config = SnapshotConfig()

        assertEquals(320, config.maxWidth)
        assertEquals(240, config.maxHeight)
        assertEquals(70, config.jpegQuality)
        assertEquals(1000L, config.snapshotIntervalMs)
        assertEquals(2, config.maxConcurrentProcessing)
        assertEquals(5000L, config.processingTimeoutMs)
    }

    @Test
    fun snapshotConfig_canBeCustomized() {
        val config = SnapshotConfig(
            maxWidth = 640,
            maxHeight = 480,
            jpegQuality = 85,
            snapshotIntervalMs = 500L,
            maxConcurrentProcessing = 4,
            processingTimeoutMs = 10000L
        )

        assertEquals(640, config.maxWidth)
        assertEquals(480, config.maxHeight)
        assertEquals(85, config.jpegQuality)
        assertEquals(500L, config.snapshotIntervalMs)
        assertEquals(4, config.maxConcurrentProcessing)
        assertEquals(10000L, config.processingTimeoutMs)
    }

    @Test
    fun performanceMetrics_hasDefaultValues() {
        val metrics = PerformanceMetrics()

        assertEquals(0L, metrics.totalSnapshots)
        assertEquals(0L, metrics.averageProcessingTimeMs)
        assertEquals(0L, metrics.droppedFrames)
        assertEquals(0L, metrics.averageFileSizeBytes)
        assertEquals(0L, metrics.memoryUsageKB)
    }

    @Test
    fun performanceMetrics_canTrackMetrics() {
        val metrics = PerformanceMetrics(
            totalSnapshots = 100,
            averageProcessingTimeMs = 50,
            droppedFrames = 5,
            averageFileSizeBytes = 1024,
            memoryUsageKB = 2048
        )

        assertEquals(100L, metrics.totalSnapshots)
        assertEquals(50L, metrics.averageProcessingTimeMs)
        assertEquals(5L, metrics.droppedFrames)
        assertEquals(1024L, metrics.averageFileSizeBytes)
        assertEquals(2048L, metrics.memoryUsageKB)
    }

    @Test
    fun processingStatus_providesStatusInfo() {
        val status = ProcessingStatus(
            isEnabled = true,
            currentProcessingCount = 1,
            maxConcurrentProcessing = 2,
            frameRate = 1.0f,
            privacyEnabled = true
        )

        assertTrue(status.isEnabled)
        assertEquals(1, status.currentProcessingCount)
        assertEquals(2, status.maxConcurrentProcessing)
        assertEquals(1.0f, status.frameRate, 0.01f)
        assertTrue(status.privacyEnabled)
    }

    @Test
    fun compressionQuality_hasCorrectValues() {
        assertEquals(50, CompressionQuality.LOW.value)
        assertEquals(70, CompressionQuality.MEDIUM.value)
        assertEquals(85, CompressionQuality.HIGH.value)
    }

    @Test
    fun snapshotResult_successfulResult() {
        val result = SnapshotResult(
            success = true,
            fileSizeBytes = 2048,
            processingTimeMs = 100,
            compressionRatio = 0.75f
        )

        assertTrue(result.success)
        assertEquals(2048, result.fileSizeBytes)
        assertEquals(100L, result.processingTimeMs)
        assertEquals(0.75f, result.compressionRatio, 0.01f)
        assertNull(result.error)
    }

    @Test
    fun snapshotResult_failedResult() {
        val result = SnapshotResult(
            success = false,
            error = "Processing failed"
        )

        assertFalse(result.success)
        assertEquals(0, result.fileSizeBytes)
        assertEquals(0L, result.processingTimeMs)
        assertEquals(0f, result.compressionRatio, 0.01f)
        assertEquals("Processing failed", result.error)
    }

    @Test
    fun rateLimitingState_tracksLimiting() {
        val state = RateLimitingState(
            lastSnapshotTime = System.currentTimeMillis(),
            droppedFrameCount = 3,
            currentProcessingCount = 1,
            canCapture = true
        )

        assertTrue(state.lastSnapshotTime > 0)
        assertEquals(3L, state.droppedFrameCount)
        assertEquals(1, state.currentProcessingCount)
        assertTrue(state.canCapture)
    }

    @Test
    fun snapshotConfig_constants_haveCorrectValues() {
        assertEquals(320, SnapshotConfig.DEFAULT_MAX_WIDTH)
        assertEquals(240, SnapshotConfig.DEFAULT_MAX_HEIGHT)
        assertEquals(70, SnapshotConfig.DEFAULT_JPEG_QUALITY)
        assertEquals(85, SnapshotConfig.HIGH_QUALITY_JPEG)
        assertEquals(70, SnapshotConfig.MEDIUM_QUALITY_JPEG)
        assertEquals(50, SnapshotConfig.LOW_QUALITY_JPEG)

        assertEquals(1000L, SnapshotConfig.DEFAULT_SNAPSHOT_INTERVAL_MS)
        assertEquals(500L, SnapshotConfig.MIN_SNAPSHOT_INTERVAL_MS)
        assertEquals(3000L, SnapshotConfig.MAX_SNAPSHOT_INTERVAL_MS)

        assertEquals(2, SnapshotConfig.MAX_CONCURRENT_PROCESSING)
        assertEquals(5000L, SnapshotConfig.PROCESSING_TIMEOUT_MS)
        assertEquals(10000L, SnapshotConfig.MEMORY_CLEANUP_INTERVAL_MS)
        assertEquals(5000L, SnapshotConfig.MEMORY_THRESHOLD_KB)
        assertEquals(10, SnapshotConfig.MAX_BITMAP_REFERENCES)
    }

    @Test
    fun imageFormatInfo_tracksFormatSupport() {
        val formatInfo = ImageFormatInfo(
            format = 256, // ImageFormat.YUV_420_888
            supportedByProcessor = true,
            requiresConversion = true,
            conversionMethod = "YUV to RGB"
        )

        assertEquals(256, formatInfo.format)
        assertTrue(formatInfo.supportedByProcessor)
        assertTrue(formatInfo.requiresConversion)
        assertEquals("YUV to RGB", formatInfo.conversionMethod)
    }

    @Test
    fun memoryInfo_tracksMemoryUsage() {
        val memoryInfo = MemoryInfo(
            activeBitmapCount = 5,
            totalMemoryKB = 2048,
            shouldSuggestGC = false
        )

        assertEquals(5, memoryInfo.activeBitmapCount)
        assertEquals(2048L, memoryInfo.totalMemoryKB)
        assertFalse(memoryInfo.shouldSuggestGC)
    }

    @Test
    fun memoryInfo_suggestsGC_whenMemoryHigh() {
        val memoryInfo = MemoryInfo(
            activeBitmapCount = 15,
            totalMemoryKB = 6000, // Above 5MB threshold
            shouldSuggestGC = true
        )

        assertEquals(15, memoryInfo.activeBitmapCount)
        assertEquals(6000L, memoryInfo.totalMemoryKB)
        assertTrue(memoryInfo.shouldSuggestGC)
    }
}