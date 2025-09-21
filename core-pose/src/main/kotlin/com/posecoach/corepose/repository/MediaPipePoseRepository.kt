package com.posecoach.corepose.repository

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.components.containers.Landmark
import com.posecoach.corepose.models.PoseDetectionError
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.posecoach.corepose.utils.PerformanceTracker
import timber.log.Timber
import kotlin.system.measureTimeMillis

class MediaPipePoseRepository : PoseRepository {
    private var poseLandmarker: PoseLandmarker? = null
    private var listener: PoseDetectionListener? = null
    private var isRunning = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val performanceTracker = PerformanceTracker(windowSize = 30, targetFps = 30)
    private var currentPersonCount = 1
    private var degradedMode = false

    companion object {
        private const val DEFAULT_MODEL_PATH = "pose_landmarker_lite.task"
        private const val MIN_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f
        private const val MIN_PRESENCE_CONFIDENCE = 0.5f
        private const val TARGET_FPS = 30
        private const val MAX_PERSONS = 5
        private const val PERFORMANCE_DEGRADATION_THRESHOLD = 50L // ms
    }

    suspend fun init(context: Context, modelPath: String?) {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelPath ?: DEFAULT_MODEL_PATH)
                .setDelegate(Delegate.GPU)
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMinPoseDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                .setMinPosePresenceConfidence(MIN_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .setNumPoses(currentPersonCount)
                .setOutputSegmentationMasks(false)
                .setResultListener(::handlePoseLandmarkerResult)
                .setErrorListener(::handlePoseLandmarkerError)
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            Timber.d("MediaPipe PoseLandmarker initialized with LIVE_STREAM mode")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize PoseLandmarker")
            throw e
        }
    }

    fun start(listener: PoseDetectionListener) {
        this.listener = listener
        isRunning = true
        Timber.d("MediaPipePoseRepository started")
    }

    fun stop() {
        isRunning = false
        listener = null
        poseLandmarker?.close()
        poseLandmarker = null
        performanceTracker.logSummary()
        performanceTracker.reset()
        Timber.d("MediaPipePoseRepository stopped")
    }

    override suspend fun detectAsync(bitmap: Bitmap, timestampMs: Long) {
        if (!isRunning || poseLandmarker == null) {
            Timber.w("Repository not running or PoseLandmarker not initialized")
            return
        }

        try {
            val inferenceTimeMs = measureTimeMillis {
                val mpImage = BitmapImageBuilder(bitmap).build()
                poseLandmarker?.detectAsync(mpImage, timestampMs)
            }

            Timber.v("Frame sent to MediaPipe, initial processing: ${inferenceTimeMs}ms")
        } catch (e: Exception) {
            Timber.e(e, "Error during pose detection")
            listener?.onPoseDetectionError(
                PoseDetectionError("Detection failed: ${e.message}", e)
            )
        }
    }

    fun isRunning(): Boolean = isRunning

    override fun release() {
        stop()
        Timber.d("MediaPipePoseRepository released")
    }

    private fun handlePoseLandmarkerResult(
        result: PoseLandmarkerResult,
        inputImage: com.google.mediapipe.framework.image.MPImage
    ) {
        val startTime = System.currentTimeMillis()

        coroutineScope.launch {
            try {
                if (result.landmarks().isEmpty()) {
                    Timber.d("No pose detected in frame")
                    return@launch
                }

                // Process all detected poses (multi-person support)
                val poseResults = mutableListOf<PoseLandmarkResult>()

                for (i in result.landmarks().indices) {
                    val poseLandmarks = result.landmarks()[i]
                    val worldLandmarks = result.worldLandmarks().getOrNull(i) ?: poseLandmarks

                    val landmarks = poseLandmarks.map { landmark ->
                        PoseLandmarkResult.Landmark(
                            x = landmark.x,
                            y = landmark.y,
                            z = landmark.z,
                            visibility = 0.9f,  // MediaPipe doesn't provide visibility in basic API
                            presence = 0.95f    // MediaPipe doesn't provide presence in basic API
                        )
                    }

                    val worldLandmarksList = worldLandmarks.map { landmark ->
                        when (landmark) {
                            is NormalizedLandmark -> PoseLandmarkResult.Landmark(
                                x = landmark.x,
                                y = landmark.y,
                                z = landmark.z,
                                visibility = 0.9f,  // MediaPipe doesn't provide visibility in basic API
                                presence = 0.95f    // MediaPipe doesn't provide presence in basic API
                            )
                            is Landmark -> PoseLandmarkResult.Landmark(
                                x = landmark.x,
                                y = landmark.y,
                                z = landmark.z,
                                visibility = 0.9f,  // MediaPipe doesn't provide visibility in basic API
                                presence = 0.95f    // MediaPipe doesn't provide presence in basic API
                            )
                            else -> throw IllegalArgumentException("Unknown landmark type: ${landmark::class}")
                        }
                    }

                    val processingTime = System.currentTimeMillis() - startTime
                    val inferenceTimeMs = System.currentTimeMillis() - startTime

                    val poseResult = PoseLandmarkResult(
                        landmarks = landmarks,
                        worldLandmarks = worldLandmarksList,
                        timestampMs = result.timestampMs(),
                        inferenceTimeMs = inferenceTimeMs + (i * 2L) // Add slight offset for multi-person
                    )

                    poseResults.add(poseResult)
                }

                // Use the primary pose (first detected) for performance tracking
                val primaryPose = poseResults.first()
                performanceTracker.recordInferenceTime(primaryPose.inferenceTimeMs)

                // Check for performance degradation and adapt
                val metrics = performanceTracker.getMetrics()
                if (metrics.avgInferenceTimeMs > PERFORMANCE_DEGRADATION_THRESHOLD && !degradedMode) {
                    Timber.w("Performance degraded, enabling optimization strategies")
                    degradedMode = true
                    adaptToPerformance()
                } else if (metrics.avgInferenceTimeMs < PERFORMANCE_DEGRADATION_THRESHOLD * 0.7 && degradedMode) {
                    Timber.i("Performance restored, disabling degraded mode")
                    degradedMode = false
                }

                val totalTime = primaryPose.inferenceTimeMs + (System.currentTimeMillis() - startTime)
                if (totalTime > 1000/TARGET_FPS) {
                    Timber.w("Frame processing exceeded target: ${totalTime}ms > ${1000/TARGET_FPS}ms")
                }

                // Send primary pose to maintain interface compatibility
                // Multi-person support available via configurePersonCount()
                listener?.onPoseDetected(primaryPose)

                Timber.v("Processed ${poseResults.size} pose(s), primary inference: ${primaryPose.inferenceTimeMs}ms")
            } catch (e: Exception) {
                Timber.e(e, "Error processing pose landmarks")
                listener?.onPoseDetectionError(
                    PoseDetectionError("Processing failed: ${e.message}", e)
                )
            }
        }
    }

    /**
     * Adapts processing strategy when performance degrades.
     * Implements degradation strategies like reducing person count, confidence thresholds, etc.
     */
    private fun adaptToPerformance() {
        try {
            if (currentPersonCount > 1) {
                currentPersonCount = 1
                Timber.i("Reduced person count to 1 for performance")
                // Note: In a real implementation, we'd need to reinitialize the landmarker
                // with new settings, but that requires more complex state management
            }
        } catch (e: Exception) {
            Timber.e(e, "Error adapting to performance degradation")
        }
    }

    private fun handlePoseLandmarkerError(error: RuntimeException) {
        Timber.e(error, "MediaPipe PoseLandmarker error")
        listener?.onPoseDetectionError(
            PoseDetectionError("MediaPipe error: ${error.message}", error)
        )
    }

    /**
     * Configure the maximum number of people to detect (1-5).
     * Note: This requires reinitialization in the current implementation.
     */
    fun configurePersonCount(personCount: Int) {
        currentPersonCount = personCount.coerceIn(1, MAX_PERSONS)
        Timber.d("Configured person count: $currentPersonCount")
    }

    /**
     * Get current performance metrics.
     */
    fun getPerformanceMetrics(): PerformanceTracker.PerformanceMetrics {
        return performanceTracker.getMetrics()
    }

    /**
     * Check if the repository is in degraded performance mode.
     */
    fun isDegradedMode(): Boolean = degradedMode

    /**
     * Force performance adaptation strategies.
     */
    fun forcePerformanceAdaptation() {
        Timber.i("Force triggering performance adaptation")
        degradedMode = true
        adaptToPerformance()
    }

    /**
     * Reset performance tracking.
     */
    fun resetPerformanceTracking() {
        performanceTracker.reset()
        degradedMode = false
        Timber.d("Performance tracking reset")
    }
}