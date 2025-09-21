package com.posecoach.testing.framework.performance

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * Performance testing orchestrator that monitors and validates app performance
 * Tracks metrics including:
 * - Method execution times
 * - Memory usage patterns
 * - CPU utilization
 * - Frame rate performance
 * - Network request latencies
 */
object PerformanceTestOrchestrator {

    private val methodPerformanceMap = ConcurrentHashMap<String, PerformanceMetrics>()
    private val memorySnapshots = mutableListOf<MemorySnapshot>()
    private val frameRateData = mutableListOf<FrameRateData>()
    private val networkLatencies = mutableListOf<NetworkLatency>()

    private var isInitialized = false
    private val startTime = AtomicLong(0)

    data class PerformanceMetrics(
        val methodName: String,
        val executionTimes: MutableList<Long> = mutableListOf(),
        val minTime: Long = Long.MAX_VALUE,
        val maxTime: Long = 0L,
        var avgTime: Long = 0L,
        var callCount: Int = 0
    )

    data class MemorySnapshot(
        val timestamp: Long,
        val heapUsed: Long,
        val heapMax: Long,
        val nativeHeap: Long,
        val context: String
    )

    data class FrameRateData(
        val timestamp: Long,
        val frameTime: Long,
        val fps: Float,
        val context: String
    )

    data class NetworkLatency(
        val timestamp: Long,
        val url: String,
        val latencyMs: Long,
        val payloadSize: Int
    )

    data class PerformanceReport(
        val totalExecutionTime: Long,
        val slowestMethods: List<PerformanceMetrics>,
        val memoryLeaks: List<MemoryLeak>,
        val frameRateIssues: List<FrameRateIssue>,
        val networkPerformance: NetworkPerformanceSummary,
        val overallScore: Float
    )

    data class MemoryLeak(
        val context: String,
        val leakSize: Long,
        val duration: Long
    )

    data class FrameRateIssue(
        val context: String,
        val averageFps: Float,
        val droppedFrames: Int
    )

    data class NetworkPerformanceSummary(
        val averageLatency: Long,
        val slowRequests: List<NetworkLatency>,
        val timeouts: Int
    )

    fun initialize() {
        if (isInitialized) return

        startTime.set(System.currentTimeMillis())
        isInitialized = true

        Timber.i("PerformanceTestOrchestrator initialized")
    }

    /**
     * Measure execution time of a method
     */
    inline fun <T> measureMethod(methodName: String, block: () -> T): T {
        var result: T
        val executionTime = measureTimeMillis {
            result = block()
        }

        recordMethodPerformance(methodName, executionTime)
        return result
    }

    /**
     * Measure execution time of a suspend method
     */
    suspend inline fun <T> measureSuspendMethod(methodName: String, crossinline block: suspend () -> T): T {
        val startTime = System.currentTimeMillis()
        val result = block()
        val executionTime = System.currentTimeMillis() - startTime

        recordMethodPerformance(methodName, executionTime)
        return result
    }

    /**
     * Record method performance metrics
     */
    fun recordMethodPerformance(methodName: String, executionTimeMs: Long) {
        val metrics = methodPerformanceMap.computeIfAbsent(methodName) {
            PerformanceMetrics(methodName)
        }

        synchronized(metrics) {
            metrics.executionTimes.add(executionTimeMs)
            metrics.callCount++

            if (executionTimeMs < metrics.minTime) {
                metrics.minTime = executionTimeMs
            }
            if (executionTimeMs > metrics.maxTime) {
                metrics.maxTime = executionTimeMs
            }

            metrics.avgTime = metrics.executionTimes.average().toLong()
        }
    }

    /**
     * Take a memory snapshot for leak detection
     */
    fun takeMemorySnapshot(context: String) {
        val runtime = Runtime.getRuntime()
        val snapshot = MemorySnapshot(
            timestamp = System.currentTimeMillis(),
            heapUsed = runtime.totalMemory() - runtime.freeMemory(),
            heapMax = runtime.maxMemory(),
            nativeHeap = android.os.Debug.getNativeHeapAllocatedSize(),
            context = context
        )

        synchronized(memorySnapshots) {
            memorySnapshots.add(snapshot)
        }

        Timber.d("Memory snapshot taken: $context - Heap: ${snapshot.heapUsed / 1024 / 1024}MB")
    }

    /**
     * Record frame rate data
     */
    fun recordFrameRate(context: String, frameTimeNanos: Long) {
        val frameTimeMs = frameTimeNanos / 1_000_000
        val fps = 1000f / frameTimeMs

        val frameData = FrameRateData(
            timestamp = System.currentTimeMillis(),
            frameTime = frameTimeMs,
            fps = fps,
            context = context
        )

        synchronized(frameRateData) {
            frameRateData.add(frameData)
        }

        if (fps < 30f) {
            Timber.w("Low frame rate detected: ${fps}fps in $context")
        }
    }

    /**
     * Record network request latency
     */
    fun recordNetworkLatency(url: String, latencyMs: Long, payloadSize: Int) {
        val latency = NetworkLatency(
            timestamp = System.currentTimeMillis(),
            url = url,
            latencyMs = latencyMs,
            payloadSize = payloadSize
        )

        synchronized(networkLatencies) {
            networkLatencies.add(latency)
        }

        if (latencyMs > 5000) {
            Timber.w("High network latency detected: ${latencyMs}ms for $url")
        }
    }

    /**
     * Generate comprehensive performance report
     */
    fun generatePerformanceReport(): PerformanceReport {
        val executionTime = System.currentTimeMillis() - startTime.get()

        val slowestMethods = methodPerformanceMap.values
            .sortedByDescending { it.avgTime }
            .take(10)

        val memoryLeaks = detectMemoryLeaks()
        val frameRateIssues = analyzeFrameRateIssues()
        val networkPerformance = analyzeNetworkPerformance()

        val overallScore = calculateOverallPerformanceScore(
            slowestMethods,
            memoryLeaks,
            frameRateIssues,
            networkPerformance
        )

        val report = PerformanceReport(
            totalExecutionTime = executionTime,
            slowestMethods = slowestMethods,
            memoryLeaks = memoryLeaks,
            frameRateIssues = frameRateIssues,
            networkPerformance = networkPerformance,
            overallScore = overallScore
        )

        logPerformanceReport(report)
        return report
    }

    private fun detectMemoryLeaks(): List<MemoryLeak> {
        val leaks = mutableListOf<MemoryLeak>()

        if (memorySnapshots.size < 2) return leaks

        val snapshots = memorySnapshots.sortedBy { it.timestamp }

        // Look for consistent memory increases without decreases
        for (i in 1 until snapshots.size) {
            val current = snapshots[i]
            val previous = snapshots[i - 1]

            val memoryIncrease = current.heapUsed - previous.heapUsed
            val duration = current.timestamp - previous.timestamp

            // Flag significant memory increases (>10MB) that persist
            if (memoryIncrease > 10 * 1024 * 1024 && duration > 5000) {
                leaks.add(
                    MemoryLeak(
                        context = "${previous.context} -> ${current.context}",
                        leakSize = memoryIncrease,
                        duration = duration
                    )
                )
            }
        }

        return leaks
    }

    private fun analyzeFrameRateIssues(): List<FrameRateIssue> {
        val issues = mutableListOf<FrameRateIssue>()

        val framesByContext = frameRateData.groupBy { it.context }

        framesByContext.forEach { (context, frames) ->
            val averageFps = frames.map { it.fps }.average().toFloat()
            val droppedFrames = frames.count { it.fps < 30f }

            if (averageFps < 45f || droppedFrames > 5) {
                issues.add(
                    FrameRateIssue(
                        context = context,
                        averageFps = averageFps,
                        droppedFrames = droppedFrames
                    )
                )
            }
        }

        return issues
    }

    private fun analyzeNetworkPerformance(): NetworkPerformanceSummary {
        val averageLatency = if (networkLatencies.isNotEmpty()) {
            networkLatencies.map { it.latencyMs }.average().toLong()
        } else 0L

        val slowRequests = networkLatencies.filter { it.latencyMs > 3000 }
        val timeouts = networkLatencies.count { it.latencyMs > 30000 }

        return NetworkPerformanceSummary(
            averageLatency = averageLatency,
            slowRequests = slowRequests,
            timeouts = timeouts
        )
    }

    private fun calculateOverallPerformanceScore(
        slowestMethods: List<PerformanceMetrics>,
        memoryLeaks: List<MemoryLeak>,
        frameRateIssues: List<FrameRateIssue>,
        networkPerformance: NetworkPerformanceSummary
    ): Float {
        var score = 100f

        // Deduct points for slow methods
        slowestMethods.forEach { method ->
            if (method.avgTime > 1000) score -= 10f
            else if (method.avgTime > 500) score -= 5f
            else if (method.avgTime > 100) score -= 2f
        }

        // Deduct points for memory leaks
        score -= memoryLeaks.size * 15f

        // Deduct points for frame rate issues
        score -= frameRateIssues.size * 10f

        // Deduct points for network performance
        if (networkPerformance.averageLatency > 3000) score -= 15f
        else if (networkPerformance.averageLatency > 1000) score -= 8f

        score -= networkPerformance.timeouts * 20f

        return maxOf(0f, score)
    }

    private fun logPerformanceReport(report: PerformanceReport) {
        Timber.i("=== PERFORMANCE REPORT ===")
        Timber.i("Total Execution Time: ${report.totalExecutionTime}ms")
        Timber.i("Overall Performance Score: %.1f/100", report.overallScore)

        if (report.slowestMethods.isNotEmpty()) {
            Timber.i("--- Slowest Methods ---")
            report.slowestMethods.take(5).forEach { method ->
                Timber.i("${method.methodName}: avg=${method.avgTime}ms (${method.callCount} calls)")
            }
        }

        if (report.memoryLeaks.isNotEmpty()) {
            Timber.w("--- Memory Leaks Detected ---")
            report.memoryLeaks.forEach { leak ->
                Timber.w("${leak.context}: ${leak.leakSize / 1024 / 1024}MB over ${leak.duration}ms")
            }
        }

        if (report.frameRateIssues.isNotEmpty()) {
            Timber.w("--- Frame Rate Issues ---")
            report.frameRateIssues.forEach { issue ->
                Timber.w("${issue.context}: avg=${issue.averageFps}fps, dropped=${issue.droppedFrames}")
            }
        }

        Timber.i("Network Performance: avg=${report.networkPerformance.averageLatency}ms")

        // Performance validation
        val meetsPerformanceRequirements = report.overallScore >= 80f
        if (meetsPerformanceRequirements) {
            Timber.i("✅ Performance requirements MET")
        } else {
            Timber.w("❌ Performance requirements NOT MET (score: %.1f/100)", report.overallScore)
        }
    }

    /**
     * Reset all performance tracking data
     */
    fun reset() {
        methodPerformanceMap.clear()
        memorySnapshots.clear()
        frameRateData.clear()
        networkLatencies.clear()
        startTime.set(System.currentTimeMillis())

        Timber.d("PerformanceTestOrchestrator reset")
    }

    fun shutdown() {
        generatePerformanceReport()
        reset()
        isInitialized = false

        Timber.i("PerformanceTestOrchestrator shutdown")
    }
}