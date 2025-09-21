package com.posecoach.app.intelligence

import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Personalized feedback management system that adapts coaching style,
 * timing, and content based on user preferences and performance
 */
class PersonalizedFeedbackManager {

    private val _feedbackEvents = MutableSharedFlow<FeedbackEvent>(
        replay = 0,
        extraBufferCapacity = 50
    )
    val feedbackEvents: SharedFlow<FeedbackEvent> = _feedbackEvents.asSharedFlow()

    private val _adaptiveFeedback = MutableSharedFlow<AdaptiveFeedback>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val adaptiveFeedback: SharedFlow<AdaptiveFeedback> = _adaptiveFeedback.asSharedFlow()

    // User profile and preferences
    private var userProfile = UserProfile()
    private val performanceHistory = mutableListOf<PerformanceSnapshot>()
    private val feedbackHistory = mutableListOf<FeedbackResponse>()

    // Feedback timing and frequency management
    private var lastFeedbackTime = 0L
    private var consecutiveFeedbackCount = 0
    private val feedbackCooldownManager = FeedbackCooldownManager()

    // Learning style adaptation
    private val learningStyleAnalyzer = LearningStyleAnalyzer()
    private val motivationProfiler = MotivationProfiler()

    data class FeedbackEvent(
        val type: FeedbackType,
        val priority: FeedbackPriority,
        val content: FeedbackContent,
        val timing: FeedbackTiming,
        val personalization: PersonalizationContext,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class AdaptiveFeedback(
        val message: String,
        val modalityPreference: List<FeedbackModality>,
        val urgency: FeedbackUrgency,
        val emotionalTone: EmotionalTone,
        val learningStyle: LearningStyle,
        val timing: OptimalTiming,
        val visualCues: List<VisualCue>?,
        val audioCues: List<AudioCue>?,
        val hapticCues: List<HapticCue>?
    )

    data class UserProfile(
        val fitnessLevel: FitnessLevel = FitnessLevel.INTERMEDIATE,
        val learningStyle: LearningStyle = LearningStyle.MIXED,
        val motivationType: MotivationType = MotivationType.ACHIEVEMENT,
        val communicationPreference: CommunicationStyle = CommunicationStyle.ENCOURAGING,
        val feedbackFrequency: FeedbackFrequency = FeedbackFrequency.MODERATE,
        val culturalContext: CulturalContext = CulturalContext.WESTERN,
        val accessibilityNeeds: List<AccessibilityNeed> = emptyList(),
        val personalGoals: List<FitnessGoal> = emptyList(),
        val injuryHistory: List<InjuryHistory> = emptyList(),
        val preferredLanguage: String = "en",
        val emotionalSensitivity: EmotionalSensitivity = EmotionalSensitivity.MODERATE
    )

    data class PerformanceSnapshot(
        val timestamp: Long,
        val exerciseType: WorkoutContextAnalyzer.ExerciseType,
        val formQuality: WorkoutContextAnalyzer.FormQuality,
        val intensity: WorkoutContextAnalyzer.IntensityLevel,
        val fatigue: WorkoutContextAnalyzer.FatigueLevel,
        val responsiveness: UserResponsiveness,
        val improvementRate: Float
    )

    data class FeedbackResponse(
        val feedbackId: String,
        val userReaction: UserReaction,
        val effectivenessScore: Float,
        val timeToImprovement: Long,
        val modalityUsed: FeedbackModality,
        val timestamp: Long
    )

    data class FeedbackContent(
        val primaryMessage: String,
        val detailedExplanation: String?,
        val visualDescription: String?,
        val demonstrationHint: String?,
        val targetAudience: FitnessLevel,
        val complexity: ComplexityLevel
    )

    data class FeedbackTiming(
        val deliveryMethod: DeliveryMethod,
        val optimalWindow: TimeWindow,
        val priorityLevel: Int,
        val canInterrupt: Boolean,
        val batchWithOthers: Boolean
    )

    data class PersonalizationContext(
        val currentMood: UserMood,
        val energyLevel: EnergyLevel,
        val focusLevel: FocusLevel,
        val previousFeedbackEffectiveness: Float,
        val sessionProgress: Float,
        val adaptationReason: String
    )

    data class VisualCue(
        val type: VisualCueType,
        val position: BodyPosition,
        val color: FeedbackColor,
        val animation: AnimationType,
        val duration: Long
    )

    data class AudioCue(
        val type: AudioCueType,
        val tone: AudioTone,
        val volume: AudioVolume,
        val duration: Long,
        val spatialDirection: SpatialDirection?
    )

    data class HapticCue(
        val type: HapticType,
        val intensity: HapticIntensity,
        val pattern: HapticPattern,
        val duration: Long,
        val bodyTarget: BodyTarget?
    )

    data class OptimalTiming(
        val preferredDelay: Long,
        val maxWaitTime: Long,
        val batchingWindow: Long,
        val avoidanceWindows: List<TimeRange>
    )

    // Enums for feedback system
    enum class FeedbackType {
        FORM_CORRECTION, ENCOURAGEMENT, WARNING, INSTRUCTION,
        PROGRESS_UPDATE, MOTIVATION, TECHNIQUE_TIP, SAFETY_ALERT
    }

    enum class FeedbackPriority {
        CRITICAL, HIGH, MEDIUM, LOW, BACKGROUND
    }

    enum class FeedbackModality {
        VISUAL, AUDIO, HAPTIC, TEXT, GESTURE, MIXED
    }

    enum class FeedbackUrgency {
        IMMEDIATE, URGENT, NORMAL, DEFERRED, OPTIONAL
    }

    enum class EmotionalTone {
        ENCOURAGING, NEUTRAL, FIRM, GENTLE, ENERGETIC, CALM, URGENT
    }

    enum class FitnessLevel {
        BEGINNER, INTERMEDIATE, ADVANCED, EXPERT, REHABILITATION
    }

    enum class LearningStyle {
        VISUAL, AUDITORY, KINESTHETIC, MIXED, ANALYTICAL, INTUITIVE
    }

    enum class MotivationType {
        ACHIEVEMENT, COMPETITION, SOCIAL, HEALTH, AESTHETIC, PERFORMANCE
    }

    enum class CommunicationStyle {
        DIRECT, ENCOURAGING, TECHNICAL, CASUAL, FORMAL, MOTIVATIONAL
    }

    enum class FeedbackFrequency {
        MINIMAL, LOW, MODERATE, HIGH, CONSTANT
    }

    enum class CulturalContext {
        WESTERN, EASTERN, LATIN, NORDIC, MEDITERRANEAN, MIXED
    }

    enum class AccessibilityNeed {
        VISUAL_IMPAIRMENT, HEARING_IMPAIRMENT, MOTOR_LIMITATION,
        COGNITIVE_ASSISTANCE, LARGE_TEXT, HIGH_CONTRAST, VOICE_CONTROL
    }

    enum class FitnessGoal {
        WEIGHT_LOSS, MUSCLE_GAIN, ENDURANCE, FLEXIBILITY,
        STRENGTH, REHABILITATION, GENERAL_FITNESS, SPORT_SPECIFIC
    }

    enum class InjuryHistory {
        KNEE, BACK, SHOULDER, ANKLE, WRIST, NECK, HIP, NONE
    }

    enum class EmotionalSensitivity {
        LOW, MODERATE, HIGH, VERY_HIGH
    }

    enum class UserReaction {
        IMMEDIATE_IMPROVEMENT, GRADUAL_IMPROVEMENT, NO_CHANGE,
        RESISTANCE, CONFUSION, POSITIVE_RESPONSE, NEGATIVE_RESPONSE
    }

    enum class UserResponsiveness {
        HIGHLY_RESPONSIVE, RESPONSIVE, MODERATE, SLOW, NON_RESPONSIVE
    }

    enum class UserMood {
        MOTIVATED, NEUTRAL, TIRED, FRUSTRATED, CONFIDENT, ANXIOUS, FOCUSED
    }

    enum class EnergyLevel {
        VERY_LOW, LOW, MODERATE, HIGH, VERY_HIGH
    }

    enum class FocusLevel {
        DISTRACTED, LOW, MODERATE, HIGH, HYPER_FOCUSED
    }

    enum class ComplexityLevel {
        SIMPLE, MODERATE, DETAILED, TECHNICAL, EXPERT
    }

    enum class DeliveryMethod {
        IMMEDIATE, BATCHED, SCHEDULED, CONTEXTUAL, ON_DEMAND
    }

    enum class TimeWindow {
        IMMEDIATE, SHORT, MEDIUM, LONG, FLEXIBLE
    }

    enum class VisualCueType {
        ARROW, CIRCLE, HIGHLIGHT, OVERLAY, ANIMATION, COLOR_CHANGE
    }

    enum class BodyPosition {
        HEAD, TORSO, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, FULL_BODY
    }

    enum class FeedbackColor {
        GREEN, YELLOW, RED, BLUE, WHITE, NEUTRAL
    }

    enum class AnimationType {
        PULSE, FADE, SLIDE, ROTATE, SCALE, BLINK
    }

    enum class AudioCueType {
        BEEP, CHIME, VOICE, MUSIC, AMBIENT, NOTIFICATION
    }

    enum class AudioTone {
        HIGH, MEDIUM, LOW, PLEASANT, ALERT, CALM
    }

    enum class AudioVolume {
        QUIET, NORMAL, LOUD, ADAPTIVE
    }

    enum class SpatialDirection {
        LEFT, RIGHT, FRONT, BACK, ABOVE, BELOW
    }

    enum class HapticType {
        VIBRATION, PULSE, TAP, PATTERN, GENTLE, STRONG
    }

    enum class HapticIntensity {
        SUBTLE, LIGHT, MEDIUM, STRONG, INTENSE
    }

    enum class HapticPattern {
        SINGLE, DOUBLE, TRIPLE, RHYTHM, CONTINUOUS, INTERMITTENT
    }

    enum class BodyTarget {
        WRIST, WAIST, CHEST, ARM, LEG, GENERAL
    }

    data class TimeRange(val start: Long, val end: Long)

    /**
     * Generate personalized feedback based on pose analysis and user context
     */
    fun generatePersonalizedFeedback(
        poseResult: PoseLandmarkResult,
        workoutContext: WorkoutContextAnalyzer.WorkoutContext,
        exerciseDetection: WorkoutContextAnalyzer.ExerciseDetection?,
        userState: UserState
    ) {
        // Check if feedback should be delivered now
        if (!shouldDeliverFeedback(userState)) {
            return
        }

        // Analyze pose for issues
        val poseIssues = analyzePoseIssues(poseResult, exerciseDetection)

        // Generate contextual feedback
        val feedbacks = generateContextualFeedback(
            poseIssues,
            workoutContext,
            exerciseDetection,
            userState
        )

        // Personalize and prioritize feedback
        val personalizedFeedbacks = feedbacks.map { feedback ->
            personalizeFeedback(feedback, userState)
        }.sortedByDescending { it.priority.ordinal }

        // Deliver feedback with optimal timing
        deliverFeedback(personalizedFeedbacks.take(3), userState) // Limit to top 3
    }

    private fun shouldDeliverFeedback(userState: UserState): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastFeedback = currentTime - lastFeedbackTime

        // Check cooldown
        if (!feedbackCooldownManager.canDeliverFeedback(userState.currentMood, userState.focusLevel)) {
            return false
        }

        // Check frequency preference
        val minInterval = when (userProfile.feedbackFrequency) {
            FeedbackFrequency.CONSTANT -> 1000L    // 1 second
            FeedbackFrequency.HIGH -> 3000L        // 3 seconds
            FeedbackFrequency.MODERATE -> 5000L    // 5 seconds
            FeedbackFrequency.LOW -> 10000L        // 10 seconds
            FeedbackFrequency.MINIMAL -> 30000L    // 30 seconds
        }

        if (timeSinceLastFeedback < minInterval) {
            return false
        }

        // Check user state
        return when {
            userState.focusLevel == FocusLevel.DISTRACTED -> false
            userState.energyLevel == EnergyLevel.VERY_LOW -> false
            consecutiveFeedbackCount > 5 -> false // Prevent feedback overload
            else -> true
        }
    }

    private fun analyzePoseIssues(
        pose: PoseLandmarkResult,
        exercise: WorkoutContextAnalyzer.ExerciseDetection?
    ): List<PoseIssue> {
        val issues = mutableListOf<PoseIssue>()

        // Basic posture analysis
        val landmarks = pose.landmarks

        // Check spine alignment
        val spineAlignment = checkSpineAlignment(landmarks)
        if (spineAlignment.severity > 0.6f) {
            issues.add(PoseIssue(
                type = IssueType.SPINE_ALIGNMENT,
                severity = spineAlignment.severity,
                description = "Spine alignment needs attention",
                bodyParts = listOf(BodyPosition.TORSO),
                correctiveAction = "Keep your back straight and shoulders aligned"
            ))
        }

        // Check knee alignment
        val kneeAlignment = checkKneeAlignment(landmarks)
        if (kneeAlignment.severity > 0.5f) {
            issues.add(PoseIssue(
                type = IssueType.KNEE_ALIGNMENT,
                severity = kneeAlignment.severity,
                description = "Knee tracking needs correction",
                bodyParts = listOf(BodyPosition.LEFT_LEG, BodyPosition.RIGHT_LEG),
                correctiveAction = "Keep knees aligned with your toes"
            ))
        }

        // Exercise-specific analysis
        exercise?.let { ex ->
            when (ex.exerciseType) {
                WorkoutContextAnalyzer.ExerciseType.SQUAT -> {
                    analyzeSquatForm(landmarks)?.let { issues.add(it) }
                }
                WorkoutContextAnalyzer.ExerciseType.PLANK -> {
                    analyzePlankForm(landmarks)?.let { issues.add(it) }
                }
                WorkoutContextAnalyzer.ExerciseType.PUSH_UP -> {
                    analyzePushUpForm(landmarks)?.let { issues.add(it) }
                }
                else -> {}
            }
        }

        return issues
    }

    private fun generateContextualFeedback(
        issues: List<PoseIssue>,
        workoutContext: WorkoutContextAnalyzer.WorkoutContext,
        exercise: WorkoutContextAnalyzer.ExerciseDetection?,
        userState: UserState
    ): List<FeedbackEvent> {
        val feedbacks = mutableListOf<FeedbackEvent>()

        // Safety-critical feedback first
        val criticalIssues = issues.filter { it.severity > 0.8f }
        criticalIssues.forEach { issue ->
            feedbacks.add(createSafetyFeedback(issue, userState))
        }

        // Form correction feedback
        val formIssues = issues.filter { it.severity > 0.5f && it.severity <= 0.8f }
        formIssues.forEach { issue ->
            feedbacks.add(createFormCorrectionFeedback(issue, userState))
        }

        // Encouragement and motivation
        if (issues.isEmpty() || issues.all { it.severity < 0.3f }) {
            feedbacks.add(createEncouragementFeedback(workoutContext, exercise, userState))
        }

        // Progress updates
        if (shouldProvideProgressUpdate(workoutContext)) {
            feedbacks.add(createProgressFeedback(workoutContext, userState))
        }

        // Fatigue management
        if (workoutContext.fatigue in listOf(
            WorkoutContextAnalyzer.FatigueLevel.TIRED,
            WorkoutContextAnalyzer.FatigueLevel.EXHAUSTED
        )) {
            feedbacks.add(createFatigueFeedback(workoutContext.fatigue, userState))
        }

        return feedbacks
    }

    private fun personalizeFeedback(
        feedback: FeedbackEvent,
        userState: UserState
    ): FeedbackEvent {
        // Adapt message based on learning style
        val adaptedContent = adaptContentForLearningStyle(feedback.content)

        // Adjust emotional tone based on user state and preferences
        val emotionalTone = selectEmotionalTone(userState)

        // Determine optimal modalities
        val modalities = selectOptimalModalities(userState)

        // Create personalization context
        val personalization = PersonalizationContext(
            currentMood = userState.currentMood,
            energyLevel = userState.energyLevel,
            focusLevel = userState.focusLevel,
            previousFeedbackEffectiveness = calculatePreviousEffectiveness(),
            sessionProgress = userState.sessionProgress,
            adaptationReason = "Personalized for current user state and preferences"
        )

        return feedback.copy(
            content = adaptedContent,
            personalization = personalization
        )
    }

    private fun deliverFeedback(feedbacks: List<FeedbackEvent>, userState: UserState) {
        feedbacks.forEach { feedback ->
            val adaptiveFeedback = convertToAdaptiveFeedback(feedback, userState)

            try {
                _feedbackEvents.tryEmit(feedback)
                _adaptiveFeedback.tryEmit(adaptiveFeedback)

                recordFeedbackDelivery(feedback)
                lastFeedbackTime = System.currentTimeMillis()
                consecutiveFeedbackCount++

                Timber.d("Delivered personalized feedback: ${feedback.type} with tone: ${adaptiveFeedback.emotionalTone}")

            } catch (e: Exception) {
                Timber.e(e, "Failed to deliver feedback")
            }
        }
    }

    private fun convertToAdaptiveFeedback(
        feedback: FeedbackEvent,
        userState: UserState
    ): AdaptiveFeedback {
        val message = generatePersonalizedMessage(feedback, userState)
        val modalities = selectOptimalModalities(userState)
        val emotionalTone = selectEmotionalTone(userState)
        val timing = calculateOptimalTiming(feedback, userState)

        return AdaptiveFeedback(
            message = message,
            modalityPreference = modalities,
            urgency = mapPriorityToUrgency(feedback.priority),
            emotionalTone = emotionalTone,
            learningStyle = userProfile.learningStyle,
            timing = timing,
            visualCues = createVisualCues(feedback, userState),
            audioCues = createAudioCues(feedback, userState),
            hapticCues = createHapticCues(feedback, userState)
        )
    }

    /**
     * Update user profile based on feedback responses and performance
     */
    fun updateUserProfile(
        performanceData: PerformanceSnapshot,
        feedbackResponse: FeedbackResponse
    ) {
        // Add to history
        performanceHistory.add(performanceData)
        feedbackHistory.add(feedbackResponse)

        // Trim history if too large
        if (performanceHistory.size > 500) {
            performanceHistory.removeAt(0)
        }
        if (feedbackHistory.size > 200) {
            feedbackHistory.removeAt(0)
        }

        // Update learning style based on feedback effectiveness
        learningStyleAnalyzer.updateLearningStyle(feedbackResponse, userProfile)

        // Update motivation profile
        motivationProfiler.updateMotivationProfile(performanceData, userProfile)

        // Adjust feedback frequency based on responsiveness
        adjustFeedbackFrequency(feedbackResponse)

        Timber.d("User profile updated with new performance and feedback data")
    }

    /**
     * Set user preferences explicitly
     */
    fun updateUserPreferences(preferences: UserPreferences) {
        userProfile = userProfile.copy(
            learningStyle = preferences.learningStyle,
            motivationType = preferences.motivationType,
            communicationPreference = preferences.communicationStyle,
            feedbackFrequency = preferences.feedbackFrequency,
            culturalContext = preferences.culturalContext,
            accessibilityNeeds = preferences.accessibilityNeeds,
            emotionalSensitivity = preferences.emotionalSensitivity
        )

        Timber.i("User preferences updated: $preferences")
    }

    // Helper data classes and methods
    data class PoseIssue(
        val type: IssueType,
        val severity: Float,
        val description: String,
        val bodyParts: List<BodyPosition>,
        val correctiveAction: String
    )

    data class AlignmentCheck(
        val severity: Float,
        val details: String
    )

    data class UserState(
        val currentMood: UserMood,
        val energyLevel: EnergyLevel,
        val focusLevel: FocusLevel,
        val sessionProgress: Float,
        val recentPerformance: Float,
        val timeInSession: Long
    )

    data class UserPreferences(
        val learningStyle: LearningStyle,
        val motivationType: MotivationType,
        val communicationStyle: CommunicationStyle,
        val feedbackFrequency: FeedbackFrequency,
        val culturalContext: CulturalContext,
        val accessibilityNeeds: List<AccessibilityNeed>,
        val emotionalSensitivity: EmotionalSensitivity
    )

    enum class IssueType {
        SPINE_ALIGNMENT, KNEE_ALIGNMENT, SHOULDER_ALIGNMENT,
        DEPTH_ISSUE, TEMPO_ISSUE, BALANCE_ISSUE, SAFETY_RISK
    }

    // Detailed implementation of helper methods would go here...
    // For brevity, showing key method signatures:

    private fun checkSpineAlignment(landmarks: List<PoseLandmarkResult.Landmark>): AlignmentCheck {
        // Implementation for spine alignment check
        return AlignmentCheck(0.3f, "Spine alignment acceptable")
    }

    private fun checkKneeAlignment(landmarks: List<PoseLandmarkResult.Landmark>): AlignmentCheck {
        // Implementation for knee alignment check
        return AlignmentCheck(0.2f, "Knee alignment good")
    }

    private fun analyzeSquatForm(landmarks: List<PoseLandmarkResult.Landmark>): PoseIssue? {
        // Implementation for squat-specific form analysis
        return null
    }

    private fun analyzePlankForm(landmarks: List<PoseLandmarkResult.Landmark>): PoseIssue? {
        // Implementation for plank-specific form analysis
        return null
    }

    private fun analyzePushUpForm(landmarks: List<PoseLandmarkResult.Landmark>): PoseIssue? {
        // Implementation for push-up specific form analysis
        return null
    }

    private fun createSafetyFeedback(issue: PoseIssue, userState: UserState): FeedbackEvent {
        return FeedbackEvent(
            type = FeedbackType.SAFETY_ALERT,
            priority = FeedbackPriority.CRITICAL,
            content = FeedbackContent(
                primaryMessage = "Safety Alert: ${issue.correctiveAction}",
                detailedExplanation = issue.description,
                visualDescription = "Highlight ${issue.bodyParts.joinToString(", ")}",
                demonstrationHint = "Show proper alignment",
                targetAudience = userProfile.fitnessLevel,
                complexity = ComplexityLevel.SIMPLE
            ),
            timing = FeedbackTiming(
                deliveryMethod = DeliveryMethod.IMMEDIATE,
                optimalWindow = TimeWindow.IMMEDIATE,
                priorityLevel = 1,
                canInterrupt = true,
                batchWithOthers = false
            ),
            personalization = PersonalizationContext(
                currentMood = userState.currentMood,
                energyLevel = userState.energyLevel,
                focusLevel = userState.focusLevel,
                previousFeedbackEffectiveness = 0.8f,
                sessionProgress = userState.sessionProgress,
                adaptationReason = "Safety-critical feedback"
            )
        )
    }

    private fun createFormCorrectionFeedback(issue: PoseIssue, userState: UserState): FeedbackEvent {
        return FeedbackEvent(
            type = FeedbackType.FORM_CORRECTION,
            priority = FeedbackPriority.HIGH,
            content = FeedbackContent(
                primaryMessage = issue.correctiveAction,
                detailedExplanation = issue.description,
                visualDescription = "Adjust ${issue.bodyParts.joinToString(", ")}",
                demonstrationHint = null,
                targetAudience = userProfile.fitnessLevel,
                complexity = ComplexityLevel.MODERATE
            ),
            timing = FeedbackTiming(
                deliveryMethod = DeliveryMethod.CONTEXTUAL,
                optimalWindow = TimeWindow.SHORT,
                priorityLevel = 2,
                canInterrupt = false,
                batchWithOthers = true
            ),
            personalization = PersonalizationContext(
                currentMood = userState.currentMood,
                energyLevel = userState.energyLevel,
                focusLevel = userState.focusLevel,
                previousFeedbackEffectiveness = calculatePreviousEffectiveness(),
                sessionProgress = userState.sessionProgress,
                adaptationReason = "Form improvement guidance"
            )
        )
    }

    private fun createEncouragementFeedback(
        workoutContext: WorkoutContextAnalyzer.WorkoutContext,
        exercise: WorkoutContextAnalyzer.ExerciseDetection?,
        userState: UserState
    ): FeedbackEvent {
        val message = when (userProfile.motivationType) {
            MotivationType.ACHIEVEMENT -> "Great form! You're nailing this exercise!"
            MotivationType.COMPETITION -> "You're ahead of your personal best!"
            MotivationType.HEALTH -> "Your body will thank you for this excellent form!"
            MotivationType.PERFORMANCE -> "Perfect technique - that's how you build strength!"
            else -> "Keep up the excellent work!"
        }

        return FeedbackEvent(
            type = FeedbackType.ENCOURAGEMENT,
            priority = FeedbackPriority.MEDIUM,
            content = FeedbackContent(
                primaryMessage = message,
                detailedExplanation = null,
                visualDescription = null,
                demonstrationHint = null,
                targetAudience = userProfile.fitnessLevel,
                complexity = ComplexityLevel.SIMPLE
            ),
            timing = FeedbackTiming(
                deliveryMethod = DeliveryMethod.BATCHED,
                optimalWindow = TimeWindow.MEDIUM,
                priorityLevel = 3,
                canInterrupt = false,
                batchWithOthers = true
            ),
            personalization = PersonalizationContext(
                currentMood = userState.currentMood,
                energyLevel = userState.energyLevel,
                focusLevel = userState.focusLevel,
                previousFeedbackEffectiveness = calculatePreviousEffectiveness(),
                sessionProgress = userState.sessionProgress,
                adaptationReason = "Positive reinforcement"
            )
        )
    }

    private fun createProgressFeedback(
        workoutContext: WorkoutContextAnalyzer.WorkoutContext,
        userState: UserState
    ): FeedbackEvent {
        val progressMessage = "You've completed ${(userState.sessionProgress * 100).toInt()}% of your workout!"

        return FeedbackEvent(
            type = FeedbackType.PROGRESS_UPDATE,
            priority = FeedbackPriority.LOW,
            content = FeedbackContent(
                primaryMessage = progressMessage,
                detailedExplanation = "Session duration: ${workoutContext.sessionDuration / 60000} minutes",
                visualDescription = null,
                demonstrationHint = null,
                targetAudience = userProfile.fitnessLevel,
                complexity = ComplexityLevel.SIMPLE
            ),
            timing = FeedbackTiming(
                deliveryMethod = DeliveryMethod.SCHEDULED,
                optimalWindow = TimeWindow.FLEXIBLE,
                priorityLevel = 4,
                canInterrupt = false,
                batchWithOthers = true
            ),
            personalization = PersonalizationContext(
                currentMood = userState.currentMood,
                energyLevel = userState.energyLevel,
                focusLevel = userState.focusLevel,
                previousFeedbackEffectiveness = calculatePreviousEffectiveness(),
                sessionProgress = userState.sessionProgress,
                adaptationReason = "Progress motivation"
            )
        )
    }

    private fun createFatigueFeedback(
        fatigue: WorkoutContextAnalyzer.FatigueLevel,
        userState: UserState
    ): FeedbackEvent {
        val message = when (fatigue) {
            WorkoutContextAnalyzer.FatigueLevel.TIRED -> "Consider taking a short break to maintain good form"
            WorkoutContextAnalyzer.FatigueLevel.EXHAUSTED -> "Time for a cool-down - you've worked hard!"
            else -> "Listen to your body and rest when needed"
        }

        return FeedbackEvent(
            type = FeedbackType.WARNING,
            priority = FeedbackPriority.HIGH,
            content = FeedbackContent(
                primaryMessage = message,
                detailedExplanation = "Fatigue can affect form and increase injury risk",
                visualDescription = null,
                demonstrationHint = null,
                targetAudience = userProfile.fitnessLevel,
                complexity = ComplexityLevel.SIMPLE
            ),
            timing = FeedbackTiming(
                deliveryMethod = DeliveryMethod.IMMEDIATE,
                optimalWindow = TimeWindow.SHORT,
                priorityLevel = 2,
                canInterrupt = true,
                batchWithOthers = false
            ),
            personalization = PersonalizationContext(
                currentMood = userState.currentMood,
                energyLevel = userState.energyLevel,
                focusLevel = userState.focusLevel,
                previousFeedbackEffectiveness = calculatePreviousEffectiveness(),
                sessionProgress = userState.sessionProgress,
                adaptationReason = "Fatigue management"
            )
        )
    }

    // Additional helper methods with simplified implementations
    private fun adaptContentForLearningStyle(content: FeedbackContent): FeedbackContent = content

    private fun selectEmotionalTone(userState: UserState): EmotionalTone {
        return when (userState.currentMood) {
            UserMood.MOTIVATED -> EmotionalTone.ENERGETIC
            UserMood.TIRED -> EmotionalTone.GENTLE
            UserMood.FRUSTRATED -> EmotionalTone.CALM
            UserMood.CONFIDENT -> EmotionalTone.ENCOURAGING
            else -> EmotionalTone.NEUTRAL
        }
    }

    private fun selectOptimalModalities(userState: UserState): List<FeedbackModality> {
        return when (userProfile.learningStyle) {
            LearningStyle.VISUAL -> listOf(FeedbackModality.VISUAL, FeedbackModality.TEXT)
            LearningStyle.AUDITORY -> listOf(FeedbackModality.AUDIO, FeedbackModality.TEXT)
            LearningStyle.KINESTHETIC -> listOf(FeedbackModality.HAPTIC, FeedbackModality.VISUAL)
            else -> listOf(FeedbackModality.MIXED)
        }
    }

    private fun calculateOptimalTiming(feedback: FeedbackEvent, userState: UserState): OptimalTiming {
        return OptimalTiming(
            preferredDelay = 500L,
            maxWaitTime = 5000L,
            batchingWindow = 2000L,
            avoidanceWindows = emptyList()
        )
    }

    private fun createVisualCues(feedback: FeedbackEvent, userState: UserState): List<VisualCue>? {
        if (FeedbackModality.VISUAL !in selectOptimalModalities(userState)) return null

        return listOf(
            VisualCue(
                type = VisualCueType.HIGHLIGHT,
                position = BodyPosition.FULL_BODY,
                color = when (feedback.priority) {
                    FeedbackPriority.CRITICAL -> FeedbackColor.RED
                    FeedbackPriority.HIGH -> FeedbackColor.YELLOW
                    else -> FeedbackColor.GREEN
                },
                animation = AnimationType.PULSE,
                duration = 2000L
            )
        )
    }

    private fun createAudioCues(feedback: FeedbackEvent, userState: UserState): List<AudioCue>? {
        if (FeedbackModality.AUDIO !in selectOptimalModalities(userState)) return null

        return listOf(
            AudioCue(
                type = AudioCueType.CHIME,
                tone = AudioTone.PLEASANT,
                volume = AudioVolume.NORMAL,
                duration = 500L,
                spatialDirection = null
            )
        )
    }

    private fun createHapticCues(feedback: FeedbackEvent, userState: UserState): List<HapticCue>? {
        if (FeedbackModality.HAPTIC !in selectOptimalModalities(userState)) return null

        return listOf(
            HapticCue(
                type = HapticType.VIBRATION,
                intensity = HapticIntensity.MEDIUM,
                pattern = HapticPattern.DOUBLE,
                duration = 300L,
                bodyTarget = BodyTarget.WRIST
            )
        )
    }

    private fun generatePersonalizedMessage(feedback: FeedbackEvent, userState: UserState): String {
        return feedback.content.primaryMessage // Simplified for now
    }

    private fun mapPriorityToUrgency(priority: FeedbackPriority): FeedbackUrgency {
        return when (priority) {
            FeedbackPriority.CRITICAL -> FeedbackUrgency.IMMEDIATE
            FeedbackPriority.HIGH -> FeedbackUrgency.URGENT
            FeedbackPriority.MEDIUM -> FeedbackUrgency.NORMAL
            FeedbackPriority.LOW -> FeedbackUrgency.DEFERRED
            FeedbackPriority.BACKGROUND -> FeedbackUrgency.OPTIONAL
        }
    }

    private fun shouldProvideProgressUpdate(context: WorkoutContextAnalyzer.WorkoutContext): Boolean {
        return context.sessionDuration > 300000L // After 5 minutes
    }

    private fun calculatePreviousEffectiveness(): Float {
        if (feedbackHistory.isEmpty()) return 0.7f

        return feedbackHistory.takeLast(10)
            .map { it.effectivenessScore }
            .average()
            .toFloat()
    }

    private fun recordFeedbackDelivery(feedback: FeedbackEvent) {
        consecutiveFeedbackCount++
        feedbackCooldownManager.recordFeedback(feedback)
    }

    private fun adjustFeedbackFrequency(response: FeedbackResponse) {
        // Adjust frequency based on user response effectiveness
        if (response.effectivenessScore < 0.3f) {
            // Reduce frequency if feedback isn't effective
            when (userProfile.feedbackFrequency) {
                FeedbackFrequency.CONSTANT -> userProfile = userProfile.copy(feedbackFrequency = FeedbackFrequency.HIGH)
                FeedbackFrequency.HIGH -> userProfile = userProfile.copy(feedbackFrequency = FeedbackFrequency.MODERATE)
                FeedbackFrequency.MODERATE -> userProfile = userProfile.copy(feedbackFrequency = FeedbackFrequency.LOW)
                else -> {}
            }
        }
    }

    /**
     * Reset feedback state for new session
     */
    fun resetSession() {
        lastFeedbackTime = 0L
        consecutiveFeedbackCount = 0
        feedbackCooldownManager.reset()

        Timber.i("Personalized feedback manager reset for new session")
    }

    /**
     * Get user profile for external access
     */
    fun getUserProfile(): UserProfile = userProfile

    /**
     * Get feedback effectiveness metrics
     */
    fun getFeedbackMetrics(): FeedbackMetrics {
        val recentHistory = feedbackHistory.takeLast(50)

        return FeedbackMetrics(
            totalFeedbackDelivered = feedbackHistory.size,
            averageEffectiveness = recentHistory.map { it.effectivenessScore }.average().toFloat(),
            preferredModality = recentHistory.groupBy { it.modalityUsed }
                .maxByOrNull { it.value.size }?.key ?: FeedbackModality.MIXED,
            userResponsiveness = calculateUserResponsiveness(),
            adaptationSuccess = calculateAdaptationSuccess()
        )
    }

    data class FeedbackMetrics(
        val totalFeedbackDelivered: Int,
        val averageEffectiveness: Float,
        val preferredModality: FeedbackModality,
        val userResponsiveness: UserResponsiveness,
        val adaptationSuccess: Float
    )

    private fun calculateUserResponsiveness(): UserResponsiveness {
        val recentHistory = feedbackHistory.takeLast(20)
        if (recentHistory.isEmpty()) return UserResponsiveness.MODERATE

        val avgEffectiveness = recentHistory.map { it.effectivenessScore }.average()
        val avgTimeToImprovement = recentHistory.map { it.timeToImprovement }.average()

        return when {
            avgEffectiveness > 0.8 && avgTimeToImprovement < 5000 -> UserResponsiveness.HIGHLY_RESPONSIVE
            avgEffectiveness > 0.6 && avgTimeToImprovement < 10000 -> UserResponsiveness.RESPONSIVE
            avgEffectiveness > 0.4 -> UserResponsiveness.MODERATE
            avgEffectiveness > 0.2 -> UserResponsiveness.SLOW
            else -> UserResponsiveness.NON_RESPONSIVE
        }
    }

    private fun calculateAdaptationSuccess(): Float {
        if (feedbackHistory.size < 10) return 0.5f

        val recent = feedbackHistory.takeLast(10).map { it.effectivenessScore }
        val earlier = feedbackHistory.dropLast(10).takeLast(10).map { it.effectivenessScore }

        if (earlier.isEmpty()) return recent.average().toFloat()

        val recentAvg = recent.average()
        val earlierAvg = earlier.average()

        return ((recentAvg - earlierAvg) + 1.0).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * Helper classes for feedback management
 */
private class FeedbackCooldownManager {
    private val cooldowns = mutableMapOf<PersonalizedFeedbackManager.FeedbackType, Long>()
    private var lastFeedbackTime = 0L

    fun canDeliverFeedback(
        mood: PersonalizedFeedbackManager.UserMood,
        focus: PersonalizedFeedbackManager.FocusLevel
    ): Boolean {
        val currentTime = System.currentTimeMillis()
        val minCooldown = when {
            focus == PersonalizedFeedbackManager.FocusLevel.DISTRACTED -> 10000L
            mood == PersonalizedFeedbackManager.UserMood.FRUSTRATED -> 8000L
            else -> 3000L
        }

        return currentTime - lastFeedbackTime >= minCooldown
    }

    fun recordFeedback(feedback: PersonalizedFeedbackManager.FeedbackEvent) {
        lastFeedbackTime = System.currentTimeMillis()
        cooldowns[feedback.type] = lastFeedbackTime
    }

    fun reset() {
        cooldowns.clear()
        lastFeedbackTime = 0L
    }
}

private class LearningStyleAnalyzer {
    fun updateLearningStyle(
        response: PersonalizedFeedbackManager.FeedbackResponse,
        profile: PersonalizedFeedbackManager.UserProfile
    ) {
        // Update learning style based on modality effectiveness
        // Simplified implementation
    }
}

private class MotivationProfiler {
    fun updateMotivationProfile(
        performance: PersonalizedFeedbackManager.PerformanceSnapshot,
        profile: PersonalizedFeedbackManager.UserProfile
    ) {
        // Update motivation profile based on performance patterns
        // Simplified implementation
    }
}