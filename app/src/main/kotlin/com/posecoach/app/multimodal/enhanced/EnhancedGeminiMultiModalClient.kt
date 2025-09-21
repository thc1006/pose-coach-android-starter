package com.posecoach.app.multimodal.enhanced

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.*
import com.posecoach.app.multimodal.models.*
import com.posecoach.app.multimodal.processors.MultiModalPrivacyManager
import com.posecoach.app.privacy.EnhancedPrivacyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import android.util.Base64

/**
 * Enhanced Gemini Multi-Modal Client
 *
 * Provides advanced multi-modal AI analysis using Gemini's capabilities:
 * - Multi-modal prompt engineering for comprehensive analysis
 * - Structured output schemas for complex multi-modal insights
 * - Context-aware model selection (text, vision, audio)
 * - Efficient batching of multi-modal requests
 * - Privacy-aware processing with selective data transmission
 */
class EnhancedGeminiMultiModalClient(
    private val apiKey: String,
    private val privacyManager: EnhancedPrivacyManager,
    private val multiModalPrivacyManager: MultiModalPrivacyManager
) {

    companion object {
        private const val GEMINI_2_0_FLASH = "gemini-2.0-flash-exp"
        private const val GEMINI_1_5_PRO = "gemini-1.5-pro"
        private const val MAX_TOKENS = 2048
        private const val TEMPERATURE = 0.7f
        private const val TOP_K = 40
        private const val TOP_P = 0.95f
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    // Model configurations for different use cases
    private val multiModalAnalysisModel by lazy {
        GenerativeModel(
            modelName = GEMINI_2_0_FLASH,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = TEMPERATURE
                topK = TOP_K
                topP = TOP_P
                maxOutputTokens = MAX_TOKENS
                responseMimeType = "application/json"
                responseSchema = createMultiModalAnalysisSchema()
            }
        )
    }

    private val contextualRecommendationModel by lazy {
        GenerativeModel(
            modelName = GEMINI_1_5_PRO,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.8f
                topK = 40
                topP = 0.9f
                maxOutputTokens = 1024
                responseMimeType = "application/json"
                responseSchema = createRecommendationSchema()
            }
        )
    }

    private val emotionalAnalysisModel by lazy {
        GenerativeModel(
            modelName = GEMINI_2_0_FLASH,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.6f
                topK = 30
                topP = 0.9f
                maxOutputTokens = 1024
                responseMimeType = "application/json"
                responseSchema = createEmotionalAnalysisSchema()
            }
        )
    }

    /**
     * Perform comprehensive multi-modal analysis
     */
    suspend fun analyzeMultiModalInput(
        filteredInput: MultiModalPrivacyManager.PrivacyFilteredMultiModalInput
    ): GeminiMultiModalAnalysisResult = withContext(Dispatchers.IO) {

        try {
            if (apiKey.isBlank()) {
                return@withContext createErrorResult("API key not configured")
            }

            // Check privacy compliance
            if (!isAnalysisAllowed(filteredInput)) {
                return@withContext createErrorResult("Analysis blocked by privacy settings")
            }

            // Build multi-modal prompt
            val prompt = buildMultiModalPrompt(filteredInput)

            Timber.d("Requesting multi-modal analysis from Gemini")
            val startTime = System.currentTimeMillis()

            val response = multiModalAnalysisModel.generateContent(prompt)
            val responseText = response.text ?: throw IllegalStateException("Empty response from Gemini")

            val analysisResult = json.decodeFromString<MultiModalAnalysisResponse>(responseText)

            val processingTime = System.currentTimeMillis() - startTime
            Timber.d("Multi-modal analysis completed in ${processingTime}ms")

            GeminiMultiModalAnalysisResult(
                success = true,
                analysisResult = analysisResult,
                processingTimeMs = processingTime,
                confidence = analysisResult.overallConfidence,
                privacyCompliant = true,
                errorMessage = null
            )

        } catch (e: Exception) {
            Timber.e(e, "Error in multi-modal analysis")
            createErrorResult("Analysis failed: ${e.message}")
        }
    }

    /**
     * Generate contextual recommendations based on multi-modal understanding
     */
    suspend fun generateContextualRecommendations(
        analysisResult: MultiModalAnalysisResponse,
        userContext: UserContextData?,
        environmentContext: EnvironmentContextData?
    ): GeminiRecommendationResult = withContext(Dispatchers.IO) {

        try {
            val prompt = buildRecommendationPrompt(analysisResult, userContext, environmentContext)

            Timber.d("Requesting contextual recommendations from Gemini")
            val startTime = System.currentTimeMillis()

            val response = contextualRecommendationModel.generateContent(prompt)
            val responseText = response.text ?: throw IllegalStateException("Empty response from Gemini")

            val recommendations = json.decodeFromString<ContextualRecommendationsResponse>(responseText)

            val processingTime = System.currentTimeMillis() - startTime
            Timber.d("Contextual recommendations generated in ${processingTime}ms")

            GeminiRecommendationResult(
                success = true,
                recommendations = recommendations,
                processingTimeMs = processingTime,
                confidence = recommendations.overallConfidence,
                errorMessage = null
            )

        } catch (e: Exception) {
            Timber.e(e, "Error generating contextual recommendations")
            GeminiRecommendationResult(
                success = false,
                recommendations = null,
                processingTimeMs = 0L,
                confidence = 0f,
                errorMessage = "Recommendation generation failed: ${e.message}"
            )
        }
    }

    /**
     * Perform emotional intelligence analysis
     */
    suspend fun analyzeEmotionalIntelligence(
        emotionalData: EmotionalStateAnalysis,
        contextualFactors: List<String>
    ): GeminiEmotionalAnalysisResult = withContext(Dispatchers.IO) {

        try {
            val prompt = buildEmotionalAnalysisPrompt(emotionalData, contextualFactors)

            Timber.d("Requesting emotional intelligence analysis from Gemini")
            val startTime = System.currentTimeMillis()

            val response = emotionalAnalysisModel.generateContent(prompt)
            val responseText = response.text ?: throw IllegalStateException("Empty response from Gemini")

            val emotionalAnalysis = json.decodeFromString<EmotionalIntelligenceResponse>(responseText)

            val processingTime = System.currentTimeMillis() - startTime
            Timber.d("Emotional intelligence analysis completed in ${processingTime}ms")

            GeminiEmotionalAnalysisResult(
                success = true,
                emotionalAnalysis = emotionalAnalysis,
                processingTimeMs = processingTime,
                confidence = emotionalAnalysis.analysisConfidence,
                errorMessage = null
            )

        } catch (e: Exception) {
            Timber.e(e, "Error in emotional intelligence analysis")
            GeminiEmotionalAnalysisResult(
                success = false,
                emotionalAnalysis = null,
                processingTimeMs = 0L,
                confidence = 0f,
                errorMessage = "Emotional analysis failed: ${e.message}"
            )
        }
    }

    /**
     * Batch process multiple inputs for efficiency
     */
    suspend fun batchAnalyzeInputs(
        filteredInputs: List<MultiModalPrivacyManager.PrivacyFilteredMultiModalInput>
    ): List<GeminiMultiModalAnalysisResult> = withContext(Dispatchers.IO) {

        try {
            if (filteredInputs.isEmpty()) {
                return@withContext emptyList()
            }

            Timber.d("Batch processing ${filteredInputs.size} multi-modal inputs")

            // Process in parallel for efficiency
            val results = filteredInputs.map { input ->
                analyzeMultiModalInput(input)
            }

            Timber.d("Batch analysis completed for ${results.size} inputs")
            results

        } catch (e: Exception) {
            Timber.e(e, "Error in batch analysis")
            filteredInputs.map { createErrorResult("Batch processing failed: ${e.message}") }
        }
    }

    // Prompt building methods

    private fun buildMultiModalPrompt(
        filteredInput: MultiModalPrivacyManager.PrivacyFilteredMultiModalInput
    ): List<Content> {
        val contentParts = mutableListOf<Part>()

        // Add system prompt
        contentParts.add(
            TextPart(buildSystemPrompt())
        )

        // Add modality-specific data
        filteredInput.poseLandmarks?.let { poseData ->
            contentParts.add(
                TextPart(buildPoseAnalysisSection(poseData))
            )
        }

        filteredInput.audioSignal?.let { audioData ->
            contentParts.add(
                TextPart(buildAudioAnalysisSection(audioData))
            )
        }

        filteredInput.visualContext?.let { visualData ->
            contentParts.add(
                TextPart(buildVisualAnalysisSection(visualData))
            )
        }

        filteredInput.environmentContext?.let { envData ->
            contentParts.add(
                TextPart(buildEnvironmentAnalysisSection(envData))
            )
        }

        filteredInput.userContext?.let { userData ->
            contentParts.add(
                TextPart(buildUserContextSection(userData))
            )
        }

        // Add privacy metadata
        contentParts.add(
            TextPart(buildPrivacyContextSection(filteredInput.privacyMetadata))
        )

        // Add analysis request
        contentParts.add(
            TextPart(buildAnalysisRequest())
        )

        return listOf(Content(parts = contentParts))
    }

    private fun buildRecommendationPrompt(
        analysisResult: MultiModalAnalysisResponse,
        userContext: UserContextData?,
        environmentContext: EnvironmentContextData?
    ): String {
        return """
            Based on the comprehensive multi-modal analysis results, generate personalized contextual recommendations.

            ANALYSIS RESULTS:
            ${json.encodeToString(MultiModalAnalysisResponse.serializer(), analysisResult)}

            USER CONTEXT:
            ${userContext?.let { json.encodeToString(UserContextData.serializer(), it) } ?: "Not available"}

            ENVIRONMENT CONTEXT:
            ${environmentContext?.let { json.encodeToString(EnvironmentContextData.serializer(), it) } ?: "Not available"}

            Generate 3-5 actionable recommendations prioritized by:
            1. Safety and injury prevention
            2. Immediate performance improvement
            3. Long-term skill development
            4. Motivation and engagement
            5. Environmental optimization

            Each recommendation should include:
            - Clear, actionable title
            - Detailed implementation steps
            - Expected outcomes
            - Priority level (CRITICAL, HIGH, MEDIUM, LOW)
            - Estimated time to complete

            Return valid JSON matching the ContextualRecommendationsResponse schema.
        """.trimIndent()
    }

    private fun buildEmotionalAnalysisPrompt(
        emotionalData: EmotionalStateAnalysis,
        contextualFactors: List<String>
    ): String {
        return """
            Perform advanced emotional intelligence analysis based on multi-modal emotional indicators.

            EMOTIONAL STATE DATA:
            ${json.encodeToString(EmotionalStateAnalysis.serializer(), emotionalData)}

            CONTEXTUAL FACTORS:
            ${contextualFactors.joinToString("\n- ", "- ")}

            Provide comprehensive emotional intelligence insights including:
            1. Emotional pattern analysis and trends
            2. Motivation enhancement strategies
            3. Stress management recommendations
            4. Confidence building approaches
            5. Adaptive coaching style suggestions

            Focus on:
            - Understanding emotional triggers and responses
            - Identifying opportunities for emotional support
            - Providing personalized emotional guidance
            - Maintaining positive motivation
            - Building long-term emotional resilience

            Return valid JSON matching the EmotionalIntelligenceResponse schema.
        """.trimIndent()
    }

    // System prompt and section builders

    private fun buildSystemPrompt(): String {
        return """
            You are an advanced multi-modal AI fitness and wellness coach with expertise in:
            - Biomechanics and movement analysis
            - Psychology and motivation
            - Fitness training and exercise science
            - Safety and injury prevention
            - Adaptive coaching methodologies

            Analyze the provided multi-modal data (pose, audio, visual, environmental, user context)
            to provide comprehensive, personalized coaching insights.

            Your analysis should be:
            - Evidence-based and scientifically sound
            - Personalized to the user's context and needs
            - Safety-focused with injury prevention priority
            - Motivational and encouraging
            - Actionable with clear implementation guidance

            Always respect privacy settings and data minimization principles.
        """.trimIndent()
    }

    private fun buildPoseAnalysisSection(poseData: MultiModalPrivacyManager.PrivacyFilteredData): String {
        return """
            POSE ANALYSIS DATA:
            Privacy Level: ${poseData.privacyLevel}
            Applied Filters: ${poseData.appliedFilters.joinToString(", ")}
            Data Retention: ${poseData.retainedDataPercentage * 100}%

            Pose Data: ${poseData.filteredData?.toString() ?: "Privacy-filtered"}

            Analyze for:
            - Postural alignment and biomechanics
            - Movement quality and efficiency
            - Stability and balance indicators
            - Potential injury risks
            - Form and technique assessment
        """.trimIndent()
    }

    private fun buildAudioAnalysisSection(audioData: MultiModalPrivacyManager.PrivacyFilteredData): String {
        return """
            AUDIO ANALYSIS DATA:
            Privacy Level: ${audioData.privacyLevel}
            Applied Filters: ${audioData.appliedFilters.joinToString(", ")}
            Data Retention: ${audioData.retainedDataPercentage * 100}%

            Audio Data: ${audioData.filteredData?.toString() ?: "Privacy-filtered"}

            Analyze for:
            - Breathing patterns and respiratory health
            - Voice stress and emotional indicators
            - Speech clarity and communication quality
            - Motivational tone and engagement levels
            - Environmental audio factors
        """.trimIndent()
    }

    private fun buildVisualAnalysisSection(visualData: MultiModalPrivacyManager.PrivacyFilteredData): String {
        return """
            VISUAL ANALYSIS DATA:
            Privacy Level: ${visualData.privacyLevel}
            Applied Filters: ${visualData.appliedFilters.joinToString(", ")}
            Data Retention: ${visualData.retainedDataPercentage * 100}%

            Visual Data: ${visualData.filteredData?.toString() ?: "Privacy-filtered"}

            Analyze for:
            - Facial expressions and emotional state
            - Environmental safety and optimization
            - Equipment and space utilization
            - Lighting and visibility conditions
            - Non-verbal communication cues
        """.trimIndent()
    }

    private fun buildEnvironmentAnalysisSection(envData: MultiModalPrivacyManager.PrivacyFilteredData): String {
        return """
            ENVIRONMENT ANALYSIS DATA:
            Privacy Level: ${envData.privacyLevel}
            Applied Filters: ${envData.appliedFilters.joinToString(", ")}
            Data Retention: ${envData.retainedDataPercentage * 100}%

            Environment Data: ${envData.filteredData?.toString() ?: "Privacy-filtered"}

            Analyze for:
            - Workout space optimization
            - Safety considerations and hazard assessment
            - Equipment availability and utilization
            - Social and contextual factors
            - Time and location appropriateness
        """.trimIndent()
    }

    private fun buildUserContextSection(userData: MultiModalPrivacyManager.PrivacyFilteredData): String {
        return """
            USER CONTEXT DATA:
            Privacy Level: ${userData.privacyLevel}
            Applied Filters: ${userData.appliedFilters.joinToString(", ")}
            Data Retention: ${userData.retainedDataPercentage * 100}%

            User Data: ${userData.filteredData?.toString() ?: "Privacy-filtered"}

            Consider for:
            - Fitness goals and experience level
            - Personal preferences and limitations
            - Motivation and engagement patterns
            - Progress tracking and achievements
            - Adaptive coaching requirements
        """.trimIndent()
    }

    private fun buildPrivacyContextSection(privacyMetadata: MultiModalPrivacyManager.PrivacyMetadata): String {
        return """
            PRIVACY CONTEXT:
            Applied Filters: ${privacyMetadata.appliedFilters.joinToString(", ")}
            Data Retention Time: ${privacyMetadata.dataRetentionTime}ms
            Encryption Applied: ${privacyMetadata.encryptionApplied}
            Anonymization Level: ${privacyMetadata.anonymizationLevel}

            Note: Respect all privacy constraints in your analysis and recommendations.
        """.trimIndent()
    }

    private fun buildAnalysisRequest(): String {
        return """
            ANALYSIS REQUEST:

            Provide a comprehensive multi-modal analysis with:

            1. INTEGRATED INSIGHTS: Synthesize findings across all available modalities
            2. COACHING PRIORITIES: Identify top 3 areas for improvement
            3. SAFETY ASSESSMENT: Evaluate and address any safety concerns
            4. MOTIVATION ANALYSIS: Assess engagement and motivation levels
            5. PERSONALIZED RECOMMENDATIONS: Provide specific, actionable guidance
            6. PROGRESS INDICATORS: Suggest metrics for tracking improvement
            7. ADAPTIVE STRATEGIES: Recommend coaching approach adjustments

            Return structured JSON response with high confidence scores and detailed reasoning.
        """.trimIndent()
    }

    // Schema definitions

    private fun createMultiModalAnalysisSchema(): Schema {
        return Schema(
            name = "MultiModalAnalysisResponse",
            description = "Comprehensive multi-modal fitness analysis results",
            type = FunctionType.OBJECT,
            properties = mapOf(
                "overallConfidence" to Schema(
                    type = FunctionType.NUMBER,
                    description = "Overall confidence in the analysis (0.0-1.0)",
                    minimum = 0.0,
                    maximum = 1.0
                ),
                "integratedInsights" to Schema(
                    type = FunctionType.ARRAY,
                    description = "Key insights synthesized across modalities",
                    items = Schema(
                        type = FunctionType.OBJECT,
                        properties = mapOf(
                            "insight" to Schema(type = FunctionType.STRING),
                            "supportingModalities" to Schema(
                                type = FunctionType.ARRAY,
                                items = Schema(type = FunctionType.STRING)
                            ),
                            "confidence" to Schema(type = FunctionType.NUMBER),
                            "category" to Schema(type = FunctionType.STRING)
                        ),
                        required = listOf("insight", "supportingModalities", "confidence", "category")
                    )
                ),
                "coachingPriorities" to Schema(
                    type = FunctionType.ARRAY,
                    description = "Top 3 coaching priorities",
                    maxItems = 3,
                    items = Schema(
                        type = FunctionType.OBJECT,
                        properties = mapOf(
                            "priority" to Schema(type = FunctionType.STRING),
                            "reasoning" to Schema(type = FunctionType.STRING),
                            "urgency" to Schema(type = FunctionType.STRING),
                            "expectedImpact" to Schema(type = FunctionType.STRING)
                        ),
                        required = listOf("priority", "reasoning", "urgency", "expectedImpact")
                    )
                ),
                "safetyAssessment" to Schema(
                    type = FunctionType.OBJECT,
                    properties = mapOf(
                        "overallSafetyScore" to Schema(type = FunctionType.NUMBER),
                        "identifiedRisks" to Schema(
                            type = FunctionType.ARRAY,
                            items = Schema(type = FunctionType.STRING)
                        ),
                        "safetyRecommendations" to Schema(
                            type = FunctionType.ARRAY,
                            items = Schema(type = FunctionType.STRING)
                        )
                    ),
                    required = listOf("overallSafetyScore", "identifiedRisks", "safetyRecommendations")
                ),
                "motivationAnalysis" to Schema(
                    type = FunctionType.OBJECT,
                    properties = mapOf(
                        "currentMotivationLevel" to Schema(type = FunctionType.NUMBER),
                        "motivationalFactors" to Schema(
                            type = FunctionType.ARRAY,
                            items = Schema(type = FunctionType.STRING)
                        ),
                        "engagementStrategies" to Schema(
                            type = FunctionType.ARRAY,
                            items = Schema(type = FunctionType.STRING)
                        )
                    ),
                    required = listOf("currentMotivationLevel", "motivationalFactors", "engagementStrategies")
                )
            ),
            required = listOf("overallConfidence", "integratedInsights", "coachingPriorities", "safetyAssessment", "motivationAnalysis")
        )
    }

    private fun createRecommendationSchema(): Schema {
        return Schema(
            name = "ContextualRecommendationsResponse",
            description = "Personalized contextual recommendations",
            type = FunctionType.OBJECT,
            properties = mapOf(
                "overallConfidence" to Schema(
                    type = FunctionType.NUMBER,
                    description = "Confidence in recommendations (0.0-1.0)"
                ),
                "recommendations" to Schema(
                    type = FunctionType.ARRAY,
                    description = "List of 3-5 actionable recommendations",
                    minItems = 3,
                    maxItems = 5,
                    items = Schema(
                        type = FunctionType.OBJECT,
                        properties = mapOf(
                            "title" to Schema(type = FunctionType.STRING),
                            "description" to Schema(type = FunctionType.STRING),
                            "implementationSteps" to Schema(
                                type = FunctionType.ARRAY,
                                items = Schema(type = FunctionType.STRING)
                            ),
                            "expectedOutcome" to Schema(type = FunctionType.STRING),
                            "priority" to Schema(
                                type = FunctionType.STRING,
                                enum = listOf("CRITICAL", "HIGH", "MEDIUM", "LOW")
                            ),
                            "estimatedTimeMinutes" to Schema(type = FunctionType.INTEGER),
                            "category" to Schema(type = FunctionType.STRING)
                        ),
                        required = listOf("title", "description", "implementationSteps", "expectedOutcome", "priority", "category")
                    )
                )
            ),
            required = listOf("overallConfidence", "recommendations")
        )
    }

    private fun createEmotionalAnalysisSchema(): Schema {
        return Schema(
            name = "EmotionalIntelligenceResponse",
            description = "Emotional intelligence analysis and strategies",
            type = FunctionType.OBJECT,
            properties = mapOf(
                "analysisConfidence" to Schema(type = FunctionType.NUMBER),
                "emotionalPatterns" to Schema(
                    type = FunctionType.ARRAY,
                    items = Schema(type = FunctionType.STRING)
                ),
                "motivationStrategies" to Schema(
                    type = FunctionType.ARRAY,
                    items = Schema(type = FunctionType.STRING)
                ),
                "stressManagement" to Schema(
                    type = FunctionType.ARRAY,
                    items = Schema(type = FunctionType.STRING)
                ),
                "confidenceBuilding" to Schema(
                    type = FunctionType.ARRAY,
                    items = Schema(type = FunctionType.STRING)
                ),
                "coachingStyleRecommendations" to Schema(
                    type = FunctionType.ARRAY,
                    items = Schema(type = FunctionType.STRING)
                )
            ),
            required = listOf("analysisConfidence", "emotionalPatterns", "motivationStrategies")
        )
    }

    // Utility methods

    private fun isAnalysisAllowed(
        filteredInput: MultiModalPrivacyManager.PrivacyFilteredMultiModalInput
    ): Boolean {
        // Check if any modality data is available after privacy filtering
        return filteredInput.poseLandmarks != null ||
               filteredInput.audioSignal != null ||
               filteredInput.visualContext != null ||
               filteredInput.environmentContext != null ||
               filteredInput.userContext != null
    }

    private fun createErrorResult(message: String): GeminiMultiModalAnalysisResult {
        return GeminiMultiModalAnalysisResult(
            success = false,
            analysisResult = null,
            processingTimeMs = 0L,
            confidence = 0f,
            privacyCompliant = true,
            errorMessage = message
        )
    }

    // Result data classes
    @Serializable
    data class MultiModalAnalysisResponse(
        val overallConfidence: Float,
        val integratedInsights: List<IntegratedInsight>,
        val coachingPriorities: List<CoachingPriority>,
        val safetyAssessment: SafetyAssessment,
        val motivationAnalysis: MotivationAnalysis
    )

    @Serializable
    data class IntegratedInsight(
        val insight: String,
        val supportingModalities: List<String>,
        val confidence: Float,
        val category: String
    )

    @Serializable
    data class CoachingPriority(
        val priority: String,
        val reasoning: String,
        val urgency: String,
        val expectedImpact: String
    )

    @Serializable
    data class SafetyAssessment(
        val overallSafetyScore: Float,
        val identifiedRisks: List<String>,
        val safetyRecommendations: List<String>
    )

    @Serializable
    data class MotivationAnalysis(
        val currentMotivationLevel: Float,
        val motivationalFactors: List<String>,
        val engagementStrategies: List<String>
    )

    @Serializable
    data class ContextualRecommendationsResponse(
        val overallConfidence: Float,
        val recommendations: List<ContextualRecommendation>
    )

    @Serializable
    data class ContextualRecommendation(
        val title: String,
        val description: String,
        val implementationSteps: List<String>,
        val expectedOutcome: String,
        val priority: String,
        val estimatedTimeMinutes: Int? = null,
        val category: String
    )

    @Serializable
    data class EmotionalIntelligenceResponse(
        val analysisConfidence: Float,
        val emotionalPatterns: List<String>,
        val motivationStrategies: List<String>,
        val stressManagement: List<String>,
        val confidenceBuilding: List<String>,
        val coachingStyleRecommendations: List<String>
    )

    data class GeminiMultiModalAnalysisResult(
        val success: Boolean,
        val analysisResult: MultiModalAnalysisResponse?,
        val processingTimeMs: Long,
        val confidence: Float,
        val privacyCompliant: Boolean,
        val errorMessage: String?
    )

    data class GeminiRecommendationResult(
        val success: Boolean,
        val recommendations: ContextualRecommendationsResponse?,
        val processingTimeMs: Long,
        val confidence: Float,
        val errorMessage: String?
    )

    data class GeminiEmotionalAnalysisResult(
        val success: Boolean,
        val emotionalAnalysis: EmotionalIntelligenceResponse?,
        val processingTimeMs: Long,
        val confidence: Float,
        val errorMessage: String?
    )
}