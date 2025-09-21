package com.posecoach.suggestions

import com.posecoach.suggestions.models.PoseLandmarksData
import com.posecoach.suggestions.models.PoseSuggestion
import com.posecoach.suggestions.models.PoseSuggestionsResponse
import kotlinx.coroutines.delay
import timber.log.Timber

class FakePoseSuggestionClient : PoseSuggestionClient {

    private val defaultSuggestions = listOf(
        PoseSuggestion(
            title = "Straighten Your Back",
            instruction = "Keep your spine aligned by imagining a string pulling you up from the top of your head",
            targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_HIP", "RIGHT_HIP")
        ),
        PoseSuggestion(
            title = "Relax Your Shoulders",
            instruction = "Lower your shoulders away from your ears and roll them back slightly",
            targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_ELBOW", "RIGHT_ELBOW")
        ),
        PoseSuggestion(
            title = "Balance Your Weight",
            instruction = "Distribute your weight evenly between both feet, keeping knees slightly bent",
            targetLandmarks = listOf("LEFT_HIP", "RIGHT_HIP", "LEFT_KNEE", "RIGHT_KNEE", "LEFT_ANKLE", "RIGHT_ANKLE")
        )
    )

    private val advancedSuggestions = mapOf(
        "poor_posture" to listOf(
            PoseSuggestion(
                title = "Fix Forward Head Posture",
                instruction = "Pull your chin back and lengthen the back of your neck, imagining a string pulling the crown of your head upward",
                targetLandmarks = listOf("NOSE", "LEFT_EAR", "RIGHT_EAR", "LEFT_SHOULDER", "RIGHT_SHOULDER")
            ),
            PoseSuggestion(
                title = "Correct Rounded Shoulders",
                instruction = "Squeeze your shoulder blades together gently and rotate your palms forward to open your chest",
                targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_ELBOW", "RIGHT_ELBOW")
            ),
            PoseSuggestion(
                title = "Activate Deep Core",
                instruction = "Draw your belly button gently toward your spine while maintaining normal breathing",
                targetLandmarks = listOf("LEFT_HIP", "RIGHT_HIP", "LEFT_SHOULDER", "RIGHT_SHOULDER")
            )
        ),
        "uneven_shoulders" to listOf(
            PoseSuggestion(
                title = "Level Your Shoulders",
                instruction = "Consciously lower the raised shoulder and gently lift the dropped one to create balance",
                targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_ELBOW", "RIGHT_ELBOW")
            ),
            PoseSuggestion(
                title = "Strengthen Weak Side",
                instruction = "Focus on activating the muscles on your weaker side to improve symmetry",
                targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_WRIST", "RIGHT_WRIST")
            ),
            PoseSuggestion(
                title = "Check Your Stance",
                instruction = "Ensure equal weight distribution on both feet to support balanced shoulder alignment",
                targetLandmarks = listOf("LEFT_HIP", "RIGHT_HIP", "LEFT_ANKLE", "RIGHT_ANKLE")
            )
        )
    )

    private val contextualSuggestions = mapOf(
        "standing" to listOf(
            PoseSuggestion(
                title = "Engage Your Core",
                instruction = "Pull your belly button towards your spine to stabilize your posture",
                targetLandmarks = listOf("LEFT_HIP", "RIGHT_HIP", "LEFT_SHOULDER", "RIGHT_SHOULDER")
            ),
            PoseSuggestion(
                title = "Align Your Head",
                instruction = "Keep your chin parallel to the ground and ears over shoulders",
                targetLandmarks = listOf("NOSE", "LEFT_EAR", "RIGHT_EAR", "LEFT_SHOULDER", "RIGHT_SHOULDER")
            ),
            PoseSuggestion(
                title = "Open Your Chest",
                instruction = "Draw your shoulder blades together gently to open your chest",
                targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_ELBOW", "RIGHT_ELBOW")
            )
        ),
        "arms_raised" to listOf(
            PoseSuggestion(
                title = "Extend Through Fingertips",
                instruction = "Reach your arms fully while keeping shoulders relaxed",
                targetLandmarks = listOf("LEFT_WRIST", "RIGHT_WRIST", "LEFT_INDEX", "RIGHT_INDEX")
            ),
            PoseSuggestion(
                title = "Maintain Arm Alignment",
                instruction = "Keep your arms parallel and avoid locking your elbows",
                targetLandmarks = listOf("LEFT_SHOULDER", "LEFT_ELBOW", "LEFT_WRIST", "RIGHT_SHOULDER", "RIGHT_ELBOW", "RIGHT_WRIST")
            ),
            PoseSuggestion(
                title = "Breathe Deeply",
                instruction = "Take slow, deep breaths to maintain stability in this position",
                targetLandmarks = listOf("LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_HIP", "RIGHT_HIP")
            )
        )
    )

    override suspend fun getPoseSuggestions(landmarks: PoseLandmarksData): Result<PoseSuggestionsResponse> {
        return try {
            delay(100)

            val suggestions = when {
                hasPosturalIssues(landmarks) -> advancedSuggestions["poor_posture"]!!
                hasUnevenShoulders(landmarks) -> advancedSuggestions["uneven_shoulders"]!!
                isArmsRaised(landmarks) -> contextualSuggestions["arms_raised"]!!
                isStanding(landmarks) -> contextualSuggestions["standing"]!!
                else -> defaultSuggestions
            }.take(3)

            Timber.d("Fake client returning ${suggestions.size} suggestions for pose hash: ${landmarks.hash()}")

            Result.success(PoseSuggestionsResponse(suggestions))
        } catch (e: Exception) {
            Timber.e(e, "Error generating fake suggestions")
            Result.failure(e)
        }
    }

    override suspend fun isAvailable(): Boolean = true

    override fun requiresApiKey(): Boolean = false

    private fun isStanding(landmarks: PoseLandmarksData): Boolean {
        if (landmarks.landmarks.size < 33) return false

        val leftHip = landmarks.landmarks[23]
        val rightHip = landmarks.landmarks[24]
        val leftKnee = landmarks.landmarks[25]
        val rightKnee = landmarks.landmarks[26]

        val hipsY = (leftHip.y + rightHip.y) / 2
        val kneesY = (leftKnee.y + rightKnee.y) / 2

        return kneesY > hipsY
    }

    private fun isArmsRaised(landmarks: PoseLandmarksData): Boolean {
        if (landmarks.landmarks.size < 33) return false

        val leftShoulder = landmarks.landmarks[11]
        val rightShoulder = landmarks.landmarks[12]
        val leftWrist = landmarks.landmarks[15]
        val rightWrist = landmarks.landmarks[16]

        val shouldersY = (leftShoulder.y + rightShoulder.y) / 2
        val wristsY = (leftWrist.y + rightWrist.y) / 2

        return wristsY < shouldersY - 0.1f
    }

    private fun hasPosturalIssues(landmarks: PoseLandmarksData): Boolean {
        if (landmarks.landmarks.size < 33) return false

        val nose = landmarks.landmarks[0]
        val leftShoulder = landmarks.landmarks[11]
        val rightShoulder = landmarks.landmarks[12]

        val shoulderMidpoint = (leftShoulder.x + rightShoulder.x) / 2
        val forwardHeadThreshold = 0.05f

        return nose.x > shoulderMidpoint + forwardHeadThreshold
    }

    private fun hasUnevenShoulders(landmarks: PoseLandmarksData): Boolean {
        if (landmarks.landmarks.size < 33) return false

        val leftShoulder = landmarks.landmarks[11]
        val rightShoulder = landmarks.landmarks[12]

        val shoulderHeightDiff = kotlin.math.abs(leftShoulder.y - rightShoulder.y)
        val unevenThreshold = 0.03f

        return shoulderHeightDiff > unevenThreshold
    }
}