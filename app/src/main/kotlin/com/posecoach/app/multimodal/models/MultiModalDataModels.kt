package com.posecoach.app.multimodal.models

import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.collections.emptyList

/**
 * Data models for multi-modal AI integration system
 */

// Visual Context Data Models
@Serializable
data class VisualContextData(
    val sceneType: String, // "gym", "home", "outdoor", "studio"
    val detectedObjects: List<DetectedObject>,
    val lightingConditions: String, // "good", "poor", "backlighted", "artificial"
    val spatialLayout: String, // "spacious", "confined", "cluttered", "organized"
    val facialExpressions: FacialExpressionData? = null,
    val gestureRecognition: List<GestureData> = emptyList(),
    val safetyAssessment: SafetyAssessmentData? = null,
    val confidence: Float
)

@Serializable
data class DetectedObject(
    val objectType: String, // "dumbbell", "yoga_mat", "chair", "mirror", etc.
    val confidence: Float,
    val boundingBox: BoundingBox,
    val relevanceToExercise: Float
)

@Serializable
data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

@Serializable
data class FacialExpressionData(
    val primaryExpression: String, // "focused", "strained", "relaxed", "confused"
    val expressionIntensity: Float,
    val eyeGaze: EyeGazeData? = null,
    val confidence: Float
)

@Serializable
data class EyeGazeData(
    val gazeDirection: String, // "camera", "mirror", "down", "away"
    val focusLevel: Float, // 0.0 to 1.0
    val confidence: Float
)

@Serializable
data class GestureData(
    val gestureType: String, // "pointing", "thumbs_up", "wave", "stop"
    val gestureDirection: String? = null,
    val confidence: Float
)

@Serializable
data class SafetyAssessmentData(
    val clearanceSpace: Float, // meters of clear space around user
    val potentialHazards: List<String>,
    val stabilityRisk: Float, // 0.0 (safe) to 1.0 (dangerous)
    val recommendedModifications: List<String>
)

// Audio Intelligence Data Models
@Serializable
data class AudioSignalData(
    val qualityScore: Float,
    val voiceActivityLevel: Float,
    val backgroundNoiseLevel: Float,
    val emotionalTone: String, // "motivated", "frustrated", "tired", "confident"
    val breathingPattern: BreathingPatternData? = null,
    val voiceStressLevel: Float,
    val speechClarity: Float,
    val environmentalAudio: EnvironmentalAudioData? = null,
    val confidence: Float
)

@Serializable
data class BreathingPatternData(
    val breathingRate: Float, // breaths per minute
    val breathingDepth: String, // "shallow", "normal", "deep"
    val breathingRhythm: String, // "regular", "irregular", "labored"
    val oxygenationLevel: String, // "good", "moderate", "concerning"
    val confidence: Float
)

@Serializable
data class EnvironmentalAudioData(
    val backgroundMusicType: String? = null, // "upbeat", "calming", "none"
    val ambientNoiseLevel: Float,
    val acousticEnvironment: String, // "echoey", "dampened", "normal"
    val potentialDistractions: List<String>
)

// Environment Context Data Models
@Serializable
data class EnvironmentContextData(
    val locationType: String, // "gym", "home", "outdoor", "studio"
    val activityContext: String, // "fitness", "rehabilitation", "sports", "wellness"
    val timeContext: String, // "morning", "afternoon", "evening", "night"
    val socialContext: String, // "solo", "group", "trainer_present", "virtual_class"
    val equipmentAvailable: List<String> = emptyList(),
    val spaceConstraints: SpaceConstraints? = null,
    val confidence: Float
)

@Serializable
data class SpaceConstraints(
    val availableWidth: Float, // meters
    val availableHeight: Float, // meters
    val availableDepth: Float, // meters
    val surfaceType: String, // "hardwood", "carpet", "rubber", "concrete"
    val stabilityRating: Float // 0.0 to 1.0
)

// User Context Data Models
@Serializable
data class UserContextData(
    val activityLevel: String, // "beginner", "moderate", "advanced", "expert"
    val fitnessGoals: List<String>,
    val experienceLevel: String,
    val physicalCondition: PhysicalConditionData? = null,
    val preferences: Map<String, String>,
    val motivationLevel: Float,
    val fatigueLevel: Float,
    val confidence: Float
)

@Serializable
data class PhysicalConditionData(
    val injuryHistory: List<String> = emptyList(),
    val mobilityLimitations: List<String> = emptyList(),
    val energyLevel: String, // "high", "moderate", "low", "exhausted"
    val perceivedExertion: Float, // 1-10 scale
    val hydrationStatus: String? = null // "good", "moderate", "dehydrated"
)

// Emotional Intelligence Data Models
@Serializable
data class EmotionalStateAnalysis(
    val primaryEmotion: String, // "motivated", "frustrated", "confident", "anxious"
    val emotionIntensity: Float,
    val motivationLevel: Float,
    val stressIndicators: List<StressIndicator>,
    val engagementLevel: Float,
    val confidenceLevel: Float,
    val fatigueIndicators: List<FatigueIndicator>,
    val overallEmotionalWellbeing: Float,
    val confidence: Float
)

@Serializable
data class StressIndicator(
    val type: String, // "vocal", "postural", "facial", "breathing"
    val severity: Float, // 0.0 to 1.0
    val description: String
)

@Serializable
data class FatigueIndicator(
    val type: String, // "physical", "mental", "emotional"
    val level: Float, // 0.0 to 1.0
    val manifestation: String
)

// Contextual AI Data Models
@Serializable
data class ContextualInsight(
    val insight: String,
    val confidence: Float,
    val recommendations: List<ActionableRecommendation>,
    val contextualFactors: List<String>
)

@Serializable
data class ActionableRecommendation(
    val priority: Priority,
    val category: String, // "posture", "safety", "motivation", "technique"
    val title: String,
    val description: String,
    val targetModalities: List<String>,
    val expectedImpact: String,
    val implementationSteps: List<String> = emptyList()
)

enum class Priority {
    CRITICAL, HIGH, MEDIUM, LOW
}

// Processing Pipeline Data Models
@Serializable
data class TemporalPattern(
    val patternType: String, // "improvement", "degradation", "stability", "oscillation"
    val timeSpan: Long, // milliseconds
    val confidence: Float,
    val keyPoints: List<TemporalKeyPoint>
)

@Serializable
data class TemporalKeyPoint(
    val timestamp: Long,
    val event: String,
    val significance: Float
)

@Serializable
data class CrossModalValidation(
    val agreementLevel: Float, // 0.0 to 1.0
    val conflictingSignals: List<ConflictingSignal>,
    val validatedInsights: List<String>,
    val confidence: Float
)

@Serializable
data class ConflictingSignal(
    val modality1: String,
    val modality2: String,
    val conflictDescription: String,
    val resolutionStrategy: String
)

// Performance and Quality Metrics
@Serializable
data class MultiModalQualityMetrics(
    val overallQuality: Float,
    val modalityQualities: Map<String, Float>,
    val processingLatency: Long,
    val resourceUsage: ResourceUsageData,
    val privacyCompliance: PrivacyComplianceData
)

@Serializable
data class ResourceUsageData(
    val cpuUsage: Float,
    val memoryUsage: Float,
    val batteryImpact: Float,
    val networkUsage: Float
)

@Serializable
data class PrivacyComplianceData(
    val dataMinimization: Boolean,
    val consentCompliance: Boolean,
    val localProcessingRatio: Float,
    val encryptionStatus: String
)

// Cross-Modal Analysis Data Models
@Serializable
data class ModalityAnalysis(
    val modality: String,
    val confidence: Float,
    val insights: List<String>,
    val timestamp: Long
)

// Multi-Modal Input Data Model
@Serializable
data class MultiModalInput(
    val timestamp: Long = System.currentTimeMillis(),
    val inputId: String,
    val poseLandmarks: @Contextual PoseLandmarkResult? = null,
    val visualContext: VisualContextData? = null,
    val audioSignal: AudioSignalData? = null,
    val environmentContext: EnvironmentContextData? = null,
    val userContext: UserContextData? = null
)

@Serializable
data class ContextualFactor(
    val type: String,
    val value: String,
    val confidence: Float
)

@Serializable
data class FusionPerformanceMetrics(
    val processingTimeMs: Long,
    val modalitiesProcessed: Int,
    val confidenceScore: Float
)