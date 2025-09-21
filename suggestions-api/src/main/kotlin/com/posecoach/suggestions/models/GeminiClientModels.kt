package com.posecoach.suggestions.models

import kotlinx.serialization.Serializable

/**
 * Status information for Gemini client
 */
data class GeminiClientStatus(
    val hasApiKey: Boolean,
    val apiKeySource: String,
    val isApiKeyValidated: Boolean,
    val hasPrivacyConsent: Boolean,
    val rateLimitStatus: RateLimitStatus,
    val privacyLevel: PrivacyLevel
) {
    val isFullyAvailable: Boolean
        get() = hasApiKey && hasPrivacyConsent && !rateLimitStatus.isLimited

    val statusMessage: String
        get() = when {
            !hasApiKey -> "API key not configured"
            !hasPrivacyConsent -> "Privacy consent required"
            rateLimitStatus.isLimited -> "Rate limited: ${rateLimitStatus.getNextAvailableSlot()}"
            !isApiKeyValidated -> "API key not validated"
            else -> "Ready"
        }
}

/**
 * Rate limiting status and statistics
 */
data class RateLimitStatus(
    val requestsInLastMinute: Int,
    val maxRequestsPerMinute: Int,
    val requestsToday: Int,
    val maxRequestsPerDay: Int,
    val consecutiveFailures: Int,
    val isLimited: Boolean
) {
    val minuteUtilization: Float = requestsInLastMinute.toFloat() / maxRequestsPerMinute
    val dailyUtilization: Float = requestsToday.toFloat() / maxRequestsPerDay

    fun canMakeRequest(): Boolean = !isLimited

    fun getNextAvailableSlot(): String {
        return when {
            requestsToday >= maxRequestsPerDay -> "Tomorrow"
            requestsInLastMinute >= maxRequestsPerMinute -> "Next minute"
            else -> "Now"
        }
    }
}

/**
 * Privacy levels for data anonymization
 */
enum class PrivacyLevel {
    MINIMAL,    // Only essential landmarks, no sensitive data
    STANDARD,   // Reduced precision for sensitive areas
    FULL        // All landmarks with differential privacy
}

/**
 * Gemini API error types
 */
sealed class GeminiError : Exception() {
    object NoApiKey : GeminiError()
    object NoConsent : GeminiError()
    object RateLimited : GeminiError()
    object Timeout : GeminiError()
    object InvalidResponse : GeminiError()
    object NetworkError : GeminiError()
    data class ApiError(override val message: String) : GeminiError()
}

/**
 * Request configuration for Gemini client
 */
data class GeminiRequestConfig(
    val timeoutMs: Long = 8000L,
    val maxRetries: Int = 3,
    val privacyLevel: PrivacyLevel = PrivacyLevel.STANDARD,
    val enableCaching: Boolean = true,
    val waitForRateLimit: Boolean = true
)

/**
 * Metrics for monitoring Gemini client performance
 */
@Serializable
data class GeminiClientMetrics(
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val cachedResponses: Long,
    val averageResponseTimeMs: Long,
    val rateLimitHits: Long,
    val privacyViolations: Long,
    val lastRequestTimestamp: Long
) {
    val successRate: Float = if (totalRequests > 0) successfulRequests.toFloat() / totalRequests else 0f
    val cacheHitRate: Float = if (totalRequests > 0) cachedResponses.toFloat() / totalRequests else 0f
}

/**
 * Configuration for structured output validation
 */
data class StructuredOutputConfig(
    val enforceExactCount: Boolean = true,
    val requiredSuggestionCount: Int = 3,
    val minTitleLength: Int = 5,
    val maxTitleLength: Int = 50,
    val minInstructionLength: Int = 30,
    val maxInstructionLength: Int = 200,
    val minTargetLandmarks: Int = 2,
    val maxTargetLandmarks: Int = 6,
    val allowedLandmarks: Set<String> = setOf(
        "NOSE", "LEFT_EYE", "RIGHT_EYE", "LEFT_EAR", "RIGHT_EAR",
        "LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_ELBOW", "RIGHT_ELBOW",
        "LEFT_WRIST", "RIGHT_WRIST", "LEFT_HIP", "RIGHT_HIP",
        "LEFT_KNEE", "RIGHT_KNEE", "LEFT_ANKLE", "RIGHT_ANKLE",
        "LEFT_INDEX", "RIGHT_INDEX", "LEFT_THUMB", "RIGHT_THUMB"
    )
)