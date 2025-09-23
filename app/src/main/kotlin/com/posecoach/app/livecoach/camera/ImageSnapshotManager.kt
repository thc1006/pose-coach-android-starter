package com.posecoach.app.livecoach.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Base64
import android.util.Size
import androidx.camera.core.ImageProxy
import com.posecoach.app.livecoach.models.LiveApiMessage
import com.posecoach.app.livecoach.models.MediaChunk
import com.posecoach.app.livecoach.models.PoseSnapshot
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

class ImageSnapshotManager(
    private val coroutineScope: CoroutineScope
) : CoroutineScope {

    override val coroutineContext: CoroutineContext =
        coroutineScope.coroutineContext + SupervisorJob()

    companion object {
        // Low resolution for Live API - reduces payload size and bandwidth
        private const val MAX_WIDTH = 320
        private const val MAX_HEIGHT = 240
        private const val JPEG_QUALITY = 70

        // Frame rate limiting - configurable for performance tuning
        private const val DEFAULT_SNAPSHOT_INTERVAL_MS = 1000L // 1 FPS default
        private const val MIN_SNAPSHOT_INTERVAL_MS = 500L      // 2 FPS max
        private const val MAX_SNAPSHOT_INTERVAL_MS = 3000L     // 0.33 FPS min

        // Memory management
        private const val MAX_CONCURRENT_PROCESSING = 2
        private const val PROCESSING_TIMEOUT_MS = 5000L

        // Quality settings
        private const val HIGH_QUALITY_JPEG = 85
        private const val MEDIUM_QUALITY_JPEG = 70
        private const val LOW_QUALITY_JPEG = 50
    }

    // Configuration
    @Volatile
    private var snapshotIntervalMs = DEFAULT_SNAPSHOT_INTERVAL_MS
    @Volatile
    private var jpegQuality = JPEG_QUALITY
    @Volatile
    private var privacyEnabled = true

    // State tracking
    private val lastSnapshotTime = AtomicLong(0L)
    private val processingCount = AtomicLong(0L)
    @Volatile
    private var isEnabled = false

    // Processing jobs
    private var snapshotJobs = mutableListOf<Job>()
    private val processingJobs = mutableSetOf<Job>()

    // Memory management
    private val weakImageReferences = mutableListOf<WeakReference<Bitmap>>()

    // Flows with enhanced buffering
    private val _snapshots = MutableSharedFlow<PoseSnapshot>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val snapshots: SharedFlow<PoseSnapshot> = _snapshots.asSharedFlow()

    private val _realtimeInput = MutableSharedFlow<LiveApiMessage.RealtimeInput>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val realtimeInput: SharedFlow<LiveApiMessage.RealtimeInput> = _realtimeInput.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    // Performance metrics
    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    val performanceMetrics: StateFlow<PerformanceMetrics> = _performanceMetrics.asStateFlow()

    // Performance metrics moved to ImageSnapshotModels.kt

    /**
     * Start capturing periodic snapshots with configurable frame rate
     * @param intervalMs Custom interval between snapshots (optional)
     * @param quality JPEG quality setting (optional)
     */
    fun startSnapshots(intervalMs: Long? = null, quality: Int? = null) {
        if (scheduler.isEnabled()) {
            Timber.w("Snapshots already enabled")
            return
        }

        // Apply custom settings if provided
        intervalMs?.let {
            config = config.copy(snapshotIntervalMs = it)
        }
        quality?.let {
            config = config.copy(jpegQuality = it)
        }

        // Update scheduler with new config
        scheduler.updateConfig(config)
        scheduler.startScheduling()
        scheduler.startMemoryCleanup()

        // Reset performance metrics
        _performanceMetrics.value = PerformanceMetrics()

        Timber.d("Image snapshots started - interval: ${config.snapshotIntervalMs}ms, quality: ${config.jpegQuality}")
    }

    fun stopSnapshots() {
        if (!scheduler.isEnabled()) {
            Timber.w("Snapshots not enabled")
            return
        }

        // Stop scheduling
        scheduler.stopScheduling()

        // Cancel all processing jobs
        snapshotJobs.forEach { it.cancel() }
        processingJobs.forEach { it.cancel() }
        snapshotJobs.clear()
        processingJobs.clear()

        // Clean up memory
        cleanupMemory()

        // Log final performance
        val finalMetrics = _performanceMetrics.value
        Timber.d("Image snapshots stopped - Final metrics: $finalMetrics")
    }

    /**
     * Process camera frame with pose landmarks for snapshot generation
     * Implements frame rate limiting and memory-efficient processing
     */
    fun processImageWithLandmarks(
        imageProxy: ImageProxy,
        landmarks: PoseLandmarkResult?
    ) {
        if (!scheduler.isEnabled() || landmarks == null || !privacyEnabled) {
            return
        }

        // Check if snapshot should be captured (handles rate limiting and concurrency)
        if (!scheduler.shouldCaptureSnapshot()) {
            return
        }

        // Mark processing as started
        scheduler.onProcessingStarted()

        // Process in background to avoid blocking camera thread
        val job = launch {
            try {
                withTimeout(config.processingTimeoutMs) {
                    processSnapshot(imageProxy, landmarks, System.currentTimeMillis())
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w("Snapshot processing timed out")
                _errors.emit("Snapshot processing timeout")
            } catch (e: Exception) {
                Timber.e(e, "Error processing snapshot")
                _errors.emit("Snapshot processing error: ${e.message}")
            } finally {
                scheduler.onProcessingCompleted()
            }
        }

        // Track job for cleanup
        synchronized(processingJobs) {
            processingJobs.add(job)
            // Clean up completed jobs
            processingJobs.removeAll { it.isCompleted }
        }
    }

    private suspend fun processSnapshot(
        imageProxy: ImageProxy,
        landmarks: PoseLandmarkResult,
        timestamp: Long
    ) = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        try {
            // Use compression handler for image processing
            val result = compressionHandler.processImageProxy(imageProxy, config)

            if (!result.success) {
                _errors.emit("Image processing failed: ${result.error}")
                return@withContext
            }

            // Get JPEG bytes from compression handler
            val jpegBytes = compressionHandler.compressBitmap(
                compressionHandler.imageProxyToBitmap(imageProxy),
                config.jpegQuality
            )

            // Encode to Base64 for Live API
            val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

            // Attach pose landmarks for context
            val enhancedSnapshot = PoseSnapshot(
                imageData = base64Image,
                landmarks = landmarks,
                timestamp = timestamp
            )

            // Emit snapshot for local processing
            _snapshots.emit(enhancedSnapshot)

            // Create optimized Live API message with pose context
            val landmarksJson = "pose_landmarks:${landmarks.landmarks.size}_points"

            val mediaChunk = MediaChunk(
                mimeType = "image/jpeg",
                data = base64Image
            )

            val realtimeInputMessage = LiveApiMessage.RealtimeInput(
                mediaChunks = listOf(mediaChunk),
                text = "Frame with $landmarksJson at ${timestamp}ms"
            )

            _realtimeInput.emit(realtimeInputMessage)

            // Update performance metrics
            val processingTime = System.currentTimeMillis() - startTime
            updateMetrics { metrics ->
                val newTotal = metrics.totalSnapshots + 1
                val newAvgTime = ((metrics.averageProcessingTimeMs * metrics.totalSnapshots) + processingTime) / newTotal
                val newAvgSize = ((metrics.averageFileSizeBytes * metrics.totalSnapshots) + jpegBytes.size) / newTotal

                metrics.copy(
                    totalSnapshots = newTotal,
                    averageProcessingTimeMs = newAvgTime,
                    averageFileSizeBytes = newAvgSize,
                    memoryUsageKB = getMemoryUsageKB()
                )
            }

            Timber.v("Processed snapshot: ${jpegBytes.size} bytes -> ${base64Image.length} chars in ${processingTime}ms")

        } catch (e: Exception) {
            Timber.e(e, "Failed to process snapshot")
            _errors.emit("Snapshot conversion failed: ${e.message}")
        }
    }

    // Image processing methods are now handled by ImageCompressionHandler

    /**
     * Track bitmap for memory management
     */
    private fun trackBitmap(bitmap: android.graphics.Bitmap) {
        synchronized(weakImageReferences) {
            weakImageReferences.add(WeakReference(bitmap))
            // Clean up null references periodically
            if (weakImageReferences.size > SnapshotConfig.MAX_BITMAP_REFERENCES) {
                weakImageReferences.removeAll { it.get() == null }
            }
        }
    }

    /**
     * Get approximate memory usage
     */
    private fun getMemoryUsageKB(): Long {
        return synchronized(weakImageReferences) {
            weakImageReferences.mapNotNull { it.get() }
                .sumOf { it.byteCount.toLong() } / 1024
        }
    }

    /**
     * Update performance metrics atomically
     */
    private fun updateMetrics(update: (PerformanceMetrics) -> PerformanceMetrics) {
        _performanceMetrics.value = update(_performanceMetrics.value)
    }

    // Memory cleanup is now handled by SnapshotScheduler

    /**
     * Force cleanup of unused bitmaps
     */
    private fun cleanupMemory() {
        synchronized(weakImageReferences) {
            val beforeCount = weakImageReferences.size
            weakImageReferences.removeAll { it.get() == null }
            val afterCount = weakImageReferences.size

            if (beforeCount > afterCount) {
                Timber.v("Cleaned up ${beforeCount - afterCount} bitmap references")
            }
        }

        // Suggest GC if memory usage is high
        if (getMemoryUsageKB() > 5000) { // More than 5MB
            System.gc()
            Timber.d("Suggested garbage collection due to high memory usage")
        }
    }

    /**
     * Configure snapshot interval for frame rate limiting
     * @param intervalMs Time between snapshots (500ms to 3000ms)
     */
    fun setSnapshotInterval(intervalMs: Long) {
        config = config.copy(snapshotIntervalMs = intervalMs)
        scheduler.updateConfig(config)
        Timber.d("Snapshot interval updated: ${config.snapshotIntervalMs}ms (${scheduler.calculateFrameRate()} fps)")
    }

    /**
     * Configure JPEG compression quality
     * @param quality JPEG quality (50-100)
     */
    fun setJpegQuality(quality: Int) {
        config = config.copy(jpegQuality = quality)
        scheduler.updateConfig(config)
        Timber.d("JPEG quality updated: ${config.jpegQuality}")
    }

    /**
     * Enable/disable privacy mode (affects image capture)
     * @param enabled When false, no images are captured
     */
    fun setPrivacyEnabled(enabled: Boolean) {
        privacyEnabled = enabled
        Timber.d("Privacy mode: ${if (enabled) "enabled" else "disabled"}")
    }

    fun isSnapshotsEnabled(): Boolean = scheduler.isEnabled()

    /**
     * Get current snapshot configuration
     * @return Triple of (width, height, intervalMs)
     */
    fun getSnapshotInfo(): Triple<Int, Int, Long> {
        return Triple(config.maxWidth, config.maxHeight, config.snapshotIntervalMs)
    }

    /**
     * Get current frame rate in FPS
     */
    fun getCurrentFrameRate(): Float {
        return scheduler.calculateFrameRate()
    }

    /**
     * Check if privacy settings allow image capture
     */
    fun isPrivacyCompliant(): Boolean {
        return privacyEnabled
    }

    /**
     * Get real-time processing status
     */
    fun getProcessingStatus(): ProcessingStatus {
        return scheduler.getSchedulingStatus().copy(
            privacyEnabled = privacyEnabled
        )
    }

    fun destroy() {
        Timber.d("Destroying ImageSnapshotManager")

        stopSnapshots()

        // Force cleanup all resources
        cleanupMemory()

        // Clear all flows
        synchronized(processingJobs) {
            processingJobs.clear()
        }
        snapshotJobs.clear()

        // Destroy components
        scheduler.destroy()
        compressionHandler.destroy()

        // Cancel coroutine scope
        cancel()

        Timber.d("ImageSnapshotManager destroyed successfully")
    }
}