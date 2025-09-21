package com.posecoach.app.intelligence

import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import kotlin.math.*

/**
 * Adaptive intervention system that provides proactive form correction
 * and injury prevention through intelligent movement analysis
 */
class AdaptiveInterventionSystem {

    private val _interventions = MutableSharedFlow<InterventionEvent>(
        replay = 0,
        extraBufferCapacity = 30
    )
    val interventions: SharedFlow<InterventionEvent> = _interventions.asSharedFlow()

    private val _riskAssessments = MutableSharedFlow<RiskAssessment>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val riskAssessments: SharedFlow<RiskAssessment> = _riskAssessments.asSharedFlow()

    // Risk analysis components
    private val injuryPredictor = InjuryPredictor()
    private val formAnalyzer = FormAnalyzer()
    private val progressiveOverloadMonitor = ProgressiveOverloadMonitor()
    private val recoveryAdviser = RecoveryAdviser()

    // Movement tracking state
    private val movementBuffer = mutableListOf<MovementFrame>()
    private val riskHistory = mutableListOf<RiskMetrics>()
    private val interventionHistory = mutableListOf<InterventionRecord>()
    private val maxBufferSize = 150 // 5 seconds at 30 fps

    // User adaptation tracking
    private var userAdaptationProfile = UserAdaptationProfile()
    private var currentRiskLevel = RiskLevel.LOW
    private var sessionStartTime = 0L

    data class InterventionEvent(
        val type: InterventionType,
        val priority: InterventionPriority,
        val triggerReason: String,
        val recommendedAction: RecommendedAction,
        val targetBodyParts: List<BodyRegion>,
        val timeWindow: TimeWindow,
        val preventativeMessage: String,
        val correctiveSteps: List<CorrectiveStep>,
        val riskMetrics: RiskMetrics,
        val confidenceScore: Float,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class RiskAssessment(
        val overallRisk: RiskLevel,
        val specificRisks: Map<RiskCategory, Float>,
        val trendDirection: RiskTrend,
        val timeToIntervention: Long,
        val recommendedActions: List<String>,
        val bodyRegionRisks: Map<BodyRegion, Float>,
        val sessionFatigue: Float,
        val formDegradation: Float,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class MovementFrame(
        val pose: PoseLandmarkResult,
        val timestamp: Long,
        val velocity: MovementVelocity,
        val acceleration: MovementAcceleration,
        val jointAngles: Map<Joint, Float>,
        val forceEstimates: Map<BodyRegion, Float>,
        val stabilityMetrics: StabilityMetrics,
        val fatigueIndicators: FatigueIndicators
    )

    data class RiskMetrics(
        val injuryRisk: Float,
        val formQuality: Float,
        val fatigueLevel: Float,
        val overloadRisk: Float,
        val balanceRisk: Float,
        val velocityRisk: Float,
        val angleRisk: Float,
        val timestamp: Long
    )

    data class RecommendedAction(
        val actionType: ActionType,
        val description: String,
        val urgency: ActionUrgency,
        val estimatedBenefit: Float,
        val timeToComplete: Long,
        val alternatives: List<String>
    )

    data class CorrectiveStep(
        val stepNumber: Int,
        val instruction: String,
        val visualCue: String?,
        val targetMetric: String,
        val successCriteria: String,
        val timeframe: Long
    )

    data class UserAdaptationProfile(
        val responseTime: Long = 3000L,
        val adaptationRate: Float = 0.7f,
        val riskTolerance: Float = 0.3f,
        val interventionPreference: InterventionStyle = InterventionStyle.PROACTIVE,
        val recoveryRate: Float = 0.5f,
        val learningCurve: Float = 0.6f
    )

    data class MovementVelocity(
        val linear: Float = 0f,
        val angular: Float = 0f,
        val byBodyPart: Map<BodyRegion, Float> = emptyMap()
    )

    data class MovementAcceleration(
        val linear: Float = 0f,
        val angular: Float = 0f,
        val jerk: Float = 0f
    )

    data class StabilityMetrics(
        val centerOfMassStability: Float,
        val postualSway: Float,
        val balanceConfidence: Float,
        val groundReactionForce: Float
    )

    data class FatigueIndicators(
        val movementEfficiency: Float,
        val tremor: Float,
        val coordinationLoss: Float,
        val reactionTime: Float
    )

    data class InterventionRecord(
        val timestamp: Long,
        val intervention: InterventionEvent,
        val userResponse: UserResponse,
        val effectiveness: Float,
        val timeToCorrection: Long
    )

    // Enums for intervention system
    enum class InterventionType {
        INJURY_PREVENTION, FORM_CORRECTION, FATIGUE_MANAGEMENT,
        OVERLOAD_WARNING, BALANCE_ASSISTANCE, RECOVERY_GUIDANCE,
        TECHNIQUE_OPTIMIZATION, SAFETY_ALERT
    }

    enum class InterventionPriority {
        CRITICAL, HIGH, MEDIUM, LOW, ADVISORY
    }

    enum class RiskLevel {
        VERY_LOW, LOW, MODERATE, HIGH, CRITICAL
    }

    enum class RiskCategory {
        ACUTE_INJURY, OVERUSE_INJURY, POOR_FORM, EXCESSIVE_LOAD,
        BALANCE_INSTABILITY, FATIGUE, PROGRESSION_TOO_FAST
    }

    enum class RiskTrend {
        IMPROVING, STABLE, DEGRADING, RAPIDLY_DEGRADING
    }

    enum class BodyRegion {
        NECK, SHOULDERS, UPPER_BACK, LOWER_BACK, CHEST,
        LEFT_ARM, RIGHT_ARM, CORE, LEFT_HIP, RIGHT_HIP,
        LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE
    }

    enum class Joint {
        NECK, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW,
        LEFT_WRIST, RIGHT_WRIST, SPINE, LEFT_HIP, RIGHT_HIP,
        LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE
    }

    enum class ActionType {
        IMMEDIATE_STOP, PAUSE_AND_CORRECT, MODIFY_TECHNIQUE,
        REDUCE_INTENSITY, TAKE_BREAK, SWITCH_EXERCISE,
        ADD_SUPPORT, SEEK_GUIDANCE
    }

    enum class ActionUrgency {
        IMMEDIATE, WITHIN_SECONDS, WITHIN_MINUTES, NEXT_SET, NEXT_SESSION
    }

    enum class InterventionStyle {
        REACTIVE, PROACTIVE, PREDICTIVE, ADAPTIVE
    }

    enum class TimeWindow {
        IMMEDIATE, SHORT, MEDIUM, LONG, SESSION_END
    }

    enum class UserResponse {
        IMMEDIATE_COMPLIANCE, GRADUAL_IMPROVEMENT, RESISTANCE,
        CONFUSION, OVERCORRECTION, NO_RESPONSE
    }

    /**
     * Process pose data and assess intervention needs
     */
    fun processPoseData(
        pose: PoseLandmarkResult,
        workoutContext: WorkoutContextAnalyzer.WorkoutContext,
        userState: PersonalizedFeedbackManager.UserState
    ) {
        val currentTime = System.currentTimeMillis()

        if (sessionStartTime == 0L) {
            sessionStartTime = currentTime
        }

        // Create movement frame
        val movementFrame = analyzeMovement(pose, currentTime)
        addMovementFrame(movementFrame)

        // Assess current risks
        val riskMetrics = assessCurrentRisk(movementFrame, workoutContext)
        riskHistory.add(riskMetrics)

        // Update risk level
        updateRiskLevel(riskMetrics)

        // Generate risk assessment
        val riskAssessment = generateRiskAssessment(riskMetrics, workoutContext)
        emitRiskAssessment(riskAssessment)

        // Check for intervention triggers
        checkInterventionTriggers(movementFrame, riskMetrics, workoutContext, userState)

        // Trim history
        trimHistories()
    }

    private fun analyzeMovement(pose: PoseLandmarkResult, timestamp: Long): MovementFrame {
        val velocity = calculateMovementVelocity(pose)
        val acceleration = calculateMovementAcceleration(pose)
        val jointAngles = calculateJointAngles(pose)
        val forceEstimates = estimateForces(pose, velocity)
        val stability = calculateStabilityMetrics(pose)
        val fatigue = assessFatigueIndicators(pose, velocity)

        return MovementFrame(
            pose = pose,
            timestamp = timestamp,
            velocity = velocity,
            acceleration = acceleration,
            jointAngles = jointAngles,
            forceEstimates = forceEstimates,
            stabilityMetrics = stability,
            fatigueIndicators = fatigue
        )
    }

    private fun assessCurrentRisk(
        frame: MovementFrame,
        workoutContext: WorkoutContextAnalyzer.WorkoutContext
    ): RiskMetrics {
        val injuryRisk = injuryPredictor.predictInjuryRisk(frame, movementBuffer)
        val formQuality = formAnalyzer.assessFormQuality(frame)
        val fatigueLevel = assessFatigueRisk(frame, workoutContext)
        val overloadRisk = progressiveOverloadMonitor.assessOverloadRisk(frame, workoutContext)
        val balanceRisk = assessBalanceRisk(frame)
        val velocityRisk = assessVelocityRisk(frame)
        val angleRisk = assessJointAngleRisk(frame)

        return RiskMetrics(
            injuryRisk = injuryRisk,
            formQuality = 1.0f - formQuality, // Invert so higher = more risk
            fatigueLevel = fatigueLevel,
            overloadRisk = overloadRisk,
            balanceRisk = balanceRisk,
            velocityRisk = velocityRisk,
            angleRisk = angleRisk,
            timestamp = frame.timestamp
        )
    }

    private fun checkInterventionTriggers(
        frame: MovementFrame,
        riskMetrics: RiskMetrics,
        workoutContext: WorkoutContextAnalyzer.WorkoutContext,
        userState: PersonalizedFeedbackManager.UserState
    ) {
        // Critical safety interventions
        if (riskMetrics.injuryRisk > 0.8f) {
            triggerInjuryPreventionIntervention(frame, riskMetrics)
        }

        // Form correction interventions
        if (riskMetrics.formQuality > 0.6f && shouldInterventForForm(riskMetrics)) {
            triggerFormCorrectionIntervention(frame, riskMetrics)
        }

        // Fatigue management interventions
        if (riskMetrics.fatigueLevel > 0.7f) {
            triggerFatigueManagementIntervention(frame, riskMetrics, workoutContext)
        }

        // Balance assistance interventions
        if (riskMetrics.balanceRisk > 0.6f) {
            triggerBalanceAssistanceIntervention(frame, riskMetrics)
        }

        // Progressive overload warnings
        if (riskMetrics.overloadRisk > 0.7f) {
            triggerOverloadWarningIntervention(frame, riskMetrics)
        }

        // Proactive technique optimization
        if (userAdaptationProfile.interventionPreference == InterventionStyle.PROACTIVE) {
            checkProactiveInterventions(frame, riskMetrics, userState)
        }
    }

    private fun triggerInjuryPreventionIntervention(frame: MovementFrame, riskMetrics: RiskMetrics) {
        val highRiskRegions = identifyHighRiskBodyRegions(frame, riskMetrics)

        val intervention = InterventionEvent(
            type = InterventionType.INJURY_PREVENTION,
            priority = InterventionPriority.CRITICAL,
            triggerReason = "High injury risk detected (${(riskMetrics.injuryRisk * 100).toInt()}%)",
            recommendedAction = RecommendedAction(
                actionType = ActionType.IMMEDIATE_STOP,
                description = "Stop immediately and reassess form",
                urgency = ActionUrgency.IMMEDIATE,
                estimatedBenefit = 0.9f,
                timeToComplete = 0L,
                alternatives = listOf("Reduce intensity significantly", "Switch to safer exercise")
            ),
            targetBodyParts = highRiskRegions,
            timeWindow = TimeWindow.IMMEDIATE,
            preventativeMessage = "Your current movement pattern poses injury risk. Let's correct this immediately.",
            correctiveSteps = generateInjuryPreventionSteps(highRiskRegions),
            riskMetrics = riskMetrics,
            confidenceScore = 0.95f
        )

        emitIntervention(intervention)
    }

    private fun triggerFormCorrectionIntervention(frame: MovementFrame, riskMetrics: RiskMetrics) {
        val formIssues = identifyFormIssues(frame)

        val intervention = InterventionEvent(
            type = InterventionType.FORM_CORRECTION,
            priority = InterventionPriority.HIGH,
            triggerReason = "Form degradation detected",
            recommendedAction = RecommendedAction(
                actionType = ActionType.PAUSE_AND_CORRECT,
                description = "Pause and focus on proper form",
                urgency = ActionUrgency.WITHIN_SECONDS,
                estimatedBenefit = 0.8f,
                timeToComplete = 5000L,
                alternatives = listOf("Reduce weight", "Perform assisted version")
            ),
            targetBodyParts = formIssues.keys.toList(),
            timeWindow = TimeWindow.SHORT,
            preventativeMessage = "Let's fine-tune your form to maximize effectiveness and safety.",
            correctiveSteps = generateFormCorrectionSteps(formIssues),
            riskMetrics = riskMetrics,
            confidenceScore = 0.8f
        )

        emitIntervention(intervention)
    }

    private fun triggerFatigueManagementIntervention(
        frame: MovementFrame,
        riskMetrics: RiskMetrics,
        workoutContext: WorkoutContextAnalyzer.WorkoutContext
    ) {
        val fatigueLevel = workoutContext.fatigue
        val restRecommendation = recoveryAdviser.recommendRest(fatigueLevel, workoutContext.sessionDuration)

        val intervention = InterventionEvent(
            type = InterventionType.FATIGUE_MANAGEMENT,
            priority = InterventionPriority.HIGH,
            triggerReason = "Fatigue affecting performance and safety",
            recommendedAction = RecommendedAction(
                actionType = ActionType.TAKE_BREAK,
                description = "Take a rest to maintain performance and prevent injury",
                urgency = ActionUrgency.WITHIN_MINUTES,
                estimatedBenefit = 0.7f,
                timeToComplete = restRecommendation.restDuration,
                alternatives = listOf("Reduce intensity by 30%", "Switch to lower-impact exercise")
            ),
            targetBodyParts = listOf(BodyRegion.CORE), // Fatigue affects whole body
            timeWindow = TimeWindow.MEDIUM,
            preventativeMessage = "Your body is showing signs of fatigue. A strategic rest will help you finish strong.",
            correctiveSteps = generateFatigueManagementSteps(restRecommendation),
            riskMetrics = riskMetrics,
            confidenceScore = 0.75f
        )

        emitIntervention(intervention)
    }

    private fun triggerBalanceAssistanceIntervention(frame: MovementFrame, riskMetrics: RiskMetrics) {
        val balanceIssues = analyzeBalanceIssues(frame)

        val intervention = InterventionEvent(
            type = InterventionType.BALANCE_ASSISTANCE,
            priority = InterventionPriority.MEDIUM,
            triggerReason = "Balance instability detected",
            recommendedAction = RecommendedAction(
                actionType = ActionType.ADD_SUPPORT,
                description = "Use support or modify stance for better balance",
                urgency = ActionUrgency.WITHIN_SECONDS,
                estimatedBenefit = 0.6f,
                timeToComplete = 2000L,
                alternatives = listOf("Widen stance", "Use wall for support", "Reduce range of motion")
            ),
            targetBodyParts = listOf(BodyRegion.CORE, BodyRegion.LEFT_ANKLE, BodyRegion.RIGHT_ANKLE),
            timeWindow = TimeWindow.SHORT,
            preventativeMessage = "Let's improve your stability for safer and more effective movement.",
            correctiveSteps = generateBalanceAssistanceSteps(balanceIssues),
            riskMetrics = riskMetrics,
            confidenceScore = 0.7f
        )

        emitIntervention(intervention)
    }

    private fun triggerOverloadWarningIntervention(frame: MovementFrame, riskMetrics: RiskMetrics) {
        val overloadIndicators = analyzeOverloadIndicators(frame)

        val intervention = InterventionEvent(
            type = InterventionType.OVERLOAD_WARNING,
            priority = InterventionPriority.MEDIUM,
            triggerReason = "Excessive load detected",
            recommendedAction = RecommendedAction(
                actionType = ActionType.REDUCE_INTENSITY,
                description = "Reduce weight or intensity to maintain safe progression",
                urgency = ActionUrgency.NEXT_SET,
                estimatedBenefit = 0.8f,
                timeToComplete = 0L,
                alternatives = listOf("Reduce reps", "Increase rest time", "Use assistance")
            ),
            targetBodyParts = overloadIndicators.keys.toList(),
            timeWindow = TimeWindow.MEDIUM,
            preventativeMessage = "You're pushing hard! Let's adjust the load to keep you progressing safely.",
            correctiveSteps = generateOverloadWarningSteps(overloadIndicators),
            riskMetrics = riskMetrics,
            confidenceScore = 0.75f
        )

        emitIntervention(intervention)
    }

    private fun checkProactiveInterventions(
        frame: MovementFrame,
        riskMetrics: RiskMetrics,
        userState: PersonalizedFeedbackManager.UserState
    ) {
        // Look for early warning signs
        val riskTrend = calculateRiskTrend()

        if (riskTrend == RiskTrend.DEGRADING) {
            val intervention = InterventionEvent(
                type = InterventionType.TECHNIQUE_OPTIMIZATION,
                priority = InterventionPriority.LOW,
                triggerReason = "Proactive technique optimization",
                recommendedAction = RecommendedAction(
                    actionType = ActionType.MODIFY_TECHNIQUE,
                    description = "Small adjustments to optimize your technique",
                    urgency = ActionUrgency.NEXT_SET,
                    estimatedBenefit = 0.5f,
                    timeToComplete = 10000L,
                    alternatives = listOf("Continue current form", "Focus on breathing")
                ),
                targetBodyParts = identifyOptimizationTargets(frame),
                timeWindow = TimeWindow.LONG,
                preventativeMessage = "Great work! Here's a small tip to make your movement even more effective.",
                correctiveSteps = generateTechniqueOptimizationSteps(frame),
                riskMetrics = riskMetrics,
                confidenceScore = 0.6f
            )

            emitIntervention(intervention)
        }
    }

    // Helper methods for movement analysis
    private fun calculateMovementVelocity(pose: PoseLandmarkResult): MovementVelocity {
        if (movementBuffer.isEmpty()) return MovementVelocity()

        val lastFrame = movementBuffer.last()
        val deltaTime = (pose.timestampMs - lastFrame.timestamp) / 1000.0

        if (deltaTime <= 0) return MovementVelocity()

        // Calculate linear velocity of center of mass
        val currentCOM = calculateCenterOfMass(pose.landmarks)
        val lastCOM = calculateCenterOfMass(lastFrame.pose.landmarks)

        val linearVelocity = sqrt(
            (currentCOM.first - lastCOM.first).pow(2) +
            (currentCOM.second - lastCOM.second).pow(2)
        ) / deltaTime

        // Calculate angular velocity (simplified)
        val angularVelocity = calculateAngularVelocity(pose, lastFrame.pose)

        // Calculate velocity by body part
        val bodyPartVelocities = calculateBodyPartVelocities(pose, lastFrame.pose, deltaTime)

        return MovementVelocity(
            linear = linearVelocity.toFloat(),
            angular = angularVelocity,
            byBodyPart = bodyPartVelocities
        )
    }

    private fun calculateMovementAcceleration(pose: PoseLandmarkResult): MovementAcceleration {
        if (movementBuffer.size < 2) return MovementAcceleration()

        val recentFrames = movementBuffer.takeLast(3)
        if (recentFrames.size < 3) return MovementAcceleration()

        // Calculate acceleration from velocity changes
        val velocities = recentFrames.map { it.velocity.linear }
        val acceleration = if (velocities.size >= 2) {
            velocities.last() - velocities[velocities.size - 2]
        } else 0f

        // Calculate jerk (rate of change of acceleration)
        val jerk = if (velocities.size >= 3) {
            val currentAccel = velocities.last() - velocities[velocities.size - 2]
            val previousAccel = velocities[velocities.size - 2] - velocities[velocities.size - 3]
            currentAccel - previousAccel
        } else 0f

        return MovementAcceleration(
            linear = acceleration,
            angular = 0f, // Simplified
            jerk = jerk
        )
    }

    private fun calculateJointAngles(pose: PoseLandmarkResult): Map<Joint, Float> {
        val landmarks = pose.landmarks
        val angles = mutableMapOf<Joint, Float>()

        // Calculate key joint angles
        angles[Joint.LEFT_ELBOW] = calculateAngle(
            landmarks[11], landmarks[13], landmarks[15] // shoulder, elbow, wrist
        )
        angles[Joint.RIGHT_ELBOW] = calculateAngle(
            landmarks[12], landmarks[14], landmarks[16]
        )
        angles[Joint.LEFT_KNEE] = calculateAngle(
            landmarks[23], landmarks[25], landmarks[27] // hip, knee, ankle
        )
        angles[Joint.RIGHT_KNEE] = calculateAngle(
            landmarks[24], landmarks[26], landmarks[28]
        )

        return angles
    }

    private fun estimateForces(pose: PoseLandmarkResult, velocity: MovementVelocity): Map<BodyRegion, Float> {
        // Simplified force estimation based on movement characteristics
        val forces = mutableMapOf<BodyRegion, Float>()

        // Base force estimation on velocity and position
        val baseForce = velocity.linear * 10f // Simplified calculation

        forces[BodyRegion.LEFT_KNEE] = baseForce * 1.2f
        forces[BodyRegion.RIGHT_KNEE] = baseForce * 1.2f
        forces[BodyRegion.LOWER_BACK] = baseForce * 1.5f
        forces[BodyRegion.SHOULDERS] = baseForce * 0.8f

        return forces
    }

    private fun calculateStabilityMetrics(pose: PoseLandmarkResult): StabilityMetrics {
        val landmarks = pose.landmarks
        val centerOfMass = calculateCenterOfMass(landmarks)
        val baseOfSupport = calculateBaseOfSupport(landmarks)

        val stability = if (baseOfSupport > 0) {
            1.0f - min(1.0f, abs(centerOfMass.first - 0.5f) / baseOfSupport)
        } else 0.5f

        return StabilityMetrics(
            centerOfMassStability = stability,
            postualSway = calculatePosturalSway(),
            balanceConfidence = calculateBalanceConfidence(landmarks),
            groundReactionForce = estimateGroundReactionForce(landmarks)
        )
    }

    private fun assessFatigueIndicators(pose: PoseLandmarkResult, velocity: MovementVelocity): FatigueIndicators {
        val efficiency = calculateMovementEfficiency(pose, velocity)
        val tremor = detectTremor(pose)
        val coordination = assessCoordination(pose)
        val reaction = estimateReactionTime()

        return FatigueIndicators(
            movementEfficiency = efficiency,
            tremor = tremor,
            coordinationLoss = 1.0f - coordination,
            reactionTime = reaction
        )
    }

    // Risk assessment methods
    private fun assessFatigueRisk(
        frame: MovementFrame,
        workoutContext: WorkoutContextAnalyzer.WorkoutContext
    ): Float {
        val sessionDuration = workoutContext.sessionDuration / 60000.0 // minutes
        val fatigueFromTime = (sessionDuration / 60.0).coerceAtMost(1.0) // Max 1 hour

        val fatigueFromMovement = frame.fatigueIndicators.let {
            (it.coordinationLoss + (1.0f - it.movementEfficiency) + it.tremor) / 3.0f
        }

        return ((fatigueFromTime + fatigueFromMovement) / 2.0).toFloat()
    }

    private fun assessBalanceRisk(frame: MovementFrame): Float {
        return 1.0f - frame.stabilityMetrics.centerOfMassStability
    }

    private fun assessVelocityRisk(frame: MovementFrame): Float {
        val velocity = frame.velocity.linear
        return when {
            velocity > 2.0f -> 0.8f // Very fast movement
            velocity > 1.5f -> 0.6f // Fast movement
            velocity > 1.0f -> 0.4f // Moderate movement
            velocity < 0.1f -> 0.3f // Very slow (possible struggling)
            else -> 0.2f // Normal range
        }
    }

    private fun assessJointAngleRisk(frame: MovementFrame): Float {
        val angles = frame.jointAngles
        var totalRisk = 0f
        var angleCount = 0

        angles.forEach { (joint, angle) ->
            val risk = when (joint) {
                Joint.LEFT_KNEE, Joint.RIGHT_KNEE -> {
                    when {
                        angle < 90f -> 0.8f // Very deep knee bend
                        angle < 120f -> 0.4f // Moderate bend
                        angle > 160f -> 0.3f // Nearly straight
                        else -> 0.1f // Normal range
                    }
                }
                Joint.LEFT_ELBOW, Joint.RIGHT_ELBOW -> {
                    when {
                        angle < 30f -> 0.6f // Very bent
                        angle > 170f -> 0.3f // Nearly straight
                        else -> 0.1f // Normal range
                    }
                }
                else -> 0.1f
            }
            totalRisk += risk
            angleCount++
        }

        return if (angleCount > 0) totalRisk / angleCount else 0f
    }

    // Intervention generation methods
    private fun generateInjuryPreventionSteps(highRiskRegions: List<BodyRegion>): List<CorrectiveStep> {
        return listOf(
            CorrectiveStep(
                stepNumber = 1,
                instruction = "Stop the current movement immediately",
                visualCue = "Red stop signal",
                targetMetric = "Movement velocity",
                successCriteria = "Complete stillness for 3 seconds",
                timeframe = 3000L
            ),
            CorrectiveStep(
                stepNumber = 2,
                instruction = "Check your posture and alignment",
                visualCue = "Posture guide overlay",
                targetMetric = "Joint alignment",
                successCriteria = "All joints in neutral position",
                timeframe = 10000L
            ),
            CorrectiveStep(
                stepNumber = 3,
                instruction = "Resume with 50% intensity",
                visualCue = "Green go signal",
                targetMetric = "Movement intensity",
                successCriteria = "Controlled, smooth movement",
                timeframe = 30000L
            )
        )
    }

    private fun generateFormCorrectionSteps(formIssues: Map<BodyRegion, String>): List<CorrectiveStep> {
        val steps = mutableListOf<CorrectiveStep>()

        formIssues.entries.forEachIndexed { index, (region, issue) ->
            steps.add(
                CorrectiveStep(
                    stepNumber = index + 1,
                    instruction = "Focus on $region: $issue",
                    visualCue = "Highlight $region",
                    targetMetric = "Form quality",
                    successCriteria = "Proper alignment maintained",
                    timeframe = 15000L
                )
            )
        }

        return steps
    }

    private fun generateFatigueManagementSteps(restRecommendation: RestRecommendation): List<CorrectiveStep> {
        return listOf(
            CorrectiveStep(
                stepNumber = 1,
                instruction = "Take a ${restRecommendation.restDuration / 1000} second break",
                visualCue = "Rest timer",
                targetMetric = "Recovery time",
                successCriteria = "Heart rate normalized",
                timeframe = restRecommendation.restDuration
            ),
            CorrectiveStep(
                stepNumber = 2,
                instruction = "Focus on deep breathing",
                visualCue = "Breathing guide",
                targetMetric = "Breathing rate",
                successCriteria = "Slow, controlled breaths",
                timeframe = 30000L
            )
        )
    }

    private fun generateBalanceAssistanceSteps(balanceIssues: BalanceAnalysis): List<CorrectiveStep> {
        return listOf(
            CorrectiveStep(
                stepNumber = 1,
                instruction = "Widen your stance for better stability",
                visualCue = "Foot position guide",
                targetMetric = "Stance width",
                successCriteria = "Feet shoulder-width apart",
                timeframe = 5000L
            ),
            CorrectiveStep(
                stepNumber = 2,
                instruction = "Engage your core muscles",
                visualCue = "Core activation guide",
                targetMetric = "Core stability",
                successCriteria = "Steady center of mass",
                timeframe = 10000L
            )
        )
    }

    private fun generateOverloadWarningSteps(overloadIndicators: Map<BodyRegion, Float>): List<CorrectiveStep> {
        return listOf(
            CorrectiveStep(
                stepNumber = 1,
                instruction = "Reduce the load by 20-30%",
                visualCue = "Load reduction guide",
                targetMetric = "Exercise intensity",
                successCriteria = "Comfortable movement range",
                timeframe = 0L
            ),
            CorrectiveStep(
                stepNumber = 2,
                instruction = "Focus on movement quality over quantity",
                visualCue = "Quality emphasis",
                targetMetric = "Form score",
                successCriteria = "Smooth, controlled movement",
                timeframe = 60000L
            )
        )
    }

    private fun generateTechniqueOptimizationSteps(frame: MovementFrame): List<CorrectiveStep> {
        return listOf(
            CorrectiveStep(
                stepNumber = 1,
                instruction = "Slight adjustment to improve efficiency",
                visualCue = "Optimization arrow",
                targetMetric = "Movement efficiency",
                successCriteria = "5% improvement in form score",
                timeframe = 15000L
            )
        )
    }

    // Analysis helper methods
    private fun identifyHighRiskBodyRegions(frame: MovementFrame, riskMetrics: RiskMetrics): List<BodyRegion> {
        val highRiskRegions = mutableListOf<BodyRegion>()

        frame.forceEstimates.forEach { (region, force) ->
            if (force > 15f) { // High force threshold
                highRiskRegions.add(region)
            }
        }

        if (riskMetrics.balanceRisk > 0.6f) {
            highRiskRegions.addAll(listOf(BodyRegion.LEFT_ANKLE, BodyRegion.RIGHT_ANKLE, BodyRegion.CORE))
        }

        return highRiskRegions.distinct()
    }

    private fun identifyFormIssues(frame: MovementFrame): Map<BodyRegion, String> {
        val issues = mutableMapOf<BodyRegion, String>()

        // Check joint angles for form issues
        frame.jointAngles.forEach { (joint, angle) ->
            when (joint) {
                Joint.LEFT_KNEE, Joint.RIGHT_KNEE -> {
                    if (angle < 90f) {
                        val region = if (joint == Joint.LEFT_KNEE) BodyRegion.LEFT_KNEE else BodyRegion.RIGHT_KNEE
                        issues[region] = "Knee angle too acute, risk of strain"
                    }
                }
                Joint.SPINE -> {
                    if (abs(angle) > 20f) {
                        issues[BodyRegion.LOWER_BACK] = "Excessive spinal flexion/extension"
                    }
                }
                else -> {}
            }
        }

        return issues
    }

    private fun analyzeBalanceIssues(frame: MovementFrame): BalanceAnalysis {
        return BalanceAnalysis(
            stabilityScore = frame.stabilityMetrics.centerOfMassStability,
            swayAmount = frame.stabilityMetrics.postualSway,
            supportArea = calculateBaseOfSupport(frame.pose.landmarks),
            recommendations = listOf("Widen stance", "Engage core")
        )
    }

    private fun analyzeOverloadIndicators(frame: MovementFrame): Map<BodyRegion, Float> {
        return frame.forceEstimates.filter { it.value > 12f }
    }

    private fun identifyOptimizationTargets(frame: MovementFrame): List<BodyRegion> {
        // Identify areas that could benefit from small improvements
        return listOf(BodyRegion.CORE, BodyRegion.SHOULDERS)
    }

    // Utility methods
    private fun addMovementFrame(frame: MovementFrame) {
        movementBuffer.add(frame)
        if (movementBuffer.size > maxBufferSize) {
            movementBuffer.removeAt(0)
        }
    }

    private fun trimHistories() {
        if (riskHistory.size > 100) {
            riskHistory.removeAt(0)
        }
        if (interventionHistory.size > 50) {
            interventionHistory.removeAt(0)
        }
    }

    private fun updateRiskLevel(riskMetrics: RiskMetrics) {
        val overallRisk = (riskMetrics.injuryRisk + riskMetrics.formQuality +
                          riskMetrics.fatigueLevel + riskMetrics.balanceRisk) / 4f

        currentRiskLevel = when {
            overallRisk > 0.8f -> RiskLevel.CRITICAL
            overallRisk > 0.6f -> RiskLevel.HIGH
            overallRisk > 0.4f -> RiskLevel.MODERATE
            overallRisk > 0.2f -> RiskLevel.LOW
            else -> RiskLevel.VERY_LOW
        }
    }

    private fun generateRiskAssessment(
        riskMetrics: RiskMetrics,
        workoutContext: WorkoutContextAnalyzer.WorkoutContext
    ): RiskAssessment {
        val specificRisks = mapOf(
            RiskCategory.ACUTE_INJURY to riskMetrics.injuryRisk,
            RiskCategory.POOR_FORM to riskMetrics.formQuality,
            RiskCategory.FATIGUE to riskMetrics.fatigueLevel,
            RiskCategory.BALANCE_INSTABILITY to riskMetrics.balanceRisk,
            RiskCategory.EXCESSIVE_LOAD to riskMetrics.overloadRisk
        )

        val trendDirection = calculateRiskTrend()
        val bodyRegionRisks = calculateBodyRegionRisks(riskMetrics)

        return RiskAssessment(
            overallRisk = currentRiskLevel,
            specificRisks = specificRisks,
            trendDirection = trendDirection,
            timeToIntervention = calculateTimeToIntervention(riskMetrics),
            recommendedActions = generateRecommendedActions(riskMetrics),
            bodyRegionRisks = bodyRegionRisks,
            sessionFatigue = riskMetrics.fatigueLevel,
            formDegradation = riskMetrics.formQuality,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun calculateRiskTrend(): RiskTrend {
        if (riskHistory.size < 10) return RiskTrend.STABLE

        val recentRisks = riskHistory.takeLast(10).map { it.injuryRisk }
        val olderRisks = riskHistory.dropLast(10).takeLast(10).map { it.injuryRisk }

        if (olderRisks.isEmpty()) return RiskTrend.STABLE

        val recentAvg = recentRisks.average()
        val olderAvg = olderRisks.average()
        val change = recentAvg - olderAvg

        return when {
            change > 0.2 -> RiskTrend.RAPIDLY_DEGRADING
            change > 0.1 -> RiskTrend.DEGRADING
            change < -0.1 -> RiskTrend.IMPROVING
            else -> RiskTrend.STABLE
        }
    }

    private fun calculateTimeToIntervention(riskMetrics: RiskMetrics): Long {
        val maxRisk = maxOf(
            riskMetrics.injuryRisk,
            riskMetrics.formQuality,
            riskMetrics.fatigueLevel,
            riskMetrics.balanceRisk
        )

        return when {
            maxRisk > 0.8f -> 0L // Immediate
            maxRisk > 0.6f -> 5000L // 5 seconds
            maxRisk > 0.4f -> 30000L // 30 seconds
            else -> 120000L // 2 minutes
        }
    }

    private fun generateRecommendedActions(riskMetrics: RiskMetrics): List<String> {
        val actions = mutableListOf<String>()

        if (riskMetrics.injuryRisk > 0.6f) {
            actions.add("Reduce intensity to prevent injury")
        }
        if (riskMetrics.formQuality > 0.5f) {
            actions.add("Focus on proper form")
        }
        if (riskMetrics.fatigueLevel > 0.6f) {
            actions.add("Consider taking a rest break")
        }
        if (riskMetrics.balanceRisk > 0.5f) {
            actions.add("Improve stability and balance")
        }

        return actions
    }

    private fun calculateBodyRegionRisks(riskMetrics: RiskMetrics): Map<BodyRegion, Float> {
        // Simplified body region risk calculation
        return mapOf(
            BodyRegion.LOWER_BACK to riskMetrics.formQuality * 0.8f,
            BodyRegion.LEFT_KNEE to riskMetrics.overloadRisk * 0.7f,
            BodyRegion.RIGHT_KNEE to riskMetrics.overloadRisk * 0.7f,
            BodyRegion.SHOULDERS to riskMetrics.angleRisk * 0.6f,
            BodyRegion.CORE to riskMetrics.balanceRisk * 0.9f
        )
    }

    private fun shouldInterventForForm(riskMetrics: RiskMetrics): Boolean {
        // Don't intervene too frequently for form issues
        val lastFormIntervention = interventionHistory
            .filter { it.intervention.type == InterventionType.FORM_CORRECTION }
            .maxByOrNull { it.timestamp }

        return lastFormIntervention?.let {
            System.currentTimeMillis() - it.timestamp > 15000L // 15 seconds cooldown
        } ?: true
    }

    private fun emitIntervention(intervention: InterventionEvent) {
        try {
            _interventions.tryEmit(intervention)

            // Record the intervention
            interventionHistory.add(
                InterventionRecord(
                    timestamp = intervention.timestamp,
                    intervention = intervention,
                    userResponse = UserResponse.NO_RESPONSE, // Will be updated later
                    effectiveness = 0f, // Will be updated later
                    timeToCorrection = 0L // Will be updated later
                )
            )

            Timber.i("Adaptive intervention triggered: ${intervention.type} (priority: ${intervention.priority})")

        } catch (e: Exception) {
            Timber.e(e, "Failed to emit intervention")
        }
    }

    private fun emitRiskAssessment(assessment: RiskAssessment) {
        try {
            _riskAssessments.tryEmit(assessment)
        } catch (e: Exception) {
            Timber.e(e, "Failed to emit risk assessment")
        }
    }

    // Helper data classes
    data class RestRecommendation(
        val restDuration: Long,
        val restType: String,
        val reason: String
    )

    data class BalanceAnalysis(
        val stabilityScore: Float,
        val swayAmount: Float,
        val supportArea: Float,
        val recommendations: List<String>
    )

    // Simplified implementations of complex calculations
    private fun calculateCenterOfMass(landmarks: List<PoseLandmarkResult.Landmark>): Pair<Float, Float> {
        val torsoLandmarks = listOf(11, 12, 23, 24) // shoulders and hips
        val x = torsoLandmarks.map { landmarks[it].x }.average().toFloat()
        val y = torsoLandmarks.map { landmarks[it].y }.average().toFloat()
        return Pair(x, y)
    }

    private fun calculateBaseOfSupport(landmarks: List<PoseLandmarkResult.Landmark>): Float {
        val leftFoot = landmarks[27] // left ankle
        val rightFoot = landmarks[28] // right ankle
        return abs(rightFoot.x - leftFoot.x)
    }

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

    private fun calculateAngularVelocity(current: PoseLandmarkResult, previous: PoseLandmarkResult): Float {
        // Simplified angular velocity calculation
        return 0f
    }

    private fun calculateBodyPartVelocities(
        current: PoseLandmarkResult,
        previous: PoseLandmarkResult,
        deltaTime: Double
    ): Map<BodyRegion, Float> {
        // Simplified body part velocity calculation
        return emptyMap()
    }

    private fun calculatePosturalSway(): Float = 0.1f
    private fun calculateBalanceConfidence(landmarks: List<PoseLandmarkResult.Landmark>): Float = 0.8f
    private fun estimateGroundReactionForce(landmarks: List<PoseLandmarkResult.Landmark>): Float = 1.0f
    private fun calculateMovementEfficiency(pose: PoseLandmarkResult, velocity: MovementVelocity): Float = 0.8f
    private fun detectTremor(pose: PoseLandmarkResult): Float = 0.1f
    private fun assessCoordination(pose: PoseLandmarkResult): Float = 0.9f
    private fun estimateReactionTime(): Float = 0.3f

    /**
     * Update user response to intervention
     */
    fun updateInterventionResponse(
        interventionId: String,
        response: UserResponse,
        effectiveness: Float,
        timeToCorrection: Long
    ) {
        val record = interventionHistory.find {
            it.intervention.timestamp.toString() == interventionId
        }

        record?.let {
            val updatedRecord = it.copy(
                userResponse = response,
                effectiveness = effectiveness,
                timeToCorrection = timeToCorrection
            )

            val index = interventionHistory.indexOf(it)
            if (index >= 0) {
                interventionHistory[index] = updatedRecord
            }

            // Update user adaptation profile based on response
            updateUserAdaptationProfile(updatedRecord)
        }
    }

    private fun updateUserAdaptationProfile(record: InterventionRecord) {
        val responseTime = record.timeToCorrection
        val effectiveness = record.effectiveness

        // Update adaptation profile based on user response patterns
        userAdaptationProfile = userAdaptationProfile.copy(
            responseTime = (userAdaptationProfile.responseTime + responseTime) / 2,
            adaptationRate = (userAdaptationProfile.adaptationRate + effectiveness) / 2,
            // Adjust risk tolerance based on user's response to interventions
            riskTolerance = when (record.userResponse) {
                UserResponse.RESISTANCE -> userAdaptationProfile.riskTolerance * 1.1f
                UserResponse.IMMEDIATE_COMPLIANCE -> userAdaptationProfile.riskTolerance * 0.9f
                else -> userAdaptationProfile.riskTolerance
            }.coerceIn(0.1f, 0.8f)
        )
    }

    /**
     * Get intervention effectiveness metrics
     */
    fun getInterventionMetrics(): InterventionMetrics {
        val totalInterventions = interventionHistory.size
        val effectiveInterventions = interventionHistory.count { it.effectiveness > 0.5f }
        val avgResponseTime = interventionHistory.map { it.timeToCorrection }.average().toLong()
        val avgEffectiveness = interventionHistory.map { it.effectiveness }.average().toFloat()

        return InterventionMetrics(
            totalInterventions = totalInterventions,
            effectivenessRate = if (totalInterventions > 0) effectiveInterventions.toFloat() / totalInterventions else 0f,
            averageResponseTime = avgResponseTime,
            averageEffectiveness = avgEffectiveness,
            userAdaptationProfile = userAdaptationProfile,
            currentRiskLevel = currentRiskLevel
        )
    }

    data class InterventionMetrics(
        val totalInterventions: Int,
        val effectivenessRate: Float,
        val averageResponseTime: Long,
        val averageEffectiveness: Float,
        val userAdaptationProfile: UserAdaptationProfile,
        val currentRiskLevel: RiskLevel
    )

    /**
     * Reset system for new session
     */
    fun resetSession() {
        movementBuffer.clear()
        riskHistory.clear()
        currentRiskLevel = RiskLevel.LOW
        sessionStartTime = 0L

        Timber.i("Adaptive intervention system reset for new session")
    }
}

/**
 * Helper classes for injury prediction and form analysis
 */
private class InjuryPredictor {
    fun predictInjuryRisk(
        frame: AdaptiveInterventionSystem.MovementFrame,
        history: List<AdaptiveInterventionSystem.MovementFrame>
    ): Float {
        // Simplified injury risk prediction
        val forceRisk = frame.forceEstimates.values.maxOrNull() ?: 0f
        val velocityRisk = if (frame.velocity.linear > 2.0f) 0.7f else 0.2f
        val fatigueRisk = frame.fatigueIndicators.coordinationLoss

        return ((forceRisk / 20f) + velocityRisk + fatigueRisk) / 3f
    }
}

private class FormAnalyzer {
    fun assessFormQuality(frame: AdaptiveInterventionSystem.MovementFrame): Float {
        // Simplified form quality assessment
        val angleQuality = frame.jointAngles.values.map { angle ->
            when {
                angle < 30f || angle > 170f -> 0.3f // Poor angles
                angle < 60f || angle > 150f -> 0.6f // Moderate angles
                else -> 0.9f // Good angles
            }
        }.average().toFloat()

        val stabilityQuality = frame.stabilityMetrics.centerOfMassStability

        return (angleQuality + stabilityQuality) / 2f
    }
}

private class ProgressiveOverloadMonitor {
    fun assessOverloadRisk(
        frame: AdaptiveInterventionSystem.MovementFrame,
        workoutContext: WorkoutContextAnalyzer.WorkoutContext
    ): Float {
        // Simplified overload risk assessment
        val intensityRisk = when (workoutContext.intensityLevel) {
            WorkoutContextAnalyzer.IntensityLevel.VERY_HIGH -> 0.8f
            WorkoutContextAnalyzer.IntensityLevel.HIGH -> 0.5f
            else -> 0.2f
        }

        val fatigueRisk = when (workoutContext.fatigue) {
            WorkoutContextAnalyzer.FatigueLevel.EXHAUSTED -> 0.9f
            WorkoutContextAnalyzer.FatigueLevel.TIRED -> 0.6f
            else -> 0.2f
        }

        return maxOf(intensityRisk, fatigueRisk)
    }
}

private class RecoveryAdviser {
    fun recommendRest(
        fatigueLevel: WorkoutContextAnalyzer.FatigueLevel,
        sessionDuration: Long
    ): AdaptiveInterventionSystem.RestRecommendation {
        return when (fatigueLevel) {
            WorkoutContextAnalyzer.FatigueLevel.EXHAUSTED -> AdaptiveInterventionSystem.RestRecommendation(
                restDuration = 120000L, // 2 minutes
                restType = "Complete rest",
                reason = "High fatigue level detected"
            )
            WorkoutContextAnalyzer.FatigueLevel.TIRED -> AdaptiveInterventionSystem.RestRecommendation(
                restDuration = 60000L, // 1 minute
                restType = "Active recovery",
                reason = "Moderate fatigue level"
            )
            else -> AdaptiveInterventionSystem.RestRecommendation(
                restDuration = 30000L, // 30 seconds
                restType = "Brief pause",
                reason = "Preventative rest"
            )
        }
    }
}