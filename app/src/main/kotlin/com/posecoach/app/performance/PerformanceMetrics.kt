package com.posecoach.app.performance

import android.os.Build
import android.os.Trace
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * 效能計量系統，支援 Systrace/Perfetto 整合
 * 測量單幀推論與端到端延遲
 */
class PerformanceMetrics {

    data class LatencyMeasurement(
        val operationName: String,
        val startTimeNs: Long,
        val endTimeNs: Long,
        val durationMs: Double,
        val threadName: String = Thread.currentThread().name
    ) {
        val durationNs: Long get() = endTimeNs - startTimeNs
    }

    data class PerformanceStats(
        val operationName: String,
        val count: Int,
        val averageMs: Double,
        val minMs: Double,
        val maxMs: Double,
        val p95Ms: Double,
        val p99Ms: Double,
        val totalMs: Double
    )

    data class FrameMetrics(
        val frameIndex: Long,
        val inferenceTimeMs: Double,
        val preprocessTimeMs: Double,
        val postprocessTimeMs: Double,
        val endToEndTimeMs: Double,
        val inputWidth: Int,
        val inputHeight: Int,
        val numDetectedPoses: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    companion object {
        private const val TAG = "PerformanceMetrics"
        private const val MAX_STORED_MEASUREMENTS = 1000
        private const val PERFETTO_SECTION_PREFIX = "PoseCoach"

        // 效能閾值 (ms)
        const val TARGET_INFERENCE_TIME_MS = 33.0 // ~30 FPS
        const val WARNING_INFERENCE_TIME_MS = 50.0 // ~20 FPS
        const val CRITICAL_INFERENCE_TIME_MS = 100.0 // ~10 FPS

        const val TARGET_END_TO_END_TIME_MS = 50.0
        const val WARNING_END_TO_END_TIME_MS = 100.0
        const val CRITICAL_END_TO_END_TIME_MS = 200.0
    }

    private val measurements = ConcurrentHashMap<String, MutableList<LatencyMeasurement>>()
    private val activeTraces = ConcurrentHashMap<String, Long>()

    private val _frameMetrics = MutableSharedFlow<FrameMetrics>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val frameMetrics: SharedFlow<FrameMetrics> = _frameMetrics.asSharedFlow()

    private val _performanceAlerts = MutableSharedFlow<PerformanceAlert>(
        replay = 0,
        extraBufferCapacity = 50
    )
    val performanceAlerts: SharedFlow<PerformanceAlert> = _performanceAlerts.asSharedFlow()

    data class PerformanceAlert(
        val type: AlertType,
        val operationName: String,
        val actualValue: Double,
        val thresholdValue: Double,
        val suggestion: String
    )

    enum class AlertType {
        WARNING, CRITICAL, IMPROVEMENT_SUGGESTION
    }

    /**
     * 開始追蹤操作效能
     */
    fun startTrace(operationName: String): String {
        val traceId = "${operationName}_${System.nanoTime()}"
        val startTime = System.nanoTime()

        activeTraces[traceId] = startTime

        // 整合 Systrace/Perfetto
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            Trace.beginSection("$PERFETTO_SECTION_PREFIX:$operationName")
        }

        Timber.v("Started trace: $operationName")
        return traceId
    }

    /**
     * 結束追蹤並記錄結果
     */
    fun endTrace(traceId: String, operationName: String = ""): LatencyMeasurement? {
        val endTime = System.nanoTime()
        val startTime = activeTraces.remove(traceId) ?: return null

        val actualOperationName = if (operationName.isNotEmpty()) operationName
                                  else traceId.substringBefore("_")

        val measurement = LatencyMeasurement(
            operationName = actualOperationName,
            startTimeNs = startTime,
            endTimeNs = endTime,
            durationMs = (endTime - startTime) / 1_000_000.0
        )

        // 結束 Systrace/Perfetto 追蹤
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            Trace.endSection()
        }

        recordMeasurement(measurement)
        checkPerformanceThresholds(measurement)

        return measurement
    }

    /**
     * 快速測量操作延遲
     */
    inline fun <T> measureOperation(operationName: String, operation: () -> T): T {
        val traceId = startTrace(operationName)
        return try {
            operation()
        } finally {
            endTrace(traceId, operationName)
        }
    }

    /**
     * 記錄單幀計量
     */
    fun recordFrameMetrics(
        frameIndex: Long,
        inferenceTimeMs: Double,
        preprocessTimeMs: Double = 0.0,
        postprocessTimeMs: Double = 0.0,
        inputWidth: Int,
        inputHeight: Int,
        numDetectedPoses: Int
    ) {
        val endToEndTimeMs = inferenceTimeMs + preprocessTimeMs + postprocessTimeMs

        val frameMetrics = FrameMetrics(
            frameIndex = frameIndex,
            inferenceTimeMs = inferenceTimeMs,
            preprocessTimeMs = preprocessTimeMs,
            postprocessTimeMs = postprocessTimeMs,
            endToEndTimeMs = endToEndTimeMs,
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            numDetectedPoses = numDetectedPoses
        )

        try {
            _frameMetrics.tryEmit(frameMetrics)
        } catch (e: Exception) {
            Timber.e(e, "Failed to emit frame metrics")
        }

        // 檢查效能警告
        checkFramePerformance(frameMetrics)

        Timber.v("Frame $frameIndex: inference=${inferenceTimeMs}ms, e2e=${endToEndTimeMs}ms, poses=$numDetectedPoses")
    }

    private fun recordMeasurement(measurement: LatencyMeasurement) {
        val operationMeasurements = measurements.getOrPut(measurement.operationName) {
            mutableListOf()
        }

        synchronized(operationMeasurements) {
            operationMeasurements.add(measurement)

            // 限制儲存的測量數量
            if (operationMeasurements.size > MAX_STORED_MEASUREMENTS) {
                operationMeasurements.removeAt(0)
            }
        }
    }

    private fun checkPerformanceThresholds(measurement: LatencyMeasurement) {
        val durationMs = measurement.durationMs

        when (measurement.operationName) {
            "pose_inference" -> {
                when {
                    durationMs > CRITICAL_INFERENCE_TIME_MS -> {
                        emitAlert(AlertType.CRITICAL, measurement.operationName, durationMs,
                                CRITICAL_INFERENCE_TIME_MS, "考慮降低輸入解析度或啟用模型量化")
                    }
                    durationMs > WARNING_INFERENCE_TIME_MS -> {
                        emitAlert(AlertType.WARNING, measurement.operationName, durationMs,
                                WARNING_INFERENCE_TIME_MS, "推論時間過長，可能影響即時性")
                    }
                }
            }
            "end_to_end" -> {
                when {
                    durationMs > CRITICAL_END_TO_END_TIME_MS -> {
                        emitAlert(AlertType.CRITICAL, measurement.operationName, durationMs,
                                CRITICAL_END_TO_END_TIME_MS, "端到端延遲過高，建議啟用降級策略")
                    }
                    durationMs > WARNING_END_TO_END_TIME_MS -> {
                        emitAlert(AlertType.WARNING, measurement.operationName, durationMs,
                                WARNING_END_TO_END_TIME_MS, "端到端延遲偏高")
                    }
                }
            }
        }
    }

    private fun checkFramePerformance(frame: FrameMetrics) {
        // 檢查推論時間
        if (frame.inferenceTimeMs > WARNING_INFERENCE_TIME_MS) {
            val suggestion = when {
                frame.inputWidth * frame.inputHeight > 640 * 480 -> "建議降低輸入解析度"
                frame.numDetectedPoses > 1 -> "多人偵測影響效能，考慮限制偵測人數"
                else -> "考慮啟用模型優化或降級策略"
            }

            emitAlert(
                if (frame.inferenceTimeMs > CRITICAL_INFERENCE_TIME_MS) AlertType.CRITICAL else AlertType.WARNING,
                "frame_inference",
                frame.inferenceTimeMs,
                if (frame.inferenceTimeMs > CRITICAL_INFERENCE_TIME_MS) CRITICAL_INFERENCE_TIME_MS else WARNING_INFERENCE_TIME_MS,
                suggestion
            )
        }

        // 檢查端到端時間
        if (frame.endToEndTimeMs > WARNING_END_TO_END_TIME_MS) {
            emitAlert(
                if (frame.endToEndTimeMs > CRITICAL_END_TO_END_TIME_MS) AlertType.CRITICAL else AlertType.WARNING,
                "frame_end_to_end",
                frame.endToEndTimeMs,
                if (frame.endToEndTimeMs > CRITICAL_END_TO_END_TIME_MS) CRITICAL_END_TO_END_TIME_MS else WARNING_END_TO_END_TIME_MS,
                "端到端延遲過高，建議啟用效能優化"
            )
        }
    }

    private fun emitAlert(type: AlertType, operationName: String, actualValue: Double,
                         thresholdValue: Double, suggestion: String) {
        val alert = PerformanceAlert(type, operationName, actualValue, thresholdValue, suggestion)
        try {
            _performanceAlerts.tryEmit(alert)
        } catch (e: Exception) {
            Timber.e(e, "Failed to emit performance alert")
        }
    }

    /**
     * 獲取操作統計資料
     */
    fun getPerformanceStats(operationName: String): PerformanceStats? {
        val operationMeasurements = measurements[operationName] ?: return null

        synchronized(operationMeasurements) {
            if (operationMeasurements.isEmpty()) return null

            val durations = operationMeasurements.map { it.durationMs }.sorted()
            val count = durations.size
            val total = durations.sum()
            val average = total / count
            val min = durations.minOrNull() ?: 0.0
            val max = durations.maxOrNull() ?: 0.0
            val p95 = durations.getOrNull((count * 0.95).toInt()) ?: max
            val p99 = durations.getOrNull((count * 0.99).toInt()) ?: max

            return PerformanceStats(
                operationName = operationName,
                count = count,
                averageMs = average,
                minMs = min,
                maxMs = max,
                p95Ms = p95,
                p99Ms = p99,
                totalMs = total
            )
        }
    }

    /**
     * 獲取所有操作的統計資料
     */
    fun getAllPerformanceStats(): Map<String, PerformanceStats> {
        return measurements.keys.mapNotNull { operationName ->
            getPerformanceStats(operationName)?.let { operationName to it }
        }.toMap()
    }

    /**
     * 清除指定操作的計量資料
     */
    fun clearMeasurements(operationName: String) {
        measurements[operationName]?.clear()
    }

    /**
     * 清除所有計量資料
     */
    fun clearAllMeasurements() {
        measurements.clear()
        activeTraces.clear()
    }

    /**
     * 產生效能報告
     */
    fun generatePerformanceReport(): String {
        val stats = getAllPerformanceStats()
        if (stats.isEmpty()) return "無效能資料"

        return buildString {
            appendLine("=== 效能報告 ===")
            appendLine()

            stats.forEach { (operationName, stat) ->
                appendLine("操作: $operationName")
                appendLine("  執行次數: ${stat.count}")
                appendLine("  平均時間: ${"%.2f".format(stat.averageMs)}ms")
                appendLine("  最小時間: ${"%.2f".format(stat.minMs)}ms")
                appendLine("  最大時間: ${"%.2f".format(stat.maxMs)}ms")
                appendLine("  P95 時間: ${"%.2f".format(stat.p95Ms)}ms")
                appendLine("  P99 時間: ${"%.2f".format(stat.p99Ms)}ms")
                appendLine()
            }

            // 效能建議
            appendLine("=== 效能建議 ===")
            stats["pose_inference"]?.let { inferenceStats ->
                when {
                    inferenceStats.averageMs > CRITICAL_INFERENCE_TIME_MS ->
                        appendLine("⚠️ 推論時間過長 (${inferenceStats.averageMs}ms)，強烈建議啟用降級策略")
                    inferenceStats.averageMs > WARNING_INFERENCE_TIME_MS ->
                        appendLine("⚠️ 推論時間偏高 (${inferenceStats.averageMs}ms)，考慮優化")
                    else ->
                        appendLine("✅ 推論效能良好 (${inferenceStats.averageMs}ms)")
                }
            }

            stats["end_to_end"]?.let { e2eStats ->
                when {
                    e2eStats.averageMs > CRITICAL_END_TO_END_TIME_MS ->
                        appendLine("⚠️ 端到端延遲過高 (${e2eStats.averageMs}ms)")
                    e2eStats.averageMs > WARNING_END_TO_END_TIME_MS ->
                        appendLine("⚠️ 端到端延遲偏高 (${e2eStats.averageMs}ms)")
                    else ->
                        appendLine("✅ 端到端效能良好 (${e2eStats.averageMs}ms)")
                }
            }
        }
    }

    /**
     * 記錄自訂計量
     */
    fun recordCustomMetric(name: String, value: Double, unit: String = "ms") {
        val measurement = LatencyMeasurement(
            operationName = name,
            startTimeNs = 0,
            endTimeNs = (value * 1_000_000).toLong(),
            durationMs = value
        )
        recordMeasurement(measurement)
        Timber.d("Custom metric: $name = $value $unit")
    }
}