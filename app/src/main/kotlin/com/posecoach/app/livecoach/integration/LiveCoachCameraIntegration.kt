package com.posecoach.app.livecoach.integration

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.LifecycleCoroutineScope
import com.posecoach.app.livecoach.LiveCoachManager
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.repository.PoseDetectionListener
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Integration class that bridges the camera/pose detection system with the Live Coach.
 * This class implements PoseDetectionListener to receive pose landmarks and forwards
 * them along with camera images to the Live Coach system.
 */
class LiveCoachCameraIntegration(
    private val liveCoachManager: LiveCoachManager,
    private val lifecycleScope: LifecycleCoroutineScope
) : PoseDetectionListener, ImageAnalysis.Analyzer {

    private var latestPoseLandmarks: PoseLandmarkResult? = null
    private var isAnalysisEnabled = true

    override fun onPoseDetected(result: PoseLandmarkResult) {
        if (!isAnalysisEnabled) return

        Timber.v("Pose detected with ${result.landmarks.size} landmarks")
        latestPoseLandmarks = result

        // Update Live Coach with latest landmarks
        liveCoachManager.updatePoseLandmarks(result)
    }

    override fun onPoseDetectionError(error: com.posecoach.corepose.models.PoseDetectionError) {
        Timber.e("Pose detection error: ${error.message}")
        // Clear landmarks on error
        latestPoseLandmarks = null
    }

    override fun analyze(image: ImageProxy) {
        if (!isAnalysisEnabled) {
            image.close()
            return
        }

        // Forward image with latest landmarks to Live Coach for snapshot processing
        latestPoseLandmarks?.let { landmarks ->
            lifecycleScope.launch {
                try {
                    liveCoachManager.processImageWithLandmarks(image, landmarks)
                } catch (e: Exception) {
                    Timber.e(e, "Error processing image with landmarks")
                } finally {
                    image.close()
                }
            }
        } ?: run {
            // No landmarks available yet
            image.close()
        }
    }

    fun setAnalysisEnabled(enabled: Boolean) {
        isAnalysisEnabled = enabled
        Timber.d("Live Coach camera analysis: $enabled")
    }

    fun isAnalysisEnabled(): Boolean = isAnalysisEnabled

    fun getCurrentLandmarks(): PoseLandmarkResult? = latestPoseLandmarks

    fun clearLandmarks() {
        latestPoseLandmarks = null
        Timber.d("Cleared pose landmarks")
    }
}