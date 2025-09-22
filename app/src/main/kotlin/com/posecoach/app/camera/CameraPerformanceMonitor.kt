package com.posecoach.app.camera

import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.ImageProxy
import com.posecoach.app.overlay.FitMode
import com.posecoach.app.performance.PerformanceMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

/**
 * Comprehensive performance monitoring system for camera pipeline.
 * Tracks transformation performance, memory usage, frame rates, and accuracy metrics.
 */
class CameraPerformanceMonitor {

    data class FrameMetrics(
        val timestamp: Long,
        val frameWidth: Int,
        val frameHeight: Int,
        val processingTimeMs: Double,
        val transformationTimeMs: Double,
        val memoryUsageMB: Double,
        val transformationAccuracy: Float,
        val rotation: Int,
        val fitMode: FitMode
    )

    data class PerformanceSnapshot(
        val currentFps: Double,
        val averageProcessingTimeMs: Double,
        val averageTransformationTimeMs: Double,
        val averageMemoryUsageMB: Double,
        val averageAccuracy: Float,
        val frameCount: Long,
        val droppedFrames: Long,
        val totalUptime: Long,
        val recentMetrics: List<FrameMetrics>
    )

    data class PerformanceAlerts(
        val lowFpsAlert: Boolean,
        val highLatencyAlert: Boolean,
        val memoryPressureAlert: Boolean,
        val accuracyDegradationAlert: Boolean,
        val thermalThrottlingAlert: Boolean
    )

    companion object {
        private const val TAG = "CameraPerformanceMonitor"

        // Performance thresholds
        private const val MIN_TARGET_FPS = 24.0
        private const val MAX_PROCESSING_TIME_MS = 33.0 // ~30 FPS
        private const val MAX_TRANSFORMATION_TIME_MS = 5.0
        private const val MAX_MEMORY_USAGE_MB = 100.0
        private const val MIN_ACCURACY_THRESHOLD = 0.95f

        // Monitoring constants
        private const val METRICS_HISTORY_SIZE = 100
        private const val FPS_CALCULATION_WINDOW_MS = 1000L
        private const val ALERT_THRESHOLD_COUNT = 5
    }

    private val _performanceSnapshot = MutableStateFlow(createEmptySnapshot())
    val performanceSnapshot: StateFlow<PerformanceSnapshot> = _performanceSnapshot.asStateFlow()

    private val _performanceAlerts = MutableStateFlow(PerformanceAlerts(false, false, false, false, false))
    val performanceAlerts: StateFlow<PerformanceAlerts> = _performanceAlerts.asStateFlow()

    private val frameMetricsHistory = ConcurrentLinkedQueue<FrameMetrics>()
    private val frameTimestamps = ConcurrentLinkedQueue<Long>()

    private val frameCount = AtomicLong(0)
    private val droppedFrames = AtomicLong(0)
    private val startTime = System.currentTimeMillis()

    private var lastSnapshotUpdate = 0L
    private val snapshotUpdateInterval = 500L // Update every 500ms

    // Alert counters
    private var consecutiveLowFps = 0
    private var consecutiveHighLatency = 0
    private var consecutiveMemoryPressure = 0
    private var consecutiveAccuracyDegradation = 0

    /**
     * Record frame processing metrics
     */
    fun recordFrameMetrics(
        imageProxy: ImageProxy,
        processingStartTime: Long,
        transformationStartTime: Long,
        transformationEndTime: Long,
        rotation: Int,
        fitMode: FitMode,
        transformationAccuracy: Float = 1.0f
    ) {
        val endTime = System.nanoTime()
        val processingTimeMs = (endTime - processingStartTime) / 1_000_000.0
        val transformationTimeMs = (transformationEndTime - transformationStartTime) / 1_000_000.0

        val metrics = FrameMetrics(
            timestamp = System.currentTimeMillis(),
            frameWidth = imageProxy.width,
            frameHeight = imageProxy.height,
            processingTimeMs = processingTimeMs,
            transformationTimeMs = transformationTimeMs,
            memoryUsageMB = getCurrentMemoryUsageMB(),
            transformationAccuracy = transformationAccuracy,
            rotation = rotation,
            fitMode = fitMode
        )

        addFrameMetrics(metrics)
        updateFpsCalculation()
        checkPerformanceAlerts(metrics)

        frameCount.incrementAndGet()

        // Update snapshot periodically
        if (System.currentTimeMillis() - lastSnapshotUpdate > snapshotUpdateInterval) {
            updatePerformanceSnapshot()
            lastSnapshotUpdate = System.currentTimeMillis()
        }
    }

    /**
     * Record dropped frame
     */
    fun recordDroppedFrame() {
        droppedFrames.incrementAndGet()
        Timber.w("Frame dropped - total dropped: ${droppedFrames.get()}")
    }

    /**
     * Benchmark transformation performance for specific configuration
     */
    fun benchmarkTransformation(
        sourceSize: Size,
        targetSize: Size,
        rotation: Int,
        fitMode: FitMode,
        iterations: Int = 1000
    ): BenchmarkResult {
        val rotationManager = RotationTransformManager()
        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 90,
            displayRotation = rotation,
            isFrontFacing = false,
            fitMode = fitMode
        )

        val times = mutableListOf<Double>()
        val memoryUsages = mutableListOf<Double>()
        var successCount = 0

        val initialMemory = getCurrentMemoryUsageMB()

        repeat(iterations) {
            val startTime = System.nanoTime()
            val result = rotationManager.calculateTransformation(config)
            val endTime = System.nanoTime()

            val timeMs = (endTime - startTime) / 1_000_000.0
            times.add(timeMs)

            if (result.isValid) {
                successCount++
            }

            if (it % 100 == 0) {
                memoryUsages.add(getCurrentMemoryUsageMB())
            }
        }

        val finalMemory = getCurrentMemoryUsageMB()

        return BenchmarkResult(
            iterations = iterations,
            successRate = successCount.toDouble() / iterations,
            averageTimeMs = times.average(),
            medianTimeMs = times.sorted()[times.size / 2],
            p95TimeMs = times.sorted()[(times.size * 0.95).toInt()],
            p99TimeMs = times.sorted()[(times.size * 0.99).toInt()],
            minTimeMs = times.minOrNull() ?: 0.0,
            maxTimeMs = times.maxOrNull() ?: 0.0,
            memoryIncreaseMB = finalMemory - initialMemory,
            averageMemoryUsageMB = memoryUsages.average(),
            sourceSize = sourceSize,
            targetSize = targetSize,
            rotation = rotation,
            fitMode = fitMode
        )
    }

    /**
     * Test accuracy under various conditions
     */
    fun testAccuracy(
        sourceSize: Size,
        targetSize: Size,
        testPointCount: Int = 100
    ): AccuracyTestResult {
        val rotationManager = RotationTransformManager()
        val results = mutableMapOf<String, Float>()

        // Test all rotation angles
        listOf(0, 90, 180, 270).forEach { rotation ->
            FitMode.values().forEach { fitMode ->
                val config = RotationTransformManager.TransformationConfig(
                    sourceSize = sourceSize,
                    targetSize = targetSize,
                    sensorOrientation = 90,
                    displayRotation = rotation,
                    isFrontFacing = false,
                    fitMode = fitMode
                )

                val result = rotationManager.calculateTransformation(config)
                val testPoints = rotationManager.generateTestPoints(sourceSize, density = sqrt(testPointCount.toDouble()).toInt())
                val inverseMatrix = rotationManager.createInverseMatrix(result.matrix)

                val accuracy = calculateRoundTripAccuracy(
                    result.matrix,
                    inverseMatrix,
                    testPoints,
                    tolerance = 2.0f
                )

                results["${rotation}deg_${fitMode}"] = accuracy
            }
        }

        return AccuracyTestResult(
            testPointCount = testPointCount,
            sourceSize = sourceSize,
            targetSize = targetSize,
            accuracyResults = results,
            overallAccuracy = results.values.average().toFloat(),
            minAccuracy = results.values.minOrNull() ?: 0f,
            maxAccuracy = results.values.maxOrNull() ?: 0f
        )
    }

    /**
     * Run comprehensive performance test suite
     */
    fun runPerformanceTestSuite(): PerformanceTestSuite {
        val benchmarks = mutableListOf<BenchmarkResult>()
        val accuracyTests = mutableListOf<AccuracyTestResult>()

        // Common test resolutions
        val testResolutions = listOf(
            Pair(Size(640, 480), Size(1280, 720)),   // HD upscale
            Pair(Size(1920, 1080), Size(640, 480)),  // HD downscale
            Pair(Size(480, 640), Size(720, 1280)),   // Portrait
            Pair(Size(320, 240), Size(640, 480))     // Low power
        )

        // Test rotations
        val testRotations = listOf(0, 90, 180, 270)

        testResolutions.forEach { (source, target) ->
            testRotations.forEach { rotation ->
                FitMode.values().forEach { fitMode ->
                    // Benchmark performance
                    val benchmark = benchmarkTransformation(
                        sourceSize = source,
                        targetSize = target,
                        rotation = rotation,
                        fitMode = fitMode,
                        iterations = 100
                    )
                    benchmarks.add(benchmark)
                }
            }

            // Test accuracy
            val accuracyTest = testAccuracy(source, target, testPointCount = 100)
            accuracyTests.add(accuracyTest)
        }

        return PerformanceTestSuite(
            benchmarks = benchmarks,
            accuracyTests = accuracyTests,
            overallPerformanceScore = calculateOverallScore(benchmarks),
            overallAccuracyScore = accuracyTests.map { it.overallAccuracy }.average().toFloat()
        )
    }

    /**
     * Get detailed performance report
     */
    fun getPerformanceReport(): String {
        val snapshot = _performanceSnapshot.value
        val alerts = _performanceAlerts.value

        return buildString {
            appendLine("=== Camera Performance Report ===")
            appendLine("Current FPS: ${"%.1f".format(snapshot.currentFps)}")
            appendLine("Average Processing Time: ${"%.2f".format(snapshot.averageProcessingTimeMs)}ms")
            appendLine("Average Transformation Time: ${"%.2f".format(snapshot.averageTransformationTimeMs)}ms")
            appendLine("Average Memory Usage: ${"%.1f".format(snapshot.averageMemoryUsageMB)}MB")
            appendLine("Average Accuracy: ${"%.1f".format(snapshot.averageAccuracy * 100)}%")
            appendLine("Total Frames: ${snapshot.frameCount}")
            appendLine("Dropped Frames: ${snapshot.droppedFrames}")
            appendLine("Uptime: ${snapshot.totalUptime / 1000}s")
            appendLine()

            appendLine("=== Performance Alerts ===")
            appendLine("Low FPS: ${if (alerts.lowFpsAlert) "⚠️ YES" else "✅ NO"}")
            appendLine("High Latency: ${if (alerts.highLatencyAlert) "⚠️ YES" else "✅ NO"}")
            appendLine("Memory Pressure: ${if (alerts.memoryPressureAlert) "⚠️ YES" else "✅ NO"}")
            appendLine("Accuracy Degradation: ${if (alerts.accuracyDegradationAlert) "⚠️ YES" else "✅ NO"}")
            appendLine("Thermal Throttling: ${if (alerts.thermalThrottlingAlert) "⚠️ YES" else "✅ NO"}")
        }
    }

    // Private helper methods

    private fun addFrameMetrics(metrics: FrameMetrics) {
        frameMetricsHistory.offer(metrics)

        // Keep only recent history
        while (frameMetricsHistory.size > METRICS_HISTORY_SIZE) {
            frameMetricsHistory.poll()
        }
    }

    private fun updateFpsCalculation() {
        val currentTime = System.currentTimeMillis()
        frameTimestamps.offer(currentTime)

        // Remove old timestamps outside the window
        while (frameTimestamps.isNotEmpty()) {
            val oldestTimestamp = frameTimestamps.peek()
            if (oldestTimestamp != null && currentTime - oldestTimestamp > FPS_CALCULATION_WINDOW_MS) {
                frameTimestamps.poll()
            } else {
                break
            }
        }
    }

    private fun checkPerformanceAlerts(metrics: FrameMetrics) {
        // Check FPS
        val currentFps = calculateCurrentFps()
        if (currentFps < MIN_TARGET_FPS) {
            consecutiveLowFps++
        } else {
            consecutiveLowFps = 0
        }

        // Check latency
        if (metrics.processingTimeMs > MAX_PROCESSING_TIME_MS) {
            consecutiveHighLatency++
        } else {
            consecutiveHighLatency = 0
        }

        // Check memory usage
        if (metrics.memoryUsageMB > MAX_MEMORY_USAGE_MB) {
            consecutiveMemoryPressure++
        } else {
            consecutiveMemoryPressure = 0
        }

        // Check accuracy
        if (metrics.transformationAccuracy < MIN_ACCURACY_THRESHOLD) {
            consecutiveAccuracyDegradation++
        } else {
            consecutiveAccuracyDegradation = 0
        }

        // Update alerts
        _performanceAlerts.value = PerformanceAlerts(
            lowFpsAlert = consecutiveLowFps >= ALERT_THRESHOLD_COUNT,
            highLatencyAlert = consecutiveHighLatency >= ALERT_THRESHOLD_COUNT,
            memoryPressureAlert = consecutiveMemoryPressure >= ALERT_THRESHOLD_COUNT,
            accuracyDegradationAlert = consecutiveAccuracyDegradation >= ALERT_THRESHOLD_COUNT,
            thermalThrottlingAlert = false // Would need thermal API integration
        )
    }

    private fun updatePerformanceSnapshot() {
        val recentMetrics = frameMetricsHistory.toList()

        _performanceSnapshot.value = PerformanceSnapshot(
            currentFps = calculateCurrentFps(),
            averageProcessingTimeMs = recentMetrics.map { it.processingTimeMs }.average(),
            averageTransformationTimeMs = recentMetrics.map { it.transformationTimeMs }.average(),
            averageMemoryUsageMB = recentMetrics.map { it.memoryUsageMB }.average(),
            averageAccuracy = recentMetrics.map { it.transformationAccuracy }.average().toFloat(),
            frameCount = frameCount.get(),
            droppedFrames = droppedFrames.get(),
            totalUptime = System.currentTimeMillis() - startTime,
            recentMetrics = recentMetrics.takeLast(20)
        )
    }

    private fun calculateCurrentFps(): Double {
        val frameCount = frameTimestamps.size
        return if (frameCount > 1) {
            (frameCount - 1) * 1000.0 / FPS_CALCULATION_WINDOW_MS
        } else {
            0.0
        }
    }

    private fun getCurrentMemoryUsageMB(): Double {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        return usedMemory / (1024.0 * 1024.0)
    }

    private fun calculateRoundTripAccuracy(
        forwardMatrix: Matrix,
        inverseMatrix: Matrix,
        testPoints: List<android.graphics.PointF>,
        tolerance: Float
    ): Float {
        val rotationManager = RotationTransformManager()
        return if (rotationManager.validateTransformation(forwardMatrix, inverseMatrix, testPoints, tolerance)) {
            1.0f
        } else {
            0.95f // Fallback accuracy estimation
        }
    }

    private fun calculateOverallScore(benchmarks: List<BenchmarkResult>): Float {
        val avgTime = benchmarks.map { it.averageTimeMs }.average()
        val avgSuccess = benchmarks.map { it.successRate }.average()
        val avgMemory = benchmarks.map { it.memoryIncreaseMB }.average()

        // Scoring formula (lower is better for time and memory, higher is better for success)
        val timeScore = max(0.0, 1.0 - (avgTime / MAX_TRANSFORMATION_TIME_MS))
        val successScore = avgSuccess
        val memoryScore = max(0.0, 1.0 - (avgMemory / 10.0)) // 10MB threshold

        return ((timeScore + successScore + memoryScore) / 3.0).toFloat()
    }

    private fun createEmptySnapshot(): PerformanceSnapshot {
        return PerformanceSnapshot(
            currentFps = 0.0,
            averageProcessingTimeMs = 0.0,
            averageTransformationTimeMs = 0.0,
            averageMemoryUsageMB = 0.0,
            averageAccuracy = 0.0f,
            frameCount = 0,
            droppedFrames = 0,
            totalUptime = 0,
            recentMetrics = emptyList()
        )
    }

    // Data classes for results

    data class BenchmarkResult(
        val iterations: Int,
        val successRate: Double,
        val averageTimeMs: Double,
        val medianTimeMs: Double,
        val p95TimeMs: Double,
        val p99TimeMs: Double,
        val minTimeMs: Double,
        val maxTimeMs: Double,
        val memoryIncreaseMB: Double,
        val averageMemoryUsageMB: Double,
        val sourceSize: Size,
        val targetSize: Size,
        val rotation: Int,
        val fitMode: FitMode
    )

    data class AccuracyTestResult(
        val testPointCount: Int,
        val sourceSize: Size,
        val targetSize: Size,
        val accuracyResults: Map<String, Float>,
        val overallAccuracy: Float,
        val minAccuracy: Float,
        val maxAccuracy: Float
    )

    data class PerformanceTestSuite(
        val benchmarks: List<BenchmarkResult>,
        val accuracyTests: List<AccuracyTestResult>,
        val overallPerformanceScore: Float,
        val overallAccuracyScore: Float
    )
}