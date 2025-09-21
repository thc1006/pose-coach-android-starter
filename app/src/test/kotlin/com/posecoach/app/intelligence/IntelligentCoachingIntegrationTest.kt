package com.posecoach.app.intelligence

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.test.core.app.ApplicationProvider
import com.posecoach.app.livecoach.LiveCoachManager
import com.posecoach.app.performance.PerformanceMetrics
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for the Intelligent Coaching System
 * with simulated workout scenarios and performance benchmarking
 */
@RunWith(RobolectricTestRunner::class)
class IntelligentCoachingIntegrationTest {

    private lateinit var context: Context
    private lateinit var testScope: TestScope
    private lateinit var performanceMetrics: PerformanceMetrics
    private lateinit var mockLiveCoachManager: MockLiveCoachManager
    private lateinit var intelligentCoachingEngine: IntelligentCoachingEngine
    private lateinit var workoutSimulator: WorkoutSimulator

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testScope = TestScope()
        performanceMetrics = PerformanceMetrics()
        mockLiveCoachManager = MockLiveCoachManager()

        intelligentCoachingEngine = IntelligentCoachingEngine(
            context = context,
            lifecycleScope = testScope,
            liveCoachManager = mockLiveCoachManager,
            performanceMetrics = performanceMetrics
        )

        workoutSimulator = WorkoutSimulator()

        // Initialize Timber for testing
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                println("$tag: $message")
            }
        })
    }

    @After
    fun tearDown() {
        testScope.cancel()
        Timber.uproot()
    }

    @Test
    fun `test initialization and basic functionality`() = testScope.runTest {
        // Initialize the engine
        intelligentCoachingEngine.initialize()

        // Verify initialization
        val status = intelligentCoachingEngine.getCoachingStatus()
        assertTrue("Engine should be active after initialization", status.isActive)
        assertEquals("Initial phase should be UNKNOWN",
            WorkoutContextAnalyzer.WorkoutPhase.UNKNOWN, status.currentPhase)
    }

    @Test
    fun `test complete workout session with real-time coaching`() = testScope.runTest {
        intelligentCoachingEngine.initialize()

        val insightsCollector = mutableListOf<IntelligentCoachingEngine.CoachingInsights>()
        val recommendationsCollector = mutableListOf<IntelligentCoachingEngine.AdaptiveRecommendation>()

        // Collect insights and recommendations
        val insightsJob = launch {
            intelligentCoachingEngine.coachingInsights.collect { insights ->
                insightsCollector.add(insights)
            }
        }

        val recommendationsJob = launch {
            intelligentCoachingEngine.adaptiveRecommendations.collect { recommendation ->
                recommendationsCollector.add(recommendation)
            }
        }

        // Simulate a complete workout session
        val workoutSession = workoutSimulator.generateCompleteWorkoutSession(
            duration = 30.seconds,
            exerciseTypes = listOf(
                WorkoutContextAnalyzer.ExerciseType.SQUAT,
                WorkoutContextAnalyzer.ExerciseType.PUSH_UP,
                WorkoutContextAnalyzer.ExerciseType.PLANK
            )
        )

        // Process each pose frame
        workoutSession.forEach { frame ->
            intelligentCoachingEngine.processPoseWithIntelligentCoaching(frame.pose, frame.userInput)
            advanceTimeBy(100.milliseconds) // 10 FPS simulation
        }

        // Wait for processing to complete
        advanceTimeBy(1.seconds)

        // Verify workout progression
        assertTrue("Should have generated insights", insightsCollector.isNotEmpty())
        assertTrue("Should have at least 10 coaching insights", insightsCollector.size >= 10)

        // Verify phase progression
        val phases = insightsCollector.map { it.workoutContext.currentPhase }.distinct()
        assertTrue("Should progress through multiple phases", phases.size >= 2)

        // Verify coaching quality
        val finalInsights = insightsCollector.last()
        assertTrue("Final coaching score should be reasonable",
            finalInsights.overallScore.overall > 0.3f)

        // Verify recommendations were generated
        if (recommendationsCollector.isNotEmpty()) {
            val criticalRecommendations = recommendationsCollector.filter {
                it.priority == IntelligentCoachingEngine.Priority.CRITICAL
            }
            // Should have appropriate safety recommendations if needed
            assertTrue("Critical recommendations should be actionable",
                criticalRecommendations.all { it.actions.isNotEmpty() })
        }

        insightsJob.cancel()
        recommendationsJob.cancel()
    }

    @Test
    fun `test performance requirements - sub 100ms latency`() = testScope.runTest {
        intelligentCoachingEngine.initialize()

        val latencyMeasurements = mutableListOf<Long>()
        val pose = workoutSimulator.generateRealisticPose(
            exerciseType = WorkoutContextAnalyzer.ExerciseType.SQUAT,
            formQuality = WorkoutContextAnalyzer.FormQuality.GOOD
        )

        // Measure processing latency over multiple iterations
        repeat(50) {
            val startTime = System.nanoTime()

            intelligentCoachingEngine.processPoseWithIntelligentCoaching(pose)

            val endTime = System.nanoTime()
            val latency = (endTime - startTime) / 1_000_000L // Convert to milliseconds
            latencyMeasurements.add(latency)

            advanceTimeBy(33.milliseconds) // ~30 FPS
        }

        // Verify performance requirements
        val averageLatency = latencyMeasurements.average()
        val maxLatency = latencyMeasurements.maxOrNull() ?: 0L
        val p95Latency = latencyMeasurements.sorted().let { sorted ->
            sorted[(sorted.size * 0.95).toInt()]
        }

        println("Latency stats - Average: ${averageLatency}ms, Max: ${maxLatency}ms, P95: ${p95Latency}ms")

        assertTrue("Average latency should be under 100ms", averageLatency < 100.0)
        assertTrue("P95 latency should be under 150ms", p95Latency < 150L)
        assertTrue("No single operation should exceed 200ms", maxLatency < 200L)
    }

    @Test
    fun `test form correction scenario`() = testScope.runTest {
        intelligentCoachingEngine.initialize()

        val recommendationsCollector = mutableListOf<IntelligentCoachingEngine.AdaptiveRecommendation>()

        val recommendationsJob = launch {
            intelligentCoachingEngine.adaptiveRecommendations.collect { recommendation ->
                recommendationsCollector.add(recommendation)
            }
        }

        // Simulate poor form that should trigger corrections
        val poorFormFrames = workoutSimulator.generateFormDegradationSequence(
            exerciseType = WorkoutContextAnalyzer.ExerciseType.SQUAT,
            startingQuality = WorkoutContextAnalyzer.FormQuality.GOOD,
            endingQuality = WorkoutContextAnalyzer.FormQuality.POOR,
            frameCount = 20
        )

        poorFormFrames.forEach { frame ->
            intelligentCoachingEngine.processPoseWithIntelligentCoaching(frame.pose)
            advanceTimeBy(100.milliseconds)
        }

        advanceTimeBy(1.seconds)

        // Verify form correction recommendations were generated
        val formCorrections = recommendationsCollector.filter {
            it.type == IntelligentCoachingEngine.RecommendationType.TECHNIQUE_ADJUSTMENT
        }

        assertTrue("Should generate form correction recommendations", formCorrections.isNotEmpty())
        assertTrue("Form corrections should have high priority",
            formCorrections.any { it.priority in listOf(
                IntelligentCoachingEngine.Priority.HIGH,
                IntelligentCoachingEngine.Priority.CRITICAL
            )}
        )

        recommendationsJob.cancel()
    }

    @Test
    fun `test fatigue detection and intervention`() = testScope.runTest {
        intelligentCoachingEngine.initialize()

        val recommendationsCollector = mutableListOf<IntelligentCoachingEngine.AdaptiveRecommendation>()

        val recommendationsJob = launch {
            intelligentCoachingEngine.adaptiveRecommendations.collect { recommendation ->
                recommendationsCollector.add(recommendation)
            }
        }

        // Simulate increasing fatigue over time
        val fatigueProgression = workoutSimulator.generateFatigueProgressionSession(
            duration = 20.seconds,
            startingFatigue = WorkoutContextAnalyzer.FatigueLevel.FRESH,
            endingFatigue = WorkoutContextAnalyzer.FatigueLevel.EXHAUSTED
        )

        fatigueProgression.forEach { frame ->
            intelligentCoachingEngine.processPoseWithIntelligentCoaching(frame.pose, frame.userInput)
            advanceTimeBy(100.milliseconds)
        }

        advanceTimeBy(1.seconds)

        // Verify fatigue-related interventions
        val recoveryRecommendations = recommendationsCollector.filter {
            it.type == IntelligentCoachingEngine.RecommendationType.RECOVERY_GUIDANCE
        }

        assertTrue("Should generate recovery recommendations when fatigued",
            recoveryRecommendations.isNotEmpty())

        recommendationsJob.cancel()
    }

    @Test
    fun `test motivation adaptation`() = testScope.runTest {
        intelligentCoachingEngine.initialize()

        val recommendationsCollector = mutableListOf<IntelligentCoachingEngine.AdaptiveRecommendation>()

        val recommendationsJob = launch {
            intelligentCoachingEngine.adaptiveRecommendations.collect { recommendation ->
                recommendationsCollector.add(recommendation)
            }
        }

        // Simulate user with low motivation
        val lowMotivationUser = IntelligentCoachingEngine.UserInput(
            mood = PersonalizedFeedbackManager.UserMood.FRUSTRATED,
            energyLevel = PersonalizedFeedbackManager.EnergyLevel.LOW,
            focusLevel = PersonalizedFeedbackManager.FocusLevel.DISTRACTED
        )

        // Process several frames with low motivation
        repeat(20) {
            val pose = workoutSimulator.generateRealisticPose(
                exerciseType = WorkoutContextAnalyzer.ExerciseType.SQUAT,
                formQuality = WorkoutContextAnalyzer.FormQuality.FAIR
            )

            intelligentCoachingEngine.processPoseWithIntelligentCoaching(pose, lowMotivationUser)
            advanceTimeBy(100.milliseconds)
        }

        advanceTimeBy(1.seconds)

        // Verify motivation boosting recommendations
        val motivationBoosts = recommendationsCollector.filter {
            it.type == IntelligentCoachingEngine.RecommendationType.MOTIVATION_BOOST
        }

        assertTrue("Should generate motivation boost recommendations", motivationBoosts.isNotEmpty())
        assertTrue("Motivation boosts should have appropriate priority",
            motivationBoosts.any { it.priority == IntelligentCoachingEngine.Priority.HIGH })

        recommendationsJob.cancel()
    }

    @Test
    fun `test safety intervention system`() = testScope.runTest {
        intelligentCoachingEngine.initialize()

        val recommendationsCollector = mutableListOf<IntelligentCoachingEngine.AdaptiveRecommendation>()

        val recommendationsJob = launch {
            intelligentCoachingEngine.adaptiveRecommendations.collect { recommendation ->
                recommendationsCollector.add(recommendation)
            }
        }

        // Simulate dangerous form that should trigger immediate intervention
        val dangerousFormPose = workoutSimulator.generateRealisticPose(
            exerciseType = WorkoutContextAnalyzer.ExerciseType.SQUAT,
            formQuality = WorkoutContextAnalyzer.FormQuality.DANGEROUS
        )

        // Process the dangerous pose
        intelligentCoachingEngine.processPoseWithIntelligentCoaching(dangerousFormPose)
        advanceTimeBy(500.milliseconds)

        // Verify immediate safety intervention
        val safetyWarnings = recommendationsCollector.filter {
            it.type == IntelligentCoachingEngine.RecommendationType.SAFETY_WARNING
        }

        assertTrue("Should generate safety warnings for dangerous form", safetyWarnings.isNotEmpty())
        assertTrue("Safety warnings should have critical priority",
            safetyWarnings.any { it.priority == IntelligentCoachingEngine.Priority.CRITICAL })

        // Verify response time for safety warnings
        val firstSafetyWarning = safetyWarnings.firstOrNull()
        assertNotNull("Should have at least one safety warning", firstSafetyWarning)

        firstSafetyWarning?.let { warning ->
            assertEquals("Safety warnings should be immediate", "immediate", warning.timeframe)
            assertTrue("Safety warnings should have high confidence", warning.confidenceScore > 0.8f)
        }

        recommendationsJob.cancel()
    }

    @Test
    fun `test coaching effectiveness feedback loop`() = testScope.runTest {
        intelligentCoachingEngine.initialize()

        val insightsCollector = mutableListOf<IntelligentCoachingEngine.CoachingInsights>()

        val insightsJob = launch {
            intelligentCoachingEngine.coachingInsights.collect { insights ->
                insightsCollector.add(insights)
            }
        }

        // Process some poses to generate coaching decisions
        repeat(10) {
            val pose = workoutSimulator.generateRealisticPose(
                exerciseType = WorkoutContextAnalyzer.ExerciseType.PUSH_UP,
                formQuality = WorkoutContextAnalyzer.FormQuality.GOOD
            )

            intelligentCoachingEngine.processPoseWithIntelligentCoaching(pose)
            advanceTimeBy(100.milliseconds)
        }

        advanceTimeBy(500.milliseconds)

        // Get a coaching decision and provide feedback
        val insightsWithDecision = insightsCollector.find { it.coachingDecision != null }
        assertNotNull("Should have at least one coaching decision", insightsWithDecision)

        insightsWithDecision?.coachingDecision?.let { decision ->
            // Provide positive feedback
            intelligentCoachingEngine.provideFeedback(
                decisionId = decision.decisionId,
                effectiveness = 0.9f,
                userResponse = "immediate"
            )

            // Verify feedback was processed
            val analytics = intelligentCoachingEngine.getCoachingAnalytics()
            assertTrue("Analytics should show total decisions", analytics.totalDecisions > 0)
        }

        insightsJob.cancel()
    }

    @Test
    fun `test multi-exercise workout transition`() = testScope.runTest {
        intelligentCoachingEngine.initialize()

        val insightsCollector = mutableListOf<IntelligentCoachingEngine.CoachingInsights>()

        val insightsJob = launch {
            intelligentCoachingEngine.coachingInsights.collect { insights ->
                insightsCollector.add(insights)
            }
        }

        // Simulate workout with multiple exercise transitions
        val multiExerciseSession = listOf(
            // Squats phase
            *Array(15) {
                WorkoutSimulationFrame(
                    workoutSimulator.generateRealisticPose(
                        WorkoutContextAnalyzer.ExerciseType.SQUAT,
                        WorkoutContextAnalyzer.FormQuality.GOOD
                    ),
                    null
                )
            },
            // Transition
            *Array(5) {
                WorkoutSimulationFrame(
                    workoutSimulator.generateTransitionPose(),
                    null
                )
            },
            // Push-ups phase
            *Array(15) {
                WorkoutSimulationFrame(
                    workoutSimulator.generateRealisticPose(
                        WorkoutContextAnalyzer.ExerciseType.PUSH_UP,
                        WorkoutContextAnalyzer.FormQuality.GOOD
                    ),
                    null
                )
            }
        )

        multiExerciseSession.forEach { frame ->
            intelligentCoachingEngine.processPoseWithIntelligentCoaching(frame.pose, frame.userInput)
            advanceTimeBy(100.milliseconds)
        }

        advanceTimeBy(1.seconds)

        // Verify exercise detection and transitions
        val exerciseDetections = insightsCollector.mapNotNull {
            it.workoutContext.currentExercise
        }.distinct()

        assertTrue("Should detect multiple exercise types", exerciseDetections.size >= 2)
        assertTrue("Should detect squats",
            exerciseDetections.contains(WorkoutContextAnalyzer.ExerciseType.SQUAT))
        assertTrue("Should detect push-ups",
            exerciseDetections.contains(WorkoutContextAnalyzer.ExerciseType.PUSH_UP))

        insightsJob.cancel()
    }

    @Test
    fun `test system performance under load`() = testScope.runTest {
        intelligentCoachingEngine.initialize()

        val startTime = System.currentTimeMillis()
        val processingTimes = mutableListOf<Long>()

        // Simulate high-frequency pose processing
        repeat(100) { iteration ->
            val pose = workoutSimulator.generateRealisticPose(
                exerciseType = WorkoutContextAnalyzer.ExerciseType.values().random(),
                formQuality = WorkoutContextAnalyzer.FormQuality.values().random()
            )

            val frameStartTime = System.nanoTime()
            intelligentCoachingEngine.processPoseWithIntelligentCoaching(pose)
            val frameEndTime = System.nanoTime()

            processingTimes.add((frameEndTime - frameStartTime) / 1_000_000L)

            // Advance time minimally to simulate real-time processing
            advanceTimeBy(33.milliseconds) // 30 FPS
        }

        val totalTime = System.currentTimeMillis() - startTime
        val averageProcessingTime = processingTimes.average()
        val maxProcessingTime = processingTimes.maxOrNull() ?: 0L

        println("Load test results:")
        println("Total time: ${totalTime}ms")
        println("Average processing time: ${averageProcessingTime}ms")
        println("Max processing time: ${maxProcessingTime}ms")
        println("Throughput: ${100.0 / (totalTime / 1000.0)} poses/second")

        // Verify performance under load
        assertTrue("Average processing time should remain under 100ms under load",
            averageProcessingTime < 100.0)
        assertTrue("Maximum processing time should not exceed 200ms",
            maxProcessingTime < 200L)

        // Get final analytics
        val analytics = intelligentCoachingEngine.getCoachingAnalytics()
        assertTrue("Should maintain effectiveness under load",
            analytics.overallEffectiveness > 0.5f)
    }

    @Test
    fun `test coaching analytics and insights`() = testScope.runTest {
        intelligentCoachingEngine.initialize()

        // Process a workout session
        repeat(30) {
            val pose = workoutSimulator.generateRealisticPose(
                exerciseType = WorkoutContextAnalyzer.ExerciseType.SQUAT,
                formQuality = WorkoutContextAnalyzer.FormQuality.GOOD
            )
            intelligentCoachingEngine.processPoseWithIntelligentCoaching(pose)
            advanceTimeBy(100.milliseconds)
        }

        // Get analytics
        val analytics = intelligentCoachingEngine.getCoachingAnalytics()

        // Verify analytics completeness
        assertTrue("Session duration should be tracked", analytics.sessionDuration > 0)
        assertTrue("Total decisions should be tracked", analytics.totalDecisions > 0)
        assertTrue("Processing time should be measured", analytics.averageProcessingTime >= 0)
        assertTrue("Effectiveness should be calculated", analytics.overallEffectiveness >= 0)

        // Verify system metrics
        assertNotNull("System metrics should be available", analytics.systemMetrics)
        assertTrue("Average latency should be reasonable",
            analytics.systemMetrics.averageDecisionLatency < 200L)

        // Verify behavior model performance
        assertNotNull("Behavior model performance should be available",
            analytics.behaviorModelPerformance)
        assertTrue("Should have adaptation insights",
            analytics.adaptationInsights.isNotEmpty())

        println("Analytics summary:")
        println("Session duration: ${analytics.sessionDuration}ms")
        println("Total decisions: ${analytics.totalDecisions}")
        println("Average processing time: ${analytics.averageProcessingTime}ms")
        println("Overall effectiveness: ${analytics.overallEffectiveness}")
        println("System health: ${analytics.behaviorModelPerformance.modelHealth}")
    }

    @Test
    fun `test session reset functionality`() = testScope.runTest {
        intelligentCoachingEngine.initialize()

        // Process some poses
        repeat(10) {
            val pose = workoutSimulator.generateRealisticPose(
                exerciseType = WorkoutContextAnalyzer.ExerciseType.PLANK,
                formQuality = WorkoutContextAnalyzer.FormQuality.GOOD
            )
            intelligentCoachingEngine.processPoseWithIntelligentCoaching(pose)
            advanceTimeBy(100.milliseconds)
        }

        // Get initial analytics
        val analyticsBeforeReset = intelligentCoachingEngine.getCoachingAnalytics()
        assertTrue("Should have session data before reset",
            analyticsBeforeReset.totalDecisions > 0)

        // Reset session
        intelligentCoachingEngine.resetSession()

        // Verify reset
        val analyticsAfterReset = intelligentCoachingEngine.getCoachingAnalytics()
        assertEquals("Total decisions should be reset", 0L, analyticsAfterReset.totalDecisions)
        assertTrue("Session duration should be reset", analyticsAfterReset.sessionDuration < 1000L)

        // Verify system still works after reset
        val status = intelligentCoachingEngine.getCoachingStatus()
        assertTrue("Engine should still be active after reset", status.isActive)
    }
}

/**
 * Mock LiveCoachManager for testing
 */
class MockLiveCoachManager : LiveCoachManager {
    private var isRecordingState = false

    override fun isRecording(): Boolean = isRecordingState

    fun setRecording(recording: Boolean) {
        isRecordingState = recording
    }

    // Implement other required methods as no-ops for testing
    // In a real implementation, these would be properly mocked
}

/**
 * Workout simulator for generating realistic test data
 */
class WorkoutSimulator {

    fun generateCompleteWorkoutSession(
        duration: kotlin.time.Duration,
        exerciseTypes: List<WorkoutContextAnalyzer.ExerciseType>
    ): List<WorkoutSimulationFrame> {
        val frameCount = (duration.inWholeMilliseconds / 100).toInt() // 10 FPS
        val framesPerExercise = frameCount / exerciseTypes.size

        val frames = mutableListOf<WorkoutSimulationFrame>()

        exerciseTypes.forEachIndexed { index, exerciseType ->
            val startFrame = index * framesPerExercise
            val endFrame = minOf((index + 1) * framesPerExercise, frameCount)

            for (frameIndex in startFrame until endFrame) {
                val progress = (frameIndex - startFrame).toFloat() / framesPerExercise
                val formQuality = simulateFormQualityProgression(progress)
                val userState = simulateUserStateProgression(progress, exerciseType)

                frames.add(
                    WorkoutSimulationFrame(
                        pose = generateRealisticPose(exerciseType, formQuality),
                        userInput = userState
                    )
                )
            }
        }

        return frames
    }

    fun generateFormDegradationSequence(
        exerciseType: WorkoutContextAnalyzer.ExerciseType,
        startingQuality: WorkoutContextAnalyzer.FormQuality,
        endingQuality: WorkoutContextAnalyzer.FormQuality,
        frameCount: Int
    ): List<WorkoutSimulationFrame> {
        val qualityLevels = WorkoutContextAnalyzer.FormQuality.values()
        val startIndex = qualityLevels.indexOf(startingQuality)
        val endIndex = qualityLevels.indexOf(endingQuality)

        return (0 until frameCount).map { frameIndex ->
            val progress = frameIndex.toFloat() / (frameCount - 1)
            val qualityIndex = (startIndex + (endIndex - startIndex) * progress).toInt()
                .coerceIn(0, qualityLevels.size - 1)

            WorkoutSimulationFrame(
                pose = generateRealisticPose(exerciseType, qualityLevels[qualityIndex]),
                userInput = null
            )
        }
    }

    fun generateFatigueProgressionSession(
        duration: kotlin.time.Duration,
        startingFatigue: WorkoutContextAnalyzer.FatigueLevel,
        endingFatigue: WorkoutContextAnalyzer.FatigueLevel
    ): List<WorkoutSimulationFrame> {
        val frameCount = (duration.inWholeMilliseconds / 100).toInt()
        val fatigueLevels = WorkoutContextAnalyzer.FatigueLevel.values()
        val startIndex = fatigueLevels.indexOf(startingFatigue)
        val endIndex = fatigueLevels.indexOf(endingFatigue)

        return (0 until frameCount).map { frameIndex ->
            val progress = frameIndex.toFloat() / (frameCount - 1)
            val energyLevel = when {
                progress < 0.3f -> PersonalizedFeedbackManager.EnergyLevel.HIGH
                progress < 0.6f -> PersonalizedFeedbackManager.EnergyLevel.MODERATE
                progress < 0.8f -> PersonalizedFeedbackManager.EnergyLevel.LOW
                else -> PersonalizedFeedbackManager.EnergyLevel.VERY_LOW
            }

            WorkoutSimulationFrame(
                pose = generateRealisticPose(
                    WorkoutContextAnalyzer.ExerciseType.SQUAT,
                    WorkoutContextAnalyzer.FormQuality.FAIR
                ),
                userInput = IntelligentCoachingEngine.UserInput(
                    energyLevel = energyLevel,
                    mood = if (progress > 0.7f)
                        PersonalizedFeedbackManager.UserMood.TIRED
                    else PersonalizedFeedbackManager.UserMood.NEUTRAL
                )
            )
        }
    }

    fun generateRealisticPose(
        exerciseType: WorkoutContextAnalyzer.ExerciseType,
        formQuality: WorkoutContextAnalyzer.FormQuality
    ): PoseLandmarkResult {
        // Generate realistic pose landmarks based on exercise type and form quality
        val landmarks = generateLandmarksForExercise(exerciseType, formQuality)

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks, // Simplified for testing
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = (15..25).random().toLong() // Realistic inference time
        )
    }

    fun generateTransitionPose(): PoseLandmarkResult {
        // Generate a neutral standing pose for transitions
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.5f + (kotlin.random.Random.nextFloat() - 0.5f) * 0.1f,
                y = when {
                    index < 11 -> 0.2f + kotlin.random.Random.nextFloat() * 0.3f // Upper body
                    index < 23 -> 0.5f + kotlin.random.Random.nextFloat() * 0.2f // Torso
                    else -> 0.7f + kotlin.random.Random.nextFloat() * 0.3f // Lower body
                },
                z = kotlin.random.Random.nextFloat() * 0.1f,
                visibility = 0.9f + kotlin.random.Random.nextFloat() * 0.1f,
                presence = 0.9f + kotlin.random.Random.nextFloat() * 0.1f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = (15..25).random().toLong()
        )
    }

    private fun generateLandmarksForExercise(
        exerciseType: WorkoutContextAnalyzer.ExerciseType,
        formQuality: WorkoutContextAnalyzer.FormQuality
    ): List<PoseLandmarkResult.Landmark> {
        // Generate 33 MediaPipe pose landmarks
        return (0..32).map { index ->
            val basePosition = getBaseLandmarkPosition(index, exerciseType)
            val variation = getFormQualityVariation(formQuality)

            PoseLandmarkResult.Landmark(
                x = basePosition.first + (kotlin.random.Random.nextFloat() - 0.5f) * variation,
                y = basePosition.second + (kotlin.random.Random.nextFloat() - 0.5f) * variation,
                z = kotlin.random.Random.nextFloat() * 0.1f,
                visibility = 0.8f + kotlin.random.Random.nextFloat() * 0.2f,
                presence = 0.8f + kotlin.random.Random.nextFloat() * 0.2f
            )
        }
    }

    private fun getBaseLandmarkPosition(
        landmarkIndex: Int,
        exerciseType: WorkoutContextAnalyzer.ExerciseType
    ): Pair<Float, Float> {
        // Simplified landmark positioning based on exercise type
        return when (exerciseType) {
            WorkoutContextAnalyzer.ExerciseType.SQUAT -> getSquatLandmarkPosition(landmarkIndex)
            WorkoutContextAnalyzer.ExerciseType.PUSH_UP -> getPushUpLandmarkPosition(landmarkIndex)
            WorkoutContextAnalyzer.ExerciseType.PLANK -> getPlankLandmarkPosition(landmarkIndex)
            else -> getNeutralLandmarkPosition(landmarkIndex)
        }
    }

    private fun getSquatLandmarkPosition(landmarkIndex: Int): Pair<Float, Float> {
        return when {
            landmarkIndex < 11 -> Pair(0.5f, 0.2f) // Head and face
            landmarkIndex < 23 -> Pair(0.5f, 0.4f) // Arms and shoulders
            landmarkIndex < 25 -> Pair(0.5f, 0.6f) // Hips
            else -> Pair(0.5f, 0.8f) // Legs and feet
        }
    }

    private fun getPushUpLandmarkPosition(landmarkIndex: Int): Pair<Float, Float> {
        return when {
            landmarkIndex < 11 -> Pair(0.5f, 0.3f) // Head (lower in push-up)
            landmarkIndex < 23 -> Pair(0.5f, 0.4f) // Arms and shoulders
            else -> Pair(0.5f, 0.7f) // Lower body
        }
    }

    private fun getPlankLandmarkPosition(landmarkIndex: Int): Pair<Float, Float> {
        return when {
            landmarkIndex < 11 -> Pair(0.5f, 0.3f) // Head
            landmarkIndex < 23 -> Pair(0.5f, 0.5f) // Arms and shoulders
            else -> Pair(0.5f, 0.6f) // Lower body (horizontal alignment)
        }
    }

    private fun getNeutralLandmarkPosition(landmarkIndex: Int): Pair<Float, Float> {
        return when {
            landmarkIndex < 11 -> Pair(0.5f, 0.1f) // Head and face
            landmarkIndex < 23 -> Pair(0.5f, 0.4f) // Arms and shoulders
            landmarkIndex < 25 -> Pair(0.5f, 0.6f) // Hips
            else -> Pair(0.5f, 0.9f) // Legs and feet
        }
    }

    private fun getFormQualityVariation(formQuality: WorkoutContextAnalyzer.FormQuality): Float {
        return when (formQuality) {
            WorkoutContextAnalyzer.FormQuality.EXCELLENT -> 0.02f
            WorkoutContextAnalyzer.FormQuality.GOOD -> 0.05f
            WorkoutContextAnalyzer.FormQuality.FAIR -> 0.1f
            WorkoutContextAnalyzer.FormQuality.POOR -> 0.2f
            WorkoutContextAnalyzer.FormQuality.DANGEROUS -> 0.3f
        }
    }

    private fun simulateFormQualityProgression(progress: Float): WorkoutContextAnalyzer.FormQuality {
        return when {
            progress < 0.2f -> WorkoutContextAnalyzer.FormQuality.FAIR // Starting out
            progress < 0.6f -> WorkoutContextAnalyzer.FormQuality.GOOD // Getting better
            progress < 0.8f -> WorkoutContextAnalyzer.FormQuality.EXCELLENT // Peak performance
            else -> WorkoutContextAnalyzer.FormQuality.GOOD // Slight fatigue
        }
    }

    private fun simulateUserStateProgression(
        progress: Float,
        exerciseType: WorkoutContextAnalyzer.ExerciseType
    ): IntelligentCoachingEngine.UserInput {
        val mood = when {
            progress < 0.3f -> PersonalizedFeedbackManager.UserMood.MOTIVATED
            progress < 0.7f -> PersonalizedFeedbackManager.UserMood.FOCUSED
            else -> PersonalizedFeedbackManager.UserMood.TIRED
        }

        val energyLevel = when {
            progress < 0.4f -> PersonalizedFeedbackManager.EnergyLevel.HIGH
            progress < 0.7f -> PersonalizedFeedbackManager.EnergyLevel.MODERATE
            else -> PersonalizedFeedbackManager.EnergyLevel.LOW
        }

        return IntelligentCoachingEngine.UserInput(
            mood = mood,
            energyLevel = energyLevel,
            focusLevel = PersonalizedFeedbackManager.FocusLevel.MODERATE
        )
    }
}

data class WorkoutSimulationFrame(
    val pose: PoseLandmarkResult,
    val userInput: IntelligentCoachingEngine.UserInput?
)