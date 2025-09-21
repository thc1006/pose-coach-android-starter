package com.posecoach.suggestions

import com.google.common.truth.Truth.assertThat
import com.posecoach.suggestions.models.PoseLandmarksData
import com.posecoach.suggestions.models.PoseSuggestion
import com.posecoach.suggestions.models.PoseSuggestionsResponse
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.net.SocketTimeoutException

class ErrorHandlingWrapperTest {

    private lateinit var mockPrimaryClient: PoseSuggestionClient
    private lateinit var mockFallbackClient: PoseSuggestionClient
    private lateinit var wrapper: ErrorHandlingWrapper
    private lateinit var testLandmarks: PoseLandmarksData

    @Before
    fun setup() {
        mockPrimaryClient = mock()
        mockFallbackClient = mock()
        wrapper = ErrorHandlingWrapper(
            primaryClient = mockPrimaryClient,
            fallbackClient = mockFallbackClient,
            timeoutMs = 5000L
        )
        testLandmarks = createTestLandmarks()
    }

    @Test
    fun `should return primary client result when successful`() = runTest {
        val expectedResponse = createValidResponse()
        whenever(mockPrimaryClient.getPoseSuggestions(testLandmarks))
            .thenReturn(Result.success(expectedResponse))

        val result = wrapper.getPoseSuggestions(testLandmarks)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.suggestions).hasSize(3)
        verify(mockPrimaryClient).getPoseSuggestions(testLandmarks)
        verify(mockFallbackClient, never()).getPoseSuggestions(any())
    }

    @Test
    fun `should fallback when primary client fails`() = runTest {
        val fallbackResponse = createValidResponse()
        whenever(mockPrimaryClient.getPoseSuggestions(testLandmarks))
            .thenReturn(Result.failure(RuntimeException("API error")))
        whenever(mockFallbackClient.getPoseSuggestions(testLandmarks))
            .thenReturn(Result.success(fallbackResponse))

        val result = wrapper.getPoseSuggestions(testLandmarks)

        assertThat(result.isSuccess).isTrue()
        verify(mockPrimaryClient).getPoseSuggestions(testLandmarks)
        verify(mockFallbackClient).getPoseSuggestions(testLandmarks)
    }

    @Test
    fun `should retry on retryable errors`() = runTest {
        val successResponse = createValidResponse()
        whenever(mockPrimaryClient.getPoseSuggestions(testLandmarks))
            .thenReturn(Result.failure(SocketTimeoutException("Timeout")))
            .thenReturn(Result.success(successResponse))

        val result = wrapper.getPoseSuggestions(testLandmarks)

        assertThat(result.isSuccess).isTrue()
        verify(mockPrimaryClient, times(2)).getPoseSuggestions(testLandmarks)
        verify(mockFallbackClient, never()).getPoseSuggestions(any())
    }

    @Test
    fun `should not retry on non-retryable errors`() = runTest {
        val fallbackResponse = createValidResponse()
        whenever(mockPrimaryClient.getPoseSuggestions(testLandmarks))
            .thenReturn(Result.failure(IllegalArgumentException("Invalid input")))
        whenever(mockFallbackClient.getPoseSuggestions(testLandmarks))
            .thenReturn(Result.success(fallbackResponse))

        val result = wrapper.getPoseSuggestions(testLandmarks)

        assertThat(result.isSuccess).isTrue()
        verify(mockPrimaryClient, times(1)).getPoseSuggestions(testLandmarks)
        verify(mockFallbackClient).getPoseSuggestions(testLandmarks)
    }

    @Test
    fun `should validate and fix malformed responses`() = runTest {
        val malformedResponse = PoseSuggestionsResponse(
            suggestions = listOf(
                PoseSuggestion("", "", emptyList()), // Invalid
                PoseSuggestion("Valid Title", "Valid instruction with enough characters", listOf("NOSE")), // Valid
                PoseSuggestion("Another Title", "Another valid instruction", listOf("LEFT_SHOULDER", "RIGHT_SHOULDER")) // Valid
            )
        )
        whenever(mockPrimaryClient.getPoseSuggestions(testLandmarks))
            .thenReturn(Result.success(malformedResponse))

        val result = wrapper.getPoseSuggestions(testLandmarks)

        assertThat(result.isSuccess).isTrue()
        val response = result.getOrNull()!!
        assertThat(response.suggestions).hasSize(3) // Should fill with defaults

        // Should have filtered out invalid suggestion and added defaults
        response.suggestions.forEach { suggestion ->
            assertThat(suggestion.title).isNotEmpty()
            assertThat(suggestion.instruction.length).isAtLeast(20)
            assertThat(suggestion.targetLandmarks).isNotEmpty()
        }
    }

    @Test
    fun `should handle empty response gracefully`() = runTest {
        val emptyResponse = PoseSuggestionsResponse(emptyList())
        val fallbackResponse = createValidResponse()

        whenever(mockPrimaryClient.getPoseSuggestions(testLandmarks))
            .thenReturn(Result.success(emptyResponse))
        whenever(mockFallbackClient.getPoseSuggestions(testLandmarks))
            .thenReturn(Result.success(fallbackResponse))

        val result = wrapper.getPoseSuggestions(testLandmarks)

        assertThat(result.isSuccess).isTrue()
        verify(mockFallbackClient).getPoseSuggestions(testLandmarks)
    }

    @Test
    fun `should handle both clients failing`() = runTest {
        whenever(mockPrimaryClient.getPoseSuggestions(testLandmarks))
            .thenReturn(Result.failure(RuntimeException("Primary failed")))
        whenever(mockFallbackClient.getPoseSuggestions(testLandmarks))
            .thenReturn(Result.failure(RuntimeException("Fallback failed")))

        val result = wrapper.getPoseSuggestions(testLandmarks)

        assertThat(result.isFailure).isTrue()
        verify(mockPrimaryClient).getPoseSuggestions(testLandmarks)
        verify(mockFallbackClient).getPoseSuggestions(testLandmarks)
    }

    @Test
    fun `isAvailable should return true if either client is available`() = runTest {
        whenever(mockPrimaryClient.isAvailable()).thenReturn(false)
        whenever(mockFallbackClient.isAvailable()).thenReturn(true)

        val available = wrapper.isAvailable()

        assertThat(available).isTrue()
    }

    @Test
    fun `requiresApiKey should delegate to primary client`() {
        whenever(mockPrimaryClient.requiresApiKey()).thenReturn(true)

        val requiresKey = wrapper.requiresApiKey()

        assertThat(requiresKey).isTrue()
        verify(mockPrimaryClient).requiresApiKey()
    }

    private fun createTestLandmarks(): PoseLandmarksData {
        val landmarks = List(33) { index ->
            PoseLandmarksData.LandmarkPoint(
                index = index,
                x = 0.5f + (index * 0.01f),
                y = 0.5f + (index * 0.01f),
                z = 0f,
                visibility = 0.9f,
                presence = 0.9f
            )
        }
        return PoseLandmarksData(landmarks)
    }

    private fun createValidResponse(): PoseSuggestionsResponse {
        return PoseSuggestionsResponse(
            suggestions = listOf(
                PoseSuggestion(
                    title = "Straighten Your Back",
                    instruction = "Keep your spine aligned and shoulders relaxed for better posture",
                    targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER")
                ),
                PoseSuggestion(
                    title = "Relax Shoulders",
                    instruction = "Lower your shoulders away from your ears to reduce tension",
                    targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER")
                ),
                PoseSuggestion(
                    title = "Ground Your Feet",
                    instruction = "Feel equal weight distribution through both feet for stability",
                    targetLandmarks = listOf("LEFT_ANKLE", "RIGHT_ANKLE")
                )
            )
        )
    }
}