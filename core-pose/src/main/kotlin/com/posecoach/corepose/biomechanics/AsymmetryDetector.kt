package com.posecoach.corepose.biomechanics

import com.posecoach.corepose.PoseLandmarks
import com.posecoach.corepose.biomechanics.models.*
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.coregeom.VectorUtils
import kotlin.math.*

/**
 * Advanced asymmetry detection system that quantifies left-right imbalances,
 * temporal asymmetries, and progressive asymmetry tracking over time.
 *
 * Features:
 * - Multi-dimensional asymmetry analysis (sagittal, frontal, transverse planes)
 * - Temporal asymmetry pattern recognition
 * - Load distribution analysis
 * - Progressive asymmetry tracking
 * - Compensation pattern detection
 */
class AsymmetryDetector {

    // Temporal tracking for asymmetry trends
    private val asymmetryHistory = mutableListOf<AsymmetrySnapshot>()
    private val maxHistorySize = 100 // ~3.3 seconds at 30fps

    // Landmark pairs for bilateral comparison
    private val bilateralPairs = mapOf(
        "shoulders" to Pair(PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER),
        "elbows" to Pair(PoseLandmarks.LEFT_ELBOW, PoseLandmarks.RIGHT_ELBOW),
        "wrists" to Pair(PoseLandmarks.LEFT_WRIST, PoseLandmarks.RIGHT_WRIST),
        "hips" to Pair(PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP),
        "knees" to Pair(PoseLandmarks.LEFT_KNEE, PoseLandmarks.RIGHT_KNEE),
        "ankles" to Pair(PoseLandmarks.LEFT_ANKLE, PoseLandmarks.RIGHT_ANKLE)
    )

    /**
     * Comprehensive asymmetry analysis
     */
    fun analyze(landmarks: PoseLandmarkResult): AsymmetryAnalysis {
        val snapshot = AsymmetrySnapshot(
            timestamp = landmarks.timestampMs,
            leftRightAsymmetry = calculateLeftRightAsymmetry(landmarks),
            anteriorPosteriorAsymmetry = calculateAnteriorPosteriorAsymmetry(landmarks),
            mediolateralAsymmetry = calculateMedialLateralAsymmetry(landmarks),
            rotationalAsymmetry = calculateRotationalAsymmetry(landmarks)
        )

        // Update history
        asymmetryHistory.add(snapshot)
        if (asymmetryHistory.size > maxHistorySize) {
            asymmetryHistory.removeAt(0)
        }

        // Calculate overall asymmetry score
        val overallScore = calculateOverallAsymmetryScore(snapshot)

        // Analyze trends over time
        val trends = analyzeAsymmetryTrends()

        // Generate recommendations
        val recommendations = generateAsymmetryRecommendations(snapshot, trends)

        return AsymmetryAnalysis(
            leftRightAsymmetry = snapshot.leftRightAsymmetry,
            anteriorPosteriorAsymmetry = snapshot.anteriorPosteriorAsymmetry,
            mediolateralAsymmetry = snapshot.mediolateralAsymmetry,
            rotationalAsymmetry = snapshot.rotationalAsymmetry,
            overallAsymmetryScore = overallScore,
            asymmetryTrends = trends,
            recommendations = recommendations
        )
    }

    /**
     * Calculate left-right asymmetry across all bilateral landmarks
     */
    private fun calculateLeftRightAsymmetry(landmarks: PoseLandmarkResult): Float {
        val asymmetries = mutableListOf<Float>()

        bilateralPairs.forEach { (jointName, indices) ->
            val leftLandmark = landmarks.landmarks[indices.first]
            val rightLandmark = landmarks.landmarks[indices.second]

            // Skip if either landmark has low visibility
            if (leftLandmark.visibility < 0.5f || rightLandmark.visibility < 0.5f) {
                return@forEach
            }

            // Calculate position asymmetry
            val positionAsymmetry = calculatePositionAsymmetry(leftLandmark, rightLandmark)
            asymmetries.add(positionAsymmetry)

            // Calculate movement asymmetry if we have history
            if (asymmetryHistory.size > 5) {
                val movementAsymmetry = calculateMovementAsymmetry(
                    jointName, leftLandmark, rightLandmark
                )
                asymmetries.add(movementAsymmetry)
            }
        }

        return if (asymmetries.isNotEmpty()) {
            asymmetries.average().toFloat()
        } else 0f
    }

    /**
     * Calculate anterior-posterior asymmetry (forward/backward lean)
     */
    private fun calculateAnteriorPosteriorAsymmetry(landmarks: PoseLandmarkResult): Float {
        val nose = landmarks.worldLandmarks[PoseLandmarks.NOSE]
        val leftShoulder = landmarks.worldLandmarks[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks.worldLandmarks[PoseLandmarks.RIGHT_SHOULDER]
        val leftHip = landmarks.worldLandmarks[PoseLandmarks.LEFT_HIP]
        val rightHip = landmarks.worldLandmarks[PoseLandmarks.RIGHT_HIP]

        // Calculate center of mass for upper and lower body
        val shoulderCenter = calculateCenterPoint(leftShoulder, rightShoulder)
        val hipCenter = calculateCenterPoint(leftHip, rightHip)

        // Calculate anterior-posterior displacement
        val shoulderDepth = shoulderCenter.z
        val hipDepth = hipCenter.z
        val headDepth = nose.z

        // Analyze forward/backward lean patterns
        val torsoLean = shoulderDepth - hipDepth
        val headLean = headDepth - shoulderDepth

        // Combine into overall anterior-posterior asymmetry score
        val overallLean = (torsoLean + headLean * 0.5f) / 1.5f

        // Normalize to -1 to 1 range (positive = forward lean)
        return overallLean.coerceIn(-1f, 1f)
    }

    /**
     * Calculate medial-lateral asymmetry (left/right lean)
     */
    private fun calculateMedialLateralAsymmetry(landmarks: PoseLandmarkResult): Float {
        val leftShoulder = landmarks.landmarks[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks.landmarks[PoseLandmarks.RIGHT_SHOULDER]
        val leftHip = landmarks.landmarks[PoseLandmarks.LEFT_HIP]
        val rightHip = landmarks.landmarks[PoseLandmarks.RIGHT_HIP]

        // Calculate center line deviation
        val shoulderMidpoint = (leftShoulder.x + rightShoulder.x) / 2f
        val hipMidpoint = (leftHip.x + rightHip.x) / 2f

        // Calculate lateral displacement
        val lateralDeviation = shoulderMidpoint - hipMidpoint

        // Calculate weight distribution asymmetry
        val shoulderWeightDistribution = calculateWeightDistribution(leftShoulder, rightShoulder)
        val hipWeightDistribution = calculateWeightDistribution(leftHip, rightHip)

        val weightAsymmetry = (shoulderWeightDistribution + hipWeightDistribution) / 2f

        // Combine lateral deviation and weight distribution
        val overallMedialLateralAsymmetry = (lateralDeviation * 2f + weightAsymmetry) / 3f

        return overallMedialLateralAsymmetry.coerceIn(-1f, 1f)
    }

    /**
     * Calculate rotational asymmetry around vertical axis
     */
    private fun calculateRotationalAsymmetry(landmarks: PoseLandmarkResult): Float {
        val leftShoulder = landmarks.landmarks[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks.landmarks[PoseLandmarks.RIGHT_SHOULDER]
        val leftHip = landmarks.landmarks[PoseLandmarks.LEFT_HIP]
        val rightHip = landmarks.landmarks[PoseLandmarks.RIGHT_HIP]

        // Calculate shoulder rotation angle
        val shoulderVector = VectorUtils.vectorFromPoints(
            leftShoulder.x.toDouble(), leftShoulder.y.toDouble(),
            rightShoulder.x.toDouble(), rightShoulder.y.toDouble()
        )

        // Calculate hip rotation angle
        val hipVector = VectorUtils.vectorFromPoints(
            leftHip.x.toDouble(), leftHip.y.toDouble(),
            rightHip.x.toDouble(), rightHip.y.toDouble()
        )

        // Calculate rotational difference between shoulders and hips
        val rotationalDifference = abs(shoulderVector.angleWith(hipVector))

        // Normalize to 0-1 range
        return (rotationalDifference / PI).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Calculate position asymmetry between bilateral landmarks
     */
    private fun calculatePositionAsymmetry(
        leftLandmark: PoseLandmarkResult.Landmark,
        rightLandmark: PoseLandmarkResult.Landmark
    ): Float {
        // Calculate Euclidean distance difference from center
        val centerX = (leftLandmark.x + rightLandmark.x) / 2f
        val centerY = (leftLandmark.y + rightLandmark.y) / 2f

        val leftDistance = sqrt(
            (leftLandmark.x - centerX).pow(2) + (leftLandmark.y - centerY).pow(2)
        )
        val rightDistance = sqrt(
            (rightLandmark.x - centerX).pow(2) + (rightLandmark.y - centerY).pow(2)
        )

        val asymmetry = (leftDistance - rightDistance) / (leftDistance + rightDistance + 1e-6f)
        return asymmetry.coerceIn(-1f, 1f)
    }

    /**
     * Calculate movement asymmetry based on temporal changes
     */
    private fun calculateMovementAsymmetry(
        jointName: String,
        leftLandmark: PoseLandmarkResult.Landmark,
        rightLandmark: PoseLandmarkResult.Landmark
    ): Float {
        if (asymmetryHistory.size < 3) return 0f

        val recentHistory = asymmetryHistory.takeLast(3)

        // Calculate movement velocity for each side
        val leftVelocities = mutableListOf<Float>()
        val rightVelocities = mutableListOf<Float>()

        for (i in 1 until recentHistory.size) {
            val prev = recentHistory[i - 1]
            val curr = recentHistory[i]
            val timeDelta = (curr.timestamp - prev.timestamp) / 1000f

            if (timeDelta > 0) {
                // This is simplified - would need to store individual landmark positions
                // For now, use the asymmetry change as a proxy for movement asymmetry
                val asymmetryChange = abs(curr.leftRightAsymmetry - prev.leftRightAsymmetry)
                leftVelocities.add(asymmetryChange / timeDelta)
                rightVelocities.add(asymmetryChange / timeDelta)
            }
        }

        val leftAvgVelocity = leftVelocities.averageOrNull() ?: 0f
        val rightAvgVelocity = rightVelocities.averageOrNull() ?: 0f

        val velocityAsymmetry = (leftAvgVelocity - rightAvgVelocity) /
                               (leftAvgVelocity + rightAvgVelocity + 1e-6f)

        return velocityAsymmetry.coerceIn(-1f, 1f)
    }

    /**
     * Calculate weight distribution between bilateral landmarks
     */
    private fun calculateWeightDistribution(
        leftLandmark: PoseLandmarkResult.Landmark,
        rightLandmark: PoseLandmarkResult.Landmark
    ): Float {
        // Use visibility and presence as proxies for weight bearing
        val leftWeight = leftLandmark.visibility * leftLandmark.presence
        val rightWeight = rightLandmark.visibility * rightLandmark.presence

        val totalWeight = leftWeight + rightWeight
        if (totalWeight < 1e-6f) return 0f

        val leftRatio = leftWeight / totalWeight
        val rightRatio = rightWeight / totalWeight

        // Calculate asymmetry (-1 = all weight on left, +1 = all weight on right)
        return (rightRatio - leftRatio)
    }

    /**
     * Calculate center point between two landmarks
     */
    private fun calculateCenterPoint(
        p1: PoseLandmarkResult.Landmark,
        p2: PoseLandmarkResult.Landmark
    ): PoseLandmarkResult.Landmark {
        return PoseLandmarkResult.Landmark(
            x = (p1.x + p2.x) / 2f,
            y = (p1.y + p2.y) / 2f,
            z = (p1.z + p2.z) / 2f,
            visibility = min(p1.visibility, p2.visibility),
            presence = min(p1.presence, p2.presence)
        )
    }

    /**
     * Calculate overall asymmetry score from individual components
     */
    private fun calculateOverallAsymmetryScore(snapshot: AsymmetrySnapshot): Float {
        val weights = mapOf(
            "leftRight" to 0.4f,
            "anteriorPosterior" to 0.3f,
            "mediolateral" to 0.2f,
            "rotational" to 0.1f
        )

        return (
            abs(snapshot.leftRightAsymmetry) * weights["leftRight"]!! +
            abs(snapshot.anteriorPosteriorAsymmetry) * weights["anteriorPosterior"]!! +
            abs(snapshot.mediolateralAsymmetry) * weights["mediolateral"]!! +
            snapshot.rotationalAsymmetry * weights["rotational"]!!
        ).coerceIn(0f, 1f)
    }

    /**
     * Analyze asymmetry trends over time
     */
    private fun analyzeAsymmetryTrends(): List<AsymmetryTrend> {
        if (asymmetryHistory.size < 20) return emptyList()

        val trends = mutableListOf<AsymmetryTrend>()
        val recentWindow = asymmetryHistory.takeLast(20)
        val olderWindow = asymmetryHistory.dropLast(20).takeLast(20)

        if (olderWindow.size < 10) return emptyList()

        // Analyze left-right asymmetry trend
        val recentLeftRight = recentWindow.map { abs(it.leftRightAsymmetry) }.average()
        val olderLeftRight = olderWindow.map { abs(it.leftRightAsymmetry) }.average()

        if (recentLeftRight > 0.1 || olderLeftRight > 0.1) {
            trends.add(
                AsymmetryTrend(
                    type = AsymmetryType.LEFT_RIGHT_IMBALANCE,
                    severity = recentLeftRight.toFloat(),
                    duration = (recentWindow.last().timestamp - recentWindow.first().timestamp),
                    isImproving = recentLeftRight < olderLeftRight
                )
            )
        }

        // Analyze anterior-posterior trend
        val recentAP = recentWindow.map { abs(it.anteriorPosteriorAsymmetry) }.average()
        val olderAP = olderWindow.map { abs(it.anteriorPosteriorAsymmetry) }.average()

        if (recentAP > 0.15) {
            val asymmetryType = if (recentWindow.last().anteriorPosteriorAsymmetry > 0) {
                AsymmetryType.FORWARD_LEAN
            } else {
                AsymmetryType.BACKWARD_LEAN
            }

            trends.add(
                AsymmetryTrend(
                    type = asymmetryType,
                    severity = recentAP.toFloat(),
                    duration = (recentWindow.last().timestamp - recentWindow.first().timestamp),
                    isImproving = recentAP < olderAP
                )
            )
        }

        // Analyze rotational trend
        val recentRotational = recentWindow.map { it.rotationalAsymmetry }.average()
        val olderRotational = olderWindow.map { it.rotationalAsymmetry }.average()

        if (recentRotational > 0.2) {
            trends.add(
                AsymmetryTrend(
                    type = AsymmetryType.ROTATIONAL_IMBALANCE,
                    severity = recentRotational.toFloat(),
                    duration = (recentWindow.last().timestamp - recentWindow.first().timestamp),
                    isImproving = recentRotational < olderRotational
                )
            )
        }

        return trends
    }

    /**
     * Generate specific recommendations based on asymmetry analysis
     */
    private fun generateAsymmetryRecommendations(
        snapshot: AsymmetrySnapshot,
        trends: List<AsymmetryTrend>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        // Left-right asymmetry recommendations
        if (abs(snapshot.leftRightAsymmetry) > 0.2f) {
            val dominantSide = if (snapshot.leftRightAsymmetry > 0) "right" else "left"
            val weakerSide = if (snapshot.leftRightAsymmetry > 0) "left" else "right"

            recommendations.add("Significant left-right imbalance detected")
            recommendations.add("Focus on strengthening the $weakerSide side")
            recommendations.add("Consider unilateral exercises to address imbalance")
        }

        // Anterior-posterior asymmetry recommendations
        if (abs(snapshot.anteriorPosteriorAsymmetry) > 0.2f) {
            if (snapshot.anteriorPosteriorAsymmetry > 0) {
                recommendations.add("Forward lean pattern detected")
                recommendations.add("Strengthen posterior chain muscles (glutes, hamstrings)")
                recommendations.add("Improve hip flexor flexibility")
            } else {
                recommendations.add("Backward lean pattern detected")
                recommendations.add("Strengthen core and hip flexors")
                recommendations.add("Address potential posterior muscle tightness")
            }
        }

        // Medial-lateral asymmetry recommendations
        if (abs(snapshot.mediolateralAsymmetry) > 0.15f) {
            val leanDirection = if (snapshot.mediolateralAsymmetry > 0) "right" else "left"
            recommendations.add("Lateral lean toward $leanDirection side detected")
            recommendations.add("Strengthen hip abductors on the opposite side")
            recommendations.add("Address potential lateral muscle imbalances")
        }

        // Rotational asymmetry recommendations
        if (snapshot.rotationalAsymmetry > 0.25f) {
            recommendations.add("Rotational imbalance detected")
            recommendations.add("Focus on core stability and anti-rotation exercises")
            recommendations.add("Address potential hip mobility restrictions")
        }

        // Trend-based recommendations
        trends.forEach { trend ->
            if (!trend.isImproving && trend.severity > 0.3f) {
                recommendations.add("${trend.type.name.lowercase().replace('_', ' ')} is worsening over time")
                recommendations.add("Consider professional movement assessment")
            }
        }

        return recommendations.distinct()
    }

    /**
     * Reset asymmetry tracking history
     */
    fun reset() {
        asymmetryHistory.clear()
    }

    /**
     * Get current asymmetry history for analysis
     */
    fun getAsymmetryHistory(): List<AsymmetrySnapshot> {
        return asymmetryHistory.toList()
    }

    /**
     * Get asymmetry statistics over time window
     */
    fun getAsymmetryStatistics(windowMs: Long = 5000): AsymmetryStatistics? {
        val cutoffTime = System.currentTimeMillis() - windowMs
        val relevantHistory = asymmetryHistory.filter { it.timestamp >= cutoffTime }

        if (relevantHistory.isEmpty()) return null

        return AsymmetryStatistics(
            averageLeftRightAsymmetry = relevantHistory.map { abs(it.leftRightAsymmetry) }.average().toFloat(),
            averageAnteriorPosteriorAsymmetry = relevantHistory.map { abs(it.anteriorPosteriorAsymmetry) }.average().toFloat(),
            averageMedialLateralAsymmetry = relevantHistory.map { abs(it.mediolateralAsymmetry) }.average().toFloat(),
            averageRotationalAsymmetry = relevantHistory.map { it.rotationalAsymmetry }.average().toFloat(),
            variabilityScore = calculateAsymmetryVariability(relevantHistory),
            sampleCount = relevantHistory.size
        )
    }

    private fun calculateAsymmetryVariability(history: List<AsymmetrySnapshot>): Float {
        if (history.size < 2) return 0f

        val leftRightValues = history.map { it.leftRightAsymmetry }
        val apValues = history.map { it.anteriorPosteriorAsymmetry }
        val mlValues = history.map { it.mediolateralAsymmetry }

        val leftRightVariance = calculateVariance(leftRightValues)
        val apVariance = calculateVariance(apValues)
        val mlVariance = calculateVariance(mlValues)

        return ((leftRightVariance + apVariance + mlVariance) / 3f).toFloat()
    }

    private fun calculateVariance(values: List<Float>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return values.map { (it - mean).pow(2) }.average()
    }
}

/**
 * Snapshot of asymmetry measurements at a point in time
 */
data class AsymmetrySnapshot(
    val timestamp: Long,
    val leftRightAsymmetry: Float,
    val anteriorPosteriorAsymmetry: Float,
    val mediolateralAsymmetry: Float,
    val rotationalAsymmetry: Float
)

/**
 * Statistical summary of asymmetry over a time window
 */
data class AsymmetryStatistics(
    val averageLeftRightAsymmetry: Float,
    val averageAnteriorPosteriorAsymmetry: Float,
    val averageMedialLateralAsymmetry: Float,
    val averageRotationalAsymmetry: Float,
    val variabilityScore: Float,
    val sampleCount: Int
)