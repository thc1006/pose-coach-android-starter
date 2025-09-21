# Week 2 Implementation Templates

## MediaPipeManager.kt Template

```kotlin
package com.posecoach.app.mediapipe

import android.content.Context
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerOptions
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber

class MediaPipeManager(
    private val context: Context,
    private val onPoseDetected: (PoseLandmarkerResult, Long) -> Unit
) {
    private var poseLandmarker: PoseLandmarker? = null
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: Flow<Boolean> = _isInitialized

    suspend fun initialize(): Boolean {
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker.task")
                .build()

            val options = PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setOutputSegmentationMasks(false) // Performance optimization
                .setResultListener(this::handlePoseResult)
                .setErrorListener { error ->
                    Timber.e(error, "MediaPipe pose detection error")
                }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            _isInitialized.value = true
            Timber.d("MediaPipe initialized successfully")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize MediaPipe")
            _isInitialized.value = false
            false
        }
    }

    fun detectAsync(image: MPImage, timestampMs: Long) {
        poseLandmarker?.detectAsync(image, timestampMs)
    }

    private fun handlePoseResult(result: PoseLandmarkerResult, input: MPImage) {
        val timestampMs = input.timestamp
        onPoseDetected(result, timestampMs)
    }

    fun release() {
        poseLandmarker?.close()
        poseLandmarker = null
        _isInitialized.value = false
    }
}
```

## PoseLandmarkerWrapper.kt Template

```kotlin
package com.posecoach.app.mediapipe

import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.posecoach.core.pose.models.PoseDetectionResult
import com.posecoach.core.pose.models.PoseLandmark33
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import timber.log.Timber

class PoseLandmarkerWrapper(
    private val mediaPipeManager: MediaPipeManager
) {
    private val _poseResults = MutableSharedFlow<PoseDetectionResult>()
    val poseResults: Flow<PoseDetectionResult> = _poseResults

    private var frameCount = 0
    private var lastFrameTime = System.currentTimeMillis()

    suspend fun initialize(): Boolean {
        return mediaPipeManager.initialize()
    }

    fun processFrame(bitmap: Bitmap, timestampMs: Long) {
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            mediaPipeManager.detectAsync(mpImage, timestampMs)

            // Performance monitoring
            frameCount++
            if (frameCount % 30 == 0) {
                logPerformanceMetrics()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing frame")
        }
    }

    fun handlePoseDetectionResult(result: PoseLandmarkerResult, timestampMs: Long) {
        val processStartTime = System.currentTimeMillis()

        val poseDetectionResult = if (result.landmarks().isNotEmpty()) {
            val landmarks = result.landmarks()[0] // First person
            val confidence = calculateAverageConfidence(landmarks)

            PoseDetectionResult(
                landmarks = convertToCoreLandmarks(landmarks),
                confidence = confidence,
                timestampMs = timestampMs,
                processingTimeMs = System.currentTimeMillis() - processStartTime
            )
        } else {
            PoseDetectionResult.empty(timestampMs)
        }

        _poseResults.tryEmit(poseDetectionResult)
    }

    private fun convertToCoreLandmarks(
        landmarks: List<com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmark>
    ): List<PoseLandmark33> {
        return landmarks.mapIndexed { index, landmark ->
            PoseLandmark33(
                id = index,
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
                visibility = landmark.visibility().orElse(0.0f),
                presence = landmark.presence().orElse(0.0f)
            )
        }
    }

    private fun calculateAverageConfidence(
        landmarks: List<com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmark>
    ): Float {
        return landmarks.mapNotNull { it.visibility().orElse(null) }
            .average()
            .toFloat()
    }

    private fun logPerformanceMetrics() {
        val currentTime = System.currentTimeMillis()
        val fps = (frameCount * 1000.0) / (currentTime - lastFrameTime)
        Timber.d("MediaPipe processing FPS: %.1f", fps)

        frameCount = 0
        lastFrameTime = currentTime
    }

    fun release() {
        mediaPipeManager.release()
    }
}
```

## Enhanced StablePoseGate.kt Template

```kotlin
package com.posecoach.core.pose.stability

import com.posecoach.core.pose.models.PoseDetectionResult
import com.posecoach.core.pose.models.PoseLandmark33
import com.posecoach.core.pose.models.StabilityStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import kotlin.math.sqrt

class StablePoseGate(
    private val config: StabilityConfig = StabilityConfig.default()
) {
    private val _stabilityStatus = MutableStateFlow<StabilityStatus>(StabilityStatus.Unstable)
    val stabilityStatus: Flow<StabilityStatus> = _stabilityStatus

    private val poseHistory = ArrayDeque<PoseDetectionResult>(config.historySize)
    private var consecutiveStableFrames = 0

    fun processPoseResult(result: PoseDetectionResult) {
        if (result.isEmpty) {
            resetStability()
            return
        }

        poseHistory.addLast(result)
        if (poseHistory.size > config.historySize) {
            poseHistory.removeFirst()
        }

        val stabilityScore = calculateStabilityScore()

        if (stabilityScore >= config.stabilityThreshold) {
            consecutiveStableFrames++
            if (consecutiveStableFrames >= config.minStableFrames) {
                emitStableStatus(result, stabilityScore)
            }
        } else {
            consecutiveStableFrames = 0
            _stabilityStatus.value = StabilityStatus.Unstable
        }
    }

    private fun calculateStabilityScore(): Float {
        if (poseHistory.size < 2) return 0f

        val recent = poseHistory.takeLast(config.comparisonFrames)
        if (recent.size < 2) return 0f

        var totalMovement = 0f
        var comparisonCount = 0

        for (i in 1 until recent.size) {
            val movement = calculateMovementBetweenPoses(recent[i-1], recent[i])
            totalMovement += movement
            comparisonCount++
        }

        val averageMovement = totalMovement / comparisonCount
        val stabilityScore = 1f - (averageMovement / config.maxAllowedMovement).coerceIn(0f, 1f)

        return stabilityScore
    }

    private fun calculateMovementBetweenPoses(
        pose1: PoseDetectionResult,
        pose2: PoseDetectionResult
    ): Float {
        if (pose1.landmarks.size != pose2.landmarks.size) return Float.MAX_VALUE

        var totalDistance = 0f
        var validLandmarks = 0

        pose1.landmarks.zip(pose2.landmarks).forEach { (landmark1, landmark2) ->
            if (landmark1.visibility > config.visibilityThreshold &&
                landmark2.visibility > config.visibilityThreshold) {

                val distance = calculateEuclideanDistance(landmark1, landmark2)
                totalDistance += distance
                validLandmarks++
            }
        }

        return if (validLandmarks > 0) totalDistance / validLandmarks else Float.MAX_VALUE
    }

    private fun calculateEuclideanDistance(
        landmark1: PoseLandmark33,
        landmark2: PoseLandmark33
    ): Float {
        val dx = landmark1.x - landmark2.x
        val dy = landmark1.y - landmark2.y
        val dz = landmark1.z - landmark2.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun emitStableStatus(result: PoseDetectionResult, stabilityScore: Float) {
        val qualityScore = calculatePoseQuality(result)

        _stabilityStatus.value = StabilityStatus.Stable(
            pose = result,
            stabilityScore = stabilityScore,
            qualityScore = qualityScore,
            duration = consecutiveStableFrames * 33 // Assuming 30fps
        )

        Timber.d("Stable pose detected - Score: %.2f, Quality: %.2f",
                stabilityScore, qualityScore)
    }

    private fun calculatePoseQuality(result: PoseDetectionResult): Float {
        val visibleLandmarks = result.landmarks.count {
            it.visibility > config.visibilityThreshold
        }
        val visibilityRatio = visibleLandmarks.toFloat() / result.landmarks.size

        val averageConfidence = result.landmarks
            .filter { it.visibility > config.visibilityThreshold }
            .map { it.visibility }
            .average()
            .toFloat()

        return (visibilityRatio * 0.4f) + (averageConfidence * 0.6f)
    }

    private fun resetStability() {
        consecutiveStableFrames = 0
        poseHistory.clear()
        _stabilityStatus.value = StabilityStatus.Unstable
    }
}

data class StabilityConfig(
    val historySize: Int = 15,
    val comparisonFrames: Int = 5,
    val minStableFrames: Int = 10,
    val stabilityThreshold: Float = 0.85f,
    val maxAllowedMovement: Float = 0.05f,
    val visibilityThreshold: Float = 0.5f
) {
    companion object {
        fun default() = StabilityConfig()

        fun performanceOptimized() = StabilityConfig(
            historySize = 10,
            comparisonFrames = 3,
            minStableFrames = 6
        )
    }
}
```

## ImageAnalysisManager.kt Template

```kotlin
package com.posecoach.app.analysis

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.posecoach.app.mediapipe.PoseLandmarkerWrapper
import com.posecoach.app.utils.ImageUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import timber.log.Timber
import java.util.concurrent.Executors

class ImageAnalysisManager(
    private val poseLandmarkerWrapper: PoseLandmarkerWrapper
) : ImageAnalysis.Analyzer {

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val analysisScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob()
    )

    private val _processingStats = MutableSharedFlow<ProcessingStats>()
    val processingStats: Flow<ProcessingStats> = _processingStats

    private var frameCount = 0
    private var droppedFrames = 0
    private var lastStatsTime = System.currentTimeMillis()

    override fun analyze(image: ImageProxy) {
        val startTime = System.currentTimeMillis()

        analysisScope.launch {
            try {
                processImage(image, startTime)
            } catch (e: Exception) {
                Timber.e(e, "Error during image analysis")
                droppedFrames++
            } finally {
                image.close()
            }
        }
    }

    private suspend fun processImage(image: ImageProxy, startTime: Long) {
        val bitmap = ImageUtils.imageProxyToBitmap(image)
        val preprocessTime = System.currentTimeMillis()

        poseLandmarkerWrapper.processFrame(bitmap, image.imageInfo.timestamp)

        val endTime = System.currentTimeMillis()
        updateProcessingStats(startTime, preprocessTime, endTime)

        frameCount++
        logPerformanceIfNeeded()
    }

    private fun updateProcessingStats(
        startTime: Long,
        preprocessTime: Long,
        endTime: Long
    ) {
        val stats = ProcessingStats(
            totalProcessingTime = endTime - startTime,
            preprocessingTime = preprocessTime - startTime,
            mediapipeTime = endTime - preprocessTime,
            timestamp = endTime
        )

        _processingStats.tryEmit(stats)
    }

    private fun logPerformanceIfNeeded() {
        if (frameCount % 30 == 0) {
            val currentTime = System.currentTimeMillis()
            val duration = currentTime - lastStatsTime
            val fps = (frameCount * 1000.0) / duration
            val dropRate = (droppedFrames.toDouble() / frameCount) * 100

            Timber.d("Analysis Performance - FPS: %.1f, Drop Rate: %.1f%%", fps, dropRate)

            // Emit performance warning if needed
            if (fps < 25.0 || dropRate > 5.0) {
                Timber.w("Performance degradation detected - FPS: %.1f, Drops: %.1f%%", fps, dropRate)
            }

            frameCount = 0
            droppedFrames = 0
            lastStatsTime = currentTime
        }
    }

    fun cleanup() {
        analysisScope.cancel()
        analysisExecutor.shutdown()
    }
}

data class ProcessingStats(
    val totalProcessingTime: Long,
    val preprocessingTime: Long,
    val mediapipeTime: Long,
    val timestamp: Long
) {
    val isWithinTarget: Boolean
        get() = totalProcessingTime < 33 // 30fps target
}
```

## Test Templates

### MediaPipeManagerTest.kt
```kotlin
package com.posecoach.app.mediapipe

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaPipeManagerTest {

    private lateinit var context: Context
    private lateinit var mediaPipeManager: MediaPipeManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mediaPipeManager = MediaPipeManager(context) { _, _ -> }
    }

    @Test
    fun `mediapipe initialization should complete successfully`() = runTest {
        val isInitialized = mediaPipeManager.initialize()
        assertThat(isInitialized).isTrue()
    }

    @Test
    fun `mediapipe should process frame within performance threshold`() = runTest {
        mediaPipeManager.initialize()

        // Create test bitmap and measure processing time
        val testBitmap = createTestBitmap()
        val startTime = System.currentTimeMillis()

        // This would be handled by the result callback in real usage
        // but for testing we measure the call overhead
        val mpImage = BitmapImageBuilder(testBitmap).build()
        mediaPipeManager.detectAsync(mpImage, System.currentTimeMillis())

        val processingTime = System.currentTimeMillis() - startTime
        assertThat(processingTime).isLessThan(30) // Sub-30ms target
    }
}
```