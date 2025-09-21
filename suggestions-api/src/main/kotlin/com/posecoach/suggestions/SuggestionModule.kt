package com.posecoach.suggestions

import android.content.Context
import com.posecoach.suggestions.models.PrivacyLevel
import timber.log.Timber

/**
 * Dependency injection module for pose suggestion components
 * Provides centralized configuration and initialization
 */
class SuggestionModule(private val context: Context) {

    // Core managers - singleton instances
    private val apiKeyManager by lazy { ApiKeyManager(context) }
    private val privacyManager by lazy { PrivacyManager(context) }
    private val rateLimitManager by lazy { RateLimitManager() }
    private val cacheManager by lazy { ResponseCacheManager(context) }

    // Client factory
    private val clientFactory by lazy { PoseSuggestionClientFactory(context) }

    /**
     * Get the API key manager instance
     */
    fun provideApiKeyManager(): ApiKeyManager = apiKeyManager

    /**
     * Get the privacy manager instance
     */
    fun providePrivacyManager(): PrivacyManager = privacyManager

    /**
     * Get the rate limit manager instance
     */
    fun provideRateLimitManager(): RateLimitManager = rateLimitManager

    /**
     * Get the cache manager instance
     */
    fun provideCacheManager(): ResponseCacheManager = cacheManager

    /**
     * Get the client factory instance
     */
    fun provideClientFactory(): PoseSuggestionClientFactory = clientFactory

    /**
     * Create a configured Gemini client
     */
    fun provideGeminiClient(): GeminiPoseSuggestionClient {
        return GeminiPoseSuggestionClient(
            context = context,
            apiKeyManager = apiKeyManager,
            rateLimitManager = rateLimitManager,
            cacheManager = cacheManager,
            privacyManager = privacyManager
        )
    }

    /**
     * Create a fake client for testing/offline use
     */
    fun provideFakeClient(): FakePoseSuggestionClient = FakePoseSuggestionClient()

    /**
     * Get the best available client based on current configuration
     */
    suspend fun provideBestClient(): PoseSuggestionClient {
        return clientFactory.createBestAvailableClient()
    }

    /**
     * Create a composite client with fallback
     */
    suspend fun provideCompositeClient(): CompositePoseSuggestionClient {
        return clientFactory.createCompositeClient()
    }

    /**
     * Initialize the module and perform any necessary setup
     */
    suspend fun initialize() {
        // Clean up expired cache entries
        cacheManager.cleanExpiredEntries()

        // Reset rate limiter if needed
        if (System.currentTimeMillis() % (24 * 60 * 60 * 1000) < 60 * 1000) {
            // Reset daily if within first minute of the day
            rateLimitManager.reset()
        }

        Timber.d("SuggestionModule initialized")
    }

    /**
     * Get module status and configuration
     */
    suspend fun getModuleStatus(): SuggestionModuleStatus {
        val apiKeyStatus = apiKeyManager.hasValidApiKey()
        val privacyStatus = privacyManager.hasValidConsent()
        val rateLimitStatus = rateLimitManager.getRateLimitStatus()
        val cacheStats = cacheManager.getCacheStats()
        val clientStatus = clientFactory.getClientStatus()

        return SuggestionModuleStatus(
            isInitialized = true,
            apiKeyConfigured = apiKeyStatus,
            privacyConsentGiven = privacyStatus,
            rateLimitStatus = rateLimitStatus,
            cacheStats = cacheStats,
            clientStatus = clientStatus
        )
    }

    /**
     * Shutdown the module and cleanup resources
     */
    suspend fun shutdown() {
        // Perform any necessary cleanup
        Timber.d("SuggestionModule shutting down")
    }
}

/**
 * Configuration options for the suggestion module
 */
data class SuggestionModuleConfig(
    val enableCaching: Boolean = true,
    val enableRateLimiting: Boolean = true,
    val defaultPrivacyLevel: PrivacyLevel = PrivacyLevel.STANDARD,
    val timeoutMs: Long = 8000L,
    val maxRetries: Int = 3,
    val cacheExpiry: Long = 60 * 60 * 1000L, // 1 hour
    val rateLimitPerMinute: Int = 15,
    val rateLimitPerDay: Int = 1500
)

/**
 * Status information for the suggestion module
 */
data class SuggestionModuleStatus(
    val isInitialized: Boolean,
    val apiKeyConfigured: Boolean,
    val privacyConsentGiven: Boolean,
    val rateLimitStatus: RateLimitStatus,
    val cacheStats: CacheStats,
    val clientStatus: ClientFactoryStatus
) {
    val isFullyOperational: Boolean
        get() = isInitialized && apiKeyConfigured && privacyConsentGiven && !rateLimitStatus.isLimited

    val readyForRequests: Boolean
        get() = clientStatus.geminiAvailable || clientStatus.fakeAvailable
}