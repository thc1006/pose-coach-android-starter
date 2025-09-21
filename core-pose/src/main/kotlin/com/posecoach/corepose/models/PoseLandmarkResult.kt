package com.posecoach.corepose.models

data class PoseLandmarkResult(
    val landmarks: List<Landmark>,
    val worldLandmarks: List<Landmark>,
    val timestampMs: Long,
    val inferenceTimeMs: Long
) {
    data class Landmark(
        val x: Float,
        val y: Float,
        val z: Float,
        val visibility: Float = 1.0f,
        val presence: Float = 1.0f
    )
}

data class PoseDetectionError(
    val message: String,
    val cause: Throwable? = null
)