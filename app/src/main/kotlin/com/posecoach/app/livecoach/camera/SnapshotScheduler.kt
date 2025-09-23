package com.posecoach.app.livecoach.camera

import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * Manages snapshot timing, rate limiting, and scheduling
 *
 * Responsibilities:
 * - Frame rate limiting based on configured intervals
 * - Concurrency control to prevent overloading
 * - Memory cleanup scheduling
 * - Performance metrics tracking
 * - Configuration management for timing parameters
 *
 * This component focuses on timing and scheduling logic,
 * delegating image processing to ImageCompressionHandler.
 */
class SnapshotScheduler(
    private val coroutineScope: CoroutineScope
) : CoroutineScope {

    override val coroutineContext: CoroutineContext =
        coroutineScope.coroutineContext + SupervisorJob()

    // Configuration
    private var config = SnapshotConfig()
    @Volatile
    private var isEnabled = false

    // Timing state
    private val lastSnapshotTime = AtomicLong(0L)
    private val processingCount = AtomicLong(0L)
    private val droppedFrameCount = AtomicLong(0L)

    // Jobs
    private var memoryCleanupJob: Job? = null

    /**
     * Start snapshot scheduling
     */
    fun startScheduling() {
        if (isEnabled) {
            Timber.w("Scheduling already enabled")
            return
        }

        isEnabled = true
        lastSnapshotTime.set(0L) // Force first snapshot

        Timber.d("Snapshot scheduling started - interval: ${config.snapshotIntervalMs}ms")
    }

    /**
     * Stop snapshot scheduling
     */
    fun stopScheduling() {
        if (!isEnabled) {
            Timber.w("Scheduling not enabled")
            return
        }

        isEnabled = false
        stopMemoryCleanup()

        Timber.d("Snapshot scheduling stopped")
    }

    /**
     * Update scheduling configuration
     * @param newConfig New configuration with validated parameters
     */
    fun updateConfig(newConfig: SnapshotConfig) {
        // Validate and clamp parameters
        val validatedConfig = newConfig.copy(
            snapshotIntervalMs = newConfig.snapshotIntervalMs.coerceIn(
                SnapshotConfig.MIN_SNAPSHOT_INTERVAL_MS,
                SnapshotConfig.MAX_SNAPSHOT_INTERVAL_MS
            ),
            jpegQuality = newConfig.jpegQuality.coerceIn(
                SnapshotConfig.LOW_QUALITY_JPEG,
                SnapshotConfig.HIGH_QUALITY_JPEG
            ),
            maxConcurrentProcessing = newConfig.maxConcurrentProcessing.coerceAtLeast(1)
        )

        config = validatedConfig

        Timber.d("Config updated: interval=${config.snapshotIntervalMs}ms, " +
                "quality=${config.jpegQuality}, maxConcurrent=${config.maxConcurrentProcessing}")
    }

    /**
     * Check if a snapshot should be captured based on timing and rate limits
     * @return true if snapshot should be captured, false otherwise
     */
    fun shouldCaptureSnapshot(): Boolean {
        if (!isEnabled) {
            return false
        }

        val currentTime = System.currentTimeMillis()
        val lastTime = lastSnapshotTime.get()

        // Check frame rate limiting
        if (currentTime - lastTime < config.snapshotIntervalMs) {
            onFrameDropped()
            return false
        }

        // Check concurrency limiting
        if (processingCount.get() >= config.maxConcurrentProcessing) {
            onFrameDropped()
            Timber.v("Skipping snapshot - too many concurrent operations")
            return false
        }

        // Update timestamp atomically
        if (!lastSnapshotTime.compareAndSet(lastTime, currentTime)) {
            return false // Another thread updated the timestamp
        }

        return true
    }

    /**
     * Mark processing as started (for concurrency tracking)
     */
    fun onProcessingStarted() {
        processingCount.incrementAndGet()
    }

    /**
     * Mark processing as completed (for concurrency tracking)
     */
    fun onProcessingCompleted() {
        val count = processingCount.decrementAndGet()
        if (count < 0) {
            processingCount.set(0) // Prevent negative counts
        }
    }

    /**
     * Record a dropped frame for metrics
     */
    fun onFrameDropped() {
        droppedFrameCount.incrementAndGet()
    }

    /**
     * Calculate current frame rate in FPS
     */
    fun calculateFrameRate(): Float {
        return 1000f / config.snapshotIntervalMs
    }

    /**
     * Get current configuration
     */
    fun getConfig(): SnapshotConfig {
        return config
    }

    /**
     * Check if scheduling is enabled
     */
    fun isEnabled(): Boolean {
        return isEnabled
    }

    /**
     * Get current rate limiting state
     */
    fun getRateLimitingState(): RateLimitingState {
        return RateLimitingState(
            lastSnapshotTime = lastSnapshotTime.get(),
            droppedFrameCount = droppedFrameCount.get(),
            currentProcessingCount = processingCount.get().toInt(),
            canCapture = shouldCaptureSnapshot()
        )
    }

    /**
     * Get current scheduling status
     */
    fun getSchedulingStatus(): ProcessingStatus {
        return ProcessingStatus(
            isEnabled = isEnabled,
            currentProcessingCount = processingCount.get().toInt(),
            maxConcurrentProcessing = config.maxConcurrentProcessing,
            frameRate = calculateFrameRate(),
            privacyEnabled = true // Always true in this context
        )
    }

    /**
     * Get performance metrics
     */
    fun getPerformanceMetrics(): PerformanceMetrics {
        return PerformanceMetrics(
            totalSnapshots = 0, // Will be tracked by the manager
            averageProcessingTimeMs = 0, // Will be tracked by compression handler
            droppedFrames = droppedFrameCount.get(),
            averageFileSizeBytes = 0, // Will be tracked by compression handler
            memoryUsageKB = 0 // Will be tracked by the manager
        )
    }

    /**
     * Reset performance metrics
     */
    fun resetMetrics() {
        droppedFrameCount.set(0)
        Timber.d("Performance metrics reset")
    }

    /**
     * Start memory cleanup coroutine
     */
    fun startMemoryCleanup() {
        memoryCleanupJob?.cancel()
        memoryCleanupJob = launch {
            while (isActive && isEnabled) {
                delay(SnapshotConfig.MEMORY_CLEANUP_INTERVAL_MS)
                performMemoryCleanup()
            }
        }
        Timber.d("Memory cleanup job started")
    }

    /**
     * Stop memory cleanup coroutine
     */
    fun stopMemoryCleanup() {
        memoryCleanupJob?.cancel()
        memoryCleanupJob = null
        Timber.d("Memory cleanup job stopped")
    }

    /**
     * Check if the scheduler has been destroyed
     */
    fun isDestroyed(): Boolean {
        return !coroutineContext.isActive
    }

    /**
     * Perform memory cleanup operations
     */
    private suspend fun performMemoryCleanup() {
        try {
            // Suggest garbage collection if needed
            System.gc()
            Timber.v("Performed memory cleanup")
        } catch (e: Exception) {
            Timber.w(e, "Error during memory cleanup")
        }
    }

    /**
     * Destroy the scheduler and clean up resources
     */
    fun destroy() {
        Timber.d("Destroying snapshot scheduler")

        stopScheduling()
        stopMemoryCleanup()

        // Cancel coroutine scope
        cancel()

        Timber.d("Snapshot scheduler destroyed")
    }
}