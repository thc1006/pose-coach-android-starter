package com.posecoach.suggestions

import android.content.Context
import timber.log.Timber

/**
 * Factory for creating appropriate PoseSuggestionClient instances
 * with fallback strategy and privacy controls
 */
class PoseSuggestionClientFactory(
    private val context: Context
) {

    private val apiKeyManager = ApiKeyManager(context)

    /**
     * Creates the best available PoseSuggestionClient based on configuration and availability
     */
    suspend fun createClient(
        preferReal: Boolean = true,
        respectPrivacySettings: Boolean = true,
        privacyLevel: PrivacyLevel = PrivacyLevel.BALANCED
    ): PoseSuggestionClient {

        // Check privacy settings if required
        if (respectPrivacySettings && !apiKeyManager.isApiEnabled()) {
            Timber.d("API calls disabled by privacy settings, using fake client")
            return FakePoseSuggestionClient()
        }

        // Try to create real client if preferred and API key is available
        if (preferReal && apiKeyManager.hasValidApiKey()) {
            val apiKey = apiKeyManager.getGeminiApiKey()
            if (!apiKey.isNullOrBlank()) {
                val realClient = GeminiPoseSuggestionClient(apiKey)

                // Test availability
                return if (realClient.isAvailable()) {
                    Timber.d("Using real Gemini client with structured output")
                    val client = if (respectPrivacySettings) {
                        createPrivacyAwareClient(realClient, privacyLevel)
                    } else {
                        realClient
                    }
                    client
                } else {
                    Timber.w("Gemini client not available, falling back to fake client")
                    val fallbackClient = FakePoseSuggestionClient()
                    if (respectPrivacySettings) {
                        createPrivacyAwareClient(fallbackClient, privacyLevel)
                    } else {
                        fallbackClient
                    }
                }
            }
        }

        // Fallback to fake client
        Timber.d("Using fake client for pose suggestions")
        val baseClient = FakePoseSuggestionClient()

        // Wrap with privacy controls if enabled
        return if (respectPrivacySettings) {
            createPrivacyAwareClient(baseClient, privacyLevel)
        } else {
            baseClient
        }
    }

    /**
     * Creates a fake client for testing purposes
     */
    fun createFakeClient(): PoseSuggestionClient {
        return FakePoseSuggestionClient()
    }

    /**
     * Creates a real client with provided API key (for testing)
     */
    fun createRealClient(apiKey: String): PoseSuggestionClient {
        return GeminiPoseSuggestionClient(apiKey)
    }

    /**
     * Gets the current API key status
     */
    fun getApiKeyStatus(): ApiKeyStatus {
        return when {
            !apiKeyManager.isApiEnabled() -> ApiKeyStatus.DISABLED_BY_PRIVACY
            !apiKeyManager.hasValidApiKey() -> ApiKeyStatus.NOT_CONFIGURED
            else -> ApiKeyStatus.CONFIGURED
        }
    }

    /**
     * Configures API key and enables API usage
     */
    fun configureApiKey(apiKey: String, enableApi: Boolean = true) {
        apiKeyManager.setGeminiApiKey(apiKey)
        apiKeyManager.setApiEnabled(enableApi)
        Timber.d("API key configured and API enabled: $enableApi")
    }

    /**
     * Enables or disables API usage (privacy control)
     */
    fun setApiEnabled(enabled: Boolean) {
        apiKeyManager.setApiEnabled(enabled)
        Timber.d("API usage set to: $enabled")
    }

    enum class ApiKeyStatus {
        CONFIGURED,
        NOT_CONFIGURED,
        DISABLED_BY_PRIVACY
    }

    enum class PrivacyLevel {
        CONSERVATIVE,  // Maximum privacy protection, local processing only
        BALANCED,      // Moderate privacy with API access but data protection
        PERMISSIVE     // Minimal privacy restrictions, full API access
    }

    private fun createPrivacyAwareClient(
        baseClient: PoseSuggestionClient,
        privacyLevel: PrivacyLevel
    ): PoseSuggestionClient {
        return when (privacyLevel) {
            PrivacyLevel.CONSERVATIVE -> PrivacyAwareSuggestionsClient.createConservative(baseClient)
            PrivacyLevel.BALANCED -> PrivacyAwareSuggestionsClient.createBalanced(baseClient)
            PrivacyLevel.PERMISSIVE -> PrivacyAwareSuggestionsClient.createPermissive(baseClient)
        }
    }
}