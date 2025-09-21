package com.posecoach.suggestions

class FakeSuggestionClient : PoseSuggestionClient {
    override suspend fun getPoseSuggestions(landmarks: List<Landmark>): PoseSuggestions =
        PoseSuggestions(
            listOf(
                PoseSuggestions.Suggestion(
                    title = "創造 S 曲線",
                    instruction = "右肩微抬 5°、重心移到左髖，讓身體呈現柔和 S 線條。",
                    target_landmarks = listOf("RIGHT_SHOULDER","LEFT_HIP")
                ),
                PoseSuggestions.Suggestion(
                    title = "延展脖頸",
                    instruction = "下巴收 5° 並微微前伸，避免雙下巴並拉長頸部。",
                    target_landmarks = listOf("NOSE","LEFT_SHOULDER","RIGHT_SHOULDER")
                ),
                PoseSuggestions.Suggestion(
                    title = "手部線條",
                    instruction = "左手腕外旋，指尖放鬆；避免手掌正對鏡頭。",
                    target_landmarks = listOf("LEFT_WRIST","LEFT_INDEX","LEFT_THUMB")
                ),
            )
        )
}
