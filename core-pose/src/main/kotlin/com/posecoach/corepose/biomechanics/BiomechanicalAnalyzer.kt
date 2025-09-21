package com.posecoach.corepose.biomechanics

import com.posecoach.corepose.PoseLandmarks
import com.posecoach.corepose.biomechanics.models.*
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.utils.PerformanceTracker
import com.posecoach.coregeom.AngleUtils
import com.posecoach.coregeom.VectorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.*

/**
 * Advanced biomechanical analysis engine that provides sophisticated pose analysis
 * with real-time biomechanical insights and movement quality assessment.
 *
 * Features:
 * - 3D joint angle calculations from 2D landmarks
 * - Range of motion assessment and tracking
 * - Joint stability and mobility analysis
 * - Kinetic chain analysis for movement efficiency
 * - Real-time processing optimized for mobile devices
 */
class BiomechanicalAnalyzer(
    private val performanceTracker: PerformanceTracker = PerformanceTracker()
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Analysis results flow
    private val _analysisResults = MutableStateFlow<BiomechanicalAnalysisResult?>(null)
    val analysisResults: StateFlow<BiomechanicalAnalysisResult?> = _analysisResults.asStateFlow()

    // Configuration
    private val jointAngleCalculator = JointAngleCalculator()
    private val asymmetryDetector = AsymmetryDetector()
    private val posturalAssessment = PosturalAssessment()
    private val movementPatternAnalyzer = MovementPatternAnalyzer()

    // Temporal tracking for kinetic chain analysis
    private val recentPoses = mutableListOf<TimestampedPose>()
    private val maxHistorySize = 30 // 1 second at 30fps

    /**
     * Performs comprehensive biomechanical analysis on pose landmarks
     * @param landmarks The pose landmark result from MediaPipe
     * @return Complete biomechanical analysis with timing under 30ms
     */
    suspend fun analyzePose(landmarks: PoseLandmarkResult): BiomechanicalAnalysisResult {
        return performanceTracker.measureOperation("biomechanical_analysis") {
            val startTime = System.currentTimeMillis()

            try {
                // Update temporal tracking
                updatePoseHistory(landmarks)

                // Parallel analysis components
                val jointAngles = jointAngleCalculator.calculateAllJoints(landmarks)
                val asymmetryAnalysis = asymmetryDetector.analyze(landmarks)
                val posturalAnalysis = posturalAssessment.assess(landmarks)
                val movementPattern = if (recentPoses.size >= 10) {
                    movementPatternAnalyzer.analyzeSequence(recentPoses.takeLast(10))
                } else null

                // Kinetic chain analysis
                val kineticChain = analyzeKineticChain(landmarks, jointAngles)

                // Movement quality scoring
                val qualityScore = calculateMovementQuality(
                    jointAngles, asymmetryAnalysis, posturalAnalysis, kineticChain
                )

                // Fatigue and compensation detection
                val fatigueIndicators = detectFatigueIndicators(landmarks)
                val compensationPatterns = detectCompensationPatterns(asymmetryAnalysis)

                val result = BiomechanicalAnalysisResult(
                    timestamp = landmarks.timestampMs,
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    jointAngles = jointAngles,
                    asymmetryAnalysis = asymmetryAnalysis,
                    posturalAnalysis = posturalAnalysis,
                    movementPattern = movementPattern,
                    kineticChainAnalysis = kineticChain,
                    movementQuality = qualityScore,
                    fatigueIndicators = fatigueIndicators,
                    compensationPatterns = compensationPatterns,
                    confidenceScore = calculateConfidenceScore(landmarks)
                )

                // Update flow asynchronously
                scope.launch { _analysisResults.value = result }

                return@measureOperation result

            } catch (e: Exception) {
                Timber.e(e, "Error in biomechanical analysis")
                throw BiomechanicalAnalysisException("Analysis failed", e)
            }
        }
    }

    private fun updatePoseHistory(landmarks: PoseLandmarkResult) {
        recentPoses.add(TimestampedPose(landmarks.timestampMs, landmarks))

        // Maintain sliding window
        if (recentPoses.size > maxHistorySize) {
            recentPoses.removeAt(0)
        }
    }

    /**
     * Analyzes kinetic chain efficiency and coordination
     */
    private fun analyzeKineticChain(
        landmarks: PoseLandmarkResult,
        jointAngles: JointAngleMap
    ): KineticChainAnalysis {
        val links = mutableListOf<KineticChainLink>()

        // Upper body kinetic chain: shoulder -> elbow -> wrist
        val leftArmChain = analyzeArmChain(landmarks, jointAngles, isLeft = true)
        val rightArmChain = analyzeArmChain(landmarks, jointAngles, isLeft = false)

        // Lower body kinetic chain: hip -> knee -> ankle
        val leftLegChain = analyzeLegChain(landmarks, jointAngles, isLeft = true)
        val rightLegChain = analyzeLegChain(landmarks, jointAngles, isLeft = false)

        // Core stability assessment
        val coreStability = assessCoreStability(landmarks, jointAngles)

        return KineticChainAnalysis(
            leftArmChain = leftArmChain,
            rightArmChain = rightArmChain,
            leftLegChain = leftLegChain,
            rightLegChain = rightLegChain,
            coreStability = coreStability,
            overallEfficiency = calculateOverallEfficiency(
                leftArmChain, rightArmChain, leftLegChain, rightLegChain, coreStability
            )
        )
    }

    private fun analyzeArmChain(
        landmarks: PoseLandmarkResult,
        jointAngles: JointAngleMap,
        isLeft: Boolean
    ): KineticChainLink {
        val shoulderIdx = if (isLeft) PoseLandmarks.LEFT_SHOULDER else PoseLandmarks.RIGHT_SHOULDER
        val elbowIdx = if (isLeft) PoseLandmarks.LEFT_ELBOW else PoseLandmarks.RIGHT_ELBOW
        val wristIdx = if (isLeft) PoseLandmarks.LEFT_WRIST else PoseLandmarks.RIGHT_WRIST

        val shoulder = landmarks.landmarks[shoulderIdx]
        val elbow = landmarks.landmarks[elbowIdx]
        val wrist = landmarks.landmarks[wristIdx]

        // Calculate chain alignment and stability
        val alignment = calculateChainAlignment(shoulder, elbow, wrist)
        val stability = calculateJointStability(shoulder, elbow, wrist)

        // Get relevant joint angles
        val shoulderAngle = jointAngles[if (isLeft) "left_shoulder" else "right_shoulder"]?.angle ?: 0f
        val elbowAngle = jointAngles[if (isLeft) "left_elbow" else "right_elbow"]?.angle ?: 0f

        return KineticChainLink(
            name = if (isLeft) "left_arm" else "right_arm",
            joints = listOf("shoulder", "elbow", "wrist"),
            alignment = alignment,
            stability = stability,
            efficiency = calculateChainEfficiency(alignment, stability, shoulderAngle, elbowAngle),
            coordinationScore = calculateCoordinationScore(shoulderAngle, elbowAngle)
        )
    }

    private fun analyzeLegChain(
        landmarks: PoseLandmarkResult,
        jointAngles: JointAngleMap,
        isLeft: Boolean
    ): KineticChainLink {
        val hipIdx = if (isLeft) PoseLandmarks.LEFT_HIP else PoseLandmarks.RIGHT_HIP
        val kneeIdx = if (isLeft) PoseLandmarks.LEFT_KNEE else PoseLandmarks.RIGHT_KNEE
        val ankleIdx = if (isLeft) PoseLandmarks.LEFT_ANKLE else PoseLandmarks.RIGHT_ANKLE

        val hip = landmarks.landmarks[hipIdx]
        val knee = landmarks.landmarks[kneeIdx]
        val ankle = landmarks.landmarks[ankleIdx]

        val alignment = calculateChainAlignment(hip, knee, ankle)
        val stability = calculateJointStability(hip, knee, ankle)

        val hipAngle = jointAngles[if (isLeft) "left_hip" else "right_hip"]?.angle ?: 0f
        val kneeAngle = jointAngles[if (isLeft) "left_knee" else "right_knee"]?.angle ?: 0f

        return KineticChainLink(
            name = if (isLeft) "left_leg" else "right_leg",
            joints = listOf("hip", "knee", "ankle"),
            alignment = alignment,
            stability = stability,
            efficiency = calculateChainEfficiency(alignment, stability, hipAngle, kneeAngle),
            coordinationScore = calculateCoordinationScore(hipAngle, kneeAngle)
        )
    }

    private fun assessCoreStability(
        landmarks: PoseLandmarkResult,
        jointAngles: JointAngleMap
    ): CoreStabilityAssessment {
        val leftShoulder = landmarks.landmarks[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks.landmarks[PoseLandmarks.RIGHT_SHOULDER]
        val leftHip = landmarks.landmarks[PoseLandmarks.LEFT_HIP]
        val rightHip = landmarks.landmarks[PoseLandmarks.RIGHT_HIP]

        // Calculate torso alignment
        val shoulderMidpoint = calculateMidpoint(leftShoulder, rightShoulder)
        val hipMidpoint = calculateMidpoint(leftHip, rightHip)

        val torsoAlignment = calculateVerticalAlignment(shoulderMidpoint, hipMidpoint)
        val shoulderLevelness = calculateLevelness(leftShoulder, rightShoulder)
        val hipLevelness = calculateLevelness(leftHip, rightHip)

        // Assess rotational stability
        val rotationalStability = assessRotationalStability(
            leftShoulder, rightShoulder, leftHip, rightHip
        )

        return CoreStabilityAssessment(
            torsoAlignment = torsoAlignment,
            shoulderLevelness = shoulderLevelness,
            hipLevelness = hipLevelness,
            rotationalStability = rotationalStability,
            overallStability = (torsoAlignment + shoulderLevelness + hipLevelness + rotationalStability) / 4.0f
        )
    }

    private fun calculateMovementQuality(
        jointAngles: JointAngleMap,
        asymmetryAnalysis: AsymmetryAnalysis,
        posturalAnalysis: PosturalAnalysis,
        kineticChain: KineticChainAnalysis
    ): MovementQualityScore {
        // Range of motion quality (0-100)
        val romQuality = calculateROMQuality(jointAngles)

        // Symmetry quality (0-100)
        val symmetryQuality = 100f - (asymmetryAnalysis.overallAsymmetryScore * 100f)

        // Postural quality (0-100)
        val posturalQuality = posturalAnalysis.overallPostureScore * 100f

        // Coordination quality (0-100)
        val coordinationQuality = kineticChain.overallEfficiency * 100f

        // Weighted overall score
        val overallScore = (
            romQuality * 0.25f +
            symmetryQuality * 0.25f +
            posturalQuality * 0.25f +
            coordinationQuality * 0.25f
        )

        return MovementQualityScore(
            overallScore = overallScore,
            rangeOfMotionScore = romQuality,
            symmetryScore = symmetryQuality,
            posturalScore = posturalQuality,
            coordinationScore = coordinationQuality,
            recommendations = generateQualityRecommendations(
                romQuality, symmetryQuality, posturalQuality, coordinationQuality
            )
        )
    }

    private fun detectFatigueIndicators(landmarks: PoseLandmarkResult): FatigueIndicators {
        if (recentPoses.size < 20) {
            return FatigueIndicators.none()
        }

        val recent10 = recentPoses.takeLast(10)
        val previous10 = recentPoses.drop(recentPoses.size - 20).take(10)

        // Analyze movement variability increase
        val recentVariability = calculateMovementVariability(recent10)
        val previousVariability = calculateMovementVariability(previous10)
        val variabilityIncrease = recentVariability - previousVariability

        // Analyze postural decline
        val recentPosture = recent10.map { calculateBasicPostureScore(it.pose) }.average()
        val previousPosture = previous10.map { calculateBasicPostureScore(it.pose) }.average()
        val postureDecline = previousPosture - recentPosture

        return FatigueIndicators(
            movementVariabilityIncrease = variabilityIncrease.toFloat(),
            posturalDecline = postureDecline.toFloat(),
            overallFatigueScore = calculateFatigueScore(variabilityIncrease, postureDecline),
            recommendations = generateFatigueRecommendations(variabilityIncrease, postureDecline)
        )
    }

    private fun detectCompensationPatterns(asymmetryAnalysis: AsymmetryAnalysis): CompensationPatterns {
        val patterns = mutableListOf<CompensationPattern>()

        // Left-right compensation
        if (asymmetryAnalysis.overallAsymmetryScore > 0.3f) {
            val dominantSide = if (asymmetryAnalysis.leftRightAsymmetry > 0) "right" else "left"
            patterns.add(
                CompensationPattern(
                    type = CompensationPatternType.LEFT_RIGHT_IMBALANCE,
                    severity = asymmetryAnalysis.overallAsymmetryScore,
                    description = "Favoring $dominantSide side",
                    recommendedCorrection = "Focus on strengthening weaker side and improving symmetry"
                )
            )
        }

        // Anterior-posterior compensation
        if (asymmetryAnalysis.anteriorPosteriorAsymmetry > 0.2f) {
            patterns.add(
                CompensationPattern(
                    type = CompensationPatternType.FORWARD_LEAN,
                    severity = asymmetryAnalysis.anteriorPosteriorAsymmetry,
                    description = "Forward leaning posture detected",
                    recommendedCorrection = "Strengthen posterior chain and improve core stability"
                )
            )
        }

        return CompensationPatterns(
            detectedPatterns = patterns,
            overallCompensationScore = patterns.maxOfOrNull { it.severity } ?: 0f
        )
    }

    // Helper functions for calculations
    private fun calculateChainAlignment(joint1: PoseLandmarkResult.Landmark,
                                      joint2: PoseLandmarkResult.Landmark,
                                      joint3: PoseLandmarkResult.Landmark): Float {
        // Calculate how well aligned the three joints are
        val vector1 = VectorUtils.vectorFromPoints(joint1.x.toDouble(), joint1.y.toDouble(),
                                                 joint2.x.toDouble(), joint2.y.toDouble())
        val vector2 = VectorUtils.vectorFromPoints(joint2.x.toDouble(), joint2.y.toDouble(),
                                                 joint3.x.toDouble(), joint3.y.toDouble())

        val angle = vector1.angleWith(vector2)
        // Convert to alignment score (straighter = better alignment)
        return (1.0f - (abs(angle - PI) / PI).toFloat()).coerceIn(0f, 1f)
    }

    private fun calculateJointStability(vararg joints: PoseLandmarkResult.Landmark): Float {
        // Basic stability based on visibility and presence
        return joints.map { (it.visibility * it.presence).coerceIn(0f, 1f) }.average().toFloat()
    }

    private fun calculateChainEfficiency(alignment: Float, stability: Float,
                                       angle1: Float, angle2: Float): Float {
        // Combine alignment, stability, and optimal angle positioning
        val angleOptimality = calculateAngleOptimality(angle1, angle2)
        return (alignment * 0.4f + stability * 0.3f + angleOptimality * 0.3f)
    }

    private fun calculateCoordinationScore(angle1: Float, angle2: Float): Float {
        // Assess how well joints coordinate (context-dependent)
        return calculateAngleOptimality(angle1, angle2)
    }

    private fun calculateAngleOptimality(angle1: Float, angle2: Float): Float {
        // Simplified optimality based on typical human movement ranges
        // This would be enhanced with exercise-specific optimal ranges
        val normalizedAngle1 = normalizeAngleToOptimalRange(angle1)
        val normalizedAngle2 = normalizeAngleToOptimalRange(angle2)
        return (normalizedAngle1 + normalizedAngle2) / 2f
    }

    private fun normalizeAngleToOptimalRange(angle: Float): Float {
        // Simplified optimal range scoring (0-1)
        // Real implementation would use exercise-specific ranges
        return when {
            angle in 30f..150f -> 1.0f
            angle in 15f..30f || angle in 150f..165f -> 0.8f
            angle in 0f..15f || angle in 165f..180f -> 0.6f
            else -> 0.4f
        }
    }

    private fun calculateOverallEfficiency(vararg chains: KineticChainLink): Float {
        return chains.map { it.efficiency }.average().toFloat()
    }

    private fun calculateOverallEfficiency(
        leftArm: KineticChainLink, rightArm: KineticChainLink,
        leftLeg: KineticChainLink, rightLeg: KineticChainLink,
        core: CoreStabilityAssessment
    ): Float {
        return (leftArm.efficiency + rightArm.efficiency + leftLeg.efficiency +
                rightLeg.efficiency + core.overallStability) / 5f
    }

    private fun calculateMidpoint(p1: PoseLandmarkResult.Landmark,
                                p2: PoseLandmarkResult.Landmark): Pair<Float, Float> {
        return Pair((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    }

    private fun calculateVerticalAlignment(point1: Pair<Float, Float>,
                                         point2: Pair<Float, Float>): Float {
        val horizontalDeviation = abs(point1.first - point2.first)
        return (1.0f - horizontalDeviation.coerceAtMost(0.2f) / 0.2f).coerceIn(0f, 1f)
    }

    private fun calculateLevelness(p1: PoseLandmarkResult.Landmark,
                                 p2: PoseLandmarkResult.Landmark): Float {
        val verticalDiff = abs(p1.y - p2.y)
        return (1.0f - verticalDiff.coerceAtMost(0.1f) / 0.1f).coerceIn(0f, 1f)
    }

    private fun assessRotationalStability(
        leftShoulder: PoseLandmarkResult.Landmark,
        rightShoulder: PoseLandmarkResult.Landmark,
        leftHip: PoseLandmarkResult.Landmark,
        rightHip: PoseLandmarkResult.Landmark
    ): Float {
        // Calculate shoulder and hip rotation angles
        val shoulderAngle = atan2(
            (rightShoulder.y - leftShoulder.y).toDouble(),
            (rightShoulder.x - leftShoulder.x).toDouble()
        )
        val hipAngle = atan2(
            (rightHip.y - leftHip.y).toDouble(),
            (rightHip.x - leftHip.x).toDouble()
        )

        val rotationalDifference = abs(shoulderAngle - hipAngle)
        return (1.0f - (rotationalDifference / PI).toFloat()).coerceIn(0f, 1f)
    }

    private fun calculateROMQuality(jointAngles: JointAngleMap): Float {
        // Assess range of motion quality across all joints
        val scores = jointAngles.values.map { jointAngle ->
            assessJointROMQuality(jointAngle.angle, jointAngle.rangeOfMotion)
        }
        return if (scores.isNotEmpty()) scores.average().toFloat() else 0f
    }

    private fun assessJointROMQuality(currentAngle: Float, rom: RangeOfMotion): Float {
        val normalizedPosition = when {
            currentAngle < rom.minAngle -> 0f
            currentAngle > rom.maxAngle -> 0f
            else -> {
                val range = rom.maxAngle - rom.minAngle
                val position = currentAngle - rom.minAngle
                position / range
            }
        }

        // Quality is best when joint is in optimal functional range
        return when {
            normalizedPosition in 0.2f..0.8f -> 1.0f
            normalizedPosition in 0.1f..0.2f || normalizedPosition in 0.8f..0.9f -> 0.8f
            else -> 0.6f
        }
    }

    private fun generateQualityRecommendations(
        rom: Float, symmetry: Float, posture: Float, coordination: Float
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (rom < 70f) recommendations.add("Focus on improving range of motion through targeted stretching")
        if (symmetry < 70f) recommendations.add("Work on symmetrical movement patterns")
        if (posture < 70f) recommendations.add("Strengthen postural muscles and improve alignment")
        if (coordination < 70f) recommendations.add("Practice coordination exercises and movement patterns")

        return recommendations
    }

    private fun calculateMovementVariability(poses: List<TimestampedPose>): Double {
        if (poses.size < 2) return 0.0

        val keyJoints = listOf(
            PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER,
            PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP
        )

        return keyJoints.map { jointIdx ->
            val positions = poses.map {
                val landmark = it.pose.landmarks[jointIdx]
                sqrt((landmark.x * landmark.x + landmark.y * landmark.y).toDouble())
            }
            calculateVariance(positions)
        }.average()
    }

    private fun calculateVariance(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return values.map { (it - mean).pow(2) }.average()
    }

    private fun calculateBasicPostureScore(pose: PoseLandmarkResult): Double {
        val leftShoulder = pose.landmarks[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = pose.landmarks[PoseLandmarks.RIGHT_SHOULDER]
        val leftHip = pose.landmarks[PoseLandmarks.LEFT_HIP]
        val rightHip = pose.landmarks[PoseLandmarks.RIGHT_HIP]

        val shoulderLevel = 1.0 - abs(leftShoulder.y - rightShoulder.y)
        val hipLevel = 1.0 - abs(leftHip.y - rightHip.y)

        return (shoulderLevel + hipLevel) / 2.0
    }

    private fun calculateFatigueScore(variabilityIncrease: Double, postureDecline: Double): Float {
        return ((variabilityIncrease * 0.6 + postureDecline * 0.4) * 100).toFloat().coerceIn(0f, 100f)
    }

    private fun generateFatigueRecommendations(
        variabilityIncrease: Double,
        postureDecline: Double
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (variabilityIncrease > 0.1) {
            recommendations.add("Consider taking a rest break - movement inconsistency detected")
        }
        if (postureDecline > 0.1) {
            recommendations.add("Focus on maintaining proper posture - fatigue affecting form")
        }

        return recommendations
    }

    private fun calculateConfidenceScore(landmarks: PoseLandmarkResult): Float {
        val visibilityScores = landmarks.landmarks.map { it.visibility * it.presence }
        return visibilityScores.average().toFloat()
    }

    /**
     * Reset all temporal tracking and analysis state
     */
    fun reset() {
        recentPoses.clear()
        jointAngleCalculator.reset()
        asymmetryDetector.reset()
        posturalAssessment.reset()
        movementPatternAnalyzer.reset()
    }

    /**
     * Get current analysis performance metrics
     */
    fun getPerformanceMetrics(): Map<String, Double> {
        return performanceTracker.getMetrics()
    }
}

/**
 * Exception thrown when biomechanical analysis fails
 */
class BiomechanicalAnalysisException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Timestamped pose for temporal analysis
 */
data class TimestampedPose(
    val timestamp: Long,
    val pose: PoseLandmarkResult
)