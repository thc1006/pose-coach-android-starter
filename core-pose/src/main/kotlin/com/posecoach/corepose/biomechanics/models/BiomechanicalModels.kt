package com.posecoach.corepose.biomechanics.models

import com.posecoach.corepose.models.PoseLandmarkResult

/**
 * Data models for advanced biomechanical analysis
 */

/**
 * Comprehensive biomechanical analysis result
 */
data class BiomechanicalAnalysisResult(
    val timestamp: Long,
    val processingTimeMs: Long,
    val jointAngles: JointAngleMap,
    val asymmetryAnalysis: AsymmetryAnalysis,
    val posturalAnalysis: PosturalAnalysis,
    val movementPattern: MovementPattern?,
    val kineticChainAnalysis: KineticChainAnalysis,
    val movementQuality: MovementQualityScore,
    val fatigueIndicators: FatigueIndicators,
    val compensationPatterns: CompensationPatterns,
    val confidenceScore: Float
)

/**
 * Joint angle information with biomechanical context
 */
data class JointAngle(
    val jointName: String,
    val angle: Float,
    val rangeOfMotion: RangeOfMotion,
    val quality: AngleQuality,
    val stability: Float,
    val isWithinNormalRange: Boolean,
    val biomechanicalRecommendation: String
)

/**
 * Map of joint names to their angle information
 */
typealias JointAngleMap = Map<String, JointAngle>

/**
 * Range of motion definition for joints
 */
data class RangeOfMotion(
    val minAngle: Float,
    val maxAngle: Float,
    val optimalAngle: Float
) {
    val range: Float get() = maxAngle - minAngle

    fun getPositionInRange(currentAngle: Float): Float {
        return if (range > 0) {
            ((currentAngle - minAngle) / range).coerceIn(0f, 1f)
        } else 0f
    }
}

/**
 * Quality assessment for angle measurements
 */
enum class AngleQuality {
    HIGH,    // >80% confidence, stable measurement
    MEDIUM,  // 60-80% confidence, some jitter
    LOW      // <60% confidence, unstable
}

/**
 * Asymmetry analysis between left and right sides
 */
data class AsymmetryAnalysis(
    val leftRightAsymmetry: Float,           // -1 to 1, negative means left side weaker
    val anteriorPosteriorAsymmetry: Float,   // -1 to 1, positive means forward lean
    val mediolateralAsymmetry: Float,        // -1 to 1, positive means right lean
    val rotationalAsymmetry: Float,          // 0 to 1, rotational imbalance
    val overallAsymmetryScore: Float,        // 0 to 1, overall asymmetry magnitude
    val asymmetryTrends: List<AsymmetryTrend>,
    val recommendations: List<String>
)

/**
 * Asymmetry trend over time
 */
data class AsymmetryTrend(
    val type: AsymmetryType,
    val severity: Float,
    val duration: Long,
    val isImproving: Boolean
)

/**
 * Types of asymmetry patterns
 */
enum class AsymmetryType {
    LEFT_RIGHT_IMBALANCE,
    FORWARD_LEAN,
    BACKWARD_LEAN,
    LEFT_LEAN,
    RIGHT_LEAN,
    ROTATIONAL_IMBALANCE
}

/**
 * Postural analysis and alignment assessment
 */
data class PosturalAnalysis(
    val headPosition: PosturalComponent,
    val shoulderAlignment: PosturalComponent,
    val spinalAlignment: PosturalComponent,
    val pelvicAlignment: PosturalComponent,
    val legAlignment: PosturalComponent,
    val overallPostureScore: Float,
    val posturalDeviations: List<PosturalDeviation>,
    val recommendations: List<String>
)

/**
 * Individual postural component assessment
 */
data class PosturalComponent(
    val name: String,
    val score: Float,          // 0-1, higher is better
    val deviation: Float,      // degrees from ideal
    val status: PosturalStatus
)

/**
 * Postural deviation description
 */
data class PosturalDeviation(
    val type: PosturalDeviationType,
    val severity: Float,
    val description: String,
    val correctionSuggestion: String
)

/**
 * Types of postural deviations
 */
enum class PosturalDeviationType {
    FORWARD_HEAD_POSTURE,
    ROUNDED_SHOULDERS,
    KYPHOSIS,
    LORDOSIS,
    LATERAL_SPINE_DEVIATION,
    PELVIC_TILT,
    KNEE_VALGUS,
    KNEE_VARUS,
    FOOT_PRONATION,
    FOOT_SUPINATION
}

/**
 * Postural status categories
 */
enum class PosturalStatus {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    CRITICAL
}

/**
 * Movement pattern recognition and analysis
 */
data class MovementPattern(
    val patternType: MovementPatternType,
    val confidence: Float,
    val qualityScore: Float,
    val phase: MovementPhase,
    val tempo: MovementTempo,
    val symmetry: Float,
    val efficiency: Float,
    val recommendations: List<String>
)

/**
 * Types of movement patterns
 */
enum class MovementPatternType {
    SQUAT,
    LUNGE,
    DEADLIFT,
    PUSH_UP,
    PULL_UP,
    OVERHEAD_PRESS,
    PLANK,
    WALKING,
    RUNNING,
    JUMPING,
    STATIC_HOLD,
    UNKNOWN
}

/**
 * Movement phase classification
 */
enum class MovementPhase {
    PREPARATION,
    ECCENTRIC,
    BOTTOM_POSITION,
    CONCENTRIC,
    COMPLETION,
    TRANSITION,
    HOLD
}

/**
 * Movement tempo analysis
 */
data class MovementTempo(
    val eccentricTime: Float,  // Time in seconds
    val pauseTime: Float,      // Time at bottom/transition
    val concentricTime: Float, // Time for concentric phase
    val totalTime: Float,      // Total movement time
    val rhythm: TempoRhythm
)

/**
 * Tempo rhythm classification
 */
enum class TempoRhythm {
    TOO_FAST,
    OPTIMAL,
    TOO_SLOW,
    INCONSISTENT
}

/**
 * Kinetic chain analysis for movement efficiency
 */
data class KineticChainAnalysis(
    val leftArmChain: KineticChainLink,
    val rightArmChain: KineticChainLink,
    val leftLegChain: KineticChainLink,
    val rightLegChain: KineticChainLink,
    val coreStability: CoreStabilityAssessment,
    val overallEfficiency: Float
)

/**
 * Individual kinetic chain link assessment
 */
data class KineticChainLink(
    val name: String,
    val joints: List<String>,
    val alignment: Float,        // 0-1, joint alignment quality
    val stability: Float,        // 0-1, joint stability
    val efficiency: Float,       // 0-1, movement efficiency
    val coordinationScore: Float // 0-1, inter-joint coordination
)

/**
 * Core stability assessment
 */
data class CoreStabilityAssessment(
    val torsoAlignment: Float,      // 0-1, trunk alignment
    val shoulderLevelness: Float,   // 0-1, shoulder symmetry
    val hipLevelness: Float,        // 0-1, hip symmetry
    val rotationalStability: Float, // 0-1, anti-rotation strength
    val overallStability: Float     // 0-1, overall core stability
)

/**
 * Movement quality scoring
 */
data class MovementQualityScore(
    val overallScore: Float,           // 0-100, overall movement quality
    val rangeOfMotionScore: Float,     // 0-100, ROM quality
    val symmetryScore: Float,          // 0-100, left-right symmetry
    val posturalScore: Float,          // 0-100, postural alignment
    val coordinationScore: Float,      // 0-100, movement coordination
    val recommendations: List<String>
)

/**
 * Fatigue detection indicators
 */
data class FatigueIndicators(
    val movementVariabilityIncrease: Float, // Increase in movement inconsistency
    val posturalDecline: Float,              // Decrease in postural control
    val overallFatigueScore: Float,          // 0-100, overall fatigue level
    val recommendations: List<String>
) {
    companion object {
        fun none() = FatigueIndicators(0f, 0f, 0f, emptyList())
    }
}

/**
 * Compensation pattern detection
 */
data class CompensationPatterns(
    val detectedPatterns: List<CompensationPattern>,
    val overallCompensationScore: Float
)

/**
 * Individual compensation pattern
 */
data class CompensationPattern(
    val type: CompensationPatternType,
    val severity: Float,
    val description: String,
    val recommendedCorrection: String
)

/**
 * Types of compensation patterns
 */
enum class CompensationPatternType {
    LEFT_RIGHT_IMBALANCE,
    FORWARD_LEAN,
    BACKWARD_LEAN,
    LATERAL_LEAN,
    ROTATIONAL_COMPENSATION,
    KNEE_VALGUS_COMPENSATION,
    ANKLE_COMPENSATION,
    SHOULDER_ELEVATION,
    HIP_HIKING
}

/**
 * Exercise-specific biomechanical standards
 */
data class ExerciseStandards(
    val exerciseType: MovementPatternType,
    val optimalAngleRanges: Map<String, RangeOfMotion>,
    val criticalSafetyAngles: Map<String, RangeOfMotion>,
    val qualityMetrics: List<QualityMetric>
)

/**
 * Quality metric for exercise assessment
 */
data class QualityMetric(
    val name: String,
    val description: String,
    val weight: Float,
    val idealValue: Float,
    val acceptableRange: ClosedFloatingPointRange<Float>
)

/**
 * Temporal movement analysis window
 */
data class MovementWindow(
    val startTime: Long,
    val endTime: Long,
    val poses: List<PoseLandmarkResult>,
    val analysisType: AnalysisType
)

/**
 * Analysis type for temporal windows
 */
enum class AnalysisType {
    REAL_TIME,          // Continuous real-time analysis
    MOVEMENT_CYCLE,     // Full movement repetition
    STABILITY_WINDOW,   // Static pose stability
    FATIGUE_ASSESSMENT, // Long-term fatigue tracking
    COMPARISON_BASELINE // Baseline comparison
}

/**
 * Biomechanical recommendations with priority
 */
data class BiomechanicalRecommendation(
    val category: RecommendationCategory,
    val priority: RecommendationPriority,
    val description: String,
    val actionItems: List<String>,
    val expectedImprovement: String
)

/**
 * Recommendation categories
 */
enum class RecommendationCategory {
    SAFETY,
    TECHNIQUE,
    PERFORMANCE,
    MOBILITY,
    STABILITY,
    SYMMETRY,
    FATIGUE_MANAGEMENT
}

/**
 * Recommendation priority levels
 */
enum class RecommendationPriority {
    CRITICAL,   // Immediate attention required
    HIGH,       // Address soon
    MEDIUM,     // Improve when possible
    LOW         // Optional enhancement
}