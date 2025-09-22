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

    data class PerformanceMetrics(
        val totalSnapshots: Long = 0,
        val averageProcessingTimeMs: Long = 0,
        val droppedFrames: Long = 0,
        val averageFileSizeBytes: Long = 0,
        val memoryUsageKB: Long = 0
    )

    /**
     * Start capturing periodic snapshots with configurable frame rate
     * @param intervalMs Custom interval between snapshots (optional)
     * @param quality JPEG quality setting (optional)
     */
    fun startSnapshots(intervalMs: Long? = null, quality: Int? = null) {
        if (isEnabled) {
            Timber.w("Snapshots already enabled")
            return
        }

        // Apply custom settings if provided
        intervalMs?.let { setSnapshotInterval(it) }
        quality?.let { setJpegQuality(it) }

        isEnabled = true
        lastSnapshotTime.set(0L) // Force first snapshot

        // Reset performance metrics
        _performanceMetrics.value = PerformanceMetrics()

        // Start memory cleanup coroutine
        startMemoryCleanup()

        Timber.d("Image snapshots started - interval: ${snapshotIntervalMs}ms, quality: $jpegQuality")
    }

    fun stopSnapshots() {
        if (!isEnabled) {
            Timber.w("Snapshots not enabled")
            return
        }

        isEnabled = false

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
        if (!isEnabled || landmarks == null || !privacyEnabled) {
            return
        }

        val currentTime = System.currentTimeMillis()
        val lastTime = lastSnapshotTime.get()

        // Frame rate limiting
        if (currentTime - lastTime < snapshotIntervalMs) {
            updateMetrics { it.copy(droppedFrames = it.droppedFrames + 1) }
            return // Skip this frame
        }

        // Prevent too many concurrent processing operations
        if (processingCount.get() >= MAX_CONCURRENT_PROCESSING) {
            updateMetrics { it.copy(droppedFrames = it.droppedFrames + 1) }
            Timber.w("Skipping snapshot - too many concurrent operations")
            return
        }

        if (!lastSnapshotTime.compareAndSet(lastTime, currentTime)) {
            return // Another thread updated the timestamp
        }

        // Process in background to avoid blocking camera thread
        val job = launch {
            processingCount.incrementAndGet()
            try {
                withTimeout(PROCESSING_TIMEOUT_MS) {
                    processSnapshot(imageProxy, landmarks, currentTime)
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w("Snapshot processing timed out")
                _errors.emit("Snapshot processing timeout")
            } catch (e: Exception) {
                Timber.e(e, "Error processing snapshot")
                _errors.emit("Snapshot processing error: ${e.message}")
            } finally {
                processingCount.decrementAndGet()
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
            // Convert ImageProxy to Bitmap with memory management
            val bitmap = imageProxyToBitmap(imageProxy)
            trackBitmap(bitmap)

            // Resize to low resolution for bandwidth optimization
            val resizedBitmap = resizeBitmap(bitmap, MAX_WIDTH, MAX_HEIGHT)
            if (resizedBitmap != bitmap) {
                trackBitmap(resizedBitmap)
            }

            // Convert to JPEG bytes with configured quality
            val jpegBytes = bitmapToJpegBytes(resizedBitmap)

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
            val landmarksJson = landmarks.let {
                "pose_landmarks:${landmarks.landmarks.size}_points"
            }

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

            // Clean up bitmaps immediately to prevent memory leaks
            bitmap.recycle()
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to process snapshot")
            _errors.emit("Snapshot conversion failed: ${e.message}")
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        return when (imageProxy.format) {
            ImageFormat.YUV_420_888 -> yuvToBitmap(imageProxy)
            ImageFormat.JPEG -> {
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            else -> throw IllegalArgumentException("Unsupported image format: ${imageProxy.format}")
        }
    }

    private fun yuvToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)

        // Interleave U and V planes for NV21 format
        val uvPixelStride = imageProxy.planes[1].pixelStride
        if (uvPixelStride == 1) {
            uBuffer.get(nv21, ySize, uSize)
            vBuffer.get(nv21, ySize + uSize, vSize)
        } else {
            // Handle pixel stride > 1 (interleaved UV)
            val uvBytes = ByteArray(uSize + vSize)
            uBuffer.get(uvBytes, 0, uSize)
            vBuffer.get(uvBytes, uSize, vSize)

            var uvIndex = 0
            for (i in ySize until nv21.size step 2) {
                nv21[i] = uvBytes[uvIndex + 1] // V
                nv21[i + 1] = uvBytes[uvIndex] // U
                uvIndex += 2
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val imageBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun resizeBitmap(original: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val originalWidth = original.width
        val originalHeight = original.height

        // Calculate scale factor to maintain aspect ratio
        val scaleX = maxWidth.toFloat() / originalWidth
        val scaleY = maxHeight.toFloat() / originalHeight
        val scale = minOf(scaleX, scaleY)

        val newWidth = (originalWidth * scale).toInt()
        val newHeight = (originalHeight * scale).toInt()

        if (newWidth == originalWidth && newHeight == originalHeight) {
            return original
        }

        val matrix = Matrix()
        matrix.postScale(scale, scale)

        return Bitmap.createBitmap(original, 0, 0, originalWidth, originalHeight, matrix, true)
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, outputStream)
        return outputStream.toByteArray()
    }

    /**
     * Track bitmap for memory management
     */
    private fun trackBitmap(bitmap: Bitmap) {
        synchronized(weakImageReferences) {
            weakImageReferences.add(WeakReference(bitmap))
            // Clean up null references periodically
            if (weakImageReferences.size > 10) {
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

    /**
     * Start memory cleanup coroutine
     */
    private fun startMemoryCleanup() {
        val cleanupJob = launch {
            while (isEnabled) {
                delay(10000) // Clean up every 10 seconds
                cleanupMemory()
            }
        }
        snapshotJobs.add(cleanupJob)
    }

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
        val clampedInterval = intervalMs.coerceIn(MIN_SNAPSHOT_INTERVAL_MS, MAX_SNAPSHOT_INTERVAL_MS)
        snapshotIntervalMs = clampedInterval
        Timber.d("Snapshot interval updated: ${clampedInterval}ms (${1000f / clampedInterval} fps)")
    }

    /**
     * Configure JPEG compression quality
     * @param quality JPEG quality (50-100)
     */
    fun setJpegQuality(quality: Int) {
        jpegQuality = quality.coerceIn(LOW_QUALITY_JPEG, HIGH_QUALITY_JPEG)
        Timber.d("JPEG quality updated: $jpegQuality")
    }

    /**
     * Enable/disable privacy mode (affects image capture)
     * @param enabled When false, no images are captured
     */
    fun setPrivacyEnabled(enabled: Boolean) {
        privacyEnabled = enabled
        Timber.d("Privacy mode: ${if (enabled) "enabled" else "disabled"}")
    }

    fun isSnapshotsEnabled(): Boolean = isEnabled

    /**
     * Get current snapshot configuration
     * @return Triple of (width, height, intervalMs)
     */
    fun getSnapshotInfo(): Triple<Int, Int, Long> {
        return Triple(MAX_WIDTH, MAX_HEIGHT, snapshotIntervalMs)
    }

    /**
     * Get current frame rate in FPS
     */
    fun getCurrentFrameRate(): Float {
        return 1000f / snapshotIntervalMs
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
        return ProcessingStatus(
            isEnabled = isEnabled,
            currentProcessingCount = processingCount.get().toInt(),
            maxConcurrentProcessing = MAX_CONCURRENT_PROCESSING,
            frameRate = getCurrentFrameRate(),
            privacyEnabled = privacyEnabled
        )
    }

    data class ProcessingStatus(
        val isEnabled: Boolean,
        val currentProcessingCount: Int,
        val maxConcurrentProcessing: Int,
        val frameRate: Float,
        val privacyEnabled: Boolean
    )

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

        // Reset atomic counters
        lastSnapshotTime.set(0L)
        processingCount.set(0L)

        // Cancel coroutine scope
        cancel()

        Timber.d("ImageSnapshotManager destroyed successfully")
    }
}