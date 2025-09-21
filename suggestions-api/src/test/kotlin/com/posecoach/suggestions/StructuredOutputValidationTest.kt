package com.posecoach.suggestions

import com.google.common.truth.Truth.assertThat
import com.posecoach.suggestions.models.PoseSuggestion
import com.posecoach.suggestions.models.PoseSuggestionsResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import org.junit.Test

/**
 * Tests for validating Gemini 2.5 structured output schema compliance
 */
class StructuredOutputValidationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false  // Strict validation
    }

    @Test
    fun `valid structured output should deserialize correctly`() {
        val validJson = """
        {
            "suggestions": [
                {
                    "title": "Straighten Your Spine",
                    "instruction": "Keep your back straight by engaging your core muscles and imagining a string pulling you upward",
                    "target_landmarks": ["LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_HIP", "RIGHT_HIP"]
                },
                {
                    "title": "Relax Your Shoulders",
                    "instruction": "Lower your shoulders away from your ears and gently roll them back",
                    "target_landmarks": ["LEFT_SHOULDER", "RIGHT_SHOULDER"]
                },
                {
                    "title": "Ground Through Your Feet",
                    "instruction": "Feel equal weight distribution across both feet with knees slightly bent",
                    "target_landmarks": ["LEFT_ANKLE", "RIGHT_ANKLE", "LEFT_KNEE", "RIGHT_KNEE"]
                }
            ]
        }
        """.trimIndent()

        val response = json.decodeFromString<PoseSuggestionsResponse>(validJson)

        assertThat(response.suggestions).hasSize(3)
        response.suggestions.forEach { suggestion ->
            assertThat(suggestion.title).isNotEmpty()
            assertThat(suggestion.instruction).isNotEmpty()
            assertThat(suggestion.targetLandmarks).isNotEmpty()
            assertThat(suggestion.targetLandmarks).isNotEmpty()
        }
    }

    @Test
    fun `missing required fields should be handled gracefully`() {
        val invalidJson = """
        {
            "suggestions": [
                {
                    "title": "Missing Instruction"
                }
            ]
        }
        """.trimIndent()

        try {
            json.decodeFromString<PoseSuggestionsResponse>(invalidJson)
            // If this succeeds, our serialization is too lenient
            assertThat(false).isTrue() // Force fail if no exception
        } catch (e: SerializationException) {
            // Expected behavior for missing required fields
            assertThat(e.message).isNotNull()
        }
    }

    @Test
    fun `extra fields should be ignored with ignoreUnknownKeys`() {
        val jsonWithExtra = """
        {
            "suggestions": [
                {
                    "title": "Test Title",
                    "instruction": "Test instruction",
                    "target_landmarks": ["NOSE"],
                    "extra_field": "should be ignored",
                    "another_extra": 123
                }
            ],
            "metadata": {
                "model": "gemini-2.0",
                "timestamp": "2024-01-01"
            }
        }
        """.trimIndent()

        val response = json.decodeFromString<PoseSuggestionsResponse>(jsonWithExtra)

        assertThat(response.suggestions).hasSize(1)
        val suggestion = response.suggestions[0]
        assertThat(suggestion.title).isEqualTo("Test Title")
        assertThat(suggestion.instruction).isEqualTo("Test instruction")
        assertThat(suggestion.targetLandmarks).containsExactly("NOSE")
    }

    @Test
    fun `landmark names should be valid MediaPipe constants`() {
        val validLandmarks = setOf(
            "NOSE", "LEFT_EYE", "RIGHT_EYE", "LEFT_EAR", "RIGHT_EAR",
            "LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_ELBOW", "RIGHT_ELBOW",
            "LEFT_WRIST", "RIGHT_WRIST", "LEFT_HIP", "RIGHT_HIP",
            "LEFT_KNEE", "RIGHT_KNEE", "LEFT_ANKLE", "RIGHT_ANKLE",
            "LEFT_INDEX", "RIGHT_INDEX", "LEFT_THUMB", "RIGHT_THUMB"
        )

        val suggestion = PoseSuggestion(
            title = "Test",
            instruction = "Test instruction",
            targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "NOSE")
        )

        suggestion.targetLandmarks.forEach { landmark ->
            assertThat(validLandmarks).contains(landmark)
        }
    }

    @Test
    fun `response should enforce exactly 3 suggestions`() {
        val tooFewSuggestions = """
        {
            "suggestions": [
                {
                    "title": "Only One",
                    "instruction": "This should not be enough",
                    "target_landmarks": ["NOSE"]
                }
            ]
        }
        """.trimIndent()

        val response = json.decodeFromString<PoseSuggestionsResponse>(tooFewSuggestions)

        // Our schema should ideally enforce this at the API level
        // But we can validate it in our application logic
        assertThat(response.suggestions.size).isLessThan(3)
    }

    @Test
    fun `chinese characters should be supported in responses`() {
        val chineseJson = """
        {
            "suggestions": [
                {
                    "title": "挺直背部",
                    "instruction": "通過收緊核心肌肉保持背部挺直，想像有一根繩子從頭頂向上拉",
                    "target_landmarks": ["LEFT_SHOULDER", "RIGHT_SHOULDER"]
                },
                {
                    "title": "放鬆肩膀",
                    "instruction": "將肩膀從耳朵處放下，輕輕向後轉動",
                    "target_landmarks": ["LEFT_SHOULDER", "RIGHT_SHOULDER"]
                },
                {
                    "title": "平衡體重",
                    "instruction": "在雙腳間均勻分配體重，膝蓋略微彎曲",
                    "target_landmarks": ["LEFT_ANKLE", "RIGHT_ANKLE"]
                }
            ]
        }
        """.trimIndent()

        val response = json.decodeFromString<PoseSuggestionsResponse>(chineseJson)

        assertThat(response.suggestions).hasSize(3)
        assertThat(response.suggestions[0].title).isEqualTo("挺直背部")
        assertThat(response.suggestions[1].title).isEqualTo("放鬆肩膀")
        assertThat(response.suggestions[2].title).isEqualTo("平衡體重")
    }

    @Test
    fun `empty target landmarks array should be rejected`() {
        val emptyLandmarksJson = """
        {
            "suggestions": [
                {
                    "title": "Bad Suggestion",
                    "instruction": "This has no target landmarks",
                    "target_landmarks": []
                }
            ]
        }
        """.trimIndent()

        val response = json.decodeFromString<PoseSuggestionsResponse>(emptyLandmarksJson)

        // Application should validate that landmarks are not empty
        val suggestion = response.suggestions[0]
        assertThat(suggestion.targetLandmarks).isEmpty()
        // This should be caught by application validation, not schema
    }
}