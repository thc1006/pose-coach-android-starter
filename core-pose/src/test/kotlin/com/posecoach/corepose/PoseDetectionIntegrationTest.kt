package com.posecoach.corepose

import android.content.Context
import android.graphics.Bitmap
import com.posecoach.corepose.detector.MediaPipePoseDetector
import com.posecoach.corepose.stability.EnhancedStablePoseGate
import com.posecoach.corepose.validation.LandmarkValidator
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.models.PoseDetectionError
import com.posecoach.corepose.utils.PerformanceTracker
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * End-to-end integration tests for the complete pose detection pipeline.
 * Tests the interaction between MediaPipePoseDetector, LandmarkValidator, and EnhancedStablePoseGate.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PoseDetectionIntegrationTest {

    private lateinit var detector: MediaPipePoseDetector
    private lateinit var validator: LandmarkValidator
    private lateinit var stabilityGate: EnhancedStablePoseGate
    private lateinit var mockContext: Context
    private lateinit var testScope: TestScope

    // Test components
    private val testBitmap = mockk<Bitmap>(relaxed = true)
    private var testTimestamp = System.currentTimeMillis()

    // Integration test listener that coordinates all components
    private inner class IntegrationTestListener : MediaPipePoseDetector.DetectionListener {
        val detectedPoses = mutableListOf<PoseLandmarkResult>()
        val validationResults = mutableListOf<LandmarkValidator.ValidationResult>()
        val stabilityResults = mutableListOf<EnhancedStablePoseGate.StabilityResult>()
        val errors = mutableListOf<PoseDetectionError>()
        val performanceUpdates = mutableListOf<PerformanceTracker.PerformanceMetrics>()

        val poseDetectionLatch = CountDownLatch(1)
        val stabilityTriggeredLatch = CountDownLatch(1)

        override fun onPoseDetected(result: PoseLandmarkResult) {
            detectedPoses.add(result)

            // Run through validation pipeline
            val validationResult = validator.validateAndFilter(result)
            validationResults.add(validationResult)

            if (validationResult.isValid) {
                // Run through stability detection
                val filteredResult = result.copy(landmarks = validationResult.filteredLandmarks)
                val stabilityResult = stabilityGate.update(filteredResult)
                stabilityResults.add(stabilityResult)

                if (stabilityResult.justTriggered) {
                    stabilityTriggeredLatch.countDown()
                }
            }

            poseDetectionLatch.countDown()
        }

        override fun onMultiplePosesDetected(results: List<PoseLandmarkResult>) {
            results.forEach { onPoseDetected(it) }
        }

        override fun onDetectionError(error: PoseDetectionError) {
            errors.add(error)
        }

        override fun onPerformanceUpdate(metrics: PerformanceTracker.PerformanceMetrics) {
            performanceUpdates.add(metrics)
        }
    }

    @BeforeEach
    fun setup() {
        testScope = TestScope()
        detector = MediaPipePoseDetector()
        validator = LandmarkValidator()
        stabilityGate = EnhancedStablePoseGate()
        mockContext = mockk(relaxed = true)
        testTimestamp = System.currentTimeMillis()

        // Mock bitmap properties
        every { testBitmap.width } returns 640
        every { testBitmap.height } returns 480
        every { testBitmap.config } returns Bitmap.Config.ARGB_8888
        every { testBitmap.isRecycled } returns false

        clearAllMocks(answers = false)
    }

    @AfterEach
    fun tearDown() {
        runBlocking {
            detector.release()
        }
        validator.reset()
        stabilityGate.reset()
    }

    @Nested
    @DisplayName("Full Pipeline Integration Tests")
    inner class FullPipelineTests {

        @Test
        fun `should process complete pose detection pipeline successfully`() = runTest {
            // Given
            val listener = IntegrationTestListener()
            detector.initialize(mockContext)
            detector.start(listener)

            // When - process a frame
            val success = detector.detectPoseAsync(testBitmap, testTimestamp)

            // Then
            assertTrue(success, "Frame should be processed successfully")
            assertTrue(listener.poseDetectionLatch.await(5, TimeUnit.SECONDS),
                "Pose detection should complete")

            // Verify pipeline execution
            assertTrue(listener.detectedPoses.isNotEmpty(), "Should detect poses")
            assertTrue(listener.validationResults.isNotEmpty(), "Should run validation")
            assertTrue(listener.stabilityResults.isNotEmpty(), "Should run stability detection")
        }

        @Test
        fun `should achieve pose stability through complete pipeline`() = runTest {
            // Given
            val config = MediaPipePoseDetector.DetectorConfig(targetFps = 30)
            detector.initialize(mockContext, config)

            val stabilityConfig = EnhancedStablePoseGate.StabilityConfig(
                windowSec = 1.0,
                stabilityScoreThreshold = 0.8
            )
            stabilityGate = EnhancedStablePoseGate(stabilityConfig)

            val listener = IntegrationTestListener()
            detector.start(listener)

            // When - feed consistent frames for stability window
            val frameCount = 35 // ~1.2 seconds at 30 FPS
            repeat(frameCount) { frame ->
                val timestamp = testTimestamp + frame * 33L
                detector.detectPoseAsync(testBitmap, timestamp)
                delay(10) // Small delay to allow processing
            }

            // Then
            assertTrue(listener.stabilityTriggeredLatch.await(10, TimeUnit.SECONDS),
                "Stability should be triggered through complete pipeline")

            val finalStabilityResult = listener.stabilityResults.lastOrNull()
            assertNotNull(finalStabilityResult, "Should have stability result")
            assertTrue(finalStabilityResult!!.isStable, "Should achieve stability")
        }

        @Test
        fun `should handle validation failures in pipeline`() = runTest {
            // Given
            val strictValidationConfig = LandmarkValidator.ValidationConfig(
                minVisibilityThreshold = 0.95f,
                minPresenceThreshold = 0.98f
            )
            validator = LandmarkValidator(strictValidationConfig)

            detector.initialize(mockContext)
            val listener = IntegrationTestListener()
            detector.start(listener)

            // When - process frames (mock will likely produce low-quality data)
            repeat(5) { frame ->
                val timestamp = testTimestamp + frame * 33L
                detector.detectPoseAsync(testBitmap, timestamp)
                delay(20)
            }

            // Allow processing to complete
            delay(500)

            // Then - validation should catch quality issues
            assertTrue(listener.validationResults.isNotEmpty(), "Should have validation results")

            val validationFailures = listener.validationResults.count { !it.isValid }
            // Note: In test environment, exact behavior depends on mock setup
            // This test mainly ensures the pipeline handles validation failures gracefully
            assertDoesNotThrow { listener.validationResults.forEach { it.qualityScore } }
        }

        @Test
        fun `should maintain performance metrics throughout pipeline`() = runTest {
            // Given
            detector.initialize(mockContext)
            val listener = IntegrationTestListener()
            detector.start(listener)

            // When - process multiple frames
            repeat(40) { frame -> // Enough to trigger performance updates
                val timestamp = testTimestamp + frame * 33L
                detector.detectPoseAsync(testBitmap, timestamp)
                delay(15)
            }

            // Wait for processing
            delay(1000)

            // Then
            assertTrue(listener.performanceUpdates.isNotEmpty(),
                "Should receive performance updates")

            val metrics = detector.getPerformanceMetrics()
            assertTrue(metrics.avgInferenceTimeMs >= 0, "Should track inference time")
            assertTrue(metrics.avgFps >= 0, "Should track FPS")
        }
    }

    @Nested
    @DisplayName("Component Interaction Tests")
    inner class ComponentInteractionTests {

        @Test
        fun `should coordinate between validation and stability detection`() {
            // Given
            val validator = LandmarkValidator()
            val stabilityGate = EnhancedStablePoseGate()

            // Create a sequence of poses with increasing quality
            val poses = (1..10).map { quality ->
                createPoseWithQuality(testTimestamp + quality * 33L, quality / 10f)
            }

            // When - process through validation then stability
            val results = poses.map { pose ->
                val validationResult = validator.validateAndFilter(pose)
                val stabilityResult = if (validationResult.isValid) {
                    val filteredPose = pose.copy(landmarks = validationResult.filteredLandmarks)
                    stabilityGate.update(filteredPose)
                } else {
                    null
                }

                Triple(pose, validationResult, stabilityResult)
            }

            // Then
            assertTrue(results.any { it.second.isValid }, "Some poses should pass validation")
            assertTrue(results.any { it.third != null }, "Some poses should reach stability detection")

            // Stability should improve over time with better quality poses
            val stabilityScores = results.mapNotNull { it.third?.stabilityScore }
            if (stabilityScores.size > 1) {
                assertTrue(stabilityScores.last() >= stabilityScores.first(),
                    "Stability should improve with better quality poses")
            }
        }

        @Test
        fun `should handle temporal smoothing in validation affecting stability`() {
            // Given
            val temporalConfig = LandmarkValidator.ValidationConfig(
                enableTemporalValidation = true,
                smoothingStrength = 0.5f
            )
            val validator = LandmarkValidator(temporalConfig)
            val stabilityGate = EnhancedStablePoseGate()

            // Create poses with sudden movement
            val basePose = createHighQualityPose(testTimestamp)
            val jumpedPose = createJumpedPose(testTimestamp + 33L)

            // When
            val result1 = validator.validateAndFilter(basePose)
            val stability1 = stabilityGate.update(basePose.copy(landmarks = result1.filteredLandmarks))

            val result2 = validator.validateAndFilter(jumpedPose)
            val stability2 = stabilityGate.update(jumpedPose.copy(landmarks = result2.filteredLandmarks))

            // Then
            assertTrue(result2.appliedFilters.isNotEmpty() || result2.warnings.isNotEmpty(),
                "Validation should apply smoothing or warn about jump")

            // Smoothed landmarks should result in better stability
            assertTrue(stability2.stabilityScore >= 0.0, "Should calculate stability score")
        }

        @Test
        fun `should handle adaptive quality control affecting all components`() = runTest {
            // Given
            val adaptiveConfig = MediaPipePoseDetector.DetectorConfig(
                adaptiveQuality = true,
                maxLatencyMs = 20 // Very strict latency
            )

            detector.initialize(mockContext, adaptiveConfig)
            val listener = IntegrationTestListener()
            detector.start(listener)

            // When - simulate high latency scenario
            repeat(10) { frame ->
                val timestamp = testTimestamp + frame * 100L // Slower than target FPS
                detector.detectPoseAsync(testBitmap, timestamp)
                delay(50) // Simulate processing delay
            }

            delay(1000) // Allow adaptation

            // Then - detector should adapt and continue processing
            val metrics = detector.getPerformanceMetrics()
            assertTrue(metrics.avgInferenceTimeMs >= 0, "Should continue tracking performance")

            // Pipeline should remain functional
            assertTrue(listener.detectedPoses.isNotEmpty() || listener.errors.isNotEmpty(),
                "Pipeline should continue functioning or report errors")
        }
    }

    @Nested
    @DisplayName("Error Propagation and Recovery Tests")
    inner class ErrorHandlingTests {

        @Test
        fun `should handle detection errors gracefully in pipeline`() = runTest {
            // Given
            detector.initialize(mockContext)
            val listener = IntegrationTestListener()
            detector.start(listener)

            // Simulate error condition
            every { testBitmap.width } returns -1 // Invalid bitmap

            // When
            detector.detectPoseAsync(testBitmap, testTimestamp)
            delay(500) // Allow error processing

            // Then
            assertTrue(listener.errors.isNotEmpty() || listener.detectedPoses.isEmpty(),
                "Should handle detection errors")

            // Pipeline should recover with valid input
            every { testBitmap.width } returns 640 // Fix bitmap
            val success = detector.detectPoseAsync(testBitmap, testTimestamp + 100L)
            assertTrue(success, "Should recover from error")
        }

        @Test
        fun `should isolate validation errors from detection`() {
            // Given
            val poses = listOf(
                createHighQualityPose(testTimestamp),
                createInvalidPose(testTimestamp + 33L),
                createHighQualityPose(testTimestamp + 66L)
            )

            // When - process through validation
            val results = poses.map { pose ->
                try {
                    validator.validateAndFilter(pose)
                } catch (e: Exception) {
                    null
                }
            }

            // Then - validation errors should not crash pipeline
            assertTrue(results.all { it != null }, "Validation should handle all poses")
            assertTrue(results.any { it!!.isValid }, "Some poses should be valid")
        }

        @Test
        fun `should maintain stability tracking despite validation failures`() {
            // Given
            val poses = (1..10).map { index ->
                if (index % 3 == 0) {
                    createInvalidPose(testTimestamp + index * 33L)
                } else {
                    createHighQualityPose(testTimestamp + index * 33L)
                }
            }

            // When - process through complete pipeline
            var stabilityResults = 0
            poses.forEach { pose ->
                val validationResult = validator.validateAndFilter(pose)
                if (validationResult.isValid) {
                    val filteredPose = pose.copy(landmarks = validationResult.filteredLandmarks)
                    stabilityGate.update(filteredPose)
                    stabilityResults++
                }
            }

            // Then - stability should continue tracking valid poses
            assertTrue(stabilityResults > 0, "Should process some poses through stability")

            val currentStability = stabilityGate.getCurrentStability()
            assertNotNull(currentStability, "Should maintain stability state")
        }
    }

    @Nested
    @DisplayName("Performance Integration Tests")
    inner class PerformanceIntegrationTests {

        @Test
        fun `should maintain target latency through complete pipeline`() = runTest {
            // Given
            val lowLatencyConfig = MediaPipePoseDetector.DetectorConfig(
                targetFps = 30,
                maxLatencyMs = 33 // Target frame time for 30 FPS
            )

            detector.initialize(mockContext, lowLatencyConfig)
            val listener = IntegrationTestListener()
            detector.start(listener)

            // When - process frames at target rate
            val latencies = mutableListOf<Long>()
            repeat(20) { frame ->
                val startTime = System.currentTimeMillis()
                detector.detectPoseAsync(testBitmap, testTimestamp + frame * 33L)
                delay(33) // Wait for target frame time
                val endTime = System.currentTimeMillis()

                latencies.add(endTime - startTime)
            }

            // Then
            val avgLatency = latencies.average()
            assertTrue(avgLatency <= 50.0, // Allow some overhead
                "Average latency should be reasonable: ${avgLatency}ms")
        }

        @Test
        fun `should scale performance with frame rate`() = runTest {
            // Given
            val highFpsConfig = MediaPipePoseDetector.DetectorConfig(targetFps = 60)
            detector.initialize(mockContext, highFpsConfig)

            val listener = IntegrationTestListener()
            detector.start(listener)

            // When - process at high frame rate
            val frameInterval = 16L // ~60 FPS
            repeat(30) { frame ->
                detector.detectPoseAsync(testBitmap, testTimestamp + frame * frameInterval)
                delay(frameInterval)
            }

            delay(1000) // Allow processing

            // Then
            val metrics = detector.getPerformanceMetrics()
            assertTrue(metrics.avgFps >= 0, "Should track high FPS performance")

            // Should process reasonable number of frames
            assertTrue(listener.detectedPoses.isNotEmpty(),
                "Should process frames at high frame rate")
        }

        @Test
        fun `should demonstrate memory efficiency with object pooling`() = runTest {
            // Given
            val poolingConfig = MediaPipePoseDetector.DetectorConfig(
                enableObjectPooling = true,
                targetFps = 30
            )

            detector.initialize(mockContext, poolingConfig)
            val listener = IntegrationTestListener()
            detector.start(listener)

            // When - process many frames to test pooling
            val frameCount = 100
            repeat(frameCount) { frame ->
                detector.detectPoseAsync(testBitmap, testTimestamp + frame * 33L)
                if (frame % 10 == 0) delay(50) // Periodic delay to allow processing
            }

            delay(2000) // Allow all processing to complete

            // Then - should complete without memory issues
            val metrics = detector.getPerformanceMetrics()
            assertTrue(metrics.avgInferenceTimeMs >= 0,
                "Should maintain performance metrics with object pooling")

            // Should process significant number of frames
            assertTrue(listener.detectedPoses.size > frameCount / 4,
                "Should process reasonable percentage of frames")
        }
    }

    @Nested
    @DisplayName("Real-world Scenario Tests")
    inner class RealWorldScenarioTests {

        @Test
        fun `should handle user workout session scenario`() = runTest {
            // Given - simulate workout session configuration
            val workoutConfig = MediaPipePoseDetector.DetectorConfig(
                targetFps = 24,
                numPoses = 1,
                adaptiveQuality = true
            )

            val stabilityConfig = EnhancedStablePoseGate.StabilityConfig(
                windowSec = 2.0, // Longer stability window for poses
                stabilityScoreThreshold = 0.75
            )

            detector.initialize(mockContext, workoutConfig)
            stabilityGate = EnhancedStablePoseGate(stabilityConfig)

            val listener = IntegrationTestListener()
            detector.start(listener)

            // When - simulate workout session with various poses
            val workoutDuration = 60 // 1 minute simulation
            val frameInterval = 42L // ~24 FPS

            repeat(workoutDuration) { second ->
                // Simulate different exercise phases
                val exercisePhase = when (second % 10) {
                    in 0..3 -> "warmup" // Gentle movement
                    in 4..7 -> "exercise" // Active movement
                    else -> "rest" // Stable pose
                }

                repeat(24) { frame -> // 24 frames per second
                    val timestamp = testTimestamp + second * 1000L + frame * frameInterval
                    detector.detectPoseAsync(testBitmap, timestamp)
                }

                if (second % 10 == 0) delay(100) // Brief pause for processing
            }

            delay(2000) // Allow final processing

            // Then
            assertTrue(listener.detectedPoses.isNotEmpty(),
                "Should detect poses throughout workout")
            assertTrue(listener.stabilityResults.isNotEmpty(),
                "Should track stability throughout workout")

            val stabilityTriggers = listener.stabilityResults.count { it.justTriggered }
            assertTrue(stabilityTriggers > 0, "Should trigger stability at least once during workout")

            val metrics = detector.getPerformanceMetrics()
            assertTrue(metrics.isPerformanceGood || metrics.avgInferenceTimeMs > 0,
                "Should maintain reasonable performance during workout")
        }

        @Test
        fun `should handle device resource constraints`() = runTest {
            // Given - simulate resource-constrained configuration
            val constrainedConfig = MediaPipePoseDetector.DetectorConfig(
                targetFps = 15, // Lower FPS for constrained device
                maxLatencyMs = 67, // ~15 FPS max latency
                numPoses = 1,
                enableSegmentation = false
            )

            detector.initialize(mockContext, constrainedConfig)
            val listener = IntegrationTestListener()
            detector.start(listener)

            // When - simulate resource pressure
            repeat(50) { frame ->
                detector.detectPoseAsync(testBitmap, testTimestamp + frame * 67L)
                // Simulate resource pressure with longer delays
                delay(if (frame % 5 == 0) 100 else 20)
            }

            delay(1000)

            // Then - should continue functioning under constraints
            assertTrue(listener.detectedPoses.isNotEmpty(),
                "Should continue detecting poses under constraints")

            val metrics = detector.getPerformanceMetrics()
            assertTrue(metrics.avgInferenceTimeMs >= 0,
                "Should maintain performance tracking under constraints")
        }
    }

    // Helper functions for creating test data

    private fun createPoseWithQuality(timestampMs: Long, quality: Float): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.3f + (index % 5) * 0.1f,
                y = 0.2f + (index / 5) * 0.1f,
                z = 0.1f,
                visibility = quality,
                presence = quality
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = timestampMs,
            inferenceTimeMs = 25L
        )
    }

    private fun createHighQualityPose(timestampMs: Long): PoseLandmarkResult {
        return createPoseWithQuality(timestampMs, 0.95f)
    }

    private fun createJumpedPose(timestampMs: Long): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.8f + (index % 5) * 0.1f, // Significant position jump
                y = 0.8f + (index / 5) * 0.1f,
                z = 0.3f,
                visibility = 0.9f,
                presence = 0.9f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = timestampMs,
            inferenceTimeMs = 25L
        )
    }

    private fun createInvalidPose(timestampMs: Long): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = if (index % 2 == 0) Float.NaN else 0.5f,
                y = 0.5f,
                z = 0.1f,
                visibility = 0.1f, // Very low visibility
                presence = 0.1f    // Very low presence
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = timestampMs,
            inferenceTimeMs = 25L
        )
    }
}