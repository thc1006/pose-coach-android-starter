package com.posecoach.app.intelligence

import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.PoseLandmarks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlin.math.*

/**
 * Context-aware workout detection system that analyzes movement patterns
 * to identify workout phases, exercise types, and intensity levels
 */
class WorkoutContextAnalyzer {

    private val _workoutContext = MutableStateFlow(WorkoutContext())
    val workoutContext: StateFlow<WorkoutContext> = _workoutContext.asStateFlow()

    private val _exerciseDetection = MutableStateFlow<ExerciseDetection?>(null)
    val exerciseDetection: StateFlow<ExerciseDetection?> = _exerciseDetection.asStateFlow()

    // Movement analysis state
    private val movementHistory = mutableListOf<MovementFrame>()
    private val maxHistorySize = 120 // 4 seconds at 30 fps
    private var lastPoseTime = 0L
    private var sessionStartTime = 0L

    // Exercise pattern recognition
    private val exercisePatterns = ExercisePatternDatabase()
    private var currentSequence = mutableListOf<MovementSignature>()
    private val maxSequenceSize = 60 // 2 seconds of movement

    // Intensity and pacing analysis
    private val intensityCalculator = IntensityCalculator()
    private val pacingAnalyzer = PacingAnalyzer()

    data class WorkoutContext(
        val phase: WorkoutPhase = WorkoutPhase.UNKNOWN,
        val intensityLevel: IntensityLevel = IntensityLevel.LOW,
        val pacing: PacingInfo = PacingInfo(),
        val fatigue: FatigueLevel = FatigueLevel.FRESH,
        val sessionDuration: Long = 0L,
        val estimatedCaloriesBurned: Double = 0.0,
        val confidence: Float = 0.0f
    )

    data class ExerciseDetection(
        val exerciseType: ExerciseType,
        val confidence: Float,
        val repetitionCount: Int,
        val setCount: Int,
        val formQuality: FormQuality,
        val targetMuscleGroups: List<MuscleGroup>,
        val difficulty: DifficultyLevel,
        val recommendedModifications: List<String>
    )

    data class MovementFrame(
        val pose: PoseLandmarkResult,
        val timestamp: Long,
        val velocity: MovementVelocity,
        val bodyAngles: BodyAngles,
        val stabilityScore: Float
    )

    data class MovementSignature(
        val type: MovementType,
        val direction: MovementDirection,
        val speed: MovementSpeed,
        val bodyPart: BodyPart,
        val timestamp: Long
    )

    data class PacingInfo(
        val currentBPM: Int = 0,
        val targetBPM: Int = 0,
        val rhythm: RhythmPattern = RhythmPattern.STEADY,
        val tempo: TempoClassification = TempoClassification.MODERATE
    )

    enum class WorkoutPhase {
        UNKNOWN, WARMUP, MAIN_SET, COOL_DOWN, REST, TRANSITION
    }

    enum class IntensityLevel {
        LOW, MODERATE, HIGH, VERY_HIGH
    }

    enum class ExerciseType {
        SQUAT, PUSH_UP, PLANK, LUNGE, DEADLIFT, JUMPING_JACK,
        BURPEE, MOUNTAIN_CLIMBER, HIGH_KNEES, BICEP_CURL,
        SHOULDER_PRESS, TRICEP_DIP, CALF_RAISE, SIDE_PLANK,
        UNKNOWN
    }

    enum class FormQuality {
        EXCELLENT, GOOD, FAIR, POOR, DANGEROUS
    }

    enum class FatigueLevel {
        FRESH, SLIGHT, MODERATE, TIRED, EXHAUSTED
    }

    enum class MuscleGroup {
        LEGS, CHEST, BACK, SHOULDERS, ARMS, CORE, GLUTES, CALVES
    }

    enum class DifficultyLevel {
        BEGINNER, INTERMEDIATE, ADVANCED, EXPERT
    }

    enum class MovementType {
        STATIC, DYNAMIC, EXPLOSIVE, CONTROLLED
    }

    enum class MovementDirection {
        UP, DOWN, FORWARD, BACKWARD, LEFT, RIGHT, ROTATION
    }

    enum class MovementSpeed {
        VERY_SLOW, SLOW, MODERATE, FAST, VERY_FAST
    }

    enum class BodyPart {
        HEAD, TORSO, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, FULL_BODY
    }

    enum class RhythmPattern {
        STEADY, ACCELERATING, DECELERATING, IRREGULAR, INTERVAL
    }

    enum class TempoClassification {
        VERY_SLOW, SLOW, MODERATE, FAST, EXPLOSIVE
    }

    /**
     * Process new pose landmarks to update workout context
     */
    fun processPoseLandmarks(landmarks: PoseLandmarkResult) {
        val currentTime = System.currentTimeMillis()

        // Initialize session if first pose
        if (sessionStartTime == 0L) {
            sessionStartTime = currentTime
        }

        // Calculate movement metrics
        val movementFrame = analyzeMovement(landmarks, currentTime)
        addMovementFrame(movementFrame)

        // Update workout context
        updateWorkoutPhase(movementFrame)
        updateIntensityLevel(movementFrame)
        updateFatigueLevel(movementFrame)

        // Detect exercise patterns
        detectExerciseType(movementFrame)

        // Update pacing analysis
        updatePacing(movementFrame)

        lastPoseTime = currentTime
    }

    private fun analyzeMovement(pose: PoseLandmarkResult, timestamp: Long): MovementFrame {
        val velocity = calculateVelocity(pose)
        val bodyAngles = calculateBodyAngles(pose)
        val stability = calculateStabilityScore(pose)

        return MovementFrame(
            pose = pose,
            timestamp = timestamp,
            velocity = velocity,
            bodyAngles = bodyAngles,
            stabilityScore = stability
        )
    }

    private fun calculateVelocity(pose: PoseLandmarkResult): MovementVelocity {
        if (movementHistory.isEmpty()) {
            return MovementVelocity()
        }

        val lastFrame = movementHistory.last()
        val deltaTime = (pose.timestampMs - lastFrame.timestamp) / 1000.0

        if (deltaTime <= 0) return MovementVelocity()

        val landmarks = pose.landmarks
        val lastLandmarks = lastFrame.pose.landmarks

        // Calculate key point velocities
        val centerOfMassVelocity = calculateCenterOfMassVelocity(landmarks, lastLandmarks, deltaTime)
        val handVelocity = calculateHandVelocity(landmarks, lastLandmarks, deltaTime)
        val footVelocity = calculateFootVelocity(landmarks, lastLandmarks, deltaTime)

        return MovementVelocity(
            centerOfMass = centerOfMassVelocity,
            leftHand = handVelocity.first,
            rightHand = handVelocity.second,
            leftFoot = footVelocity.first,
            rightFoot = footVelocity.second
        )
    }

    private fun calculateBodyAngles(pose: PoseLandmarkResult): BodyAngles {
        val landmarks = pose.landmarks

        return BodyAngles(
            leftElbow = calculateAngle(
                landmarks[PoseLandmarks.LEFT_SHOULDER],
                landmarks[PoseLandmarks.LEFT_ELBOW],
                landmarks[PoseLandmarks.LEFT_WRIST]
            ),
            rightElbow = calculateAngle(
                landmarks[PoseLandmarks.RIGHT_SHOULDER],
                landmarks[PoseLandmarks.RIGHT_ELBOW],
                landmarks[PoseLandmarks.RIGHT_WRIST]
            ),
            leftKnee = calculateAngle(
                landmarks[PoseLandmarks.LEFT_HIP],
                landmarks[PoseLandmarks.LEFT_KNEE],
                landmarks[PoseLandmarks.LEFT_ANKLE]
            ),
            rightKnee = calculateAngle(
                landmarks[PoseLandmarks.RIGHT_HIP],
                landmarks[PoseLandmarks.RIGHT_KNEE],
                landmarks[PoseLandmarks.RIGHT_ANKLE]
            ),
            leftShoulder = calculateAngle(
                landmarks[PoseLandmarks.LEFT_ELBOW],
                landmarks[PoseLandmarks.LEFT_SHOULDER],
                landmarks[PoseLandmarks.LEFT_HIP]
            ),
            rightShoulder = calculateAngle(
                landmarks[PoseLandmarks.RIGHT_ELBOW],
                landmarks[PoseLandmarks.RIGHT_SHOULDER],
                landmarks[PoseLandmarks.RIGHT_HIP]
            ),
            spineAngle = calculateSpineAngle(landmarks),
            hipAngle = calculateHipAngle(landmarks)
        )
    }

    private fun calculateStabilityScore(pose: PoseLandmarkResult): Float {
        val landmarks = pose.landmarks

        // Calculate center of mass
        val centerOfMass = calculateCenterOfMass(landmarks)

        // Calculate base of support
        val baseOfSupport = calculateBaseOfSupport(landmarks)

        // Calculate balance score based on center of mass relative to base of support
        val stabilityFactor = if (baseOfSupport > 0) {
            1.0f - min(1.0f, abs(centerOfMass.first - 0.5f) / baseOfSupport)
        } else 0.5f

        // Factor in pose confidence
        val avgVisibility = landmarks.map { it.visibility }.average().toFloat()

        return (stabilityFactor * 0.7f + avgVisibility * 0.3f).coerceIn(0f, 1f)
    }

    private fun updateWorkoutPhase(frame: MovementFrame) {
        val currentContext = _workoutContext.value
        val sessionDuration = frame.timestamp - sessionStartTime

        val phase = when {
            sessionDuration < 3 * 60 * 1000 && isLowIntensityMovement(frame) -> WorkoutPhase.WARMUP
            sessionDuration > 5 * 60 * 1000 && isLowIntensityMovement(frame) -> WorkoutPhase.COOL_DOWN
            isRestingPosition(frame) -> WorkoutPhase.REST
            isTransitionMovement(frame) -> WorkoutPhase.TRANSITION
            else -> WorkoutPhase.MAIN_SET
        }

        if (phase != currentContext.phase) {
            Timber.i("Workout phase changed: ${currentContext.phase} -> $phase")
        }

        _workoutContext.value = currentContext.copy(
            phase = phase,
            sessionDuration = sessionDuration
        )
    }

    private fun updateIntensityLevel(frame: MovementFrame) {
        val intensity = intensityCalculator.calculateIntensity(frame, movementHistory)
        val currentContext = _workoutContext.value

        _workoutContext.value = currentContext.copy(
            intensityLevel = intensity,
            estimatedCaloriesBurned = calculateCaloriesBurned(intensity, currentContext.sessionDuration)
        )
    }

    private fun updateFatigueLevel(frame: MovementFrame) {
        val currentContext = _workoutContext.value
        val fatigue = calculateFatigueLevel(frame, currentContext.sessionDuration)

        _workoutContext.value = currentContext.copy(fatigue = fatigue)
    }

    private fun detectExerciseType(frame: MovementFrame) {
        // Add movement signature to current sequence
        val signature = extractMovementSignature(frame)
        currentSequence.add(signature)

        // Trim sequence to max size
        if (currentSequence.size > maxSequenceSize) {
            currentSequence.removeAt(0)
        }

        // Try to match exercise patterns
        val detectedExercise = exercisePatterns.matchPattern(currentSequence)

        if (detectedExercise != null) {
            _exerciseDetection.value = detectedExercise
            Timber.d("Exercise detected: ${detectedExercise.exerciseType} (confidence: ${detectedExercise.confidence})")
        }
    }

    private fun updatePacing(frame: MovementFrame) {
        val pacing = pacingAnalyzer.analyzePacing(frame, movementHistory)
        val currentContext = _workoutContext.value

        _workoutContext.value = currentContext.copy(pacing = pacing)
    }

    private fun addMovementFrame(frame: MovementFrame) {
        movementHistory.add(frame)

        // Trim history to max size
        if (movementHistory.size > maxHistorySize) {
            movementHistory.removeAt(0)
        }
    }

    // Helper data classes
    data class MovementVelocity(
        val centerOfMass: Float = 0f,
        val leftHand: Float = 0f,
        val rightHand: Float = 0f,
        val leftFoot: Float = 0f,
        val rightFoot: Float = 0f
    )

    data class BodyAngles(
        val leftElbow: Float,
        val rightElbow: Float,
        val leftKnee: Float,
        val rightKnee: Float,
        val leftShoulder: Float,
        val rightShoulder: Float,
        val spineAngle: Float,
        val hipAngle: Float
    )

    // Utility methods
    private fun calculateAngle(
        point1: PoseLandmarkResult.Landmark,
        point2: PoseLandmarkResult.Landmark,
        point3: PoseLandmarkResult.Landmark
    ): Float {
        val vector1 = Pair(point1.x - point2.x, point1.y - point2.y)
        val vector2 = Pair(point3.x - point2.x, point3.y - point2.y)

        val dotProduct = vector1.first * vector2.first + vector1.second * vector2.second
        val magnitude1 = sqrt(vector1.first * vector1.first + vector1.second * vector1.second)
        val magnitude2 = sqrt(vector2.first * vector2.first + vector2.second * vector2.second)

        return if (magnitude1 > 0 && magnitude2 > 0) {
            acos((dotProduct / (magnitude1 * magnitude2)).coerceIn(-1f, 1f)) * 180f / PI.toFloat()
        } else 0f
    }

    private fun calculateSpineAngle(landmarks: List<PoseLandmarkResult.Landmark>): Float {
        val shoulder = landmarks[PoseLandmarks.LEFT_SHOULDER]
        val hip = landmarks[PoseLandmarks.LEFT_HIP]

        return atan2(hip.y - shoulder.y, hip.x - shoulder.x) * 180f / PI.toFloat()
    }

    private fun calculateHipAngle(landmarks: List<PoseLandmarkResult.Landmark>): Float {
        val leftHip = landmarks[PoseLandmarks.LEFT_HIP]
        val rightHip = landmarks[PoseLandmarks.RIGHT_HIP]

        return atan2(rightHip.y - leftHip.y, rightHip.x - leftHip.x) * 180f / PI.toFloat()
    }

    private fun calculateCenterOfMass(landmarks: List<PoseLandmarkResult.Landmark>): Pair<Float, Float> {
        val torsoLandmarks = listOf(
            PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER,
            PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP
        )

        val x = torsoLandmarks.map { landmarks[it].x }.average().toFloat()
        val y = torsoLandmarks.map { landmarks[it].y }.average().toFloat()

        return Pair(x, y)
    }

    private fun calculateBaseOfSupport(landmarks: List<PoseLandmarkResult.Landmark>): Float {
        val leftFoot = landmarks[PoseLandmarks.LEFT_ANKLE]
        val rightFoot = landmarks[PoseLandmarks.RIGHT_ANKLE]

        return abs(rightFoot.x - leftFoot.x)
    }

    private fun calculateCenterOfMassVelocity(
        landmarks: List<PoseLandmarkResult.Landmark>,
        lastLandmarks: List<PoseLandmarkResult.Landmark>,
        deltaTime: Double
    ): Float {
        val currentCOM = calculateCenterOfMass(landmarks)
        val lastCOM = calculateCenterOfMass(lastLandmarks)

        val distance = sqrt(
            (currentCOM.first - lastCOM.first).pow(2) +
            (currentCOM.second - lastCOM.second).pow(2)
        )

        return (distance / deltaTime).toFloat()
    }

    private fun calculateHandVelocity(
        landmarks: List<PoseLandmarkResult.Landmark>,
        lastLandmarks: List<PoseLandmarkResult.Landmark>,
        deltaTime: Double
    ): Pair<Float, Float> {
        val leftHand = landmarks[PoseLandmarks.LEFT_WRIST]
        val rightHand = landmarks[PoseLandmarks.RIGHT_WRIST]
        val lastLeftHand = lastLandmarks[PoseLandmarks.LEFT_WRIST]
        val lastRightHand = lastLandmarks[PoseLandmarks.RIGHT_WRIST]

        val leftVelocity = sqrt(
            (leftHand.x - lastLeftHand.x).pow(2) + (leftHand.y - lastLeftHand.y).pow(2)
        ) / deltaTime

        val rightVelocity = sqrt(
            (rightHand.x - lastRightHand.x).pow(2) + (rightHand.y - lastRightHand.y).pow(2)
        ) / deltaTime

        return Pair(leftVelocity.toFloat(), rightVelocity.toFloat())
    }

    private fun calculateFootVelocity(
        landmarks: List<PoseLandmarkResult.Landmark>,
        lastLandmarks: List<PoseLandmarkResult.Landmark>,
        deltaTime: Double
    ): Pair<Float, Float> {
        val leftFoot = landmarks[PoseLandmarks.LEFT_ANKLE]
        val rightFoot = landmarks[PoseLandmarks.RIGHT_ANKLE]
        val lastLeftFoot = lastLandmarks[PoseLandmarks.LEFT_ANKLE]
        val lastRightFoot = lastLandmarks[PoseLandmarks.RIGHT_ANKLE]

        val leftVelocity = sqrt(
            (leftFoot.x - lastLeftFoot.x).pow(2) + (leftFoot.y - lastLeftFoot.y).pow(2)
        ) / deltaTime

        val rightVelocity = sqrt(
            (rightFoot.x - lastRightFoot.x).pow(2) + (rightFoot.y - lastRightFoot.y).pow(2)
        ) / deltaTime

        return Pair(leftVelocity.toFloat(), rightVelocity.toFloat())
    }

    private fun isLowIntensityMovement(frame: MovementFrame): Boolean {
        return frame.velocity.centerOfMass < 0.1f && frame.stabilityScore > 0.7f
    }

    private fun isRestingPosition(frame: MovementFrame): Boolean {
        return frame.velocity.centerOfMass < 0.05f && frame.stabilityScore > 0.8f
    }

    private fun isTransitionMovement(frame: MovementFrame): Boolean {
        return frame.velocity.centerOfMass > 0.2f && frame.stabilityScore < 0.6f
    }

    private fun extractMovementSignature(frame: MovementFrame): MovementSignature {
        val velocity = frame.velocity.centerOfMass
        val direction = determineMovementDirection(frame)
        val speed = when {
            velocity < 0.05f -> MovementSpeed.VERY_SLOW
            velocity < 0.1f -> MovementSpeed.SLOW
            velocity < 0.2f -> MovementSpeed.MODERATE
            velocity < 0.4f -> MovementSpeed.FAST
            else -> MovementSpeed.VERY_FAST
        }

        return MovementSignature(
            type = if (velocity < 0.05f) MovementType.STATIC else MovementType.DYNAMIC,
            direction = direction,
            speed = speed,
            bodyPart = BodyPart.FULL_BODY,
            timestamp = frame.timestamp
        )
    }

    private fun determineMovementDirection(frame: MovementFrame): MovementDirection {
        // Simple direction detection based on velocity components
        val velocity = frame.velocity

        return when {
            velocity.centerOfMass < 0.05f -> MovementDirection.UP // Static, default to up
            velocity.leftHand > velocity.rightHand -> MovementDirection.LEFT
            velocity.rightHand > velocity.leftHand -> MovementDirection.RIGHT
            else -> MovementDirection.UP
        }
    }

    private fun calculateFatigueLevel(frame: MovementFrame, sessionDuration: Long): FatigueLevel {
        val stabilityDecline = 1.0f - frame.stabilityScore
        val timeFactorHours = sessionDuration / (60.0 * 60.0 * 1000.0)

        return when {
            stabilityDecline > 0.5f || timeFactorHours > 2.0 -> FatigueLevel.EXHAUSTED
            stabilityDecline > 0.3f || timeFactorHours > 1.5 -> FatigueLevel.TIRED
            stabilityDecline > 0.2f || timeFactorHours > 1.0 -> FatigueLevel.MODERATE
            stabilityDecline > 0.1f || timeFactorHours > 0.5 -> FatigueLevel.SLIGHT
            else -> FatigueLevel.FRESH
        }
    }

    private fun calculateCaloriesBurned(intensity: IntensityLevel, sessionDuration: Long): Double {
        val durationHours = sessionDuration / (60.0 * 60.0 * 1000.0)
        val baseCaloriesPerHour = when (intensity) {
            IntensityLevel.LOW -> 200.0
            IntensityLevel.MODERATE -> 300.0
            IntensityLevel.HIGH -> 450.0
            IntensityLevel.VERY_HIGH -> 600.0
        }

        return baseCaloriesPerHour * durationHours
    }

    /**
     * Reset the workout context for a new session
     */
    fun resetSession() {
        movementHistory.clear()
        currentSequence.clear()
        sessionStartTime = 0L
        lastPoseTime = 0L

        _workoutContext.value = WorkoutContext()
        _exerciseDetection.value = null

        Timber.i("Workout context analyzer reset for new session")
    }

    /**
     * Get current workout insights for coaching
     */
    fun getWorkoutInsights(): WorkoutInsights {
        val context = _workoutContext.value
        val exercise = _exerciseDetection.value

        return WorkoutInsights(
            currentPhase = context.phase,
            intensity = context.intensityLevel,
            fatigue = context.fatigue,
            pacing = context.pacing,
            currentExercise = exercise?.exerciseType,
            formQuality = exercise?.formQuality,
            suggestions = generateContextualSuggestions(context, exercise)
        )
    }

    data class WorkoutInsights(
        val currentPhase: WorkoutPhase,
        val intensity: IntensityLevel,
        val fatigue: FatigueLevel,
        val pacing: PacingInfo,
        val currentExercise: ExerciseType?,
        val formQuality: FormQuality?,
        val suggestions: List<String>
    )

    private fun generateContextualSuggestions(
        context: WorkoutContext,
        exercise: ExerciseDetection?
    ): List<String> {
        val suggestions = mutableListOf<String>()

        // Phase-based suggestions
        when (context.phase) {
            WorkoutPhase.WARMUP -> suggestions.add("Focus on dynamic stretching and gradual movement")
            WorkoutPhase.COOL_DOWN -> suggestions.add("Incorporate static stretches and deep breathing")
            WorkoutPhase.MAIN_SET -> suggestions.add("Maintain proper form and controlled breathing")
            WorkoutPhase.REST -> suggestions.add("Take time to recover between sets")
            else -> {}
        }

        // Intensity-based suggestions
        when (context.intensityLevel) {
            IntensityLevel.VERY_HIGH -> suggestions.add("Consider reducing intensity to maintain form")
            IntensityLevel.HIGH -> suggestions.add("Great intensity! Focus on breathing and form")
            IntensityLevel.LOW -> suggestions.add("You can increase the intensity if you feel ready")
            else -> {}
        }

        // Fatigue-based suggestions
        when (context.fatigue) {
            FatigueLevel.TIRED -> suggestions.add("Consider taking a longer rest or reducing intensity")
            FatigueLevel.EXHAUSTED -> suggestions.add("Time for a cool-down and recovery")
            else -> {}
        }

        // Exercise-specific suggestions
        exercise?.let { ex ->
            when (ex.formQuality) {
                FormQuality.POOR -> suggestions.add("Focus on proper form rather than speed")
                FormQuality.DANGEROUS -> suggestions.add("Stop and reset your form to prevent injury")
                FormQuality.EXCELLENT -> suggestions.add("Excellent form! Keep it up!")
                else -> {}
            }
        }

        return suggestions
    }
}

/**
 * Helper classes for workout analysis
 */
private class IntensityCalculator {
    fun calculateIntensity(frame: WorkoutContextAnalyzer.MovementFrame, history: List<WorkoutContextAnalyzer.MovementFrame>): WorkoutContextAnalyzer.IntensityLevel {
        val recentFrames = history.takeLast(30) // Last second of movement
        if (recentFrames.isEmpty()) return WorkoutContextAnalyzer.IntensityLevel.LOW

        val avgVelocity = recentFrames.map { it.velocity.centerOfMass }.average()
        val avgStability = recentFrames.map { it.stabilityScore }.average()

        return when {
            avgVelocity > 0.4 -> WorkoutContextAnalyzer.IntensityLevel.VERY_HIGH
            avgVelocity > 0.25 -> WorkoutContextAnalyzer.IntensityLevel.HIGH
            avgVelocity > 0.15 -> WorkoutContextAnalyzer.IntensityLevel.MODERATE
            else -> WorkoutContextAnalyzer.IntensityLevel.LOW
        }
    }
}

private class PacingAnalyzer {
    fun analyzePacing(frame: WorkoutContextAnalyzer.MovementFrame, history: List<WorkoutContextAnalyzer.MovementFrame>): WorkoutContextAnalyzer.PacingInfo {
        // Simple pacing analysis - would be more sophisticated in real implementation
        val recentFrames = history.takeLast(60) // Last 2 seconds

        if (recentFrames.size < 10) {
            return WorkoutContextAnalyzer.PacingInfo()
        }

        val velocities = recentFrames.map { it.velocity.centerOfMass }
        val avgVelocity = velocities.average()

        val bpm = estimateBPMFromMovement(velocities)
        val rhythm = analyzeRhythm(velocities)
        val tempo = classifyTempo(avgVelocity)

        return WorkoutContextAnalyzer.PacingInfo(
            currentBPM = bpm,
            targetBPM = calculateTargetBPM(avgVelocity),
            rhythm = rhythm,
            tempo = tempo
        )
    }

    private fun estimateBPMFromMovement(velocities: List<Double>): Int {
        // Simplified BPM estimation based on movement frequency
        // Real implementation would use more sophisticated signal processing
        return (velocities.average() * 60).toInt().coerceIn(60, 180)
    }

    private fun analyzeRhythm(velocities: List<Double>): WorkoutContextAnalyzer.RhythmPattern {
        if (velocities.size < 5) return WorkoutContextAnalyzer.RhythmPattern.STEADY

        val variance = velocities.map { (it - velocities.average()).pow(2) }.average()

        return when {
            variance < 0.01 -> WorkoutContextAnalyzer.RhythmPattern.STEADY
            variance > 0.05 -> WorkoutContextAnalyzer.RhythmPattern.IRREGULAR
            else -> WorkoutContextAnalyzer.RhythmPattern.STEADY
        }
    }

    private fun classifyTempo(avgVelocity: Double): WorkoutContextAnalyzer.TempoClassification {
        return when {
            avgVelocity < 0.05 -> WorkoutContextAnalyzer.TempoClassification.VERY_SLOW
            avgVelocity < 0.15 -> WorkoutContextAnalyzer.TempoClassification.SLOW
            avgVelocity < 0.25 -> WorkoutContextAnalyzer.TempoClassification.MODERATE
            avgVelocity < 0.4 -> WorkoutContextAnalyzer.TempoClassification.FAST
            else -> WorkoutContextAnalyzer.TempoClassification.EXPLOSIVE
        }
    }

    private fun calculateTargetBPM(currentVelocity: Double): Int {
        // Simple target BPM calculation
        return when {
            currentVelocity < 0.1 -> 80
            currentVelocity < 0.2 -> 100
            currentVelocity < 0.3 -> 120
            else -> 140
        }
    }
}

private class ExercisePatternDatabase {
    fun matchPattern(sequence: List<WorkoutContextAnalyzer.MovementSignature>): WorkoutContextAnalyzer.ExerciseDetection? {
        // Simplified pattern matching - real implementation would use more sophisticated ML models
        if (sequence.size < 10) return null

        val recentMovements = sequence.takeLast(20)
        val staticCount = recentMovements.count { it.type == WorkoutContextAnalyzer.MovementType.STATIC }
        val dynamicCount = recentMovements.count { it.type == WorkoutContextAnalyzer.MovementType.DYNAMIC }

        return when {
            staticCount > dynamicCount * 2 -> WorkoutContextAnalyzer.ExerciseDetection(
                exerciseType = WorkoutContextAnalyzer.ExerciseType.PLANK,
                confidence = 0.7f,
                repetitionCount = 0,
                setCount = 1,
                formQuality = WorkoutContextAnalyzer.FormQuality.GOOD,
                targetMuscleGroups = listOf(WorkoutContextAnalyzer.MuscleGroup.CORE),
                difficulty = WorkoutContextAnalyzer.DifficultyLevel.INTERMEDIATE,
                recommendedModifications = listOf("Keep your core engaged", "Maintain straight line from head to heels")
            )
            dynamicCount > staticCount -> WorkoutContextAnalyzer.ExerciseDetection(
                exerciseType = WorkoutContextAnalyzer.ExerciseType.SQUAT,
                confidence = 0.6f,
                repetitionCount = dynamicCount / 4, // Rough estimation
                setCount = 1,
                formQuality = WorkoutContextAnalyzer.FormQuality.GOOD,
                targetMuscleGroups = listOf(WorkoutContextAnalyzer.MuscleGroup.LEGS, WorkoutContextAnalyzer.MuscleGroup.GLUTES),
                difficulty = WorkoutContextAnalyzer.DifficultyLevel.BEGINNER,
                recommendedModifications = listOf("Keep knees aligned with toes", "Lower until thighs are parallel to ground")
            )
            else -> null
        }
    }
}