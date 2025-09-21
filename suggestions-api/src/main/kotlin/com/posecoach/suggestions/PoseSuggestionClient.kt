package com.posecoach.suggestions

import com.posecoach.suggestions.models.PoseLandmarksData
import com.posecoach.suggestions.models.PoseSuggestionsResponse

interface PoseSuggestionClient {
    suspend fun getPoseSuggestions(landmarks: PoseLandmarksData): Result<PoseSuggestionsResponse>
    suspend fun isAvailable(): Boolean
    fun requiresApiKey(): Boolean
}