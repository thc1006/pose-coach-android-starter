package com.posecoach.suggestions

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.*
import com.posecoach.suggestions.models.PoseLandmarksData
import com.posecoach.suggestions.models.PoseSuggestion
import com.posecoach.suggestions.models.PoseSuggestionsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.concurrent.CancellationException

class GeminiPoseSuggestionClient(
    private val context: Context,
    private val apiKeyManager: ApiKeyManager = ApiKeyManager(context),
    private val rateLimitManager: RateLimitManager = RateLimitManager(),
    private val cacheManager: ResponseCacheManager = ResponseCacheManager(context),
    private val privacyManager: PrivacyManager = PrivacyManager(context),
    private val timeoutMs: Long = 8000L // 8 second timeout
) : PoseSuggestionClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun createGenerativeModel(apiKey: String): GenerativeModel {
        return GenerativeModel(
            modelName = "gemini-2.0-flash-exp",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 1024
                responseMimeType = "application/json"
                responseSchema = Schema(
                    name = "PoseSuggestionsResponse",
                    description = "Structured pose improvement suggestions with exactly 3 suggestions",
                    type = FunctionType.OBJECT,
                    properties = mapOf(
                        "suggestions" to Schema(
                            type = FunctionType.ARRAY,
                            description = "Array of exactly 3 pose improvement suggestions",
                            minItems = 3,
                            maxItems = 3,
                            items = Schema(
                                type = FunctionType.OBJECT,
                                description = "Individual pose suggestion with title, instruction, and target landmarks",
                                properties = mapOf(
                                    "title" to Schema(
                                        type = FunctionType.STRING,
                                        description = "Concise title (20-50 characters) describing the improvement",
                                        minLength = 5,
                                        maxLength = 50
                                    ),
                                    "instruction" to Schema(
                                        type = FunctionType.STRING,
                                        description = "Detailed, actionable instruction (50-200 characters) for pose improvement",
                                        minLength = 30,
                                        maxLength = 200
                                    ),
                                    "target_landmarks" to Schema(
                                        type = FunctionType.ARRAY,
                                        description = "Array of 2-6 MediaPipe landmark names to focus on",
                                        minItems = 2,
                                        maxItems = 6,
                                        items = Schema(
                                            type = FunctionType.STRING,
                                            description = "MediaPipe landmark name (e.g., LEFT_SHOULDER, RIGHT_HIP)",
                                            enum = listOf(
                                                "NOSE", "LEFT_EYE", "RIGHT_EYE", "LEFT_EAR", "RIGHT_EAR",
                                                "LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_ELBOW", "RIGHT_ELBOW",
                                                "LEFT_WRIST", "RIGHT_WRIST", "LEFT_HIP", "RIGHT_HIP",
                                                "LEFT_KNEE", "RIGHT_KNEE", "LEFT_ANKLE", "RIGHT_ANKLE",
                                                "LEFT_INDEX", "RIGHT_INDEX", "LEFT_THUMB", "RIGHT_THUMB"
                                            )
                                        )
                                    )
                                ),
                                required = listOf("title", "instruction", "target_landmarks")
                            )
                        )
                    ),
                    required = listOf("suggestions")
                )
            }
        )
    }

    companion object {
        private const val SYSTEM_PROMPT = """
You are a professional pose coach and movement expert with expertise in biomechanics and postural analysis.
Analyze the provided 33-point MediaPipe body landmarks and return exactly 3 prioritized suggestions for pose improvement.

Core Analysis Framework:
1. **Postural Alignment**: Check head-neck-spine alignment, shoulder level, pelvic alignment
2. **Joint Mechanics**: Analyze joint angles, muscle tension patterns, movement efficiency
3. **Balance & Stability**: Assess weight distribution, center of gravity, base of support
4. **Functional Movement**: Consider movement quality, compensation patterns, asymmetries

Suggestion Requirements:
- **Title**: Concise, actionable heading (20-50 characters)
- **Instruction**: Specific, implementable guidance with clear cues (50-200 characters)
- **Target Landmarks**: 2-6 relevant MediaPipe landmarks that user should focus on

MediaPipe Landmark Reference (33 points):
• Head/Face: 0=NOSE, 1-6=EYES, 7-8=EARS, 9-10=MOUTH
• Upper Body: 11-12=SHOULDERS, 13-14=ELBOWS, 15-16=WRISTS, 17-22=FINGERS
• Lower Body: 23-24=HIPS, 25-26=KNEES, 27-28=ANKLES, 29-32=FEET

Prioritization Order:
1. Safety-critical postural issues (e.g., excessive forward head, severe asymmetry)
2. Primary movement quality improvements (e.g., core stability, shoulder alignment)
3. Fine-tuning and optimization (e.g., breathing mechanics, micro-adjustments)

Response must be valid JSON matching the exact schema structure.
"""
    }

    override suspend fun getPoseSuggestions(landmarks: PoseLandmarksData): Result<PoseSuggestionsResponse> {
        // Check privacy consent first
        if (!privacyManager.hasValidConsent()) {
            Timber.w("No valid privacy consent for pose analysis")
            return Result.failure(SecurityException("Privacy consent required for pose analysis"))
        }

        // Check for cached response first
        val cachedResponse = cacheManager.getCachedSuggestions(landmarks)
        if (cachedResponse != null) {
            Timber.d("Returning cached suggestions for pose hash: ${landmarks.hash()}")
            return Result.success(cachedResponse)
        }

        // Get API key
        val apiKey = apiKeyManager.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            Timber.e("Gemini API key not configured")
            return Result.failure(IllegalStateException("API key not configured"))
        }

        return withContext(Dispatchers.IO) {
            try {
                // Check rate limits
                if (!rateLimitManager.canMakeRequest(waitIfLimited = true)) {
                    Timber.w("Rate limit exceeded, request blocked")
                    return@withContext Result.failure(IllegalStateException("Rate limit exceeded"))
                }

                // Anonymize landmarks for privacy
                val anonymizedLandmarks = privacyManager.anonymizeLandmarks(landmarks, PrivacyLevel.STANDARD)
                val landmarksJson = anonymizedLandmarks.toJsonString()
                val prompt = buildPrompt(anonymizedLandmarks, landmarksJson)

                Timber.d("Requesting suggestions from Gemini for pose hash: ${landmarks.hash()}")

                // Make request with timeout and retry logic
                val response = withTimeout(timeoutMs) {
                    val model = createGenerativeModel(apiKey)
                    model.generateContent(prompt)
                }

                val responseText = response.text ?: throw IllegalStateException("Empty response from Gemini")
                val suggestions = json.decodeFromString<PoseSuggestionsResponse>(responseText)

                Timber.d("Received ${suggestions.suggestions.size} suggestions from Gemini")

                // Validate response structure
                val validatedSuggestions = validateSuggestions(suggestions)

                // Cache the response
                cacheManager.cacheSuggestions(landmarks, validatedSuggestions)

                // Record successful request
                rateLimitManager.recordSuccess()
                apiKeyManager.markApiKeyAsValidated(true)

                Timber.d("Successfully processed ${validatedSuggestions.suggestions.size} suggestions")
                Result.success(validatedSuggestions)

            } catch (e: CancellationException) {
                Timber.w("Request timeout after ${timeoutMs}ms")
                rateLimitManager.recordFailure(e)
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Error getting suggestions from Gemini")
                rateLimitManager.recordFailure(e)
                apiKeyManager.markApiKeyAsValidated(false)
                Result.failure(e)
            }
        }
    }

    override suspend fun isAvailable(): Boolean {
        // Check if we have consent and API key
        if (!privacyManager.hasValidConsent()) {
            Timber.d("Gemini client not available: no privacy consent")
            return false
        }

        val apiKey = apiKeyManager.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            Timber.d("Gemini client not available: no API key")
            return false
        }

        // Check rate limits
        val rateLimitStatus = rateLimitManager.getRateLimitStatus()
        if (rateLimitStatus.isLimited) {
            Timber.d("Gemini client not available: rate limited")
            return false
        }

        // Use cached validation result if recent
        if (apiKeyManager.isApiKeyRecentlyValidated()) {
            return true
        }

        // Test API connectivity
        return try {
            withContext(Dispatchers.IO) {
                withTimeout(5000L) { // 5 second timeout for availability check
                    val testModel = createGenerativeModel(apiKey)
                    val response = testModel.generateContent("test")
                    val isAvailable = response.text != null

                    apiKeyManager.markApiKeyAsValidated(isAvailable)
                    Timber.d("Gemini API availability test: $isAvailable")

                    isAvailable
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Gemini API not available")
            apiKeyManager.markApiKeyAsValidated(false)
            false
        }
    }

    override fun requiresApiKey(): Boolean = true

    /**
     * Get detailed status of the Gemini client
     */
    fun getClientStatus(): GeminiClientStatus {
        val apiKey = apiKeyManager.getGeminiApiKey()
        val rateLimitStatus = rateLimitManager.getRateLimitStatus()
        val privacyStatus = privacyManager.getPrivacyStatus()

        return GeminiClientStatus(
            hasApiKey = !apiKey.isNullOrBlank(),
            apiKeySource = apiKeyManager.getApiKeySource(),
            isApiKeyValidated = apiKeyManager.isApiKeyRecentlyValidated(),
            hasPrivacyConsent = privacyStatus.hasConsent,
            rateLimitStatus = rateLimitStatus,
            privacyLevel = PrivacyLevel.STANDARD
        )
    }

    /**
     * Retry failed request with exponential backoff
     */
    suspend fun retryWithBackoff(
        landmarks: PoseLandmarksData,
        maxRetries: Int = 3
    ): Result<PoseSuggestionsResponse> {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                val result = getPoseSuggestions(landmarks)
                if (result.isSuccess) {
                    return result
                }
                lastException = result.exceptionOrNull() as? Exception
            } catch (e: Exception) {
                lastException = e
                Timber.w(e, "Retry attempt ${attempt + 1} failed")

                // Exponential backoff: 1s, 2s, 4s
                if (attempt < maxRetries - 1) {
                    val delayMs = 1000L * (1 shl attempt)
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        }

        return Result.failure(lastException ?: Exception("All retry attempts failed"))
    }

    data class GeminiClientStatus(
        val hasApiKey: Boolean,
        val apiKeySource: String,
        val isApiKeyValidated: Boolean,
        val hasPrivacyConsent: Boolean,
        val rateLimitStatus: RateLimitStatus,
        val privacyLevel: PrivacyLevel
    )}

    private fun buildPrompt(landmarks: PoseLandmarksData, landmarksJson: String): String {
        val poseAnalysis = analyzePoseContext(landmarks)

        return """
            $SYSTEM_PROMPT

            Current pose landmarks (33 MediaPipe points):
            $landmarksJson

            Pose Context Analysis:
            ${poseAnalysis.joinToString("\n")}

            Based on this pose analysis, provide exactly 3 improvement suggestions prioritized by importance.
            Focus on the most impactful corrections first. Ensure suggestions are:
            1. Immediately actionable
            2. Biomechanically sound
            3. Appropriate for the detected pose context

            Return valid JSON matching the required schema structure.
        """.trimIndent()
    }

    private fun analyzePoseContext(landmarks: PoseLandmarksData): List<String> {
        val analysis = mutableListOf<String>()

        if (landmarks.landmarks.size >= 33) {
            // Head position analysis
            val nose = landmarks.landmarks[0]
            val leftShoulder = landmarks.landmarks[11]
            val rightShoulder = landmarks.landmarks[12]
            val shoulderMidpoint = (leftShoulder.x + rightShoulder.x) / 2

            if (nose.x > shoulderMidpoint + 0.05f) {
                analysis.add("• Forward head posture detected (nose ahead of shoulders)")
            }

            // Shoulder level analysis
            val shoulderHeightDiff = kotlin.math.abs(leftShoulder.y - rightShoulder.y)
            if (shoulderHeightDiff > 0.03f) {
                analysis.add("• Shoulder asymmetry detected (${String.format("%.2f", shoulderHeightDiff)} height difference)")
            }

            // Arm position analysis
            val leftWrist = landmarks.landmarks[15]
            val rightWrist = landmarks.landmarks[16]
            val shouldersY = (leftShoulder.y + rightShoulder.y) / 2
            val wristsY = (leftWrist.y + rightWrist.y) / 2

            if (wristsY < shouldersY - 0.1f) {
                analysis.add("• Arms raised position detected")
            }

            // Hip-knee alignment
            val leftHip = landmarks.landmarks[23]
            val rightHip = landmarks.landmarks[24]
            val leftKnee = landmarks.landmarks[25]
            val rightKnee = landmarks.landmarks[26]

            val hipsY = (leftHip.y + rightHip.y) / 2
            val kneesY = (leftKnee.y + rightKnee.y) / 2

            if (kneesY > hipsY) {
                analysis.add("• Standing posture detected")
            } else {
                analysis.add("• Seated or bent posture detected")
            }
        }

        if (analysis.isEmpty()) {
            analysis.add("• Standard pose analysis - general postural recommendations")
        }

        return analysis
    }

    private fun validateSuggestions(suggestions: PoseSuggestionsResponse): PoseSuggestionsResponse {
        // Ensure exactly 3 suggestions
        val validSuggestions = suggestions.suggestions.take(3).toMutableList()

        // Fill with default suggestions if needed
        while (validSuggestions.size < 3) {
            validSuggestions.add(
                PoseSuggestion(
                    title = "Maintain Good Posture",
                    instruction = "Keep your spine aligned and shoulders relaxed for optimal posture",
                    targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_HIP", "RIGHT_HIP")
                )
            )
        }

        // Validate each suggestion
        val validatedSuggestions = validSuggestions.map { suggestion ->
            PoseSuggestion(
                title = suggestion.title.take(50).ifEmpty { "Posture Improvement" },
                instruction = suggestion.instruction.take(200).let {
                    if (it.length < 30) "$it - Focus on maintaining proper alignment" else it
                },
                targetLandmarks = suggestion.targetLandmarks.take(6).let {
                    if (it.size < 2) it + listOf("LEFT_SHOULDER", "RIGHT_SHOULDER") else it
                }
            )
        }

        return PoseSuggestionsResponse(validatedSuggestions)
    }
}