package com.posecoach.app.multimodal.performance

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.lifecycle.LifecycleCoroutineScope
import com.posecoach.app.multimodal.MultiModalFusionEngine
import com.posecoach.app.multimodal.integration.LiveCoachMultiModalIntegration
import com.posecoach.app.multimodal.pipeline.MultiModalProcessingPipeline
import com.posecoach.app.privacy.EnhancedPrivacyManager
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import timber.log.Timber
import kotlin.math.*
import kotlin.system.measureTimeMillis

/**
 * Multi-Modal Performance Benchmark
 *
 * Comprehensive performance benchmarking and optimization system:
 * - Benchmarks individual component performance
 * - Tests system performance under various load conditions
 * - Identifies bottlenecks and optimization opportunities
 * - Provides performance regression testing
 * - Monitors real-time performance metrics
 */
class MultiModalPerformanceBenchmark(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val privacyManager: EnhancedPrivacyManager
) {

    companion object {
        private const val BENCHMARK_ITERATIONS = 100
        private const val STRESS_TEST_DURATION = 30000L // 30 seconds
        private const val WARMUP_ITERATIONS = 10
        private const val MEMORY_SAMPLING_INTERVAL = 1000L // 1 second
    }

    // Test API key for benchmarking
    private val benchmarkApiKey = "benchmark_test_key"

    @Serializable
    data class BenchmarkResult(
        val testName: String,
        val averageLatencyMs: Double,
        val minLatencyMs: Long,
        val maxLatencyMs: Long,
        val throughputOpsPerSecond: Double,
        val memoryUsageMB: Double,
        val cpuUsagePercent: Double,
        val successRate: Double,
        val p95LatencyMs: Double,
        val p99LatencyMs: Double,
        val deviceInfo: DeviceInfo,
        val timestamp: Long = System.currentTimeMillis()
    )

    @Serializable
    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val androidVersion: String,
        val apiLevel: Int,
        val totalMemoryMB: Long,
        val availableProcessors: Int
    )

    @Serializable
    data class StressTestResult(
        val testDurationMs: Long,
        val totalOperations: Long,
        val averageThroughput: Double,
        val memoryPeakMB: Double,
        val memoryLeakDetected: Boolean,
        val errorCount: Long,
        val performanceDegradation: Double,
        val systemStability: String
    )

    @Serializable
    data class OptimizationRecommendation(
        val component: String,
        val issue: String,
        val recommendation: String,
        val expectedImprovement: String,
        val priority: Priority
    )

    enum class Priority {
        CRITICAL, HIGH, MEDIUM, LOW
    }

    /**
     * Run comprehensive performance benchmark suite
     */
    suspend fun runComprehensiveBenchmark(): BenchmarkSuite = withContext(Dispatchers.Default) {
        Timber.i("Starting comprehensive multi-modal performance benchmark")

        val results = mutableListOf<BenchmarkResult>()

        try {
            // Individual component benchmarks
            results.add(benchmarkFusionEngine())
            results.add(benchmarkProcessingPipeline())
            results.add(benchmarkVisionAnalyzer())
            results.add(benchmarkAudioProcessor())
            results.add(benchmarkPrivacyManager())
            results.add(benchmarkGeminiIntegration())

            // Integration benchmarks
            results.add(benchmarkLiveCoachIntegration())
            results.add(benchmarkEndToEndWorkflow())

            // System benchmarks
            results.add(benchmarkConcurrentProcessing())
            results.add(benchmarkMemoryUsage())

            val stressTestResult = runStressTest()
            val optimizationRecommendations = generateOptimizationRecommendations(results, stressTestResult)

            BenchmarkSuite(
                results = results,
                stressTestResult = stressTestResult,
                optimizationRecommendations = optimizationRecommendations,
                deviceInfo = getDeviceInfo(),
                overallScore = calculateOverallScore(results),
                timestamp = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Timber.e(e, "Error running comprehensive benchmark")
            throw e
        }
    }

    /**
     * Benchmark MultiModalFusionEngine performance
     */
    private suspend fun benchmarkFusionEngine(): BenchmarkResult = withContext(Dispatchers.IO) {
        val fusionEngine = MultiModalFusionEngine(context, lifecycleScope, privacyManager)

        try {
            val latencies = mutableListOf<Long>()
            var successCount = 0

            // Warmup
            repeat(WARMUP_ITERATIONS) {
                val landmarks = createTestPoseLandmarks()
                fusionEngine.processPoseLandmarks(landmarks)
            }

            val memoryBefore = getCurrentMemoryUsage()

            // Benchmark iterations
            repeat(BENCHMARK_ITERATIONS) { iteration ->
                val landmarks = createTestPoseLandmarks()
                val bitmap = if (iteration % 3 == 0) createTestBitmap() else null

                val latency = measureTimeMillis {
                    try {
                        fusionEngine.processPoseLandmarks(landmarks, bitmap)
                        successCount++
                    } catch (e: Exception) {
                        Timber.w(e, "Fusion engine benchmark iteration $iteration failed")
                    }
                }

                latencies.add(latency)
            }

            val memoryAfter = getCurrentMemoryUsage()

            createBenchmarkResult(
                testName = "FusionEngine",
                latencies = latencies,
                successCount = successCount,
                totalIterations = BENCHMARK_ITERATIONS,
                memoryUsage = memoryAfter - memoryBefore
            )

        } finally {
            fusionEngine.shutdown()
        }
    }

    /**
     * Benchmark MultiModalProcessingPipeline performance
     */
    private suspend fun benchmarkProcessingPipeline(): BenchmarkResult = withContext(Dispatchers.IO) {
        val pipeline = MultiModalProcessingPipeline(context, lifecycleScope, privacyManager, benchmarkApiKey)

        try {
            pipeline.startPipeline()
            delay(1000L) // Allow pipeline to start

            val latencies = mutableListOf<Long>()
            var successCount = 0

            // Warmup
            repeat(WARMUP_ITERATIONS) {
                val landmarks = createTestPoseLandmarks()
                pipeline.submitPoseLandmarks(landmarks)
            }

            val memoryBefore = getCurrentMemoryUsage()

            // Benchmark iterations
            repeat(BENCHMARK_ITERATIONS) { iteration ->
                val landmarks = createTestPoseLandmarks()

                val latency = measureTimeMillis {
                    try {
                        pipeline.submitPoseLandmarks(landmarks)
                        successCount++
                    } catch (e: Exception) {
                        Timber.w(e, "Pipeline benchmark iteration $iteration failed")
                    }
                }

                latencies.add(latency)
            }

            val memoryAfter = getCurrentMemoryUsage()

            createBenchmarkResult(
                testName = "ProcessingPipeline",
                latencies = latencies,
                successCount = successCount,
                totalIterations = BENCHMARK_ITERATIONS,
                memoryUsage = memoryAfter - memoryBefore
            )

        } finally {
            pipeline.stopPipeline()
        }
    }

    /**
     * Benchmark AdvancedVisionAnalyzer performance
     */
    private suspend fun benchmarkVisionAnalyzer(): BenchmarkResult = withContext(Dispatchers.IO) {
        val visionAnalyzer = com.posecoach.app.multimodal.processors.AdvancedVisionAnalyzer(context)

        val latencies = mutableListOf<Long>()
        var successCount = 0

        // Test with different image sizes
        val imageSizes = listOf(
            Pair(320, 240),
            Pair(640, 480),
            Pair(1280, 720)
        )

        val memoryBefore = getCurrentMemoryUsage()

        imageSizes.forEach { (width, height) ->
            repeat(BENCHMARK_ITERATIONS / 3) { iteration ->
                val image = createTestBitmap(width, height)
                val landmarks = if (iteration % 2 == 0) createTestPoseLandmarks() else null

                val latency = measureTimeMillis {
                    try {
                        if (landmarks != null) {
                            visionAnalyzer.analyzeImage(image, landmarks)
                        } else {
                            visionAnalyzer.analyzeScene(image)
                        }
                        successCount++
                    } catch (e: Exception) {
                        Timber.w(e, "Vision analyzer benchmark failed for ${width}x${height}")
                    }
                }

                latencies.add(latency)
            }
        }

        val memoryAfter = getCurrentMemoryUsage()

        createBenchmarkResult(
            testName = "VisionAnalyzer",
            latencies = latencies,
            successCount = successCount,
            totalIterations = BENCHMARK_ITERATIONS,
            memoryUsage = memoryAfter - memoryBefore
        )
    }

    /**
     * Benchmark AudioIntelligenceProcessor performance
     */
    private suspend fun benchmarkAudioProcessor(): BenchmarkResult = withContext(Dispatchers.IO) {
        val audioProcessor = com.posecoach.app.multimodal.processors.AudioIntelligenceProcessor(context)

        val latencies = mutableListOf<Long>()
        var successCount = 0

        val memoryBefore = getCurrentMemoryUsage()

        repeat(BENCHMARK_ITERATIONS) { iteration ->
            val audioData = createTestAudioData(durationSeconds = 1f)
            val sampleRate = 44100
            val channels = 1

            val latency = measureTimeMillis {
                try {
                    audioProcessor.processAudioSignal(audioData, sampleRate, channels)
                    successCount++
                } catch (e: Exception) {
                    Timber.w(e, "Audio processor benchmark iteration $iteration failed")
                }
            }

            latencies.add(latency)
        }

        val memoryAfter = getCurrentMemoryUsage()

        createBenchmarkResult(
            testName = "AudioProcessor",
            latencies = latencies,
            successCount = successCount,
            totalIterations = BENCHMARK_ITERATIONS,
            memoryUsage = memoryAfter - memoryBefore
        )
    }

    /**
     * Benchmark MultiModalPrivacyManager performance
     */
    private suspend fun benchmarkPrivacyManager(): BenchmarkResult = withContext(Dispatchers.IO) {
        val privacyManagerMM = com.posecoach.app.multimodal.processors.MultiModalPrivacyManager(context, privacyManager)

        val latencies = mutableListOf<Long>()
        var successCount = 0

        val memoryBefore = getCurrentMemoryUsage()

        repeat(BENCHMARK_ITERATIONS) { iteration ->
            val multiModalInput = createTestMultiModalInput()

            val latency = measureTimeMillis {
                try {
                    privacyManagerMM.filterMultiModalInput(multiModalInput)
                    successCount++
                } catch (e: Exception) {
                    Timber.w(e, "Privacy manager benchmark iteration $iteration failed")
                }
            }

            latencies.add(latency)
        }

        val memoryAfter = getCurrentMemoryUsage()

        createBenchmarkResult(
            testName = "PrivacyManager",
            latencies = latencies,
            successCount = successCount,
            totalIterations = BENCHMARK_ITERATIONS,
            memoryUsage = memoryAfter - memoryBefore
        )
    }

    /**
     * Benchmark Gemini integration performance (with mock responses)
     */
    private suspend fun benchmarkGeminiIntegration(): BenchmarkResult = withContext(Dispatchers.IO) {
        // Note: This would typically require a test API key or mock implementation
        // For benchmarking purposes, we'll measure the setup and request preparation time

        val latencies = mutableListOf<Long>()
        var successCount = 0

        val memoryBefore = getCurrentMemoryUsage()

        repeat(BENCHMARK_ITERATIONS) { iteration ->
            val latency = measureTimeMillis {
                try {
                    // Simulate Gemini client initialization and request preparation
                    val client = com.posecoach.app.multimodal.enhanced.EnhancedGeminiMultiModalClient(
                        benchmarkApiKey, privacyManager,
                        com.posecoach.app.multimodal.processors.MultiModalPrivacyManager(context, privacyManager)
                    )
                    // We don't actually make API calls in benchmarks to avoid costs
                    successCount++
                } catch (e: Exception) {
                    Timber.w(e, "Gemini integration benchmark iteration $iteration failed")
                }
            }

            latencies.add(latency)
        }

        val memoryAfter = getCurrentMemoryUsage()

        createBenchmarkResult(
            testName = "GeminiIntegration",
            latencies = latencies,
            successCount = successCount,
            totalIterations = BENCHMARK_ITERATIONS,
            memoryUsage = memoryAfter - memoryBefore
        )
    }

    /**
     * Benchmark LiveCoachMultiModalIntegration performance
     */
    private suspend fun benchmarkLiveCoachIntegration(): BenchmarkResult = withContext(Dispatchers.IO) {
        // Mock LiveCoachManager for benchmarking
        val mockLiveCoachManager = createMockLiveCoachManager()

        val integration = LiveCoachMultiModalIntegration(
            context, lifecycleScope, mockLiveCoachManager, privacyManager, benchmarkApiKey
        )

        try {
            integration.startIntegration()
            delay(1000L) // Allow integration to start

            val latencies = mutableListOf<Long>()
            var successCount = 0

            val memoryBefore = getCurrentMemoryUsage()

            repeat(BENCHMARK_ITERATIONS) { iteration ->
                val landmarks = createTestPoseLandmarks()

                val latency = measureTimeMillis {
                    try {
                        integration.processPoseWithMultiModal(landmarks)
                        successCount++
                    } catch (e: Exception) {
                        Timber.w(e, "Integration benchmark iteration $iteration failed")
                    }
                }

                latencies.add(latency)
            }

            val memoryAfter = getCurrentMemoryUsage()

            createBenchmarkResult(
                testName = "LiveCoachIntegration",
                latencies = latencies,
                successCount = successCount,
                totalIterations = BENCHMARK_ITERATIONS,
                memoryUsage = memoryAfter - memoryBefore
            )

        } finally {
            integration.stopIntegration()
        }
    }

    /**
     * Benchmark end-to-end multi-modal workflow
     */
    private suspend fun benchmarkEndToEndWorkflow(): BenchmarkResult = withContext(Dispatchers.IO) {
        val fusionEngine = MultiModalFusionEngine(context, lifecycleScope, privacyManager)

        try {
            val latencies = mutableListOf<Long>()
            var successCount = 0

            val memoryBefore = getCurrentMemoryUsage()

            repeat(BENCHMARK_ITERATIONS / 2) { iteration -> // Fewer iterations for complex workflow
                val landmarks = createTestPoseLandmarks()
                val image = createTestBitmap()
                val audioData = createTestAudioData()

                val latency = measureTimeMillis {
                    try {
                        // Full multi-modal workflow
                        fusionEngine.processPoseLandmarks(landmarks, image)
                        fusionEngine.processAudioSignal(audioData, 44100, 1)
                        fusionEngine.processVisualScene(image, landmarks)
                        successCount++
                    } catch (e: Exception) {
                        Timber.w(e, "End-to-end benchmark iteration $iteration failed")
                    }
                }

                latencies.add(latency)
            }

            val memoryAfter = getCurrentMemoryUsage()

            createBenchmarkResult(
                testName = "EndToEndWorkflow",
                latencies = latencies,
                successCount = successCount,
                totalIterations = BENCHMARK_ITERATIONS / 2,
                memoryUsage = memoryAfter - memoryBefore
            )

        } finally {
            fusionEngine.shutdown()
        }
    }

    /**
     * Benchmark concurrent processing performance
     */
    private suspend fun benchmarkConcurrentProcessing(): BenchmarkResult = withContext(Dispatchers.IO) {
        val fusionEngine = MultiModalFusionEngine(context, lifecycleScope, privacyManager)

        try {
            val latencies = mutableListOf<Long>()
            var successCount = 0

            val memoryBefore = getCurrentMemoryUsage()

            repeat(BENCHMARK_ITERATIONS / 4) { iteration ->
                val latency = measureTimeMillis {
                    try {
                        // Process multiple inputs concurrently
                        val jobs = listOf(
                            async { fusionEngine.processPoseLandmarks(createTestPoseLandmarks()) },
                            async { fusionEngine.processAudioSignal(createTestAudioData(), 44100, 1) },
                            async { fusionEngine.processVisualScene(createTestBitmap()) },
                            async { fusionEngine.processPoseLandmarks(createTestPoseLandmarks(), createTestBitmap()) }
                        )

                        jobs.awaitAll()
                        successCount++
                    } catch (e: Exception) {
                        Timber.w(e, "Concurrent processing benchmark iteration $iteration failed")
                    }
                }

                latencies.add(latency)
            }

            val memoryAfter = getCurrentMemoryUsage()

            createBenchmarkResult(
                testName = "ConcurrentProcessing",
                latencies = latencies,
                successCount = successCount,
                totalIterations = BENCHMARK_ITERATIONS / 4,
                memoryUsage = memoryAfter - memoryBefore
            )

        } finally {
            fusionEngine.shutdown()
        }
    }

    /**
     * Benchmark memory usage patterns
     */
    private suspend fun benchmarkMemoryUsage(): BenchmarkResult = withContext(Dispatchers.IO) {
        val fusionEngine = MultiModalFusionEngine(context, lifecycleScope, privacyManager)

        try {
            val latencies = mutableListOf<Long>()
            var successCount = 0
            val memorySnapshots = mutableListOf<Long>()

            val memoryBefore = getCurrentMemoryUsage()

            repeat(BENCHMARK_ITERATIONS) { iteration ->
                val latency = measureTimeMillis {
                    try {
                        // Create and process data to test memory patterns
                        val landmarks = createTestPoseLandmarks()
                        val largeImage = createTestBitmap(1920, 1080)
                        val audioData = createTestAudioData(durationSeconds = 2f)

                        fusionEngine.processPoseLandmarks(landmarks, largeImage)
                        fusionEngine.processAudioSignal(audioData, 44100, 1)

                        // Sample memory usage
                        if (iteration % 10 == 0) {
                            memorySnapshots.add(getCurrentMemoryUsage())
                        }

                        successCount++
                    } catch (e: Exception) {
                        Timber.w(e, "Memory usage benchmark iteration $iteration failed")
                    }
                }

                latencies.add(latency)
            }

            val memoryAfter = getCurrentMemoryUsage()
            val memoryPeak = memorySnapshots.maxOrNull() ?: memoryAfter

            // Analyze memory patterns
            val memoryGrowth = memoryAfter - memoryBefore
            val memoryVariability = if (memorySnapshots.isNotEmpty()) {
                val mean = memorySnapshots.average()
                sqrt(memorySnapshots.map { (it - mean).pow(2) }.average())
            } else 0.0

            Timber.d("Memory analysis - Growth: ${memoryGrowth}MB, Peak: ${memoryPeak}MB, Variability: ${memoryVariability}")

            createBenchmarkResult(
                testName = "MemoryUsage",
                latencies = latencies,
                successCount = successCount,
                totalIterations = BENCHMARK_ITERATIONS,
                memoryUsage = memoryPeak - memoryBefore
            )

        } finally {
            fusionEngine.shutdown()
            System.gc() // Suggest garbage collection after memory test
        }
    }

    /**
     * Run stress test for system stability
     */
    private suspend fun runStressTest(): StressTestResult = withContext(Dispatchers.IO) {
        Timber.i("Starting stress test for ${STRESS_TEST_DURATION}ms")

        val fusionEngine = MultiModalFusionEngine(context, lifecycleScope, privacyManager)
        val startTime = System.currentTimeMillis()
        val endTime = startTime + STRESS_TEST_DURATION

        var totalOperations = 0L
        var errorCount = 0L
        val memorySnapshots = mutableListOf<Double>()
        val throughputSnapshots = mutableListOf<Double>()

        val memoryMonitoringJob = launch {
            while (System.currentTimeMillis() < endTime) {
                memorySnapshots.add(getCurrentMemoryUsage())
                delay(MEMORY_SAMPLING_INTERVAL)
            }
        }

        try {
            val initialMemory = getCurrentMemoryUsage()
            var lastThroughputCheck = startTime
            var operationsSinceLastCheck = 0L

            while (System.currentTimeMillis() < endTime) {
                try {
                    // Simulate realistic workload
                    val landmarks = createTestPoseLandmarks()
                    val image = if (totalOperations % 5 == 0L) createTestBitmap() else null
                    val audioData = if (totalOperations % 3 == 0L) createTestAudioData() else null

                    fusionEngine.processPoseLandmarks(landmarks, image)

                    if (audioData != null) {
                        fusionEngine.processAudioSignal(audioData, 44100, 1)
                    }

                    totalOperations++
                    operationsSinceLastCheck++

                    // Calculate throughput every 5 seconds
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastThroughputCheck >= 5000L) {
                        val throughput = operationsSinceLastCheck * 1000.0 / (currentTime - lastThroughputCheck)
                        throughputSnapshots.add(throughput)
                        lastThroughputCheck = currentTime
                        operationsSinceLastCheck = 0L
                    }

                } catch (e: Exception) {
                    errorCount++
                    Timber.w(e, "Stress test operation failed")
                }
            }

            memoryMonitoringJob.cancel()

            val finalMemory = getCurrentMemoryUsage()
            val actualDuration = System.currentTimeMillis() - startTime
            val averageThroughput = totalOperations * 1000.0 / actualDuration
            val memoryPeak = memorySnapshots.maxOrNull() ?: finalMemory
            val memoryLeakDetected = finalMemory > initialMemory * 1.5 // 50% increase indicates potential leak

            // Calculate performance degradation
            val performanceDegradation = if (throughputSnapshots.size >= 2) {
                val firstHalf = throughputSnapshots.take(throughputSnapshots.size / 2).average()
                val secondHalf = throughputSnapshots.drop(throughputSnapshots.size / 2).average()
                maxOf(0.0, (firstHalf - secondHalf) / firstHalf)
            } else 0.0

            val systemStability = when {
                errorCount > totalOperations * 0.1 -> "unstable"
                performanceDegradation > 0.2 -> "degraded"
                memoryLeakDetected -> "memory_leak"
                else -> "stable"
            }

            StressTestResult(
                testDurationMs = actualDuration,
                totalOperations = totalOperations,
                averageThroughput = averageThroughput,
                memoryPeakMB = memoryPeak,
                memoryLeakDetected = memoryLeakDetected,
                errorCount = errorCount,
                performanceDegradation = performanceDegradation,
                systemStability = systemStability
            )

        } finally {
            fusionEngine.shutdown()
        }
    }

    // Helper methods

    private fun createBenchmarkResult(
        testName: String,
        latencies: List<Long>,
        successCount: Int,
        totalIterations: Int,
        memoryUsage: Double
    ): BenchmarkResult {
        val sortedLatencies = latencies.sorted()
        val averageLatency = latencies.average()
        val throughput = if (averageLatency > 0) 1000.0 / averageLatency else 0.0

        return BenchmarkResult(
            testName = testName,
            averageLatencyMs = averageLatency,
            minLatencyMs = sortedLatencies.minOrNull() ?: 0L,
            maxLatencyMs = sortedLatencies.maxOrNull() ?: 0L,
            throughputOpsPerSecond = throughput,
            memoryUsageMB = memoryUsage,
            cpuUsagePercent = getCpuUsage(),
            successRate = successCount.toDouble() / totalIterations,
            p95LatencyMs = if (sortedLatencies.isNotEmpty()) sortedLatencies[(sortedLatencies.size * 0.95).toInt()] else 0.0,
            p99LatencyMs = if (sortedLatencies.isNotEmpty()) sortedLatencies[(sortedLatencies.size * 0.99).toInt()] else 0.0,
            deviceInfo = getDeviceInfo()
        )
    }

    private fun generateOptimizationRecommendations(
        results: List<BenchmarkResult>,
        stressTestResult: StressTestResult
    ): List<OptimizationRecommendation> {
        val recommendations = mutableListOf<OptimizationRecommendation>()

        results.forEach { result ->
            // High latency recommendations
            if (result.averageLatencyMs > 1000) {
                recommendations.add(OptimizationRecommendation(
                    component = result.testName,
                    issue = "High average latency (${result.averageLatencyMs.toInt()}ms)",
                    recommendation = "Consider optimizing processing algorithms or reducing data complexity",
                    expectedImprovement = "50-70% latency reduction",
                    priority = Priority.HIGH
                ))
            }

            // Low throughput recommendations
            if (result.throughputOpsPerSecond < 10) {
                recommendations.add(OptimizationRecommendation(
                    component = result.testName,
                    issue = "Low throughput (${result.throughputOpsPerSecond.toInt()} ops/sec)",
                    recommendation = "Implement parallel processing or batch optimization",
                    expectedImprovement = "2-3x throughput improvement",
                    priority = Priority.MEDIUM
                ))
            }

            // High memory usage recommendations
            if (result.memoryUsageMB > 100) {
                recommendations.add(OptimizationRecommendation(
                    component = result.testName,
                    issue = "High memory usage (${result.memoryUsageMB.toInt()}MB)",
                    recommendation = "Implement memory pooling and reduce object allocations",
                    expectedImprovement = "30-50% memory reduction",
                    priority = Priority.MEDIUM
                ))
            }

            // Low success rate recommendations
            if (result.successRate < 0.95) {
                recommendations.add(OptimizationRecommendation(
                    component = result.testName,
                    issue = "Low success rate (${(result.successRate * 100).toInt()}%)",
                    recommendation = "Improve error handling and input validation",
                    expectedImprovement = "Near 100% success rate",
                    priority = Priority.HIGH
                ))
            }
        }

        // Stress test specific recommendations
        if (stressTestResult.memoryLeakDetected) {
            recommendations.add(OptimizationRecommendation(
                component = "System",
                issue = "Memory leak detected during stress test",
                recommendation = "Profile memory usage and fix object retention issues",
                expectedImprovement = "Stable memory usage under load",
                priority = Priority.CRITICAL
            ))
        }

        if (stressTestResult.performanceDegradation > 0.2) {
            recommendations.add(OptimizationRecommendation(
                component = "System",
                issue = "Performance degradation under load (${(stressTestResult.performanceDegradation * 100).toInt()}%)",
                recommendation = "Implement backpressure handling and adaptive throttling",
                expectedImprovement = "Stable performance under sustained load",
                priority = Priority.HIGH
            ))
        }

        return recommendations
    }

    private fun calculateOverallScore(results: List<BenchmarkResult>): Double {
        if (results.isEmpty()) return 0.0

        val latencyScore = results.map { 1000.0 / maxOf(it.averageLatencyMs, 1.0) }.average()
        val throughputScore = results.map { minOf(it.throughputOpsPerSecond, 100.0) }.average()
        val successScore = results.map { it.successRate * 100 }.average()
        val memoryScore = results.map { maxOf(0.0, 100.0 - it.memoryUsageMB) }.average()

        return (latencyScore + throughputScore + successScore + memoryScore) / 4.0
    }

    private fun getCurrentMemoryUsage(): Double {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0) // MB
    }

    private fun getCpuUsage(): Double {
        // Simplified CPU usage estimation
        return 50.0 // Placeholder - would use actual CPU monitoring in production
    }

    private fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            totalMemoryMB = Runtime.getRuntime().maxMemory() / (1024 * 1024),
            availableProcessors = Runtime.getRuntime().availableProcessors()
        )
    }

    // Test data creation methods (same as in tests)
    private fun createTestPoseLandmarks(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.5f + (index * 0.01f),
                y = 0.5f + (index * 0.01f),
                z = 0f,
                visibility = 0.9f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            confidence = 0.8f,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun createTestBitmap(width: Int = 640, height: Int = 480): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    private fun createTestAudioData(durationSeconds: Float = 1f): ByteArray {
        val sampleRate = 44100
        val sampleCount = (sampleRate * durationSeconds).toInt()
        val audioData = ByteArray(sampleCount * 2)

        for (i in 0 until sampleCount) {
            val sample = (32767 * sin(2 * PI * 440 * i / sampleRate)).toInt().toShort()
            audioData[i * 2] = (sample and 0xFF).toByte()
            audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return audioData
    }

    private fun createTestMultiModalInput(): MultiModalFusionEngine.MultiModalInput {
        return MultiModalFusionEngine.MultiModalInput(
            timestamp = System.currentTimeMillis(),
            inputId = "benchmark_input",
            poseLandmarks = createTestPoseLandmarks(),
            visualContext = null,
            audioSignal = null,
            environmentContext = null,
            userContext = null
        )
    }

    private fun createMockLiveCoachManager(): com.posecoach.app.livecoach.LiveCoachManager {
        // Return a mock implementation for benchmarking
        return com.posecoach.app.livecoach.LiveCoachManager(context, lifecycleScope, benchmarkApiKey)
    }

    // Result container
    @Serializable
    data class BenchmarkSuite(
        val results: List<BenchmarkResult>,
        val stressTestResult: StressTestResult,
        val optimizationRecommendations: List<OptimizationRecommendation>,
        val deviceInfo: DeviceInfo,
        val overallScore: Double,
        val timestamp: Long
    )
}