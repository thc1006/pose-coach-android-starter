package com.posecoach.corepose.biomechanics

import com.posecoach.corepose.PoseLandmarks
import com.posecoach.corepose.biomechanics.models.*
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.coregeom.AngleUtils
import com.posecoach.coregeom.VectorUtils
import kotlin.math.*

/**
 * Advanced postural assessment system for dynamic posture analysis
 * and real-time postural deviation detection.
 *
 * Features:
 * - Dynamic posture analysis during movement
 * - Deviation from ideal alignment quantification
 * - Muscle activation pattern inference from pose
 * - Real-time postural correction recommendations
 * - Progressive postural tracking over time
 */
class PosturalAssessment {

    // Ideal postural reference values (in degrees from vertical/horizontal)
    private val idealPosturalValues = mapOf(
        "head_angle" to 0f,           // Neutral head position
        "cervical_angle" to 35f,      // Natural cervical lordosis
        "thoracic_angle" to 40f,      // Natural thoracic kyphosis
        "lumbar_angle" to 40f,        // Natural lumbar lordosis
        "pelvic_tilt" to 0f,          // Neutral pelvic tilt
        "shoulder_level" to 0f,       // Level shoulders
        "hip_level" to 0f,            // Level hips
        "knee_alignment" to 0f,       // Neutral knee position
        "ankle_alignment" to 90f      // Neutral ankle position
    )

    // Tolerance ranges for each postural component (degrees)
    private val posturalTolerances = mapOf(
        "head_angle" to 10f,
        "cervical_angle" to 15f,
        "thoracic_angle" to 15f,
        "lumbar_angle" to 15f,
        "pelvic_tilt" to 8f,
        "shoulder_level" to 5f,
        "hip_level" to 5f,
        "knee_alignment" to 10f,
        "ankle_alignment" to 10f
    )

    // Postural history for trend analysis
    private val posturalHistory = mutableListOf<PosturalSnapshot>()
    private val maxHistorySize = 150 // ~5 seconds at 30fps

    /**
     * Comprehensive postural assessment
     */
    fun assess(landmarks: PoseLandmarkResult): PosturalAnalysis {
        // Assess individual postural components
        val headPosition = assessHeadPosition(landmarks)
        val shoulderAlignment = assessShoulderAlignment(landmarks)
        val spinalAlignment = assessSpinalAlignment(landmarks)
        val pelvicAlignment = assessPelvicAlignment(landmarks)
        val legAlignment = assessLegAlignment(landmarks)

        // Calculate overall posture score
        val overallScore = calculateOverallPostureScore(
            headPosition, shoulderAlignment, spinalAlignment, pelvicAlignment, legAlignment
        )

        // Detect specific postural deviations
        val posturalDeviations = detectPosturalDeviations(landmarks)

        // Generate recommendations
        val recommendations = generatePosturalRecommendations(
            headPosition, shoulderAlignment, spinalAlignment, pelvicAlignment, legAlignment, posturalDeviations
        )

        // Update postural history
        updatePosturalHistory(landmarks, overallScore)

        return PosturalAnalysis(
            headPosition = headPosition,
            shoulderAlignment = shoulderAlignment,
            spinalAlignment = spinalAlignment,
            pelvicAlignment = pelvicAlignment,
            legAlignment = legAlignment,
            overallPostureScore = overallScore,
            posturalDeviations = posturalDeviations,
            recommendations = recommendations
        )
    }

    /**
     * Assess head and neck position
     */
    private fun assessHeadPosition(landmarks: PoseLandmarkResult): PosturalComponent {
        val nose = landmarks.landmarks[PoseLandmarks.NOSE]
        val leftEar = landmarks.landmarks[PoseLandmarks.LEFT_EAR]
        val rightEar = landmarks.landmarks[PoseLandmarks.RIGHT_EAR]
        val leftShoulder = landmarks.landmarks[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks.landmarks[PoseLandmarks.RIGHT_SHOULDER]

        // Calculate ear-shoulder alignment
        val earMidpoint = calculateMidpoint(leftEar, rightEar)
        val shoulderMidpoint = calculateMidpoint(leftShoulder, rightShoulder)

        // Forward head posture assessment
        val headAngle = calculateForwardHeadAngle(earMidpoint, shoulderMidpoint)

        // Lateral head tilt assessment
        val lateralTilt = calculateLateralHeadTilt(leftEar, rightEar)

        // Combined head position score
        val forwardDeviation = abs(headAngle - idealPosturalValues["head_angle"]!!)
        val lateralDeviation = abs(lateralTilt)

        val score = calculatePosturalScore(forwardDeviation, posturalTolerances["head_angle"]!!) *
                   calculatePosturalScore(lateralDeviation, posturalTolerances["head_angle"]!!)

        val status = determinePosturalStatus(score)

        return PosturalComponent(
            name = "Head Position",
            score = score,
            deviation = maxOf(forwardDeviation, lateralDeviation),
            status = status
        )
    }

    /**
     * Assess shoulder alignment and levelness
     */
    private fun assessShoulderAlignment(landmarks: PoseLandmarkResult): PosturalComponent {
        val leftShoulder = landmarks.landmarks[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks.landmarks[PoseLandmarks.RIGHT_SHOULDER]

        // Shoulder levelness
        val shoulderLevelness = calculateShoulderLevelness(leftShoulder, rightShoulder)

        // Shoulder protraction (forward shoulders)
        val shoulderProtraction = calculateShoulderProtraction(landmarks)

        // Shoulder elevation asymmetry
        val elevationAsymmetry = calculateShoulderElevationAsymmetry(leftShoulder, rightShoulder)

        val totalDeviation = maxOf(shoulderLevelness, shoulderProtraction, elevationAsymmetry)
        val score = calculatePosturalScore(totalDeviation, posturalTolerances["shoulder_level"]!!)
        val status = determinePosturalStatus(score)

        return PosturalComponent(
            name = "Shoulder Alignment",
            score = score,
            deviation = totalDeviation,
            status = status
        )
    }

    /**
     * Assess spinal alignment and curves
     */
    private fun assessSpinalAlignment(landmarks: PoseLandmarkResult): PosturalComponent {
        val nose = landmarks.landmarks[PoseLandmarks.NOSE]
        val leftShoulder = landmarks.landmarks[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks.landmarks[PoseLandmarks.RIGHT_SHOULDER]
        val leftHip = landmarks.landmarks[PoseLandmarks.LEFT_HIP]
        val rightHip = landmarks.landmarks[PoseLandmarks.RIGHT_HIP]

        val shoulderMidpoint = calculateMidpoint(leftShoulder, rightShoulder)
        val hipMidpoint = calculateMidpoint(leftHip, rightHip)

        // Spinal alignment from side view
        val spinalAngle = calculateSpinalAlignment(shoulderMidpoint, hipMidpoint)

        // Lateral spinal deviation
        val lateralDeviation = calculateLateralSpinalDeviation(shoulderMidpoint, hipMidpoint)

        // Cervical alignment
        val cervicalAlignment = calculateCervicalAlignment(nose, shoulderMidpoint)

        val maxDeviation = maxOf(
            abs(spinalAngle - idealPosturalValues["cervical_angle"]!!),
            lateralDeviation,
            abs(cervicalAlignment)
        )

        val score = calculatePosturalScore(maxDeviation, posturalTolerances["cervical_angle"]!!)
        val status = determinePosturalStatus(score)

        return PosturalComponent(
            name = "Spinal Alignment",
            score = score,
            deviation = maxDeviation,
            status = status
        )
    }

    /**
     * Assess pelvic alignment and tilt
     */
    private fun assessPelvicAlignment(landmarks: PoseLandmarkResult): PosturalComponent {
        val leftHip = landmarks.landmarks[PoseLandmarks.LEFT_HIP]
        val rightHip = landmarks.landmarks[PoseLandmarks.RIGHT_HIP]

        // Pelvic levelness (frontal plane)
        val pelvicLevelness = calculatePelvicLevelness(leftHip, rightHip)

        // Pelvic tilt assessment (sagittal plane)
        val pelvicTilt = calculatePelvicTilt(landmarks)

        // Pelvic rotation (transverse plane)
        val pelvicRotation = calculatePelvicRotation(leftHip, rightHip)

        val maxDeviation = maxOf(pelvicLevelness, abs(pelvicTilt), pelvicRotation)
        val score = calculatePosturalScore(maxDeviation, posturalTolerances["pelvic_tilt"]!!)
        val status = determinePosturalStatus(score)

        return PosturalComponent(
            name = "Pelvic Alignment",
            score = score,
            deviation = maxDeviation,
            status = status
        )
    }

    /**
     * Assess leg alignment and knee position
     */
    private fun assessLegAlignment(landmarks: PoseLandmarkResult): PosturalComponent {
        val leftHip = landmarks.landmarks[PoseLandmarks.LEFT_HIP]
        val rightHip = landmarks.landmarks[PoseLandmarks.RIGHT_HIP]
        val leftKnee = landmarks.landmarks[PoseLandmarks.LEFT_KNEE]
        val rightKnee = landmarks.landmarks[PoseLandmarks.RIGHT_KNEE]
        val leftAnkle = landmarks.landmarks[PoseLandmarks.LEFT_ANKLE]
        val rightAnkle = landmarks.landmarks[PoseLandmarks.RIGHT_ANKLE]

        // Knee valgus/varus assessment
        val leftKneeAlignment = calculateKneeAlignment(leftHip, leftKnee, leftAnkle)
        val rightKneeAlignment = calculateKneeAlignment(rightHip, rightKnee, rightAnkle)

        // Leg length discrepancy
        val legLengthAsymmetry = calculateLegLengthAsymmetry(
            leftHip, leftKnee, leftAnkle,
            rightHip, rightKnee, rightAnkle
        )

        // Ankle alignment
        val leftAnkleAlignment = calculateAnkleAlignment(leftKnee, leftAnkle)
        val rightAnkleAlignment = calculateAnkleAlignment(rightKnee, rightAnkle)

        val maxDeviation = maxOf(
            abs(leftKneeAlignment), abs(rightKneeAlignment),
            legLengthAsymmetry,
            abs(leftAnkleAlignment), abs(rightAnkleAlignment)
        )

        val score = calculatePosturalScore(maxDeviation, posturalTolerances["knee_alignment"]!!)
        val status = determinePosturalStatus(score)

        return PosturalComponent(
            name = "Leg Alignment",
            score = score,
            deviation = maxDeviation,
            status = status
        )
    }

    /**
     * Calculate forward head angle
     */
    private fun calculateForwardHeadAngle(earPosition: PoseLandmarkResult.Landmark, shoulderPosition: PoseLandmarkResult.Landmark): Float {
        val horizontalDistance = abs(earPosition.x - shoulderPosition.x)
        val verticalDistance = abs(earPosition.y - shoulderPosition.y)

        return if (verticalDistance > 0) {
            Math.toDegrees(atan((horizontalDistance / verticalDistance).toDouble())).toFloat()
        } else 0f
    }

    /**
     * Calculate lateral head tilt
     */
    private fun calculateLateralHeadTilt(leftEar: PoseLandmarkResult.Landmark, rightEar: PoseLandmarkResult.Landmark): Float {
        val heightDifference = leftEar.y - rightEar.y
        val horizontalDistance = abs(leftEar.x - rightEar.x)

        return if (horizontalDistance > 0) {
            Math.toDegrees(atan((heightDifference / horizontalDistance).toDouble())).toFloat()
        } else 0f
    }

    /**
     * Calculate shoulder levelness
     */
    private fun calculateShoulderLevelness(leftShoulder: PoseLandmarkResult.Landmark, rightShoulder: PoseLandmarkResult.Landmark): Float {
        val heightDifference = abs(leftShoulder.y - rightShoulder.y)
        val shoulderWidth = abs(leftShoulder.x - rightShoulder.x)

        return if (shoulderWidth > 0) {
            Math.toDegrees(atan((heightDifference / shoulderWidth).toDouble())).toFloat()
        } else 0f
    }

    /**
     * Calculate shoulder protraction
     */
    private fun calculateShoulderProtraction(landmarks: PoseLandmarkResult): Float {
        val leftShoulder = landmarks.landmarks[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks.landmarks[PoseLandmarks.RIGHT_SHOULDER]
        val leftEar = landmarks.landmarks[PoseLandmarks.LEFT_EAR]
        val rightEar = landmarks.landmarks[PoseLandmarks.RIGHT_EAR]

        val shoulderMidpoint = calculateMidpoint(leftShoulder, rightShoulder)
        val earMidpoint = calculateMidpoint(leftEar, rightEar)

        // Calculate protraction angle
        val forwardDistance = abs(shoulderMidpoint.x - earMidpoint.x)
        val verticalDistance = abs(shoulderMidpoint.y - earMidpoint.y)

        return if (verticalDistance > 0) {
            Math.toDegrees(atan((forwardDistance / verticalDistance).toDouble())).toFloat()
        } else 0f
    }

    /**
     * Calculate shoulder elevation asymmetry
     */
    private fun calculateShoulderElevationAsymmetry(leftShoulder: PoseLandmarkResult.Landmark, rightShoulder: PoseLandmarkResult.Landmark): Float {
        return abs(leftShoulder.y - rightShoulder.y) * 100f // Convert to degrees equivalent
    }

    /**
     * Calculate spinal alignment angle
     */
    private fun calculateSpinalAlignment(shoulderPoint: PoseLandmarkResult.Landmark, hipPoint: PoseLandmarkResult.Landmark): Float {
        val spineVector = VectorUtils.vectorFromPoints(
            hipPoint.x.toDouble(), hipPoint.y.toDouble(),
            shoulderPoint.x.toDouble(), shoulderPoint.y.toDouble()
        )
        val verticalVector = VectorUtils.Vector2D(0.0, 1.0)

        return Math.toDegrees(spineVector.angleWith(verticalVector)).toFloat()
    }

    /**
     * Calculate lateral spinal deviation
     */
    private fun calculateLateralSpinalDeviation(shoulderPoint: PoseLandmarkResult.Landmark, hipPoint: PoseLandmarkResult.Landmark): Float {
        return abs(shoulderPoint.x - hipPoint.x) * 100f // Convert to angle equivalent
    }

    /**
     * Calculate cervical alignment
     */
    private fun calculateCervicalAlignment(nosePoint: PoseLandmarkResult.Landmark, shoulderPoint: PoseLandmarkResult.Landmark): Float {
        val neckVector = VectorUtils.vectorFromPoints(
            shoulderPoint.x.toDouble(), shoulderPoint.y.toDouble(),
            nosePoint.x.toDouble(), nosePoint.y.toDouble()
        )
        val verticalVector = VectorUtils.Vector2D(0.0, 1.0)

        return Math.toDegrees(neckVector.angleWith(verticalVector)).toFloat()
    }

    /**
     * Calculate pelvic levelness
     */
    private fun calculatePelvicLevelness(leftHip: PoseLandmarkResult.Landmark, rightHip: PoseLandmarkResult.Landmark): Float {
        val heightDifference = abs(leftHip.y - rightHip.y)
        val hipWidth = abs(leftHip.x - rightHip.x)

        return if (hipWidth > 0) {
            Math.toDegrees(atan((heightDifference / hipWidth).toDouble())).toFloat()
        } else 0f
    }

    /**
     * Calculate pelvic tilt
     */
    private fun calculatePelvicTilt(landmarks: PoseLandmarkResult): Float {
        val leftHip = landmarks.landmarks[PoseLandmarks.LEFT_HIP]
        val rightHip = landmarks.landmarks[PoseLandmarks.RIGHT_HIP]

        // Using world coordinates for depth information
        val leftHipWorld = landmarks.worldLandmarks[PoseLandmarks.LEFT_HIP]
        val rightHipWorld = landmarks.worldLandmarks[PoseLandmarks.RIGHT_HIP]

        val avgDepth = (leftHipWorld.z + rightHipWorld.z) / 2f
        val avgHeight = (leftHip.y + rightHip.y) / 2f

        // Approximate pelvic tilt from depth-height ratio
        return Math.toDegrees(atan(avgDepth / avgHeight.coerceAtLeast(0.1f).toDouble())).toFloat()
    }

    /**
     * Calculate pelvic rotation
     */
    private fun calculatePelvicRotation(leftHip: PoseLandmarkResult.Landmark, rightHip: PoseLandmarkResult.Landmark): Float {
        val pelvicVector = VectorUtils.vectorFromPoints(
            leftHip.x.toDouble(), leftHip.y.toDouble(),
            rightHip.x.toDouble(), rightHip.y.toDouble()
        )
        val horizontalVector = VectorUtils.Vector2D(1.0, 0.0)

        return Math.toDegrees(pelvicVector.angleWith(horizontalVector)).toFloat()
    }

    /**
     * Calculate knee alignment (valgus/varus)
     */
    private fun calculateKneeAlignment(hip: PoseLandmarkResult.Landmark, knee: PoseLandmarkResult.Landmark, ankle: PoseLandmarkResult.Landmark): Float {
        // Calculate the angle of the knee relative to the vertical line from hip to ankle
        val hipAnkleVector = VectorUtils.vectorFromPoints(
            hip.x.toDouble(), hip.y.toDouble(),
            ankle.x.toDouble(), ankle.y.toDouble()
        )
        val hipKneeVector = VectorUtils.vectorFromPoints(
            hip.x.toDouble(), hip.y.toDouble(),
            knee.x.toDouble(), knee.y.toDouble()
        )

        return Math.toDegrees(hipAnkleVector.angleWith(hipKneeVector)).toFloat()
    }

    /**
     * Calculate leg length asymmetry
     */
    private fun calculateLegLengthAsymmetry(
        leftHip: PoseLandmarkResult.Landmark, leftKnee: PoseLandmarkResult.Landmark, leftAnkle: PoseLandmarkResult.Landmark,
        rightHip: PoseLandmarkResult.Landmark, rightKnee: PoseLandmarkResult.Landmark, rightAnkle: PoseLandmarkResult.Landmark
    ): Float {
        val leftLegLength = calculateLegLength(leftHip, leftKnee, leftAnkle)
        val rightLegLength = calculateLegLength(rightHip, rightKnee, rightAnkle)

        val asymmetry = abs(leftLegLength - rightLegLength) / maxOf(leftLegLength, rightLegLength)
        return asymmetry * 100f // Convert to percentage
    }

    /**
     * Calculate leg length
     */
    private fun calculateLegLength(hip: PoseLandmarkResult.Landmark, knee: PoseLandmarkResult.Landmark, ankle: PoseLandmarkResult.Landmark): Float {
        val thighLength = sqrt((hip.x - knee.x).pow(2) + (hip.y - knee.y).pow(2))
        val shinLength = sqrt((knee.x - ankle.x).pow(2) + (knee.y - ankle.y).pow(2))
        return thighLength + shinLength
    }

    /**
     * Calculate ankle alignment
     */
    private fun calculateAnkleAlignment(knee: PoseLandmarkResult.Landmark, ankle: PoseLandmarkResult.Landmark): Float {
        val ankleVector = VectorUtils.vectorFromPoints(
            knee.x.toDouble(), knee.y.toDouble(),
            ankle.x.toDouble(), ankle.y.toDouble()
        )
        val verticalVector = VectorUtils.Vector2D(0.0, 1.0)

        return Math.toDegrees(ankleVector.angleWith(verticalVector)).toFloat()
    }

    /**
     * Calculate midpoint between two landmarks
     */
    private fun calculateMidpoint(p1: PoseLandmarkResult.Landmark, p2: PoseLandmarkResult.Landmark): PoseLandmarkResult.Landmark {
        return PoseLandmarkResult.Landmark(
            x = (p1.x + p2.x) / 2f,
            y = (p1.y + p2.y) / 2f,
            z = (p1.z + p2.z) / 2f,
            visibility = min(p1.visibility, p2.visibility),
            presence = min(p1.presence, p2.presence)
        )
    }

    /**
     * Calculate overall posture score
     */
    private fun calculateOverallPostureScore(vararg components: PosturalComponent): Float {
        return components.map { it.score }.average().toFloat()
    }

    /**
     * Calculate postural score from deviation and tolerance
     */
    private fun calculatePosturalScore(deviation: Float, tolerance: Float): Float {
        return (1f - (deviation / tolerance).coerceAtMost(1f)).coerceAtLeast(0f)
    }

    /**
     * Determine postural status from score
     */
    private fun determinePosturalStatus(score: Float): PosturalStatus {
        return when {
            score >= 0.9f -> PosturalStatus.EXCELLENT
            score >= 0.8f -> PosturalStatus.GOOD
            score >= 0.6f -> PosturalStatus.FAIR
            score >= 0.4f -> PosturalStatus.POOR
            else -> PosturalStatus.CRITICAL
        }
    }

    /**
     * Detect specific postural deviations
     */
    private fun detectPosturalDeviations(landmarks: PoseLandmarkResult): List<PosturalDeviation> {
        val deviations = mutableListOf<PosturalDeviation>()

        // Forward head posture
        val nose = landmarks.landmarks[PoseLandmarks.NOSE]
        val leftShoulder = landmarks.landmarks[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks.landmarks[PoseLandmarks.RIGHT_SHOULDER]
        val shoulderMidpoint = calculateMidpoint(leftShoulder, rightShoulder)

        val forwardHeadSeverity = abs(nose.x - shoulderMidpoint.x) * 10f // Scale for severity
        if (forwardHeadSeverity > 1f) {
            deviations.add(
                PosturalDeviation(
                    type = PosturalDeviationType.FORWARD_HEAD_POSTURE,
                    severity = forwardHeadSeverity.coerceAtMost(5f),
                    description = "Forward head posture detected",
                    correctionSuggestion = "Strengthen deep neck flexors and stretch chest muscles"
                )
            )
        }

        // Rounded shoulders
        val shoulderProtraction = calculateShoulderProtraction(landmarks)
        if (shoulderProtraction > 15f) {
            deviations.add(
                PosturalDeviation(
                    type = PosturalDeviationType.ROUNDED_SHOULDERS,
                    severity = (shoulderProtraction / 30f).coerceAtMost(5f),
                    description = "Rounded shoulders detected",
                    correctionSuggestion = "Strengthen rhomboids and middle trapezius, stretch pectorals"
                )
            )
        }

        // Pelvic tilt
        val pelvicTilt = calculatePelvicTilt(landmarks)
        if (abs(pelvicTilt) > 10f) {
            val deviationType = if (pelvicTilt > 0) PosturalDeviationType.LORDOSIS else PosturalDeviationType.KYPHOSIS
            deviations.add(
                PosturalDeviation(
                    type = deviationType,
                    severity = (abs(pelvicTilt) / 20f).coerceAtMost(5f),
                    description = if (pelvicTilt > 0) "Excessive lumbar lordosis" else "Reduced lumbar lordosis",
                    correctionSuggestion = if (pelvicTilt > 0) "Strengthen core and glutes" else "Improve hip flexor flexibility"
                )
            )
        }

        // Knee valgus/varus
        val leftHip = landmarks.landmarks[PoseLandmarks.LEFT_HIP]
        val rightHip = landmarks.landmarks[PoseLandmarks.RIGHT_HIP]
        val leftKnee = landmarks.landmarks[PoseLandmarks.LEFT_KNEE]
        val rightKnee = landmarks.landmarks[PoseLandmarks.RIGHT_KNEE]
        val leftAnkle = landmarks.landmarks[PoseLandmarks.LEFT_ANKLE]
        val rightAnkle = landmarks.landmarks[PoseLandmarks.RIGHT_ANKLE]

        val leftKneeAlignment = calculateKneeAlignment(leftHip, leftKnee, leftAnkle)
        val rightKneeAlignment = calculateKneeAlignment(rightHip, rightKnee, rightAnkle)

        val maxKneeDeviation = maxOf(abs(leftKneeAlignment), abs(rightKneeAlignment))
        if (maxKneeDeviation > 15f) {
            val isValgus = (leftKneeAlignment + rightKneeAlignment) / 2f > 0
            deviations.add(
                PosturalDeviation(
                    type = if (isValgus) PosturalDeviationType.KNEE_VALGUS else PosturalDeviationType.KNEE_VARUS,
                    severity = (maxKneeDeviation / 30f).coerceAtMost(5f),
                    description = if (isValgus) "Knee valgus (knock-knees) detected" else "Knee varus (bow-legs) detected",
                    correctionSuggestion = if (isValgus) "Strengthen hip abductors and external rotators" else "Improve ankle and hip mobility"
                )
            )
        }

        return deviations
    }

    /**
     * Generate postural recommendations
     */
    private fun generatePosturalRecommendations(
        headPosition: PosturalComponent,
        shoulderAlignment: PosturalComponent,
        spinalAlignment: PosturalComponent,
        pelvicAlignment: PosturalComponent,
        legAlignment: PosturalComponent,
        deviations: List<PosturalDeviation>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        // General postural recommendations based on component scores
        if (headPosition.score < 0.7f) {
            recommendations.add("Focus on neck and head positioning exercises")
        }

        if (shoulderAlignment.score < 0.7f) {
            recommendations.add("Work on shoulder blade stability and chest flexibility")
        }

        if (spinalAlignment.score < 0.7f) {
            recommendations.add("Strengthen core muscles and improve spinal mobility")
        }

        if (pelvicAlignment.score < 0.7f) {
            recommendations.add("Address pelvic positioning through hip strengthening")
        }

        if (legAlignment.score < 0.7f) {
            recommendations.add("Focus on lower extremity alignment and stability")
        }

        // Specific recommendations from detected deviations
        deviations.forEach { deviation ->
            recommendations.add(deviation.correctionSuggestion)
        }

        // Add general postural awareness recommendations
        val overallScore = calculateOverallPostureScore(headPosition, shoulderAlignment, spinalAlignment, pelvicAlignment, legAlignment)
        when {
            overallScore < 0.5f -> {
                recommendations.add("Consider professional postural assessment")
                recommendations.add("Implement regular posture breaks during activities")
            }
            overallScore < 0.7f -> {
                recommendations.add("Increase postural awareness throughout the day")
                recommendations.add("Practice basic postural correction exercises")
            }
            else -> {
                recommendations.add("Maintain current good postural habits")
            }
        }

        return recommendations.distinct()
    }

    /**
     * Update postural history for trend analysis
     */
    private fun updatePosturalHistory(landmarks: PoseLandmarkResult, overallScore: Float) {
        val snapshot = PosturalSnapshot(
            timestamp = landmarks.timestampMs,
            overallScore = overallScore,
            headScore = assessHeadPosition(landmarks).score,
            shoulderScore = assessShoulderAlignment(landmarks).score,
            spinalScore = assessSpinalAlignment(landmarks).score,
            pelvicScore = assessPelvicAlignment(landmarks).score,
            legScore = assessLegAlignment(landmarks).score
        )

        posturalHistory.add(snapshot)
        if (posturalHistory.size > maxHistorySize) {
            posturalHistory.removeAt(0)
        }
    }

    /**
     * Get postural trend analysis
     */
    fun getPosturalTrends(windowMs: Long = 10000): PosturalTrends? {
        val cutoffTime = System.currentTimeMillis() - windowMs
        val relevantHistory = posturalHistory.filter { it.timestamp >= cutoffTime }

        if (relevantHistory.size < 10) return null

        val recent = relevantHistory.takeLast(relevantHistory.size / 2)
        val earlier = relevantHistory.take(relevantHistory.size / 2)

        return PosturalTrends(
            overallTrend = calculateTrend(earlier.map { it.overallScore }, recent.map { it.overallScore }),
            headTrend = calculateTrend(earlier.map { it.headScore }, recent.map { it.headScore }),
            shoulderTrend = calculateTrend(earlier.map { it.shoulderScore }, recent.map { it.shoulderScore }),
            spinalTrend = calculateTrend(earlier.map { it.spinalScore }, recent.map { it.spinalScore }),
            pelvicTrend = calculateTrend(earlier.map { it.pelvicScore }, recent.map { it.pelvicScore }),
            legTrend = calculateTrend(earlier.map { it.legScore }, recent.map { it.legScore })
        )
    }

    private fun calculateTrend(earlier: List<Float>, recent: List<Float>): PosturalTrend {
        val earlierAvg = earlier.average()
        val recentAvg = recent.average()
        val change = recentAvg - earlierAvg

        return when {
            change > 0.05 -> PosturalTrend.IMPROVING
            change < -0.05 -> PosturalTrend.DECLINING
            else -> PosturalTrend.STABLE
        }
    }

    /**
     * Reset postural tracking
     */
    fun reset() {
        posturalHistory.clear()
    }
}

// Supporting data classes
data class PosturalSnapshot(
    val timestamp: Long,
    val overallScore: Float,
    val headScore: Float,
    val shoulderScore: Float,
    val spinalScore: Float,
    val pelvicScore: Float,
    val legScore: Float
)

data class PosturalTrends(
    val overallTrend: PosturalTrend,
    val headTrend: PosturalTrend,
    val shoulderTrend: PosturalTrend,
    val spinalTrend: PosturalTrend,
    val pelvicTrend: PosturalTrend,
    val legTrend: PosturalTrend
)

enum class PosturalTrend {
    IMPROVING,
    STABLE,
    DECLINING
}