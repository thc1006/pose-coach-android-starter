package com.posecoach.app.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Manages MediaPipe pose detection integration
 * Processes camera frames and emits pose landmarks
 * Following CLAUDE.md requirements for MediaPipe integration
 */
class PoseDetectionManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    private var poseLandmarker: PoseLandmarker? = null

    private val _poseLandmarks = MutableSharedFlow<PoseLandmarkResult>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val poseLandmarks: SharedFlow<PoseLandmarkResult> = _poseLandmarks.asSharedFlow()

    private val _processingErrors = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val processingErrors: SharedFlow<String> = _processingErrors.asSharedFlow()

    init {
        initializePoseLandmarker()
    }

    private fun initializePoseLandmarker() {
        try {
            // Check if running in test environment
            if (isTestEnvironment()) {
                Timber.d("Skipping MediaPipe initialization in test environment")
                return
            }

            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker.task")
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, _ ->
                    handlePoseResult(result, System.currentTimeMillis())
                }
                .setErrorListener { error ->
                    lifecycleScope.launch {
                        _processingErrors.emit("Pose detection error: ${error.message}")
                    }
                    Timber.e(error, "MediaPipe pose detection error")
                }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            Timber.d("MediaPipe PoseLandmarker initialized successfully")

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize MediaPipe PoseLandmarker")
            lifecycleScope.launch {
                _processingErrors.emit("Failed to initialize pose detection: ${e.message}")
            }
        }
    }

    private fun isTestEnvironment(): Boolean {
        return try {
            Class.forName("org.robolectric.RobolectricTestRunner")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    /**
     * Process camera frame for pose detection
     * Called from camera analyzer on background thread
     */
    fun processFrame(imageProxy: ImageProxy) {
        poseLandmarker?.let { landmarker ->
            try {
                // Convert ImageProxy to MediaPipe format
                val mpImage = convertImageProxyToMpImage(imageProxy)

                // Detect pose landmarks asynchronously
                landmarker.detectAsync(mpImage, imageProxy.imageInfo.timestamp / 1_000_000)

            } catch (e: Exception) {
                Timber.e(e, "Error processing frame for pose detection")
                lifecycleScope.launch {
                    _processingErrors.emit("Frame processing error: ${e.message}")
                }
            } finally {
                imageProxy.close()
            }
        } ?: run {
            imageProxy.close()
            Timber.w("PoseLandmarker not initialized, skipping frame")
        }
    }

    private fun handlePoseResult(result: PoseLandmarkerResult, timestampMs: Long) {
        lifecycleScope.launch {
            try {
                // Convert MediaPipe result to our common format
                val poseLandmarkResult = convertToPoseLandmarkResult(result, timestampMs)
                _poseLandmarks.emit(poseLandmarkResult)

                Timber.v("Pose detected with ${result.landmarks().size} landmark sets")

            } catch (e: Exception) {
                Timber.e(e, "Error handling pose detection result")
                _processingErrors.emit("Result processing error: ${e.message}")
            }
        }
    }

    private fun convertImageProxyToMpImage(imageProxy: ImageProxy): MPImage {
        return try {
            // Convert ImageProxy to bitmap first
            val bitmap = imageProxyToBitmap(imageProxy)

            // Create MPImage from bitmap using the correct MediaPipe API
            val bitmapImageBuilder = com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap)
            bitmapImageBuilder.build()

        } catch (e: Exception) {
            Timber.e(e, "Failed to convert ImageProxy to MPImage")
            throw e
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        return when (imageProxy.format) {
            ImageFormat.YUV_420_888 -> yuv420ToBitmap(imageProxy)
            else -> {
                // Fallback: try to decode from first plane
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalStateException("Failed to decode ImageProxy to Bitmap")
            }
        }
    }

    private fun yuv420ToBitmap(imageProxy: ImageProxy): Bitmap {
        val yuvImage = imageProxyToYuvImage(imageProxy)
        val outputStream = ByteArrayOutputStream()
        val rect = Rect(0, 0, imageProxy.width, imageProxy.height)

        yuvImage.compressToJpeg(rect, 100, outputStream)
        val jpegBytes = outputStream.toByteArray()

        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: throw IllegalStateException("Failed to convert YUV to Bitmap")
    }

    private fun imageProxyToYuvImage(imageProxy: ImageProxy): YuvImage {
        val planes = imageProxy.planes
        val yData = planes[0].buffer
        val uData = planes[1].buffer
        val vData = planes[2].buffer

        val ySize = yData.remaining()
        val uSize = uData.remaining()
        val vSize = vData.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Copy Y data
        yData.get(nv21, 0, ySize)

        // Interleave U and V data for NV21 format
        val uvPixelStride = planes[1].pixelStride
        if (uvPixelStride == 1) {
            uData.get(nv21, ySize, uSize)
            vData.get(nv21, ySize + uSize, vSize)
        } else {
            // Handle pixel stride for U and V planes
            val uvBuffer = ByteArray(uSize + vSize)
            uData.get(uvBuffer, 0, uSize)
            vData.get(uvBuffer, uSize, vSize)

            // Interleave UV data
            for (i in 0 until uSize step uvPixelStride) {
                nv21[ySize + i] = uvBuffer[i]
                nv21[ySize + i + 1] = uvBuffer[uSize + i]
            }
        }

        return YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
    }

    private fun convertToPoseLandmarkResult(
        result: PoseLandmarkerResult,
        timestampMs: Long
    ): PoseLandmarkResult {
        // Convert MediaPipe landmarks to our common format
        val landmarks = mutableListOf<PoseLandmarkResult.Landmark>()
        val worldLandmarks = mutableListOf<PoseLandmarkResult.Landmark>()

        // Process the first pose if available
        if (result.landmarks().isNotEmpty()) {
            val poseMarks = result.landmarks()[0]
            val worldMarks = result.worldLandmarks()[0]

            for (i in 0 until minOf(poseMarks.size, worldMarks.size)) {
                val landmark = poseMarks[i]
                val worldLandmark = worldMarks[i]

                landmarks.add(
                    PoseLandmarkResult.Landmark(
                        x = landmark.x(),
                        y = landmark.y(),
                        z = landmark.z(),
                        visibility = landmark.visibility().orElse(1.0f),
                        presence = landmark.presence().orElse(1.0f)
                    )
                )

                worldLandmarks.add(
                    PoseLandmarkResult.Landmark(
                        x = worldLandmark.x(),
                        y = worldLandmark.y(),
                        z = worldLandmark.z(),
                        visibility = worldLandmark.visibility().orElse(1.0f),
                        presence = worldLandmark.presence().orElse(1.0f)
                    )
                )
            }
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = worldLandmarks,
            timestampMs = timestampMs,
            inferenceTimeMs = System.currentTimeMillis() - (timestampMs / 1_000_000)
        )
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        poseLandmarker?.close()
        poseLandmarker = null
        Timber.d("PoseDetectionManager cleaned up")
    }
}