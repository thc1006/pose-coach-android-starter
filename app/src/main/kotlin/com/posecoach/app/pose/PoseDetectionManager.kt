package com.posecoach.app.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import androidx.lifecycle.LifecycleCoroutineScope
import android.os.Build
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

    private var mlKitPoseDetector: MLKitPoseDetector? = null
    private var isInitialized = false

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
        // Use ML Kit for all devices - it's 16KB aligned
        Timber.i("Using ML Kit Pose Detection for SDK ${Build.VERSION.SDK_INT}")
        initializeMLKitDetector()
    }

    private fun initializeMLKitDetector() {
        try {
            mlKitPoseDetector = MLKitPoseDetector(context)
            if (mlKitPoseDetector?.initialize() == true) {
                isInitialized = true
                Timber.i("✅ ML Kit Pose Detector initialized successfully")
            } else {
                Timber.e("❌ Failed to initialize ML Kit Pose Detector")
                lifecycleScope.launch {
                    _processingErrors.emit("Pose detection initialization failed")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error initializing ML Kit Pose Detector")
            lifecycleScope.launch {
                _processingErrors.emit("Error: ${e.message}")
            }
        }
    }

    /**
     * Process camera frame for pose detection
     * Called from camera analyzer on background thread
     */
    fun processFrame(imageProxy: ImageProxy) {
        if (!isInitialized) {
            imageProxy.close()
            return
        }

        mlKitPoseDetector?.let { detector ->
            lifecycleScope.launch {
                try {
                    detector.processImageProxy(imageProxy)
                        .collect { result ->
                            _poseLandmarks.emit(result)
                            Timber.i("ML Kit pose emitted: ${result.landmarks.size} landmarks")
                        }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing frame with ML Kit")
                    _processingErrors.emit("Frame processing error: ${e.message}")
                }
            }
        } ?: run {
            imageProxy.close()
            Timber.w("ML Kit detector not initialized")
        }
    }


    /**
     * Clean up resources
     */
    fun cleanup() {
        mlKitPoseDetector?.close()
        mlKitPoseDetector = null
        isInitialized = false
        Timber.d("PoseDetectionManager cleaned up")
    }

    /**
     * Release resources (alias for cleanup)
     */
    fun release() {
        cleanup()
    }
}