package com.posecoach.corepose.biomechanics

import com.posecoach.corepose.PoseLandmarks
import com.posecoach.corepose.biomechanics.models.*
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.coregeom.AngleUtils
import com.posecoach.coregeom.VectorUtils
import kotlin.math.*

/**
 * Advanced joint angle calculator that computes 3D joint angles from 2D pose landmarks
 * with biomechanical accuracy and range of motion assessment.
 *
 * Features:
 * - 3D angle estimation from MediaPipe world coordinates
 * - Comprehensive range of motion tracking
 * - Joint stability assessment
 * - Biomechanically accurate angle calculations
 * - Real-time performance optimization
 */
class JointAngleCalculator {

    // Anatomical range of motion references (in degrees)
    private val anatomicalRanges = mapOf(
        "shoulder_flexion" to RangeOfMotion(0f, 180f, 90f),
        "shoulder_abduction" to RangeOfMotion(0f, 180f, 90f),
        "elbow_flexion" to RangeOfMotion(0f, 145f, 90f),
        "hip_flexion" to RangeOfMotion(0f, 120f, 90f),
        "hip_abduction" to RangeOfMotion(0f, 45f, 20f),
        "knee_flexion" to RangeOfMotion(0f, 135f, 90f),
        "ankle_dorsiflexion" to RangeOfMotion(-20f, 20f, 0f)
    )

    // Temporal smoothing for angle calculations
    private val angleHistory = mutableMapOf<String, MutableList<Float>>()
    private val maxHistorySize = 5

    /**
     * Calculate all major joint angles from pose landmarks
     */
    fun calculateAllJoints(landmarks: PoseLandmarkResult): JointAngleMap {
        val angles = mutableMapOf<String, JointAngle>()

        try {
            // Upper body joints
            angles["left_shoulder"] = calculateShoulderAngle(landmarks, isLeft = true)
            angles["right_shoulder"] = calculateShoulderAngle(landmarks, isLeft = false)
            angles["left_elbow"] = calculateElbowAngle(landmarks, isLeft = true)
            angles["right_elbow"] = calculateElbowAngle(landmarks, isLeft = false)

            // Core and spine
            angles["spine_alignment"] = calculateSpineAlignment(landmarks)
            angles["torso_lean"] = calculateTorsoLean(landmarks)

            // Lower body joints
            angles["left_hip"] = calculateHipAngle(landmarks, isLeft = true)
            angles["right_hip"] = calculateHipAngle(landmarks, isLeft = false)
            angles["left_knee"] = calculateKneeAngle(landmarks, isLeft = true)
            angles["right_knee"] = calculateKneeAngle(landmarks, isLeft = false)
            angles["left_ankle"] = calculateAnkleAngle(landmarks, isLeft = true)
            angles["right_ankle"] = calculateAnkleAngle(landmarks, isLeft = false)

            // Specialized biomechanical angles
            angles["neck_angle"] = calculateNeckAngle(landmarks)
            angles["pelvic_tilt"] = calculatePelvicTilt(landmarks)

        } catch (e: Exception) {
            // Log error but continue with partial results
            println("Error calculating joint angles: ${e.message}")
        }

        return angles
    }

    /**
     * Calculate shoulder flexion/extension angle with 3D estimation
     */
    private fun calculateShoulderAngle(landmarks: PoseLandmarkResult, isLeft: Boolean): JointAngle {
        val shoulderIdx = if (isLeft) PoseLandmarks.LEFT_SHOULDER else PoseLandmarks.RIGHT_SHOULDER
        val elbowIdx = if (isLeft) PoseLandmarks.LEFT_ELBOW else PoseLandmarks.RIGHT_ELBOW
        val hipIdx = if (isLeft) PoseLandmarks.LEFT_HIP else PoseLandmarks.RIGHT_HIP

        val shoulder = landmarks.worldLandmarks[shoulderIdx]
        val elbow = landmarks.worldLandmarks[elbowIdx]
        val hip = landmarks.worldLandmarks[hipIdx]

        // Calculate 3D angle: hip -> shoulder -> elbow
        val angle = calculate3DAngle(hip, shoulder, elbow)
        val smoothedAngle = smoothAngle("${if (isLeft) "left" else "right"}_shoulder", angle)

        val range = anatomicalRanges["shoulder_flexion"] ?: RangeOfMotion(0f, 180f, 90f)
        val quality = assessAngleQuality(smoothedAngle, shoulder.visibility, range)

        return JointAngle(
            jointName = "${if (isLeft) "Left" else "Right"} Shoulder",
            angle = smoothedAngle,
            rangeOfMotion = range,
            quality = quality,
            stability = shoulder.visibility * shoulder.presence,
            isWithinNormalRange = smoothedAngle in range.minAngle..range.maxAngle,
            biomechanicalRecommendation = generateShoulderRecommendation(smoothedAngle, range)
        )
    }

    /**
     * Calculate elbow flexion angle with biomechanical accuracy
     */
    private fun calculateElbowAngle(landmarks: PoseLandmarkResult, isLeft: Boolean): JointAngle {
        val shoulderIdx = if (isLeft) PoseLandmarks.LEFT_SHOULDER else PoseLandmarks.RIGHT_SHOULDER
        val elbowIdx = if (isLeft) PoseLandmarks.LEFT_ELBOW else PoseLandmarks.RIGHT_ELBOW
        val wristIdx = if (isLeft) PoseLandmarks.LEFT_WRIST else PoseLandmarks.RIGHT_WRIST

        val shoulder = landmarks.worldLandmarks[shoulderIdx]
        val elbow = landmarks.worldLandmarks[elbowIdx]
        val wrist = landmarks.worldLandmarks[wristIdx]

        val angle = calculate3DAngle(shoulder, elbow, wrist)
        val smoothedAngle = smoothAngle("${if (isLeft) "left" else "right"}_elbow", angle)

        val range = anatomicalRanges["elbow_flexion"] ?: RangeOfMotion(0f, 145f, 90f)
        val quality = assessAngleQuality(smoothedAngle, elbow.visibility, range)

        return JointAngle(
            jointName = "${if (isLeft) "Left" else "Right"} Elbow",
            angle = smoothedAngle,
            rangeOfMotion = range,
            quality = quality,
            stability = elbow.visibility * elbow.presence,
            isWithinNormalRange = smoothedAngle in range.minAngle..range.maxAngle,
            biomechanicalRecommendation = generateElbowRecommendation(smoothedAngle, range)
        )
    }

    /**
     * Calculate hip flexion angle with pelvic reference
     */
    private fun calculateHipAngle(landmarks: PoseLandmarkResult, isLeft: Boolean): JointAngle {
        val hipIdx = if (isLeft) PoseLandmarks.LEFT_HIP else PoseLandmarks.RIGHT_HIP
        val kneeIdx = if (isLeft) PoseLandmarks.LEFT_KNEE else PoseLandmarks.RIGHT_KNEE
        val shoulderIdx = if (isLeft) PoseLandmarks.LEFT_SHOULDER else PoseLandmarks.RIGHT_SHOULDER

        val hip = landmarks.worldLandmarks[hipIdx]
        val knee = landmarks.worldLandmarks[kneeIdx]
        val shoulder = landmarks.worldLandmarks[shoulderIdx]

        // Use torso reference for hip angle calculation
        val angle = calculate3DAngle(shoulder, hip, knee)
        val smoothedAngle = smoothAngle("${if (isLeft) "left" else "right"}_hip", angle)

        val range = anatomicalRanges["hip_flexion"] ?: RangeOfMotion(0f, 120f, 90f)
        val quality = assessAngleQuality(smoothedAngle, hip.visibility, range)

        return JointAngle(
            jointName = "${if (isLeft) "Left" else "Right"} Hip",
            angle = smoothedAngle,
            rangeOfMotion = range,
            quality = quality,
            stability = hip.visibility * hip.presence,
            isWithinNormalRange = smoothedAngle in range.minAngle..range.maxAngle,
            biomechanicalRecommendation = generateHipRecommendation(smoothedAngle, range)
        )
    }

    /**
     * Calculate knee flexion angle
     */
    internal fun calculateKneeAngle(landmarks: PoseLandmarkResult, isLeft: Boolean): JointAngle {
        val hipIdx = if (isLeft) PoseLandmarks.LEFT_HIP else PoseLandmarks.RIGHT_HIP
        val kneeIdx = if (isLeft) PoseLandmarks.LEFT_KNEE else PoseLandmarks.RIGHT_KNEE
        val ankleIdx = if (isLeft) PoseLandmarks.LEFT_ANKLE else PoseLandmarks.RIGHT_ANKLE

        val hip = landmarks.worldLandmarks[hipIdx]
        val knee = landmarks.worldLandmarks[kneeIdx]
        val ankle = landmarks.worldLandmarks[ankleIdx]

        val angle = calculate3DAngle(hip, knee, ankle)
        val smoothedAngle = smoothAngle("${if (isLeft) "left" else "right"}_knee", angle)

        val range = anatomicalRanges["knee_flexion"] ?: RangeOfMotion(0f, 135f, 90f)
        val quality = assessAngleQuality(smoothedAngle, knee.visibility, range)

        return JointAngle(
            jointName = "${if (isLeft) "Left" else "Right"} Knee",
            angle = smoothedAngle,
            rangeOfMotion = range,
            quality = quality,
            stability = knee.visibility * knee.presence,
            isWithinNormalRange = smoothedAngle in range.minAngle..range.maxAngle,
            biomechanicalRecommendation = generateKneeRecommendation(smoothedAngle, range)
        )
    }

    /**
     * Calculate ankle dorsiflexion angle
     */
    private fun calculateAnkleAngle(landmarks: PoseLandmarkResult, isLeft: Boolean): JointAngle {
        val kneeIdx = if (isLeft) PoseLandmarks.LEFT_KNEE else PoseLandmarks.RIGHT_KNEE
        val ankleIdx = if (isLeft) PoseLandmarks.LEFT_ANKLE else PoseLandmarks.RIGHT_ANKLE
        val footIdx = if (isLeft) PoseLandmarks.LEFT_FOOT_INDEX else PoseLandmarks.RIGHT_FOOT_INDEX

        val knee = landmarks.worldLandmarks[kneeIdx]
        val ankle = landmarks.worldLandmarks[ankleIdx]
        val foot = landmarks.worldLandmarks[footIdx]

        val angle = calculate3DAngle(knee, ankle, foot)
        val smoothedAngle = smoothAngle("${if (isLeft) "left" else "right"}_ankle", angle)

        val range = anatomicalRanges["ankle_dorsiflexion"] ?: RangeOfMotion(-20f, 20f, 0f)
        val quality = assessAngleQuality(smoothedAngle, ankle.visibility, range)

        return JointAngle(
            jointName = "${if (isLeft) "Left" else "Right"} Ankle",
            angle = smoothedAngle,
            rangeOfMotion = range,
            quality = quality,
            stability = ankle.visibility * ankle.presence,
            isWithinNormalRange = smoothedAngle in range.minAngle..range.maxAngle,
            biomechanicalRecommendation = generateAnkleRecommendation(smoothedAngle, range)
        )
    }

    /**
     * Calculate spine alignment angle
     */
    internal fun calculateSpineAlignment(landmarks: PoseLandmarkResult): JointAngle {
        val leftShoulder = landmarks.worldLandmarks[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks.worldLandmarks[PoseLandmarks.RIGHT_SHOULDER]
        val leftHip = landmarks.worldLandmarks[PoseLandmarks.LEFT_HIP]
        val rightHip = landmarks.worldLandmarks[PoseLandmarks.RIGHT_HIP]

        // Calculate midpoints
        val shoulderMid = calculateMidpoint3D(leftShoulder, rightShoulder)
        val hipMid = calculateMidpoint3D(leftHip, rightHip)

        // Calculate spine angle from vertical
        val spineVector = VectorUtils.vectorFromPoints(
            hipMid.x.toDouble(), hipMid.y.toDouble(),
            shoulderMid.x.toDouble(), shoulderMid.y.toDouble()
        )
        val verticalVector = VectorUtils.Vector2D(0.0, 1.0)

        val angle = Math.toDegrees(spineVector.angleWith(verticalVector)).toFloat()
        val smoothedAngle = smoothAngle("spine_alignment", angle)

        val range = RangeOfMotion(-10f, 10f, 0f) // Ideal spine alignment
        val avgVisibility = (leftShoulder.visibility + rightShoulder.visibility +
                           leftHip.visibility + rightHip.visibility) / 4f

        return JointAngle(
            jointName = "Spine Alignment",
            angle = smoothedAngle,
            rangeOfMotion = range,
            quality = assessAngleQuality(smoothedAngle, avgVisibility, range),
            stability = avgVisibility,
            isWithinNormalRange = smoothedAngle in range.minAngle..range.maxAngle,
            biomechanicalRecommendation = generateSpineRecommendation(smoothedAngle)
        )
    }

    /**
     * Calculate torso lean angle (anterior/posterior)
     */
    private fun calculateTorsoLean(landmarks: PoseLandmarkResult): JointAngle {
        val leftShoulder = landmarks.worldLandmarks[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks.worldLandmarks[PoseLandmarks.RIGHT_SHOULDER]
        val leftHip = landmarks.worldLandmarks[PoseLandmarks.LEFT_HIP]
        val rightHip = landmarks.worldLandmarks[PoseLandmarks.RIGHT_HIP]

        val shoulderMid = calculateMidpoint3D(leftShoulder, rightShoulder)
        val hipMid = calculateMidpoint3D(leftHip, rightHip)

        // Calculate lean angle using Z-axis depth
        val depthDifference = shoulderMid.z - hipMid.z
        val height = abs(shoulderMid.y - hipMid.y)

        val leanAngle = if (height > 0) {
            Math.toDegrees(atan((depthDifference / height).toDouble())).toFloat()
        } else 0f

        val smoothedAngle = smoothAngle("torso_lean", leanAngle)
        val range = RangeOfMotion(-15f, 15f, 0f)

        return JointAngle(
            jointName = "Torso Lean",
            angle = smoothedAngle,
            rangeOfMotion = range,
            quality = assessAngleQuality(smoothedAngle, leftShoulder.visibility, range),
            stability = (leftShoulder.visibility + rightShoulder.visibility) / 2f,
            isWithinNormalRange = smoothedAngle in range.minAngle..range.maxAngle,
            biomechanicalRecommendation = generateTorsoRecommendation(smoothedAngle)
        )
    }

    /**
     * Calculate neck angle and head position
     */
    private fun calculateNeckAngle(landmarks: PoseLandmarkResult): JointAngle {
        val nose = landmarks.worldLandmarks[PoseLandmarks.NOSE]
        val leftShoulder = landmarks.worldLandmarks[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks.worldLandmarks[PoseLandmarks.RIGHT_SHOULDER]

        val shoulderMid = calculateMidpoint3D(leftShoulder, rightShoulder)

        // Calculate neck extension/flexion
        val neckVector = VectorUtils.vectorFromPoints(
            shoulderMid.x.toDouble(), shoulderMid.y.toDouble(),
            nose.x.toDouble(), nose.y.toDouble()
        )
        val verticalVector = VectorUtils.Vector2D(0.0, 1.0)

        val angle = Math.toDegrees(neckVector.angleWith(verticalVector)).toFloat()
        val smoothedAngle = smoothAngle("neck_angle", angle)

        val range = RangeOfMotion(-30f, 30f, 0f)

        return JointAngle(
            jointName = "Neck Angle",
            angle = smoothedAngle,
            rangeOfMotion = range,
            quality = assessAngleQuality(smoothedAngle, nose.visibility, range),
            stability = nose.visibility * nose.presence,
            isWithinNormalRange = smoothedAngle in range.minAngle..range.maxAngle,
            biomechanicalRecommendation = generateNeckRecommendation(smoothedAngle)
        )
    }

    /**
     * Calculate pelvic tilt angle
     */
    private fun calculatePelvicTilt(landmarks: PoseLandmarkResult): JointAngle {
        val leftHip = landmarks.worldLandmarks[PoseLandmarks.LEFT_HIP]
        val rightHip = landmarks.worldLandmarks[PoseLandmarks.RIGHT_HIP]

        // Calculate pelvic tilt from horizontal
        val pelvicVector = VectorUtils.vectorFromPoints(
            leftHip.x.toDouble(), leftHip.y.toDouble(),
            rightHip.x.toDouble(), rightHip.y.toDouble()
        )
        val horizontalVector = VectorUtils.Vector2D(1.0, 0.0)

        val angle = Math.toDegrees(pelvicVector.angleWith(horizontalVector)).toFloat()
        val smoothedAngle = smoothAngle("pelvic_tilt", angle)

        val range = RangeOfMotion(-10f, 10f, 0f)

        return JointAngle(
            jointName = "Pelvic Tilt",
            angle = smoothedAngle,
            rangeOfMotion = range,
            quality = assessAngleQuality(smoothedAngle, leftHip.visibility, range),
            stability = (leftHip.visibility + rightHip.visibility) / 2f,
            isWithinNormalRange = smoothedAngle in range.minAngle..range.maxAngle,
            biomechanicalRecommendation = generatePelvicRecommendation(smoothedAngle)
        )
    }

    /**
     * Calculate 3D angle between three points using world coordinates
     */
    private fun calculate3DAngle(
        p1: PoseLandmarkResult.Landmark,
        vertex: PoseLandmarkResult.Landmark,
        p3: PoseLandmarkResult.Landmark
    ): Float {
        // Create 3D vectors
        val v1 = Triple(
            p1.x - vertex.x,
            p1.y - vertex.y,
            p1.z - vertex.z
        )
        val v2 = Triple(
            p3.x - vertex.x,
            p3.y - vertex.y,
            p3.z - vertex.z
        )

        // Calculate dot product
        val dotProduct = v1.first * v2.first + v1.second * v2.second + v1.third * v2.third

        // Calculate magnitudes
        val mag1 = sqrt(v1.first * v1.first + v1.second * v1.second + v1.third * v1.third)
        val mag2 = sqrt(v2.first * v2.first + v2.second * v2.second + v2.third * v2.third)

        // Calculate angle
        return if (mag1 > 0 && mag2 > 0) {
            val cosAngle = (dotProduct / (mag1 * mag2)).coerceIn(-1f, 1f)
            Math.toDegrees(acos(cosAngle.toDouble())).toFloat()
        } else {
            0f
        }
    }

    /**
     * Calculate 3D midpoint between two landmarks
     */
    private fun calculateMidpoint3D(
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
     * Apply temporal smoothing to reduce angle jitter
     */
    private fun smoothAngle(jointName: String, currentAngle: Float): Float {
        val history = angleHistory.getOrPut(jointName) { mutableListOf() }

        history.add(currentAngle)
        if (history.size > maxHistorySize) {
            history.removeAt(0)
        }

        // Apply weighted average with recent values having higher weight
        return if (history.size == 1) {
            currentAngle
        } else {
            val weights = (1..history.size).map { it.toFloat() }
            val weightedSum = history.zip(weights).map { it.first * it.second }.sum()
            val totalWeight = weights.sum()
            weightedSum / totalWeight
        }
    }

    /**
     * Assess the quality of an angle measurement
     */
    private fun assessAngleQuality(angle: Float, visibility: Float, range: RangeOfMotion): AngleQuality {
        val stabilityScore = visibility
        val accuracyScore = if (angle in range.minAngle..range.maxAngle) 1.0f else 0.7f
        val consistencyScore = 1.0f // Would be enhanced with temporal consistency checking

        val overallQuality = (stabilityScore + accuracyScore + consistencyScore) / 3f

        return when {
            overallQuality >= 0.8f -> AngleQuality.HIGH
            overallQuality >= 0.6f -> AngleQuality.MEDIUM
            else -> AngleQuality.LOW
        }
    }

    // Recommendation generators for different joints
    private fun generateShoulderRecommendation(angle: Float, range: RangeOfMotion): String {
        return when {
            angle < range.minAngle -> "Increase shoulder mobility with gentle stretching"
            angle > range.maxAngle -> "Reduce shoulder elevation, focus on controlled movement"
            else -> "Good shoulder position maintained"
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun generateElbowRecommendation(angle: Float, range: RangeOfMotion): String {
        return when {
            angle < 30f -> "Increase elbow bend for better biomechanics"
            angle > 150f -> "Reduce excessive elbow extension"
            else -> "Optimal elbow positioning"
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun generateHipRecommendation(angle: Float, range: RangeOfMotion): String {
        return when {
            angle < 60f -> "Increase hip flexion for better movement pattern"
            angle > 120f -> "Excessive hip flexion - focus on hip extension"
            else -> "Good hip mobility demonstrated"
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun generateKneeRecommendation(angle: Float, range: RangeOfMotion): String {
        return when {
            angle < 30f -> "Increase knee bend for better shock absorption"
            angle > 150f -> "Reduce knee hyperextension for joint protection"
            else -> "Appropriate knee position maintained"
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun generateAnkleRecommendation(angle: Float, range: RangeOfMotion): String {
        return when {
            angle < -15f -> "Improve ankle dorsiflexion mobility"
            angle > 15f -> "Reduce excessive ankle plantarflexion"
            else -> "Good ankle positioning for stability"
        }
    }

    private fun generateSpineRecommendation(angle: Float): String {
        return when {
            abs(angle) < 5f -> "Excellent spinal alignment maintained"
            angle > 10f -> "Forward head posture detected - strengthen posterior chain"
            angle < -10f -> "Excessive backward lean - engage core muscles"
            else -> "Minor spinal deviation - focus on postural awareness"
        }
    }

    private fun generateTorsoRecommendation(angle: Float): String {
        return when {
            abs(angle) < 5f -> "Neutral torso position maintained"
            angle > 10f -> "Forward lean detected - strengthen core and glutes"
            angle < -10f -> "Backward lean - improve hip flexibility"
            else -> "Minor torso deviation noted"
        }
    }

    private fun generateNeckRecommendation(angle: Float): String {
        return when {
            abs(angle) < 10f -> "Good head and neck alignment"
            angle > 20f -> "Forward head posture - strengthen deep neck flexors"
            angle < -20f -> "Excessive neck extension - improve thoracic mobility"
            else -> "Minor neck deviation - maintain postural awareness"
        }
    }

    private fun generatePelvicRecommendation(angle: Float): String {
        return when {
            abs(angle) < 5f -> "Neutral pelvic alignment maintained"
            angle > 10f -> "Pelvic drop detected - strengthen hip abductors"
            angle < -10f -> "Pelvic hiking - improve lateral flexibility"
            else -> "Minor pelvic asymmetry noted"
        }
    }

    /**
     * Reset all temporal tracking data
     */
    fun reset() {
        angleHistory.clear()
    }

    /**
     * Get current angle history for analysis
     */
    fun getAngleHistory(): Map<String, List<Float>> {
        return angleHistory.mapValues { it.value.toList() }
    }
}