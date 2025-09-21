package com.posecoach.testing.mocks.suggestions

import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.suggestions.PoseSuggestionClient
import com.posecoach.suggestions.models.PoseSuggestion
import com.posecoach.suggestions.models.SuggestionResponse
import com.posecoach.testing.mocks.MockService
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mock implementation of PoseSuggestionClient for comprehensive testing
 * Supports various Gemini API testing scenarios including:
 * - Successful suggestion generation
 * - Network errors and timeouts
 * - Rate limiting scenarios
 * - Invalid responses
 * - Performance testing
 */
class MockPoseSuggestionClient : PoseSuggestionClient, MockService {

    private val interactionCount = AtomicInteger(0)

    // Test configuration
    var shouldSimulateNetworkError = false
    var shouldSimulateTimeout = false
    var shouldSimulateRateLimiting = false
    var shouldReturnInvalidResponse = false
    var responseLatencyMs = 0L
    var customSuggestions: List<PoseSuggestion>? = null

    override suspend fun getPoseSuggestions(landmarks: List<PoseLandmarkResult.Landmark>): SuggestionResponse {
        interactionCount.incrementAndGet()

        // Simulate response latency
        if (responseLatencyMs > 0) {
            kotlinx.coroutines.delay(responseLatencyMs)
        }

        // Simulate various error conditions
        when {
            shouldSimulateNetworkError -> {
                throw Exception("Mock network error: Unable to connect to Gemini API")
            }
            shouldSimulateTimeout -> {
                kotlinx.coroutines.delay(30000) // Simulate timeout
                throw Exception("Mock timeout error: Request timed out")
            }
            shouldSimulateRateLimiting -> {
                throw Exception("Mock rate limiting: API quota exceeded")
            }
            shouldReturnInvalidResponse -> {
                return SuggestionResponse(
                    suggestions = emptyList(),
                    requestId = "invalid-${System.currentTimeMillis()}",
                    processingTimeMs = responseLatencyMs,
                    confidence = 0.0f
                )
            }
        }

        val suggestions = customSuggestions ?: generateMockSuggestions(landmarks)

        return SuggestionResponse(
            suggestions = suggestions,
            requestId = "mock-${System.currentTimeMillis()}",
            processingTimeMs = responseLatencyMs,
            confidence = 0.85f + (Math.random() * 0.15f).toFloat()
        )
    }

    override suspend fun analyzePoseForm(landmarks: List<PoseLandmarkResult.Landmark>): SuggestionResponse {
        interactionCount.incrementAndGet()

        if (responseLatencyMs > 0) {
            kotlinx.coroutines.delay(responseLatencyMs)
        }

        val formAnalysis = generateFormAnalysisSuggestions(landmarks)

        return SuggestionResponse(
            suggestions = formAnalysis,
            requestId = "analysis-${System.currentTimeMillis()}",
            processingTimeMs = responseLatencyMs,
            confidence = 0.90f
        )
    }

    private fun generateMockSuggestions(landmarks: List<PoseLandmarkResult.Landmark>): List<PoseSuggestion> {
        // Analyze landmarks to generate contextual suggestions
        val poseQuality = analyzePoseQuality(landmarks)

        return when {
            poseQuality.overallScore >= 0.8 -> generateExcellentPoseSuggestions()
            poseQuality.overallScore >= 0.6 -> generateGoodPoseSuggestions()
            poseQuality.overallScore >= 0.4 -> generateModeratePoseSuggestions()
            else -> generatePoorPoseSuggestions()
        }
    }

    private fun generateFormAnalysisSuggestions(landmarks: List<PoseLandmarkResult.Landmark>): List<PoseSuggestion> {
        val analysis = analyzePoseQuality(landmarks)
        val suggestions = mutableListOf<PoseSuggestion>()

        // Generate specific form corrections based on analysis
        if (analysis.shoulderAlignment < 0.7) {
            suggestions.add(
                PoseSuggestion(
                    title = "Shoulder Alignment",
                    description = "Keep your shoulders level and aligned over your hips",
                    priority = PoseSuggestion.Priority.HIGH,
                    category = PoseSuggestion.Category.FORM_CORRECTION,
                    confidence = 0.95f
                )
            )
        }

        if (analysis.spineAlignment < 0.7) {
            suggestions.add(
                PoseSuggestion(
                    title = "Spine Posture",
                    description = "Maintain a neutral spine position - avoid excessive arching or rounding",
                    priority = PoseSuggestion.Priority.HIGH,
                    category = PoseSuggestion.Category.FORM_CORRECTION,
                    confidence = 0.92f
                )
            )
        }

        if (analysis.weightDistribution < 0.6) {
            suggestions.add(
                PoseSuggestion(
                    title = "Weight Distribution",
                    description = "Distribute your weight evenly between both feet",
                    priority = PoseSuggestion.Priority.MEDIUM,
                    category = PoseSuggestion.Category.BALANCE,
                    confidence = 0.88f
                )
            )
        }

        return suggestions.ifEmpty { generateDefaultSuggestions() }
    }

    private fun generateExcellentPoseSuggestions(): List<PoseSuggestion> {
        return listOf(
            PoseSuggestion(
                title = "Excellent Form!",
                description = "Your posture looks great. Consider holding this position for 30 seconds.",
                priority = PoseSuggestion.Priority.LOW,
                category = PoseSuggestion.Category.ENCOURAGEMENT,
                confidence = 0.98f
            ),
            PoseSuggestion(
                title = "Advanced Variation",
                description = "Try adding a slight reach overhead to challenge your balance.",
                priority = PoseSuggestion.Priority.LOW,
                category = PoseSuggestion.Category.PROGRESSION,
                confidence = 0.85f
            )
        )
    }

    private fun generateGoodPoseSuggestions(): List<PoseSuggestion> {
        return listOf(
            PoseSuggestion(
                title = "Good Alignment",
                description = "Nice work! Try to engage your core muscles slightly more.",
                priority = PoseSuggestion.Priority.MEDIUM,
                category = PoseSuggestion.Category.REFINEMENT,
                confidence = 0.90f
            ),
            PoseSuggestion(
                title = "Breathing",
                description = "Remember to breathe steadily while maintaining this position.",
                priority = PoseSuggestion.Priority.LOW,
                category = PoseSuggestion.Category.TECHNIQUE,
                confidence = 0.92f
            )
        )
    }

    private fun generateModeratePoseSuggestions(): List<PoseSuggestion> {
        return listOf(
            PoseSuggestion(
                title = "Adjust Posture",
                description = "Straighten your back and lift your chest slightly.",
                priority = PoseSuggestion.Priority.HIGH,
                category = PoseSuggestion.Category.FORM_CORRECTION,
                confidence = 0.88f
            ),
            PoseSuggestion(
                title = "Foundation Check",
                description = "Make sure your feet are planted firmly on the ground.",
                priority = PoseSuggestion.Priority.MEDIUM,
                category = PoseSuggestion.Category.STABILITY,
                confidence = 0.85f
            )
        )
    }

    private fun generatePoorPoseSuggestions(): List<PoseSuggestion> {
        return listOf(
            PoseSuggestion(
                title = "Reset Your Position",
                description = "Take a moment to reset. Stand tall with shoulders back.",
                priority = PoseSuggestion.Priority.HIGH,
                category = PoseSuggestion.Category.FORM_CORRECTION,
                confidence = 0.95f
            ),
            PoseSuggestion(
                title = "Start Simple",
                description = "Focus on basic standing posture before progressing.",
                priority = PoseSuggestion.Priority.HIGH,
                category = PoseSuggestion.Category.BEGINNER_GUIDANCE,
                confidence = 0.92f
            ),
            PoseSuggestion(
                title = "Safety First",
                description = "If you feel unstable, hold onto a wall or chair for support.",
                priority = PoseSuggestion.Priority.HIGH,
                category = PoseSuggestion.Category.SAFETY,
                confidence = 0.98f
            )
        )
    }

    private fun generateDefaultSuggestions(): List<PoseSuggestion> {
        return listOf(
            PoseSuggestion(
                title = "General Guidance",
                description = "Maintain good posture and breathe steadily.",
                priority = PoseSuggestion.Priority.MEDIUM,
                category = PoseSuggestion.Category.GENERAL,
                confidence = 0.80f
            )
        )
    }

    private fun analyzePoseQuality(landmarks: List<PoseLandmarkResult.Landmark>): PoseQualityAnalysis {
        if (landmarks.size < 33) {
            return PoseQualityAnalysis(0.3f, 0.3f, 0.3f, 0.3f, 0.3f)
        }

        // Simplified pose quality analysis
        val shoulderAlignment = calculateShoulderAlignment(landmarks)
        val spineAlignment = calculateSpineAlignment(landmarks)
        val weightDistribution = calculateWeightDistribution(landmarks)
        val stability = calculateStability(landmarks)

        val overallScore = (shoulderAlignment + spineAlignment + weightDistribution + stability) / 4.0f

        return PoseQualityAnalysis(
            overallScore = overallScore,
            shoulderAlignment = shoulderAlignment,
            spineAlignment = spineAlignment,
            weightDistribution = weightDistribution,
            stability = stability
        )
    }

    private fun calculateShoulderAlignment(landmarks: List<PoseLandmarkResult.Landmark>): Float {
        // Simplified shoulder alignment calculation
        val leftShoulder = landmarks.getOrNull(11)
        val rightShoulder = landmarks.getOrNull(12)

        return if (leftShoulder != null && rightShoulder != null) {
            val yDiff = Math.abs(leftShoulder.y - rightShoulder.y)
            Math.max(0.0f, 1.0f - (yDiff * 10)) // Normalize to 0-1
        } else 0.5f
    }

    private fun calculateSpineAlignment(landmarks: List<PoseLandmarkResult.Landmark>): Float {
        // Simplified spine alignment calculation
        val nose = landmarks.getOrNull(0)
        val leftHip = landmarks.getOrNull(23)
        val rightHip = landmarks.getOrNull(24)

        return if (nose != null && leftHip != null && rightHip != null) {
            val hipCenterX = (leftHip.x + rightHip.x) / 2
            val xDiff = Math.abs(nose.x - hipCenterX)
            Math.max(0.0f, 1.0f - (xDiff * 5)) // Normalize to 0-1
        } else 0.5f
    }

    private fun calculateWeightDistribution(landmarks: List<PoseLandmarkResult.Landmark>): Float {
        // Simplified weight distribution calculation
        val leftAnkle = landmarks.getOrNull(27)
        val rightAnkle = landmarks.getOrNull(28)

        return if (leftAnkle != null && rightAnkle != null) {
            val yDiff = Math.abs(leftAnkle.y - rightAnkle.y)
            Math.max(0.0f, 1.0f - (yDiff * 8)) // Normalize to 0-1
        } else 0.5f
    }

    private fun calculateStability(landmarks: List<PoseLandmarkResult.Landmark>): Float {
        // Calculate overall pose stability based on landmark confidence
        val avgVisibility = landmarks.map { it.visibility }.average().toFloat()
        return Math.min(1.0f, avgVisibility)
    }

    // Test utility methods

    /**
     * Configure the mock to simulate specific test scenarios
     */
    fun configureTestScenario(scenario: TestScenario) {
        when (scenario) {
            TestScenario.NETWORK_ERROR -> {
                shouldSimulateNetworkError = true
            }
            TestScenario.TIMEOUT -> {
                shouldSimulateTimeout = true
            }
            TestScenario.RATE_LIMITED -> {
                shouldSimulateRateLimiting = true
            }
            TestScenario.INVALID_RESPONSE -> {
                shouldReturnInvalidResponse = true
            }
            TestScenario.HIGH_LATENCY -> {
                responseLatencyMs = 5000L
            }
            TestScenario.PERFECT_SUGGESTIONS -> {
                customSuggestions = generateExcellentPoseSuggestions()
            }
            TestScenario.NORMAL -> {
                reset()
            }
        }
    }

    /**
     * Set custom suggestions for testing specific scenarios
     */
    fun setCustomSuggestions(suggestions: List<PoseSuggestion>) {
        customSuggestions = suggestions
    }

    /**
     * Set response latency for performance testing
     */
    fun setResponseLatency(latencyMs: Long) {
        responseLatencyMs = latencyMs
    }

    // MockService implementation

    override fun reset() {
        shouldSimulateNetworkError = false
        shouldSimulateTimeout = false
        shouldSimulateRateLimiting = false
        shouldReturnInvalidResponse = false
        responseLatencyMs = 0L
        customSuggestions = null
        interactionCount.set(0)
    }

    override fun getInteractionCount(): Int = interactionCount.get()

    data class PoseQualityAnalysis(
        val overallScore: Float,
        val shoulderAlignment: Float,
        val spineAlignment: Float,
        val weightDistribution: Float,
        val stability: Float
    )

    enum class TestScenario {
        NORMAL,
        NETWORK_ERROR,
        TIMEOUT,
        RATE_LIMITED,
        INVALID_RESPONSE,
        HIGH_LATENCY,
        PERFECT_SUGGESTIONS
    }
}