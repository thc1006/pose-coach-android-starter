package com.posecoach.corepose.utils

import com.posecoach.corepose.utils.PerformanceTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Extension function to calculate average of a list of Double values
 */
fun List<Double>.averageOrNull(): Double? {
    return if (isEmpty()) null else average()
}

/**
 * Extension function to calculate average of a list of Float values
 */
fun List<Float>.averageOrNull(): Float? {
    return if (isEmpty()) null else average().toFloat()
}

/**
 * Extension function to measure operation performance
 */
suspend fun <T> PerformanceTracker.measureOperation(
    operationName: String,
    operation: suspend () -> T
): T = coroutineScope {
    val startTime = System.nanoTime()
    try {
        operation()
    } finally {
        val durationMs = (System.nanoTime() - startTime) / 1_000_000
        recordInferenceTime(durationMs)
    }
}

/**
 * Convert PerformanceMetrics to Map for compatibility
 */
fun PerformanceTracker.PerformanceMetrics.toMap(): Map<String, Double> {
    return mapOf(
        "avgProcessingTime" to this.avgInferenceTimeMs,
        "maxProcessingTime" to this.maxInferenceTimeMs.toDouble(),
        "minProcessingTime" to this.minInferenceTimeMs.toDouble(),
        "avgFps" to this.avgFps,
        "droppedFrames" to this.droppedFrames.toDouble(),
        "isPerformanceGood" to if (this.isPerformanceGood) 1.0 else 0.0
    )
}