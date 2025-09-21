package com.posecoach.corepose.biomechanics

import com.posecoach.corepose.biomechanics.models.QualityLevel
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.utils.PerformanceTracker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.*

/**
 * Performance and reliability tests for real-time biomechanics processing
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealTimeBiomechanicsProcessorTest {

    private lateinit var processor: RealTimeBiomechanicsProcessor
    private lateinit var performanceTracker: PerformanceTracker

    @BeforeEach
    fun setup() {
        performanceTracker = PerformanceTracker()
        processor = RealTimeBiomechanicsProcessor(
            performanceTracker = performanceTracker,
            maxLatencyMs = 30L,
            enableAdaptiveQuality = true
        )
    }

    @AfterEach
    fun teardown() {
        processor.stop()
    }

    @Test
    fun `test real-time processing latency requirement`() = runTest {
        val landmarks = createTestPoseLandmarks()

        // Test multiple frames to ensure consistent performance
        val latencies = mutableListOf<Long>()

        repeat(10) {
            val startTime = System.currentTimeMillis()

            val success = processor.processPose(landmarks)
            assertTrue(success, "Frame processing should succeed")

            // Wait for result
            val result = withTimeoutOrNull(100L) {
                processor.results.first()
            }

            val latency = System.currentTimeMillis() - startTime
            latencies.add(latency)

            assertNotNull(result, "Should receive analysis result")
            assertTrue(latency < 50L, "Processing latency ${latency}ms exceeds limit")
        }

        // Verify average latency
        val avgLatency = latencies.average()
        assertTrue(avgLatency < 35.0, "Average latency ${avgLatency}ms should be under 35ms")
    }

    @Test
    fun `test adaptive quality adjustment under load`() = runTest {
        val landmarks = createTestPoseLandmarks()

        // Start with high quality
        processor.setQualityLevel(QualityLevel.HIGH, disableAdaptive = false)

        // Simulate high load by rapid frame submission
        val job = launch {
            repeat(50) {
                processor.processPose(landmarks)
                delay(10) // 100fps - very high load
            }
        }

        delay(1000) // Let adaptive quality work

        val stats = processor.getProcessingStatistics()

        // Quality should have adapted to handle the load
        assertTrue(
            stats.currentQualityLevel != QualityLevel.HIGH || stats.frameDropRate < 0.1,
            "Should adapt quality or maintain low drop rate under load"
        )

        job.cancel()
    }

    @Test
    fun `test different quality levels performance`() = runTest {
        val landmarks = createTestPoseLandmarks()
        val qualityLatencies = mutableMapOf<QualityLevel, List<Long>>()

        for (quality in QualityLevel.values()) {
            processor.setQualityLevel(quality, disableAdaptive = true)

            val latencies = mutableListOf<Long>()
            repeat(5) {
                val startTime = System.currentTimeMillis()

                processor.processPose(landmarks)

                val result = withTimeoutOrNull(100L) {
                    processor.results.first()
                }

                val latency = System.currentTimeMillis() - startTime
                latencies.add(latency)

                assertNotNull(result, "Should get result for quality level $quality")
            }

            qualityLatencies[quality] = latencies
        }

        // Verify quality levels have expected performance characteristics
        val highAvg = qualityLatencies[QualityLevel.HIGH]!!.average()
        val mediumAvg = qualityLatencies[QualityLevel.MEDIUM]!!.average()
        val lowAvg = qualityLatencies[QualityLevel.LOW]!!.average()
        val minimalAvg = qualityLatencies[QualityLevel.MINIMAL]!!.average()

        // Lower quality should generally be faster
        assertTrue(minimalAvg <= lowAvg, "Minimal should be faster than low quality")
        assertTrue(lowAvg <= mediumAvg, "Low should be faster than medium quality")
        assertTrue(mediumAvg <= highAvg + 10, "Medium should be faster than or similar to high quality")

        // All should meet latency requirements
        assertTrue(highAvg < 50.0, "High quality average ${highAvg}ms too slow")
        assertTrue(minimalAvg < 10.0, "Minimal quality average ${minimalAvg}ms should be very fast")
    }

    @Test
    fun `test frame dropping under extreme load`() = runTest {
        val landmarks = createTestPoseLandmarks()

        // Disable adaptive quality to test pure frame dropping
        processor.setQualityLevel(QualityLevel.HIGH, disableAdaptive = true)

        // Submit frames faster than they can be processed
        val submitted = 100
        var successCount = 0

        repeat(submitted) {
            val success = processor.processPose(landmarks)
            if (success) successCount++
        }

        delay(2000) // Let processing catch up

        val stats = processor.getProcessingStatistics()

        // Should have dropped some frames under extreme load
        assertTrue(stats.frameDropRate > 0 || successCount < submitted,
                  "Should drop frames under extreme load")

        // But should maintain reasonable success rate
        assertTrue(stats.successRate > 0.5, "Should maintain reasonable success rate")
    }

    @Test
    fun `test memory efficiency under sustained load`() = runTest {
        val landmarks = createTestPoseLandmarks()

        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // Process many frames to test memory usage
        repeat(200) {
            processor.processPose(landmarks)
            if (it % 20 == 0) {
                delay(50) // Occasional pause to let processing catch up
            }
        }

        delay(1000) // Let processing complete

        // Force garbage collection
        System.gc()
        delay(100)

        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryIncrease = finalMemory - initialMemory

        // Memory increase should be reasonable (less than 20MB for sustained processing)
        assertTrue(memoryIncrease < 20 * 1024 * 1024,
                  "Memory usage increased by ${memoryIncrease / 1024 / 1024}MB, should be under 20MB")
    }

    @Test
    fun `test backpressure handling`() = runTest {
        val landmarks = createTestPoseLandmarks()

        // Fill up the processing queue
        repeat(20) {
            processor.processPose(landmarks)
        }

        val initialStats = processor.getProcessingStatistics()
        val initialQueueSize = initialStats.queueSize

        // Add more frames
        repeat(10) {
            processor.processPose(landmarks)
        }

        val finalStats = processor.getProcessingStatistics()

        // Queue should not grow indefinitely (backpressure should engage)
        assertTrue(finalStats.queueSize <= initialQueueSize + 5,
                  "Queue should not grow indefinitely due to backpressure")
    }

    @Test
    fun `test error recovery`() = runTest {
        val landmarks = createTestPoseLandmarks()

        // Process valid frames
        repeat(5) {
            val success = processor.processPose(landmarks)
            assertTrue(success, "Valid frames should process successfully")
        }

        // Process invalid frames
        val invalidLandmarks = createInvalidPoseLandmarks()
        repeat(3) {
            // Should not crash on invalid input
            assertDoesNotThrow {
                processor.processPose(invalidLandmarks)
            }
        }

        // Should still be able to process valid frames after errors
        repeat(5) {
            val success = processor.processPose(landmarks)
            assertTrue(success, "Should recover from errors and process valid frames")
        }

        delay(500) // Let processing complete

        val stats = processor.getProcessingStatistics()
        assertTrue(stats.successRate > 0.5, "Should maintain reasonable success rate despite errors")
    }

    @Test
    fun `test concurrent processing safety`() = runTest {
        val landmarks = createTestPoseLandmarks()

        // Launch multiple coroutines to submit frames concurrently
        val jobs = (1..5).map { threadId ->
            launch {
                repeat(20) {
                    processor.processPose(landmarks.copy(timestampMs = System.currentTimeMillis() + threadId))
                    delay(5)
                }
            }
        }

        // Wait for all submissions to complete
        jobs.forEach { it.join() }

        delay(1000) // Let processing complete

        val stats = processor.getProcessingStatistics()

        // Should handle concurrent access without issues
        assertTrue(stats.errorRate < 0.1, "Error rate should be low with concurrent access")
        assertTrue(stats.successRate > 0.8, "Success rate should be high with concurrent access")
    }

    @Test
    fun `test performance metrics accuracy`() = runTest {
        val landmarks = createTestPoseLandmarks()

        // Process known number of frames
        val framesToProcess = 10
        var successfulSubmissions = 0

        repeat(framesToProcess) {
            val success = processor.processPose(landmarks)
            if (success) successfulSubmissions++
        }

        delay(500) // Let processing complete

        val stats = processor.getProcessingStatistics()

        // Verify metrics are reasonable
        assertTrue(stats.averageProcessingTime > 0, "Should track processing time")
        assertTrue(stats.successRate >= 0.0, "Success rate should be non-negative")
        assertTrue(stats.frameDropRate >= 0.0, "Drop rate should be non-negative")
        assertTrue(stats.successRate + stats.frameDropRate <= 1.1, "Rates should sum to ~1.0")
    }

    @Test
    fun `test graceful shutdown`() = runTest {
        val landmarks = createTestPoseLandmarks()

        // Start processing
        repeat(5) {
            processor.processPose(landmarks)
        }

        // Stop processor
        processor.stop()

        // Should not accept new frames after stop
        val success = processor.processPose(landmarks)
        assertFalse(success, "Should not accept frames after stop")

        // Should not crash when stopped
        assertDoesNotThrow {
            processor.getProcessingStatistics()
        }
    }

    @Test
    fun `test quality level transitions preserve correctness`() = runTest {
        val landmarks = createTestPoseLandmarks()

        val qualities = listOf(
            QualityLevel.HIGH,
            QualityLevel.MEDIUM,
            QualityLevel.LOW,
            QualityLevel.MINIMAL,
            QualityLevel.HIGH
        )

        for (quality in qualities) {
            processor.setQualityLevel(quality, disableAdaptive = true)

            processor.processPose(landmarks)

            val result = withTimeoutOrNull(100L) {
                processor.results.first()
            }

            assertNotNull(result, "Should get result for quality level $quality")

            // All quality levels should provide basic analysis structure
            assertNotNull(result.jointAngles)
            assertNotNull(result.asymmetryAnalysis)
            assertNotNull(result.posturalAnalysis)
            assertNotNull(result.movementQuality)

            // Confidence should be reasonable for all quality levels
            assertTrue(result.confidenceScore >= 0f && result.confidenceScore <= 1f,
                      "Confidence score should be valid for quality level $quality")
        }
    }

    // Helper functions
    private fun createTestPoseLandmarks(): PoseLandmarkResult {
        val landmarks = (0 until 33).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.5f + (index % 3 - 1) * 0.1f,
                y = 0.5f + (index / 11) * 0.1f,
                z = 0f,
                visibility = 0.9f,
                presence = 0.95f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 10L
        )
    }

    private fun createInvalidPoseLandmarks(): PoseLandmarkResult {
        val landmarks = (0 until 33).map { index ->
            PoseLandmarkResult.Landmark(
                x = Float.NaN,
                y = Float.NaN,
                z = Float.NaN,
                visibility = 0f,
                presence = 0f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 10L
        )
    }
}