package com.posecoach.app.suggestions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.posecoach.suggestions.GeminiPoseSuggestionClient
import com.posecoach.suggestions.models.PoseSuggestion
import com.posecoach.corepose.models.PoseLandmarkResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection

/**
 * Gemini Structured Output Integration Test
 *
 * Tests: Pose landmarks → Gemini API → Exactly 3 suggestions
 * This test MUST FAIL initially according to CLAUDE.md TDD methodology
 *
 * Expected behavior after implementation:
 * - Valid JSON with responseSchema compliance
 * - Exactly 3 coaching suggestions per request
 * - Proper error handling for API failures
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class GeminiIntegrationTest {

    @MockK
    private lateinit var mockHttpConnection: HttpURLConnection

    @MockK
    private lateinit var mockPoseLandmarkResult: PoseLandmarkResult

    private lateinit var geminiClient: GeminiPoseSuggestionClient
    private lateinit var gson: Gson

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        gson = Gson()

        // This will fail - GeminiPoseSuggestionClient not fully implemented yet
        geminiClient = GeminiPoseSuggestionClient(
            apiKey = "test_api_key",
            modelName = "gemini-1.5-flash"
        )
    }

    @Test
    fun `should return exactly 3 suggestions with valid JSON schema`() = runTest {
        // ARRANGE - This test MUST FAIL initially
        val mockValidPose = createMockValidPose()
        val expectedSuggestionCount = 3

        // Mock successful Gemini API response
        val mockApiResponse = createMockGeminiApiResponse()
        coEvery { mockHttpConnection.responseCode } returns 200
        coEvery { mockHttpConnection.inputStream } returns mockApiResponse.byteInputStream()

        // ACT - This will fail as full implementation doesn't exist
        val suggestions = geminiClient.getSuggestions(mockValidPose)

        // ASSERT - Define expected behavior to guide implementation
        assertThat(suggestions).isNotNull()
        assertThat(suggestions).hasSize(expectedSuggestionCount)

        // Verify each suggestion has required fields
        suggestions.forEach { suggestion ->
            assertThat(suggestion.title).isNotEmpty()
            assertThat(suggestion.description).isNotEmpty()
            assertThat(suggestion.priority).isIn(1..3)
            assertThat(suggestion.category).isNotEmpty()
        }

        coVerify { mockHttpConnection.setRequestMethod("POST") }
        coVerify { mockHttpConnection.setRequestProperty("Content-Type", "application/json") }
    }

    @Test
    fun `should comply with Gemini responseSchema for structured output`() = runTest {
        // ARRANGE
        val mockPose = createMockValidPose()
        val expectedResponseSchema = createExpectedResponseSchema()

        // ACT
        val suggestions = geminiClient.getSuggestions(mockPose)
        val jsonResponse = gson.toJson(suggestions)

        // ASSERT - Verify JSON structure matches expected schema
        val parsedResponse = gson.fromJson(jsonResponse, Array<PoseSuggestion>::class.java)
        assertThat(parsedResponse).hasLength(3)

        // Verify schema compliance
        parsedResponse.forEach { suggestion ->
            // Required fields per schema
            assertThat(suggestion.title).isNotNull()
            assertThat(suggestion.description).isNotNull()
            assertThat(suggestion.priority).isNotNull()
            assertThat(suggestion.category).isNotNull()

            // Field constraints
            assertThat(suggestion.title.length).isAtMost(100)
            assertThat(suggestion.description.length).isAtMost(500)
            assertThat(suggestion.priority).isIn(1..3)
            assertThat(suggestion.category).isIn(listOf("form", "technique", "safety", "performance"))
        }
    }

    @Test
    fun `should handle different pose scenarios with appropriate suggestions`() = runTest {
        // ARRANGE
        val squatPose = createMockSquatPose()
        val pushupPose = createMockPushupPose()
        val plankPose = createMockPlankPose()

        // ACT
        val squatSuggestions = geminiClient.getSuggestions(squatPose)
        val pushupSuggestions = geminiClient.getSuggestions(pushupPose)
        val plankSuggestions = geminiClient.getSuggestions(plankPose)

        // ASSERT - Different poses should yield different suggestions
        assertThat(squatSuggestions).isNotEqualTo(pushupSuggestions)
        assertThat(pushupSuggestions).isNotEqualTo(plankSuggestions)

        // Verify context-appropriate suggestions
        val squatCategories = squatSuggestions.map { it.category }
        assertThat(squatCategories).contains("form") // Squat form is critical

        val pushupCategories = pushupSuggestions.map { it.category }
        assertThat(pushupCategories).contains("technique") // Pushup technique matters
    }

    @Test
    fun `should handle API rate limiting gracefully`() = runTest {
        // ARRANGE
        val mockPose = createMockValidPose()

        // Mock rate limit response
        coEvery { mockHttpConnection.responseCode } returns 429
        coEvery { mockHttpConnection.getHeaderField("Retry-After") } returns "60"

        // ACT & ASSERT
        try {
            geminiClient.getSuggestions(mockPose)
            assertThat(false).isTrue() // Should not reach here
        } catch (e: Exception) {
            assertThat(e.message).contains("rate limit")
            assertThat(geminiClient.getRetryAfterSeconds()).isEqualTo(60)
        }
    }

    @Test
    fun `should handle malformed API responses with proper error handling`() = runTest {
        // ARRANGE
        val mockPose = createMockValidPose()
        val malformedResponse = "{ invalid json }"

        coEvery { mockHttpConnection.responseCode } returns 200
        coEvery { mockHttpConnection.inputStream } returns malformedResponse.byteInputStream()

        // ACT & ASSERT
        try {
            geminiClient.getSuggestions(mockPose)
            assertThat(false).isTrue() // Should throw exception
        } catch (e: JsonSyntaxException) {
            assertThat(e.message).contains("malformed")
        }
    }

    @Test
    fun `should validate API key before making requests`() = runTest {
        // ARRANGE
        val invalidClient = GeminiPoseSuggestionClient(
            apiKey = "",
            modelName = "gemini-1.5-flash"
        )
        val mockPose = createMockValidPose()

        // ACT & ASSERT
        try {
            invalidClient.getSuggestions(mockPose)
            assertThat(false).isTrue() // Should throw exception
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("API key")
        }
    }

    @Test
    fun `should include pose context in API request payload`() = runTest {
        // ARRANGE
        val mockPose = createMockValidPose()
        var capturedPayload: String? = null

        // Mock to capture request payload
        coEvery {
            mockHttpConnection.outputStream.write(any<ByteArray>())
        } answers {
            capturedPayload = String(firstArg<ByteArray>())
        }

        // ACT
        geminiClient.getSuggestions(mockPose)

        // ASSERT
        assertThat(capturedPayload).isNotNull()
        assertThat(capturedPayload).contains("landmarks")
        assertThat(capturedPayload).contains("confidence")
        assertThat(capturedPayload).contains("responseSchema")
    }

    // Helper methods - These will also fail as types don't exist yet
    private fun createMockValidPose(): PoseLandmarkResult {
        TODO("Implement mock valid pose with proper landmarks")
    }

    private fun createMockSquatPose(): PoseLandmarkResult {
        TODO("Implement mock squat pose landmarks")
    }

    private fun createMockPushupPose(): PoseLandmarkResult {
        TODO("Implement mock pushup pose landmarks")
    }

    private fun createMockPlankPose(): PoseLandmarkResult {
        TODO("Implement mock plank pose landmarks")
    }

    private fun createMockGeminiApiResponse(): String {
        return """
        {
            "suggestions": [
                {
                    "title": "Improve knee alignment",
                    "description": "Keep your knees aligned with your toes during the squat movement",
                    "priority": 1,
                    "category": "form"
                },
                {
                    "title": "Maintain neutral spine",
                    "description": "Keep your back straight and avoid excessive forward lean",
                    "priority": 2,
                    "category": "technique"
                },
                {
                    "title": "Control descent speed",
                    "description": "Lower yourself slowly to maintain control and muscle engagement",
                    "priority": 3,
                    "category": "performance"
                }
            ]
        }
        """.trimIndent()
    }

    private fun createExpectedResponseSchema(): Map<String, Any> {
        TODO("Implement expected Gemini response schema definition")
    }
}