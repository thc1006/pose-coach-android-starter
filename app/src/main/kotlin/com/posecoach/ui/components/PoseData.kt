package com.posecoach.ui.components

/**
 * Data class representing pose analysis data for Gemini Live API
 */
data class PoseData(
    val landmarks: List<LandmarkData>,
    val overallConfidence: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    data class LandmarkData(
        val joint: String,
        val x: Float,
        val y: Float,
        val z: Float,
        val confidence: Float
    )
}