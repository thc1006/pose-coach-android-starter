package com.posecoach.corepose.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.posecoach.corepose.models.PoseDetectionError
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.repository.PoseDetectionListener
import com.posecoach.corepose.repository.PoseRepository
import com.posecoach.corepose.repository.PoseRepositoryErrorHandler
import kotlinx.coroutines.*
import timber.log.Timber
import java.nio.ByteBuffer
import kotlin.system.measureTimeMillis

/**
 * CameraX ImageAnalyzer that integrates with PoseRepository for real-time pose detection.
 * Handles image preprocessing, format conversion, and performance optimization.
 */
class CameraPoseAnalyzer(
    private val poseRepository: PoseRepository,
    private val listener: PoseDetectionListener? = null
) : ImageAnalysis.Analyzer {

    private val analyzerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isProcessing = false
    private var frameDropCount = 0
    private var totalFrameCount = 0
    private var consecutiveNoDetectionCount = 0

    // Performance settings
    private var targetImageWidth = 720
    private var targetImageHeight = 1280
    private var processingQuality = ProcessingQuality.HIGH

    enum class ProcessingQuality(
        val targetImageWidth: Int,
        val targetImageHeight: Int,
        val frameSkipRate: Int
    ) {
        HIGH(720, 1280, 1),
        MEDIUM(480, 640, 2),
        LOW(240, 320, 3)
    }

    companion object {
        private const val MAX_CONSECUTIVE_NO_DETECTION = 15
        private const val FRAME_TIMEOUT_MS = 100L
    }

    override fun analyze(imageProxy: ImageProxy) {
        totalFrameCount++

        // Skip if already processing to maintain frame rate
        if (isProcessing) {
            frameDropCount++
            if (frameDropCount % 10 == 0) {
                Timber.w("Dropped $frameDropCount frames due to processing backlog")
            }
            imageProxy.close()
            return
        }

        isProcessing = true

        analyzerScope.launch {
            try {
                val processingTime = measureTimeMillis {
                    processImageProxy(imageProxy)
                }

                if (processingTime > FRAME_TIMEOUT_MS) {
                    Timber.w("Frame processing took ${processingTime}ms (>${FRAME_TIMEOUT_MS}ms)")
                    adaptProcessingQuality()
                }

            } catch (e: Exception) {
                PoseRepositoryErrorHandler.handleError(e, "camera_analysis", listener)
            } finally {
                isProcessing = false
                imageProxy.close()
            }
        }
    }

    private suspend fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = convertImageProxyToBitmap(imageProxy)
        if (bitmap != null) {
            val timestampMs = System.currentTimeMillis()
            poseRepository.detectAsync(bitmap, timestampMs)
        } else {
            Timber.e("Failed to convert ImageProxy to Bitmap")
            listener?.onPoseDetectionError(
                PoseDetectionError("Image conversion failed")
            )
        }
    }

    /**
     * Converts ImageProxy to Bitmap with proper format handling and optimization.
     */
    private fun convertImageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            when (imageProxy.format) {
                ImageFormat.YUV_420_888 -> yuv420ToBitmap(imageProxy)
                ImageFormat.NV21 -> nv21ToBitmap(imageProxy)
                else -> {
                    Timber.w("Unsupported image format: ${imageProxy.format}")
                    null
                }
            }?.let { bitmap ->
                // Resize if needed for performance
                resizeBitmapIfNeeded(bitmap, imageProxy.imageInfo.rotationDegrees)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error converting ImageProxy to Bitmap")
            null
        }
    }

    /**
     * Converts YUV_420_888 format to Bitmap.
     */
    private fun yuv420ToBitmap(imageProxy: ImageProxy): Bitmap? {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        val uvPixelStride = imageProxy.planes[1].pixelStride

        if (uvPixelStride == 1) {
            uBuffer.get(nv21, ySize, uSize)
            vBuffer.get(nv21, ySize + uSize, vSize)
        } else {
            // Interleaved UV
            val uvBufferPos = ySize
            for (i in 0 until uSize step uvPixelStride) {
                nv21[uvBufferPos + i] = vBuffer.get(i)
                nv21[uvBufferPos + i + 1] = uBuffer.get(i)
            }
        }

        val yuvImage = android.graphics.YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val outputStream = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            80, // Compression quality
            outputStream
        )

        val jpegByteArray = outputStream.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.size)
    }

    /**
     * Converts NV21 format to Bitmap.
     */
    private fun nv21ToBitmap(imageProxy: ImageProxy): Bitmap? {
        val buffer = imageProxy.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        val yuvImage = android.graphics.YuvImage(
            data,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val outputStream = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            85, // Higher quality for NV21
            outputStream
        )

        val jpegByteArray = outputStream.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.size)
    }

    /**
     * Resizes bitmap to target dimensions and applies rotation if needed.
     */
    private fun resizeBitmapIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = Matrix().apply {
            if (rotationDegrees != 0) {
                postRotate(rotationDegrees.toFloat())
            }
        }

        // Calculate optimal size based on processing quality
        val quality = processingQuality
        val targetWidth = quality.targetImageWidth
        val targetHeight = quality.targetImageHeight

        return if (bitmap.width > targetWidth || bitmap.height > targetHeight) {
            val scaleFactor = minOf(
                targetWidth.toFloat() / bitmap.width,
                targetHeight.toFloat() / bitmap.height
            )

            matrix.preScale(scaleFactor, scaleFactor)

            Bitmap.createBitmap(
                bitmap,
                0, 0,
                bitmap.width, bitmap.height,
                matrix,
                true
            ).also {
                if (it != bitmap) bitmap.recycle()
            }
        } else if (rotationDegrees != 0) {
            Bitmap.createBitmap(
                bitmap,
                0, 0,
                bitmap.width, bitmap.height,
                matrix,
                true
            ).also {
                if (it != bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }
    }

    /**
     * Adapts processing quality based on performance metrics.
     */
    private fun adaptProcessingQuality() {
        when (processingQuality) {
            ProcessingQuality.HIGH -> {
                processingQuality = ProcessingQuality.MEDIUM
                Timber.i("Reduced processing quality to MEDIUM for performance")
            }
            ProcessingQuality.MEDIUM -> {
                processingQuality = ProcessingQuality.LOW
                Timber.i("Reduced processing quality to LOW for performance")
            }
            ProcessingQuality.LOW -> {
                Timber.w("Already at lowest processing quality")
            }
        }
    }

    /**
     * Configures target image dimensions for processing.
     */
    fun setTargetImageSize(width: Int, height: Int) {
        targetImageWidth = width
        targetImageHeight = height
        Timber.d("Target image size set to ${width}x${height}")
    }

    /**
     * Manually sets processing quality.
     */
    fun setProcessingQuality(quality: ProcessingQuality) {
        processingQuality = quality
        Timber.d("Processing quality set to $quality")
    }

    /**
     * Gets current processing statistics.
     */
    fun getProcessingStats(): ProcessingStats {
        return ProcessingStats(
            totalFrames = totalFrameCount,
            droppedFrames = frameDropCount,
            dropRate = if (totalFrameCount > 0) frameDropCount.toFloat() / totalFrameCount else 0f,
            consecutiveNoDetection = consecutiveNoDetectionCount,
            currentQuality = processingQuality
        )
    }

    /**
     * Resets processing statistics.
     */
    fun resetStats() {
        totalFrameCount = 0
        frameDropCount = 0
        consecutiveNoDetectionCount = 0
        Timber.d("Processing statistics reset")
    }

    /**
     * Cleanup resources when analyzer is no longer needed.
     */
    fun cleanup() {
        analyzerScope.cancel()
        Timber.d("CameraPoseAnalyzer cleaned up")
    }

    data class ProcessingStats(
        val totalFrames: Int,
        val droppedFrames: Int,
        val dropRate: Float,
        val consecutiveNoDetection: Int,
        val currentQuality: ProcessingQuality
    )

    /**
     * Listener that wraps the original listener and tracks detection patterns.
     */
    inner class AnalyzerPoseDetectionListener(
        private val originalListener: PoseDetectionListener
    ) : PoseDetectionListener {

        override fun onPoseDetected(result: PoseLandmarkResult) {
            consecutiveNoDetectionCount = 0
            originalListener.onPoseDetected(result)
        }

        override fun onPoseDetectionError(error: PoseDetectionError) {
            if (error.message.contains("No pose detected", ignoreCase = true)) {
                consecutiveNoDetectionCount++

                if (consecutiveNoDetectionCount > MAX_CONSECUTIVE_NO_DETECTION) {
                    PoseRepositoryErrorHandler.handleNoPoseDetected(
                        totalFrameCount,
                        consecutiveNoDetectionCount,
                        originalListener
                    )
                }
            }

            originalListener.onPoseDetectionError(error)
        }
    }
}