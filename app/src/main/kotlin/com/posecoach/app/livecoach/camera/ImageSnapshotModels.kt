package com.posecoach.app.livecoach.camera

/**
 * Shared data models and configuration for Image Snapshot processing
 *
 * Contains all data structures, configuration objects, and constants
 * needed across the image snapshot components for consistent behavior.
 */

/**
 * Configuration for image snapshot capture and processing
 */
data class SnapshotConfig(
    val maxWidth: Int = SnapshotConstants.DEFAULT_MAX_WIDTH,
    val maxHeight: Int = SnapshotConstants.DEFAULT_MAX_HEIGHT,
    val jpegQuality: Int = SnapshotConstants.DEFAULT_JPEG_QUALITY,
    val snapshotIntervalMs: Long = SnapshotConstants.DEFAULT_SNAPSHOT_INTERVAL_MS,
    val maxConcurrentProcessing: Int = SnapshotConstants.MAX_CONCURRENT_PROCESSING,
    val processingTimeoutMs: Long = SnapshotConstants.PROCESSING_TIMEOUT_MS
)

/**
 * Performance metrics for monitoring snapshot processing
 */
data class PerformanceMetrics(
    val totalSnapshots: Long = 0,
    val averageProcessingTimeMs: Long = 0,
    val droppedFrames: Long = 0,
    val averageFileSizeBytes: Long = 0,
    val memoryUsageKB: Long = 0
)

/**
 * Real-time processing status information
 */
data class ProcessingStatus(
    val isEnabled: Boolean,
    val currentProcessingCount: Int,
    val maxConcurrentProcessing: Int,
    val frameRate: Float,
    val privacyEnabled: Boolean
)

/**
 * Quality settings for JPEG compression
 */
enum class CompressionQuality(val value: Int) {
    LOW(50),
    MEDIUM(70),
    HIGH(85)
}

/**
 * Snapshot capture result with metadata
 */
data class SnapshotResult(
    val success: Boolean,
    val fileSizeBytes: Int = 0,
    val processingTimeMs: Long = 0,
    val compressionRatio: Float = 0f,
    val error: String? = null
)

/**
 * Rate limiting state for snapshot capture
 */
data class RateLimitingState(
    val lastSnapshotTime: Long,
    val droppedFrameCount: Long,
    val currentProcessingCount: Int,
    val canCapture: Boolean
)

/**
 * Configuration constants for snapshot processing
 */
object SnapshotConstants {
    // Resolution constraints for bandwidth optimization
    const val DEFAULT_MAX_WIDTH = 320
    const val DEFAULT_MAX_HEIGHT = 240

    // Quality settings
    const val DEFAULT_JPEG_QUALITY = 70
    const val HIGH_QUALITY_JPEG = 85
    const val MEDIUM_QUALITY_JPEG = 70
    const val LOW_QUALITY_JPEG = 50

    // Frame rate constraints
    const val DEFAULT_SNAPSHOT_INTERVAL_MS = 1000L // 1 FPS default
    const val MIN_SNAPSHOT_INTERVAL_MS = 500L      // 2 FPS max
    const val MAX_SNAPSHOT_INTERVAL_MS = 3000L     // 0.33 FPS min

    // Performance limits
    const val MAX_CONCURRENT_PROCESSING = 2
    const val PROCESSING_TIMEOUT_MS = 5000L
    const val MEMORY_CLEANUP_INTERVAL_MS = 10000L
    const val MEMORY_THRESHOLD_KB = 5000L // 5MB threshold for GC suggestion

    // Memory management
    const val MAX_BITMAP_REFERENCES = 10
}

/**
 * Image format information
 */
data class ImageFormatInfo(
    val format: Int,
    val supportedByProcessor: Boolean,
    val requiresConversion: Boolean,
    val conversionMethod: String? = null
)

/**
 * Memory usage tracking data
 */
data class MemoryInfo(
    val activeBitmapCount: Int,
    val totalMemoryKB: Long,
    val shouldSuggestGC: Boolean
)