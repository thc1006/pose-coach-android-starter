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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.io.ByteArrayOutputStream
import kotlin.coroutines.CoroutineContext

class ImageSnapshotManager(
    private val coroutineScope: CoroutineScope
) : CoroutineScope {

    override val coroutineContext: CoroutineContext =
        coroutineScope.coroutineContext + SupervisorJob()

    companion object {
        // Low resolution for Live API - reduces payload size
        private const val MAX_WIDTH = 320
        private const val MAX_HEIGHT = 240
        private const val JPEG_QUALITY = 70
        private const val SNAPSHOT_INTERVAL_MS = 1500L // 1.5 seconds
    }

    private val _snapshots = MutableSharedFlow<PoseSnapshot>(
        replay = 0,
        extraBufferCapacity = 5
    )
    val snapshots: SharedFlow<PoseSnapshot> = _snapshots.asSharedFlow()

    private val _realtimeInput = MutableSharedFlow<LiveApiMessage.RealtimeInput>(
        replay = 0,
        extraBufferCapacity = 5
    )
    val realtimeInput: SharedFlow<LiveApiMessage.RealtimeInput> = _realtimeInput.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 3
    )
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var lastSnapshotTime = 0L
    private var isEnabled = false
    private var snapshotJob: Job? = null

    fun startSnapshots() {
        if (isEnabled) {
            Timber.w("Snapshots already enabled")
            return
        }

        isEnabled = true
        lastSnapshotTime = 0L // Force first snapshot
        Timber.d("Image snapshots started")
    }

    fun stopSnapshots() {
        if (!isEnabled) {
            Timber.w("Snapshots not enabled")
            return
        }

        isEnabled = false
        snapshotJob?.cancel()
        snapshotJob = null
        Timber.d("Image snapshots stopped")
    }

    fun processImageWithLandmarks(
        imageProxy: ImageProxy,
        landmarks: PoseLandmarkResult?
    ) {
        if (!isEnabled || landmarks == null) {
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSnapshotTime < SNAPSHOT_INTERVAL_MS) {
            return // Skip this frame
        }

        lastSnapshotTime = currentTime

        // Process in background to avoid blocking camera thread
        snapshotJob?.cancel()
        snapshotJob = launch {
            try {
                processSnapshot(imageProxy, landmarks, currentTime)
            } catch (e: Exception) {
                Timber.e(e, "Error processing snapshot")
                _errors.emit("Snapshot processing error: ${e.message}")
            }
        }
    }

    private suspend fun processSnapshot(
        imageProxy: ImageProxy,
        landmarks: PoseLandmarkResult,
        timestamp: Long
    ) = withContext(Dispatchers.Default) {
        try {
            // Convert ImageProxy to Bitmap
            val bitmap = imageProxyToBitmap(imageProxy)

            // Resize to low resolution
            val resizedBitmap = resizeBitmap(bitmap, MAX_WIDTH, MAX_HEIGHT)

            // Convert to JPEG bytes
            val jpegBytes = bitmapToJpegBytes(resizedBitmap)

            // Encode to Base64
            val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

            // Create snapshot
            val snapshot = PoseSnapshot(
                imageData = base64Image,
                landmarks = landmarks,
                timestamp = timestamp
            )

            // Emit snapshot
            _snapshots.emit(snapshot)

            // Create Live API message
            val mediaChunk = MediaChunk(
                mimeType = "image/jpeg",
                data = base64Image
            )

            val realtimeInputMessage = LiveApiMessage.RealtimeInput(
                mediaChunks = listOf(mediaChunk)
            )

            _realtimeInput.emit(realtimeInputMessage)

            Timber.v("Processed snapshot: ${jpegBytes.size} bytes -> ${base64Image.length} chars")

            // Clean up
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
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        return outputStream.toByteArray()
    }

    fun setSnapshotInterval(intervalMs: Long) {
        // For now, we use a fixed interval, but this could be made configurable
        Timber.d("Snapshot interval: ${intervalMs}ms (currently fixed at ${SNAPSHOT_INTERVAL_MS}ms)")
    }

    fun isSnapshotsEnabled(): Boolean = isEnabled

    fun getSnapshotInfo(): Triple<Int, Int, Long> {
        return Triple(MAX_WIDTH, MAX_HEIGHT, SNAPSHOT_INTERVAL_MS)
    }

    fun destroy() {
        stopSnapshots()
        cancel() // Cancel coroutine scope
    }
}