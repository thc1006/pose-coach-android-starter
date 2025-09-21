package com.posecoach.suggestions

import com.posecoach.suggestions.models.PoseLandmarksData
import com.posecoach.suggestions.models.PoseSuggestion
import com.posecoach.suggestions.models.PoseSuggestionsResponse

class FakeSuggestionClient : PoseSuggestionClient {

    override suspend fun getPoseSuggestions(landmarks: PoseLandmarksData): Result<PoseSuggestionsResponse> {
        return Result.success(
            PoseSuggestionsResponse(
                suggestions = listOf(
                    PoseSuggestion(
                        title = "創造 S 曲線",
                        instruction = "右肩微抬 5°、重心移到左髖，讓身體呈現柔和 S 線條。",
                        targetLandmarks = listOf("RIGHT_SHOULDER", "LEFT_HIP")
                    ),
                    PoseSuggestion(
                        title = "延展脖頸",
                        instruction = "下巴收 5° 並微微前伸，避免雙下巴並拉長頸部。",
                        targetLandmarks = listOf("NOSE", "LEFT_SHOULDER", "RIGHT_SHOULDER")
                    ),
                    PoseSuggestion(
                        title = "手部線條",
                        instruction = "左手腕外旋，指尖放鬆；避免手掌正對鏡頭。",
                        targetLandmarks = listOf("LEFT_WRIST", "LEFT_INDEX", "LEFT_THUMB")
                    )
                )
            )
        )
    }

    override suspend fun isAvailable(): Boolean = true

    override fun requiresApiKey(): Boolean = false
}
