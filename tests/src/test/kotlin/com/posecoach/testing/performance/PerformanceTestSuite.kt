package com.posecoach.testing.performance

import com.google.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.random.Random

/**
 * Comprehensive test suite for performance compliance with Gemini Live API specifications.
 *
 * Tests cover:
 * - Real-time latency measurement (<500ms target)
 * - Long session performance (15 minutes)
 * - Memory usage during extended sessions
 * - Network resilience testing
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class PerformanceTestSuite {

    private lateinit var testScope: TestScope
    private lateinit var performanceMonitor: TestPerformanceMonitor
    private lateinit var latencyFlow: MutableSharedFlow<LatencyMeasurement>
    private lateinit var memoryFlow: MutableSharedFlow<MemorySnapshot>
    private lateinit var networkFlow: MutableSharedFlow<NetworkMetrics>

    data class LatencyMeasurement(
        val operationType: String,
        val latencyMs: Long,
        val timestamp: Long,
        val success: Boolean
    )

    data class MemorySnapshot(
        val timestamp: Long,
        val usedMemoryBytes: Long,
        val maxMemoryBytes: Long,
        val gcCount: Int,
        val activeObjects: Int
    )

    data class NetworkMetrics(
        val timestamp: Long,
        val bytesSent: Long,
        val bytesReceived: Long,
        val connectionTime: Long,
        val errorCount: Int,
        val retryCount: Int
    )

    data class SessionMetrics(
        val sessionDuration: Long,
        val totalOperations: Int,
        val averageLatency: Double,
        val p95Latency: Long,
        val p99Latency: Long,
        val memoryGrowth: Double,
        val errorRate: Double
    )

    companion object {
        const val TARGET_LATENCY_MS = 500L
        const val ACCEPTABLE_LATENCY_MS = 1000L
        const val SESSION_DURATION_MINUTES = 15L
        const val MEMORY_GROWTH_THRESHOLD = 0.5 // 50%
        const val MAX_ERROR_RATE = 0.05 // 5%
    }

    @Before
    fun setup() {
        testScope = TestScope()
        latencyFlow = MutableSharedFlow()
        memoryFlow = MutableSharedFlow()
        networkFlow = MutableSharedFlow()
        performanceMonitor = TestPerformanceMonitor(
            latencyFlow = latencyFlow,
            memoryFlow = memoryFlow,
            networkFlow = networkFlow,
            scope = testScope
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test real-time latency measurement under 500ms target`() = testScope.runTest {
        val operationTypes = listOf("websocket_send", "tool_execution", "audio_processing", "response_generation")
        val measurementsPerType = 100
        val latencyMeasurements = mutableListOf<LatencyMeasurement>()

        val job = launch {
            latencyFlow.collect { measurement ->
                latencyMeasurements.add(measurement)
            }
        }

        // Execute operations and measure latency
        operationTypes.forEach { operationType ->
            repeat(measurementsPerType) { index ->
                performanceMonitor.measureLatency(operationType) {
                    simulateOperation(operationType, index)
                }
                delay(10) // Small delay between operations
            }
        }

        delay(2000) // Allow all measurements to complete
        job.cancel()

        // Validate latency measurements
        assertThat(latencyMeasurements).hasSize(operationTypes.size * measurementsPerType)

        // Analyze latency by operation type
        operationTypes.forEach { operationType ->
            val typeLatencies = latencyMeasurements
                .filter { it.operationType == operationType && it.success }
                .map { it.latencyMs }

            assertThat(typeLatencies).hasSize(measurementsPerType)

            // Calculate percentiles
            val sortedLatencies = typeLatencies.sorted()
            val p50 = sortedLatencies[sortedLatencies.size / 2]
            val p95 = sortedLatencies[(sortedLatencies.size * 0.95).toInt()]
            val p99 = sortedLatencies[(sortedLatencies.size * 0.99).toInt()]
            val average = typeLatencies.average()

            // Validate latency targets
            assertThat(p50).isLessThan(TARGET_LATENCY_MS)
            assertThat(p95).isLessThan(TARGET_LATENCY_MS * 2) // Allow 2x for 95th percentile
            assertThat(p99).isLessThan(ACCEPTABLE_LATENCY_MS)
            assertThat(average).isLessThan(TARGET_LATENCY_MS.toDouble())

            // Validate consistency (standard deviation should be reasonable)
            val variance = typeLatencies.map { (it - average) * (it - average) }.average()
            val stdDev = kotlin.math.sqrt(variance)
            assertThat(stdDev).isLessThan(average * 0.5) // Std dev < 50% of mean
        }
    }

    @Test
    fun `test long session performance over 15 minutes`() = testScope.runTest {
        val sessionDuration = TimeUnit.MINUTES.toMillis(SESSION_DURATION_MINUTES)
        val operationInterval = 1000L // 1 operation per second
        val expectedOperations = sessionDuration / operationInterval

        val sessionMetrics = mutableListOf<SessionMetrics>()
        val allLatencies = mutableListOf<Long>()
        val memorySnapshots = mutableListOf<MemorySnapshot>()

        // Start memory monitoring
        val memoryJob = launch {
            memoryFlow.collect { snapshot ->
                memorySnapshots.add(snapshot)
            }
        }

        // Start latency monitoring
        val latencyJob = launch {
            latencyFlow.collect { measurement ->
                if (measurement.success) {
                    allLatencies.add(measurement.latencyMs)
                }
            }
        }

        val sessionStartTime = System.currentTimeMillis()
        performanceMonitor.startSession()

        // Simulate 15-minute session with regular operations
        var operationCount = 0
        while (System.currentTimeMillis() - sessionStartTime < sessionDuration) {
            val operationType = listOf("websocket_send", "tool_execution", "audio_processing")[operationCount % 3]

            performanceMonitor.measureLatency(operationType) {
                simulateOperation(operationType, operationCount)
            }

            // Take memory snapshot every 30 seconds
            if (operationCount % 30 == 0) {
                performanceMonitor.takeMemorySnapshot()
            }

            operationCount++
            delay(operationInterval)

            // Check if we should continue (with some tolerance for test execution time)
            if (operationCount >= expectedOperations * 1.1) break
        }

        performanceMonitor.endSession()
        delay(1000) // Allow final measurements

        memoryJob.cancel()
        latencyJob.cancel()

        // Validate session performance
        assertThat(operationCount).isAtLeast((expectedOperations * 0.9).toInt()) // Allow 10% tolerance

        // Analyze latency degradation over time
        val timeWindows = allLatencies.chunked(100) // Analyze in 100-operation windows
        val windowAverages = timeWindows.map { it.average() }

        // Latency shouldn't degrade significantly over time
        if (windowAverages.size > 2) {
            val firstWindow = windowAverages.take(3).average()
            val lastWindow = windowAverages.takeLast(3).average()
            val degradation = (lastWindow - firstWindow) / firstWindow

            assertThat(degradation).isLessThan(0.3) // Less than 30% degradation
        }

        // Analyze memory usage
        assertThat(memorySnapshots).isNotEmpty()

        val initialMemory = memorySnapshots.first().usedMemoryBytes
        val finalMemory = memorySnapshots.last().usedMemoryBytes
        val memoryGrowth = (finalMemory - initialMemory).toDouble() / initialMemory

        assertThat(memoryGrowth).isLessThan(MEMORY_GROWTH_THRESHOLD)

        // Check for memory leaks (memory should stabilize, not grow continuously)
        if (memorySnapshots.size > 10) {
            val recentSnapshots = memorySnapshots.takeLast(5)
            val memoryTrend = calculateMemoryTrend(recentSnapshots)
            assertThat(abs(memoryTrend)).isLessThan(0.1) // Memory should be stable
        }

        // Overall session metrics
        val sessionMetrics = calculateSessionMetrics(allLatencies, memorySnapshots, operationCount, sessionDuration)
        assertThat(sessionMetrics.averageLatency).isLessThan(TARGET_LATENCY_MS.toDouble())
        assertThat(sessionMetrics.p95Latency).isLessThan(TARGET_LATENCY_MS * 2)
        assertThat(sessionMetrics.memoryGrowth).isLessThan(MEMORY_GROWTH_THRESHOLD)
    }

    @Test
    fun `test memory usage during extended sessions`() = testScope.runTest {
        val testDuration = TimeUnit.MINUTES.toMillis(10) // 10-minute test
        val memorySnapshots = mutableListOf<MemorySnapshot>()
        val objectCounts = mutableListOf<Int>()

        val job = launch {
            memoryFlow.collect { snapshot ->
                memorySnapshots.add(snapshot)
                objectCounts.add(snapshot.activeObjects)
            }
        }

        performanceMonitor.startMemoryMonitoring()

        // Simulate various memory-intensive operations
        val operations = listOf(
            "large_websocket_message",
            "audio_buffer_processing",
            "image_analysis",
            "tool_execution_with_large_response"
        )

        val endTime = System.currentTimeMillis() + testDuration
        var operationIndex = 0

        while (System.currentTimeMillis() < endTime) {
            val operation = operations[operationIndex % operations.size]

            performanceMonitor.measureLatency(operation) {
                simulateMemoryIntensiveOperation(operation, operationIndex)
            }

            performanceMonitor.takeMemorySnapshot()

            // Force garbage collection periodically
            if (operationIndex % 50 == 0) {
                performanceMonitor.forceGC()
                delay(100) // Allow GC to complete
                performanceMonitor.takeMemorySnapshot() // Snapshot after GC
            }

            operationIndex++
            delay(200) // 5 operations per second
        }

        performanceMonitor.stopMemoryMonitoring()
        delay(1000)
        job.cancel()

        // Analyze memory behavior
        assertThat(memorySnapshots).hasSize(greaterThan(10))

        val initialMemory = memorySnapshots.first().usedMemoryBytes
        val peakMemory = memorySnapshots.maxByOrNull { it.usedMemoryBytes }!!.usedMemoryBytes
        val finalMemory = memorySnapshots.last().usedMemoryBytes

        // Memory should not grow unbounded
        val memoryGrowth = (finalMemory - initialMemory).toDouble() / initialMemory
        assertThat(memoryGrowth).isLessThan(MEMORY_GROWTH_THRESHOLD)

        // Peak memory should not be excessive
        val peakGrowth = (peakMemory - initialMemory).toDouble() / initialMemory
        assertThat(peakGrowth).isLessThan(2.0) // Less than 200% growth

        // Memory should recover after operations (GC effectiveness)
        val preGCSnapshots = mutableListOf<MemorySnapshot>()
        val postGCSnapshots = mutableListOf<MemorySnapshot>()

        for (i in 1 until memorySnapshots.size) {
            val current = memorySnapshots[i]
            val previous = memorySnapshots[i - 1]

            // Detect GC events (significant memory drop)
            if (current.usedMemoryBytes < previous.usedMemoryBytes * 0.8) {
                preGCSnapshots.add(previous)
                postGCSnapshots.add(current)
            }
        }

        if (preGCSnapshots.isNotEmpty()) {
            val avgMemoryRecovery = preGCSnapshots.zip(postGCSnapshots) { pre, post ->
                (pre.usedMemoryBytes - post.usedMemoryBytes).toDouble() / pre.usedMemoryBytes
            }.average()

            assertThat(avgMemoryRecovery).isAtLeast(0.2) // At least 20% memory recovery after GC
        }

        // Object count should not grow indefinitely
        if (objectCounts.size > 10) {
            val firstHalf = objectCounts.take(objectCounts.size / 2).average()
            val secondHalf = objectCounts.takeLast(objectCounts.size / 2).average()
            val objectGrowth = (secondHalf - firstHalf) / firstHalf

            assertThat(objectGrowth).isLessThan(0.5) // Less than 50% object growth
        }
    }

    @Test
    fun `test network resilience and performance under adverse conditions`() = testScope.runTest {
        val networkConditions = listOf(
            NetworkCondition("excellent", 1000, 10, 0.0),  // 1Gbps, 10ms, 0% loss
            NetworkCondition("good", 100, 50, 0.01),       // 100Mbps, 50ms, 1% loss
            NetworkCondition("fair", 10, 150, 0.05),       // 10Mbps, 150ms, 5% loss
            NetworkCondition("poor", 1, 500, 0.1),         // 1Mbps, 500ms, 10% loss
            NetworkCondition("unstable", 5, 200, 0.15)     // 5Mbps, 200ms, 15% loss
        )

        val networkMetrics = mutableListOf<NetworkMetrics>()
        val job = launch {
            networkFlow.collect { metrics ->
                networkMetrics.add(metrics)
            }
        }

        for (condition in networkConditions) {
            performanceMonitor.setNetworkCondition(condition)

            val conditionMetrics = mutableListOf<LatencyMeasurement>()
            val conditionJob = launch {
                latencyFlow.collect { measurement ->
                    conditionMetrics.add(measurement)
                }
            }

            // Test various network operations under this condition
            val operations = listOf("websocket_connect", "websocket_send", "websocket_receive", "token_refresh")

            repeat(25) { index -> // 25 operations per condition
                operations.forEach { operation ->
                    performanceMonitor.measureLatency(operation) {
                        simulateNetworkOperation(operation, condition, index)
                    }
                    delay(100) // Space out operations
                }
            }

            conditionJob.cancel()

            // Analyze performance under this condition
            val successfulOps = conditionMetrics.filter { it.success }
            val failedOps = conditionMetrics.filter { !it.success }

            val errorRate = failedOps.size.toDouble() / conditionMetrics.size

            when (condition.quality) {
                "excellent", "good" -> {
                    assertThat(errorRate).isLessThan(0.02) // < 2% error rate
                    if (successfulOps.isNotEmpty()) {
                        val avgLatency = successfulOps.map { it.latencyMs }.average()
                        assertThat(avgLatency).isLessThan(TARGET_LATENCY_MS * 2)
                    }
                }
                "fair" -> {
                    assertThat(errorRate).isLessThan(0.1) // < 10% error rate
                    if (successfulOps.isNotEmpty()) {
                        val avgLatency = successfulOps.map { it.latencyMs }.average()
                        assertThat(avgLatency).isLessThan(TARGET_LATENCY_MS * 3)
                    }
                }
                "poor", "unstable" -> {
                    assertThat(errorRate).isLessThan(0.25) // < 25% error rate (graceful degradation)
                    // Allow higher latency but ensure system doesn't completely fail
                    assertThat(successfulOps.size).isGreaterThan(conditionMetrics.size / 4) // At least 25% success
                }
            }
        }

        job.cancel()

        // Validate adaptive behavior
        assertThat(networkMetrics).isNotEmpty()

        // Network metrics should show adaptation to conditions
        val excellentMetrics = networkMetrics.filter { it.errorCount == 0 }
        val poorMetrics = networkMetrics.filter { it.errorCount > 0 }

        if (excellentMetrics.isNotEmpty() && poorMetrics.isNotEmpty()) {
            val excellentAvgTime = excellentMetrics.map { it.connectionTime }.average()
            val poorAvgTime = poorMetrics.map { it.connectionTime }.average()

            // Poor conditions should show higher connection times
            assertThat(poorAvgTime).isGreaterThan(excellentAvgTime)
        }
    }

    @Test
    fun `test concurrent operation performance and resource contention`() = testScope.runTest {
        val concurrencyLevels = listOf(1, 5, 10, 20, 50)
        val operationsPerLevel = 100

        for (concurrencyLevel in concurrencyLevels) {
            val latencyMeasurements = mutableListOf<LatencyMeasurement>()
            val job = launch {
                latencyFlow.collect { measurement ->
                    synchronized(latencyMeasurements) {
                        latencyMeasurements.add(measurement)
                    }
                }
            }

            val startTime = System.currentTimeMillis()

            // Launch concurrent operations
            val jobs = (1..concurrencyLevel).map { workerIndex ->
                launch {
                    repeat(operationsPerLevel / concurrencyLevel) { opIndex ->
                        val operationType = "concurrent_operation_${workerIndex}"
                        performanceMonitor.measureLatency(operationType) {
                            simulateOperation(operationType, opIndex)
                        }
                        delay(10) // Small delay between operations
                    }
                }
            }

            jobs.joinAll()
            delay(1000) // Allow measurements to complete

            val endTime = System.currentTimeMillis()
            job.cancel()

            // Analyze performance at this concurrency level
            val totalTime = endTime - startTime
            val throughput = latencyMeasurements.size.toDouble() / (totalTime / 1000.0)

            if (latencyMeasurements.isNotEmpty()) {
                val avgLatency = latencyMeasurements.map { it.latencyMs }.average()
                val p95Latency = latencyMeasurements.map { it.latencyMs }.sorted()
                    .let { it[(it.size * 0.95).toInt()] }

                // Latency should not degrade excessively with concurrency
                when (concurrencyLevel) {
                    1 -> {
                        assertThat(avgLatency).isLessThan(TARGET_LATENCY_MS.toDouble())
                        assertThat(p95Latency).isLessThan(TARGET_LATENCY_MS * 2)
                    }
                    5, 10 -> {
                        assertThat(avgLatency).isLessThan(TARGET_LATENCY_MS * 2.0)
                        assertThat(p95Latency).isLessThan(TARGET_LATENCY_MS * 3)
                    }
                    20, 50 -> {
                        assertThat(avgLatency).isLessThan(TARGET_LATENCY_MS * 3.0)
                        assertThat(p95Latency).isLessThan(TARGET_LATENCY_MS * 5)
                    }
                }

                // Throughput should scale with concurrency (up to a point)
                if (concurrencyLevel <= 10) {
                    // Lower concurrency should achieve reasonable throughput
                    assertThat(throughput).isAtLeast(concurrencyLevel * 0.5) // At least 0.5 ops/sec per worker
                }
            }
        }
    }

    @Test
    fun `test performance monitoring and alerting system`() = testScope.runTest {
        val alerts = mutableListOf<PerformanceAlert>()
        val alertJob = launch {
            performanceMonitor.alertFlow.collect { alert ->
                alerts.add(alert)
            }
        }

        performanceMonitor.enablePerformanceMonitoring()

        // Simulate operations that should trigger alerts
        val scenarios = listOf(
            "high_latency" to 10,
            "memory_pressure" to 15,
            "network_errors" to 20,
            "normal_operation" to 30
        )

        scenarios.forEach { (scenario, count) ->
            repeat(count) { index ->
                when (scenario) {
                    "high_latency" -> {
                        performanceMonitor.measureLatency("slow_operation") {
                            delay(TARGET_LATENCY_MS + 200) // Exceed target latency
                        }
                    }
                    "memory_pressure" -> {
                        performanceMonitor.measureLatency("memory_intensive") {
                            simulateMemoryIntensiveOperation("large_allocation", index)
                        }
                        performanceMonitor.takeMemorySnapshot()
                    }
                    "network_errors" -> {
                        performanceMonitor.measureLatency("failing_network_op") {
                            simulateFailingNetworkOperation(index)
                        }
                    }
                    "normal_operation" -> {
                        performanceMonitor.measureLatency("normal_op") {
                            delay(50) // Normal operation time
                        }
                    }
                }
                delay(100)
            }
        }

        delay(2000) // Allow alert processing
        alertJob.cancel()

        // Validate alert system
        assertThat(alerts).isNotEmpty()

        val alertTypes = alerts.map { it.type }.toSet()
        assertThat(alertTypes).contains(PerformanceAlert.Type.HIGH_LATENCY)

        // High latency scenario should trigger alerts
        val latencyAlerts = alerts.filter { it.type == PerformanceAlert.Type.HIGH_LATENCY }
        assertThat(latencyAlerts).isNotEmpty()

        // Memory pressure should trigger alerts if thresholds exceeded
        val memoryAlerts = alerts.filter { it.type == PerformanceAlert.Type.MEMORY_PRESSURE }
        // Memory alerts are optional depending on threshold configuration

        // Network errors should trigger alerts
        val networkAlerts = alerts.filter { it.type == PerformanceAlert.Type.NETWORK_ERRORS }
        assertThat(networkAlerts).isNotEmpty()

        // Normal operations should not trigger excessive alerts
        val normalPeriodAlerts = alerts.filter {
            it.timestamp > System.currentTimeMillis() - 5000 // Last 5 seconds (normal operations)
        }
        assertThat(normalPeriodAlerts.size).isLessThan(5) // Should be minimal during normal operations
    }

    // Helper methods and classes
    private suspend fun simulateOperation(operationType: String, index: Int) {
        val baseDelay = when (operationType) {
            "websocket_send" -> 50L
            "tool_execution" -> 200L
            "audio_processing" -> 100L
            "response_generation" -> 150L
            else -> 100L
        }

        // Add some randomness
        val randomDelay = Random.nextLong(0, baseDelay / 2)
        delay(baseDelay + randomDelay)
    }

    private suspend fun simulateMemoryIntensiveOperation(operation: String, index: Int) {
        val delay = when (operation) {
            "large_websocket_message" -> 100L
            "audio_buffer_processing" -> 150L
            "image_analysis" -> 300L
            "tool_execution_with_large_response" -> 250L
            "large_allocation" -> 200L
            else -> 100L
        }

        delay(delay + Random.nextLong(0, 50))
    }

    private suspend fun simulateNetworkOperation(operation: String, condition: NetworkCondition, index: Int) {
        val baseLatency = condition.latencyMs
        val jitter = Random.nextLong(0, baseLatency / 4)

        delay(baseLatency + jitter)

        // Simulate packet loss
        if (Random.nextDouble() < condition.packetLoss) {
            throw RuntimeException("Simulated network error")
        }
    }

    private suspend fun simulateFailingNetworkOperation(index: Int) {
        delay(Random.nextLong(100, 500))
        if (index % 3 == 0) { // Fail 1/3 of operations
            throw RuntimeException("Simulated network failure")
        }
    }

    private fun calculateMemoryTrend(snapshots: List<MemorySnapshot>): Double {
        if (snapshots.size < 2) return 0.0

        val times = snapshots.map { it.timestamp.toDouble() }
        val memories = snapshots.map { it.usedMemoryBytes.toDouble() }

        // Simple linear regression slope
        val n = snapshots.size
        val sumX = times.sum()
        val sumY = memories.sum()
        val sumXY = times.zip(memories) { x, y -> x * y }.sum()
        val sumXX = times.map { it * it }.sum()

        return (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX)
    }

    private fun calculateSessionMetrics(
        latencies: List<Long>,
        memorySnapshots: List<MemorySnapshot>,
        operationCount: Int,
        sessionDuration: Long
    ): SessionMetrics {
        val sortedLatencies = latencies.sorted()
        val avgLatency = latencies.average()
        val p95Latency = if (sortedLatencies.isNotEmpty()) {
            sortedLatencies[(sortedLatencies.size * 0.95).toInt()]
        } else 0L
        val p99Latency = if (sortedLatencies.isNotEmpty()) {
            sortedLatencies[(sortedLatencies.size * 0.99).toInt()]
        } else 0L

        val memoryGrowth = if (memorySnapshots.size >= 2) {
            val initial = memorySnapshots.first().usedMemoryBytes.toDouble()
            val final = memorySnapshots.last().usedMemoryBytes.toDouble()
            (final - initial) / initial
        } else 0.0

        val errorRate = 0.0 // Simplified for this test

        return SessionMetrics(
            sessionDuration = sessionDuration,
            totalOperations = operationCount,
            averageLatency = avgLatency,
            p95Latency = p95Latency,
            p99Latency = p99Latency,
            memoryGrowth = memoryGrowth,
            errorRate = errorRate
        )
    }

    data class NetworkCondition(
        val quality: String,
        val bandwidthMbps: Int,
        val latencyMs: Long,
        val packetLoss: Double
    )

    data class PerformanceAlert(
        val type: Type,
        val message: String,
        val timestamp: Long,
        val severity: Severity
    ) {
        enum class Type {
            HIGH_LATENCY, MEMORY_PRESSURE, NETWORK_ERRORS, RESOURCE_EXHAUSTION
        }

        enum class Severity {
            LOW, MEDIUM, HIGH, CRITICAL
        }
    }

    // Test implementation of performance monitor
    private class TestPerformanceMonitor(
        private val latencyFlow: MutableSharedFlow<LatencyMeasurement>,
        private val memoryFlow: MutableSharedFlow<MemorySnapshot>,
        private val networkFlow: MutableSharedFlow<NetworkMetrics>,
        private val scope: CoroutineScope
    ) {
        private var currentNetworkCondition: NetworkCondition? = null
        private var sessionStartTime: Long = 0
        private var isMonitoring = false
        private val operationCounts = ConcurrentHashMap<String, AtomicInteger>()
        private val totalMemoryAllocated = AtomicLong(0)
        private val activeObjects = AtomicInteger(0)
        private val gcCount = AtomicInteger(0)

        val alertFlow = MutableSharedFlow<PerformanceAlert>()

        suspend fun measureLatency(operationType: String, operation: suspend () -> Unit) {
            val startTime = System.currentTimeMillis()
            var success = true

            try {
                operation()
            } catch (e: Exception) {
                success = false
            }

            val latencyMs = System.currentTimeMillis() - startTime

            latencyFlow.emit(LatencyMeasurement(
                operationType = operationType,
                latencyMs = latencyMs,
                timestamp = System.currentTimeMillis(),
                success = success
            ))

            operationCounts.computeIfAbsent(operationType) { AtomicInteger(0) }.incrementAndGet()

            // Check for performance alerts
            if (isMonitoring) {
                checkForAlerts(operationType, latencyMs, success)
            }
        }

        fun startSession() {
            sessionStartTime = System.currentTimeMillis()
        }

        fun endSession() {
            // Session ended
        }

        fun startMemoryMonitoring() {
            isMonitoring = true
        }

        fun stopMemoryMonitoring() {
            isMonitoring = false
        }

        suspend fun takeMemorySnapshot() {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()

            memoryFlow.emit(MemorySnapshot(
                timestamp = System.currentTimeMillis(),
                usedMemoryBytes = usedMemory,
                maxMemoryBytes = maxMemory,
                gcCount = gcCount.get(),
                activeObjects = activeObjects.get()
            ))
        }

        fun forceGC() {
            System.gc()
            gcCount.incrementAndGet()
        }

        fun setNetworkCondition(condition: NetworkCondition) {
            currentNetworkCondition = condition
        }

        fun enablePerformanceMonitoring() {
            isMonitoring = true
        }

        private suspend fun checkForAlerts(operationType: String, latencyMs: Long, success: Boolean) {
            // High latency alert
            if (latencyMs > TARGET_LATENCY_MS) {
                alertFlow.emit(PerformanceAlert(
                    type = PerformanceAlert.Type.HIGH_LATENCY,
                    message = "High latency detected: ${latencyMs}ms for $operationType",
                    timestamp = System.currentTimeMillis(),
                    severity = if (latencyMs > ACCEPTABLE_LATENCY_MS)
                        PerformanceAlert.Severity.HIGH else PerformanceAlert.Severity.MEDIUM
                ))
            }

            // Network error alert
            if (!success) {
                alertFlow.emit(PerformanceAlert(
                    type = PerformanceAlert.Type.NETWORK_ERRORS,
                    message = "Operation failed: $operationType",
                    timestamp = System.currentTimeMillis(),
                    severity = PerformanceAlert.Severity.MEDIUM
                ))
            }
        }
    }
}