package com.posecoach.analytics.benchmarking

import com.posecoach.analytics.engine.RealTimeAnalyticsEngine
import com.posecoach.analytics.pipeline.DataPipelineManager
import com.posecoach.analytics.visualization.VisualizationEngine
import com.posecoach.analytics.intelligence.BusinessIntelligenceEngine
import com.posecoach.analytics.models.*
import com.posecoach.analytics.interfaces.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive performance benchmarking system for analytics components
 * Measures latency, throughput, memory usage, and scalability under various loads
 */
@Singleton
class AnalyticsPerformanceBenchmark @Inject constructor(
    private val analyticsEngine: AnalyticsEngine,
    private val pipelineManager: DataPipelineManager,
    private val visualizationEngine: VisualizationEngine,
    private val businessIntelligence: BusinessIntelligenceEngine
) {

    private val benchmarkResults = mutableMapOf<String, BenchmarkResult>()
    private val performanceMetrics = PerformanceMetrics()

    /**
     * Run comprehensive performance benchmark suite
     */
    suspend fun runFullBenchmarkSuite(): BenchmarkSuiteResult {
        println("🚀 Starting Analytics Performance Benchmark Suite...")

        val results = mutableMapOf<String, BenchmarkResult>()

        // Core Analytics Benchmarks
        results["event_tracking_latency"] = benchmarkEventTrackingLatency()
        results["event_tracking_throughput"] = benchmarkEventTrackingThroughput()
        results["realtime_stream_performance"] = benchmarkRealtimeStreamPerformance()
        results["insight_generation_speed"] = benchmarkInsightGenerationSpeed()

        // Data Pipeline Benchmarks
        results["pipeline_ingestion_throughput"] = benchmarkPipelineIngestionThroughput()
        results["pipeline_processing_latency"] = benchmarkPipelineProcessingLatency()
        results["aggregation_performance"] = benchmarkAggregationPerformance()

        // Visualization Benchmarks
        results["chart_rendering_performance"] = benchmarkChartRenderingPerformance()
        results["3d_pose_rendering_performance"] = benchmark3DPoseRenderingPerformance()
        results["heatmap_generation_performance"] = benchmarkHeatmapGenerationPerformance()

        // Business Intelligence Benchmarks
        results["bi_metrics_generation_speed"] = benchmarkBIMetricsGenerationSpeed()
        results["churn_prediction_performance"] = benchmarkChurnPredictionPerformance()
        results["anomaly_detection_speed"] = benchmarkAnomalyDetectionSpeed()

        // Scalability Benchmarks
        results["concurrent_user_scalability"] = benchmarkConcurrentUserScalability()
        results["memory_usage_under_load"] = benchmarkMemoryUsageUnderLoad()
        results["system_stability_test"] = benchmarkSystemStabilityTest()

        val overallScore = calculateOverallPerformanceScore(results)

        return BenchmarkSuiteResult(
            timestamp = System.currentTimeMillis(),
            results = results,
            overallScore = overallScore,
            systemInfo = collectSystemInfo(),
            recommendations = generatePerformanceRecommendations(results)
        )
    }

    /**
     * Benchmark event tracking latency (should be < 100ms)
     */
    suspend fun benchmarkEventTrackingLatency(): BenchmarkResult {
        println("📊 Benchmarking event tracking latency...")

        val iterations = 1000
        val latencies = mutableListOf<Long>()

        repeat(iterations) {
            val event = createTestEvent()
            val latency = measureTimeMillis {
                runBlocking { analyticsEngine.trackEvent(event) }
            }
            latencies.add(latency)
        }

        val avgLatency = latencies.average()
        val p50Latency = latencies.sorted()[iterations / 2]
        val p95Latency = latencies.sorted()[(iterations * 0.95).toInt()]
        val p99Latency = latencies.sorted()[(iterations * 0.99).toInt()]

        val passed = avgLatency < 100 && p95Latency < 200 && p99Latency < 500

        return BenchmarkResult(
            testName = "event_tracking_latency",
            passed = passed,
            score = calculateLatencyScore(avgLatency, p95Latency, p99Latency),
            metrics = mapOf(
                "average_latency_ms" to avgLatency,
                "p50_latency_ms" to p50Latency.toDouble(),
                "p95_latency_ms" to p95Latency.toDouble(),
                "p99_latency_ms" to p99Latency.toDouble(),
                "iterations" to iterations.toDouble()
            ),
            duration = iterations * avgLatency.toLong(),
            details = "Average: ${avgLatency}ms, P95: ${p95Latency}ms, P99: ${p99Latency}ms"
        )
    }

    /**
     * Benchmark event tracking throughput (should be > 1000 events/sec)
     */
    suspend fun benchmarkEventTrackingThroughput(): BenchmarkResult {
        println("📊 Benchmarking event tracking throughput...")

        val testDuration = 10000L // 10 seconds
        val eventCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()

        // Generate events concurrently
        val jobs = (1..10).map {
            GlobalScope.launch {
                while (System.currentTimeMillis() - startTime < testDuration) {
                    analyticsEngine.trackEvent(createTestEvent())
                    eventCount.incrementAndGet()
                }
            }
        }

        jobs.joinAll()

        val actualDuration = System.currentTimeMillis() - startTime
        val eventsPerSecond = (eventCount.get() * 1000.0) / actualDuration

        val passed = eventsPerSecond > 1000

        return BenchmarkResult(
            testName = "event_tracking_throughput",
            passed = passed,
            score = calculateThroughputScore(eventsPerSecond, 1000.0),
            metrics = mapOf(
                "events_per_second" to eventsPerSecond,
                "total_events" to eventCount.get().toDouble(),
                "duration_ms" to actualDuration.toDouble()
            ),
            duration = actualDuration,
            details = "Throughput: ${eventsPerSecond.toInt()} events/sec over ${actualDuration}ms"
        )
    }

    /**
     * Benchmark real-time stream performance
     */
    suspend fun benchmarkRealtimeStreamPerformance(): BenchmarkResult {
        println("📊 Benchmarking real-time stream performance...")

        val streamData = mutableListOf<RealtimeAnalyticsData>()
        val startTime = System.currentTimeMillis()

        val job = launch {
            analyticsEngine.getRealtimeStream()
                .take(100)
                .collect { data ->
                    streamData.add(data)
                }
        }

        // Generate events to trigger stream updates
        repeat(200) {
            analyticsEngine.trackEvent(createTestEvent())
            delay(10)
        }

        job.join()

        val duration = System.currentTimeMillis() - startTime
        val avgLatency = streamData.map { it.latency }.average()
        val streamRate = (streamData.size * 1000.0) / duration

        val passed = avgLatency < 500 && streamRate > 10

        return BenchmarkResult(
            testName = "realtime_stream_performance",
            passed = passed,
            score = calculateStreamScore(avgLatency, streamRate),
            metrics = mapOf(
                "average_stream_latency_ms" to avgLatency,
                "stream_rate_per_second" to streamRate,
                "total_stream_events" to streamData.size.toDouble(),
                "test_duration_ms" to duration.toDouble()
            ),
            duration = duration,
            details = "Stream latency: ${avgLatency.toInt()}ms, Rate: ${streamRate.toInt()}/sec"
        )
    }

    /**
     * Benchmark insight generation speed
     */
    suspend fun benchmarkInsightGenerationSpeed(): BenchmarkResult {
        println("📊 Benchmarking insight generation speed...")

        val userId = "benchmark_user"
        val iterations = 50

        // Pre-populate with some data
        repeat(100) {
            analyticsEngine.trackUserPerformance(createTestUserPerformance(userId))
        }

        val latencies = mutableListOf<Long>()

        repeat(iterations) {
            val latency = measureTimeMillis {
                runBlocking { analyticsEngine.generateInsights(userId) }
            }
            latencies.add(latency)
        }

        val avgLatency = latencies.average()
        val passed = avgLatency < 1000 // Should generate insights in < 1 second

        return BenchmarkResult(
            testName = "insight_generation_speed",
            passed = passed,
            score = calculateLatencyScore(avgLatency, avgLatency, avgLatency),
            metrics = mapOf(
                "average_generation_time_ms" to avgLatency,
                "iterations" to iterations.toDouble()
            ),
            duration = (avgLatency * iterations).toLong(),
            details = "Average insight generation: ${avgLatency.toInt()}ms"
        )
    }

    /**
     * Benchmark pipeline ingestion throughput
     */
    suspend fun benchmarkPipelineIngestionThroughput(): BenchmarkResult {
        println("📊 Benchmarking pipeline ingestion throughput...")

        val testDuration = 5000L // 5 seconds
        val ingestedCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()

        val jobs = (1..5).map {
            GlobalScope.launch {
                while (System.currentTimeMillis() - startTime < testDuration) {
                    pipelineManager.ingestData(createTestEvent())
                    ingestedCount.incrementAndGet()
                }
            }
        }

        jobs.joinAll()

        val actualDuration = System.currentTimeMillis() - startTime
        val ingestionRate = (ingestedCount.get() * 1000.0) / actualDuration

        val passed = ingestionRate > 2000

        return BenchmarkResult(
            testName = "pipeline_ingestion_throughput",
            passed = passed,
            score = calculateThroughputScore(ingestionRate, 2000.0),
            metrics = mapOf(
                "ingestion_rate_per_second" to ingestionRate,
                "total_ingested" to ingestedCount.get().toDouble(),
                "duration_ms" to actualDuration.toDouble()
            ),
            duration = actualDuration,
            details = "Pipeline ingestion: ${ingestionRate.toInt()} items/sec"
        )
    }

    /**
     * Benchmark pipeline processing latency
     */
    suspend fun benchmarkPipelineProcessingLatency(): BenchmarkResult {
        println("📊 Benchmarking pipeline processing latency...")

        val processedData = mutableListOf<ProcessedData>()
        val startTime = System.currentTimeMillis()

        val job = launch {
            pipelineManager.startRealtimeProcessing()
                .take(50)
                .collect { data ->
                    processedData.add(data)
                }
        }

        // Ingest test data
        repeat(100) {
            pipelineManager.ingestData(createTestEvent())
            delay(20)
        }

        job.join()

        val avgProcessingTime = processedData.map { it.processingTime }.average()
        val passed = avgProcessingTime < 200 // Should process in < 200ms

        return BenchmarkResult(
            testName = "pipeline_processing_latency",
            passed = passed,
            score = calculateLatencyScore(avgProcessingTime, avgProcessingTime, avgProcessingTime),
            metrics = mapOf(
                "average_processing_time_ms" to avgProcessingTime,
                "processed_items" to processedData.size.toDouble()
            ),
            duration = System.currentTimeMillis() - startTime,
            details = "Average processing time: ${avgProcessingTime.toInt()}ms"
        )
    }

    /**
     * Benchmark aggregation performance
     */
    suspend fun benchmarkAggregationPerformance(): BenchmarkResult {
        println("📊 Benchmarking aggregation performance...")

        val timeWindow = 5000L // 5 seconds
        val iterations = 20

        // Pre-populate with data
        repeat(1000) {
            pipelineManager.ingestData(createTestUserPerformance("user_$it"))
        }

        val latencies = mutableListOf<Long>()

        repeat(iterations) {
            val latency = measureTimeMillis {
                runBlocking { pipelineManager.aggregateMetrics(timeWindow) }
            }
            latencies.add(latency)
        }

        val avgLatency = latencies.average()
        val passed = avgLatency < 500 // Should aggregate in < 500ms

        return BenchmarkResult(
            testName = "aggregation_performance",
            passed = passed,
            score = calculateLatencyScore(avgLatency, avgLatency, avgLatency),
            metrics = mapOf(
                "average_aggregation_time_ms" to avgLatency,
                "time_window_ms" to timeWindow.toDouble(),
                "iterations" to iterations.toDouble()
            ),
            duration = (avgLatency * iterations).toLong(),
            details = "Average aggregation: ${avgLatency.toInt()}ms for ${timeWindow}ms window"
        )
    }

    /**
     * Benchmark chart rendering performance
     */
    suspend fun benchmarkChartRenderingPerformance(): BenchmarkResult {
        println("📊 Benchmarking chart rendering performance...")

        val chartTypes = listOf(WidgetType.LINE_CHART, WidgetType.BAR_CHART, WidgetType.PIE_CHART)
        val dataSize = 1000
        val testData = (1..dataSize).map { Random.nextFloat() * 100 }

        val renderingTimes = mutableMapOf<WidgetType, Double>()

        chartTypes.forEach { chartType ->
            val times = mutableListOf<Long>()

            repeat(10) {
                val time = measureTimeMillis {
                    runBlocking { visualizationEngine.renderChart(chartType, testData) }
                }
                times.add(time)
            }

            renderingTimes[chartType] = times.average()
        }

        val avgRenderingTime = renderingTimes.values.average()
        val passed = avgRenderingTime < 1000 // Should render in < 1 second

        return BenchmarkResult(
            testName = "chart_rendering_performance",
            passed = passed,
            score = calculateLatencyScore(avgRenderingTime, avgRenderingTime, avgRenderingTime),
            metrics = renderingTimes.mapKeys { it.key.name } + mapOf(
                "average_rendering_time_ms" to avgRenderingTime,
                "data_points" to dataSize.toDouble()
            ),
            duration = (avgRenderingTime * chartTypes.size * 10).toLong(),
            details = "Average chart rendering: ${avgRenderingTime.toInt()}ms for $dataSize points"
        )
    }

    /**
     * Benchmark 3D pose rendering performance
     */
    suspend fun benchmark3DPoseRenderingPerformance(): BenchmarkResult {
        println("📊 Benchmarking 3D pose rendering performance...")

        val iterations = 20
        val poseData = createTestPoseData()

        val renderingTimes = mutableListOf<Long>()

        repeat(iterations) {
            val time = measureTimeMillis {
                runBlocking { visualizationEngine.render3DPose(poseData) }
            }
            renderingTimes.add(time)
        }

        val avgRenderingTime = renderingTimes.average()
        val passed = avgRenderingTime < 500 // Should render 3D pose in < 500ms

        return BenchmarkResult(
            testName = "3d_pose_rendering_performance",
            passed = passed,
            score = calculateLatencyScore(avgRenderingTime, avgRenderingTime, avgRenderingTime),
            metrics = mapOf(
                "average_rendering_time_ms" to avgRenderingTime,
                "iterations" to iterations.toDouble(),
                "joint_count" to poseData.joints.size.toDouble()
            ),
            duration = (avgRenderingTime * iterations).toLong(),
            details = "Average 3D pose rendering: ${avgRenderingTime.toInt()}ms"
        )
    }

    /**
     * Benchmark heatmap generation performance
     */
    suspend fun benchmarkHeatmapGenerationPerformance(): BenchmarkResult {
        println("📊 Benchmarking heatmap generation performance...")

        val dataSize = 100
        val testData = (1..dataSize).associate { "metric_$it" to Random.nextFloat() }

        val iterations = 15
        val generationTimes = mutableListOf<Long>()

        repeat(iterations) {
            val time = measureTimeMillis {
                runBlocking { visualizationEngine.generateHeatmap(testData) }
            }
            generationTimes.add(time)
        }

        val avgGenerationTime = generationTimes.average()
        val passed = avgGenerationTime < 300 // Should generate heatmap in < 300ms

        return BenchmarkResult(
            testName = "heatmap_generation_performance",
            passed = passed,
            score = calculateLatencyScore(avgGenerationTime, avgGenerationTime, avgGenerationTime),
            metrics = mapOf(
                "average_generation_time_ms" to avgGenerationTime,
                "data_points" to dataSize.toDouble(),
                "iterations" to iterations.toDouble()
            ),
            duration = (avgGenerationTime * iterations).toLong(),
            details = "Average heatmap generation: ${avgGenerationTime.toInt()}ms for $dataSize points"
        )
    }

    /**
     * Benchmark business intelligence metrics generation
     */
    suspend fun benchmarkBIMetricsGenerationSpeed(): BenchmarkResult {
        println("📊 Benchmarking BI metrics generation speed...")

        val iterations = 10
        val generationTimes = mutableListOf<Long>()

        repeat(iterations) {
            val time = measureTimeMillis {
                runBlocking { businessIntelligence.generateBusinessMetrics() }
            }
            generationTimes.add(time)
        }

        val avgGenerationTime = generationTimes.average()
        val passed = avgGenerationTime < 2000 // Should generate BI metrics in < 2 seconds

        return BenchmarkResult(
            testName = "bi_metrics_generation_speed",
            passed = passed,
            score = calculateLatencyScore(avgGenerationTime, avgGenerationTime, avgGenerationTime),
            metrics = mapOf(
                "average_generation_time_ms" to avgGenerationTime,
                "iterations" to iterations.toDouble()
            ),
            duration = (avgGenerationTime * iterations).toLong(),
            details = "Average BI metrics generation: ${avgGenerationTime.toInt()}ms"
        )
    }

    /**
     * Benchmark churn prediction performance
     */
    suspend fun benchmarkChurnPredictionPerformance(): BenchmarkResult {
        println("📊 Benchmarking churn prediction performance...")

        val timeframe = 7 * 24 * 60 * 60 * 1000L // 7 days
        val iterations = 5

        val predictionTimes = mutableListOf<Long>()

        repeat(iterations) {
            val time = measureTimeMillis {
                runBlocking { businessIntelligence.predictChurnRisk(timeframe) }
            }
            predictionTimes.add(time)
        }

        val avgPredictionTime = predictionTimes.average()
        val passed = avgPredictionTime < 5000 // Should predict churn in < 5 seconds

        return BenchmarkResult(
            testName = "churn_prediction_performance",
            passed = passed,
            score = calculateLatencyScore(avgPredictionTime, avgPredictionTime, avgPredictionTime),
            metrics = mapOf(
                "average_prediction_time_ms" to avgPredictionTime,
                "timeframe_ms" to timeframe.toDouble(),
                "iterations" to iterations.toDouble()
            ),
            duration = (avgPredictionTime * iterations).toLong(),
            details = "Average churn prediction: ${avgPredictionTime.toInt()}ms"
        )
    }

    /**
     * Benchmark anomaly detection speed
     */
    suspend fun benchmarkAnomalyDetectionSpeed(): BenchmarkResult {
        println("📊 Benchmarking anomaly detection speed...")

        val iterations = 10
        val detectionTimes = mutableListOf<Long>()

        repeat(iterations) {
            val time = measureTimeMillis {
                runBlocking { businessIntelligence.detectAnomalies() }
            }
            detectionTimes.add(time)
        }

        val avgDetectionTime = detectionTimes.average()
        val passed = avgDetectionTime < 1000 // Should detect anomalies in < 1 second

        return BenchmarkResult(
            testName = "anomaly_detection_speed",
            passed = passed,
            score = calculateLatencyScore(avgDetectionTime, avgDetectionTime, avgDetectionTime),
            metrics = mapOf(
                "average_detection_time_ms" to avgDetectionTime,
                "iterations" to iterations.toDouble()
            ),
            duration = (avgDetectionTime * iterations).toLong(),
            details = "Average anomaly detection: ${avgDetectionTime.toInt()}ms"
        )
    }

    /**
     * Benchmark concurrent user scalability
     */
    suspend fun benchmarkConcurrentUserScalability(): BenchmarkResult {
        println("📊 Benchmarking concurrent user scalability...")

        val userCounts = listOf(10, 50, 100, 200, 500)
        val eventsPerUser = 20
        val results = mutableMapOf<Int, Double>()

        userCounts.forEach { userCount ->
            val startTime = System.currentTimeMillis()

            val jobs = (1..userCount).map { userId ->
                GlobalScope.launch {
                    repeat(eventsPerUser) {
                        analyticsEngine.trackEvent(createTestEvent("user_$userId"))
                    }
                }
            }

            jobs.joinAll()

            val duration = System.currentTimeMillis() - startTime
            val eventsPerSecond = (userCount * eventsPerUser * 1000.0) / duration
            results[userCount] = eventsPerSecond

            println("  $userCount users: ${eventsPerSecond.toInt()} events/sec")
        }

        val maxThroughput = results.values.maxOrNull() ?: 0.0
        val passed = results[500]!! > 1000 // Should handle 500 users at > 1000 events/sec

        return BenchmarkResult(
            testName = "concurrent_user_scalability",
            passed = passed,
            score = calculateThroughputScore(maxThroughput, 2000.0),
            metrics = results.mapKeys { "users_${it.key}" } + mapOf(
                "max_throughput" to maxThroughput,
                "events_per_user" to eventsPerUser.toDouble()
            ),
            duration = 0L, // Calculated across multiple sub-tests
            details = "Max throughput: ${maxThroughput.toInt()} events/sec with ${userCounts.maxOrNull()} users"
        )
    }

    /**
     * Benchmark memory usage under load
     */
    suspend fun benchmarkMemoryUsageUnderLoad(): BenchmarkResult {
        println("📊 Benchmarking memory usage under load...")

        val runtime = Runtime.getRuntime()

        // Measure baseline memory
        runtime.gc()
        delay(1000)
        val baselineMemory = runtime.totalMemory() - runtime.freeMemory()

        // Generate load
        val eventCount = 10000
        repeat(eventCount) {
            analyticsEngine.trackEvent(createTestEvent())
            if (it % 1000 == 0) {
                delay(10) // Small delay to prevent overwhelming
            }
        }

        // Measure memory after load
        runtime.gc()
        delay(1000)
        val loadMemory = runtime.totalMemory() - runtime.freeMemory()

        val memoryIncrease = loadMemory - baselineMemory
        val memoryPerEvent = memoryIncrease.toDouble() / eventCount

        // Memory increase should be reasonable (< 1KB per event)
        val passed = memoryPerEvent < 1024

        return BenchmarkResult(
            testName = "memory_usage_under_load",
            passed = passed,
            score = calculateMemoryScore(memoryPerEvent),
            metrics = mapOf(
                "baseline_memory_mb" to (baselineMemory / 1024.0 / 1024.0),
                "load_memory_mb" to (loadMemory / 1024.0 / 1024.0),
                "memory_increase_mb" to (memoryIncrease / 1024.0 / 1024.0),
                "memory_per_event_bytes" to memoryPerEvent,
                "event_count" to eventCount.toDouble()
            ),
            duration = 0L,
            details = "Memory per event: ${memoryPerEvent.toInt()}bytes, Total increase: ${memoryIncrease / 1024 / 1024}MB"
        )
    }

    /**
     * Benchmark system stability under prolonged load
     */
    suspend fun benchmarkSystemStabilityTest(): BenchmarkResult {
        println("📊 Benchmarking system stability test...")

        val testDuration = 30000L // 30 seconds
        val errorCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()

        val job = GlobalScope.launch {
            while (System.currentTimeMillis() - startTime < testDuration) {
                try {
                    analyticsEngine.trackEvent(createTestEvent())
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
                delay(5) // 200 events/second
            }
        }

        job.join()

        val totalOperations = successCount.get() + errorCount.get()
        val errorRate = errorCount.get().toDouble() / totalOperations
        val passed = errorRate < 0.01 // Less than 1% error rate

        return BenchmarkResult(
            testName = "system_stability_test",
            passed = passed,
            score = calculateStabilityScore(errorRate),
            metrics = mapOf(
                "total_operations" to totalOperations.toDouble(),
                "successful_operations" to successCount.get().toDouble(),
                "failed_operations" to errorCount.get().toDouble(),
                "error_rate" to errorRate,
                "test_duration_ms" to testDuration.toDouble()
            ),
            duration = testDuration,
            details = "Error rate: ${(errorRate * 100).format(2)}% over ${testDuration}ms"
        )
    }

    // Scoring functions
    private fun calculateLatencyScore(avgLatency: Double, p95Latency: Double, p99Latency: Double): Double {
        val avgScore = max(0.0, 100.0 - (avgLatency / 10.0))
        val p95Score = max(0.0, 100.0 - (p95Latency / 20.0))
        val p99Score = max(0.0, 100.0 - (p99Latency / 50.0))
        return (avgScore * 0.5 + p95Score * 0.3 + p99Score * 0.2).coerceIn(0.0, 100.0)
    }

    private fun calculateThroughputScore(throughput: Double, baseline: Double): Double {
        return min(100.0, (throughput / baseline) * 100.0)
    }

    private fun calculateStreamScore(latency: Double, rate: Double): Double {
        val latencyScore = max(0.0, 100.0 - (latency / 10.0))
        val rateScore = min(100.0, rate * 5.0)
        return (latencyScore * 0.6 + rateScore * 0.4).coerceIn(0.0, 100.0)
    }

    private fun calculateMemoryScore(memoryPerEvent: Double): Double {
        return max(0.0, 100.0 - (memoryPerEvent / 100.0))
    }

    private fun calculateStabilityScore(errorRate: Double): Double {
        return max(0.0, 100.0 - (errorRate * 10000.0)) // 1% error = 0 score
    }

    private fun calculateOverallPerformanceScore(results: Map<String, BenchmarkResult>): Double {
        val weights = mapOf(
            "event_tracking_latency" to 0.15,
            "event_tracking_throughput" to 0.15,
            "realtime_stream_performance" to 0.12,
            "pipeline_ingestion_throughput" to 0.12,
            "concurrent_user_scalability" to 0.15,
            "memory_usage_under_load" to 0.10,
            "system_stability_test" to 0.21
        )

        var totalScore = 0.0
        var totalWeight = 0.0

        results.forEach { (testName, result) ->
            val weight = weights[testName] ?: 0.05
            totalScore += result.score * weight
            totalWeight += weight
        }

        return if (totalWeight > 0) totalScore / totalWeight else 0.0
    }

    private fun generatePerformanceRecommendations(results: Map<String, BenchmarkResult>): List<String> {
        val recommendations = mutableListOf<String>()

        results.forEach { (testName, result) ->
            if (!result.passed) {
                when (testName) {
                    "event_tracking_latency" -> recommendations.add("Optimize event processing pipeline to reduce latency")
                    "event_tracking_throughput" -> recommendations.add("Increase parallel processing capacity for event ingestion")
                    "realtime_stream_performance" -> recommendations.add("Optimize real-time data streaming and buffering")
                    "memory_usage_under_load" -> recommendations.add("Implement memory optimization and garbage collection tuning")
                    "system_stability_test" -> recommendations.add("Improve error handling and system resilience")
                    "concurrent_user_scalability" -> recommendations.add("Scale horizontally or optimize concurrent processing")
                }
            }
        }

        if (recommendations.isEmpty()) {
            recommendations.add("System performance is within acceptable ranges")
            recommendations.add("Consider implementing advanced caching strategies for further optimization")
        }

        return recommendations
    }

    private fun collectSystemInfo(): SystemInfo {
        val runtime = Runtime.getRuntime()
        return SystemInfo(
            availableProcessors = runtime.availableProcessors(),
            maxMemoryMB = runtime.maxMemory() / 1024 / 1024,
            totalMemoryMB = runtime.totalMemory() / 1024 / 1024,
            freeMemoryMB = runtime.freeMemory() / 1024 / 1024,
            javaVersion = System.getProperty("java.version"),
            osName = System.getProperty("os.name"),
            osVersion = System.getProperty("os.version")
        )
    }

    // Test data creation helpers
    private fun createTestEvent(userId: String = "test_user_${Random.nextInt()}"): AnalyticsEvent {
        return AnalyticsEvent(
            userId = userId,
            sessionId = "session_${Random.nextInt()}",
            timestamp = System.currentTimeMillis() / 1000,
            eventType = EventType.USER_ACTION,
            category = EventCategory.WORKOUT,
            properties = mapOf(
                "action" to "pose_detected",
                "accuracy" to Random.nextFloat(),
                "duration" to Random.nextInt(60)
            ),
            privacyLevel = PrivacyLevel.PSEUDONYMIZED
        )
    }

    private fun createTestUserPerformance(userId: String): UserPerformanceMetrics {
        return UserPerformanceMetrics(
            userId = userId,
            sessionId = "session_${Random.nextInt()}",
            timestamp = System.currentTimeMillis() / 1000,
            workoutType = "yoga",
            duration = Random.nextLong(600, 3600),
            poseAccuracy = Random.nextFloat() * 0.4f + 0.6f,
            energyExpenditure = Random.nextFloat() * 500 + 100,
            intensityLevel = IntensityLevel.values().random(),
            movementPatterns = emptyList(),
            personalBests = emptyList(),
            improvementRate = Random.nextFloat() * 0.2f + 0.05f
        )
    }

    private fun createTestPoseData(): PoseData {
        val joints = listOf(
            Joint3D("head", Vector3D(0f, 1.8f, 0f), 0.95f, true),
            Joint3D("neck", Vector3D(0f, 1.6f, 0f), 0.92f, true),
            Joint3D("left_shoulder", Vector3D(-0.3f, 1.5f, 0f), 0.88f, true),
            Joint3D("right_shoulder", Vector3D(0.3f, 1.5f, 0f), 0.90f, true)
        )

        return PoseData(
            joints = joints,
            timestamp = System.currentTimeMillis(),
            confidence = 0.89f,
            frameId = "frame_${Random.nextInt()}"
        )
    }

    // Extension function for number formatting
    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    // Data classes for benchmark results
    data class BenchmarkResult(
        val testName: String,
        val passed: Boolean,
        val score: Double, // 0-100
        val metrics: Map<String, Double>,
        val duration: Long, // milliseconds
        val details: String
    )

    data class BenchmarkSuiteResult(
        val timestamp: Long,
        val results: Map<String, BenchmarkResult>,
        val overallScore: Double,
        val systemInfo: SystemInfo,
        val recommendations: List<String>
    )

    data class SystemInfo(
        val availableProcessors: Int,
        val maxMemoryMB: Long,
        val totalMemoryMB: Long,
        val freeMemoryMB: Long,
        val javaVersion: String,
        val osName: String,
        val osVersion: String
    )

    data class PerformanceMetrics(
        val startTime: Long = System.currentTimeMillis(),
        val eventCount: AtomicLong = AtomicLong(0),
        val errorCount: AtomicLong = AtomicLong(0),
        val totalLatency: AtomicLong = AtomicLong(0)
    ) {
        fun recordEvent(latency: Long) {
            eventCount.incrementAndGet()
            totalLatency.addAndGet(latency)
        }

        fun recordError() {
            errorCount.incrementAndGet()
        }

        fun getAverageLatency(): Double {
            val events = eventCount.get()
            return if (events > 0) totalLatency.get().toDouble() / events else 0.0
        }

        fun getErrorRate(): Double {
            val total = eventCount.get() + errorCount.get()
            return if (total > 0) errorCount.get().toDouble() / total else 0.0
        }

        fun getThroughput(): Double {
            val duration = System.currentTimeMillis() - startTime
            return if (duration > 0) (eventCount.get() * 1000.0) / duration else 0.0
        }
    }
}