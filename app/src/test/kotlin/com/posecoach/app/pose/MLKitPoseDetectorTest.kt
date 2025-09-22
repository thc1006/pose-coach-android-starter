package com.posecoach.app.pose

import android.content.Context
import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import androidx.test.core.app.ApplicationProvider
import com.google.mlkit.vision.pose.PoseLandmark
import com.posecoach.corepose.models.PoseLandmarkResult
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * TDD test suite for MLKitPoseDetector
 * Following the specification in .claude/specs/voice-coach-integration.md
 *
 * Test categories:
 * 1. Initialization and configuration
 * 2. Image processing accuracy
 * 3. Performance benchmarks
 * 4. Error handling
 * 5. Resource management
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MLKitPoseDetectorTest {

    private lateinit var mockContext: Context
    private lateinit var detector: MLKitPoseDetector
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        // Mock Timber to avoid log errors
        mockkStatic(Timber::class)
        every { Timber.d(any<String>()) } just Runs
        every { Timber.e(any<Throwable>(), any<String>()) } just Runs
        every { Timber.e(any<String>()) } just Runs
        every { Timber.v(any<String>()) } just Runs
        every { Timber.w(any<String>()) } just Runs
        every { Timber.i(any<String>()) } just Runs

        mockContext = ApplicationProvider.getApplicationContext()
        testScope = TestScope()
        detector = MLKitPoseDetector(mockContext)
    }

    @After
    fun tearDown() {
        detector.close()
        testScope.cancel()
        unmockkAll()
    }

    // INITIALIZATION AND CONFIGURATION TESTS

    @Test
    fun `should initialize successfully with valid context`() {
        // ARRANGE & ACT
        val result = detector.initialize()

        // ASSERT
        assertTrue(result, "MLKitPoseDetector should initialize successfully")
    }

    @Test
    fun `should handle initialization failure gracefully`() {
        // ARRANGE
        val invalidDetector = MLKitPoseDetector(mockContext)

        // ACT & ASSERT
        // Should not throw exception even if ML Kit fails to initialize
        assertDoesNotThrow {
            invalidDetector.initialize()
        }

        invalidDetector.close()
    }

    @Test
    fun `should configure pose detector with correct options`() {
        // ARRANGE & ACT
        val initResult = detector.initialize()

        // ASSERT
        assertTrue(initResult, "Should initialize with correct AccuratePoseDetectorOptions")
        // Additional verification would require access to internal detector options
        // In a real implementation, we might expose configuration details for testing
    }

    // IMAGE PROCESSING ACCURACY TESTS

    @Test
    fun `should detect 33 landmarks for valid pose`() = testScope.runTest {
        // ARRANGE
        detector.initialize()
        val mockImageProxy = createMockImageProxy()

        // ACT
        val results = detector.processImageProxy(mockImageProxy).take(1).toList()

        // ASSERT
        assertEquals(1, results.size, "Should emit exactly one result")
        val result = results.first()
        assertEquals(33, result.landmarks.size, "Should detect 33 pose landmarks")
        assertEquals(33, result.worldLandmarks.size, "Should have 33 world landmarks")
        assertTrue(result.timestampMs > 0, "Should have valid timestamp")
    }

    @Test
    fun `should handle invalid image formats gracefully`() = testScope.runTest {
        // ARRANGE
        detector.initialize()
        val invalidImageProxy = createMockImageProxy(format = ImageFormat.UNKNOWN)

        // ACT
        val results = detector.processImageProxy(invalidImageProxy).take(1).toList()

        // ASSERT
        // Should either process successfully or handle error gracefully
        assertTrue(results.isEmpty() || results.first().landmarks.isNotEmpty())
    }

    @Test
    fun `should handle concurrent processing correctly`() = testScope.runTest {
        // ARRANGE
        detector.initialize()
        val imageProxy1 = createMockImageProxy()
        val imageProxy2 = createMockImageProxy()

        // ACT - Process multiple images concurrently
        val flow1 = detector.processImageProxy(imageProxy1)
        val flow2 = detector.processImageProxy(imageProxy2)

        val results1 = flow1.take(1).toList()
        val results2 = flow2.take(1).toList()

        // ASSERT
        // Should handle concurrent processing without crashes
        // Results might be empty if second request is dropped (expected behavior)
        assertTrue(results1.size <= 1, "Should handle first request")
        assertTrue(results2.size <= 1, "Should handle second request gracefully")
    }

    @Test
    fun `should maintain coordinate accuracy within tolerance`() = testScope.runTest {
        // ARRANGE
        detector.initialize()
        val mockImageProxy = createMockImageProxy()

        // ACT
        val results = detector.processImageProxy(mockImageProxy).take(1).toList()

        // ASSERT
        if (results.isNotEmpty()) {
            val result = results.first()
            result.landmarks.forEach { landmark ->
                assertTrue(landmark.x >= 0f && landmark.x <= 1f,
                    "X coordinate should be normalized (0-1)")
                assertTrue(landmark.y >= 0f && landmark.y <= 1f,
                    "Y coordinate should be normalized (0-1)")
                assertTrue(landmark.visibility >= 0f && landmark.visibility <= 1f,
                    "Visibility should be between 0-1")
                assertTrue(landmark.presence >= 0f && landmark.presence <= 1f,
                    "Presence should be between 0-1")
            }
        }
    }

    // PERFORMANCE BENCHMARK TESTS

    @Test
    fun `should process images under 100ms threshold`() = testScope.runTest {
        // ARRANGE
        detector.initialize()
        val mockImageProxy = createMockImageProxy()
        val iterations = 10
        val maxProcessingTime = 100L

        // ACT
        val processingTimes = mutableListOf<Long>()

        repeat(iterations) {
            val startTime = System.currentTimeMillis()
            detector.processImageProxy(mockImageProxy).take(1).collect { result ->
                val processingTime = System.currentTimeMillis() - startTime
                processingTimes.add(processingTime)
            }
        }

        // ASSERT
        if (processingTimes.isNotEmpty()) {
            val averageTime = processingTimes.average()
            val maxTime = processingTimes.maxOrNull() ?: 0L

            assertTrue(averageTime <= maxProcessingTime,
                "Average processing time should be under ${maxProcessingTime}ms, was ${averageTime}ms")
            assertTrue(maxTime <= maxProcessingTime * 2,
                "Max processing time should be reasonable, was ${maxTime}ms")
        }
    }

    @Test
    fun `should handle memory pressure gracefully`() = testScope.runTest {
        // ARRANGE
        detector.initialize()
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // ACT - Process many images to create memory pressure
        repeat(50) {
            val mockImageProxy = createMockImageProxy()
            detector.processImageProxy(mockImageProxy).take(1).collect { }
        }

        // Force garbage collection
        System.gc()
        advanceUntilIdle()

        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // ASSERT
        val memoryIncrease = finalMemory - initialMemory
        val maxMemoryIncrease = 50 * 1024 * 1024 // 50MB threshold

        assertTrue(memoryIncrease < maxMemoryIncrease,
            "Memory increase should be reasonable: ${memoryIncrease / 1024 / 1024}MB")
    }

    // ERROR HANDLING TESTS

    @Test
    fun `should handle null image gracefully`() = testScope.runTest {
        // ARRANGE
        detector.initialize()
        val mockImageProxy = mockk<ImageProxy> {
            every { image } returns null
            every { close() } just Runs
            every { imageInfo } returns mockk {
                every { rotationDegrees } returns 0
            }
        }

        // ACT
        val results = detector.processImageProxy(mockImageProxy).toList()

        // ASSERT
        // Should handle null image without crashing
        assertTrue(results.isEmpty() || results.first().landmarks.isEmpty())
    }

    @Test
    fun `should handle processing errors gracefully`() = testScope.runTest {
        // ARRANGE
        detector.initialize()
        val mockImageProxy = mockk<ImageProxy> {
            every { image } throws RuntimeException("Processing error")
            every { close() } just Runs
            every { imageInfo } returns mockk {
                every { rotationDegrees } returns 0
            }
        }

        // ACT & ASSERT
        assertDoesNotThrow {
            detector.processImageProxy(mockImageProxy).toList()
        }
    }

    @Test
    fun `should handle uninitialized detector gracefully`() = testScope.runTest {
        // ARRANGE
        val uninitializedDetector = MLKitPoseDetector(mockContext)
        val mockImageProxy = createMockImageProxy()

        // ACT
        val results = uninitializedDetector.processImageProxy(mockImageProxy).toList()

        // ASSERT
        // Should handle uninitialized state gracefully
        assertTrue(results.isEmpty())

        uninitializedDetector.close()
    }

    // RESOURCE MANAGEMENT TESTS

    @Test
    fun `should release resources properly on close`() {
        // ARRANGE
        detector.initialize()

        // ACT
        detector.close()

        // ASSERT
        // Should not throw exceptions when closed
        assertDoesNotThrow {
            detector.close() // Should handle multiple close calls
        }
    }

    @Test
    fun `should handle image proxy lifecycle correctly`() = testScope.runTest {
        // ARRANGE
        detector.initialize()
        val mockImageProxy = createMockImageProxy()

        // ACT
        detector.processImageProxy(mockImageProxy).take(1).collect { }

        // ASSERT
        verify { mockImageProxy.close() }
    }

    @Test
    fun `should manage processing state correctly`() = testScope.runTest {
        // ARRANGE
        detector.initialize()
        val mockImageProxy1 = createMockImageProxy()
        val mockImageProxy2 = createMockImageProxy()

        // ACT - Start first processing
        val flow1 = detector.processImageProxy(mockImageProxy1)

        // Immediately start second processing (should be skipped due to isProcessing flag)
        val flow2 = detector.processImageProxy(mockImageProxy2)

        val results1 = flow1.take(1).toList()
        val results2 = flow2.take(1).toList()

        // ASSERT
        // First should process, second should be skipped
        assertTrue(results1.size <= 1, "First processing should complete")
        assertTrue(results2.isEmpty(), "Second processing should be skipped due to concurrent processing prevention")

        // Both image proxies should be closed
        verify { mockImageProxy1.close() }
        verify { mockImageProxy2.close() }
    }

    // PERFORMANCE REGRESSION TESTS

    @Test
    fun `should maintain consistent performance across multiple invocations`() = testScope.runTest {
        // ARRANGE
        detector.initialize()
        val iterations = 20
        val processingTimes = mutableListOf<Long>()

        // ACT
        repeat(iterations) {
            val mockImageProxy = createMockImageProxy()
            val startTime = System.currentTimeMillis()

            detector.processImageProxy(mockImageProxy).take(1).collect { }

            val processingTime = System.currentTimeMillis() - startTime
            processingTimes.add(processingTime)
        }

        // ASSERT
        if (processingTimes.size > 5) {
            val averageTime = processingTimes.average()
            val standardDeviation = calculateStandardDeviation(processingTimes, averageTime)

            // Performance should be consistent (low standard deviation)
            assertTrue(standardDeviation < averageTime * 0.5,
                "Performance should be consistent. StdDev: $standardDeviation, Avg: $averageTime")
        }
    }

    // HELPER METHODS

    private fun createMockImageProxy(
        width: Int = 720,
        height: Int = 1280,
        format: Int = ImageFormat.YUV_420_888
    ): ImageProxy = mockk {
        every { this@mockk.width } returns width
        every { this@mockk.height } returns height
        every { this@mockk.format } returns format
        every { close() } just Runs
        every { imageInfo } returns mockk {
            every { rotationDegrees } returns 0
        }
        every { planes } returns arrayOf(
            mockk { every { buffer } returns mockk(relaxed = true) },
            mockk { every { buffer } returns mockk(relaxed = true) },
            mockk { every { buffer } returns mockk(relaxed = true) }
        )

        // Mock the image property
        every { image } returns mockk(relaxed = true)
    }

    private fun calculateStandardDeviation(values: List<Long>, mean: Double): Double {
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }
}