package com.posecoach.app.livecoach.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * Handles image compression, format conversion, and bitmap processing
 *
 * Responsibilities:
 * - Convert ImageProxy to Bitmap (various formats)
 * - Resize images while maintaining aspect ratio
 * - Compress bitmaps to JPEG with configurable quality
 * - Track compression metrics and performance
 * - Manage memory efficiently during processing
 *
 * This component focuses purely on image processing operations
 * and does not handle scheduling or rate limiting.
 */
class ImageCompressionHandler(
    private val coroutineScope: CoroutineScope
) : CoroutineScope {

    override val coroutineContext: CoroutineContext =
        coroutineScope.coroutineContext + SupervisorJob()

    // Processing metrics
    private val totalImagesProcessed = AtomicLong(0)
    private val totalBytesGenerated = AtomicLong(0)
    private val totalProcessingTimeMs = AtomicLong(0)
    private val compressionRatioSum = AtomicLong(0) // Store as percentage * 1000 for precision

    /**
     * Process an ImageProxy and convert it to compressed JPEG
     * @param imageProxy The camera image to process
     * @param config Optional compression configuration
     * @return SnapshotResult with success status and metrics
     */
    suspend fun processImageProxy(
        imageProxy: ImageProxy,
        config: SnapshotConfig = SnapshotConfig()
    ): SnapshotResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        try {
            // Convert ImageProxy to Bitmap
            val bitmap = imageProxyToBitmap(imageProxy)

            // Resize to target dimensions
            val resizedBitmap = resizeBitmap(bitmap, config.maxWidth, config.maxHeight)

            // Compress to JPEG
            val jpegBytes = compressBitmap(resizedBitmap, config.jpegQuality)

            // Calculate metrics
            val processingTime = System.currentTimeMillis() - startTime
            val originalSize = bitmap.byteCount
            val compressionRatio = if (originalSize > 0) {
                jpegBytes.size.toFloat() / originalSize.toFloat()
            } else {
                1.0f
            }

            // Update metrics
            updateMetrics(jpegBytes.size, processingTime, compressionRatio)

            // Clean up bitmaps
            bitmap.recycle()
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }

            SnapshotResult(
                success = true,
                fileSizeBytes = jpegBytes.size,
                processingTimeMs = processingTime,
                compressionRatio = compressionRatio
            )

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Timber.e(e, "Failed to process image")

            SnapshotResult(
                success = false,
                processingTimeMs = processingTime,
                error = "Image processing failed: ${e.message}"
            )
        }
    }

    /**
     * Convert ImageProxy to Bitmap supporting multiple formats
     */
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        return when (imageProxy.format) {
            ImageFormat.YUV_420_888 -> yuvToBitmap(imageProxy)
            ImageFormat.JPEG -> jpegToBitmap(imageProxy)
            else -> throw IllegalArgumentException("Unsupported image format: ${imageProxy.format}")
        }
    }

    /**
     * Resize bitmap while maintaining aspect ratio
     * @param original Source bitmap
     * @param maxWidth Maximum width
     * @param maxHeight Maximum height
     * @return Resized bitmap (may be the same instance if no resize needed)
     */
    fun resizeBitmap(original: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val originalWidth = original.width
        val originalHeight = original.height

        // Calculate scale factor to maintain aspect ratio
        val scaleX = maxWidth.toFloat() / originalWidth
        val scaleY = maxHeight.toFloat() / originalHeight
        val scale = minOf(scaleX, scaleY)

        val newWidth = (originalWidth * scale).toInt()
        val newHeight = (originalHeight * scale).toInt()

        // Return original if no resizing needed
        if (newWidth == originalWidth && newHeight == originalHeight) {
            return original
        }

        val matrix = Matrix()
        matrix.postScale(scale, scale)

        return Bitmap.createBitmap(original, 0, 0, originalWidth, originalHeight, matrix, true)
    }

    /**
     * Compress bitmap to JPEG bytes
     * @param bitmap Source bitmap
     * @param quality JPEG quality (1-100)
     * @return JPEG byte array
     */
    fun compressBitmap(bitmap: Bitmap, quality: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }

    /**
     * Get compression performance metrics
     */
    fun getCompressionMetrics(): CompressionMetrics {
        val totalImages = totalImagesProcessed.get()
        val totalBytes = totalBytesGenerated.get()
        val totalTime = totalProcessingTimeMs.get()
        val compressionSum = compressionRatioSum.get()

        return CompressionMetrics(
            totalImages = totalImages,
            totalBytes = totalBytes,
            averageCompressionRatio = if (totalImages > 0) {
                (compressionSum / 1000.0f) / totalImages
            } else {
                0f
            },
            averageProcessingTimeMs = if (totalImages > 0) totalTime / totalImages else 0L
        )
    }

    /**
     * Get supported image formats and their properties
     */
    fun getSupportedFormats(): List<ImageFormatInfo> {
        return listOf(
            ImageFormatInfo(
                format = ImageFormat.JPEG,
                supportedByProcessor = true,
                requiresConversion = false,
                conversionMethod = null
            ),
            ImageFormatInfo(
                format = ImageFormat.YUV_420_888,
                supportedByProcessor = true,
                requiresConversion = true,
                conversionMethod = "YUV to RGB conversion"
            )
        )
    }

    /**
     * Check if the handler has been destroyed
     */
    fun isDestroyed(): Boolean {
        return !coroutineContext.isActive
    }

    /**
     * Convert YUV_420_888 ImageProxy to Bitmap
     */
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
            // Planes are already packed
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

    /**
     * Convert JPEG ImageProxy to Bitmap
     */
    private fun jpegToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * Update processing metrics atomically
     */
    private fun updateMetrics(fileSizeBytes: Int, processingTimeMs: Long, compressionRatio: Float) {
        totalImagesProcessed.incrementAndGet()
        totalBytesGenerated.addAndGet(fileSizeBytes.toLong())
        totalProcessingTimeMs.addAndGet(processingTimeMs)

        // Store compression ratio as integer (multiplied by 1000 for precision)
        compressionRatioSum.addAndGet((compressionRatio * 1000).toLong())
    }

    /**
     * Destroy the compression handler and clean up resources
     */
    fun destroy() {
        Timber.d("Destroying image compression handler")
        cancel()
        Timber.d("Image compression handler destroyed")
    }

    /**
     * Compression metrics data class
     */
    data class CompressionMetrics(
        val totalImages: Long,
        val totalBytes: Long,
        val averageCompressionRatio: Float,
        val averageProcessingTimeMs: Long
    )
}