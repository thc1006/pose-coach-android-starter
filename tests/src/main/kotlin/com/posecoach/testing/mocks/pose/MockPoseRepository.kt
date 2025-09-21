package com.posecoach.testing.mocks.pose

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.repository.PoseDetectionListener
import com.posecoach.corepose.repository.PoseRepository
import com.posecoach.testing.mocks.MockService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mock implementation of PoseRepository for comprehensive testing
 * Supports various test scenarios including:
 * - Successful pose detection
 * - Error conditions
 * - Performance testing scenarios
 * - Edge cases and boundary conditions
 */
class MockPoseRepository : PoseRepository, MockService {

    private val interactionCount = AtomicInteger(0)
    private val _isInitialized = MutableStateFlow(false)
    private val _currentPose = MutableStateFlow<PoseLandmarkResult?>(null)

    // Test configuration
    var shouldSimulateInitializationFailure = false
    var shouldSimulateDetectionErrors = false
    var detectionLatencyMs = 0L
    var shouldReturnNullPoses = false
    var customPoseSequence: List<PoseLandmarkResult>? = null
    private var sequenceIndex = 0

    override val isInitialized: StateFlow<Boolean> = _isInitialized
    override val currentPose: StateFlow<PoseLandmarkResult?> = _currentPose

    override suspend fun initialize(): Result<Unit> {
        interactionCount.incrementAndGet()

        return if (shouldSimulateInitializationFailure) {
            Timber.w("MockPoseRepository: Simulating initialization failure")
            Result.failure(Exception("Mock initialization failure"))
        } else {
            simulateDelay(100) // Simulate initialization time
            _isInitialized.value = true
            Timber.d("MockPoseRepository: Initialized successfully")
            Result.success(Unit)
        }
    }

    override suspend fun startDetection(listener: PoseDetectionListener) {
        interactionCount.incrementAndGet()

        if (!_isInitialized.value) {
            throw IllegalStateException("Repository not initialized")
        }

        Timber.d("MockPoseRepository: Starting pose detection")

        if (shouldSimulateDetectionErrors) {
            listener.onError(Exception("Mock detection error"))
            return
        }

        // Simulate continuous pose detection
        startPoseDetectionSimulation(listener)
    }

    override suspend fun stopDetection() {
        interactionCount.incrementAndGet()
        Timber.d("MockPoseRepository: Stopping pose detection")
        _currentPose.value = null
    }

    override suspend fun release() {
        interactionCount.incrementAndGet()
        _isInitialized.value = false
        _currentPose.value = null
        Timber.d("MockPoseRepository: Released")
    }

    private suspend fun startPoseDetectionSimulation(listener: PoseDetectionListener) {
        // Simulate detection latency
        if (detectionLatencyMs > 0) {
            simulateDelay(detectionLatencyMs)
        }

        when {
            shouldReturnNullPoses -> {
                listener.onPoseDetected(null)
            }
            customPoseSequence != null -> {
                val sequence = customPoseSequence!!
                if (sequence.isNotEmpty()) {
                    val pose = sequence[sequenceIndex % sequence.size]
                    sequenceIndex++
                    _currentPose.value = pose
                    listener.onPoseDetected(pose)
                }
            }
            else -> {
                val mockPose = generateMockPose()
                _currentPose.value = mockPose
                listener.onPoseDetected(mockPose)
            }
        }
    }

    private fun generateMockPose(): PoseLandmarkResult {
        return PoseLandmarkResult(
            landmarks = generateMockLandmarks(),
            confidence = 0.95f + (Math.random() * 0.05f).toFloat(),
            timestamp = System.currentTimeMillis(),
            imageWidth = 1920,
            imageHeight = 1080
        )
    }

    private fun generateMockLandmarks(): List<PoseLandmarkResult.Landmark> {
        // Generate 33 MediaPipe pose landmarks with realistic coordinates
        return (0..32).map { index ->
            val baseX = 0.5f + (Math.random() * 0.4f - 0.2f).toFloat()
            val baseY = when {
                index <= 10 -> 0.2f + (Math.random() * 0.3f).toFloat() // Head/neck area
                index <= 22 -> 0.4f + (Math.random() * 0.3f).toFloat() // Torso area
                else -> 0.7f + (Math.random() * 0.3f).toFloat() // Legs area
            }

            PoseLandmarkResult.Landmark(
                x = baseX,
                y = baseY,
                z = (Math.random() * 0.2f - 0.1f).toFloat(),
                visibility = 0.8f + (Math.random() * 0.2f).toFloat()
            )
        }
    }

    // Test utility methods

    /**
     * Configure the mock to simulate specific test scenarios
     */
    fun configureTestScenario(scenario: TestScenario) {
        when (scenario) {
            TestScenario.INITIALIZATION_FAILURE -> {
                shouldSimulateInitializationFailure = true
            }
            TestScenario.DETECTION_ERRORS -> {
                shouldSimulateDetectionErrors = true
            }
            TestScenario.HIGH_LATENCY -> {
                detectionLatencyMs = 500L
            }
            TestScenario.NULL_POSES -> {
                shouldReturnNullPoses = true
            }
            TestScenario.PERFECT_POSES -> {
                customPoseSequence = generatePerfectPoseSequence()
            }
            TestScenario.POOR_QUALITY_POSES -> {
                customPoseSequence = generatePoorQualityPoseSequence()
            }
            TestScenario.NORMAL -> {
                reset()
            }
        }
    }

    /**
     * Set a custom sequence of poses for testing specific scenarios
     */
    fun setCustomPoseSequence(poses: List<PoseLandmarkResult>) {
        customPoseSequence = poses
        sequenceIndex = 0
    }

    /**
     * Simulate network latency or processing delays
     */
    fun setDetectionLatency(latencyMs: Long) {
        detectionLatencyMs = latencyMs
    }

    private fun generatePerfectPoseSequence(): List<PoseLandmarkResult> {
        return listOf(
            generateMockPose().copy(confidence = 1.0f),
            generateMockPose().copy(confidence = 0.99f),
            generateMockPose().copy(confidence = 0.98f)
        )
    }

    private fun generatePoorQualityPoseSequence(): List<PoseLandmarkResult> {
        return listOf(
            generateMockPose().copy(confidence = 0.3f),
            generateMockPose().copy(confidence = 0.2f),
            generateMockPose().copy(confidence = 0.1f)
        )
    }

    private suspend fun simulateDelay(ms: Long) {
        kotlinx.coroutines.delay(ms)
    }

    // MockService implementation

    override fun reset() {
        shouldSimulateInitializationFailure = false
        shouldSimulateDetectionErrors = false
        detectionLatencyMs = 0L
        shouldReturnNullPoses = false
        customPoseSequence = null
        sequenceIndex = 0
        interactionCount.set(0)
        _isInitialized.value = false
        _currentPose.value = null
    }

    override fun getInteractionCount(): Int = interactionCount.get()

    enum class TestScenario {
        NORMAL,
        INITIALIZATION_FAILURE,
        DETECTION_ERRORS,
        HIGH_LATENCY,
        NULL_POSES,
        PERFECT_POSES,
        POOR_QUALITY_POSES
    }
}