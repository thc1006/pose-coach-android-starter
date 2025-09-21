package com.posecoach.suggestions

import com.posecoach.suggestions.models.PoseLandmarksData
import com.posecoach.suggestions.models.PoseSuggestionsResponse

/**
 * Legacy compatibility layer - keeping for backward compatibility
 * Use PoseLandmarksData and PoseSuggestionsResponse for new implementations
 */
@Deprecated("Use PoseLandmarksData instead", ReplaceWith("PoseLandmarksData.LandmarkPoint"))
data class Landmark(val x: Double, val y: Double, val z: Double, val visibility: Double? = null)

@Deprecated("Use PoseSuggestionsResponse instead", ReplaceWith("PoseSuggestionsResponse"))
data class PoseSuggestions(val suggestions: List<Suggestion>) {
    @Deprecated("Use PoseSuggestion instead", ReplaceWith("PoseSuggestion"))
    data class Suggestion(
        val title: String,
        val instruction: String,
        val target_landmarks: List<String>
    )
}

/**
 * Main interface for pose suggestion clients
 */
interface PoseSuggestionClient {
    /**
     * Get pose suggestions based on MediaPipe landmarks
     * @param landmarks The pose landmarks data from MediaPipe
     * @return Result containing suggestions or error
     */
    suspend fun getPoseSuggestions(landmarks: PoseLandmarksData): Result<PoseSuggestionsResponse>

    /**
     * Check if the client is available and ready to make requests
     */
    suspend fun isAvailable(): Boolean

    /**
     * Check if this client requires an API key to function
     */
    fun requiresApiKey(): Boolean

    /**
     * Legacy method for backward compatibility
     */
    @Deprecated("Use getPoseSuggestions(PoseLandmarksData) instead")
    suspend fun getPoseSuggestions(landmarks: List<Landmark>): PoseSuggestions {
        // Convert legacy format to new format
        val landmarksData = PoseLandmarksData(
            landmarks = landmarks.mapIndexed { index, landmark ->
                PoseLandmarksData.LandmarkPoint(
                    index = index,
                    x = landmark.x.toFloat(),
                    y = landmark.y.toFloat(),
                    z = landmark.z.toFloat(),
                    visibility = landmark.visibility?.toFloat() ?: 1.0f,
                    presence = 1.0f
                )
            }
        )

        return getPoseSuggestions(landmarksData).getOrThrow().let { response ->
            PoseSuggestions(
                response.suggestions.map { suggestion ->
                    PoseSuggestions.Suggestion(
                        title = suggestion.title,
                        instruction = suggestion.instruction,
                        target_landmarks = suggestion.targetLandmarks
                    )
                }
            )
        }
    }
}
