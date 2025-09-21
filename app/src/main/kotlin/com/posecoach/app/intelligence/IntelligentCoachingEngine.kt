package com.posecoach.app.intelligence

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.posecoach.app.livecoach.LiveCoachManager
import com.posecoach.app.performance.PerformanceMetrics
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import kotlin.math.min

/**
 * Main intelligent coaching engine that integrates all AI coaching systems
 * and provides a unified interface for intelligent, context-aware coaching
 */
class IntelligentCoachingEngine(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val liveCoachManager: LiveCoachManager,
    private val performanceMetrics: PerformanceMetrics
) {

    // Core AI systems
    private val workoutAnalyzer = WorkoutContextAnalyzer()
    private val feedbackManager = PersonalizedFeedbackManager()
    private val interventionSystem = AdaptiveInterventionSystem()
    private val decisionEngine = CoachingDecisionEngine(lifecycleScope, performanceMetrics)
    private val behaviorPredictor = UserBehaviorPredictor()

    // Coaching state
    private val _coachingInsights = MutableSharedFlow<CoachingInsights>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val coachingInsights: SharedFlow<CoachingInsights> = _coachingInsights.asSharedFlow()

    private val _adaptiveRecommendations = MutableSharedFlow<AdaptiveRecommendation>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val adaptiveRecommendations: SharedFlow<AdaptiveRecommendation> = _adaptiveRecommendations.asSharedFlow()

    // Integration state
    private var isInitialized = false
    private var currentUserState = createDefaultUserState()
    private var sessionStartTime = 0L
    private val sessionDataBuffer = mutableListOf<SessionDataPoint>()
    private val maxBufferSize = 300 // 10 seconds at 30 fps

    // Performance monitoring
    private var totalProcessingTime = 0L
    private var totalDecisions = 0L

    data class CoachingInsights(
        val workoutContext: WorkoutContextAnalyzer.WorkoutInsights,
        val behaviorPredictions: UserBehaviorPredictor.BehaviorPrediction?,
        val riskAssessment: AdaptiveInterventionSystem.RiskAssessment?,
        val coachingDecision: CoachingDecisionEngine.CoachingDecision?,
        val overallScore: CoachingScore,
        val adaptationSuggestions: List<String>,
        val sessionMetrics: SessionMetrics,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class AdaptiveRecommendation(
        val type: RecommendationType,
        val priority: Priority,
        val content: String,
        val reasoning: String,
        val actions: List<String>,
        val expectedImpact: Float,
        val timeframe: String,
        val confidenceScore: Float,
        val targetSystems: List<String>,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class CoachingScore(
        val overall: Float,
        val formQuality: Float,
        val motivation: Float,
        val safety: Float,
        val engagement: Float,
        val progress: Float
    )

    data class SessionMetrics(
        val sessionDuration: Long,
        val totalDecisions: Long,
        val averageProcessingTime: Long,
        val userResponsiveness: Float,
        val adaptationRate: Float,
        val effectivenessScore: Float
    )

    data class SessionDataPoint(
        val timestamp: Long,
        val pose: PoseLandmarkResult,
        val workoutContext: WorkoutContextAnalyzer.WorkoutContext,
        val userState: PersonalizedFeedbackManager.UserState,
        val coachingDecision: CoachingDecisionEngine.CoachingDecision?
    )

    enum class RecommendationType {
        IMMEDIATE_ACTION, TECHNIQUE_ADJUSTMENT, MOTIVATION_BOOST,
        SAFETY_WARNING, RECOVERY_GUIDANCE, PROGRESSION_ADVICE,
        PERSONALIZATION_UPDATE, SYSTEM_OPTIMIZATION
    }

    enum class Priority {
        CRITICAL, HIGH, MEDIUM, LOW, BACKGROUND
    }

    /**
     * Initialize the intelligent coaching engine
     */
    suspend fun initialize() {
        try {
            Timber.i("Initializing Intelligent Coaching Engine")

            // Initialize all subsystems
            setupIntegrationFlows()
            setupPerformanceMonitoring()
            setupAdaptiveLearning()

            sessionStartTime = System.currentTimeMillis()
            isInitialized = true

            Timber.i("Intelligent Coaching Engine initialized successfully")

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Intelligent Coaching Engine")
            throw e
        }
    }

    /**
     * Main processing method for pose data with intelligent coaching
     */
    suspend fun processPoseWithIntelligentCoaching(
        pose: PoseLandmarkResult,
        userInput: UserInput? = null
    ) {
        if (!isInitialized) {
            Timber.w("Processing pose before initialization - initializing now")
            initialize()
        }

        val processingStart = System.nanoTime()

        try {
            // Update user state if input provided
            userInput?.let { updateUserState(it) }

            // Process through workout analyzer
            workoutAnalyzer.processPoseLandmarks(pose)
            val workoutContext = workoutAnalyzer.workoutContext.value

            // Process through decision engine for real-time coaching
            val coachingDecision = decisionEngine.processPoseInput(pose, currentUserState)

            // Generate behavior predictions periodically
            val behaviorPrediction = if (shouldGenerateBehaviorPrediction()) {
                generateBehaviorPrediction(pose, workoutContext)
            } else null

            // Process through intervention system
            interventionSystem.processPoseData(workoutContext, currentUserState)

            // Generate comprehensive coaching insights
            val insights = generateCoachingInsights(
                workoutContext = workoutAnalyzer.getWorkoutInsights(),
                behaviorPrediction = behaviorPrediction,
                coachingDecision = coachingDecision
            )

            // Emit insights
            _coachingInsights.emit(insights)

            // Store session data for learning
            addSessionDataPoint(pose, workoutContext, coachingDecision)

            // Generate adaptive recommendations
            generateAdaptiveRecommendations(insights)

            // Integrate with LiveCoach if connected
            integrateWithLiveCoach(insights, coachingDecision)

            // Update performance metrics
            val processingTime = (System.nanoTime() - processingStart) / 1_000_000L
            updatePerformanceMetrics(processingTime)

        } catch (e: Exception) {
            Timber.e(e, "Error in intelligent coaching processing")
            performanceMetrics.recordCustomMetric("intelligent_coaching_error", 1.0)
        }
    }

    private suspend fun generateBehaviorPrediction(
        pose: PoseLandmarkResult,
        workoutContext: WorkoutContextAnalyzer.WorkoutContext
    ): UserBehaviorPredictor.BehaviorPrediction? {
        return try {
            val sessionHistory = extractSessionHistory()
            val currentState = extractCurrentUserState(workoutContext)

            behaviorPredictor.predictUserBehavior(
                userId = "current_user", // In real app, would use actual user ID
                currentState = currentState,
                sessionHistory = sessionHistory,
                workoutContext = workoutContext
            )
        } catch (e: Exception) {
            Timber.e(e, "Error generating behavior prediction")
            null
        }
    }

    private fun generateCoachingInsights(
        workoutContext: WorkoutContextAnalyzer.WorkoutInsights,
        behaviorPrediction: UserBehaviorPredictor.BehaviorPrediction?,
        coachingDecision: CoachingDecisionEngine.CoachingDecision?
    ): CoachingInsights {

        val coachingScore = calculateCoachingScore(workoutContext, behaviorPrediction)
        val adaptationSuggestions = generateAdaptationSuggestions(workoutContext, behaviorPrediction)
        val sessionMetrics = calculateSessionMetrics()

        return CoachingInsights(
            workoutContext = workoutContext,
            behaviorPredictions = behaviorPrediction,
            riskAssessment = null, // Would be populated from intervention system
            coachingDecision = coachingDecision,
            overallScore = coachingScore,
            adaptationSuggestions = adaptationSuggestions,
            sessionMetrics = sessionMetrics
        )
    }

    private fun calculateCoachingScore(
        workoutContext: WorkoutContextAnalyzer.WorkoutInsights,
        behaviorPrediction: UserBehaviorPredictor.BehaviorPrediction?
    ): CoachingScore {

        val formQuality = workoutContext.formQuality?.let { quality ->
            when (quality) {
                WorkoutContextAnalyzer.FormQuality.EXCELLENT -> 1.0f
                WorkoutContextAnalyzer.FormQuality.GOOD -> 0.8f
                WorkoutContextAnalyzer.FormQuality.FAIR -> 0.6f
                WorkoutContextAnalyzer.FormQuality.POOR -> 0.4f
                WorkoutContextAnalyzer.FormQuality.DANGEROUS -> 0.2f
            }
        } ?: 0.5f

        val motivation = behaviorPrediction?.predictions?.get(
            UserBehaviorPredictor.PredictionType.MOTIVATION_LEVEL
        )?.value ?: 0.5f

        val safety = 1.0f - (behaviorPrediction?.riskFactors?.maxOfOrNull { it.probability } ?: 0.0f)

        val engagement = calculateEngagementScore(workoutContext)
        val progress = calculateProgressScore(workoutContext)

        val overall = (formQuality + motivation + safety + engagement + progress) / 5f

        return CoachingScore(
            overall = overall,
            formQuality = formQuality,
            motivation = motivation,
            safety = safety,
            engagement = engagement,
            progress = progress
        )
    }

    private fun calculateEngagementScore(workoutContext: WorkoutContextAnalyzer.WorkoutInsights): Float {
        // Simplified engagement calculation based on intensity and fatigue
        val intensityScore = when (workoutContext.intensity) {
            WorkoutContextAnalyzer.IntensityLevel.VERY_HIGH -> 1.0f
            WorkoutContextAnalyzer.IntensityLevel.HIGH -> 0.8f
            WorkoutContextAnalyzer.IntensityLevel.MODERATE -> 0.6f
            WorkoutContextAnalyzer.IntensityLevel.LOW -> 0.4f
        }

        val fatigueScore = when (workoutContext.fatigue) {
            WorkoutContextAnalyzer.FatigueLevel.FRESH -> 1.0f
            WorkoutContextAnalyzer.FatigueLevel.SLIGHT -> 0.8f
            WorkoutContextAnalyzer.FatigueLevel.MODERATE -> 0.6f
            WorkoutContextAnalyzer.FatigueLevel.TIRED -> 0.4f
            WorkoutContextAnalyzer.FatigueLevel.EXHAUSTED -> 0.2f
        }

        return (intensityScore + fatigueScore) / 2f
    }

    private fun calculateProgressScore(workoutContext: WorkoutContextAnalyzer.WorkoutInsights): Float {
        // Simplified progress calculation based on phase and current exercise
        val phaseScore = when (workoutContext.currentPhase) {
            WorkoutContextAnalyzer.WorkoutPhase.MAIN_SET -> 1.0f
            WorkoutContextAnalyzer.WorkoutPhase.WARMUP -> 0.7f
            WorkoutContextAnalyzer.WorkoutPhase.COOL_DOWN -> 0.8f
            WorkoutContextAnalyzer.WorkoutPhase.TRANSITION -> 0.6f
            WorkoutContextAnalyzer.WorkoutPhase.REST -> 0.5f
            WorkoutContextAnalyzer.WorkoutPhase.UNKNOWN -> 0.3f
        }

        return phaseScore
    }

    private fun generateAdaptationSuggestions(
        workoutContext: WorkoutContextAnalyzer.WorkoutInsights,
        behaviorPrediction: UserBehaviorPredictor.BehaviorPrediction?
    ): List<String> {
        val suggestions = mutableListOf<String>()

        // Form-based suggestions
        workoutContext.formQuality?.let { quality ->
            when (quality) {
                WorkoutContextAnalyzer.FormQuality.POOR -> {
                    suggestions.add("Consider slowing down to focus on proper form")
                }
                WorkoutContextAnalyzer.FormQuality.DANGEROUS -> {
                    suggestions.add("Immediate form correction needed to prevent injury")
                }
                WorkoutContextAnalyzer.FormQuality.EXCELLENT -> {
                    suggestions.add("Excellent form! Consider increasing intensity")
                }
                else -> {}
            }
        }

        // Fatigue-based suggestions
        when (workoutContext.fatigue) {
            WorkoutContextAnalyzer.FatigueLevel.TIRED -> {
                suggestions.add("Consider reducing intensity or taking a break")
            }
            WorkoutContextAnalyzer.FatigueLevel.EXHAUSTED -> {
                suggestions.add("Time for cool-down and recovery")
            }
            else -> {}
        }

        // Behavior prediction suggestions
        behaviorPrediction?.recommendedActions?.forEach { action ->
            suggestions.add(action.action)
        }

        return suggestions
    }

    private suspend fun generateAdaptiveRecommendations(insights: CoachingInsights) {
        try {
            val recommendations = mutableListOf<AdaptiveRecommendation>()

            // Safety recommendations
            if (insights.overallScore.safety < 0.6f) {
                recommendations.add(
                    AdaptiveRecommendation(
                        type = RecommendationType.SAFETY_WARNING,
                        priority = Priority.CRITICAL,
                        content = "Safety risk detected - immediate attention needed",
                        reasoning = "Low safety score: ${insights.overallScore.safety}",
                        actions = listOf("Pause workout", "Check form", "Reduce intensity"),
                        expectedImpact = 0.9f,
                        timeframe = "immediate",
                        confidenceScore = 0.9f,
                        targetSystems = listOf("intervention_system", "live_coach")
                    )
                )
            }

            // Motivation recommendations
            insights.behaviorPredictions?.predictions?.get(
                UserBehaviorPredictor.PredictionType.MOTIVATION_LEVEL
            )?.let { motivation ->
                if (motivation.value < 0.4f) {
                    recommendations.add(
                        AdaptiveRecommendation(
                            type = RecommendationType.MOTIVATION_BOOST,
                            priority = Priority.HIGH,
                            content = "Motivation boost needed",
                            reasoning = "Low motivation predicted: ${(motivation.value * 100).toInt()}%",
                            actions = listOf("Provide encouragement", "Adjust goals", "Add variety"),
                            expectedImpact = 0.7f,
                            timeframe = "next_few_minutes",
                            confidenceScore = motivation.confidence,
                            targetSystems = listOf("feedback_manager", "live_coach")
                        )
                    )
                }
            }

            // Form improvement recommendations
            if (insights.overallScore.formQuality < 0.7f) {
                recommendations.add(
                    AdaptiveRecommendation(
                        type = RecommendationType.TECHNIQUE_ADJUSTMENT,
                        priority = Priority.MEDIUM,
                        content = "Form improvement opportunity",
                        reasoning = "Form quality below optimal: ${insights.overallScore.formQuality}",
                        actions = listOf("Provide form feedback", "Slow down pace", "Focus on technique"),
                        expectedImpact = 0.6f,
                        timeframe = "current_set",
                        confidenceScore = 0.8f,
                        targetSystems = listOf("feedback_manager", "workout_analyzer")
                    )
                )
            }

            // Performance optimization recommendations
            if (insights.sessionMetrics.effectivenessScore < 0.7f) {
                recommendations.add(
                    AdaptiveRecommendation(
                        type = RecommendationType.SYSTEM_OPTIMIZATION,
                        priority = Priority.LOW,
                        content = "System performance optimization needed",
                        reasoning = "Low effectiveness score: ${insights.sessionMetrics.effectivenessScore}",
                        actions = listOf("Adjust parameters", "Retrain models", "Optimize algorithms"),
                        expectedImpact = 0.5f,
                        timeframe = "next_session",
                        confidenceScore = 0.7f,
                        targetSystems = listOf("decision_engine", "behavior_predictor")
                    )
                )
            }

            // Emit recommendations
            recommendations.forEach { recommendation ->
                _adaptiveRecommendations.emit(recommendation)
            }

        } catch (e: Exception) {
            Timber.e(e, "Error generating adaptive recommendations")
        }
    }

    private suspend fun integrateWithLiveCoach(
        insights: CoachingInsights,
        coachingDecision: CoachingDecisionEngine.CoachingDecision?
    ) {
        try {
            if (liveCoachManager.isRecording()) {
                // Send intelligent insights to Live Coach for enhanced coaching
                val enhancedContext = createEnhancedCoachingContext(insights, coachingDecision)
                // This would be integrated with the Live Coach system
                Timber.d("Integrating insights with Live Coach: ${enhancedContext.summary}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error integrating with Live Coach")
        }
    }

    private fun createEnhancedCoachingContext(
        insights: CoachingInsights,
        coachingDecision: CoachingDecisionEngine.CoachingDecision?
    ): EnhancedCoachingContext {
        return EnhancedCoachingContext(
            summary = "Intelligent coaching analysis complete",
            formQuality = insights.overallScore.formQuality,
            motivationLevel = insights.overallScore.motivation,
            safetyScore = insights.overallScore.safety,
            recommendations = insights.adaptationSuggestions,
            urgentActions = coachingDecision?.actions?.filter {
                it.actionType == CoachingDecisionEngine.CoachingActionType.IMMEDIATE_FEEDBACK
            }?.map { it.parameters["message"]?.toString() ?: "" } ?: emptyList()
        )
    }

    /**
     * Update user state based on input
     */
    fun updateUserState(userInput: UserInput) {
        currentUserState = currentUserState.copy(
            currentMood = userInput.mood ?: currentUserState.currentMood,
            energyLevel = userInput.energyLevel ?: currentUserState.energyLevel,
            focusLevel = userInput.focusLevel ?: currentUserState.focusLevel,
            timeInSession = System.currentTimeMillis() - sessionStartTime
        )
    }

    /**
     * Provide feedback on coaching effectiveness
     */
    suspend fun provideFeedback(
        decisionId: String,
        effectiveness: Float,
        userResponse: String
    ) {
        try {
            // Update decision engine
            decisionEngine.updateDecisionFeedback(
                decisionId = decisionId,
                effectiveness = effectiveness,
                userResponse = userResponse,
                timeToResponse = 5000L // Simplified
            )

            // Update behavior predictor with actual outcomes
            val features = extractCurrentFeatures()
            val outcomes = mapOf(
                "effectiveness" to effectiveness,
                "user_satisfaction" to if (effectiveness > 0.7f) 1f else 0f
            )

            behaviorPredictor.updateModels(
                userId = "current_user",
                actualOutcome = outcomes,
                features = features,
                sessionContext = createSessionContext()
            )

            Timber.d("Coaching feedback processed: $decisionId (effectiveness: $effectiveness)")

        } catch (e: Exception) {
            Timber.e(e, "Error processing coaching feedback")
        }
    }

    /**
     * Get comprehensive coaching analytics
     */
    fun getCoachingAnalytics(): CoachingAnalytics {
        return CoachingAnalytics(
            sessionDuration = if (sessionStartTime > 0) {
                System.currentTimeMillis() - sessionStartTime
            } else 0L,
            totalDecisions = totalDecisions,
            averageProcessingTime = if (totalDecisions > 0) totalProcessingTime / totalDecisions else 0L,
            systemMetrics = decisionEngine.getSystemMetrics(),
            behaviorModelPerformance = behaviorPredictor.getModelPerformance(),
            overallEffectiveness = calculateOverallEffectiveness(),
            adaptationInsights = generateAdaptationInsights()
        )
    }

    data class CoachingAnalytics(
        val sessionDuration: Long,
        val totalDecisions: Long,
        val averageProcessingTime: Long,
        val systemMetrics: CoachingDecisionEngine.SystemMetrics,
        val behaviorModelPerformance: UserBehaviorPredictor.ModelPerformanceSummary,
        val overallEffectiveness: Float,
        val adaptationInsights: List<String>
    )

    private fun calculateOverallEffectiveness(): Float {
        // Simplified effectiveness calculation
        val sessionData = sessionDataBuffer.takeLast(50)
        if (sessionData.isEmpty()) return 0.5f

        val successfulDecisions = sessionData.count { it.coachingDecision != null }
        return successfulDecisions.toFloat() / sessionData.size
    }

    private fun generateAdaptationInsights(): List<String> {
        return listOf(
            "System performance is optimal",
            "User engagement is high",
            "Behavior predictions are accurate",
            "Coaching decisions are effective"
        )
    }

    /**
     * Optimize system performance based on current metrics
     */
    suspend fun optimizePerformance() {
        try {
            // Optimize decision engine
            decisionEngine.optimizePerformance()

            // Optimize behavior predictor if needed
            val modelPerformance = behaviorPredictor.getModelPerformance()
            if (modelPerformance.averageAccuracy < 0.7f) {
                Timber.i("Behavior model performance below threshold, enabling online learning")
                behaviorPredictor.setOnlineLearningEnabled(true)
            }

            // Optimize buffer sizes if memory usage is high
            if (sessionDataBuffer.size > maxBufferSize * 0.8) {
                val toRemove = sessionDataBuffer.size - maxBufferSize / 2
                repeat(toRemove) {
                    if (sessionDataBuffer.isNotEmpty()) {
                        sessionDataBuffer.removeAt(0)
                    }
                }
                Timber.d("Optimized session data buffer size")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error optimizing performance")
        }
    }

    /**
     * Reset all systems for new session
     */
    suspend fun resetSession() {
        try {
            // Reset all subsystems
            workoutAnalyzer.resetSession()
            feedbackManager.resetSession()
            interventionSystem.resetSession()
            decisionEngine.resetSession()

            // Reset session state
            sessionStartTime = System.currentTimeMillis()
            sessionDataBuffer.clear()
            currentUserState = createDefaultUserState()
            totalProcessingTime = 0L
            totalDecisions = 0L

            Timber.i("Intelligent Coaching Engine reset for new session")

        } catch (e: Exception) {
            Timber.e(e, "Error resetting Intelligent Coaching Engine")
        }
    }

    // Helper methods and setup
    private fun setupIntegrationFlows() {
        // Setup cross-system communication flows
        lifecycleScope.launch {
            // Listen to intervention events and coordinate with other systems
            interventionSystem.interventions.collect { intervention ->
                handleInterventionEvent(intervention)
            }
        }

        lifecycleScope.launch {
            // Listen to coaching decisions and execute actions
            decisionEngine.coachingDecisions.collect { decision ->
                handleCoachingDecision(decision)
            }
        }
    }

    private suspend fun handleInterventionEvent(intervention: AdaptiveInterventionSystem.InterventionEvent) {
        try {
            // Coordinate intervention with other systems
            when (intervention.type) {
                AdaptiveInterventionSystem.InterventionType.SAFETY_ALERT -> {
                    // High priority - coordinate with live coach immediately
                    if (liveCoachManager.isRecording()) {
                        // Send alert to live coach
                        Timber.i("Coordinating safety alert with Live Coach")
                    }
                }
                AdaptiveInterventionSystem.InterventionType.FORM_CORRECTION -> {
                    // Update feedback manager with specific form guidance
                    Timber.d("Coordinating form correction with feedback systems")
                }
                else -> {
                    // Standard coordination
                    Timber.d("Coordinating intervention: ${intervention.type}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling intervention event")
        }
    }

    private suspend fun handleCoachingDecision(decision: CoachingDecisionEngine.CoachingDecision) {
        try {
            // Execute coaching decision actions
            decision.actions.forEach { action ->
                when (action.actionType) {
                    CoachingDecisionEngine.CoachingActionType.IMMEDIATE_FEEDBACK -> {
                        // Provide immediate feedback through appropriate channels
                        Timber.d("Executing immediate feedback: ${decision.content.primaryMessage}")
                    }
                    CoachingDecisionEngine.CoachingActionType.TRIGGER_ALERT -> {
                        // Trigger system alert
                        Timber.i("Triggering system alert: ${decision.content.primaryMessage}")
                    }
                    CoachingDecisionEngine.CoachingActionType.UPDATE_PROFILE -> {
                        // Update user profile based on decision
                        Timber.d("Updating user profile based on coaching decision")
                    }
                    else -> {
                        Timber.d("Executing coaching action: ${action.actionType}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling coaching decision")
        }
    }

    private fun setupPerformanceMonitoring() {
        lifecycleScope.launch {
            // Monitor system performance and adapt
            decisionEngine.performanceInsights.collect { insight ->
                if (insight.averageLatency > 80L) {
                    Timber.w("Decision latency high: ${insight.averageLatency}ms - optimizing")
                    optimizePerformance()
                }
            }
        }
    }

    private fun setupAdaptiveLearning() {
        lifecycleScope.launch {
            // Monitor behavior predictions and adapt models
            behaviorPredictor.behaviorPredictions.collect { prediction ->
                // Use predictions to enhance other systems
                adaptSystemsBasedOnPredictions(prediction)
            }
        }
    }

    private suspend fun adaptSystemsBasedOnPredictions(prediction: UserBehaviorPredictor.BehaviorPrediction) {
        try {
            // Adapt feedback frequency based on motivation
            prediction.predictions[UserBehaviorPredictor.PredictionType.MOTIVATION_LEVEL]?.let { motivation ->
                if (motivation.value < 0.4f) {
                    // Increase feedback frequency for low motivation
                    Timber.d("Adapting feedback frequency for low motivation")
                }
            }

            // Adapt intervention sensitivity based on adherence
            prediction.predictions[UserBehaviorPredictor.PredictionType.ADHERENCE_LIKELIHOOD]?.let { adherence ->
                if (adherence.value < 0.5f) {
                    // Reduce intervention threshold to prevent dropout
                    Timber.d("Adapting intervention sensitivity for low adherence risk")
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error adapting systems based on predictions")
        }
    }

    // Helper data structures and methods
    private fun shouldGenerateBehaviorPrediction(): Boolean {
        // Generate predictions every 30 seconds or after significant state changes
        return sessionDataBuffer.size % 30 == 0
    }

    private fun addSessionDataPoint(
        pose: PoseLandmarkResult,
        workoutContext: WorkoutContextAnalyzer.WorkoutContext,
        coachingDecision: CoachingDecisionEngine.CoachingDecision?
    ) {
        val dataPoint = SessionDataPoint(
            timestamp = System.currentTimeMillis(),
            pose = pose,
            workoutContext = workoutContext,
            userState = currentUserState,
            coachingDecision = coachingDecision
        )

        sessionDataBuffer.add(dataPoint)

        if (sessionDataBuffer.size > maxBufferSize) {
            sessionDataBuffer.removeAt(0)
        }
    }

    private fun updatePerformanceMetrics(processingTime: Long) {
        totalProcessingTime += processingTime
        totalDecisions++

        if (processingTime > 100L) {
            performanceMetrics.recordCustomMetric("intelligent_coaching_latency_violation", processingTime.toDouble())
        }
    }

    private fun extractSessionHistory(): List<UserBehaviorPredictor.SessionData> {
        // Convert session data buffer to behavior predictor format
        return sessionDataBuffer.mapNotNull { dataPoint ->
            dataPoint.coachingDecision?.let {
                UserBehaviorPredictor.SessionData(
                    timestamp = dataPoint.timestamp,
                    duration = dataPoint.timestamp - sessionStartTime,
                    performanceScore = 0.8f, // Simplified
                    completed = true,
                    workoutType = dataPoint.workoutContext.phase.name,
                    intensityLevel = when (dataPoint.workoutContext.intensityLevel) {
                        WorkoutContextAnalyzer.IntensityLevel.LOW -> 0.25f
                        WorkoutContextAnalyzer.IntensityLevel.MODERATE -> 0.5f
                        WorkoutContextAnalyzer.IntensityLevel.HIGH -> 0.75f
                        WorkoutContextAnalyzer.IntensityLevel.VERY_HIGH -> 1.0f
                    }
                )
            }
        }
    }

    private fun extractCurrentUserState(workoutContext: WorkoutContextAnalyzer.WorkoutContext): UserBehaviorPredictor.UserCurrentState {
        return UserBehaviorPredictor.UserCurrentState(
            energyLevel = when (currentUserState.energyLevel) {
                PersonalizedFeedbackManager.EnergyLevel.VERY_LOW -> 0.1f
                PersonalizedFeedbackManager.EnergyLevel.LOW -> 0.3f
                PersonalizedFeedbackManager.EnergyLevel.MODERATE -> 0.5f
                PersonalizedFeedbackManager.EnergyLevel.HIGH -> 0.7f
                PersonalizedFeedbackManager.EnergyLevel.VERY_HIGH -> 0.9f
            },
            moodScore = when (currentUserState.currentMood) {
                PersonalizedFeedbackManager.UserMood.ANXIOUS -> 0.2f
                PersonalizedFeedbackManager.UserMood.FRUSTRATED -> 0.3f
                PersonalizedFeedbackManager.UserMood.NEUTRAL -> 0.5f
                PersonalizedFeedbackManager.UserMood.MOTIVATED -> 0.8f
                PersonalizedFeedbackManager.UserMood.CONFIDENT -> 0.9f
                PersonalizedFeedbackManager.UserMood.TIRED -> 0.3f
                PersonalizedFeedbackManager.UserMood.FOCUSED -> 0.8f
            },
            motivationLevel = 0.7f, // Would be calculated from various factors
            fatigueLevel = when (workoutContext.fatigue) {
                WorkoutContextAnalyzer.FatigueLevel.FRESH -> 0.1f
                WorkoutContextAnalyzer.FatigueLevel.SLIGHT -> 0.3f
                WorkoutContextAnalyzer.FatigueLevel.MODERATE -> 0.5f
                WorkoutContextAnalyzer.FatigueLevel.TIRED -> 0.7f
                WorkoutContextAnalyzer.FatigueLevel.EXHAUSTED -> 0.9f
            },
            stressLevel = 0.3f, // Would be calculated from various indicators
            sleepQuality = 0.7f, // Would come from user input or wearables
            timeSinceLastWorkout = 24 * 60 * 60 * 1000L // 24 hours, simplified
        )
    }

    private fun extractCurrentFeatures(): Map<String, Float> {
        // Extract current features for model training
        return mapOf(
            "session_duration" to (System.currentTimeMillis() - sessionStartTime) / 60000f,
            "total_decisions" to totalDecisions.toFloat(),
            "energy_level" to when (currentUserState.energyLevel) {
                PersonalizedFeedbackManager.EnergyLevel.VERY_LOW -> 0.1f
                PersonalizedFeedbackManager.EnergyLevel.LOW -> 0.3f
                PersonalizedFeedbackManager.EnergyLevel.MODERATE -> 0.5f
                PersonalizedFeedbackManager.EnergyLevel.HIGH -> 0.7f
                PersonalizedFeedbackManager.EnergyLevel.VERY_HIGH -> 0.9f
            }
        )
    }

    private fun createSessionContext(): UserBehaviorPredictor.SessionContext {
        return UserBehaviorPredictor.SessionContext(
            workoutType = "general_fitness",
            duration = System.currentTimeMillis() - sessionStartTime,
            intensity = "moderate",
            timeOfDay = "afternoon", // Would be calculated from actual time
            dayOfWeek = "weekday", // Would be calculated from actual day
            environmentalFactors = mapOf(
                "location" to "home",
                "equipment" to "basic"
            )
        )
    }

    private fun createDefaultUserState(): PersonalizedFeedbackManager.UserState {
        return PersonalizedFeedbackManager.UserState(
            currentMood = PersonalizedFeedbackManager.UserMood.NEUTRAL,
            energyLevel = PersonalizedFeedbackManager.EnergyLevel.MODERATE,
            focusLevel = PersonalizedFeedbackManager.FocusLevel.MODERATE,
            sessionProgress = 0f,
            recentPerformance = 0.7f,
            timeInSession = 0L
        )
    }

    private fun calculateSessionMetrics(): SessionMetrics {
        return SessionMetrics(
            sessionDuration = if (sessionStartTime > 0) {
                System.currentTimeMillis() - sessionStartTime
            } else 0L,
            totalDecisions = totalDecisions,
            averageProcessingTime = if (totalDecisions > 0) totalProcessingTime / totalDecisions else 0L,
            userResponsiveness = 0.8f, // Would be calculated from actual responses
            adaptationRate = 0.7f, // Would be calculated from model updates
            effectivenessScore = calculateOverallEffectiveness()
        )
    }

    // Input data classes
    data class UserInput(
        val mood: PersonalizedFeedbackManager.UserMood? = null,
        val energyLevel: PersonalizedFeedbackManager.EnergyLevel? = null,
        val focusLevel: PersonalizedFeedbackManager.FocusLevel? = null,
        val goals: List<String>? = null,
        val preferences: Map<String, String>? = null
    )

    data class EnhancedCoachingContext(
        val summary: String,
        val formQuality: Float,
        val motivationLevel: Float,
        val safetyScore: Float,
        val recommendations: List<String>,
        val urgentActions: List<String>
    )

    /**
     * Get current coaching status
     */
    fun getCoachingStatus(): CoachingStatus {
        val workoutContext = workoutAnalyzer.workoutContext.value
        val systemMetrics = decisionEngine.getSystemMetrics()

        return CoachingStatus(
            isActive = isInitialized,
            currentPhase = workoutContext.phase,
            overallScore = calculateOverallEffectiveness(),
            systemHealth = if (systemMetrics.averageDecisionLatency < 100L) "healthy" else "degraded",
            userState = currentUserState,
            sessionDuration = if (sessionStartTime > 0) {
                System.currentTimeMillis() - sessionStartTime
            } else 0L,
            totalDecisions = totalDecisions
        )
    }

    data class CoachingStatus(
        val isActive: Boolean,
        val currentPhase: WorkoutContextAnalyzer.WorkoutPhase,
        val overallScore: Float,
        val systemHealth: String,
        val userState: PersonalizedFeedbackManager.UserState,
        val sessionDuration: Long,
        val totalDecisions: Long
    )
}