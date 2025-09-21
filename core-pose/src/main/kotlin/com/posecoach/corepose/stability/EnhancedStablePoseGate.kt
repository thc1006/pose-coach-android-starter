package com.posecoach.corepose.stability

import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.PoseLandmarks
import timber.log.Timber
import kotlin.math.*

/**
 * Enhanced pose stability detection with advanced algorithms.
 * Provides more sophisticated stability analysis beyond simple position/angle thresholds.
 */
class EnhancedStablePoseGate(
    private val config: StabilityConfig = StabilityConfig()
) {

    private val positionHistory = mutableListOf<PoseLandmarkResult>()
    private val velocityHistory = mutableListOf<VelocityData>()
    private val accelerationHistory = mutableListOf<AccelerationData>()

    private var accumulatedStableTime = 0.0
    private var lastTimestamp = 0L
    private var isCurrentlyStable = false
    private var lastTriggerTime = 0L

    data class StabilityConfig(
        val windowSec: Double = 1.5,
        val positionThresholdNormalized: Double = 0.01, // Normalized screen units
        val velocityThresholdPerSec: Double = 0.02, // Normalized units per second
        val accelerationThresholdPerSec2: Double = 0.05, // Normalized units per second squared
        val angleThresholdDeg: Double = 5.0,
        val minHistorySize: Int = 5,
        val maxHistorySize: Int = 30,
        val keyPointWeights: Map<Int, Double> = defaultKeyPointWeights(),
        val enableAdvancedMetrics: Boolean = true,
        val stabilityScoreThreshold: Double = 0.85
    )

    data class StabilityResult(
        val isStable: Boolean,
        val justTriggered: Boolean,
        val stabilityScore: Double,
        val metrics: StabilityMetrics
    )

    data class StabilityMetrics(
        val positionStability: Double,
        val velocityStability: Double,
        val accelerationStability: Double,
        val angularStability: Double,
        val overallScore: Double,
        val timeStable: Double,
        val keyPointStabilities: Map<Int, Double>
    )

    private data class VelocityData(
        val timestamp: Long,
        val velocities: Map<Int, Vector3D>,
        val overallMagnitude: Double
    )

    private data class AccelerationData(
        val timestamp: Long,
        val accelerations: Map<Int, Vector3D>,
        val overallMagnitude: Double
    )

    private data class Vector3D(val x: Double, val y: Double, val z: Double) {
        val magnitude: Double get() = sqrt(x*x + y*y + z*z)

        operator fun minus(other: Vector3D) = Vector3D(x - other.x, y - other.y, z - other.z)
        operator fun plus(other: Vector3D) = Vector3D(x + other.x, y + other.y, z + other.z)
        operator fun times(scalar: Double) = Vector3D(x * scalar, y * scalar, z * scalar)
    }

    /**
     * Update stability analysis with new pose data.
     */
    fun update(poseResult: PoseLandmarkResult): StabilityResult {
        val currentTime = poseResult.timestampMs

        // Initialize on first frame
        if (lastTimestamp == 0L) {
            lastTimestamp = currentTime
            positionHistory.add(poseResult)
            return StabilityResult(
                isStable = false,
                justTriggered = false,
                stabilityScore = 0.0,
                metrics = createEmptyMetrics()
            )
        }

        val deltaTime = (currentTime - lastTimestamp) / 1000.0 // Convert to seconds
        lastTimestamp = currentTime

        // Add to history and maintain size limits
        positionHistory.add(poseResult)
        if (positionHistory.size > config.maxHistorySize) {
            positionHistory.removeAt(0)
        }

        // Calculate motion derivatives
        updateVelocityHistory(deltaTime)
        updateAccelerationHistory(deltaTime)

        // Calculate stability metrics
        val metrics = calculateStabilityMetrics()
        val overallScore = metrics.overallScore
        val isStable = overallScore >= config.stabilityScoreThreshold

        // Update stability state
        val wasStable = isCurrentlyStable
        isCurrentlyStable = isStable

        if (isStable) {
            accumulatedStableTime += deltaTime
        } else {
            accumulatedStableTime = 0.0
        }

        // Check if stability window is reached
        val justTriggered = accumulatedStableTime >= config.windowSec &&
                           (accumulatedStableTime - deltaTime) < config.windowSec &&
                           (currentTime - lastTriggerTime) > (config.windowSec * 1000)

        if (justTriggered) {
            lastTriggerTime = currentTime
            Timber.i("Pose stability achieved! Score: ${String.format("%.3f", overallScore)}, Time: ${String.format("%.1f", accumulatedStableTime)}s")
        }

        return StabilityResult(
            isStable = isStable,
            justTriggered = justTriggered,
            stabilityScore = overallScore,
            metrics = metrics
        )
    }

    /**
     * Get current stability state without updating.
     */
    fun getCurrentStability(): StabilityResult? {
        if (positionHistory.isEmpty()) return null

        val metrics = calculateStabilityMetrics()
        val overallScore = metrics.overallScore

        return StabilityResult(
            isStable = overallScore >= config.stabilityScoreThreshold,
            justTriggered = false,
            stabilityScore = overallScore,
            metrics = metrics
        )
    }

    /**
     * Reset all stability tracking.
     */
    fun reset() {
        positionHistory.clear()
        velocityHistory.clear()
        accelerationHistory.clear()
        accumulatedStableTime = 0.0
        lastTimestamp = 0L
        isCurrentlyStable = false
        lastTriggerTime = 0L
        Timber.d("Enhanced stability gate reset")
    }

    private fun updateVelocityHistory(deltaTime: Double) {
        if (positionHistory.size < 2) return

        val current = positionHistory.last()
        val previous = positionHistory[positionHistory.size - 2]

        val velocities = mutableMapOf<Int, Vector3D>()
        var totalMagnitude = 0.0

        for (i in current.landmarks.indices) {
            if (i < previous.landmarks.size) {
                val currentLandmark = current.landmarks[i]
                val previousLandmark = previous.landmarks[i]

                val velocity = Vector3D(
                    x = (currentLandmark.x - previousLandmark.x).toDouble() / deltaTime,
                    y = (currentLandmark.y - previousLandmark.y).toDouble() / deltaTime,
                    z = (currentLandmark.z - previousLandmark.z).toDouble() / deltaTime
                )

                velocities[i] = velocity
                totalMagnitude += velocity.magnitude * (config.keyPointWeights[i] ?: 1.0)
            }
        }

        velocityHistory.add(VelocityData(current.timestampMs, velocities, totalMagnitude))

        if (velocityHistory.size > config.maxHistorySize) {
            velocityHistory.removeAt(0)
        }
    }

    private fun updateAccelerationHistory(deltaTime: Double) {
        if (velocityHistory.size < 2) return

        val current = velocityHistory.last()
        val previous = velocityHistory[velocityHistory.size - 2]

        val accelerations = mutableMapOf<Int, Vector3D>()
        var totalMagnitude = 0.0

        for ((landmarkIndex, currentVelocity) in current.velocities) {
            val previousVelocity = previous.velocities[landmarkIndex]
            if (previousVelocity != null) {
                val acceleration = (currentVelocity - previousVelocity) * (1.0 / deltaTime)
                accelerations[landmarkIndex] = acceleration
                totalMagnitude += acceleration.magnitude * (config.keyPointWeights[landmarkIndex] ?: 1.0)
            }
        }

        accelerationHistory.add(AccelerationData(current.timestamp, accelerations, totalMagnitude))

        if (accelerationHistory.size > config.maxHistorySize) {
            accelerationHistory.removeAt(0)
        }
    }

    private fun calculateStabilityMetrics(): StabilityMetrics {
        if (positionHistory.size < config.minHistorySize) {
            return createEmptyMetrics()
        }

        val positionStability = calculatePositionStability()
        val velocityStability = calculateVelocityStability()
        val accelerationStability = calculateAccelerationStability()
        val angularStability = calculateAngularStability()
        val keyPointStabilities = calculateKeyPointStabilities()

        // Weighted overall score
        val overallScore = if (config.enableAdvancedMetrics) {
            (positionStability * 0.3 +
             velocityStability * 0.3 +
             accelerationStability * 0.2 +
             angularStability * 0.2)
        } else {
            (positionStability * 0.6 + velocityStability * 0.4)
        }

        return StabilityMetrics(
            positionStability = positionStability,
            velocityStability = velocityStability,
            accelerationStability = accelerationStability,
            angularStability = angularStability,
            overallScore = overallScore,
            timeStable = accumulatedStableTime,
            keyPointStabilities = keyPointStabilities
        )
    }

    private fun calculatePositionStability(): Double {
        if (positionHistory.size < 2) return 0.0

        val recentPoses = positionHistory.takeLast(config.minHistorySize)
        val referenceIndex = recentPoses.size / 2 // Use middle pose as reference
        val reference = recentPoses[referenceIndex]

        var totalDeviation = 0.0
        var weightSum = 0.0

        for (pose in recentPoses) {
            for (i in pose.landmarks.indices) {
                if (i < reference.landmarks.size) {
                    val current = pose.landmarks[i]
                    val ref = reference.landmarks[i]

                    val deviation = sqrt(
                        (current.x - ref.x).toDouble().pow(2) +
                        (current.y - ref.y).toDouble().pow(2) +
                        (current.z - ref.z).toDouble().pow(2)
                    )

                    val weight = config.keyPointWeights[i] ?: 1.0
                    totalDeviation += deviation * weight
                    weightSum += weight
                }
            }
        }

        val avgDeviation = if (weightSum > 0) totalDeviation / weightSum else 1.0
        val stability = 1.0 - (avgDeviation / config.positionThresholdNormalized).coerceIn(0.0, 1.0)

        return stability.coerceIn(0.0, 1.0)
    }

    private fun calculateVelocityStability(): Double {
        if (velocityHistory.isEmpty()) return 0.0

        val recentVelocities = velocityHistory.takeLast(min(config.minHistorySize, velocityHistory.size))
        val avgMagnitude = recentVelocities.map { it.overallMagnitude }.average()

        val stability = 1.0 - (avgMagnitude / config.velocityThresholdPerSec).coerceIn(0.0, 1.0)
        return stability.coerceIn(0.0, 1.0)
    }

    private fun calculateAccelerationStability(): Double {
        if (accelerationHistory.isEmpty()) return 1.0 // No acceleration data means stable

        val recentAccelerations = accelerationHistory.takeLast(min(config.minHistorySize, accelerationHistory.size))
        val avgMagnitude = recentAccelerations.map { it.overallMagnitude }.average()

        val stability = 1.0 - (avgMagnitude / config.accelerationThresholdPerSec2).coerceIn(0.0, 1.0)
        return stability.coerceIn(0.0, 1.0)
    }

    private fun calculateAngularStability(): Double {
        if (positionHistory.size < 3) return 1.0

        val recentPoses = positionHistory.takeLast(config.minHistorySize)
        val angles = mutableListOf<Double>()

        // Calculate key body angles (shoulder-hip-knee, etc.)
        for (pose in recentPoses) {
            try {
                // Shoulder-Hip-Knee angle (example)
                val leftShoulder = pose.landmarks[PoseLandmarks.LEFT_SHOULDER]
                val leftHip = pose.landmarks[PoseLandmarks.LEFT_HIP]
                val leftKnee = pose.landmarks[PoseLandmarks.LEFT_KNEE]

                val angle = calculateAngle(leftShoulder, leftHip, leftKnee)
                if (!angle.isNaN()) {
                    angles.add(angle)
                }
            } catch (e: IndexOutOfBoundsException) {
                // Silently continue if landmarks are missing
            }
        }

        if (angles.isEmpty()) return 1.0

        val angleVariation = if (angles.size > 1) {
            val mean = angles.average()
            val variance = angles.map { (it - mean).pow(2) }.average()
            sqrt(variance)
        } else {
            0.0
        }

        val stability = 1.0 - (angleVariation / config.angleThresholdDeg).coerceIn(0.0, 1.0)
        return stability.coerceIn(0.0, 1.0)
    }

    private fun calculateKeyPointStabilities(): Map<Int, Double> {
        if (positionHistory.size < config.minHistorySize) return emptyMap()

        val stabilities = mutableMapOf<Int, Double>()
        val recentPoses = positionHistory.takeLast(config.minHistorySize)

        for (landmarkIndex in 0 until 33) { // MediaPipe has 33 landmarks
            val positions = recentPoses.mapNotNull { pose ->
                if (landmarkIndex < pose.landmarks.size) {
                    val landmark = pose.landmarks[landmarkIndex]
                    Vector3D(landmark.x.toDouble(), landmark.y.toDouble(), landmark.z.toDouble())
                } else null
            }

            if (positions.size >= config.minHistorySize) {
                val centroid = positions.reduce { acc, pos -> acc + pos } * (1.0 / positions.size)
                val avgDeviation = positions.map { (it - centroid).magnitude }.average()
                val stability = 1.0 - (avgDeviation / config.positionThresholdNormalized).coerceIn(0.0, 1.0)
                stabilities[landmarkIndex] = stability.coerceIn(0.0, 1.0)
            }
        }

        return stabilities
    }

    private fun calculateAngle(p1: PoseLandmarkResult.Landmark, p2: PoseLandmarkResult.Landmark, p3: PoseLandmarkResult.Landmark): Double {
        val v1 = Vector3D(
            (p1.x - p2.x).toDouble(),
            (p1.y - p2.y).toDouble(),
            (p1.z - p2.z).toDouble()
        )
        val v2 = Vector3D(
            (p3.x - p2.x).toDouble(),
            (p3.y - p2.y).toDouble(),
            (p3.z - p2.z).toDouble()
        )

        val dotProduct = v1.x * v2.x + v1.y * v2.y + v1.z * v2.z
        val angle = acos((dotProduct / (v1.magnitude * v2.magnitude)).coerceIn(-1.0, 1.0))
        return Math.toDegrees(angle)
    }

    private fun createEmptyMetrics(): StabilityMetrics {
        return StabilityMetrics(
            positionStability = 0.0,
            velocityStability = 0.0,
            accelerationStability = 0.0,
            angularStability = 0.0,
            overallScore = 0.0,
            timeStable = accumulatedStableTime,
            keyPointStabilities = emptyMap()
        )
    }

    companion object {
        private fun defaultKeyPointWeights(): Map<Int, Double> {
            return mapOf(
                // Head and face (lower weight for stability)
                PoseLandmarks.NOSE to 0.3,
                PoseLandmarks.LEFT_EYE to 0.2,
                PoseLandmarks.RIGHT_EYE to 0.2,
                PoseLandmarks.LEFT_EAR to 0.2,
                PoseLandmarks.RIGHT_EAR to 0.2,

                // Core body (high weight)
                PoseLandmarks.LEFT_SHOULDER to 1.5,
                PoseLandmarks.RIGHT_SHOULDER to 1.5,
                PoseLandmarks.LEFT_HIP to 1.8,
                PoseLandmarks.RIGHT_HIP to 1.8,

                // Arms (medium weight)
                PoseLandmarks.LEFT_ELBOW to 1.0,
                PoseLandmarks.RIGHT_ELBOW to 1.0,
                PoseLandmarks.LEFT_WRIST to 0.8,
                PoseLandmarks.RIGHT_WRIST to 0.8,

                // Legs (high weight for stability)
                PoseLandmarks.LEFT_KNEE to 1.3,
                PoseLandmarks.RIGHT_KNEE to 1.3,
                PoseLandmarks.LEFT_ANKLE to 1.1,
                PoseLandmarks.RIGHT_ANKLE to 1.1,

                // Hands and feet (lower weight)
                PoseLandmarks.LEFT_PINKY to 0.4,
                PoseLandmarks.RIGHT_PINKY to 0.4,
                PoseLandmarks.LEFT_INDEX to 0.4,
                PoseLandmarks.RIGHT_INDEX to 0.4,
                PoseLandmarks.LEFT_THUMB to 0.4,
                PoseLandmarks.RIGHT_THUMB to 0.4,
                PoseLandmarks.LEFT_HEEL to 0.6,
                PoseLandmarks.RIGHT_HEEL to 0.6,
                PoseLandmarks.LEFT_FOOT_INDEX to 0.5,
                PoseLandmarks.RIGHT_FOOT_INDEX to 0.5
            )
        }
    }
}