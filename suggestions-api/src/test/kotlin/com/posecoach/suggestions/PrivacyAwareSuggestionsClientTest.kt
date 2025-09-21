package com.posecoach.suggestions

import com.google.common.truth.Truth.assertThat
import com.posecoach.suggestions.models.PoseLandmarksData
import com.posecoach.suggestions.models.PoseSuggestion
import com.posecoach.suggestions.models.PoseSuggestionsResponse
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class PrivacyAwareSuggestionsClientTest {

    private lateinit var mockDelegate: PoseSuggestionClient
    private lateinit var testLandmarks: PoseLandmarksData

    @Before
    fun setup() {
        mockDelegate = mock()
        testLandmarks = createTestLandmarks()

        // Default mock behavior
        whenever(mockDelegate.requiresApiKey()).thenReturn(true)
        whenever(mockDelegate.isAvailable()).thenReturn(true)
    }

    @Test
    fun `should use delegate when privacy allows API calls`() = runTest {
        val expectedResponse = createMockResponse()
        whenever(mockDelegate.getPoseSuggestions(any())).thenReturn(Result.success(expectedResponse))

        val privacySettings = PrivacyAwareSuggestionsClient.PrivacySettings(
            allowApiCalls = true,
            allowDataTransmission = true
        )
        val client = PrivacyAwareSuggestionsClient(mockDelegate, privacySettings)

        val result = client.getPoseSuggestions(testLandmarks)

        assertThat(result.isSuccess).isTrue()
        verify(mockDelegate).getPoseSuggestions(any())
    }

    @Test
    fun `should use local fallback when API calls disabled`() = runTest {
        val privacySettings = PrivacyAwareSuggestionsClient.PrivacySettings(
            allowApiCalls = false
        )
        val client = PrivacyAwareSuggestionsClient(mockDelegate, privacySettings)

        val result = client.getPoseSuggestions(testLandmarks)

        assertThat(result.isSuccess).isTrue()
        val response = result.getOrNull()!!
        assertThat(response.suggestions).hasSize(3)
        assertThat(response.suggestions[0].title).contains("Privacy-Protected")

        verify(mockDelegate, never()).getPoseSuggestions(any())
    }

    @Test
    fun `should use local fallback when data transmission disabled`() = runTest {
        val privacySettings = PrivacyAwareSuggestionsClient.PrivacySettings(
            allowApiCalls = true,
            allowDataTransmission = false
        )
        val client = PrivacyAwareSuggestionsClient(mockDelegate, privacySettings)

        val result = client.getPoseSuggestions(testLandmarks)

        assertThat(result.isSuccess).isTrue()
        verify(mockDelegate, never()).getPoseSuggestions(any())
    }

    @Test
    fun `should use local fallback when local processing only enabled`() = runTest {
        val privacySettings = PrivacyAwareSuggestionsClient.PrivacySettings(
            localProcessingOnly = true
        )
        val client = PrivacyAwareSuggestionsClient(mockDelegate, privacySettings)

        val result = client.getPoseSuggestions(testLandmarks)

        assertThat(result.isSuccess).isTrue()
        verify(mockDelegate, never()).getPoseSuggestions(any())
    }

    @Test
    fun `should anonymize landmarks when anonymization enabled`() = runTest {
        val expectedResponse = createMockResponse()
        whenever(mockDelegate.getPoseSuggestions(any())).thenReturn(Result.success(expectedResponse))

        val privacySettings = PrivacyAwareSuggestionsClient.PrivacySettings(
            allowApiCalls = true,
            allowDataTransmission = true,
            anonymizeLandmarks = true
        )
        val client = PrivacyAwareSuggestionsClient(mockDelegate, privacySettings)

        client.getPoseSuggestions(testLandmarks)

        verify(mockDelegate).getPoseSuggestions(argThat { landmarks ->
            // Check that face landmarks (0-10) have reduced precision
            val faceLandmarks = landmarks.landmarks.filter { it.index <= 10 }
            faceLandmarks.all { it.z == 0f && it.visibility <= 0.8f }
        })
    }

    @Test
    fun `should limit precision when precision limiting enabled`() = runTest {
        val expectedResponse = createMockResponse()
        whenever(mockDelegate.getPoseSuggestions(any())).thenReturn(Result.success(expectedResponse))

        val privacySettings = PrivacyAwareSuggestionsClient.PrivacySettings(
            allowApiCalls = true,
            allowDataTransmission = true,
            limitDataPrecision = true
        )
        val client = PrivacyAwareSuggestionsClient(mockDelegate, privacySettings)

        client.getPoseSuggestions(testLandmarks)

        verify(mockDelegate).getPoseSuggestions(argThat { landmarks ->
            // Check that landmarks have limited precision
            landmarks.landmarks.all { landmark ->
                val xPrecision = countDecimalPlaces(landmark.x)
                val yPrecision = countDecimalPlaces(landmark.y)
                xPrecision <= 3 && yPrecision <= 3
            }
        })
    }

    @Test
    fun `isAvailable should delegate when API calls allowed`() = runTest {
        whenever(mockDelegate.isAvailable()).thenReturn(true)

        val privacySettings = PrivacyAwareSuggestionsClient.PrivacySettings(
            allowApiCalls = true
        )
        val client = PrivacyAwareSuggestionsClient(mockDelegate, privacySettings)

        val available = client.isAvailable()

        assertThat(available).isTrue()
        verify(mockDelegate).isAvailable()
    }

    @Test
    fun `isAvailable should return true for local processing when API disabled`() = runTest {
        val privacySettings = PrivacyAwareSuggestionsClient.PrivacySettings(
            allowApiCalls = false
        )
        val client = PrivacyAwareSuggestionsClient(mockDelegate, privacySettings)

        val available = client.isAvailable()

        assertThat(available).isTrue()
        verify(mockDelegate, never()).isAvailable()
    }

    @Test
    fun `requiresApiKey should consider privacy settings`() {
        val privacySettings = PrivacyAwareSuggestionsClient.PrivacySettings(
            allowApiCalls = false
        )
        val client = PrivacyAwareSuggestionsClient(mockDelegate, privacySettings)

        val requiresKey = client.requiresApiKey()

        assertThat(requiresKey).isFalse()
    }

    @Test
    fun `conservative factory should create restrictive settings`() {
        val client = PrivacyAwareSuggestionsClient.createConservative(mockDelegate)
        val settings = client.getPrivacySettings()

        assertThat(settings.allowApiCalls).isFalse()
        assertThat(settings.allowDataTransmission).isFalse()
        assertThat(settings.anonymizeLandmarks).isTrue()
        assertThat(settings.limitDataPrecision).isTrue()
        assertThat(settings.requireExplicitConsent).isTrue()
        assertThat(settings.localProcessingOnly).isTrue()
    }

    @Test
    fun `balanced factory should create moderate settings`() {
        val client = PrivacyAwareSuggestionsClient.createBalanced(mockDelegate)
        val settings = client.getPrivacySettings()

        assertThat(settings.allowApiCalls).isTrue()
        assertThat(settings.allowDataTransmission).isTrue()
        assertThat(settings.anonymizeLandmarks).isTrue()
        assertThat(settings.limitDataPrecision).isTrue()
        assertThat(settings.requireExplicitConsent).isTrue()
        assertThat(settings.localProcessingOnly).isFalse()
    }

    @Test
    fun `permissive factory should create open settings`() {
        val client = PrivacyAwareSuggestionsClient.createPermissive(mockDelegate)
        val settings = client.getPrivacySettings()

        assertThat(settings.allowApiCalls).isTrue()
        assertThat(settings.allowDataTransmission).isTrue()
        assertThat(settings.anonymizeLandmarks).isFalse()
        assertThat(settings.limitDataPrecision).isFalse()
        assertThat(settings.requireExplicitConsent).isFalse()
        assertThat(settings.localProcessingOnly).isFalse()
    }

    @Test
    fun `should handle delegate failures gracefully`() = runTest {
        whenever(mockDelegate.getPoseSuggestions(any())).thenReturn(
            Result.failure(RuntimeException("API error"))
        )

        val privacySettings = PrivacyAwareSuggestionsClient.PrivacySettings(
            allowApiCalls = true,
            allowDataTransmission = true
        )
        val client = PrivacyAwareSuggestionsClient(mockDelegate, privacySettings)

        val result = client.getPoseSuggestions(testLandmarks)

        // Should fall back to local processing on error
        assertThat(result.isSuccess).isTrue()
        val response = result.getOrNull()!!
        assertThat(response.suggestions).hasSize(3)
    }

    private fun createTestLandmarks(): PoseLandmarksData {
        return PoseLandmarksData(
            landmarks = List(33) { index ->
                PoseLandmarksData.LandmarkPoint(
                    index = index,
                    x = 0.123456f,
                    y = 0.789012f,
                    z = 0.345678f,
                    visibility = 0.987654f,
                    presence = 0.876543f
                )
            }
        )
    }

    private fun createMockResponse(): PoseSuggestionsResponse {
        return PoseSuggestionsResponse(
            suggestions = listOf(
                PoseSuggestion(
                    title = "Test Suggestion",
                    instruction = "This is a test suggestion from the delegate",
                    targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER")
                )
            )
        )
    }

    private fun countDecimalPlaces(value: Float): Int {
        val str = value.toString()
        val decimalIndex = str.indexOf('.')
        return if (decimalIndex >= 0) {
            str.length - decimalIndex - 1
        } else {
            0
        }
    }
}