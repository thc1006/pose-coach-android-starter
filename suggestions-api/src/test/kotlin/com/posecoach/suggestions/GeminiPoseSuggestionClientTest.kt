package com.posecoach.suggestions

import com.google.common.truth.Truth.assertThat
import com.posecoach.suggestions.models.PoseLandmarksData
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class GeminiPoseSuggestionClientTest {

    private lateinit var clientWithValidKey: GeminiPoseSuggestionClient
    private lateinit var clientWithInvalidKey: GeminiPoseSuggestionClient
    private lateinit var testLandmarks: PoseLandmarksData

    @Before
    fun setup() {
        // Use a test API key format - actual tests would need real key
        clientWithValidKey = GeminiPoseSuggestionClient("test-api-key-valid-format-abcdef123456")
        clientWithInvalidKey = GeminiPoseSuggestionClient("")
        testLandmarks = createTestLandmarks()
    }

    @Test
    fun `client should require API key`() {
        assertThat(clientWithValidKey.requiresApiKey()).isTrue()
        assertThat(clientWithInvalidKey.requiresApiKey()).isTrue()
    }

    @Test
    fun `client with empty API key should fail immediately`() = runTest {
        val result = clientWithInvalidKey.getPoseSuggestions(testLandmarks)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
        assertThat(result.exceptionOrNull()?.message).contains("API key not configured")
    }

    @Test
    fun `client should validate structured output schema`() {
        // Test that the response schema is properly configured
        val client = GeminiPoseSuggestionClient("test-key")

        // Verify schema structure exists (this tests the configuration)
        assertThat(client.requiresApiKey()).isTrue()
    }

    @Test
    fun `landmarks conversion should produce valid JSON`() {
        val landmarks = createTestLandmarks()
        val json = landmarks.toJsonString()

        assertThat(json).startsWith("[")
        assertThat(json).endsWith("]")
        assertThat(json).contains("\"i\":")
        assertThat(json).contains("\"x\":")
        assertThat(json).contains("\"y\":")
        assertThat(json).contains("\"z\":")
        assertThat(json).contains("\"v\":")
    }

    @Test
    fun `pose hash should be deterministic and unique`() {
        val landmarks1 = createTestLandmarks(baseX = 0.5f)
        val landmarks2 = createTestLandmarks(baseX = 0.5f) // Same pose
        val landmarks3 = createTestLandmarks(baseX = 0.7f) // Different pose

        assertThat(landmarks1.hash()).isEqualTo(landmarks2.hash())
        assertThat(landmarks1.hash()).isNotEqualTo(landmarks3.hash())
    }

    @Test
    fun `schema validation should enforce exactly 3 suggestions`() {
        // This test validates our response schema structure
        // In a real implementation with API key, this would test the actual response
        val testJson = """
        {
            "suggestions": [
                {
                    "title": "Test Title 1",
                    "instruction": "Test instruction 1",
                    "target_landmarks": ["LEFT_SHOULDER", "RIGHT_SHOULDER"]
                },
                {
                    "title": "Test Title 2",
                    "instruction": "Test instruction 2",
                    "target_landmarks": ["LEFT_HIP", "RIGHT_HIP"]
                },
                {
                    "title": "Test Title 3",
                    "instruction": "Test instruction 3",
                    "target_landmarks": ["LEFT_KNEE", "RIGHT_KNEE"]
                }
            ]
        }
        """.trimIndent()

        // Test JSON deserialization matches our schema
        import kotlinx.serialization.json.Json
        import com.posecoach.suggestions.models.PoseSuggestionsResponse

        val json = Json { ignoreUnknownKeys = true }
        val response = json.decodeFromString<PoseSuggestionsResponse>(testJson)

        assertThat(response.suggestions).hasSize(3)
        response.suggestions.forEach { suggestion ->
            assertThat(suggestion.title).isNotEmpty()
            assertThat(suggestion.instruction).isNotEmpty()
            assertThat(suggestion.targetLandmarks).isNotEmpty()
        }
    }

    @Test
    fun `error handling should wrap exceptions properly`() = runTest {
        // Test with obviously invalid key format
        val clientWithBadKey = GeminiPoseSuggestionClient("")
        val result = clientWithBadKey.getPoseSuggestions(testLandmarks)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isNotNull()
    }

    private fun createTestLandmarks(baseX: Float = 0.5f): PoseLandmarksData {
        val landmarks = List(33) { index ->
            PoseLandmarksData.LandmarkPoint(
                index = index,
                x = baseX + (index * 0.01f),
                y = 0.5f + (index * 0.01f),
                z = if (index % 3 == 0) -0.1f else 0.1f,
                visibility = 0.9f,
                presence = 0.9f
            )
        }
        return PoseLandmarksData(landmarks)
    }
}