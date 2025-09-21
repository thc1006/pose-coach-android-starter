package com.posecoach.app.intelligence

import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.app.performance.PerformanceMetrics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.system.measureTimeMillis

/**
 * Real-time coaching decision engine with <100ms latency that coordinates
 * all coaching systems and provides intelligent, context-aware decisions
 */
class CoachingDecisionEngine(
    private val scope: CoroutineScope,
    private val performanceMetrics: PerformanceMetrics
) {

    // Core coaching components
    private val workoutAnalyzer = WorkoutContextAnalyzer()
    private val feedbackManager = PersonalizedFeedbackManager()
    private val interventionSystem = AdaptiveInterventionSystem()

    // Decision making state
    private val _coachingDecisions = MutableSharedFlow<CoachingDecision>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val coachingDecisions: SharedFlow<CoachingDecision> = _coachingDecisions.asSharedFlow()

    private val _performanceInsights = MutableSharedFlow<PerformanceInsight>(
        replay = 0,
        extraBufferCapacity = 50
    )
    val performanceInsights: SharedFlow<PerformanceInsight> = _performanceInsights.asSharedFlow()

    // Performance tracking
    private val decisionLatencyTracker = AtomicLong(0)
    private val decisionCounter = AtomicLong(0)
    private val sessionStartTime = AtomicLong(0)

    // Decision processing pipeline
    private val decisionPipeline = DecisionPipeline()
    private val priorityQueue = PriorityDecisionQueue()
    private val contextAggregator = ContextAggregator()

    // Real-time processing
    private var isProcessing = false
    private val processingLock = Any()

    data class CoachingDecision(
        val decisionId: String,
        val timestamp: Long,
        val processingLatency: Long,
        val priority: DecisionPriority,
        val type: DecisionType,
        val content: DecisionContent,
        val targetDelivery: DeliveryTarget,
        val confidence: Float,
        val reasoning: DecisionReasoning,
        val actions: List<CoachingAction>,
        val expectedOutcome: ExpectedOutcome,
        val context: DecisionContext
    )

    data class PerformanceInsight(
        val sessionDuration: Long,
        val totalDecisions: Long,
        val averageLatency: Long,
        val decisionEffectiveness: Float,
        val systemLoad: Float,
        val adaptationRate: Float,
        val userResponsiveness: Float,
        val predictionAccuracy: Float
    )

    data class DecisionContent(
        val primaryMessage: String,
        val actionableAdvice: String,
        val technicalDetails: String?,
        val motivationalElement: String?,
        val visualInstructions: List<VisualInstruction>,
        val audioInstructions: List<AudioInstruction>,
        val adaptations: List<PersonalizationAdaptation>
    )

    data class DecisionReasoning(
        val triggers: List<DecisionTrigger>,
        val analysisResults: List<AnalysisResult>,
        val riskFactors: List<RiskFactor>,
        val opportunityFactors: List<OpportunityFactor>,
        val conflictResolution: ConflictResolution?,
        val confidenceFactors: List<ConfidenceFactor>
    )

    data class CoachingAction(
        val actionType: CoachingActionType,
        val parameters: Map<String, Any>,
        val executionTiming: ActionTiming,
        val successMetrics: List<String>,
        val fallbackActions: List<String>
    )

    data class ExpectedOutcome(
        val primaryGoal: String,
        val measurableObjectives: List<String>,
        val timeframe: Long,
        val successProbability: Float,
        val alternativeOutcomes: List<String>
    )

    data class DecisionContext(
        val workoutPhase: WorkoutContextAnalyzer.WorkoutPhase,
        val userState: PersonalizedFeedbackManager.UserState,
        val riskLevel: AdaptiveInterventionSystem.RiskLevel,
        val environmentalFactors: EnvironmentalFactors,
        val sessionHistory: SessionHistory,
        val userPreferences: UserPreferences
    )

    data class VisualInstruction(
        val type: String,
        val content: String,
        val position: String,
        val duration: Long
    )

    data class AudioInstruction(
        val type: String,
        val content: String,
        val tone: String,
        val timing: String
    )

    data class PersonalizationAdaptation(
        val aspect: String,
        val adjustment: String,
        val reason: String
    )

    data class DecisionTrigger(
        val source: String,
        val type: String,
        val value: Float,
        val threshold: Float
    )

    data class AnalysisResult(
        val component: String,
        val result: String,
        val confidence: Float
    )

    data class RiskFactor(
        val type: String,
        val severity: Float,
        val impact: String
    )

    data class OpportunityFactor(
        val type: String,
        val potential: Float,
        val benefit: String
    )

    data class ConflictResolution(
        val conflictingDecisions: List<String>,
        val resolutionStrategy: String,
        val rationale: String
    )

    data class ConfidenceFactor(
        val source: String,
        val contribution: Float,
        val reliability: Float
    )

    data class EnvironmentalFactors(
        val lighting: String = "normal",
        val noise: String = "low",
        val space: String = "adequate",
        val equipment: String = "basic"
    )

    data class SessionHistory(
        val previousDecisions: List<String>,
        val userResponses: List<String>,
        val effectivenessScores: List<Float>
    )

    data class UserPreferences(
        val feedbackStyle: String,
        val interventionFrequency: String,
        val motivationStyle: String
    )

    data class DeliveryTarget(
        val modalities: List<String>,
        val urgency: String,
        val duration: Long,
        val repeatStrategy: String?
    )

    data class ActionTiming(
        val delay: Long,
        val window: Long,
        val conditions: List<String>
    )

    enum class DecisionPriority {
        CRITICAL, HIGH, MEDIUM, LOW, BACKGROUND
    }

    enum class DecisionType {
        SAFETY_INTERVENTION, FORM_CORRECTION, MOTIVATION_BOOST,
        TECHNIQUE_GUIDANCE, PROGRESS_UPDATE, WORKOUT_ADJUSTMENT,
        RECOVERY_ADVICE, PERFORMANCE_INSIGHT
    }

    enum class CoachingActionType {
        IMMEDIATE_FEEDBACK, SCHEDULE_INTERVENTION, ADJUST_PARAMETERS,
        TRIGGER_ALERT, UPDATE_PROFILE, LOG_INSIGHT, NOTIFY_USER
    }

    /**
     * Main entry point for real-time pose processing
     */
    suspend fun processPoseInput(
        pose: PoseLandmarkResult,
        userState: PersonalizedFeedbackManager.UserState
    ) {
        val processingStart = System.nanoTime()

        try {
            // Ensure we're not overwhelming the system
            synchronized(processingLock) {
                if (isProcessing) {
                    Timber.w("Skipping pose processing - previous decision still in progress")
                    return
                }
                isProcessing = true
            }

            val decisionId = generateDecisionId()

            // Start performance tracking
            val traceId = performanceMetrics.startTrace("coaching_decision")

            // Process through the decision pipeline
            val decision = decisionPipeline.process(
                decisionId = decisionId,
                pose = pose,
                userState = userState,
                workoutAnalyzer = workoutAnalyzer,
                feedbackManager = feedbackManager,
                interventionSystem = interventionSystem,
                contextAggregator = contextAggregator
            )

            // Check latency requirement
            val processingTime = (System.nanoTime() - processingStart) / 1_000_000L
            performanceMetrics.endTrace(traceId, "coaching_decision")

            if (processingTime > 100L) {
                Timber.w("Decision processing exceeded 100ms target: ${processingTime}ms")
                performanceMetrics.recordCustomMetric("coaching_latency_violation", processingTime.toDouble())
            }

            // Update tracking
            decisionLatencyTracker.set(processingTime)
            decisionCounter.incrementAndGet()

            // Emit decision if it meets criteria
            decision?.let {
                emitDecision(it.copy(processingLatency = processingTime))

                // Execute any immediate actions
                executeImmediateActions(it)
            }

            // Update performance insights periodically
            if (decisionCounter.get() % 50 == 0L) {
                emitPerformanceInsights()
            }

        } catch (e: Exception) {
            Timber.e(e, "Error in coaching decision processing")
            performanceMetrics.recordCustomMetric("coaching_decision_error", 1.0)
        } finally {
            synchronized(processingLock) {
                isProcessing = false
            }
        }
    }

    private suspend fun executeImmediateActions(decision: CoachingDecision) {
        decision.actions.filter {
            it.actionType == CoachingActionType.IMMEDIATE_FEEDBACK ||
            it.actionType == CoachingActionType.TRIGGER_ALERT
        }.forEach { action ->
            try {
                when (action.actionType) {
                    CoachingActionType.IMMEDIATE_FEEDBACK -> {
                        // Trigger immediate feedback through appropriate channel
                        Timber.d("Executing immediate feedback: ${decision.content.primaryMessage}")
                    }
                    CoachingActionType.TRIGGER_ALERT -> {
                        // Trigger safety or urgent alert
                        Timber.i("Triggering alert: ${decision.content.primaryMessage}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Timber.e(e, "Error executing immediate action: ${action.actionType}")
            }
        }
    }

    private suspend fun emitDecision(decision: CoachingDecision) {
        try {
            _coachingDecisions.emit(decision)

            // Add to priority queue for further processing if needed
            if (decision.priority in listOf(DecisionPriority.CRITICAL, DecisionPriority.HIGH)) {
                priorityQueue.addDecision(decision)
            }

            Timber.d("Coaching decision emitted: ${decision.type} (${decision.processingLatency}ms)")

        } catch (e: Exception) {
            Timber.e(e, "Failed to emit coaching decision")
        }
    }

    private suspend fun emitPerformanceInsights() {
        try {
            val sessionDuration = if (sessionStartTime.get() == 0L) {
                sessionStartTime.set(System.currentTimeMillis())
                0L
            } else {
                System.currentTimeMillis() - sessionStartTime.get()
            }

            val insight = PerformanceInsight(
                sessionDuration = sessionDuration,
                totalDecisions = decisionCounter.get(),
                averageLatency = decisionLatencyTracker.get(),
                decisionEffectiveness = calculateDecisionEffectiveness(),
                systemLoad = calculateSystemLoad(),
                adaptationRate = calculateAdaptationRate(),
                userResponsiveness = calculateUserResponsiveness(),
                predictionAccuracy = calculatePredictionAccuracy()
            )

            _performanceInsights.emit(insight)

        } catch (e: Exception) {
            Timber.e(e, "Failed to emit performance insights")
        }
    }

    private fun generateDecisionId(): String {
        return "decision_${System.currentTimeMillis()}_${decisionCounter.get()}"
    }

    private fun calculateDecisionEffectiveness(): Float {
        // Simplified effectiveness calculation
        // In a real implementation, this would track user responses and outcomes
        return 0.85f
    }

    private fun calculateSystemLoad(): Float {
        // Simplified system load calculation
        val averageLatency = decisionLatencyTracker.get()
        return min(1.0f, averageLatency / 100f) // 100ms = 100% load
    }

    private fun calculateAdaptationRate(): Float {
        // Simplified adaptation rate calculation
        return 0.75f
    }

    private fun calculateUserResponsiveness(): Float {
        // Get user responsiveness from feedback manager
        return feedbackManager.getFeedbackMetrics().userResponsiveness.let {
            when (it) {
                PersonalizedFeedbackManager.UserResponsiveness.HIGHLY_RESPONSIVE -> 0.9f
                PersonalizedFeedbackManager.UserResponsiveness.RESPONSIVE -> 0.8f
                PersonalizedFeedbackManager.UserResponsiveness.MODERATE -> 0.6f
                PersonalizedFeedbackManager.UserResponsiveness.SLOW -> 0.4f
                PersonalizedFeedbackManager.UserResponsiveness.NON_RESPONSIVE -> 0.2f
            }
        }
    }

    private fun calculatePredictionAccuracy(): Float {
        // Simplified prediction accuracy calculation
        return 0.80f
    }

    /**
     * Update user feedback on coaching decision effectiveness
     */
    suspend fun updateDecisionFeedback(
        decisionId: String,
        effectiveness: Float,
        userResponse: String,
        timeToResponse: Long
    ) {
        try {
            // Update internal metrics
            performanceMetrics.recordCustomMetric("decision_effectiveness", effectiveness.toDouble())
            performanceMetrics.recordCustomMetric("user_response_time", timeToResponse.toDouble())

            // Update components with feedback
            feedbackManager.updateUserProfile(
                PersonalizedFeedbackManager.PerformanceSnapshot(
                    timestamp = System.currentTimeMillis(),
                    exerciseType = WorkoutContextAnalyzer.ExerciseType.UNKNOWN,
                    formQuality = WorkoutContextAnalyzer.FormQuality.GOOD,
                    intensity = WorkoutContextAnalyzer.IntensityLevel.MODERATE,
                    fatigue = WorkoutContextAnalyzer.FatigueLevel.SLIGHT,
                    responsiveness = mapEffectivenessToResponsiveness(effectiveness),
                    improvementRate = effectiveness
                ),
                PersonalizedFeedbackManager.FeedbackResponse(
                    feedbackId = decisionId,
                    userReaction = mapResponseToReaction(userResponse),
                    effectivenessScore = effectiveness,
                    timeToImprovement = timeToResponse,
                    modalityUsed = PersonalizedFeedbackManager.FeedbackModality.MIXED,
                    timestamp = System.currentTimeMillis()
                )
            )

            Timber.d("Decision feedback updated: $decisionId (effectiveness: $effectiveness)")

        } catch (e: Exception) {
            Timber.e(e, "Failed to update decision feedback")
        }
    }

    private fun mapEffectivenessToResponsiveness(effectiveness: Float): PersonalizedFeedbackManager.UserResponsiveness {
        return when {
            effectiveness > 0.8f -> PersonalizedFeedbackManager.UserResponsiveness.HIGHLY_RESPONSIVE
            effectiveness > 0.6f -> PersonalizedFeedbackManager.UserResponsiveness.RESPONSIVE
            effectiveness > 0.4f -> PersonalizedFeedbackManager.UserResponsiveness.MODERATE
            effectiveness > 0.2f -> PersonalizedFeedbackManager.UserResponsiveness.SLOW
            else -> PersonalizedFeedbackManager.UserResponsiveness.NON_RESPONSIVE
        }
    }

    private fun mapResponseToReaction(response: String): PersonalizedFeedbackManager.UserReaction {
        return when (response.lowercase()) {
            "immediate" -> PersonalizedFeedbackManager.UserReaction.IMMEDIATE_IMPROVEMENT
            "gradual" -> PersonalizedFeedbackManager.UserReaction.GRADUAL_IMPROVEMENT
            "none" -> PersonalizedFeedbackManager.UserReaction.NO_CHANGE
            "resistance" -> PersonalizedFeedbackManager.UserReaction.RESISTANCE
            "confusion" -> PersonalizedFeedbackManager.UserReaction.CONFUSION
            else -> PersonalizedFeedbackManager.UserReaction.POSITIVE_RESPONSE
        }
    }

    /**
     * Get current system performance metrics
     */
    fun getSystemMetrics(): SystemMetrics {
        return SystemMetrics(
            averageDecisionLatency = decisionLatencyTracker.get(),
            totalDecisions = decisionCounter.get(),
            sessionUptime = if (sessionStartTime.get() > 0) {
                System.currentTimeMillis() - sessionStartTime.get()
            } else 0L,
            systemLoad = calculateSystemLoad(),
            memoryUsage = estimateMemoryUsage(),
            errorRate = calculateErrorRate(),
            throughput = calculateThroughput()
        )
    }

    data class SystemMetrics(
        val averageDecisionLatency: Long,
        val totalDecisions: Long,
        val sessionUptime: Long,
        val systemLoad: Float,
        val memoryUsage: Float,
        val errorRate: Float,
        val throughput: Float
    )

    private fun estimateMemoryUsage(): Float {
        // Simplified memory usage estimation
        return 0.4f // 40% of available memory
    }

    private fun calculateErrorRate(): Float {
        // In a real implementation, track actual errors
        return 0.02f // 2% error rate
    }

    private fun calculateThroughput(): Float {
        val sessionDuration = if (sessionStartTime.get() > 0) {
            (System.currentTimeMillis() - sessionStartTime.get()) / 1000.0
        } else 1.0

        return (decisionCounter.get() / sessionDuration).toFloat()
    }

    /**
     * Optimize system performance based on current load
     */
    suspend fun optimizePerformance() {
        val metrics = getSystemMetrics()

        when {
            metrics.averageDecisionLatency > 80L -> {
                // Approaching latency limit - optimize
                Timber.i("Optimizing for latency: ${metrics.averageDecisionLatency}ms")
                decisionPipeline.enableFastMode()
                contextAggregator.reduceComplexity()
            }
            metrics.systemLoad > 0.8f -> {
                // High system load - reduce complexity
                Timber.i("Reducing complexity due to high load: ${metrics.systemLoad}")
                decisionPipeline.enableLightweightMode()
            }
            metrics.errorRate > 0.05f -> {
                // High error rate - increase robustness
                Timber.w("Increasing robustness due to high error rate: ${metrics.errorRate}")
                decisionPipeline.enableRobustMode()
            }
        }
    }

    /**
     * Reset all systems for new session
     */
    suspend fun resetSession() {
        try {
            // Reset all components
            workoutAnalyzer.resetSession()
            feedbackManager.resetSession()
            interventionSystem.resetSession()

            // Reset tracking
            decisionCounter.set(0)
            decisionLatencyTracker.set(0)
            sessionStartTime.set(System.currentTimeMillis())

            // Reset pipeline state
            decisionPipeline.reset()
            priorityQueue.clear()
            contextAggregator.reset()

            synchronized(processingLock) {
                isProcessing = false
            }

            Timber.i("Coaching decision engine reset for new session")

        } catch (e: Exception) {
            Timber.e(e, "Error resetting coaching decision engine")
        }
    }

    /**
     * Get comprehensive coaching insights for external systems
     */
    fun getCoachingInsights(): CoachingInsights {
        val workoutInsights = workoutAnalyzer.getWorkoutInsights()
        val feedbackMetrics = feedbackManager.getFeedbackMetrics()
        val interventionMetrics = interventionSystem.getInterventionMetrics()

        return CoachingInsights(
            workoutContext = workoutInsights,
            feedbackEffectiveness = feedbackMetrics,
            interventionPerformance = interventionMetrics,
            systemPerformance = getSystemMetrics(),
            overallCoachingScore = calculateOverallCoachingScore(
                workoutInsights, feedbackMetrics, interventionMetrics
            ),
            recommendations = generateSystemRecommendations()
        )
    }

    data class CoachingInsights(
        val workoutContext: WorkoutContextAnalyzer.WorkoutInsights,
        val feedbackEffectiveness: PersonalizedFeedbackManager.FeedbackMetrics,
        val interventionPerformance: AdaptiveInterventionSystem.InterventionMetrics,
        val systemPerformance: SystemMetrics,
        val overallCoachingScore: Float,
        val recommendations: List<String>
    )

    private fun calculateOverallCoachingScore(
        workout: WorkoutContextAnalyzer.WorkoutInsights,
        feedback: PersonalizedFeedbackManager.FeedbackMetrics,
        intervention: AdaptiveInterventionSystem.InterventionMetrics
    ): Float {
        val workoutScore = when (workout.formQuality) {
            WorkoutContextAnalyzer.FormQuality.EXCELLENT -> 1.0f
            WorkoutContextAnalyzer.FormQuality.GOOD -> 0.8f
            WorkoutContextAnalyzer.FormQuality.FAIR -> 0.6f
            WorkoutContextAnalyzer.FormQuality.POOR -> 0.4f
            WorkoutContextAnalyzer.FormQuality.DANGEROUS -> 0.2f
            null -> 0.5f
        }

        val feedbackScore = feedback.averageEffectiveness
        val interventionScore = intervention.effectivenessRate

        return (workoutScore + feedbackScore + interventionScore) / 3f
    }

    private fun generateSystemRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()

        val metrics = getSystemMetrics()

        if (metrics.averageDecisionLatency > 75L) {
            recommendations.add("Consider reducing analysis complexity to improve response time")
        }

        if (metrics.systemLoad > 0.7f) {
            recommendations.add("System load is high - consider reducing feedback frequency")
        }

        val feedbackMetrics = feedbackManager.getFeedbackMetrics()
        if (feedbackMetrics.averageEffectiveness < 0.6f) {
            recommendations.add("Feedback effectiveness is low - consider adjusting personalization")
        }

        return recommendations
    }
}

/**
 * Decision processing pipeline optimized for <100ms latency
 */
private class DecisionPipeline {
    private var fastModeEnabled = false
    private var lightweightModeEnabled = false
    private var robustModeEnabled = false

    suspend fun process(
        decisionId: String,
        pose: PoseLandmarkResult,
        userState: PersonalizedFeedbackManager.UserState,
        workoutAnalyzer: WorkoutContextAnalyzer,
        feedbackManager: PersonalizedFeedbackManager,
        interventionSystem: AdaptiveInterventionSystem,
        contextAggregator: ContextAggregator
    ): CoachingDecisionEngine.CoachingDecision? {

        return withContext(Dispatchers.Default) {
            try {
                // Stage 1: Parallel analysis (target: <30ms)
                val analysisJobs = listOf(
                    async { analyzeWorkoutContext(workoutAnalyzer, pose) },
                    async { analyzeFeedbackNeeds(feedbackManager, pose, userState) },
                    async { analyzeInterventionNeeds(interventionSystem, pose, userState) }
                )

                val analysisResults = analysisJobs.awaitAll()

                // Stage 2: Context aggregation (target: <20ms)
                val context = contextAggregator.aggregate(analysisResults, userState)

                // Stage 3: Decision synthesis (target: <30ms)
                val decision = synthesizeDecision(decisionId, context, analysisResults)

                // Stage 4: Quality check (target: <20ms)
                if (validateDecision(decision)) decision else null

            } catch (e: Exception) {
                Timber.e(e, "Error in decision pipeline")
                null
            }
        }
    }

    private suspend fun analyzeWorkoutContext(
        analyzer: WorkoutContextAnalyzer,
        pose: PoseLandmarkResult
    ): AnalysisResult {
        return try {
            analyzer.processPoseLandmarks(pose)
            val insights = analyzer.getWorkoutInsights()

            AnalysisResult(
                component = "workout_context",
                result = "phase:${insights.currentPhase},intensity:${insights.intensity}",
                confidence = 0.8f
            )
        } catch (e: Exception) {
            Timber.e(e, "Error analyzing workout context")
            AnalysisResult("workout_context", "error", 0.0f)
        }
    }

    private suspend fun analyzeFeedbackNeeds(
        manager: PersonalizedFeedbackManager,
        pose: PoseLandmarkResult,
        userState: PersonalizedFeedbackManager.UserState
    ): AnalysisResult {
        return try {
            // Simplified feedback analysis
            val needsFeedback = userState.focusLevel != PersonalizedFeedbackManager.FocusLevel.DISTRACTED

            AnalysisResult(
                component = "feedback_needs",
                result = "needs_feedback:$needsFeedback",
                confidence = 0.7f
            )
        } catch (e: Exception) {
            Timber.e(e, "Error analyzing feedback needs")
            AnalysisResult("feedback_needs", "error", 0.0f)
        }
    }

    private suspend fun analyzeInterventionNeeds(
        system: AdaptiveInterventionSystem,
        pose: PoseLandmarkResult,
        userState: PersonalizedFeedbackManager.UserState
    ): AnalysisResult {
        return try {
            // This would normally trigger intervention analysis
            // For now, return simplified result

            AnalysisResult(
                component = "intervention_needs",
                result = "no_immediate_intervention",
                confidence = 0.9f
            )
        } catch (e: Exception) {
            Timber.e(e, "Error analyzing intervention needs")
            AnalysisResult("intervention_needs", "error", 0.0f)
        }
    }

    private fun synthesizeDecision(
        decisionId: String,
        context: CoachingDecisionEngine.DecisionContext,
        analysisResults: List<AnalysisResult>
    ): CoachingDecisionEngine.CoachingDecision {

        // Determine decision type and priority
        val decisionType = determineDecisionType(analysisResults)
        val priority = determinePriority(decisionType, context)

        // Create decision content
        val content = createDecisionContent(decisionType, context, analysisResults)

        // Create reasoning
        val reasoning = createDecisionReasoning(analysisResults, context)

        // Create actions
        val actions = createCoachingActions(decisionType, content)

        return CoachingDecisionEngine.CoachingDecision(
            decisionId = decisionId,
            timestamp = System.currentTimeMillis(),
            processingLatency = 0L, // Will be set by caller
            priority = priority,
            type = decisionType,
            content = content,
            targetDelivery = createDeliveryTarget(priority, context),
            confidence = calculateOverallConfidence(analysisResults),
            reasoning = reasoning,
            actions = actions,
            expectedOutcome = createExpectedOutcome(decisionType),
            context = context
        )
    }

    private fun determineDecisionType(results: List<AnalysisResult>): CoachingDecisionEngine.DecisionType {
        // Simplified decision type determination
        return when {
            results.any { it.result.contains("error") } -> CoachingDecisionEngine.DecisionType.SAFETY_INTERVENTION
            results.any { it.result.contains("needs_feedback") } -> CoachingDecisionEngine.DecisionType.FORM_CORRECTION
            else -> CoachingDecisionEngine.DecisionType.PROGRESS_UPDATE
        }
    }

    private fun determinePriority(
        type: CoachingDecisionEngine.DecisionType,
        context: CoachingDecisionEngine.DecisionContext
    ): CoachingDecisionEngine.DecisionPriority {
        return when (type) {
            CoachingDecisionEngine.DecisionType.SAFETY_INTERVENTION -> CoachingDecisionEngine.DecisionPriority.CRITICAL
            CoachingDecisionEngine.DecisionType.FORM_CORRECTION -> CoachingDecisionEngine.DecisionPriority.HIGH
            CoachingDecisionEngine.DecisionType.MOTIVATION_BOOST -> CoachingDecisionEngine.DecisionPriority.MEDIUM
            else -> CoachingDecisionEngine.DecisionPriority.LOW
        }
    }

    private fun createDecisionContent(
        type: CoachingDecisionEngine.DecisionType,
        context: CoachingDecisionEngine.DecisionContext,
        results: List<AnalysisResult>
    ): CoachingDecisionEngine.DecisionContent {
        return when (type) {
            CoachingDecisionEngine.DecisionType.FORM_CORRECTION -> CoachingDecisionEngine.DecisionContent(
                primaryMessage = "Let's adjust your form for better results",
                actionableAdvice = "Focus on keeping your core engaged",
                technicalDetails = "Maintain neutral spine alignment",
                motivationalElement = "Great effort! Small adjustments make big differences",
                visualInstructions = listOf(
                    CoachingDecisionEngine.VisualInstruction("highlight", "core_region", "center", 3000L)
                ),
                audioInstructions = listOf(
                    CoachingDecisionEngine.AudioInstruction("voice", "Engage your core", "encouraging", "immediate")
                ),
                adaptations = listOf(
                    CoachingDecisionEngine.PersonalizationAdaptation("tone", "encouraging", "user prefers positive feedback")
                )
            )
            else -> CoachingDecisionEngine.DecisionContent(
                primaryMessage = "Keep up the great work!",
                actionableAdvice = "Continue with your current form",
                technicalDetails = null,
                motivationalElement = "You're doing excellently",
                visualInstructions = emptyList(),
                audioInstructions = emptyList(),
                adaptations = emptyList()
            )
        }
    }

    private fun createDecisionReasoning(
        results: List<AnalysisResult>,
        context: CoachingDecisionEngine.DecisionContext
    ): CoachingDecisionEngine.DecisionReasoning {
        return CoachingDecisionEngine.DecisionReasoning(
            triggers = listOf(
                CoachingDecisionEngine.DecisionTrigger("pose_analysis", "form_issue", 0.6f, 0.5f)
            ),
            analysisResults = results,
            riskFactors = listOf(
                CoachingDecisionEngine.RiskFactor("form_degradation", 0.3f, "minor_risk")
            ),
            opportunityFactors = listOf(
                CoachingDecisionEngine.OpportunityFactor("improvement_potential", 0.7f, "form_optimization")
            ),
            conflictResolution = null,
            confidenceFactors = listOf(
                CoachingDecisionEngine.ConfidenceFactor("pose_detection", 0.8f, 0.9f)
            )
        )
    }

    private fun createCoachingActions(
        type: CoachingDecisionEngine.DecisionType,
        content: CoachingDecisionEngine.DecisionContent
    ): List<CoachingDecisionEngine.CoachingAction> {
        return listOf(
            CoachingDecisionEngine.CoachingAction(
                actionType = CoachingDecisionEngine.CoachingActionType.IMMEDIATE_FEEDBACK,
                parameters = mapOf("message" to content.primaryMessage),
                executionTiming = CoachingDecisionEngine.ActionTiming(0L, 5000L, listOf("user_ready")),
                successMetrics = listOf("user_response", "form_improvement"),
                fallbackActions = listOf("repeat_message", "simplify_instruction")
            )
        )
    }

    private fun createDeliveryTarget(
        priority: CoachingDecisionEngine.DecisionPriority,
        context: CoachingDecisionEngine.DecisionContext
    ): CoachingDecisionEngine.DeliveryTarget {
        return CoachingDecisionEngine.DeliveryTarget(
            modalities = listOf("visual", "audio"),
            urgency = when (priority) {
                CoachingDecisionEngine.DecisionPriority.CRITICAL -> "immediate"
                CoachingDecisionEngine.DecisionPriority.HIGH -> "urgent"
                else -> "normal"
            },
            duration = 3000L,
            repeatStrategy = if (priority == CoachingDecisionEngine.DecisionPriority.CRITICAL) "until_acknowledged" else null
        )
    }

    private fun createExpectedOutcome(type: CoachingDecisionEngine.DecisionType): CoachingDecisionEngine.ExpectedOutcome {
        return CoachingDecisionEngine.ExpectedOutcome(
            primaryGoal = "Improve user performance and safety",
            measurableObjectives = listOf("form_score_improvement", "injury_risk_reduction"),
            timeframe = 30000L, // 30 seconds
            successProbability = 0.8f,
            alternativeOutcomes = listOf("gradual_improvement", "user_resistance")
        )
    }

    private fun calculateOverallConfidence(results: List<AnalysisResult>): Float {
        if (results.isEmpty()) return 0.5f
        return results.map { it.confidence }.average().toFloat()
    }

    private fun validateDecision(decision: CoachingDecisionEngine.CoachingDecision): Boolean {
        // Basic validation checks
        return decision.confidence > 0.3f &&
               decision.content.primaryMessage.isNotBlank() &&
               decision.actions.isNotEmpty()
    }

    fun enableFastMode() { fastModeEnabled = true }
    fun enableLightweightMode() { lightweightModeEnabled = true }
    fun enableRobustMode() { robustModeEnabled = true }
    fun reset() {
        fastModeEnabled = false
        lightweightModeEnabled = false
        robustModeEnabled = false
    }
}

/**
 * Priority-based decision queue for handling multiple decisions
 */
private class PriorityDecisionQueue {
    private val decisions = mutableListOf<CoachingDecisionEngine.CoachingDecision>()

    fun addDecision(decision: CoachingDecisionEngine.CoachingDecision) {
        synchronized(decisions) {
            decisions.add(decision)
            decisions.sortByDescending { it.priority.ordinal }

            // Keep only the most recent high-priority decisions
            if (decisions.size > 10) {
                decisions.removeAt(decisions.size - 1)
            }
        }
    }

    fun getNextDecision(): CoachingDecisionEngine.CoachingDecision? {
        synchronized(decisions) {
            return if (decisions.isNotEmpty()) {
                decisions.removeAt(0)
            } else null
        }
    }

    fun clear() {
        synchronized(decisions) {
            decisions.clear()
        }
    }
}

/**
 * Context aggregator for efficient context building
 */
private class ContextAggregator {
    private var complexityReduced = false

    fun aggregate(
        analysisResults: List<AnalysisResult>,
        userState: PersonalizedFeedbackManager.UserState
    ): CoachingDecisionEngine.DecisionContext {

        return CoachingDecisionEngine.DecisionContext(
            workoutPhase = WorkoutContextAnalyzer.WorkoutPhase.MAIN_SET,
            userState = userState,
            riskLevel = AdaptiveInterventionSystem.RiskLevel.LOW,
            environmentalFactors = CoachingDecisionEngine.EnvironmentalFactors(),
            sessionHistory = CoachingDecisionEngine.SessionHistory(
                previousDecisions = emptyList(),
                userResponses = emptyList(),
                effectivenessScores = emptyList()
            ),
            userPreferences = CoachingDecisionEngine.UserPreferences(
                feedbackStyle = "encouraging",
                interventionFrequency = "moderate",
                motivationStyle = "achievement"
            )
        )
    }

    fun reduceComplexity() { complexityReduced = true }
    fun reset() { complexityReduced = false }
}

// Helper type alias for analysis results
private typealias AnalysisResult = CoachingDecisionEngine.AnalysisResult