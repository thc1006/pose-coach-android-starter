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

// PoseSuggestionClient interface is defined in PoseSuggestionClient.kt
// This file contains only legacy compatibility types
