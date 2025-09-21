package com.posecoach.corepose.repository

import android.content.Context
import android.graphics.Bitmap
import com.posecoach.corepose.models.PoseDetectionError
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.mock
import kotlinx.coroutines.delay
import kotlin.system.measureTimeMillis

/**
 * Performance benchmarking tests for PoseRepository implementations.
 * These tests measure and validate performance characteristics against requirements.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PoseRepositoryBenchmarkTest {

    companion object {
        private const val TARGET_INFERENCE_TIME_MS = 30L
        private const val TARGET_FPS = 30
        private const val FRAME_TIME_BUDGET_MS = 1000L / TARGET_FPS // ~33ms
        private const val BENCHMARK_ITERATIONS = 100
        private const val WARM_UP_ITERATIONS = 10
    }

    private lateinit var fakeRepository: FakePoseRepository
    private lateinit var mockContext: Context
    private lateinit var mockBitmap: Bitmap
    private lateinit var benchmarkListener: BenchmarkPoseDetectionListener

    @Before
    fun setup() {
        fakeRepository = FakePoseRepository()
        mockContext = mock()
        mockBitmap = mock()
        benchmarkListener = BenchmarkPoseDetectionListener()
    }

    @Test
    fun `fake repository should meet inference time requirements`() = runTest {
        fakeRepository.init(mockContext)
        fakeRepository.start(benchmarkListener)

        // Warm up
        repeat(WARM_UP_ITERATIONS) {
            fakeRepository.detectAsync(mockBitmap, System.currentTimeMillis())
            delay(5)
        }

        // Benchmark
        val inferenceTimeMs = measureTimeMillis {
            repeat(BENCHMARK_ITERATIONS) {
                fakeRepository.detectAsync(mockBitmap, System.currentTimeMillis())
                delay(1) // Minimal delay to allow processing
            }
        }

        val avgInferenceTime = inferenceTimeMs.toDouble() / BENCHMARK_ITERATIONS
        val avgFps = 1000.0 / avgInferenceTime

        println("=== FakePoseRepository Benchmark ===")
        println("Average inference time: ${avgInferenceTime.toInt()}ms")
        println("Average FPS: ${avgFps.toInt()}")
        println("Target: <${TARGET_INFERENCE_TIME_MS}ms @${TARGET_FPS}fps")

        // Verify performance requirements
        assertTrue(
            "Average inference time ${avgInferenceTime.toInt()}ms exceeds target ${TARGET_INFERENCE_TIME_MS}ms",
            avgInferenceTime < TARGET_INFERENCE_TIME_MS
        )

        assertTrue(
            "Average FPS ${avgFps.toInt()} below target ${TARGET_FPS}",
            avgFps >= TARGET_FPS * 0.9 // Allow 10% tolerance
        )
    }

    @Test
    fun `fake repository should handle concurrent detection requests`() = runTest {
        fakeRepository.init(mockContext)
        fakeRepository.start(benchmarkListener)

        val concurrentRequests = 10
        val startTime = System.currentTimeMillis()

        // Send multiple concurrent requests
        repeat(concurrentRequests) { index ->
            fakeRepository.detectAsync(mockBitmap, System.currentTimeMillis() + index)
        }

        // Wait for all to complete
        delay(200)

        val totalTime = System.currentTimeMillis() - startTime
        val avgTimePerRequest = totalTime.toDouble() / concurrentRequests

        println("=== Concurrent Detection Benchmark ===")
        println("Total time for $concurrentRequests requests: ${totalTime}ms")
        println("Average time per request: ${avgTimePerRequest.toInt()}ms")

        // Should handle concurrent requests efficiently
        assertTrue(
            "Concurrent request handling too slow: ${avgTimePerRequest.toInt()}ms per request",
            avgTimePerRequest < FRAME_TIME_BUDGET_MS
        )
    }

    @Test
    fun `fake repository should generate consistent inference times`() = runTest {
        fakeRepository.init(mockContext)
        fakeRepository.start(benchmarkListener)

        val inferenceTimes = mutableListOf<Long>()

        repeat(50) {
            val timestampMs = System.currentTimeMillis()
            fakeRepository.detectAsync(mockBitmap, timestampMs)
            delay(5)

            benchmarkListener.lastResult?.let { result ->
                inferenceTimes.add(result.inferenceTimeMs)
            }
        }

        assertTrue("Not enough samples collected", inferenceTimes.size >= 40)

        val avgInference = inferenceTimes.average()
        val minInference = inferenceTimes.minOrNull() ?: 0L
        val maxInference = inferenceTimes.maxOrNull() ?: 0L
        val variance = inferenceTimes.map { (it - avgInference) * (it - avgInference) }.average()
        val stdDev = kotlin.math.sqrt(variance)

        println("=== Inference Time Consistency ===")
        println("Average: ${avgInference.toInt()}ms")
        println("Min: ${minInference}ms, Max: ${maxInference}ms")
        println("Standard deviation: ${stdDev.toInt()}ms")

        // Verify consistency (low variance)
        assertTrue(
            "Inference times too variable (stddev: ${stdDev.toInt()}ms)",
            stdDev < avgInference * 0.2 // Less than 20% of average
        )
    }

    @Test
    fun `pose generation methods should meet performance targets`() {
        val iterations = 1000
        val timestampMs = System.currentTimeMillis()

        // Benchmark generateStablePose
        val stablePoseTime = measureTimeMillis {
            repeat(iterations) {
                fakeRepository.generateStablePose(timestampMs)
            }
        }

        // Benchmark generateMultiPersonPose
        val multiPersonTime = measureTimeMillis {
            repeat(iterations) {
                fakeRepository.generateMultiPersonPose(timestampMs, 3)
            }
        }

        // Benchmark generateLowVisibilityPose
        val lowVisibilityTime = measureTimeMillis {
            repeat(iterations) {
                fakeRepository.generateLowVisibilityPose(timestampMs)
            }
        }

        val avgStableTime = stablePoseTime.toDouble() / iterations
        val avgMultiPersonTime = multiPersonTime.toDouble() / iterations
        val avgLowVisibilityTime = lowVisibilityTime.toDouble() / iterations

        println("=== Pose Generation Benchmark ===")
        println("Stable pose: ${avgStableTime}ms avg")
        println("Multi-person pose: ${avgMultiPersonTime}ms avg")
        println("Low visibility pose: ${avgLowVisibilityTime}ms avg")

        // All generation methods should be very fast (< 1ms)
        assertTrue("Stable pose generation too slow", avgStableTime < 1.0)
        assertTrue("Multi-person pose generation too slow", avgMultiPersonTime < 2.0)
        assertTrue("Low visibility pose generation too slow", avgLowVisibilityTime < 1.0)
    }

    @Test
    fun `repository should handle high frequency detection requests`() = runTest {
        fakeRepository.init(mockContext)
        fakeRepository.start(benchmarkListener)

        val requestFrequency = 60 // 60 FPS
        val testDurationMs = 1000L // 1 second
        val expectedRequests = (testDurationMs / (1000.0 / requestFrequency)).toInt()

        val startTime = System.currentTimeMillis()
        var requestCount = 0

        while (System.currentTimeMillis() - startTime < testDurationMs) {
            fakeRepository.detectAsync(mockBitmap, System.currentTimeMillis())
            requestCount++
            delay(1000L / requestFrequency) // Wait for next frame
        }

        val actualDuration = System.currentTimeMillis() - startTime
        val actualFps = requestCount.toDouble() / (actualDuration / 1000.0)

        println("=== High Frequency Detection ===")
        println("Requests sent: $requestCount in ${actualDuration}ms")
        println("Actual FPS: ${actualFps.toInt()}")
        println("Target FPS: $requestFrequency")

        // Should handle high frequency requests without significant drops
        assertTrue(
            "Failed to maintain target FPS: ${actualFps.toInt()} < ${requestFrequency * 0.9}",
            actualFps >= requestFrequency * 0.9
        )
    }

    @Test
    fun `memory usage should remain stable during extended operation`() = runTest {
        fakeRepository.init(mockContext)
        fakeRepository.start(benchmarkListener)

        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()

        // Run for extended period
        repeat(500) {
            fakeRepository.detectAsync(mockBitmap, System.currentTimeMillis())
            if (it % 50 == 0) {
                System.gc() // Encourage garbage collection
                delay(10)
            }
        }

        delay(100) // Allow final processing
        System.gc()
        delay(50)

        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = finalMemory - initialMemory

        println("=== Memory Usage ===")
        println("Initial memory: ${initialMemory / 1024}KB")
        println("Final memory: ${finalMemory / 1024}KB")
        println("Memory increase: ${memoryIncrease / 1024}KB")

        // Memory usage should not grow significantly (< 10MB increase)
        assertTrue(
            "Memory usage increased too much: ${memoryIncrease / 1024}KB",
            memoryIncrease < 10 * 1024 * 1024 // 10MB
        )
    }

    private class BenchmarkPoseDetectionListener : PoseDetectionListener {
        var lastResult: PoseLandmarkResult? = null
        var lastError: PoseDetectionError? = null
        var detectionCount = 0
        var errorCount = 0

        override fun onPoseDetected(result: PoseLandmarkResult) {
            lastResult = result
            detectionCount++
        }

        override fun onPoseDetectionError(error: PoseDetectionError) {
            lastError = error
            errorCount++
        }

        fun reset() {
            lastResult = null
            lastError = null
            detectionCount = 0
            errorCount = 0
        }
    }
}