package com.posecoach.app.pose

import android.content.Context
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

/**
 * ML Kit Pose Detection implementation
 * This is 16KB aligned and works on Android 15+ (SDK 36)
 */
class MLKitPoseDetector(private val context: Context) {

    private var poseDetector: PoseDetector? = null
    private var isProcessing = false

    // Store the image dimensions for proper coordinate normalization
    private var lastImageWidth = 0
    private var lastImageHeight = 0
    private var lastRotationDegrees = 0

    fun initialize(): Boolean {
        return try {
            Timber.i("Initializing ML Kit Pose Detector...")

            // Configure ML Kit pose detector options
            val options = AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .setPreferredHardwareConfigs(
                    AccuratePoseDetectorOptions.CPU
                )
                .build()

            // Create the pose detector
            poseDetector = PoseDetection.getClient(options)

            Timber.i("✅ ML Kit Pose Detector initialized successfully")
            true

        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to initialize ML Kit Pose Detector")
            false
        }
    }

    fun processImageProxy(imageProxy: ImageProxy): Flow<PoseLandmarkResult> = callbackFlow {
        if (isProcessing) {
            imageProxy.close()
            close()
            return@callbackFlow
        }

        isProcessing = true

        try {
            @androidx.camera.core.ExperimentalGetImage
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                // Store image dimensions and rotation for coordinate normalization
                lastImageWidth = imageProxy.width
                lastImageHeight = imageProxy.height
                lastRotationDegrees = imageProxy.imageInfo.rotationDegrees

                val inputImage = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                poseDetector?.process(inputImage)
                    ?.addOnSuccessListener { pose ->
                        Timber.d("ML Kit detected pose with ${pose.allPoseLandmarks.size} landmarks")

                        // Convert ML Kit pose to our format
                        val result = convertMLKitPose(pose)
                        trySend(result)

                        imageProxy.close()
                        isProcessing = false
                    }
                    ?.addOnFailureListener { e ->
                        Timber.e(e, "ML Kit pose detection failed")
                        imageProxy.close()
                        isProcessing = false
                    }
                    ?: run {
                        imageProxy.close()
                        isProcessing = false
                    }
            } else {
                imageProxy.close()
                isProcessing = false
            }

        } catch (e: Exception) {
            Timber.e(e, "Error processing image with ML Kit")
            imageProxy.close()
            isProcessing = false
        }

        awaitClose {
            isProcessing = false
        }
    }

    private fun convertMLKitPose(pose: Pose): PoseLandmarkResult {
        val landmarks = mutableListOf<PoseLandmarkResult.Landmark>()
        val worldLandmarks = mutableListOf<PoseLandmarkResult.Landmark>()

        // Calculate the actual dimensions considering rotation
        val (actualWidth, actualHeight) = when (lastRotationDegrees) {
            90, 270 -> Pair(lastImageHeight.toFloat(), lastImageWidth.toFloat())
            else -> Pair(lastImageWidth.toFloat(), lastImageHeight.toFloat())
        }

        // ML Kit provides 33 pose landmarks (same as MediaPipe)
        val mlKitLandmarkTypes = listOf(
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_EYE_INNER, PoseLandmark.LEFT_EYE,
            PoseLandmark.LEFT_EYE_OUTER, PoseLandmark.RIGHT_EYE_INNER,
            PoseLandmark.RIGHT_EYE, PoseLandmark.RIGHT_EYE_OUTER,
            PoseLandmark.LEFT_EAR, PoseLandmark.RIGHT_EAR,
            PoseLandmark.LEFT_MOUTH, PoseLandmark.RIGHT_MOUTH,
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_PINKY, PoseLandmark.RIGHT_PINKY,
            PoseLandmark.LEFT_INDEX, PoseLandmark.RIGHT_INDEX,
            PoseLandmark.LEFT_THUMB, PoseLandmark.RIGHT_THUMB,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE,
            PoseLandmark.LEFT_HEEL, PoseLandmark.RIGHT_HEEL,
            PoseLandmark.LEFT_FOOT_INDEX, PoseLandmark.RIGHT_FOOT_INDEX
        )

        mlKitLandmarkTypes.forEach { landmarkType ->
            val mlKitLandmark = pose.getPoseLandmark(landmarkType)

            if (mlKitLandmark != null) {
                // Convert screen coordinates (normalized to 0-1)
                // ML Kit returns pixel coordinates in the input image coordinate system
                val position = mlKitLandmark.position

                // Normalize coordinates to 0-1 range using actual image dimensions
                val normalizedX = if (actualWidth > 0) position.x / actualWidth else 0f
                val normalizedY = if (actualHeight > 0) position.y / actualHeight else 0f

                landmarks.add(
                    PoseLandmarkResult.Landmark(
                        x = normalizedX.coerceIn(0f, 1f),
                        y = normalizedY.coerceIn(0f, 1f),
                        z = 0f,  // ML Kit doesn't provide Z in 2D mode
                        visibility = mlKitLandmark.inFrameLikelihood,
                        presence = mlKitLandmark.inFrameLikelihood
                    )
                )

                // Convert 3D world coordinates
                val position3D = mlKitLandmark.position3D
                worldLandmarks.add(
                    PoseLandmarkResult.Landmark(
                        x = position3D.x,
                        y = position3D.y,
                        z = position3D.z,
                        visibility = mlKitLandmark.inFrameLikelihood,
                        presence = mlKitLandmark.inFrameLikelihood
                    )
                )
            } else {
                // Add placeholder for missing landmark
                landmarks.add(
                    PoseLandmarkResult.Landmark(0f, 0f, 0f, 0f, 0f)
                )
                worldLandmarks.add(
                    PoseLandmarkResult.Landmark(0f, 0f, 0f, 0f, 0f)
                )
            }
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = worldLandmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 0L
        )
    }

    fun close() {
        try {
            poseDetector?.close()
            poseDetector = null
            Timber.i("ML Kit Pose Detector closed")
        } catch (e: Exception) {
            Timber.e(e, "Error closing ML Kit Pose Detector")
        }
    }
}