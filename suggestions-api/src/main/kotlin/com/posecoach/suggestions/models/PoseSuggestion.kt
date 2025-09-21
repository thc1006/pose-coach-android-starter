package com.posecoach.suggestions.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class PoseSuggestionsResponse(
    @SerialName("suggestions")
    val suggestions: List<PoseSuggestion>
)

@Serializable
data class PoseSuggestion(
    @SerialName("title")
    val title: String,

    @SerialName("instruction")
    val instruction: String,

    @SerialName("target_landmarks")
    val targetLandmarks: List<String>
)

data class PoseLandmarksData(
    val landmarks: List<LandmarkPoint>,
    val timestamp: Long = System.currentTimeMillis()
) {
    data class LandmarkPoint(
        val index: Int,
        val x: Float,
        val y: Float,
        val z: Float,
        val visibility: Float,
        val presence: Float
    )

    fun toJsonString(): String {
        return landmarks.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]"
        ) { landmark ->
            """{"i":${landmark.index},"x":${landmark.x},"y":${landmark.y},"z":${landmark.z},"v":${landmark.visibility}}"""
        }
    }

    fun hash(): String {
        val hashInput = landmarks.take(12)
            .map { "${it.x.toInt()}_${it.y.toInt()}" }
            .joinToString("")
        return hashInput.hashCode().toString()
    }
}