package com.posecoach.suggestions

data class Landmark(val x: Double, val y: Double, val z: Double, val visibility: Double? = null)

data class PoseSuggestions(val suggestions: List<Suggestion>) {
    data class Suggestion(
        val title: String,
        val instruction: String,
        val target_landmarks: List<String>
    )
}

interface PoseSuggestionClient {
    suspend fun getPoseSuggestions(landmarks: List<Landmark>): PoseSuggestions
}
