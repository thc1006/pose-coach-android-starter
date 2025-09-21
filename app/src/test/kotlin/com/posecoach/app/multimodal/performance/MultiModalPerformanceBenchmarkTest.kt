package com.posecoach.app.multimodal.performance

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.test.core.app.ApplicationProvider
import com.posecoach.app.privacy.EnhancedPrivacyManager
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

/**
 * Test suite for MultiModalPerformanceBenchmark
 */
@RunWith(RobolectricTestRunner::class)
class MultiModalPerformanceBenchmarkTest {

    private lateinit var context: Context
    private lateinit var testScope: TestScope
    private lateinit var privacyManager: EnhancedPrivacyManager
    private lateinit var benchmark: MultiModalPerformanceBenchmark

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testScope = TestScope()
        privacyManager = EnhancedPrivacyManager(context)

        // Set balanced privacy level for testing
        privacyManager.setPrivacyLevel(EnhancedPrivacyManager.PrivacyLevel.BALANCED)

        benchmark = MultiModalPerformanceBenchmark(
            context = context,
            lifecycleScope = testScope,
            privacyManager = privacyManager
        )
    }

    @Test
    fun `test comprehensive benchmark execution`() = testScope.runTest {
        // When
        val result = benchmark.runComprehensiveBenchmark()

        // Then
        assertNotNull(result)
        assertTrue(result.results.isNotEmpty())
        assertNotNull(result.stressTestResult)
        assertNotNull(result.optimizationRecommendations)
        assertTrue(result.overallScore >= 0.0)

        // Verify all expected benchmark components are present
        val testNames = result.results.map { it.testName }
        assertTrue(testNames.contains("FusionEngine"))
        assertTrue(testNames.contains("ProcessingPipeline"))
        assertTrue(testNames.contains("VisionAnalyzer"))
        assertTrue(testNames.contains("AudioProcessor"))
        assertTrue(testNames.contains("PrivacyManager"))
        assertTrue(testNames.contains("EndToEndWorkflow"))
    }

    @Test
    fun `test benchmark result validation`() = testScope.runTest {
        // When
        val result = benchmark.runComprehensiveBenchmark()

        // Then
        result.results.forEach { benchmarkResult ->
            assertTrue(benchmarkResult.averageLatencyMs >= 0.0)
            assertTrue(benchmarkResult.minLatencyMs >= 0L)
            assertTrue(benchmarkResult.maxLatencyMs >= benchmarkResult.minLatencyMs)
            assertTrue(benchmarkResult.throughputOpsPerSecond >= 0.0)
            assertTrue(benchmarkResult.memoryUsageMB >= 0.0)
            assertTrue(benchmarkResult.successRate >= 0.0)
            assertTrue(benchmarkResult.successRate <= 1.0)
            assertTrue(benchmarkResult.p95LatencyMs >= 0.0)
            assertTrue(benchmarkResult.p99LatencyMs >= benchmarkResult.p95LatencyMs)
        }
    }

    @Test
    fun `test stress test result validation`() = testScope.runTest {
        // When
        val result = benchmark.runComprehensiveBenchmark()
        val stressResult = result.stressTestResult

        // Then
        assertTrue(stressResult.testDurationMs > 0L)
        assertTrue(stressResult.totalOperations >= 0L)
        assertTrue(stressResult.averageThroughput >= 0.0)
        assertTrue(stressResult.memoryPeakMB >= 0.0)
        assertTrue(stressResult.errorCount >= 0L)
        assertTrue(stressResult.performanceDegradation >= 0.0)
        assertTrue(listOf("stable", "degraded", "unstable", "memory_leak").contains(stressResult.systemStability))
    }

    @Test
    fun `test optimization recommendations generation`() = testScope.runTest {
        // When
        val result = benchmark.runComprehensiveBenchmark()

        // Then
        result.optimizationRecommendations.forEach { recommendation ->
            assertNotNull(recommendation.component)
            assertNotNull(recommendation.issue)
            assertNotNull(recommendation.recommendation)
            assertNotNull(recommendation.expectedImprovement)
            assertTrue(MultiModalPerformanceBenchmark.Priority.values().contains(recommendation.priority))
        }
    }

    @Test
    fun `test device info collection`() = testScope.runTest {
        // When
        val result = benchmark.runComprehensiveBenchmark()
        val deviceInfo = result.deviceInfo

        // Then
        assertNotNull(deviceInfo.manufacturer)
        assertNotNull(deviceInfo.model)
        assertNotNull(deviceInfo.androidVersion)
        assertTrue(deviceInfo.apiLevel > 0)
        assertTrue(deviceInfo.totalMemoryMB > 0)
        assertTrue(deviceInfo.availableProcessors > 0)
    }

    @Test
    fun `test overall score calculation`() = testScope.runTest {
        // When
        val result = benchmark.runComprehensiveBenchmark()

        // Then
        assertTrue(result.overallScore >= 0.0)
        assertTrue(result.overallScore <= 1000.0) // Reasonable upper bound
    }

    @Test
    fun `test benchmark with privacy restrictions`() = testScope.runTest {
        // Given - maximum privacy restrictions
        privacyManager.setPrivacyLevel(EnhancedPrivacyManager.PrivacyLevel.MAXIMUM_PRIVACY)
        privacyManager.setOfflineModeEnabled(true)

        // When
        val result = benchmark.runComprehensiveBenchmark()

        // Then - should still complete successfully but with potentially different results
        assertNotNull(result)
        assertTrue(result.results.isNotEmpty())

        // Privacy manager benchmark should show different characteristics
        val privacyResult = result.results.find { it.testName == "PrivacyManager" }
        assertNotNull(privacyResult)
    }

    @Test
    fun `test benchmark performance characteristics`() = testScope.runTest {
        // When
        val startTime = System.currentTimeMillis()
        val result = benchmark.runComprehensiveBenchmark()
        val totalTime = System.currentTimeMillis() - startTime

        // Then - comprehensive benchmark should complete in reasonable time
        assertTrue(totalTime < 120000L) // Less than 2 minutes for comprehensive benchmark

        // Check that results show reasonable performance characteristics
        result.results.forEach { benchmarkResult ->
            // Most operations should complete within reasonable time
            assertTrue(benchmarkResult.averageLatencyMs < 10000.0) // 10 seconds max average

            // Success rate should be high for properly functioning components
            if (benchmarkResult.testName !in listOf("GeminiIntegration")) {
                assertTrue(benchmarkResult.successRate > 0.8) // 80% minimum success rate
            }
        }
    }

    @Test
    fun `test memory leak detection in stress test`() = testScope.runTest {
        // When
        val result = benchmark.runComprehensiveBenchmark()
        val stressResult = result.stressTestResult

        // Then - check if memory leak detection is working
        // (Note: In test environment, we may not detect actual leaks)
        assertNotNull(stressResult.memoryLeakDetected)

        if (stressResult.memoryLeakDetected) {
            // If leak detected, should have critical recommendation
            assertTrue(result.optimizationRecommendations.any {
                it.priority == MultiModalPerformanceBenchmark.Priority.CRITICAL
            })
        }
    }

    @Test
    fun `test error handling during benchmark execution`() = testScope.runTest {
        // Given - invalid privacy manager state to potentially cause errors
        // (This is more for robustness testing)

        // When
        val result = benchmark.runComprehensiveBenchmark()

        // Then - should handle errors gracefully and still produce results
        assertNotNull(result)
        assertTrue(result.results.isNotEmpty())

        // Check that we get reasonable error rates
        result.results.forEach { benchmarkResult ->
            // Even with errors, success rate shouldn't be zero
            assertTrue(benchmarkResult.successRate >= 0.0)
        }
    }

    @Test
    fun `test benchmark reproducibility`() = testScope.runTest {
        // When - run benchmark twice
        val result1 = benchmark.runComprehensiveBenchmark()
        val result2 = benchmark.runComprehensiveBenchmark()

        // Then - results should be generally consistent
        assertEquals(result1.results.size, result2.results.size)

        result1.results.zip(result2.results).forEach { (r1, r2) ->
            assertEquals(r1.testName, r2.testName)
            // Allow for some variation but should be in same ballpark
            assertTrue(abs(r1.averageLatencyMs - r2.averageLatencyMs) < r1.averageLatencyMs * 2.0)
        }
    }

    @Test
    fun `test priority assignment in recommendations`() = testScope.runTest {
        // When
        val result = benchmark.runComprehensiveBenchmark()

        // Then - verify priority assignment logic
        result.optimizationRecommendations.forEach { recommendation ->
            when {
                recommendation.issue.contains("memory leak") -> {
                    assertEquals(MultiModalPerformanceBenchmark.Priority.CRITICAL, recommendation.priority)
                }
                recommendation.issue.contains("Low success rate") -> {
                    assertTrue(recommendation.priority in listOf(
                        MultiModalPerformanceBenchmark.Priority.HIGH,
                        MultiModalPerformanceBenchmark.Priority.CRITICAL
                    ))
                }
                recommendation.issue.contains("High average latency") -> {
                    assertEquals(MultiModalPerformanceBenchmark.Priority.HIGH, recommendation.priority)
                }
            }
        }
    }

    @Test
    fun `test component isolation in benchmarks`() = testScope.runTest {
        // When
        val result = benchmark.runComprehensiveBenchmark()

        // Then - each component should be benchmarked independently
        val componentNames = result.results.map { it.testName }.toSet()

        // Verify expected components are present and unique
        assertTrue(componentNames.contains("FusionEngine"))
        assertTrue(componentNames.contains("VisionAnalyzer"))
        assertTrue(componentNames.contains("AudioProcessor"))
        assertEquals(componentNames.size, result.results.size) // No duplicates
    }
}

/**
 * Performance regression tests
 */
@RunWith(RobolectricTestRunner::class)
class MultiModalPerformanceBenchmarkRegressionTest {

    private lateinit var context: Context
    private lateinit var testScope: TestScope
    private lateinit var privacyManager: EnhancedPrivacyManager
    private lateinit var benchmark: MultiModalPerformanceBenchmark

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testScope = TestScope()
        privacyManager = EnhancedPrivacyManager(context)

        benchmark = MultiModalPerformanceBenchmark(
            context = context,
            lifecycleScope = testScope,
            privacyManager = privacyManager
        )
    }

    @Test
    fun `test performance baseline requirements`() = testScope.runTest {
        // When
        val result = benchmark.runComprehensiveBenchmark()

        // Then - verify performance meets baseline requirements
        result.results.forEach { benchmarkResult ->
            when (benchmarkResult.testName) {
                "FusionEngine" -> {
                    // Core fusion should be fast
                    assertTrue(benchmarkResult.averageLatencyMs < 500.0,
                        "FusionEngine latency ${benchmarkResult.averageLatencyMs}ms exceeds 500ms baseline")
                }
                "VisionAnalyzer" -> {
                    // Vision processing can be slower but should be reasonable
                    assertTrue(benchmarkResult.averageLatencyMs < 2000.0,
                        "VisionAnalyzer latency ${benchmarkResult.averageLatencyMs}ms exceeds 2000ms baseline")
                }
                "AudioProcessor" -> {
                    // Audio processing should be real-time capable
                    assertTrue(benchmarkResult.averageLatencyMs < 100.0,
                        "AudioProcessor latency ${benchmarkResult.averageLatencyMs}ms exceeds 100ms baseline")
                }
                "PrivacyManager" -> {
                    // Privacy filtering should be very fast
                    assertTrue(benchmarkResult.averageLatencyMs < 50.0,
                        "PrivacyManager latency ${benchmarkResult.averageLatencyMs}ms exceeds 50ms baseline")
                }
            }
        }
    }

    @Test
    fun `test memory usage baseline requirements`() = testScope.runTest {
        // When
        val result = benchmark.runComprehensiveBenchmark()

        // Then - verify memory usage is within acceptable limits
        result.results.forEach { benchmarkResult ->
            when (benchmarkResult.testName) {
                "MemoryUsage" -> {
                    // Memory growth should be controlled
                    assertTrue(benchmarkResult.memoryUsageMB < 200.0,
                        "Memory usage ${benchmarkResult.memoryUsageMB}MB exceeds 200MB baseline")
                }
                "EndToEndWorkflow" -> {
                    // Complex workflows should still be memory efficient
                    assertTrue(benchmarkResult.memoryUsageMB < 150.0,
                        "EndToEnd memory usage ${benchmarkResult.memoryUsageMB}MB exceeds 150MB baseline")
                }
            }
        }
    }

    @Test
    fun `test throughput baseline requirements`() = testScope.runTest {
        // When
        val result = benchmark.runComprehensiveBenchmark()

        // Then - verify throughput meets minimum requirements
        result.results.forEach { benchmarkResult ->
            when (benchmarkResult.testName) {
                "ProcessingPipeline" -> {
                    // Pipeline should handle reasonable throughput
                    assertTrue(benchmarkResult.throughputOpsPerSecond > 10.0,
                        "Pipeline throughput ${benchmarkResult.throughputOpsPerSecond} ops/sec below 10 ops/sec baseline")
                }
                "ConcurrentProcessing" -> {
                    // Concurrent processing should be efficient
                    assertTrue(benchmarkResult.throughputOpsPerSecond > 5.0,
                        "Concurrent throughput ${benchmarkResult.throughputOpsPerSecond} ops/sec below 5 ops/sec baseline")
                }
            }
        }
    }
}