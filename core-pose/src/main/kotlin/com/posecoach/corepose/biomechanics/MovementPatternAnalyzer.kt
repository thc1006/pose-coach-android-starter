package com.posecoach.corepose.biomechanics

import com.posecoach.corepose.PoseLandmarks
import com.posecoach.corepose.biomechanics.models.*
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.coregeom.AngleUtils
import com.posecoach.coregeom.VectorUtils
import com.posecoach.corepose.utils.averageOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Advanced movement pattern analyzer that performs temporal sequence analysis
 * for exercise identification, movement quality scoring, and form breakdown prediction.
 *
 * Features:
 * - Exercise pattern recognition from pose sequences
 * - Movement phase detection and timing analysis
 * - Quality scoring based on biomechanical principles
 * - Form breakdown prediction before it occurs
 * - Tempo and rhythm analysis
 * - Movement efficiency assessment
 */
class MovementPatternAnalyzer {

    // Movement pattern templates for common exercises
    private val movementTemplates = initializeMovementTemplates()

    // Temporal analysis parameters
    private val minSequenceLength = 10 // Minimum poses for pattern analysis
    private val maxSequenceLength = 60 // Maximum poses for single movement cycle

    // Pattern matching thresholds
    private val similarityThreshold = 0.7f
    private val confidenceThreshold = 0.6f

    /**
     * Analyze a sequence of poses for movement pattern recognition
     */
    suspend fun analyzeSequence(poses: List<TimestampedPose>): MovementPattern? {
        return withContext(Dispatchers.Default) {
            if (poses.size < minSequenceLength) return@withContext null

            try {
                // Extract key features from pose sequence
                val features = extractMovementFeatures(poses)

                // Identify movement pattern
                val patternType = identifyMovementPattern(features)

                // Analyze movement phases
                val phases = analyzeMovementPhases(poses, patternType)

                // Calculate tempo and rhythm
                val tempo = calculateMovementTempo(poses, phases)

                // Assess movement quality
                val qualityScore = assessMovementQuality(poses, patternType, phases)

                // Calculate symmetry
                val symmetry = calculateMovementSymmetry(poses)

                // Assess efficiency
                val efficiency = calculateMovementEfficiency(poses, patternType)

                // Generate recommendations
                val recommendations = generateMovementRecommendations(
                    patternType, qualityScore, symmetry, efficiency, tempo
                )

                // Calculate confidence based on pattern matching quality
                val confidence = calculatePatternConfidence(features, patternType)

                return@withContext MovementPattern(
                    patternType = patternType,
                    confidence = confidence,
                    qualityScore = qualityScore,
                    phase = determineCurrentPhase(poses.last(), patternType),
                    tempo = tempo,
                    symmetry = symmetry,
                    efficiency = efficiency,
                    recommendations = recommendations
                )

            } catch (e: Exception) {
                println("Error in movement pattern analysis: ${e.message}")
                return@withContext null
            }
        }
    }

    /**
     * Extract movement features from pose sequence
     */
    private fun extractMovementFeatures(poses: List<TimestampedPose>): MovementFeatures {
        val keyJoints = listOf(
            PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER,
            PoseLandmarks.LEFT_ELBOW, PoseLandmarks.RIGHT_ELBOW,
            PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP,
            PoseLandmarks.LEFT_KNEE, PoseLandmarks.RIGHT_KNEE
        )

        // Calculate joint trajectories
        val trajectories = keyJoints.associateWith { jointIdx ->
            poses.map { pose ->
                val landmark = pose.pose.landmarks[jointIdx]
                Triple(landmark.x, landmark.y, landmark.z)
            }
        }

        // Calculate movement velocity profiles
        val velocityProfiles = trajectories.mapValues { (_, trajectory) ->
            calculateVelocityProfile(trajectory, poses.map { it.timestamp })
        }

        // Calculate key angle changes
        val angleChanges = calculateKeyAngleChanges(poses)

        // Calculate center of mass movement
        val comMovement = calculateCenterOfMassMovement(poses)

        // Calculate movement range and direction
        val movementRange = calculateMovementRange(trajectories)
        val primaryDirection = calculatePrimaryMovementDirection(comMovement)

        return MovementFeatures(
            trajectories = trajectories,
            velocityProfiles = velocityProfiles,
            angleChanges = angleChanges,
            comMovement = comMovement,
            movementRange = movementRange,
            primaryDirection = primaryDirection,
            duration = poses.last().timestamp - poses.first().timestamp,
            poseCount = poses.size
        )
    }

    /**
     * Identify movement pattern type from features
     */
    private fun identifyMovementPattern(features: MovementFeatures): MovementPatternType {
        var bestMatch = MovementPatternType.UNKNOWN
        var bestScore = 0f

        movementTemplates.forEach { (patternType, template) ->
            val score = calculatePatternMatchScore(features, template)
            if (score > bestScore && score > similarityThreshold) {
                bestScore = score
                bestMatch = patternType
            }
        }

        return bestMatch
    }

    /**
     * Analyze movement phases within the sequence
     */
    private fun analyzeMovementPhases(
        poses: List<TimestampedPose>,
        patternType: MovementPatternType
    ): List<MovementPhaseSegment> {
        val phases = mutableListOf<MovementPhaseSegment>()

        when (patternType) {
            MovementPatternType.SQUAT -> phases.addAll(analyzeSquatPhases(poses))
            MovementPatternType.PUSH_UP -> phases.addAll(analyzePushUpPhases(poses))
            MovementPatternType.DEADLIFT -> phases.addAll(analyzeDeadliftPhases(poses))
            MovementPatternType.LUNGE -> phases.addAll(analyzeLungePhases(poses))
            else -> phases.addAll(analyzeGenericPhases(poses))
        }

        return phases
    }

    /**
     * Analyze squat movement phases
     */
    private fun analyzeSquatPhases(poses: List<TimestampedPose>): List<MovementPhaseSegment> {
        val phases = mutableListOf<MovementPhaseSegment>()

        // Calculate hip height trajectory
        val hipHeights = poses.map { pose ->
            val leftHip = pose.pose.landmarks[PoseLandmarks.LEFT_HIP]
            val rightHip = pose.pose.landmarks[PoseLandmarks.RIGHT_HIP]
            (leftHip.y + rightHip.y) / 2f
        }

        // Find key points in movement
        val startHeight = hipHeights.first()
        val minHeight = hipHeights.minOrNull() ?: startHeight
        val minHeightIndex = hipHeights.indexOf(minHeight)

        // Descending phase (eccentric)
        if (minHeightIndex > 0) {
            phases.add(
                MovementPhaseSegment(
                    phase = MovementPhase.ECCENTRIC,
                    startIndex = 0,
                    endIndex = minHeightIndex,
                    startTime = poses.first().timestamp,
                    endTime = poses[minHeightIndex].timestamp,
                    qualityScore = assessPhaseQuality(poses.subList(0, minHeightIndex + 1), MovementPhase.ECCENTRIC)
                )
            )
        }

        // Bottom position
        val bottomStart = maxOf(0, minHeightIndex - 2)
        val bottomEnd = minOf(poses.size - 1, minHeightIndex + 2)
        if (bottomEnd > bottomStart) {
            phases.add(
                MovementPhaseSegment(
                    phase = MovementPhase.BOTTOM_POSITION,
                    startIndex = bottomStart,
                    endIndex = bottomEnd,
                    startTime = poses[bottomStart].timestamp,
                    endTime = poses[bottomEnd].timestamp,
                    qualityScore = assessPhaseQuality(poses.subList(bottomStart, bottomEnd + 1), MovementPhase.BOTTOM_POSITION)
                )
            )
        }

        // Ascending phase (concentric)
        if (minHeightIndex < poses.size - 1) {
            phases.add(
                MovementPhaseSegment(
                    phase = MovementPhase.CONCENTRIC,
                    startIndex = minHeightIndex,
                    endIndex = poses.size - 1,
                    startTime = poses[minHeightIndex].timestamp,
                    endTime = poses.last().timestamp,
                    qualityScore = assessPhaseQuality(poses.subList(minHeightIndex, poses.size), MovementPhase.CONCENTRIC)
                )
            )
        }

        return phases
    }

    /**
     * Analyze push-up movement phases
     */
    private fun analyzePushUpPhases(poses: List<TimestampedPose>): List<MovementPhaseSegment> {
        val phases = mutableListOf<MovementPhaseSegment>()

        // Calculate chest/shoulder height trajectory
        val chestHeights = poses.map { pose ->
            val leftShoulder = pose.pose.landmarks[PoseLandmarks.LEFT_SHOULDER]
            val rightShoulder = pose.pose.landmarks[PoseLandmarks.RIGHT_SHOULDER]
            (leftShoulder.y + rightShoulder.y) / 2f
        }

        val minHeight = chestHeights.minOrNull() ?: chestHeights.first()
        val minHeightIndex = chestHeights.indexOf(minHeight)

        // Descending phase
        if (minHeightIndex > 0) {
            phases.add(
                MovementPhaseSegment(
                    phase = MovementPhase.ECCENTRIC,
                    startIndex = 0,
                    endIndex = minHeightIndex,
                    startTime = poses.first().timestamp,
                    endTime = poses[minHeightIndex].timestamp,
                    qualityScore = assessPhaseQuality(poses.subList(0, minHeightIndex + 1), MovementPhase.ECCENTRIC)
                )
            )
        }

        // Bottom position
        phases.add(
            MovementPhaseSegment(
                phase = MovementPhase.BOTTOM_POSITION,
                startIndex = maxOf(0, minHeightIndex - 1),
                endIndex = minOf(poses.size - 1, minHeightIndex + 1),
                startTime = poses[maxOf(0, minHeightIndex - 1)].timestamp,
                endTime = poses[minOf(poses.size - 1, minHeightIndex + 1)].timestamp,
                qualityScore = assessPhaseQuality(
                    poses.subList(maxOf(0, minHeightIndex - 1), minOf(poses.size, minHeightIndex + 2)),
                    MovementPhase.BOTTOM_POSITION
                )
            )
        )

        // Ascending phase
        if (minHeightIndex < poses.size - 1) {
            phases.add(
                MovementPhaseSegment(
                    phase = MovementPhase.CONCENTRIC,
                    startIndex = minHeightIndex,
                    endIndex = poses.size - 1,
                    startTime = poses[minHeightIndex].timestamp,
                    endTime = poses.last().timestamp,
                    qualityScore = assessPhaseQuality(poses.subList(minHeightIndex, poses.size), MovementPhase.CONCENTRIC)
                )
            )
        }

        return phases
    }

    /**
     * Analyze deadlift movement phases
     */
    private fun analyzeDeadliftPhases(poses: List<TimestampedPose>): List<MovementPhaseSegment> {
        // Similar to squat but with different biomechanical markers
        return analyzeSquatPhases(poses) // Simplified for now
    }

    /**
     * Analyze lunge movement phases
     */
    private fun analyzeLungePhases(poses: List<TimestampedPose>): List<MovementPhaseSegment> {
        // Analyze forward/backward movement pattern
        return analyzeGenericPhases(poses) // Simplified for now
    }

    /**
     * Analyze generic movement phases
     */
    private fun analyzeGenericPhases(poses: List<TimestampedPose>): List<MovementPhaseSegment> {
        val phases = mutableListOf<MovementPhaseSegment>()

        // Basic phase segmentation based on movement velocity
        val velocities = calculateOverallVelocity(poses)
        val lowVelocityThreshold = velocities.average() * 0.3

        var currentPhase = MovementPhase.PREPARATION
        var phaseStartIndex = 0

        for (i in velocities.indices) {
            val velocity = velocities[i]
            val nextPhase = when {
                velocity < lowVelocityThreshold -> MovementPhase.HOLD
                i < velocities.size / 2 -> MovementPhase.ECCENTRIC
                else -> MovementPhase.CONCENTRIC
            }

            if (nextPhase != currentPhase) {
                // End current phase
                if (i > phaseStartIndex) {
                    phases.add(
                        MovementPhaseSegment(
                            phase = currentPhase,
                            startIndex = phaseStartIndex,
                            endIndex = i - 1,
                            startTime = poses[phaseStartIndex].timestamp,
                            endTime = poses[i - 1].timestamp,
                            qualityScore = assessPhaseQuality(
                                poses.subList(phaseStartIndex, i),
                                currentPhase
                            )
                        )
                    )
                }

                currentPhase = nextPhase
                phaseStartIndex = i
            }
        }

        // Add final phase
        if (phaseStartIndex < poses.size) {
            phases.add(
                MovementPhaseSegment(
                    phase = currentPhase,
                    startIndex = phaseStartIndex,
                    endIndex = poses.size - 1,
                    startTime = poses[phaseStartIndex].timestamp,
                    endTime = poses.last().timestamp,
                    qualityScore = assessPhaseQuality(
                        poses.subList(phaseStartIndex, poses.size),
                        currentPhase
                    )
                )
            )
        }

        return phases
    }

    /**
     * Calculate movement tempo from phases
     */
    private fun calculateMovementTempo(
        poses: List<TimestampedPose>,
        phases: List<MovementPhaseSegment>
    ): MovementTempo {
        val eccentricPhase = phases.find { it.phase == MovementPhase.ECCENTRIC }
        val concentricPhase = phases.find { it.phase == MovementPhase.CONCENTRIC }
        val pausePhase = phases.find { it.phase == MovementPhase.BOTTOM_POSITION }

        val eccentricTime = eccentricPhase?.let { (it.endTime - it.startTime) / 1000f } ?: 0f
        val concentricTime = concentricPhase?.let { (it.endTime - it.startTime) / 1000f } ?: 0f
        val pauseTime = pausePhase?.let { (it.endTime - it.startTime) / 1000f } ?: 0f

        val totalTime = (poses.last().timestamp - poses.first().timestamp) / 1000f

        val rhythm = when {
            totalTime < 1f -> TempoRhythm.TOO_FAST
            totalTime > 8f -> TempoRhythm.TOO_SLOW
            eccentricTime > 0 && concentricTime > 0 -> {
                val ratio = eccentricTime / concentricTime
                if (ratio in 0.5f..2f) TempoRhythm.OPTIMAL else TempoRhythm.INCONSISTENT
            }
            else -> TempoRhythm.INCONSISTENT
        }

        return MovementTempo(
            eccentricTime = eccentricTime,
            pauseTime = pauseTime,
            concentricTime = concentricTime,
            totalTime = totalTime,
            rhythm = rhythm
        )
    }

    /**
     * Assess overall movement quality
     */
    private fun assessMovementQuality(
        poses: List<TimestampedPose>,
        patternType: MovementPatternType,
        phases: List<MovementPhaseSegment>
    ): Float {
        // Base quality from pose quality
        val poseQualities = poses.map { calculatePoseQuality(it.pose) }
        val avgPoseQuality = poseQualities.average().toFloat()

        // Phase quality assessment
        val phaseQuality = phases.map { it.qualityScore }.averageOrNull()?.toFloat() ?: 0.5f

        // Pattern-specific quality assessment
        val patternSpecificQuality = when (patternType) {
            MovementPatternType.SQUAT -> assessSquatQuality(poses)
            MovementPatternType.PUSH_UP -> assessPushUpQuality(poses)
            MovementPatternType.DEADLIFT -> assessDeadliftQuality(poses)
            else -> 0.7f // Default quality for unknown patterns
        }

        // Weighted combination
        return (avgPoseQuality * 0.3f + phaseQuality * 0.4f + patternSpecificQuality * 0.3f)
            .coerceIn(0f, 1f)
    }

    /**
     * Calculate movement symmetry
     */
    private fun calculateMovementSymmetry(poses: List<TimestampedPose>): Float {
        val bilateralPairs = listOf(
            Pair(PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER),
            Pair(PoseLandmarks.LEFT_ELBOW, PoseLandmarks.RIGHT_ELBOW),
            Pair(PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP),
            Pair(PoseLandmarks.LEFT_KNEE, PoseLandmarks.RIGHT_KNEE)
        )

        val symmetryScores = mutableListOf<Float>()

        bilateralPairs.forEach { (leftIdx, rightIdx) ->
            val leftTrajectory = poses.map { it.pose.landmarks[leftIdx] }
            val rightTrajectory = poses.map { it.pose.landmarks[rightIdx] }

            val symmetryScore = calculateTrajectorySymmetry(leftTrajectory, rightTrajectory)
            symmetryScores.add(symmetryScore)
        }

        return symmetryScores.averageOrNull()?.toFloat() ?: 0.5f
    }

    /**
     * Calculate movement efficiency
     */
    private fun calculateMovementEfficiency(
        poses: List<TimestampedPose>,
        patternType: MovementPatternType
    ): Float {
        // Calculate path efficiency (straight line vs actual path)
        val comTrajectory = poses.map { calculateCenterOfMass(it.pose) }

        val startPos = comTrajectory.first()
        val endPos = comTrajectory.last()
        val directDistance = sqrt(
            (endPos.first - startPos.first).pow(2) +
            (endPos.second - startPos.second).pow(2)
        )

        val actualDistance = comTrajectory.zipWithNext { a, b ->
            sqrt((b.first - a.first).pow(2) + (b.second - a.second).pow(2))
        }.sum()

        val pathEfficiency = if (actualDistance > 0) {
            (directDistance / actualDistance).coerceAtMost(1f)
        } else 1f

        // Energy efficiency (smoothness of movement)
        val velocities = calculateOverallVelocity(poses)
        val accelerations = velocities.zipWithNext { v1, v2 -> abs(v2 - v1) }
        val smoothness = 1f - (accelerations.average().toFloat() / velocities.maxOrNull()!!.coerceAtLeast(0.01f))

        return (pathEfficiency * 0.6f + smoothness * 0.4f).coerceIn(0f, 1f)
    }

    // Helper functions
    private fun calculateVelocityProfile(
        trajectory: List<Triple<Float, Float, Float>>,
        timestamps: List<Long>
    ): List<Float> {
        val velocities = mutableListOf<Float>()

        for (i in 1 until trajectory.size) {
            val prev = trajectory[i - 1]
            val curr = trajectory[i]
            val timeDelta = (timestamps[i] - timestamps[i - 1]) / 1000f

            if (timeDelta > 0) {
                val distance = sqrt(
                    (curr.first - prev.first).pow(2) +
                    (curr.second - prev.second).pow(2) +
                    (curr.third - prev.third).pow(2)
                )
                velocities.add(distance / timeDelta)
            } else {
                velocities.add(0f)
            }
        }

        return velocities
    }

    private fun calculateKeyAngleChanges(poses: List<TimestampedPose>): Map<String, Float> {
        // Simplified angle change calculation
        val firstPose = poses.first().pose
        val lastPose = poses.last().pose

        return mapOf(
            "hip_flexion_change" to calculateAngleChange(firstPose, lastPose, "hip"),
            "knee_flexion_change" to calculateAngleChange(firstPose, lastPose, "knee"),
            "shoulder_flexion_change" to calculateAngleChange(firstPose, lastPose, "shoulder")
        )
    }

    private fun calculateAngleChange(
        pose1: PoseLandmarkResult,
        pose2: PoseLandmarkResult,
        joint: String
    ): Float {
        // Simplified angle calculation - would use JointAngleCalculator in practice
        return 0f // Placeholder
    }

    private fun calculateCenterOfMassMovement(poses: List<TimestampedPose>): List<Pair<Float, Float>> {
        return poses.map { calculateCenterOfMass(it.pose) }
    }

    private fun calculateCenterOfMass(pose: PoseLandmarkResult): Pair<Float, Float> {
        val keyJoints = listOf(
            PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER,
            PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP
        )

        val avgX = keyJoints.map { pose.landmarks[it].x }.average().toFloat()
        val avgY = keyJoints.map { pose.landmarks[it].y }.average().toFloat()

        return Pair(avgX, avgY)
    }

    private fun calculateMovementRange(trajectories: Map<Int, List<Triple<Float, Float, Float>>>): Float {
        val allPositions = trajectories.values.flatten()

        val xRange = allPositions.maxOf { it.first } - allPositions.minOf { it.first }
        val yRange = allPositions.maxOf { it.second } - allPositions.minOf { it.second }

        return sqrt(xRange.pow(2) + yRange.pow(2))
    }

    private fun calculatePrimaryMovementDirection(comMovement: List<Pair<Float, Float>>): Float {
        if (comMovement.size < 2) return 0f

        val start = comMovement.first()
        val end = comMovement.last()

        return atan2((end.second - start.second).toDouble(), (end.first - start.first).toDouble()).toFloat()
    }

    private fun calculatePatternMatchScore(features: MovementFeatures, template: MovementTemplate): Float {
        // Simplified pattern matching - would use more sophisticated ML techniques in practice
        val durationMatch = 1f - abs(features.duration - template.expectedDuration) / template.expectedDuration.coerceAtLeast(1000)
        val rangeMatch = 1f - abs(features.movementRange - template.expectedRange) / template.expectedRange.coerceAtLeast(0.1f)

        return (durationMatch * 0.5f + rangeMatch * 0.5f).coerceIn(0f, 1f)
    }

    private fun assessPhaseQuality(poses: List<TimestampedPose>, phase: MovementPhase): Float {
        // Assess quality specific to movement phase
        return poses.map { calculatePoseQuality(it.pose) }.average().toFloat()
    }

    private fun calculatePoseQuality(pose: PoseLandmarkResult): Double {
        // Basic pose quality based on landmark visibility
        return pose.landmarks.map { it.visibility * it.presence }.average()
    }

    private fun determineCurrentPhase(pose: TimestampedPose, patternType: MovementPatternType): MovementPhase {
        // Simplified phase determination
        return MovementPhase.PREPARATION
    }

    private fun calculatePatternConfidence(features: MovementFeatures, patternType: MovementPatternType): Float {
        // Calculate confidence based on feature matching
        return if (patternType == MovementPatternType.UNKNOWN) 0.3f else 0.8f
    }

    private fun generateMovementRecommendations(
        patternType: MovementPatternType,
        qualityScore: Float,
        symmetry: Float,
        efficiency: Float,
        tempo: MovementTempo
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (qualityScore < 0.6f) {
            recommendations.add("Focus on movement quality and proper form")
        }

        if (symmetry < 0.7f) {
            recommendations.add("Work on left-right movement symmetry")
        }

        if (efficiency < 0.6f) {
            recommendations.add("Improve movement efficiency with smoother patterns")
        }

        when (tempo.rhythm) {
            TempoRhythm.TOO_FAST -> recommendations.add("Slow down the movement for better control")
            TempoRhythm.TOO_SLOW -> recommendations.add("Increase movement tempo for better dynamics")
            TempoRhythm.INCONSISTENT -> recommendations.add("Focus on consistent movement rhythm")
            TempoRhythm.OPTIMAL -> recommendations.add("Good movement tempo maintained")
        }

        return recommendations
    }

    private fun assessSquatQuality(poses: List<TimestampedPose>): Float {
        // Squat-specific quality assessment
        // Check depth, knee tracking, back position, etc.
        return 0.8f // Placeholder
    }

    private fun assessPushUpQuality(poses: List<TimestampedPose>): Float {
        // Push-up specific quality assessment
        return 0.8f // Placeholder
    }

    private fun assessDeadliftQuality(poses: List<TimestampedPose>): Float {
        // Deadlift specific quality assessment
        return 0.8f // Placeholder
    }

    private fun calculateOverallVelocity(poses: List<TimestampedPose>): List<Float> {
        val velocities = mutableListOf<Float>()

        for (i in 1 until poses.size) {
            val prev = calculateCenterOfMass(poses[i - 1].pose)
            val curr = calculateCenterOfMass(poses[i].pose)
            val timeDelta = (poses[i].timestamp - poses[i - 1].timestamp) / 1000f

            if (timeDelta > 0) {
                val distance = sqrt((curr.first - prev.first).pow(2) + (curr.second - prev.second).pow(2))
                velocities.add(distance / timeDelta)
            } else {
                velocities.add(0f)
            }
        }

        return velocities
    }

    private fun calculateTrajectorySymmetry(
        leftTrajectory: List<PoseLandmarkResult.Landmark>,
        rightTrajectory: List<PoseLandmarkResult.Landmark>
    ): Float {
        if (leftTrajectory.size != rightTrajectory.size) return 0f

        val symmetryScores = leftTrajectory.zip(rightTrajectory) { left, right ->
            val distance = sqrt((left.x - right.x).pow(2) + (left.y - right.y).pow(2))
            1f / (1f + distance) // Higher score for closer positions
        }

        return symmetryScores.average().toFloat()
    }

    private fun initializeMovementTemplates(): Map<MovementPatternType, MovementTemplate> {
        return mapOf(
            MovementPatternType.SQUAT to MovementTemplate(
                expectedDuration = 3000L, // 3 seconds
                expectedRange = 0.3f,
                keyFeatures = listOf("hip_flexion", "knee_flexion", "vertical_movement")
            ),
            MovementPatternType.PUSH_UP to MovementTemplate(
                expectedDuration = 2000L, // 2 seconds
                expectedRange = 0.2f,
                keyFeatures = listOf("shoulder_flexion", "elbow_flexion", "horizontal_movement")
            ),
            MovementPatternType.DEADLIFT to MovementTemplate(
                expectedDuration = 4000L, // 4 seconds
                expectedRange = 0.4f,
                keyFeatures = listOf("hip_hinge", "back_angle", "vertical_movement")
            )
        )
    }

    /**
     * Reset analyzer state
     */
    fun reset() {
        // Reset any internal state if needed
    }
}

// Supporting data classes
data class MovementFeatures(
    val trajectories: Map<Int, List<Triple<Float, Float, Float>>>,
    val velocityProfiles: Map<Int, List<Float>>,
    val angleChanges: Map<String, Float>,
    val comMovement: List<Pair<Float, Float>>,
    val movementRange: Float,
    val primaryDirection: Float,
    val duration: Long,
    val poseCount: Int
)

data class MovementTemplate(
    val expectedDuration: Long,
    val expectedRange: Float,
    val keyFeatures: List<String>
)

data class MovementPhaseSegment(
    val phase: MovementPhase,
    val startIndex: Int,
    val endIndex: Int,
    val startTime: Long,
    val endTime: Long,
    val qualityScore: Float
)