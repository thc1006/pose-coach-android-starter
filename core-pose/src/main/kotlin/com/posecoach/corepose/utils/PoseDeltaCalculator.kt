package com.posecoach.corepose.utils

import com.posecoach.corepose.models.PoseLandmarkResult
import kotlin.math.*

class PoseDeltaCalculator {

    data class PoseDelta(
        val positionDelta: Float,
        val angleDelta: Float,
        val keyPointsMovement: Map<Int, Float>
    )

    companion object {
        private val KEY_LANDMARKS = listOf(
            11, 12,  // Shoulders
            13, 14,  // Elbows
            15, 16,  // Wrists
            23, 24,  // Hips
            25, 26,  // Knees
            27, 28   // Ankles
        )

        private val ANGLE_LANDMARKS = listOf(
            Triple(11, 13, 15),  // Left arm
            Triple(12, 14, 16),  // Right arm
            Triple(23, 25, 27),  // Left leg
            Triple(24, 26, 28)   // Right leg
        )
    }

    private var previousLandmarks: PoseLandmarkResult? = null

    fun calculateDelta(currentLandmarks: PoseLandmarkResult): PoseDelta {
        val previous = previousLandmarks
        previousLandmarks = currentLandmarks

        if (previous == null) {
            return PoseDelta(0f, 0f, emptyMap())
        }

        val positionDelta = calculatePositionDelta(previous, currentLandmarks)
        val angleDelta = calculateAngleDelta(previous, currentLandmarks)
        val keyPointsMovement = calculateKeyPointsMovement(previous, currentLandmarks)

        return PoseDelta(positionDelta, angleDelta, keyPointsMovement)
    }

    private fun calculatePositionDelta(
        previous: PoseLandmarkResult,
        current: PoseLandmarkResult
    ): Float {
        var totalDelta = 0f
        var count = 0

        KEY_LANDMARKS.forEach { idx ->
            if (idx < previous.landmarks.size && idx < current.landmarks.size) {
                val prevLandmark = previous.landmarks[idx]
                val currLandmark = current.landmarks[idx]

                if (prevLandmark.visibility > 0.5f && currLandmark.visibility > 0.5f) {
                    val dx = currLandmark.x - prevLandmark.x
                    val dy = currLandmark.y - prevLandmark.y
                    val dz = currLandmark.z - prevLandmark.z

                    totalDelta += sqrt(dx * dx + dy * dy + dz * dz)
                    count++
                }
            }
        }

        return if (count > 0) totalDelta / count else 0f
    }

    private fun calculateAngleDelta(
        previous: PoseLandmarkResult,
        current: PoseLandmarkResult
    ): Float {
        var totalAngleDelta = 0f
        var count = 0

        ANGLE_LANDMARKS.forEach { (start, middle, end) ->
            val prevAngle = calculateAngle(
                previous.landmarks.getOrNull(start),
                previous.landmarks.getOrNull(middle),
                previous.landmarks.getOrNull(end)
            )

            val currAngle = calculateAngle(
                current.landmarks.getOrNull(start),
                current.landmarks.getOrNull(middle),
                current.landmarks.getOrNull(end)
            )

            if (prevAngle != null && currAngle != null) {
                var delta = abs(currAngle - prevAngle)
                if (delta > 180f) {
                    delta = 360f - delta
                }
                totalAngleDelta += delta
                count++
            }
        }

        return if (count > 0) totalAngleDelta / count else 0f
    }

    private fun calculateAngle(
        start: PoseLandmarkResult.Landmark?,
        middle: PoseLandmarkResult.Landmark?,
        end: PoseLandmarkResult.Landmark?
    ): Float? {
        if (start == null || middle == null || end == null) return null
        if (start.visibility < 0.5f || middle.visibility < 0.5f || end.visibility < 0.5f) return null

        val v1x = start.x - middle.x
        val v1y = start.y - middle.y
        val v2x = end.x - middle.x
        val v2y = end.y - middle.y

        val dot = v1x * v2x + v1y * v2y
        val det = v1x * v2y - v1y * v2x
        val angle = atan2(det, dot) * 180f / PI.toFloat()

        return if (angle < 0) angle + 360f else angle
    }

    private fun calculateKeyPointsMovement(
        previous: PoseLandmarkResult,
        current: PoseLandmarkResult
    ): Map<Int, Float> {
        val movements = mutableMapOf<Int, Float>()

        KEY_LANDMARKS.forEach { idx ->
            if (idx < previous.landmarks.size && idx < current.landmarks.size) {
                val prevLandmark = previous.landmarks[idx]
                val currLandmark = current.landmarks[idx]

                if (prevLandmark.visibility > 0.5f && currLandmark.visibility > 0.5f) {
                    val dx = currLandmark.x - prevLandmark.x
                    val dy = currLandmark.y - prevLandmark.y
                    movements[idx] = sqrt(dx * dx + dy * dy)
                }
            }
        }

        return movements
    }

    fun reset() {
        previousLandmarks = null
    }
}