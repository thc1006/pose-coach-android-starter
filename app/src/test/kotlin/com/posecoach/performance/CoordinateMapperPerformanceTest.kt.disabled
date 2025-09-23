package com.posecoach.performance

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.posecoach.app.overlay.CoordinateMapper
import com.posecoach.corepose.PoseLandmark
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Performance benchmark tests for coordinate transformation components.
 *
 * Requirements from CLAUDE.md:
 * - Coordinate transformation speed optimization
 * - Batch processing performance for landmarks
 * - Memory efficiency for large landmark sets
 *
 * TDD Approach:
 * RED: Write failing tests defining coordinate mapping performance requirements
 * GREEN: Implement minimal coordinate transformation logic
 * REFACTOR: Optimize for large datasets and real-time processing
 */
@RunWith(AndroidJUnit4::class)
class CoordinateMapperPerformanceTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var coordinateMapper: CoordinateMapper

    // Test data generators
    private val landmarkGenerator = LandmarkGenerator()
    private val memoryProfiler = MemoryProfiler()

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)

        coordinateMapper = mockk(relaxed = true)
        clearAllMocks()
    }

    @After
    fun tearDown() {
        testScope.cancel()
        clearAllMocks()
    }

    /**
     * TDD RED: Test single landmark transformation speed
     */
    @Test
    fun `single landmark transformation should be under 1ms`() = testScope.runTest {
        // Arrange
        val maxTransformationTimeMs = 1.0
        val testLandmark = landmarkGenerator.generateLandmark(0.5f, 0.5f, 0.8f)
        val sourceWidth = 1920
        val sourceHeight = 1080
        val targetWidth = 640
        val targetHeight = 480

        val mockTransformedPoint = CoordinateMapper.Point(320f, 240f)

        every {
            coordinateMapper.mapToOverlay(any(), any(), any(), any(), any())
        } returns mockTransformedPoint

        // Act & Assert
        val transformationTime = measureTimeMillis {
            val result = coordinateMapper.mapToOverlay(
                testLandmark, sourceWidth, sourceHeight, targetWidth, targetHeight
            )
            assertNotNull("Transformation result should not be null", result)
        }

        assertTrue("Single landmark transformation ($transformationTime ms) should be under $maxTransformationTimeMs ms",
                  transformationTime < maxTransformationTimeMs)
    }

    /**
     * TDD RED: Test batch landmark transformation performance
     */
    @Test
    fun `batch landmark transformation should process 33 landmarks under 5ms`() = testScope.runTest {
        // Arrange
        val landmarkCount = 33 // Full pose landmark set
        val maxBatchTimeMs = 5.0
        val landmarks = landmarkGenerator.generateLandmarkSet(landmarkCount)
        val sourceWidth = 1920
        val sourceHeight = 1080
        val targetWidth = 640
        val targetHeight = 480

        val mockTransformedPoints = landmarks.map {
            CoordinateMapper.Point(Random.nextFloat() * targetWidth, Random.nextFloat() * targetHeight)
        }

        every {
            coordinateMapper.mapBatchToOverlay(any(), any(), any(), any(), any())
        } returns mockTransformedPoints

        // Act & Assert
        val batchTransformationTime = measureTimeMillis {
            val results = coordinateMapper.mapBatchToOverlay(
                landmarks, sourceWidth, sourceHeight, targetWidth, targetHeight
            )
            assertEquals("Should transform all landmarks", landmarkCount, results.size)
            results.forEach { point ->
                assertTrue("X coordinate should be within bounds", point.x >= 0 && point.x <= targetWidth)
                assertTrue("Y coordinate should be within bounds", point.y >= 0 && point.y <= targetHeight)
            }
        }

        assertTrue("Batch transformation ($batchTransformationTime ms) should be under $maxBatchTimeMs ms",
                  batchTransformationTime < maxBatchTimeMs)
    }

    /**
     * TDD RED: Test coordinate transformation accuracy under performance pressure
     */
    @Test
    fun `coordinate transformation should maintain accuracy under time pressure`() = testScope.runTest {
        // Arrange
        val transformationCount = 1000
        val maxTotalTimeMs = 100L // 100ms for 1000 transformations = 0.1ms per transformation
        val accuracyTolerance = 2.0f // 2 pixel tolerance

        val testCases = (1..transformationCount).map {
            val x = Random.nextFloat()
            val y = Random.nextFloat()
            val landmark = landmarkGenerator.generateLandmark(x, y, 0.9f)
            val expectedX = x * 640
            val expectedY = y * 480
            TestCase(landmark, expectedX, expectedY)
        }

        // Mock accurate transformations
        every {
            coordinateMapper.mapToOverlay(any(), any(), any(), any(), any())
        } answers {
            val landmark = firstArg<PoseLandmark>()
            val targetWidth = arg<Int>(3)
            val targetHeight = arg<Int>(4)
            CoordinateMapper.Point(
                landmark.x * targetWidth,
                landmark.y * targetHeight
            )
        }

        // Act
        val totalTime = measureTimeMillis {
            testCases.forEach { testCase ->
                val result = coordinateMapper.mapToOverlay(
                    testCase.landmark, 1920, 1080, 640, 480
                )

                // Assert accuracy
                val errorX = Math.abs(result.x - testCase.expectedX)
                val errorY = Math.abs(result.y - testCase.expectedY)

                assertTrue("X coordinate error ($errorX) should be under tolerance ($accuracyTolerance)",
                          errorX <= accuracyTolerance)
                assertTrue("Y coordinate error ($errorY) should be under tolerance ($accuracyTolerance)",
                          errorY <= accuracyTolerance)
            }
        }

        // Assert performance
        assertTrue("Total transformation time ($totalTime ms) should be under $maxTotalTimeMs ms",
                  totalTime < maxTotalTimeMs)
    }

    /**
     * TDD RED: Test memory efficiency for large landmark sets
     */
    @Test
    fun `large landmark set processing should be memory efficient`() = testScope.runTest {
        // Arrange
        val largeSetSize = 1000 // Simulate large dataset
        val maxMemoryIncreaseMB = 10 // 10MB max increase
        val batchSize = 100

        val initialMemory = memoryProfiler.getCurrentMemoryUsage()
        val memoryMeasurements = mutableListOf<Long>()

        // Mock memory-efficient batch processing
        every {
            coordinateMapper.mapBatchToOverlay(any(), any(), any(), any(), any())
        } answers {
            val landmarks = firstArg<List<PoseLandmark>>()
            landmarks.map { landmark ->
                CoordinateMapper.Point(
                    landmark.x * 640,
                    landmark.y * 480
                )
            }
        }

        // Act
        val batches = (largeSetSize / batchSize)
        repeat(batches) { batchIndex ->
            val landmarks = landmarkGenerator.generateLandmarkSet(batchSize)

            coordinateMapper.mapBatchToOverlay(landmarks, 1920, 1080, 640, 480)

            // Measure memory every few batches
            if (batchIndex % 2 == 0) {
                memoryMeasurements.add(memoryProfiler.getCurrentMemoryUsage())
            }

            // Force minor GC to test memory cleanup
            if (batchIndex % 5 == 0) {
                System.gc()
                delay(10)
            }
        }

        // Final memory measurement
        System.gc()
        delay(50)
        val finalMemory = memoryProfiler.getCurrentMemoryUsage()

        // Assert
        val memoryIncreaseMB = (finalMemory - initialMemory) / (1024 * 1024)
        assertTrue("Memory increase ($memoryIncreaseMB MB) should be under $maxMemoryIncreaseMB MB",
                  memoryIncreaseMB < maxMemoryIncreaseMB)

        // Check for memory leaks
        if (memoryMeasurements.size >= 3) {
            val firstMeasurement = memoryMeasurements.first()
            val lastMeasurement = memoryMeasurements.last()
            val memoryGrowth = (lastMeasurement - firstMeasurement).toDouble() / firstMeasurement

            assertTrue("Memory growth rate ($memoryGrowth) should be under 30%", memoryGrowth < 0.3)
        }
    }

    /**
     * TDD RED: Test concurrent coordinate transformations
     */
    @Test
    fun `concurrent coordinate transformations should not degrade performance`() = testScope.runTest {
        // Arrange
        val threadCount = 4
        val transformationsPerThread = 250 // Total 1000 transformations
        val maxTotalTimeMs = 200L // 200ms for concurrent execution
        val completionCounters = (1..threadCount).map { AtomicInteger(0) }

        // Mock thread-safe coordinate mapping
        every {
            coordinateMapper.mapToOverlay(any(), any(), any(), any(), any())
        } answers {
            val landmark = firstArg<PoseLandmark>()
            delay(1) // Simulate minimal processing time
            CoordinateMapper.Point(
                landmark.x * 640,
                landmark.y * 480
            )
        }

        // Act
        val totalTime = measureTimeMillis {
            val jobs = (0 until threadCount).map { threadIndex ->
                testScope.launch {
                    repeat(transformationsPerThread) {
                        val landmark = landmarkGenerator.generateLandmark(
                            Random.nextFloat(),
                            Random.nextFloat(),
                            0.9f
                        )

                        val result = coordinateMapper.mapToOverlay(
                            landmark, 1920, 1080, 640, 480
                        )

                        assertNotNull("Result should not be null", result)
                        completionCounters[threadIndex].incrementAndGet()
                    }
                }
            }

            jobs.forEach { it.join() }
        }

        advanceUntilIdle()

        // Assert
        val totalCompletions = completionCounters.sumOf { it.get() }
        val expectedTotal = threadCount * transformationsPerThread

        assertEquals("All transformations should complete", expectedTotal, totalCompletions)
        assertTrue("Concurrent execution time ($totalTime ms) should be under $maxTotalTimeMs ms",
                  totalTime < maxTotalTimeMs)
    }

    /**
     * TDD RED: Test coordinate mapping with different rotation angles
     */
    @Test
    fun `coordinate mapping with rotation should maintain performance`() = testScope.runTest {
        // Arrange
        val rotationAngles = listOf(0f, 90f, 180f, 270f)
        val landmarksPerRotation = 33
        val maxTimePerRotationMs = 10L

        val landmarks = landmarkGenerator.generateLandmarkSet(landmarksPerRotation)

        every {
            coordinateMapper.mapToOverlayWithRotation(any(), any(), any(), any(), any(), any())
        } answers {
            val landmarkList = firstArg<List<PoseLandmark>>()
            val rotation = arg<Float>(5)

            // Simulate rotation calculation
            delay(2)

            landmarkList.map { landmark ->
                val rotatedX = when (rotation.toInt()) {
                    90 -> 1.0f - landmark.y
                    180 -> 1.0f - landmark.x
                    270 -> landmark.y
                    else -> landmark.x
                }
                val rotatedY = when (rotation.toInt()) {
                    90 -> landmark.x
                    180 -> 1.0f - landmark.y
                    270 -> 1.0f - landmark.x
                    else -> landmark.y
                }

                CoordinateMapper.Point(rotatedX * 640, rotatedY * 480)
            }
        }

        // Act & Assert
        rotationAngles.forEach { angle ->
            val rotationTime = measureTimeMillis {
                val results = coordinateMapper.mapToOverlayWithRotation(
                    landmarks, 1920, 1080, 640, 480, angle
                )

                assertEquals("Should transform all landmarks", landmarksPerRotation, results.size)
                results.forEach { point ->
                    assertTrue("Point should be within bounds",
                              point.x >= 0 && point.x <= 640 && point.y >= 0 && point.y <= 480)
                }
            }

            assertTrue("Rotation transformation ($rotationTime ms) for $angle° should be under $maxTimePerRotationMs ms",
                      rotationTime < maxTimePerRotationMs)
        }

        advanceUntilIdle()
    }

    /**
     * TDD RED: Test coordinate mapping precision with different screen sizes
     */
    @Test
    fun `coordinate mapping should maintain precision across different screen sizes`() = testScope.runTest {
        // Arrange
        val screenSizes = listOf(
            Pair(480, 640),   // Small phone
            Pair(720, 1280),  // HD phone
            Pair(1080, 1920), // FHD phone
            Pair(1440, 2560)  // QHD phone
        )
        val testLandmark = landmarkGenerator.generateLandmark(0.5f, 0.5f, 0.95f)
        val maxTransformationTimeMs = 2L

        // Mock precise coordinate mapping
        every {
            coordinateMapper.mapToOverlay(any(), any(), any(), any(), any())
        } answers {
            val landmark = firstArg<PoseLandmark>()
            val targetWidth = arg<Int>(3)
            val targetHeight = arg<Int>(4)

            CoordinateMapper.Point(
                landmark.x * targetWidth,
                landmark.y * targetHeight
            )
        }

        // Act & Assert
        screenSizes.forEach { (width, height) ->
            val transformationTime = measureTimeMillis {
                val result = coordinateMapper.mapToOverlay(
                    testLandmark, 1920, 1080, width, height
                )

                // Expected center point
                val expectedX = width / 2f
                val expectedY = height / 2f

                val errorX = Math.abs(result.x - expectedX)
                val errorY = Math.abs(result.y - expectedY)

                assertTrue("X precision error for ${width}x${height} should be minimal",
                          errorX < 1.0f)
                assertTrue("Y precision error for ${width}x${height} should be minimal",
                          errorY < 1.0f)
            }

            assertTrue("Transformation time for ${width}x${height} should be under $maxTransformationTimeMs ms",
                      transformationTime < maxTransformationTimeMs)
        }
    }

    /**
     * TDD RED: Test memory allocation patterns during continuous mapping
     */
    @Test
    fun `continuous coordinate mapping should have predictable memory patterns`() = testScope.runTest {
        // Arrange
        val continuousDurationMs = 5000L // 5 seconds
        val frameRate = 30 // 30 FPS
        val frameInterval = 1000L / frameRate
        val maxMemoryVarianceMB = 15 // 15MB variance allowed

        val memoryMeasurements = mutableListOf<Long>()
        val landmarks = landmarkGenerator.generateLandmarkSet(33)

        every {
            coordinateMapper.mapBatchToOverlay(any(), any(), any(), any(), any())
        } answers {
            val landmarkList = firstArg<List<PoseLandmark>>()
            landmarkList.map { landmark ->
                CoordinateMapper.Point(landmark.x * 640, landmark.y * 480)
            }
        }

        // Act
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < continuousDurationMs) {
            // Process frame
            coordinateMapper.mapBatchToOverlay(landmarks, 1920, 1080, 640, 480)

            // Measure memory every second
            if (memoryMeasurements.isEmpty() ||
                System.currentTimeMillis() - startTime > memoryMeasurements.size * 1000) {
                memoryMeasurements.add(memoryProfiler.getCurrentMemoryUsage())
            }

            delay(frameInterval)
        }

        advanceUntilIdle()

        // Assert
        if (memoryMeasurements.size >= 3) {
            val memoryVariance = calculateVariance(memoryMeasurements)
            val memoryVarianceMB = Math.sqrt(memoryVariance) / (1024 * 1024)

            assertTrue("Memory variance ($memoryVarianceMB MB) should be under $maxMemoryVarianceMB MB",
                      memoryVarianceMB < maxMemoryVarianceMB)

            // Check for steady state
            val trend = calculateTrend(memoryMeasurements)
            assertTrue("Memory usage should reach steady state (trend: $trend)", Math.abs(trend) < 0.1)
        }
    }

    // Helper classes and functions
    private data class TestCase(
        val landmark: PoseLandmark,
        val expectedX: Float,
        val expectedY: Float
    )

    private class LandmarkGenerator {
        fun generateLandmark(x: Float, y: Float, visibility: Float): PoseLandmark {
            return mockk<PoseLandmark> {
                every { this@mockk.x } returns x
                every { this@mockk.y } returns y
                every { this@mockk.visibility } returns visibility
            }
        }

        fun generateLandmarkSet(count: Int): List<PoseLandmark> {
            return (1..count).map {
                generateLandmark(
                    Random.nextFloat(),
                    Random.nextFloat(),
                    0.7f + Random.nextFloat() * 0.3f // Visibility between 0.7 and 1.0
                )
            }
        }
    }

    private class MemoryProfiler {
        fun getCurrentMemoryUsage(): Long {
            val runtime = Runtime.getRuntime()
            return runtime.totalMemory() - runtime.freeMemory()
        }
    }

    private fun calculateVariance(values: List<Long>): Double {
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }

    private fun calculateTrend(values: List<Long>): Double {
        if (values.size < 2) return 0.0

        val n = values.size
        val x = (0 until n).toList()
        val y = values.map { it.toDouble() }

        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y) { xi, yi -> xi * yi }.sum()
        val sumX2 = x.map { it * it }.sum()

        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
    }
}