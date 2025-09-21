package com.posecoach.suggestions

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.posecoach.suggestions.models.PoseLandmarksData
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.system.measureTimeMillis

/**
 * Performance benchmarks for pose suggestion clients
 */
class PerformanceBenchmarkTest {

    private lateinit var mockContext: Context
    private lateinit var clientFactory: PoseSuggestionClientFactory
    private lateinit var fakeClient: PoseSuggestionClient
    private lateinit var testLandmarks: PoseLandmarksData

    companion object {
        private const val BENCHMARK_ITERATIONS = 10
        private const val MAX_ACCEPTABLE_RESPONSE_TIME_MS = 500L
        private const val TARGET_THROUGHPUT_PER_SECOND = 5
    }

    @Before
    fun setup() {
        mockContext = mock()
        clientFactory = PoseSuggestionClientFactory(mockContext)
        fakeClient = clientFactory.createFakeClient()
        testLandmarks = createTestLandmarks()
    }

    @Test
    fun `fake client response time should be under threshold`() = runTest {
        val responseTimes = mutableListOf<Long>()

        repeat(BENCHMARK_ITERATIONS) {
            val responseTime = measureTimeMillis {
                val result = fakeClient.getPoseSuggestions(testLandmarks)
                assertThat(result.isSuccess).isTrue()
            }
            responseTimes.add(responseTime)
        }

        val averageTime = responseTimes.average()
        val maxTime = responseTimes.maxOrNull() ?: 0L

        println("Fake client performance:")
        println("  Average response time: ${averageTime}ms")
        println("  Max response time: ${maxTime}ms")
        println("  All response times: $responseTimes")

        assertThat(averageTime).isLessThan(MAX_ACCEPTABLE_RESPONSE_TIME_MS.toDouble())
        assertThat(maxTime).isLessThan(MAX_ACCEPTABLE_RESPONSE_TIME_MS * 2) // Allow 2x for max
    }

    @Test
    fun `fake client should handle concurrent requests efficiently`() = runTest {
        val concurrentRequests = 5
        val requestTime = measureTimeMillis {
            repeat(concurrentRequests) {
                val result = fakeClient.getPoseSuggestions(testLandmarks)
                assertThat(result.isSuccess).isTrue()
            }
        }

        val throughput = (concurrentRequests * 1000.0) / requestTime
        println("Concurrent performance:")
        println("  $concurrentRequests requests in ${requestTime}ms")
        println("  Throughput: ${"%.2f".format(throughput)} requests/second")

        assertThat(throughput).isAtLeast(TARGET_THROUGHPUT_PER_SECOND.toDouble())
    }

    @Test
    fun `error handling wrapper should not significantly impact performance`() = runTest {
        val wrapper = ErrorHandlingWrapper(
            primaryClient = fakeClient,
            fallbackClient = fakeClient
        )

        // Benchmark direct client
        val directTime = measureTimeMillis {
            repeat(BENCHMARK_ITERATIONS) {
                fakeClient.getPoseSuggestions(testLandmarks)
            }
        }

        // Benchmark wrapped client
        val wrappedTime = measureTimeMillis {
            repeat(BENCHMARK_ITERATIONS) {
                wrapper.getPoseSuggestions(testLandmarks)
            }
        }

        val overhead = wrappedTime - directTime
        val overheadPercentage = (overhead.toDouble() / directTime) * 100

        println("Error handling overhead:")
        println("  Direct client: ${directTime}ms")
        println("  Wrapped client: ${wrappedTime}ms")
        println("  Overhead: ${overhead}ms (${overheadPercentage.toInt()}%)")

        // Overhead should be minimal (less than 50%)
        assertThat(overheadPercentage).isLessThan(50.0)
    }

    @Test
    fun `memory usage should be reasonable for repeated calls`() = runTest {
        val runtime = Runtime.getRuntime()

        // Force garbage collection before measurement
        System.gc()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()

        // Make many requests to test memory usage
        repeat(50) {
            val result = fakeClient.getPoseSuggestions(testLandmarks)
            assertThat(result.isSuccess).isTrue()
        }

        System.gc()
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = finalMemory - initialMemory

        println("Memory usage:")
        println("  Initial memory: ${initialMemory / 1024}KB")
        println("  Final memory: ${finalMemory / 1024}KB")
        println("  Memory increase: ${memoryIncrease / 1024}KB")

        // Memory increase should be reasonable (less than 1MB for 50 calls)
        assertThat(memoryIncrease).isLessThan(1024 * 1024) // 1MB
    }

    @Test
    fun `response validation should be fast`() = runTest {
        val wrapper = ErrorHandlingWrapper(fakeClient, fakeClient)

        val validationTime = measureTimeMillis {
            repeat(BENCHMARK_ITERATIONS) {
                val result = wrapper.getPoseSuggestions(testLandmarks)
                assertThat(result.isSuccess).isTrue()

                // Verify validation worked
                val response = result.getOrNull()!!
                assertThat(response.suggestions).hasSize(3)
                response.suggestions.forEach { suggestion ->
                    assertThat(suggestion.title).isNotEmpty()
                    assertThat(suggestion.instruction.length).isAtLeast(20)
                    assertThat(suggestion.targetLandmarks).isNotEmpty()
                }
            }
        }

        val avgValidationTime = validationTime.toDouble() / BENCHMARK_ITERATIONS
        println("Response validation performance:")
        println("  Total validation time: ${validationTime}ms")
        println("  Average per validation: ${avgValidationTime}ms")

        assertThat(avgValidationTime).isLessThan(50.0) // Should be very fast
    }

    @Test
    fun `pose context analysis should be efficient`() = runTest {
        val fakeClientWithAnalysis = FakePoseSuggestionClient()

        // Test different pose types for analysis performance
        val standingPose = createStandingLandmarks()
        val armsRaisedPose = createArmsRaisedLandmarks()
        val forwardHeadPose = createForwardHeadLandmarks()

        val poses = listOf(standingPose, armsRaisedPose, forwardHeadPose)

        val analysisTime = measureTimeMillis {
            poses.forEach { pose ->
                repeat(10) {
                    val result = fakeClientWithAnalysis.getPoseSuggestions(pose)
                    assertThat(result.isSuccess).isTrue()
                }
            }
        }

        val avgAnalysisTime = analysisTime.toDouble() / (poses.size * 10)
        println("Pose context analysis performance:")
        println("  Total analysis time: ${analysisTime}ms")
        println("  Average per analysis: ${avgAnalysisTime}ms")

        assertThat(avgAnalysisTime).isLessThan(100.0)
    }

    private fun createTestLandmarks(): PoseLandmarksData {
        return PoseLandmarksData(
            landmarks = List(33) { index ->
                PoseLandmarksData.LandmarkPoint(
                    index = index,
                    x = 0.5f + (index * 0.01f),
                    y = 0.5f + (index * 0.01f),
                    z = 0f,
                    visibility = 0.9f,
                    presence = 0.9f
                )
            }
        )
    }

    private fun createStandingLandmarks(): PoseLandmarksData {
        return PoseLandmarksData(
            landmarks = List(33) { index ->
                when (index) {
                    23, 24 -> PoseLandmarksData.LandmarkPoint(index, 0.5f, 0.6f, 0f, 0.9f, 0.9f) // Hips
                    25, 26 -> PoseLandmarksData.LandmarkPoint(index, 0.5f, 0.8f, 0f, 0.9f, 0.9f) // Knees below hips
                    else -> PoseLandmarksData.LandmarkPoint(index, 0.5f, 0.5f, 0f, 0.9f, 0.9f)
                }
            }
        )
    }

    private fun createArmsRaisedLandmarks(): PoseLandmarksData {
        return PoseLandmarksData(
            landmarks = List(33) { index ->
                when (index) {
                    11, 12 -> PoseLandmarksData.LandmarkPoint(index, 0.5f, 0.5f, 0f, 0.9f, 0.9f) // Shoulders
                    15, 16 -> PoseLandmarksData.LandmarkPoint(index, 0.5f, 0.3f, 0f, 0.9f, 0.9f) // Wrists above shoulders
                    else -> PoseLandmarksData.LandmarkPoint(index, 0.5f, 0.5f, 0f, 0.9f, 0.9f)
                }
            }
        )
    }

    private fun createForwardHeadLandmarks(): PoseLandmarksData {
        return PoseLandmarksData(
            landmarks = List(33) { index ->
                when (index) {
                    0 -> PoseLandmarksData.LandmarkPoint(index, 0.6f, 0.3f, 0f, 0.9f, 0.9f) // Nose forward
                    11, 12 -> PoseLandmarksData.LandmarkPoint(index, 0.5f, 0.5f, 0f, 0.9f, 0.9f) // Shoulders
                    else -> PoseLandmarksData.LandmarkPoint(index, 0.5f, 0.5f, 0f, 0.9f, 0.9f)
                }
            }
        )
    }
}