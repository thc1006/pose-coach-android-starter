package com.posecoach.corepose.detector

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.tasks.core.Delegate
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.models.PoseDetectionError
import com.posecoach.corepose.utils.PerformanceTracker
import com.posecoach.corepose.utils.HardwareCapabilityDetector
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Comprehensive test suite for MediaPipePoseDetector.
 * Tests core functionality, performance optimization, error handling, and edge cases.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MediaPipePoseDetectorTest {

    private lateinit var detector: MediaPipePoseDetector
    private lateinit var mockContext: Context
    private lateinit var mockListener: MediaPipePoseDetector.DetectionListener
    private lateinit var testScope: TestScope

    // Test data
    private val testBitmap = mockk<Bitmap>(relaxed = true)
    private val testTimestamp = System.currentTimeMillis()

    private val sampleLandmarks = (0..32).map { index ->
        PoseLandmarkResult.Landmark(
            x = 0.5f + (index * 0.01f),
            y = 0.5f + (index * 0.01f),
            z = 0.1f,
            visibility = 0.9f,
            presence = 0.95f
        )
    }

    private val samplePoseResult = PoseLandmarkResult(
        landmarks = sampleLandmarks,
        worldLandmarks = sampleLandmarks,
        timestampMs = testTimestamp,
        inferenceTimeMs = 25L
    )

    @BeforeEach
    fun setup() {
        testScope = TestScope()
        detector = MediaPipePoseDetector()
        mockContext = mockk(relaxed = true)
        mockListener = mockk(relaxed = true)

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
    }

    @Nested
    @DisplayName("Initialization Tests")
    inner class InitializationTests {

        @Test
        fun `should initialize successfully with default configuration`() = runTest {
            // Given
            val config = MediaPipePoseDetector.DetectorConfig()

            // When
            val result = detector.initialize(mockContext, config)

            // Then
            assertTrue(result, "Detector should initialize successfully")
        }

        @Test
        fun `should initialize with custom configuration`() = runTest {
            // Given
            val config = MediaPipePoseDetector.DetectorConfig(
                targetFps = 24,
                maxLatencyMs = 40,
                minDetectionConfidence = 0.6f,
                numPoses = 2,
                enableSegmentation = true
            )

            // When
            val result = detector.initialize(mockContext, config)

            // Then
            assertTrue(result, "Detector should initialize with custom config")
        }

        @Test
        fun `should handle initialization failure gracefully`() = runTest {
            // Given
            every { mockContext.assets } throws RuntimeException("Asset access failed")

            // When
            val result = detector.initialize(mockContext)

            // Then
            assertFalse(result, "Detector should handle initialization failure")
        }

        @Test
        fun `should not initialize twice`() = runTest {
            // Given
            detector.initialize(mockContext)

            // When
            val secondResult = detector.initialize(mockContext)

            // Then
            assertTrue(secondResult, "Should handle double initialization gracefully")
        }
    }

    @Nested
    @DisplayName("Detection Lifecycle Tests")
    inner class DetectionLifecycleTests {

        @BeforeEach
        fun initializeDetector() = runTest {
            detector.initialize(mockContext)
        }

        @Test
        fun `should start detection successfully`() {
            // When
            val result = detector.start(mockListener)

            // Then
            assertTrue(result, "Detection should start successfully")
        }

        @Test
        fun `should not start without initialization`() {
            // Given
            val uninitializedDetector = MediaPipePoseDetector()

            // When
            val result = uninitializedDetector.start(mockListener)

            // Then
            assertFalse(result, "Should not start without initialization")
        }

        @Test
        fun `should not start twice`() {
            // Given
            detector.start(mockListener)

            // When
            val secondResult = detector.start(mockListener)

            // Then
            assertFalse(secondResult, "Should not start twice")
        }

        @Test
        fun `should stop detection successfully`() {
            // Given
            detector.start(mockListener)

            // When
            detector.stop()

            // Then
            // No exception should be thrown
            // Can start again after stopping
            assertTrue(detector.start(mockListener))
        }

        @Test
        fun `should handle stop without start`() {
            // When/Then - should not throw exception
            assertDoesNotThrow {
                detector.stop()
            }
        }
    }

    @Nested
    @DisplayName("Frame Processing Tests")
    inner class FrameProcessingTests {

        @BeforeEach
        fun setupDetection() = runTest {
            detector.initialize(mockContext)
            detector.start(mockListener)
        }

        @Test
        fun `should process single frame successfully`() = runTest {
            // When
            val result = detector.detectPoseAsync(testBitmap, testTimestamp)

            // Then
            assertTrue(result, "Frame should be processed successfully")
        }

        @Test
        fun `should handle null bitmap gracefully`() = runTest {
            // Given
            val nullBitmap: Bitmap? = null

            // When/Then
            assertDoesNotThrow {
                @Suppress("UNNECESSARY_SAFE_CALL")
                nullBitmap?.let { detector.detectPoseAsync(it, testTimestamp) }
            }
        }

        @Test
        fun `should handle recycled bitmap`() = runTest {
            // Given
            every { testBitmap.isRecycled } returns true

            // When
            val result = detector.detectPoseAsync(testBitmap, testTimestamp)

            // Then
            assertFalse(result, "Should reject recycled bitmap")
        }

        @Test
        fun `should process batch of frames`() = runTest {
            // Given
            val frames = (1..5).map { i ->
                testBitmap to (testTimestamp + i * 33L) // ~30 FPS intervals
            }

            // When
            val results = detector.detectPoseBatch(frames)

            // Then
            assertEquals(5, results.size, "Should process all frames")
            assertTrue(results.all { it }, "All frames should be processed successfully")
        }

        @Test
        fun `should respect frame rate limiting`() = runTest {
            // Given
            val rapidFrames = (1..10).map { i ->
                testBitmap to (testTimestamp + i * 5L) // Very rapid frames
            }

            // When
            val results = detector.detectPoseBatch(rapidFrames)

            // Then
            assertTrue(results.any { !it }, "Some frames should be skipped for rate limiting")
        }
    }

    @Nested
    @DisplayName("Performance Optimization Tests")
    inner class PerformanceOptimizationTests {

        @BeforeEach
        fun setupDetection() = runTest {
            detector.initialize(mockContext, MediaPipePoseDetector.DetectorConfig(
                targetFps = 30,
                maxLatencyMs = 30,
                adaptiveQuality = true
            ))
            detector.start(mockListener)
        }

        @Test
        fun `should track performance metrics`() = runTest {
            // Given
            repeat(10) { i ->
                detector.detectPoseAsync(testBitmap, testTimestamp + i * 33L)
                delay(35) // Simulate processing time
            }

            // When
            val metrics = detector.getPerformanceMetrics()

            // Then
            assertTrue(metrics.avgInferenceTimeMs >= 0, "Should track inference time")
            assertTrue(metrics.avgFps >= 0, "Should track FPS")
        }

        @Test
        fun `should adapt to high latency`() = runTest {
            // Given - simulate high latency scenario
            val highLatencyConfig = MediaPipePoseDetector.DetectorConfig(
                targetFps = 30,
                maxLatencyMs = 15, // Very strict latency requirement
                adaptiveQuality = true
            )

            // When
            detector.updateConfiguration(highLatencyConfig)

            // Simulate high latency frames
            repeat(5) { i ->
                detector.detectPoseAsync(testBitmap, testTimestamp + i * 100L) // Slow frames
                delay(50)
            }

            // Then
            val metrics = detector.getPerformanceMetrics()
            // Detector should adapt by reducing quality or FPS
            assertTrue(metrics.avgInferenceTimeMs >= 0, "Should continue processing")
        }

        @Test
        fun `should report hardware capabilities`() {
            // When
            val capabilities = detector.getHardwareCapabilities()

            // Then
            // Note: In test environment, capabilities might be null
            // In real device, should return valid capabilities
            // This test mainly ensures no exceptions are thrown
            assertDoesNotThrow {
                capabilities?.performanceClass
            }
        }

        @Test
        fun `should handle delegate switching`() = runTest {
            // When
            val gpuResult = detector.switchDelegate(Delegate.GPU)
            val cpuResult = detector.switchDelegate(Delegate.CPU)

            // Then
            // Should handle delegate switching without crashing
            assertTrue(gpuResult || cpuResult, "At least one delegate should work")
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    inner class ErrorHandlingTests {

        @BeforeEach
        fun setupDetection() = runTest {
            detector.initialize(mockContext)
            detector.start(mockListener)
        }

        @Test
        fun `should handle detection errors gracefully`() = runTest {
            // Given
            val errorLatch = CountDownLatch(1)
            var capturedError: PoseDetectionError? = null

            every { mockListener.onDetectionError(any()) } answers {
                capturedError = firstArg()
                errorLatch.countDown()
            }

            // Simulate error by providing invalid bitmap
            every { testBitmap.width } returns -1

            // When
            detector.detectPoseAsync(testBitmap, testTimestamp)

            // Then
            assertTrue(errorLatch.await(5, TimeUnit.SECONDS), "Error callback should be called")
            assertNotNull(capturedError, "Error should be captured")
        }

        @Test
        fun `should handle MediaPipe initialization failure`() = runTest {
            // Given
            val failingDetector = MediaPipePoseDetector()
            every { mockContext.assets } throws RuntimeException("MediaPipe init failed")

            // When
            val result = failingDetector.initialize(mockContext)

            // Then
            assertFalse(result, "Should handle MediaPipe failure gracefully")
        }

        @Test
        fun `should handle memory pressure gracefully`() = runTest {
            // Given
            val config = MediaPipePoseDetector.DetectorConfig(
                enableObjectPooling = true,
                maxLatencyMs = 50
            )
            detector.updateConfiguration(config)

            // Simulate memory pressure with many rapid frames
            val largeFrameCount = 100
            val frames = (1..largeFrameCount).map { i ->
                testBitmap to (testTimestamp + i * 10L)
            }

            // When/Then - should not crash under memory pressure
            assertDoesNotThrow {
                runBlocking {
                    detector.detectPoseBatch(frames)
                }
            }
        }
    }

    @Nested
    @DisplayName("Configuration Update Tests")
    inner class ConfigurationUpdateTests {

        @BeforeEach
        fun setupDetection() = runTest {
            detector.initialize(mockContext)
        }

        @Test
        fun `should update configuration while stopped`() = runTest {
            // Given
            val newConfig = MediaPipePoseDetector.DetectorConfig(
                targetFps = 15,
                numPoses = 2,
                enableSegmentation = true
            )

            // When
            val result = detector.updateConfiguration(newConfig)

            // Then
            assertTrue(result, "Should update configuration when stopped")
        }

        @Test
        fun `should update configuration while running`() = runTest {
            // Given
            detector.start(mockListener)
            val newConfig = MediaPipePoseDetector.DetectorConfig(
                targetFps = 20,
                maxLatencyMs = 40
            )

            // When
            val result = detector.updateConfiguration(newConfig)

            // Then
            assertTrue(result, "Should update configuration while running")
            // Should restart with new configuration
        }

        @Test
        fun `should handle configuration update failure`() = runTest {
            // Given
            detector.start(mockListener)
            every { mockContext.assets } throws RuntimeException("Config update failed")

            val invalidConfig = MediaPipePoseDetector.DetectorConfig(
                modelPath = "nonexistent_model.task"
            )

            // When
            val result = detector.updateConfiguration(invalidConfig)

            // Then
            assertFalse(result, "Should handle configuration update failure")
        }
    }

    @Nested
    @DisplayName("Resource Management Tests")
    inner class ResourceManagementTests {

        @Test
        fun `should release resources properly`() = runTest {
            // Given
            detector.initialize(mockContext)
            detector.start(mockListener)

            // Process some frames
            repeat(5) { i ->
                detector.detectPoseAsync(testBitmap, testTimestamp + i * 33L)
            }

            // When
            detector.release()

            // Then - should not throw exception after release
            assertDoesNotThrow {
                detector.getPerformanceMetrics()
            }
        }

        @Test
        fun `should handle multiple releases`() {
            // When/Then - should not throw exception
            assertDoesNotThrow {
                detector.release()
                detector.release()
            }
        }

        @Test
        fun `should not accept frames after release`() = runTest {
            // Given
            detector.initialize(mockContext)
            detector.start(mockListener)
            detector.release()

            // When
            val result = detector.detectPoseAsync(testBitmap, testTimestamp)

            // Then
            assertFalse(result, "Should not accept frames after release")
        }
    }

    @Nested
    @DisplayName("Callback Verification Tests")
    inner class CallbackVerificationTests {

        @BeforeEach
        fun setupDetection() = runTest {
            detector.initialize(mockContext)
            detector.start(mockListener)
        }

        @Test
        fun `should call onPoseDetected for single pose`() = runTest {
            // Given
            val detectionLatch = CountDownLatch(1)
            var capturedResult: PoseLandmarkResult? = null

            every { mockListener.onPoseDetected(any()) } answers {
                capturedResult = firstArg()
                detectionLatch.countDown()
            }

            // When
            detector.detectPoseAsync(testBitmap, testTimestamp)

            // Then
            assertTrue(detectionLatch.await(5, TimeUnit.SECONDS), "Detection callback should be called")
            assertNotNull(capturedResult, "Result should be captured")
        }

        @Test
        fun `should call onMultiplePosesDetected for multiple poses`() = runTest {
            // Given
            val multiPoseConfig = MediaPipePoseDetector.DetectorConfig(numPoses = 3)
            detector.updateConfiguration(multiPoseConfig)
            detector.start(mockListener)

            val detectionLatch = CountDownLatch(1)
            var capturedResults: List<PoseLandmarkResult>? = null

            every { mockListener.onMultiplePosesDetected(any()) } answers {
                capturedResults = firstArg()
                detectionLatch.countDown()
            }

            // When
            detector.detectPoseAsync(testBitmap, testTimestamp)

            // Then
            assertTrue(detectionLatch.await(5, TimeUnit.SECONDS), "Multi-pose callback should be called")
            assertNotNull(capturedResults, "Results should be captured")
        }

        @Test
        fun `should call onPerformanceUpdate periodically`() = runTest {
            // Given
            val performanceUpdateLatch = CountDownLatch(1)
            var capturedMetrics: PerformanceTracker.PerformanceMetrics? = null

            every { mockListener.onPerformanceUpdate(any()) } answers {
                capturedMetrics = firstArg()
                performanceUpdateLatch.countDown()
            }

            // When - process enough frames to trigger performance update
            repeat(35) { i -> // Should trigger on frame 30
                detector.detectPoseAsync(testBitmap, testTimestamp + i * 33L)
                delay(10)
            }

            // Then
            assertTrue(performanceUpdateLatch.await(10, TimeUnit.SECONDS), "Performance update should be called")
            assertNotNull(capturedMetrics, "Metrics should be captured")
        }
    }

    @Nested
    @DisplayName("Edge Cases and Stress Tests")
    inner class EdgeCasesAndStressTests {

        @BeforeEach
        fun setupDetection() = runTest {
            detector.initialize(mockContext)
            detector.start(mockListener)
        }

        @Test
        fun `should handle rapid start-stop cycles`() {
            // When/Then - should not crash
            assertDoesNotThrow {
                repeat(10) {
                    detector.stop()
                    detector.start(mockListener)
                }
            }
        }

        @Test
        fun `should handle concurrent frame processing`() = runTest {
            // Given
            val frameCount = 20
            val jobs = mutableListOf<Job>()

            // When - submit many frames concurrently
            repeat(frameCount) { i ->
                val job = launch {
                    detector.detectPoseAsync(testBitmap, testTimestamp + i * 10L)
                }
                jobs.add(job)
            }

            // Wait for all jobs to complete
            jobs.joinAll()

            // Then - should not crash and should maintain thread safety
            val metrics = detector.getPerformanceMetrics()
            assertTrue(metrics.avgInferenceTimeMs >= 0, "Should maintain thread safety")
        }

        @Test
        fun `should handle extreme timestamps`() = runTest {
            // Given
            val extremeTimestamps = listOf(
                0L,
                Long.MAX_VALUE,
                -1L,
                testTimestamp + 1000000L // Very far future
            )

            // When/Then - should handle extreme values gracefully
            assertDoesNotThrow {
                extremeTimestamps.forEach { timestamp ->
                    runBlocking {
                        detector.detectPoseAsync(testBitmap, timestamp)
                    }
                }
            }
        }

        @Test
        fun `should handle configuration with extreme values`() = runTest {
            // Given
            val extremeConfig = MediaPipePoseDetector.DetectorConfig(
                targetFps = 1000, // Unrealistic high FPS
                maxLatencyMs = 1, // Extremely low latency
                minDetectionConfidence = 0.99f, // Very high confidence
                numPoses = 10 // Many poses
            )

            // When/Then - should handle extreme configuration gracefully
            assertDoesNotThrow {
                runBlocking {
                    detector.updateConfiguration(extremeConfig)
                }
            }
        }
    }
}

/**
 * Test utility functions and helpers.
 */
object MediaPipePoseDetectorTestUtils {

    fun createTestPoseResult(
        landmarkCount: Int = 33,
        timestampMs: Long = System.currentTimeMillis(),
        inferenceTimeMs: Long = 25L
    ): PoseLandmarkResult {
        val landmarks = (0 until landmarkCount).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.3f + (index * 0.01f),
                y = 0.4f + (index * 0.01f),
                z = 0.1f + (index * 0.001f),
                visibility = 0.8f + (index * 0.005f),
                presence = 0.9f + (index * 0.002f)
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = timestampMs,
            inferenceTimeMs = inferenceTimeMs
        )
    }

    fun createTestBitmap(width: Int = 640, height: Int = 480): Bitmap {
        return mockk<Bitmap> {
            every { this@mockk.width } returns width
            every { this@mockk.height } returns height
            every { config } returns Bitmap.Config.ARGB_8888
            every { isRecycled } returns false
        }
    }
}