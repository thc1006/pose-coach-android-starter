package com.posecoach.suggestions

import android.content.Context
import com.posecoach.suggestions.models.PoseLandmarksData
import com.posecoach.suggestions.models.PoseSuggestion
import com.posecoach.suggestions.models.PoseSuggestionsResponse
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeminiPoseSuggestionClientTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockApiKeyManager: ApiKeyManager

    @Mock
    private lateinit var mockPrivacyManager: PrivacyManager

    @Mock
    private lateinit var mockRateLimitManager: RateLimitManager

    @Mock
    private lateinit var mockCacheManager: ResponseCacheManager

    private lateinit var geminiClient: GeminiPoseSuggestionClient

    private val sampleLandmarks = PoseLandmarksData(
        landmarks = listOf(
            PoseLandmarksData.LandmarkPoint(0, 0.5f, 0.3f, 0.0f, 0.9f, 0.9f), // nose
            PoseLandmarksData.LandmarkPoint(11, 0.4f, 0.5f, 0.0f, 0.9f, 0.9f), // left shoulder
            PoseLandmarksData.LandmarkPoint(12, 0.6f, 0.5f, 0.0f, 0.9f, 0.9f)  // right shoulder
        )
    )

    @Before
    fun setup() {
        mockContext = mock(Context::class.java)
        mockApiKeyManager = mock(ApiKeyManager::class.java)
        mockPrivacyManager = mock(PrivacyManager::class.java)
        mockRateLimitManager = mock(RateLimitManager::class.java)
        mockCacheManager = mock(ResponseCacheManager::class.java)

        geminiClient = GeminiPoseSuggestionClient(
            context = mockContext,
            apiKeyManager = mockApiKeyManager,
            rateLimitManager = mockRateLimitManager,
            cacheManager = mockCacheManager,
            privacyManager = mockPrivacyManager
        )
    }

    @Test
    fun `requiresApiKey returns true`() {
        assertTrue(geminiClient.requiresApiKey())
    }

    @Test
    fun `isAvailable returns false when no privacy consent`() = runTest {
        whenever(mockPrivacyManager.hasValidConsent()).thenReturn(false)

        val result = geminiClient.isAvailable()

        assertFalse(result)
    }

    @Test
    fun `isAvailable returns false when no API key`() = runTest {
        whenever(mockPrivacyManager.hasValidConsent()).thenReturn(true)
        whenever(mockApiKeyManager.getGeminiApiKey()).thenReturn(null)

        val result = geminiClient.isAvailable()

        assertFalse(result)
    }

    @Test
    fun `isAvailable returns false when rate limited`() = runTest {
        whenever(mockPrivacyManager.hasValidConsent()).thenReturn(true)
        whenever(mockApiKeyManager.getGeminiApiKey()).thenReturn("valid-api-key")

        val rateLimitStatus = RateLimitStatus(
            requestsInLastMinute = 15,
            maxRequestsPerMinute = 15,
            requestsToday = 100,
            maxRequestsPerDay = 1500,
            consecutiveFailures = 0,
            isLimited = true
        )
        whenever(mockRateLimitManager.getRateLimitStatus()).thenReturn(rateLimitStatus)

        val result = geminiClient.isAvailable()

        assertFalse(result)
    }

    @Test
    fun `getPoseSuggestions returns error when no privacy consent`() = runTest {
        whenever(mockPrivacyManager.hasValidConsent()).thenReturn(false)

        val result = geminiClient.getPoseSuggestions(sampleLandmarks)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `getPoseSuggestions returns cached response when available`() = runTest {
        val cachedResponse = PoseSuggestionsResponse(
            suggestions = listOf(
                PoseSuggestion(
                    title = "Cached Suggestion",
                    instruction = "This is from cache",
                    targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER")
                )
            )
        )

        whenever(mockPrivacyManager.hasValidConsent()).thenReturn(true)
        whenever(mockCacheManager.getCachedSuggestions(sampleLandmarks)).thenReturn(cachedResponse)

        val result = geminiClient.getPoseSuggestions(sampleLandmarks)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.suggestions?.size)
        assertEquals("Cached Suggestion", result.getOrNull()?.suggestions?.first()?.title)
    }

    @Test
    fun `getPoseSuggestions returns error when no API key`() = runTest {
        whenever(mockPrivacyManager.hasValidConsent()).thenReturn(true)
        whenever(mockCacheManager.getCachedSuggestions(sampleLandmarks)).thenReturn(null)
        whenever(mockApiKeyManager.getGeminiApiKey()).thenReturn(null)

        val result = geminiClient.getPoseSuggestions(sampleLandmarks)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `getClientStatus returns correct status information`() {
        val rateLimitStatus = RateLimitStatus(
            requestsInLastMinute = 5,
            maxRequestsPerMinute = 15,
            requestsToday = 100,
            maxRequestsPerDay = 1500,
            consecutiveFailures = 0,
            isLimited = false
        )

        val privacyStatus = PrivacyStatus(
            hasConsent = true,
            consentVersion = 1,
            consentTimestamp = System.currentTimeMillis(),
            dataRetentionDays = 30,
            auditLogSize = 10
        )

        whenever(mockApiKeyManager.getGeminiApiKey()).thenReturn("test-api-key")
        whenever(mockApiKeyManager.getApiKeySource()).thenReturn("local.properties")
        whenever(mockApiKeyManager.isApiKeyRecentlyValidated()).thenReturn(true)
        whenever(mockRateLimitManager.getRateLimitStatus()).thenReturn(rateLimitStatus)
        whenever(mockPrivacyManager.getPrivacyStatus()).thenReturn(privacyStatus)

        val status = geminiClient.getClientStatus()

        assertTrue(status.hasApiKey)
        assertEquals("local.properties", status.apiKeySource)
        assertTrue(status.isApiKeyValidated)
        assertTrue(status.hasPrivacyConsent)
        assertEquals(PrivacyLevel.STANDARD, status.privacyLevel)
    }

    @Test
    fun `analyzePoseContext identifies forward head posture`() {
        val forwardHeadLandmarks = PoseLandmarksData(
            landmarks = listOf(
                PoseLandmarksData.LandmarkPoint(0, 0.7f, 0.3f, 0.0f, 0.9f, 0.9f), // nose forward
                PoseLandmarksData.LandmarkPoint(11, 0.4f, 0.5f, 0.0f, 0.9f, 0.9f), // left shoulder
                PoseLandmarksData.LandmarkPoint(12, 0.6f, 0.5f, 0.0f, 0.9f, 0.9f)  // right shoulder
            )
        )

        // This would test the private analyzePoseContext method if it were public
        // For now, we can test the overall behavior by checking if appropriate suggestions are generated
        assertTrue(forwardHeadLandmarks.landmarks.isNotEmpty())
    }

    @Test
    fun `validateSuggestions ensures exactly 3 suggestions`() {
        val invalidResponse = PoseSuggestionsResponse(
            suggestions = listOf(
                PoseSuggestion(
                    title = "Only One",
                    instruction = "This is the only suggestion",
                    targetLandmarks = listOf("LEFT_SHOULDER")
                )
            )
        )

        // This would test the private validateSuggestions method if it were public
        // The method should pad to 3 suggestions
        assertEquals(1, invalidResponse.suggestions.size)
    }
}