package com.posecoach.app.livecoach.performance

import android.content.Context
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

/**
 * Performance monitoring and optimization for Live Coach functionality
 * Tracks latency, memory usage, battery consumption, and provides optimization recommendations
 */
class LiveCoachPerformanceMonitor(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : CoroutineScope {

    override val coroutineContext: CoroutineContext =
        coroutineScope.coroutineContext + SupervisorJob()

    companion object {
        private const val PERFORMANCE_CHECK_INTERVAL_MS = 30000L // 30 seconds
        private const val LATENCY_WARNING_THRESHOLD_MS = 200L
        private const val LATENCY_CRITICAL_THRESHOLD_MS = 500L
        private const val MEMORY_WARNING_THRESHOLD_MB = 50L
        private const val BATTERY_OPTIMIZATION_THRESHOLD = 0.7 // 70% performance to enable battery savings
    }

    // Performance metrics
    private val performanceMetrics = PerformanceMetrics()
    private var monitoringJob: Job? = null
    private var lastOptimizationTime = 0L

    // Performance events
    private val _performanceAlerts = MutableSharedFlow<PerformanceAlert>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val performanceAlerts: SharedFlow<PerformanceAlert> = _performanceAlerts.asSharedFlow()

    private val _optimizationRecommendations = MutableSharedFlow<OptimizationRecommendation>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val optimizationRecommendations: SharedFlow<OptimizationRecommendation> =
        _optimizationRecommendations.asSharedFlow()

    data class PerformanceMetrics(
        var audioLatency: MutableList<Long> = mutableListOf(),
        var websocketLatency: MutableList<Long> = mutableListOf(),
        var memoryUsage: MutableList<Long> = mutableListOf(),
        var cpuUsage: MutableList<Double> = mutableListOf(),
        var batteryLevel: Int = 100,
        var connectionDrops: Int = 0,
        var audioDropouts: Int = 0,
        var totalMessages: Long = 0,
        var totalDataTransferred: Long = 0,
        var sessionStartTime: Long = System.currentTimeMillis()
    )

    data class PerformanceAlert(
        val type: AlertType,
        val severity: Severity,
        val message: String,
        val timestamp: Long = System.currentTimeMillis(),
        val metrics: Map<String, Any> = emptyMap()
    )

    data class OptimizationRecommendation(
        val type: OptimizationType,
        val priority: Priority,
        val description: String,
        val expectedImpact: String,
        val actionRequired: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class AlertType {
        HIGH_LATENCY, HIGH_MEMORY, LOW_BATTERY, CONNECTION_UNSTABLE, AUDIO_QUALITY_POOR
    }

    enum class Severity {
        INFO, WARNING, CRITICAL
    }

    enum class OptimizationType {
        BATTERY_OPTIMIZATION, AUDIO_OPTIMIZATION, CONNECTION_OPTIMIZATION, MEMORY_OPTIMIZATION
    }

    enum class Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    fun startMonitoring() {
        if (monitoringJob?.isActive == true) {
            Timber.w("Performance monitoring already active")
            return
        }

        performanceMetrics.sessionStartTime = System.currentTimeMillis()

        monitoringJob = launch {
            Timber.d("Performance monitoring started")

            while (isActive) {
                try {
                    collectPerformanceMetrics()
                    analyzePerformance()
                    generateOptimizationRecommendations()

                    delay(PERFORMANCE_CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Timber.e(e, "Error in performance monitoring")
                    delay(PERFORMANCE_CHECK_INTERVAL_MS)
                }
            }
        }
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        Timber.d("Performance monitoring stopped")
    }

    fun recordAudioLatency(latencyMs: Long) {
        performanceMetrics.audioLatency.add(latencyMs)

        // Keep only recent measurements (last 100)
        if (performanceMetrics.audioLatency.size > 100) {
            performanceMetrics.audioLatency.removeAt(0)
        }

        // Check for immediate alerts
        when {
            latencyMs > LATENCY_CRITICAL_THRESHOLD_MS -> {
                launch {
                    _performanceAlerts.emit(
                        PerformanceAlert(
                            type = AlertType.HIGH_LATENCY,
                            severity = Severity.CRITICAL,
                            message = "Critical audio latency detected: ${latencyMs}ms",
                            metrics = mapOf("latency" to latencyMs, "threshold" to LATENCY_CRITICAL_THRESHOLD_MS)
                        )
                    )
                }
            }
            latencyMs > LATENCY_WARNING_THRESHOLD_MS -> {
                launch {
                    _performanceAlerts.emit(
                        PerformanceAlert(
                            type = AlertType.HIGH_LATENCY,
                            severity = Severity.WARNING,
                            message = "High audio latency: ${latencyMs}ms",
                            metrics = mapOf("latency" to latencyMs, "threshold" to LATENCY_WARNING_THRESHOLD_MS)
                        )
                    )
                }
            }
        }
    }

    fun recordWebSocketLatency(latencyMs: Long) {
        performanceMetrics.websocketLatency.add(latencyMs)

        // Keep only recent measurements (last 100)
        if (performanceMetrics.websocketLatency.size > 100) {
            performanceMetrics.websocketLatency.removeAt(0)
        }
    }

    fun recordConnectionDrop() {
        performanceMetrics.connectionDrops++

        launch {
            _performanceAlerts.emit(
                PerformanceAlert(
                    type = AlertType.CONNECTION_UNSTABLE,
                    severity = if (performanceMetrics.connectionDrops > 3) Severity.CRITICAL else Severity.WARNING,
                    message = "Connection dropped (total: ${performanceMetrics.connectionDrops})",
                    metrics = mapOf("total_drops" to performanceMetrics.connectionDrops)
                )
            )
        }
    }

    fun recordAudioDropout() {
        performanceMetrics.audioDropouts++

        launch {
            _performanceAlerts.emit(
                PerformanceAlert(
                    type = AlertType.AUDIO_QUALITY_POOR,
                    severity = if (performanceMetrics.audioDropouts > 5) Severity.CRITICAL else Severity.WARNING,
                    message = "Audio dropout detected (total: ${performanceMetrics.audioDropouts})",
                    metrics = mapOf("total_dropouts" to performanceMetrics.audioDropouts)
                )
            )
        }
    }

    fun recordDataTransfer(bytes: Long) {
        performanceMetrics.totalDataTransferred += bytes
        performanceMetrics.totalMessages++
    }

    private suspend fun collectPerformanceMetrics() {
        // Collect memory usage
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 // MB
        performanceMetrics.memoryUsage.add(usedMemory)

        // Keep only recent measurements (last 100)
        if (performanceMetrics.memoryUsage.size > 100) {
            performanceMetrics.memoryUsage.removeAt(0)
        }

        // Collect battery level (simplified - in real app would use BatteryManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Battery level would be collected here
            // For now, simulate declining battery
            if (performanceMetrics.batteryLevel > 20) {
                performanceMetrics.batteryLevel = (performanceMetrics.batteryLevel * 0.999).toInt()
            }
        }

        Timber.v("Performance metrics collected: memory=${usedMemory}MB, battery=${performanceMetrics.batteryLevel}%")
    }

    private suspend fun analyzePerformance() {
        // Analyze memory usage
        if (performanceMetrics.memoryUsage.isNotEmpty()) {
            val avgMemory = performanceMetrics.memoryUsage.average()
            if (avgMemory > MEMORY_WARNING_THRESHOLD_MB) {
                _performanceAlerts.emit(
                    PerformanceAlert(
                        type = AlertType.HIGH_MEMORY,
                        severity = if (avgMemory > MEMORY_WARNING_THRESHOLD_MB * 2) Severity.CRITICAL else Severity.WARNING,
                        message = "High memory usage: ${String.format("%.1f", avgMemory)}MB",
                        metrics = mapOf("average_memory" to avgMemory, "threshold" to MEMORY_WARNING_THRESHOLD_MB)
                    )
                )
            }
        }

        // Analyze battery level
        if (performanceMetrics.batteryLevel < 20) {
            _performanceAlerts.emit(
                PerformanceAlert(
                    type = AlertType.LOW_BATTERY,
                    severity = if (performanceMetrics.batteryLevel < 10) Severity.CRITICAL else Severity.WARNING,
                    message = "Low battery: ${performanceMetrics.batteryLevel}%",
                    metrics = mapOf("battery_level" to performanceMetrics.batteryLevel)
                )
            )
        }

        // Analyze latency trends
        if (performanceMetrics.audioLatency.size >= 10) {
            val recentLatency = performanceMetrics.audioLatency.takeLast(10).average()
            val overallLatency = performanceMetrics.audioLatency.average()

            if (recentLatency > overallLatency * 1.5) {
                _performanceAlerts.emit(
                    PerformanceAlert(
                        type = AlertType.HIGH_LATENCY,
                        severity = Severity.WARNING,
                        message = "Latency trend increasing: recent ${String.format("%.0f", recentLatency)}ms vs avg ${String.format("%.0f", overallLatency)}ms",
                        metrics = mapOf("recent_latency" to recentLatency, "average_latency" to overallLatency)
                    )
                )
            }
        }
    }

    private suspend fun generateOptimizationRecommendations() {
        val now = System.currentTimeMillis()

        // Don't generate recommendations too frequently
        if (now - lastOptimizationTime < 60000) { // 1 minute cooldown
            return
        }

        lastOptimizationTime = now

        // Battery optimization recommendations
        if (performanceMetrics.batteryLevel < 30) {
            _optimizationRecommendations.emit(
                OptimizationRecommendation(
                    type = OptimizationType.BATTERY_OPTIMIZATION,
                    priority = if (performanceMetrics.batteryLevel < 15) Priority.CRITICAL else Priority.HIGH,
                    description = "Enable battery optimization mode",
                    expectedImpact = "Reduce power consumption by 20-30%",
                    actionRequired = "Disable barge-in mode, reduce audio quality, increase chunk intervals"
                )
            )
        }

        // Audio optimization recommendations
        if (performanceMetrics.audioLatency.isNotEmpty()) {
            val avgLatency = performanceMetrics.audioLatency.average()
            if (avgLatency > LATENCY_WARNING_THRESHOLD_MS) {
                _optimizationRecommendations.emit(
                    OptimizationRecommendation(
                        type = OptimizationType.AUDIO_OPTIMIZATION,
                        priority = if (avgLatency > LATENCY_CRITICAL_THRESHOLD_MS) Priority.CRITICAL else Priority.HIGH,
                        description = "Optimize audio processing for lower latency",
                        expectedImpact = "Reduce audio latency by 30-50%",
                        actionRequired = "Reduce buffer size, enable low-latency mode, check for background audio apps"
                    )
                )
            }
        }

        // Connection optimization recommendations
        if (performanceMetrics.connectionDrops > 2) {
            _optimizationRecommendations.emit(
                OptimizationRecommendation(
                    type = OptimizationType.CONNECTION_OPTIMIZATION,
                    priority = Priority.HIGH,
                    description = "Improve connection stability",
                    expectedImpact = "Reduce connection drops by 60-80%",
                    actionRequired = "Check network conditions, adjust retry parameters, consider WiFi vs cellular"
                )
            )
        }

        // Memory optimization recommendations
        if (performanceMetrics.memoryUsage.isNotEmpty()) {
            val avgMemory = performanceMetrics.memoryUsage.average()
            if (avgMemory > MEMORY_WARNING_THRESHOLD_MB) {
                _optimizationRecommendations.emit(
                    OptimizationRecommendation(
                        type = OptimizationType.MEMORY_OPTIMIZATION,
                        priority = Priority.MEDIUM,
                        description = "Reduce memory usage",
                        expectedImpact = "Free up 20-40% of current memory usage",
                        actionRequired = "Clear audio buffers more frequently, reduce history retention, optimize image processing"
                    )
                )
            }
        }
    }

    fun getPerformanceSummary(): Map<String, Any> {
        val sessionDuration = System.currentTimeMillis() - performanceMetrics.sessionStartTime

        return mapOf(
            "session_duration_ms" to sessionDuration,
            "average_audio_latency_ms" to if (performanceMetrics.audioLatency.isNotEmpty())
                performanceMetrics.audioLatency.average() else 0.0,
            "average_websocket_latency_ms" to if (performanceMetrics.websocketLatency.isNotEmpty())
                performanceMetrics.websocketLatency.average() else 0.0,
            "average_memory_usage_mb" to if (performanceMetrics.memoryUsage.isNotEmpty())
                performanceMetrics.memoryUsage.average() else 0.0,
            "connection_drops" to performanceMetrics.connectionDrops,
            "audio_dropouts" to performanceMetrics.audioDropouts,
            "total_messages" to performanceMetrics.totalMessages,
            "total_data_transferred_bytes" to performanceMetrics.totalDataTransferred,
            "battery_level" to performanceMetrics.batteryLevel,
            "performance_score" to calculatePerformanceScore()
        )
    }

    fun getLatencyMetrics(): Map<String, Any> {
        return mapOf(
            "audio_latency" to mapOf(
                "current" to performanceMetrics.audioLatency.lastOrNull(),
                "average" to if (performanceMetrics.audioLatency.isNotEmpty())
                    performanceMetrics.audioLatency.average() else 0.0,
                "min" to performanceMetrics.audioLatency.minOrNull(),
                "max" to performanceMetrics.audioLatency.maxOrNull(),
                "p95" to calculatePercentile(performanceMetrics.audioLatency, 0.95),
                "samples" to performanceMetrics.audioLatency.size
            ),
            "websocket_latency" to mapOf(
                "current" to performanceMetrics.websocketLatency.lastOrNull(),
                "average" to if (performanceMetrics.websocketLatency.isNotEmpty())
                    performanceMetrics.websocketLatency.average() else 0.0,
                "min" to performanceMetrics.websocketLatency.minOrNull(),
                "max" to performanceMetrics.websocketLatency.maxOrNull(),
                "p95" to calculatePercentile(performanceMetrics.websocketLatency, 0.95),
                "samples" to performanceMetrics.websocketLatency.size
            )
        )
    }

    private fun calculatePerformanceScore(): Double {
        var score = 1.0

        // Penalize high latency
        if (performanceMetrics.audioLatency.isNotEmpty()) {
            val avgLatency = performanceMetrics.audioLatency.average()
            score *= when {
                avgLatency > LATENCY_CRITICAL_THRESHOLD_MS -> 0.3
                avgLatency > LATENCY_WARNING_THRESHOLD_MS -> 0.6
                else -> 1.0
            }
        }

        // Penalize connection drops
        score *= when (performanceMetrics.connectionDrops) {
            0 -> 1.0
            1, 2 -> 0.8
            3, 4 -> 0.6
            else -> 0.4
        }

        // Penalize audio dropouts
        score *= when (performanceMetrics.audioDropouts) {
            0 -> 1.0
            1, 2 -> 0.9
            3, 4, 5 -> 0.7
            else -> 0.5
        }

        // Consider battery level
        score *= when (performanceMetrics.batteryLevel) {
            in 50..100 -> 1.0
            in 20..49 -> 0.9
            in 10..19 -> 0.7
            else -> 0.5
        }

        return score.coerceIn(0.0, 1.0)
    }

    private fun calculatePercentile(values: List<Long>, percentile: Double): Long? {
        if (values.isEmpty()) return null

        val sorted = values.sorted()
        val index = (percentile * (sorted.size - 1)).toInt()
        return sorted.getOrNull(index)
    }

    fun shouldOptimizeForBattery(): Boolean {
        val performanceScore = calculatePerformanceScore()
        return performanceScore < BATTERY_OPTIMIZATION_THRESHOLD || performanceMetrics.batteryLevel < 20
    }

    fun getOptimizationRecommendationsFor(type: OptimizationType): List<String> {
        return when (type) {
            OptimizationType.BATTERY_OPTIMIZATION -> listOf(
                "Enable battery optimization mode",
                "Reduce audio processing frequency",
                "Disable barge-in detection",
                "Increase connection heartbeat interval",
                "Reduce image snapshot frequency"
            )
            OptimizationType.AUDIO_OPTIMIZATION -> listOf(
                "Reduce audio buffer size",
                "Enable low-latency audio mode",
                "Check for conflicting audio apps",
                "Adjust audio format settings",
                "Optimize silence detection thresholds"
            )
            OptimizationType.CONNECTION_OPTIMIZATION -> listOf(
                "Check network signal strength",
                "Switch to WiFi if available",
                "Adjust retry timeout parameters",
                "Enable connection health monitoring",
                "Reduce message sending frequency"
            )
            OptimizationType.MEMORY_OPTIMIZATION -> listOf(
                "Clear old audio buffers",
                "Reduce history retention time",
                "Optimize image processing",
                "Garbage collect unused objects",
                "Reduce concurrent operations"
            )
        }
    }

    fun destroy() {
        Timber.d("Destroying performance monitor")
        stopMonitoring()
        cancel() // Cancel coroutine scope
    }
}