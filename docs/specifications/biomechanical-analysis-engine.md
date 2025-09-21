# Biomechanical Analysis Engine Specification
# Sprint P2: Intelligent Pose Analysis System

## 🎯 Overview

The Biomechanical Analysis Engine transforms basic pose landmarks into actionable biomechanical insights, enabling intelligent coaching decisions. Building on Sprint P1's high-performance pose detection (<30ms), this engine adds sophisticated movement analysis while maintaining real-time performance requirements.

## 🏗 System Architecture

### Input/Output Flow

```kotlin
Input: MediaPipe Pose Landmarks (33 points, normalized coordinates)
├─ Enhanced with OneEuroFilter smoothing (Sprint P1)
├─ Confidence scores and visibility data
└─ Temporal sequence context (frame history)

Processing Pipeline:
┌─────────────────────────────────────────────────────────┐
│ Joint Angle Calculator → Movement Pattern Recognition   │
│ (<50ms per pose)       → (<100ms per sequence)         │
├─────────────────────────────────────────────────────────┤
│ Asymmetry Detection → Compensation Analysis            │
│ (2% precision)      → (Risk Assessment)                │
└─────────────────────────────────────────────────────────┘

Output: BiomechanicalAnalysisResult
├─ Joint angles with anatomical context
├─ Movement quality scores
├─ Asymmetry measurements
└─ Compensation patterns and recommendations
```

## 🦴 Joint Angle Calculator

### Anatomical Joint Definitions

```kotlin
data class AnatomicalJoint(
    val name: String,
    val landmarks: Triple<PoseLandmark, PoseLandmark, PoseLandmark>, // point1-vertex-point2
    val anatomicalRange: AngleRange,
    val significance: Float // 0.0-1.0 importance for movement quality
)

// Primary joints for analysis (ordered by coaching significance)
val PRIMARY_JOINTS = listOf(
    // Upper Body
    AnatomicalJoint("LEFT_SHOULDER",
        landmarks = Triple(LEFT_ELBOW, LEFT_SHOULDER, LEFT_HIP),
        anatomicalRange = AngleRange(0f, 180f),
        significance = 0.95f
    ),
    AnatomicalJoint("RIGHT_SHOULDER",
        landmarks = Triple(RIGHT_ELBOW, RIGHT_SHOULDER, RIGHT_HIP),
        anatomicalRange = AngleRange(0f, 180f),
        significance = 0.95f
    ),
    AnatomicalJoint("LEFT_ELBOW",
        landmarks = Triple(LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST),
        anatomicalRange = AngleRange(0f, 180f),
        significance = 0.90f
    ),
    AnatomicalJoint("RIGHT_ELBOW",
        landmarks = Triple(RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST),
        anatomicalRange = AngleRange(0f, 180f),
        significance = 0.90f
    ),

    // Core & Spine
    AnatomicalJoint("SPINE_UPPER",
        landmarks = Triple(LEFT_SHOULDER, NOSE, RIGHT_SHOULDER),
        anatomicalRange = AngleRange(160f, 180f), // near-straight for good posture
        significance = 1.0f
    ),
    AnatomicalJoint("SPINE_LOWER",
        landmarks = Triple(LEFT_HIP, MID_HIP, RIGHT_HIP),
        anatomicalRange = AngleRange(170f, 180f),
        significance = 0.98f
    ),

    // Lower Body
    AnatomicalJoint("LEFT_HIP",
        landmarks = Triple(LEFT_KNEE, LEFT_HIP, LEFT_SHOULDER),
        anatomicalRange = AngleRange(0f, 180f),
        significance = 0.95f
    ),
    AnatomicalJoint("RIGHT_HIP",
        landmarks = Triple(RIGHT_KNEE, RIGHT_HIP, RIGHT_SHOULDER),
        anatomicalRange = AngleRange(0f, 180f),
        significance = 0.95f
    ),
    AnatomicalJoint("LEFT_KNEE",
        landmarks = Triple(LEFT_HIP, LEFT_KNEE, LEFT_ANKLE),
        anatomicalRange = AngleRange(0f, 180f),
        significance = 0.92f
    ),
    AnatomicalJoint("RIGHT_KNEE",
        landmarks = Triple(RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE),
        anatomicalRange = AngleRange(0f, 180f),
        significance = 0.92f
    )
)
```

### Joint Angle Calculation Algorithm

```kotlin
class JointAngleCalculator {

    /**
     * Calculates 3D angle between three points with anatomical context
     * Accuracy target: 0.5 degrees for key joints
     * Performance target: <5ms for all joints
     */
    fun calculateJointAngle(
        point1: PoseLandmark,
        vertex: PoseLandmark,
        point2: PoseLandmark
    ): JointAngleResult {

        // 1. Convert normalized coordinates to 3D vectors
        val vector1 = Vector3D(
            x = point1.x - vertex.x,
            y = point1.y - vertex.y,
            z = point1.z - vertex.z
        )

        val vector2 = Vector3D(
            x = point2.x - vertex.x,
            y = point2.y - vertex.y,
            z = point2.z - vertex.z
        )

        // 2. Calculate angle using dot product formula
        val dotProduct = vector1.dot(vector2)
        val magnitude1 = vector1.magnitude()
        val magnitude2 = vector2.magnitude()

        // Handle edge cases (zero magnitude vectors)
        if (magnitude1 < EPSILON || magnitude2 < EPSILON) {
            return JointAngleResult.invalid()
        }

        val cosTheta = dotProduct / (magnitude1 * magnitude2)
        val clampedCosTheta = cosTheta.coerceIn(-1.0f, 1.0f)
        val angleRadians = acos(clampedCosTheta)
        val angleDegrees = Math.toDegrees(angleRadians.toDouble()).toFloat()

        // 3. Apply confidence-based filtering
        val confidence = calculateAngleConfidence(point1, vertex, point2)

        return JointAngleResult(
            angle = angleDegrees,
            confidence = confidence,
            isValid = confidence > MIN_ANGLE_CONFIDENCE,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Calculates confidence score based on landmark visibility and geometric validity
     */
    private fun calculateAngleConfidence(
        point1: PoseLandmark,
        vertex: PoseLandmark,
        point2: PoseLandmark
    ): Float {
        // Base confidence from landmark visibility
        val visibilityScore = (point1.visibility + vertex.visibility + point2.visibility) / 3f

        // Geometric validity (avoid near-collinear points)
        val distance1 = point1.distanceTo(vertex)
        val distance2 = point2.distanceTo(vertex)
        val geometryScore = if (distance1 > MIN_SEGMENT_LENGTH && distance2 > MIN_SEGMENT_LENGTH) 1.0f else 0.5f

        // Z-depth consistency (for 3D stability)
        val depthVariance = calculateDepthVariance(point1, vertex, point2)
        val depthScore = (1.0f - depthVariance.coerceIn(0f, 1f))

        return (visibilityScore * 0.5f + geometryScore * 0.3f + depthScore * 0.2f)
    }

    companion object {
        private const val EPSILON = 1e-6f
        private const val MIN_ANGLE_CONFIDENCE = 0.7f
        private const val MIN_SEGMENT_LENGTH = 0.01f // normalized coordinates
    }
}
```

## 📊 Movement Pattern Recognition

### Exercise Classification System

```kotlin
enum class ExerciseType(
    val displayName: String,
    val keyJoints: List<String>,
    val movementPlanes: List<MovementPlane>,
    val difficultyLevel: Int
) {
    // Upper Body
    PUSH_UP("Push-up",
        keyJoints = listOf("LEFT_ELBOW", "RIGHT_ELBOW", "SPINE_UPPER"),
        movementPlanes = listOf(MovementPlane.SAGITTAL),
        difficultyLevel = 2
    ),
    PLANK("Plank",
        keyJoints = listOf("SPINE_UPPER", "SPINE_LOWER", "LEFT_HIP", "RIGHT_HIP"),
        movementPlanes = listOf(MovementPlane.ALL),
        difficultyLevel = 3
    ),
    SHOULDER_PRESS("Shoulder Press",
        keyJoints = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_ELBOW", "RIGHT_ELBOW"),
        movementPlanes = listOf(MovementPlane.FRONTAL),
        difficultyLevel = 2
    ),

    // Lower Body
    SQUAT("Squat",
        keyJoints = listOf("LEFT_HIP", "RIGHT_HIP", "LEFT_KNEE", "RIGHT_KNEE"),
        movementPlanes = listOf(MovementPlane.SAGITTAL),
        difficultyLevel = 2
    ),
    LUNGE("Lunge",
        keyJoints = listOf("LEFT_HIP", "RIGHT_HIP", "LEFT_KNEE", "RIGHT_KNEE"),
        movementPlanes = listOf(MovementPlane.SAGITTAL),
        difficultyLevel = 3
    ),
    DEADLIFT("Deadlift",
        keyJoints = listOf("LEFT_HIP", "RIGHT_HIP", "SPINE_LOWER"),
        movementPlanes = listOf(MovementPlane.SAGITTAL),
        difficultyLevel = 4
    ),

    // Full Body
    BURPEE("Burpee",
        keyJoints = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_HIP", "RIGHT_HIP", "LEFT_KNEE", "RIGHT_KNEE"),
        movementPlanes = listOf(MovementPlane.ALL),
        difficultyLevel = 5
    ),
    MOUNTAIN_CLIMBER("Mountain Climber",
        keyJoints = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_HIP", "RIGHT_HIP"),
        movementPlanes = listOf(MovementPlane.SAGITTAL, MovementPlane.TRANSVERSE),
        difficultyLevel = 4
    );

    companion object {
        fun fromKeyJointPattern(activeJoints: List<String>): ExerciseType? {
            return values().find { exercise ->
                exercise.keyJoints.any { it in activeJoints }
            }
        }
    }
}

enum class MovementPlane {
    SAGITTAL,    // Forward/backward movement
    FRONTAL,     // Side-to-side movement
    TRANSVERSE,  // Rotational movement
    ALL          // Multi-planar movement
}
```

### Movement Phase Detection

```kotlin
enum class MovementPhase(
    val displayName: String,
    val description: String,
    val coachingFocus: List<String>
) {
    SETUP(
        displayName = "Setup",
        description = "Initial position and preparation",
        coachingFocus = listOf("Proper alignment", "Core engagement", "Breathing preparation")
    ),
    ECCENTRIC(
        displayName = "Eccentric",
        description = "Lowering or lengthening phase",
        coachingFocus = listOf("Controlled movement", "Time under tension", "Range of motion")
    ),
    ISOMETRIC(
        displayName = "Isometric",
        description = "Hold or pause phase",
        coachingFocus = listOf("Muscle engagement", "Stability", "Breathing control")
    ),
    CONCENTRIC(
        displayName = "Concentric",
        description = "Lifting or shortening phase",
        coachingFocus = listOf("Power generation", "Proper form", "Speed control")
    ),
    RECOVERY(
        displayName = "Recovery",
        description = "Return to starting position",
        coachingFocus = listOf("Reset alignment", "Prepare for next rep", "Breathing recovery")
    )
}

class MovementPhaseDetector {

    private val phaseHistory = CircularBuffer<MovementPhase>(capacity = 10)
    private val angleVelocityTracker = mutableMapOf<String, CircularBuffer<Float>>()

    /**
     * Detects current movement phase based on joint angle velocities and patterns
     * Performance target: <20ms per frame
     */
    fun detectMovementPhase(
        currentAngles: Map<String, Float>,
        exerciseType: ExerciseType
    ): MovementPhaseResult {

        // 1. Calculate joint angle velocities
        val velocities = calculateJointVelocities(currentAngles, exerciseType.keyJoints)

        // 2. Analyze velocity patterns for phase classification
        val phaseIndicators = analyzeVelocityPatterns(velocities, exerciseType)

        // 3. Apply temporal smoothing to prevent phase flickering
        val predictedPhase = classifyPhase(phaseIndicators, exerciseType)
        val smoothedPhase = applySmoothingFilter(predictedPhase)

        // 4. Generate phase-specific coaching cues
        val coachingCues = generateCoachingCues(smoothedPhase, exerciseType, velocities)

        return MovementPhaseResult(
            phase = smoothedPhase,
            confidence = phaseIndicators.confidence,
            coachingCues = coachingCues,
            transitionProbability = calculateTransitionProbability(smoothedPhase)
        )
    }

    private fun calculateJointVelocities(
        currentAngles: Map<String, Float>,
        keyJoints: List<String>
    ): Map<String, Float> {
        val velocities = mutableMapOf<String, Float>()

        keyJoints.forEach { joint ->
            val currentAngle = currentAngles[joint] ?: return@forEach

            // Get or create velocity buffer for this joint
            val velocityBuffer = angleVelocityTracker.getOrPut(joint) {
                CircularBuffer(capacity = 5)
            }

            // Calculate velocity if we have previous data
            if (velocityBuffer.isNotEmpty()) {
                val previousAngle = velocityBuffer.last()
                val velocity = (currentAngle - previousAngle) / FRAME_TIME_MS
                velocities[joint] = velocity
            }

            velocityBuffer.add(currentAngle)
        }

        return velocities
    }

    companion object {
        private const val FRAME_TIME_MS = 33.33f // ~30fps
    }
}
```

## ⚖️ Asymmetry Detection System

### Bilateral Comparison Analysis

```kotlin
class AsymmetryDetector {

    private val asymmetryHistory = CircularBuffer<AsymmetryResult>(capacity = 30) // 1 second at 30fps

    /**
     * Detects left/right asymmetries in joint angles and movement patterns
     * Precision target: 2% accuracy for identifying imbalances
     */
    fun detectAsymmetries(
        jointAngles: Map<String, Float>
    ): AsymmetryAnalysisResult {

        val asymmetries = mutableListOf<AsymmetryResult>()

        // 1. Bilateral joint comparisons
        BILATERAL_JOINT_PAIRS.forEach { (leftJoint, rightJoint) ->
            val leftAngle = jointAngles[leftJoint]
            val rightAngle = jointAngles[rightJoint]

            if (leftAngle != null && rightAngle != null) {
                val asymmetry = calculateBilateralAsymmetry(
                    leftAngle = leftAngle,
                    rightAngle = rightAngle,
                    jointPair = leftJoint to rightJoint
                )

                if (asymmetry.isSignificant) {
                    asymmetries.add(asymmetry)
                }
            }
        }

        // 2. Postural asymmetries
        val posturalAsymmetries = detectPosturalAsymmetries(jointAngles)
        asymmetries.addAll(posturalAsymmetries)

        // 3. Movement pattern asymmetries (requires temporal data)
        val movementAsymmetries = detectMovementAsymmetries(jointAngles)
        asymmetries.addAll(movementAsymmetries)

        // 4. Generate corrective recommendations
        val corrections = generateCorrectiveRecommendations(asymmetries)

        return AsymmetryAnalysisResult(
            detectedAsymmetries = asymmetries,
            overallAsymmetryScore = calculateOverallAsymmetryScore(asymmetries),
            correctiveRecommendations = corrections,
            riskAssessment = assessInjuryRisk(asymmetries)
        )
    }

    private fun calculateBilateralAsymmetry(
        leftAngle: Float,
        rightAngle: Float,
        jointPair: Pair<String, String>
    ): AsymmetryResult {

        val angleDifference = abs(leftAngle - rightAngle)
        val averageAngle = (leftAngle + rightAngle) / 2f
        val percentageDifference = (angleDifference / averageAngle) * 100f

        // Determine significance based on joint type and movement context
        val significanceThreshold = ASYMMETRY_THRESHOLDS[jointPair.first] ?: DEFAULT_ASYMMETRY_THRESHOLD
        val isSignificant = percentageDifference > significanceThreshold

        return AsymmetryResult(
            jointPair = jointPair,
            leftValue = leftAngle,
            rightValue = rightAngle,
            absoluteDifference = angleDifference,
            percentageDifference = percentageDifference,
            isSignificant = isSignificant,
            severity = classifyAsymmetrySeverity(percentageDifference),
            recommendedCorrection = generateCorrection(jointPair, percentageDifference)
        )
    }

    companion object {
        private val BILATERAL_JOINT_PAIRS = mapOf(
            "LEFT_SHOULDER" to "RIGHT_SHOULDER",
            "LEFT_ELBOW" to "RIGHT_ELBOW",
            "LEFT_HIP" to "RIGHT_HIP",
            "LEFT_KNEE" to "RIGHT_KNEE"
        )

        private val ASYMMETRY_THRESHOLDS = mapOf(
            "LEFT_SHOULDER" to 5.0f,  // 5% difference threshold
            "LEFT_ELBOW" to 7.0f,     // 7% difference threshold
            "LEFT_HIP" to 3.0f,       // 3% difference threshold (more critical)
            "LEFT_KNEE" to 4.0f       // 4% difference threshold
        )

        private const val DEFAULT_ASYMMETRY_THRESHOLD = 5.0f
    }
}

data class AsymmetryResult(
    val jointPair: Pair<String, String>,
    val leftValue: Float,
    val rightValue: Float,
    val absoluteDifference: Float,
    val percentageDifference: Float,
    val isSignificant: Boolean,
    val severity: AsymmetrySeverity,
    val recommendedCorrection: String
)

enum class AsymmetrySeverity(val threshold: Float, val description: String) {
    MINIMAL(5.0f, "Minor asymmetry - monitor over time"),
    MODERATE(10.0f, "Noticeable asymmetry - consider corrective exercises"),
    SIGNIFICANT(20.0f, "Significant asymmetry - address with targeted intervention"),
    SEVERE(30.0f, "Severe asymmetry - consult with movement specialist")
}
```

## 🎯 Movement Quality Scoring

### Comprehensive Quality Assessment

```kotlin
class MovementQualityScorer {

    /**
     * Generates comprehensive movement quality score based on biomechanical analysis
     * Score range: 0.0 (poor) to 1.0 (excellent)
     * Performance target: <30ms per assessment
     */
    fun scoreMovementQuality(
        jointAngles: Map<String, Float>,
        exerciseType: ExerciseType,
        movementPhase: MovementPhase,
        asymmetries: List<AsymmetryResult>
    ): MovementQualityScore {

        // 1. Joint angle quality (40% of total score)
        val angleQuality = scoreJointAngleQuality(jointAngles, exerciseType)

        // 2. Movement symmetry (25% of total score)
        val symmetryQuality = scoreSymmetryQuality(asymmetries)

        // 3. Range of motion (20% of total score)
        val romQuality = scoreRangeOfMotion(jointAngles, exerciseType)

        // 4. Movement phase appropriateness (15% of total score)
        val phaseQuality = scorePhaseAppropriateness(jointAngles, movementPhase, exerciseType)

        // 5. Calculate weighted overall score
        val overallScore = (
            angleQuality * 0.40f +
            symmetryQuality * 0.25f +
            romQuality * 0.20f +
            phaseQuality * 0.15f
        ).coerceIn(0.0f, 1.0f)

        return MovementQualityScore(
            overallScore = overallScore,
            angleQuality = angleQuality,
            symmetryQuality = symmetryQuality,
            rangeOfMotionQuality = romQuality,
            phaseQuality = phaseQuality,
            detailedFeedback = generateDetailedFeedback(
                overallScore, angleQuality, symmetryQuality, romQuality, phaseQuality
            )
        )
    }

    private fun scoreJointAngleQuality(
        jointAngles: Map<String, Float>,
        exerciseType: ExerciseType
    ): Float {
        val idealAngles = EXERCISE_IDEAL_ANGLES[exerciseType] ?: return 0.5f
        var totalScore = 0.0f
        var jointCount = 0

        exerciseType.keyJoints.forEach { joint ->
            val currentAngle = jointAngles[joint]
            val idealRange = idealAngles[joint]

            if (currentAngle != null && idealRange != null) {
                val score = calculateAngleScore(currentAngle, idealRange)
                totalScore += score
                jointCount++
            }
        }

        return if (jointCount > 0) totalScore / jointCount else 0.5f
    }

    private fun calculateAngleScore(currentAngle: Float, idealRange: AngleRange): Float {
        return when {
            currentAngle in idealRange.optimal -> 1.0f
            currentAngle in idealRange.acceptable -> 0.8f
            currentAngle in idealRange.suboptimal -> 0.6f
            else -> 0.4f
        }
    }

    companion object {
        private val EXERCISE_IDEAL_ANGLES = mapOf(
            ExerciseType.SQUAT to mapOf(
                "LEFT_HIP" to AngleRange(
                    optimal = 80f..100f,
                    acceptable = 70f..110f,
                    suboptimal = 60f..120f
                ),
                "LEFT_KNEE" to AngleRange(
                    optimal = 90f..110f,
                    acceptable = 80f..120f,
                    suboptimal = 70f..130f
                )
            ),
            ExerciseType.PUSH_UP to mapOf(
                "LEFT_ELBOW" to AngleRange(
                    optimal = 90f..110f,
                    acceptable = 80f..120f,
                    suboptimal = 70f..130f
                ),
                "SPINE_UPPER" to AngleRange(
                    optimal = 170f..180f,
                    acceptable = 160f..180f,
                    suboptimal = 150f..180f
                )
            )
            // Add more exercises as needed
        )
    }
}

data class AngleRange(
    val optimal: ClosedFloatingPointRange<Float>,
    val acceptable: ClosedFloatingPointRange<Float>,
    val suboptimal: ClosedFloatingPointRange<Float>
)

data class MovementQualityScore(
    val overallScore: Float,
    val angleQuality: Float,
    val symmetryQuality: Float,
    val rangeOfMotionQuality: Float,
    val phaseQuality: Float,
    val detailedFeedback: List<String>
)
```

## 📈 Performance Optimization

### Real-Time Processing Optimizations

```kotlin
class BiomechanicalAnalysisEngine {

    private val jointAngleCalculator = JointAngleCalculator()
    private val movementPatternRecognizer = MovementPatternRecognizer()
    private val asymmetryDetector = AsymmetryDetector()
    private val qualityScorer = MovementQualityScorer()

    // Performance optimization: Pre-allocate objects to avoid GC pressure
    private val angleResultsPool = ObjectPool<Map<String, Float>>(capacity = 10)
    private val analysisResultsPool = ObjectPool<BiomechanicalAnalysisResult>(capacity = 5)

    /**
     * Main analysis entry point - processes pose landmarks into biomechanical insights
     * Performance target: <50ms total processing time
     */
    suspend fun analyzePose(
        landmarks: List<PoseLandmark>,
        timestamp: Long
    ): BiomechanicalAnalysisResult = withContext(Dispatchers.Default) {

        val startTime = System.nanoTime()

        try {
            // 1. Calculate joint angles (target: <15ms)
            val jointAngles = async {
                calculateAllJointAngles(landmarks)
            }

            // 2. Detect movement patterns (target: <20ms)
            val movementPattern = async {
                movementPatternRecognizer.recognizePattern(landmarks, timestamp)
            }

            // 3. Analyze asymmetries (target: <10ms)
            val asymmetries = async {
                asymmetryDetector.detectAsymmetries(jointAngles.await())
            }

            // 4. Score movement quality (target: <5ms)
            val qualityScore = async {
                val angles = jointAngles.await()
                val pattern = movementPattern.await()
                val asymmetryResults = asymmetries.await()

                qualityScorer.scoreMovementQuality(
                    jointAngles = angles,
                    exerciseType = pattern.exerciseType,
                    movementPhase = pattern.currentPhase,
                    asymmetries = asymmetryResults.detectedAsymmetries
                )
            }

            // 5. Combine results
            val result = BiomechanicalAnalysisResult(
                jointAngles = jointAngles.await(),
                movementPattern = movementPattern.await(),
                asymmetryAnalysis = asymmetries.await(),
                qualityScore = qualityScore.await(),
                processingTimeMs = (System.nanoTime() - startTime) / 1_000_000,
                timestamp = timestamp
            )

            // Performance monitoring
            logPerformanceMetrics(result.processingTimeMs)

            result

        } catch (e: Exception) {
            // Graceful degradation - return minimal result
            BiomechanicalAnalysisResult.degraded(e, timestamp)
        }
    }

    private fun calculateAllJointAngles(landmarks: List<PoseLandmark>): Map<String, Float> {
        val results = angleResultsPool.acquire() ?: mutableMapOf()
        results.clear()

        PRIMARY_JOINTS.forEach { joint ->
            try {
                val landmark1 = landmarks[joint.landmarks.first.ordinal]
                val vertex = landmarks[joint.landmarks.second.ordinal]
                val landmark2 = landmarks[joint.landmarks.third.ordinal]

                val angleResult = jointAngleCalculator.calculateJointAngle(
                    landmark1, vertex, landmark2
                )

                if (angleResult.isValid) {
                    results[joint.name] = angleResult.angle
                }
            } catch (e: Exception) {
                // Skip invalid joints, continue processing
                Timber.w(e, "Failed to calculate angle for joint: ${joint.name}")
            }
        }

        return results
    }

    private fun logPerformanceMetrics(processingTimeMs: Long) {
        // Track performance metrics for optimization
        if (processingTimeMs > TARGET_PROCESSING_TIME_MS) {
            Timber.w("Biomechanical analysis exceeded target time: ${processingTimeMs}ms")
        }

        // Send metrics to observability system
        MetricsCollector.recordBiomechanicalAnalysisTime(processingTimeMs)
    }

    companion object {
        private const val TARGET_PROCESSING_TIME_MS = 50L
    }
}
```

## 🎯 Integration with Coaching System

### Coaching Recommendations Engine

```kotlin
class BiomechanicalCoachingRecommendations {

    /**
     * Converts biomechanical analysis into actionable coaching recommendations
     */
    fun generateRecommendations(
        analysisResult: BiomechanicalAnalysisResult
    ): List<CoachingRecommendation> {

        val recommendations = mutableListOf<CoachingRecommendation>()

        // 1. Joint angle recommendations
        analysisResult.jointAngles.forEach { (joint, angle) ->
            val recommendation = generateJointAngleRecommendation(joint, angle, analysisResult.movementPattern)
            if (recommendation != null) {
                recommendations.add(recommendation)
            }
        }

        // 2. Asymmetry corrections
        analysisResult.asymmetryAnalysis.detectedAsymmetries.forEach { asymmetry ->
            if (asymmetry.isSignificant) {
                recommendations.add(
                    CoachingRecommendation(
                        type = RecommendationType.ASYMMETRY_CORRECTION,
                        priority = asymmetry.severity.toPriority(),
                        title = "Address ${asymmetry.jointPair.first}/${asymmetry.jointPair.second} Imbalance",
                        instruction = asymmetry.recommendedCorrection,
                        targetLandmarks = listOf(asymmetry.jointPair.first, asymmetry.jointPair.second),
                        expectedImprovement = "Improved movement symmetry and injury prevention"
                    )
                )
            }
        }

        // 3. Movement quality improvements
        if (analysisResult.qualityScore.overallScore < 0.7f) {
            val qualityRecommendation = generateQualityImprovementRecommendation(analysisResult.qualityScore)
            recommendations.add(qualityRecommendation)
        }

        // 4. Sort by priority and limit to top 3 recommendations
        return recommendations
            .sortedByDescending { it.priority.value }
            .take(3)
    }

    private fun generateJointAngleRecommendation(
        joint: String,
        currentAngle: Float,
        movementPattern: MovementPatternResult
    ): CoachingRecommendation? {

        val idealRange = EXERCISE_IDEAL_ANGLES[movementPattern.exerciseType]?.get(joint)
        if (idealRange == null || currentAngle in idealRange.acceptable) {
            return null // Angle is acceptable
        }

        val (instruction, expectedAngle) = when {
            currentAngle < idealRange.acceptable.start -> {
                generateIncreaseAngleInstruction(joint) to idealRange.optimal.start
            }
            currentAngle > idealRange.acceptable.endInclusive -> {
                generateDecreaseAngleInstruction(joint) to idealRange.optimal.endInclusive
            }
            else -> return null
        }

        return CoachingRecommendation(
            type = RecommendationType.JOINT_ANGLE_CORRECTION,
            priority = RecommendationPriority.MEDIUM,
            title = "Adjust ${joint.replace("_", " ").titleCase()} Position",
            instruction = instruction,
            targetLandmarks = getJointLandmarks(joint),
            expectedImprovement = "Improved movement efficiency and reduced injury risk"
        )
    }
}

data class CoachingRecommendation(
    val type: RecommendationType,
    val priority: RecommendationPriority,
    val title: String,
    val instruction: String,
    val targetLandmarks: List<String>,
    val expectedImprovement: String
)

enum class RecommendationType {
    JOINT_ANGLE_CORRECTION,
    ASYMMETRY_CORRECTION,
    RANGE_OF_MOTION_IMPROVEMENT,
    MOVEMENT_QUALITY_ENHANCEMENT,
    POSTURAL_ALIGNMENT
}

enum class RecommendationPriority(val value: Int) {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4)
}
```

This biomechanical analysis engine provides the foundation for intelligent coaching by transforming basic pose data into actionable insights about movement quality, asymmetries, and opportunities for improvement.