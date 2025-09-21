package com.posecoach.corepose.utils

import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.roundToInt

class PerformanceTracker(
    private val windowSize: Int = 30,
    private val targetFps: Int = 30
) {
    private val inferenceTimesMs = ConcurrentLinkedQueue<Long>()
    private val frameTimesMs = ConcurrentLinkedQueue<Long>()
    private var lastFrameTimeMs = 0L
    private val targetFrameTimeMs = 1000L / targetFps

    data class PerformanceMetrics(
        val avgInferenceTimeMs: Double,
        val minInferenceTimeMs: Long,
        val maxInferenceTimeMs: Long,
        val avgFps: Double,
        val droppedFrames: Int,
        val isPerformanceGood: Boolean
    )

    fun recordInferenceTime(inferenceTimeMs: Long) {
        inferenceTimesMs.add(inferenceTimeMs)
        while (inferenceTimesMs.size > windowSize) {
            inferenceTimesMs.poll()
        }

        val currentTime = System.currentTimeMillis()
        if (lastFrameTimeMs != 0L) {
            val frameTime = currentTime - lastFrameTimeMs
            frameTimesMs.add(frameTime)
            while (frameTimesMs.size > windowSize) {
                frameTimesMs.poll()
            }

            if (frameTime > targetFrameTimeMs * 1.5) {
                Timber.w("Frame dropped! Time: ${frameTime}ms > ${targetFrameTimeMs * 1.5}ms")
            }
        }
        lastFrameTimeMs = currentTime

        logPerformance(inferenceTimeMs)
    }

    fun getMetrics(): PerformanceMetrics {
        val inferenceTimes = inferenceTimesMs.toList()
        val frameTimes = frameTimesMs.toList()

        if (inferenceTimes.isEmpty()) {
            return PerformanceMetrics(0.0, 0, 0, 0.0, 0, false)
        }

        val avgInference = inferenceTimes.average()
        val minInference = inferenceTimes.minOrNull() ?: 0
        val maxInference = inferenceTimes.maxOrNull() ?: 0

        val avgFrameTime = if (frameTimes.isNotEmpty()) frameTimes.average() else 0.0
        val avgFps = if (avgFrameTime > 0) 1000.0 / avgFrameTime else 0.0

        val droppedFrames = frameTimes.count { it > targetFrameTimeMs * 1.5 }
        val isPerformanceGood = avgInference < targetFrameTimeMs && avgFps >= targetFps * 0.9

        return PerformanceMetrics(
            avgInferenceTimeMs = avgInference,
            minInferenceTimeMs = minInference,
            maxInferenceTimeMs = maxInference,
            avgFps = avgFps,
            droppedFrames = droppedFrames,
            isPerformanceGood = isPerformanceGood
        )
    }

    fun reset() {
        inferenceTimesMs.clear()
        frameTimesMs.clear()
        lastFrameTimeMs = 0L
    }

    private fun logPerformance(currentInferenceMs: Long) {
        val metrics = getMetrics()

        val status = when {
            currentInferenceMs < targetFrameTimeMs * 0.5 -> "✓ EXCELLENT"
            currentInferenceMs < targetFrameTimeMs -> "✓ GOOD"
            currentInferenceMs < targetFrameTimeMs * 1.5 -> "⚠ WARNING"
            else -> "✗ POOR"
        }

        Timber.d(
            "Pose inference: ${currentInferenceMs}ms $status | " +
            "Avg: ${metrics.avgInferenceTimeMs.roundToInt()}ms | " +
            "FPS: ${metrics.avgFps.roundToInt()} | " +
            "Target: <${targetFrameTimeMs}ms@${targetFps}fps"
        )

        if (inferenceTimesMs.size >= windowSize && !metrics.isPerformanceGood) {
            Timber.w(
                "Performance degraded! Avg inference: ${metrics.avgInferenceTimeMs.roundToInt()}ms, " +
                "FPS: ${metrics.avgFps.roundToInt()}, Dropped frames: ${metrics.droppedFrames}"
            )
        }
    }

    fun logSummary() {
        val metrics = getMetrics()
        Timber.i(
            """
            === Performance Summary (${windowSize} frames) ===
            Inference: avg=${metrics.avgInferenceTimeMs.roundToInt()}ms,
                      min=${metrics.minInferenceTimeMs}ms,
                      max=${metrics.maxInferenceTimeMs}ms
            FPS: ${metrics.avgFps.roundToInt()} (target: ${targetFps})
            Dropped frames: ${metrics.droppedFrames}
            Status: ${if (metrics.isPerformanceGood) "✓ GOOD" else "✗ NEEDS OPTIMIZATION"}
            """.trimIndent()
        )
    }
}