package com.posecoach.corepose

import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.utils.PoseDeltaCalculator
import timber.log.Timber

class EnhancedStablePoseGate(
    private val windowSec: Double = 1.5,
    private val posThreshold: Float = 0.02f,
    private val angleThresholdDeg: Float = 5.0f,
    private val minVisibility: Float = 0.5f
) {
    private val deltaCalculator = PoseDeltaCalculator()
    private var accumulatedTime = 0.0
    private var lastTimestampMs: Long? = null
    private var isStable = false
    private var hasTriggered = false

    data class StabilityResult(
        val isStable: Boolean,
        val justTriggered: Boolean,
        val stabilityDuration: Double,
        val positionDelta: Float,
        val angleDelta: Float,
        val confidenceScore: Float
    )

    fun update(landmarks: PoseLandmarkResult): StabilityResult {
        val currentTime = landmarks.timestampMs
        val lastTime = lastTimestampMs
        lastTimestampMs = currentTime

        val visibleCount = landmarks.landmarks.count { it.visibility >= minVisibility }
        val confidenceScore = visibleCount / 33.0f

        if (lastTime == null || confidenceScore < 0.3f) {
            reset()
            return StabilityResult(
                isStable = false,
                justTriggered = false,
                stabilityDuration = 0.0,
                positionDelta = Float.MAX_VALUE,
                angleDelta = Float.MAX_VALUE,
                confidenceScore = confidenceScore
            )
        }

        val deltaTime = (currentTime - lastTime) / 1000.0
        if (deltaTime > 1.0) {
            reset()
            return StabilityResult(
                isStable = false,
                justTriggered = false,
                stabilityDuration = 0.0,
                positionDelta = Float.MAX_VALUE,
                angleDelta = Float.MAX_VALUE,
                confidenceScore = confidenceScore
            )
        }

        val delta = deltaCalculator.calculateDelta(landmarks)
        val currentlyStable = delta.positionDelta <= posThreshold &&
                            delta.angleDelta <= angleThresholdDeg

        if (currentlyStable) {
            accumulatedTime += deltaTime
            isStable = true
        } else {
            reset()
        }

        val justTriggered = !hasTriggered && accumulatedTime >= windowSec
        if (justTriggered) {
            hasTriggered = true
            Timber.d("Stable pose detected after ${accumulatedTime}s")
        }

        return StabilityResult(
            isStable = isStable,
            justTriggered = justTriggered,
            stabilityDuration = accumulatedTime,
            positionDelta = delta.positionDelta,
            angleDelta = delta.angleDelta,
            confidenceScore = confidenceScore
        )
    }

    fun reset() {
        accumulatedTime = 0.0
        isStable = false
        hasTriggered = false
        deltaCalculator.reset()
    }

    fun isCurrentlyStable(): Boolean = isStable

    fun getStabilityDuration(): Double = accumulatedTime
}