package com.posecoach.app.performance.adaptive

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

/**
 * Advanced Performance Monitor - Real-time system monitoring with minimal overhead
 */
class AdvancedPerformanceMonitor(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    data class SystemMetrics(
        val timestamp: Long,
        val cpuUsage: Float,
        val memoryUsage: MemoryUsage,
        val gpuUsage: Float,
        val thermalState: ThermalState,
        val batteryMetrics: BatteryMetrics,
        val networkMetrics: NetworkMetrics,
        val diskUsage: DiskUsage,
        val processMetrics: ProcessMetrics
    )

    data class MemoryUsage(
        val used: Long,
        val available: Long,
        val total: Long,
        val gcCount: Int,
        val gcTime: Long,
        val nativeHeap: Long,
        val dalvikHeap: Long
    ) {
        val usagePercentage: Float get() = (used.toFloat() / total) * 100f
    }

    data class ThermalState(
        val state: Int, // 0-6 based on PowerManager.THERMAL_STATUS_*
        val temperature: Float, // Celsius
        val throttling: Boolean
    )

    data class BatteryMetrics(
        val level: Float, // 0-100
        val isCharging: Boolean,
        val voltage: Float,
        val temperature: Float,
        val drainRate: Float, // mAh/hour
        val estimatedTimeRemaining: Long // milliseconds
    )

    data class NetworkMetrics(
        val bytesReceived: Long,
        val bytesSent: Long,
        val packetsReceived: Long,
        val packetsSent: Long,
        val latency: Float, // milliseconds
        val bandwidth: Float // Mbps
    )

    data class DiskUsage(
        val totalSpace: Long,
        val availableSpace: Long,
        val readBytes: Long,
        val writeBytes: Long,
        val readOps: Long,
        val writeOps: Long
    ) {
        val usagePercentage: Float get() = ((totalSpace - availableSpace).toFloat() / totalSpace) * 100f
    }

    data class ProcessMetrics(
        val threadCount: Int,
        val fileDescriptorCount: Int,
        val cpuTime: Long,
        val userTime: Long,
        val systemTime: Long,
        val majorPageFaults: Long,
        val minorPageFaults: Long
    )

    data class PerformanceAlert(
        val timestamp: Long,
        val severity: AlertSeverity,
        val category: AlertCategory,
        val message: String,
        val metrics: Map<String, Float>,
        val suggestions: List<String>
    )

    enum class AlertSeverity {
        INFO, WARNING, CRITICAL
    }

    enum class AlertCategory {
        CPU, MEMORY, THERMAL, BATTERY, NETWORK, DISK, GENERAL
    }

    data class PerformanceProfile(
        val name: String,
        val cpuThresholds: Thresholds,
        val memoryThresholds: Thresholds,
        val thermalThresholds: Thresholds,
        val batteryThresholds: Thresholds,
        val monitoringInterval: Long = 1000L
    )

    data class Thresholds(
        val warning: Float,
        val critical: Float
    )

    companion object {
        private const val DEFAULT_MONITORING_INTERVAL = 1000L
        private const val METRICS_HISTORY_SIZE = 300 // 5 minutes at 1s intervals
        private const val ALERT_COOLDOWN_MS = 30000L // 30 seconds

        val DEFAULT_PROFILE = PerformanceProfile(
            name = "default",
            cpuThresholds = Thresholds(70f, 90f),
            memoryThresholds = Thresholds(80f, 95f),
            thermalThresholds = Thresholds(60f, 80f),
            batteryThresholds = Thresholds(20f, 10f)
        )

        val BATTERY_SAVER_PROFILE = PerformanceProfile(
            name = "battery_saver",
            cpuThresholds = Thresholds(50f, 70f),
            memoryThresholds = Thresholds(70f, 85f),
            thermalThresholds = Thresholds(50f, 70f),
            batteryThresholds = Thresholds(30f, 15f),
            monitoringInterval = 2000L
        )

        val PERFORMANCE_PROFILE = PerformanceProfile(
            name = "performance",
            cpuThresholds = Thresholds(80f, 95f),
            memoryThresholds = Thresholds(85f, 98f),
            thermalThresholds = Thresholds(70f, 85f),
            batteryThresholds = Thresholds(15f, 5f),
            monitoringInterval = 500L
        )
    }

    // Monitoring state
    private var isMonitoring = false
    private var currentProfile = DEFAULT_PROFILE
    private val metricsHistory = ConcurrentLinkedQueue<SystemMetrics>()
    private val alertHistory = ConcurrentLinkedQueue<PerformanceAlert>()
    private val lastAlertTimes = mutableMapOf<String, Long>()

    // Flow publishers
    private val _systemMetrics = MutableSharedFlow<SystemMetrics>(
        replay = 1,
        extraBufferCapacity = 100
    )
    val systemMetrics: SharedFlow<SystemMetrics> = _systemMetrics.asSharedFlow()

    private val _performanceAlerts = MutableSharedFlow<PerformanceAlert>(
        replay = 0,
        extraBufferCapacity = 50
    )
    val performanceAlerts: SharedFlow<PerformanceAlert> = _performanceAlerts.asSharedFlow()

    // Monitoring job
    private var monitoringJob: Job? = null

    init {
        setupDefaultThresholds()
    }

    private fun setupDefaultThresholds() {
        setProfile(DEFAULT_PROFILE)
    }

    fun startMonitoring() {
        if (isMonitoring) return

        isMonitoring = true
        monitoringJob = scope.launch {
            while (isActive && isMonitoring) {
                try {
                    val metrics = collectSystemMetrics()
                    processMetrics(metrics)
                    delay(currentProfile.monitoringInterval)
                } catch (e: Exception) {
                    Timber.e(e, "Error in performance monitoring")
                    delay(currentProfile.monitoringInterval * 2)
                }
            }
        }
        Timber.i("Performance monitoring started")
    }

    fun stopMonitoring() {
        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null
        Timber.i("Performance monitoring stopped")
    }

    private suspend fun collectSystemMetrics(): SystemMetrics = withContext(Dispatchers.IO) {
        val timestamp = SystemClock.elapsedRealtime()

        SystemMetrics(
            timestamp = timestamp,
            cpuUsage = collectCpuUsage(),
            memoryUsage = collectMemoryUsage(),
            gpuUsage = collectGpuUsage(),
            thermalState = collectThermalState(),
            batteryMetrics = collectBatteryMetrics(),
            networkMetrics = collectNetworkMetrics(),
            diskUsage = collectDiskUsage(),
            processMetrics = collectProcessMetrics()
        )
    }

    private fun collectCpuUsage(): Float {
        // Simplified CPU usage calculation
        // In a real implementation, this would read from /proc/stat or use other system APIs
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            // Use memory pressure as a proxy for CPU usage
            ((usedMemory.toFloat() / maxMemory) * 100f).coerceIn(0f, 100f)
        } catch (e: Exception) {
            Timber.e(e, "Error collecting CPU usage")
            0f
        }
    }

    private fun collectMemoryUsage(): MemoryUsage {
        return try {
            val runtime = Runtime.getRuntime()
            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)

            val used = runtime.totalMemory() - runtime.freeMemory()
            val available = runtime.maxMemory() - used
            val total = runtime.maxMemory()

            MemoryUsage(
                used = used,
                available = available,
                total = total,
                gcCount = getGcCount(),
                gcTime = getGcTime(),
                nativeHeap = memoryInfo.nativeHeapSize.toLong() * 1024,
                dalvikHeap = memoryInfo.dalvikHeapSize.toLong() * 1024
            )
        } catch (e: Exception) {
            Timber.e(e, "Error collecting memory usage")
            MemoryUsage(0, 0, 0, 0, 0, 0, 0)
        }
    }

    private fun collectGpuUsage(): Float {
        // GPU usage is difficult to measure directly on Android
        // This would require vendor-specific APIs or root access
        return 0f // Placeholder
    }

    private fun collectThermalState(): ThermalState {
        return try {
            // This would integrate with PowerManager.getThermalState() in API 29+
            // For now, return a mock thermal state
            ThermalState(
                state = 1, // Normal
                temperature = 35f, // Mock temperature
                throttling = false
            )
        } catch (e: Exception) {
            Timber.e(e, "Error collecting thermal state")
            ThermalState(0, 0f, false)
        }
    }

    private fun collectBatteryMetrics(): BatteryMetrics {
        return try {
            // This would integrate with BatteryManager
            // For now, return mock battery metrics
            BatteryMetrics(
                level = 75f,
                isCharging = false,
                voltage = 3.8f,
                temperature = 25f,
                drainRate = 500f, // mAh/hour
                estimatedTimeRemaining = 4 * 60 * 60 * 1000L // 4 hours
            )
        } catch (e: Exception) {
            Timber.e(e, "Error collecting battery metrics")
            BatteryMetrics(0f, false, 0f, 0f, 0f, 0L)
        }
    }

    private fun collectNetworkMetrics(): NetworkMetrics {
        return try {
            // This would integrate with TrafficStats
            NetworkMetrics(
                bytesReceived = 0L,
                bytesSent = 0L,
                packetsReceived = 0L,
                packetsSent = 0L,
                latency = 0f,
                bandwidth = 0f
            )
        } catch (e: Exception) {
            Timber.e(e, "Error collecting network metrics")
            NetworkMetrics(0L, 0L, 0L, 0L, 0f, 0f)
        }
    }

    private fun collectDiskUsage(): DiskUsage {
        return try {
            val internalDir = context.filesDir
            val totalSpace = internalDir.totalSpace
            val availableSpace = internalDir.usableSpace

            DiskUsage(
                totalSpace = totalSpace,
                availableSpace = availableSpace,
                readBytes = 0L, // Would need to track this
                writeBytes = 0L,
                readOps = 0L,
                writeOps = 0L
            )
        } catch (e: Exception) {
            Timber.e(e, "Error collecting disk usage")
            DiskUsage(0L, 0L, 0L, 0L, 0L, 0L)
        }
    }

    private fun collectProcessMetrics(): ProcessMetrics {
        return try {
            val threadCount = Thread.activeCount()

            ProcessMetrics(
                threadCount = threadCount,
                fileDescriptorCount = 0, // Would need proc filesystem access
                cpuTime = 0L,
                userTime = 0L,
                systemTime = 0L,
                majorPageFaults = 0L,
                minorPageFaults = 0L
            )
        } catch (e: Exception) {
            Timber.e(e, "Error collecting process metrics")
            ProcessMetrics(0, 0, 0L, 0L, 0L, 0L, 0L)
        }
    }

    private fun getGcCount(): Int {
        // This would track GC events
        return 0 // Placeholder
    }

    private fun getGcTime(): Long {
        // This would track GC time
        return 0L // Placeholder
    }

    private suspend fun processMetrics(metrics: SystemMetrics) {
        // Store metrics
        metricsHistory.offer(metrics)
        while (metricsHistory.size > METRICS_HISTORY_SIZE) {
            metricsHistory.poll()
        }

        // Emit metrics
        _systemMetrics.tryEmit(metrics)

        // Check for alerts
        checkForAlerts(metrics)

        // Log periodic summaries
        if (metricsHistory.size % 60 == 0) { // Every minute
            logPerformanceSummary()
        }
    }

    private fun checkForAlerts(metrics: SystemMetrics) {
        checkCpuAlerts(metrics)
        checkMemoryAlerts(metrics)
        checkThermalAlerts(metrics)
        checkBatteryAlerts(metrics)
        checkDiskAlerts(metrics)
    }

    private fun checkCpuAlerts(metrics: SystemMetrics) {
        val cpuUsage = metrics.cpuUsage
        val thresholds = currentProfile.cpuThresholds

        when {
            cpuUsage >= thresholds.critical -> {
                emitAlert(
                    AlertSeverity.CRITICAL,
                    AlertCategory.CPU,
                    "Critical CPU usage: ${cpuUsage.toInt()}%",
                    mapOf("cpu_usage" to cpuUsage),
                    listOf(
                        "Reduce processing load immediately",
                        "Enable performance degradation",
                        "Close background applications"
                    )
                )
            }
            cpuUsage >= thresholds.warning -> {
                emitAlert(
                    AlertSeverity.WARNING,
                    AlertCategory.CPU,
                    "High CPU usage: ${cpuUsage.toInt()}%",
                    mapOf("cpu_usage" to cpuUsage),
                    listOf(
                        "Consider reducing quality settings",
                        "Enable frame skipping",
                        "Monitor for sustained high usage"
                    )
                )
            }
        }
    }

    private fun checkMemoryAlerts(metrics: SystemMetrics) {
        val memoryUsage = metrics.memoryUsage.usagePercentage
        val thresholds = currentProfile.memoryThresholds

        when {
            memoryUsage >= thresholds.critical -> {
                emitAlert(
                    AlertSeverity.CRITICAL,
                    AlertCategory.MEMORY,
                    "Critical memory usage: ${memoryUsage.toInt()}%",
                    mapOf(
                        "memory_usage" to memoryUsage,
                        "available_mb" to (metrics.memoryUsage.available / 1024f / 1024f)
                    ),
                    listOf(
                        "Clear caches immediately",
                        "Force garbage collection",
                        "Reduce memory allocations"
                    )
                )
            }
            memoryUsage >= thresholds.warning -> {
                emitAlert(
                    AlertSeverity.WARNING,
                    AlertCategory.MEMORY,
                    "High memory usage: ${memoryUsage.toInt()}%",
                    mapOf(
                        "memory_usage" to memoryUsage,
                        "available_mb" to (metrics.memoryUsage.available / 1024f / 1024f)
                    ),
                    listOf(
                        "Clear low-priority caches",
                        "Reduce image quality",
                        "Limit concurrent operations"
                    )
                )
            }
        }
    }

    private fun checkThermalAlerts(metrics: SystemMetrics) {
        val temperature = metrics.thermalState.temperature
        val thresholds = currentProfile.thermalThresholds

        if (metrics.thermalState.throttling) {
            emitAlert(
                AlertSeverity.CRITICAL,
                AlertCategory.THERMAL,
                "Thermal throttling active",
                mapOf(
                    "temperature" to temperature,
                    "thermal_state" to metrics.thermalState.state.toFloat()
                ),
                listOf(
                    "Reduce processing immediately",
                    "Enable thermal management mode",
                    "Increase cooling period"
                )
            )
        } else when {
            temperature >= thresholds.critical -> {
                emitAlert(
                    AlertSeverity.CRITICAL,
                    AlertCategory.THERMAL,
                    "Critical temperature: ${temperature.toInt()}°C",
                    mapOf("temperature" to temperature),
                    listOf(
                        "Activate thermal protection",
                        "Reduce processing load",
                        "Monitor for overheating"
                    )
                )
            }
            temperature >= thresholds.warning -> {
                emitAlert(
                    AlertSeverity.WARNING,
                    AlertCategory.THERMAL,
                    "High temperature: ${temperature.toInt()}°C",
                    mapOf("temperature" to temperature),
                    listOf(
                        "Enable thermal monitoring",
                        "Reduce processing intensity",
                        "Consider cooling period"
                    )
                )
            }
        }
    }

    private fun checkBatteryAlerts(metrics: SystemMetrics) {
        val batteryLevel = metrics.batteryMetrics.level
        val thresholds = currentProfile.batteryThresholds

        if (!metrics.batteryMetrics.isCharging) {
            when {
                batteryLevel <= thresholds.critical -> {
                    emitAlert(
                        AlertSeverity.CRITICAL,
                        AlertCategory.BATTERY,
                        "Critical battery level: ${batteryLevel.toInt()}%",
                        mapOf(
                            "battery_level" to batteryLevel,
                            "drain_rate" to metrics.batteryMetrics.drainRate
                        ),
                        listOf(
                            "Enable ultra battery saver mode",
                            "Reduce all processing",
                            "Minimize screen updates"
                        )
                    )
                }
                batteryLevel <= thresholds.warning -> {
                    emitAlert(
                        AlertSeverity.WARNING,
                        AlertCategory.BATTERY,
                        "Low battery level: ${batteryLevel.toInt()}%",
                        mapOf(
                            "battery_level" to batteryLevel,
                            "drain_rate" to metrics.batteryMetrics.drainRate
                        ),
                        listOf(
                            "Enable battery saver mode",
                            "Reduce quality settings",
                            "Optimize processing frequency"
                        )
                    )
                }
            }
        }

        // Check for high drain rate
        if (metrics.batteryMetrics.drainRate > 1000f) { // mAh/hour
            emitAlert(
                AlertSeverity.WARNING,
                AlertCategory.BATTERY,
                "High battery drain rate: ${metrics.batteryMetrics.drainRate.toInt()} mAh/h",
                mapOf("drain_rate" to metrics.batteryMetrics.drainRate),
                listOf(
                    "Investigate high power consumption",
                    "Reduce processing intensity",
                    "Check for background processes"
                )
            )
        }
    }

    private fun checkDiskAlerts(metrics: SystemMetrics) {
        val diskUsage = metrics.diskUsage.usagePercentage

        when {
            diskUsage >= 95f -> {
                emitAlert(
                    AlertSeverity.CRITICAL,
                    AlertCategory.DISK,
                    "Critical disk space: ${diskUsage.toInt()}% used",
                    mapOf(
                        "disk_usage" to diskUsage,
                        "available_mb" to (metrics.diskUsage.availableSpace / 1024f / 1024f)
                    ),
                    listOf(
                        "Clear all caches immediately",
                        "Remove temporary files",
                        "Free up storage space"
                    )
                )
            }
            diskUsage >= 85f -> {
                emitAlert(
                    AlertSeverity.WARNING,
                    AlertCategory.DISK,
                    "Low disk space: ${diskUsage.toInt()}% used",
                    mapOf(
                        "disk_usage" to diskUsage,
                        "available_mb" to (metrics.diskUsage.availableSpace / 1024f / 1024f)
                    ),
                    listOf(
                        "Clear low-priority caches",
                        "Reduce cache sizes",
                        "Monitor storage usage"
                    )
                )
            }
        }
    }

    private fun emitAlert(
        severity: AlertSeverity,
        category: AlertCategory,
        message: String,
        metrics: Map<String, Float>,
        suggestions: List<String>
    ) {
        val alertKey = "${category.name}_${severity.name}"
        val currentTime = SystemClock.elapsedRealtime()
        val lastAlertTime = lastAlertTimes[alertKey] ?: 0L

        // Implement alert cooldown to prevent spam
        if (currentTime - lastAlertTime < ALERT_COOLDOWN_MS) {
            return
        }

        val alert = PerformanceAlert(
            timestamp = currentTime,
            severity = severity,
            category = category,
            message = message,
            metrics = metrics,
            suggestions = suggestions
        )

        alertHistory.offer(alert)
        _performanceAlerts.tryEmit(alert)
        lastAlertTimes[alertKey] = currentTime

        val logLevel = when (severity) {
            AlertSeverity.INFO -> "INFO"
            AlertSeverity.WARNING -> "WARN"
            AlertSeverity.CRITICAL -> "ERROR"
        }

        Timber.tag("PerformanceAlert").log(
            when (severity) {
                AlertSeverity.INFO -> android.util.Log.INFO
                AlertSeverity.WARNING -> android.util.Log.WARN
                AlertSeverity.CRITICAL -> android.util.Log.ERROR
            },
            "[$logLevel] $message"
        )
    }

    private fun logPerformanceSummary() {
        if (metricsHistory.isEmpty()) return

        val recentMetrics = metricsHistory.takeLast(60) // Last minute
        val avgCpu = recentMetrics.map { it.cpuUsage }.average()
        val avgMemory = recentMetrics.map { it.memoryUsage.usagePercentage }.average()
        val avgTemperature = recentMetrics.map { it.thermalState.temperature }.average()

        Timber.i("""
            Performance Summary (1 min):
            CPU: ${"%.1f".format(avgCpu)}%
            Memory: ${"%.1f".format(avgMemory)}%
            Temperature: ${"%.1f".format(avgTemperature)}°C
            Alerts: ${alertHistory.size}
        """.trimIndent())
    }

    // Public API methods
    fun setProfile(profile: PerformanceProfile) {
        currentProfile = profile
        Timber.i("Performance monitoring profile set to: ${profile.name}")
    }

    fun getCurrentMetrics(): SystemMetrics? {
        return metricsHistory.lastOrNull()
    }

    fun getMetricsHistory(durationMs: Long): List<SystemMetrics> {
        val cutoffTime = SystemClock.elapsedRealtime() - durationMs
        return metricsHistory.filter { it.timestamp >= cutoffTime }
    }

    fun getRecentAlerts(count: Int = 10): List<PerformanceAlert> {
        return alertHistory.takeLast(count)
    }

    fun clearAlerts() {
        alertHistory.clear()
        lastAlertTimes.clear()
        Timber.i("Performance alerts cleared")
    }

    fun getPerformanceSummary(): Map<String, Any> {
        val currentMetrics = getCurrentMetrics()
        val recentMetrics = getMetricsHistory(60000L) // Last minute

        return if (currentMetrics != null) {
            mapOf(
                "current" to mapOf(
                    "cpu_usage" to currentMetrics.cpuUsage,
                    "memory_usage" to currentMetrics.memoryUsage.usagePercentage,
                    "temperature" to currentMetrics.thermalState.temperature,
                    "battery_level" to currentMetrics.batteryMetrics.level,
                    "disk_usage" to currentMetrics.diskUsage.usagePercentage
                ),
                "averages_1min" to if (recentMetrics.isNotEmpty()) mapOf(
                    "cpu_usage" to recentMetrics.map { it.cpuUsage }.average(),
                    "memory_usage" to recentMetrics.map { it.memoryUsage.usagePercentage }.average(),
                    "temperature" to recentMetrics.map { it.thermalState.temperature }.average()
                ) else emptyMap(),
                "alerts" to mapOf(
                    "total_count" to alertHistory.size,
                    "critical_count" to alertHistory.count { it.severity == AlertSeverity.CRITICAL },
                    "warning_count" to alertHistory.count { it.severity == AlertSeverity.WARNING }
                ),
                "monitoring" to mapOf(
                    "is_active" to isMonitoring,
                    "profile" to currentProfile.name,
                    "interval_ms" to currentProfile.monitoringInterval,
                    "history_size" to metricsHistory.size
                )
            )
        } else {
            mapOf("error" to "No metrics available")
        }
    }

    fun exportMetrics(durationMs: Long): String {
        val metrics = getMetricsHistory(durationMs)
        if (metrics.isEmpty()) return "No metrics available"

        return buildString {
            appendLine("=== Performance Metrics Export ===")
            appendLine("Duration: ${durationMs / 1000}s")
            appendLine("Samples: ${metrics.size}")
            appendLine()

            appendLine("Timestamp,CPU%,Memory%,Temperature°C,Battery%,DiskUsage%")
            metrics.forEach { metric ->
                appendLine("${metric.timestamp},${metric.cpuUsage},${metric.memoryUsage.usagePercentage},${metric.thermalState.temperature},${metric.batteryMetrics.level},${metric.diskUsage.usagePercentage}")
            }
        }
    }

    fun shutdown() {
        stopMonitoring()
        scope.cancel()
        Timber.i("Advanced performance monitor shutdown")
    }
}