package com.posecoach.app.config

/**
 * Feature flags for production deployment control.
 * Allows safe rollout of features and fallback to stable implementations.
 */
object FeatureFlags {

    /**
     * Enable real MediaPipe pose detection.
     * When false, uses fake pose data for testing.
     */
    const val ENABLE_REAL_POSE_DETECTION = false

    /**
     * Enable Gemini 2.5 API integration.
     * When false, uses fake suggestions client.
     */
    const val ENABLE_GEMINI_API = false

    /**
     * Enable Gemini Live API features.
     * When false, disables voice interaction features.
     */
    const val ENABLE_GEMINI_LIVE = false

    /**
     * Enable privacy controls and consent UI.
     * When false, runs in privacy-first mode (no uploads).
     */
    const val ENABLE_PRIVACY_CONTROLS = true

    /**
     * Enable performance tracking and analytics.
     * When false, disables non-essential telemetry.
     */
    const val ENABLE_PERFORMANCE_TRACKING = true

    /**
     * Enable debug overlay with pose landmarks.
     * Should be false in production builds.
     */
    const val ENABLE_DEBUG_OVERLAY = BuildConfig.DEBUG

    /**
     * Maximum inference time threshold in milliseconds.
     * Triggers fallback strategies when exceeded.
     */
    const val MAX_INFERENCE_TIME_MS = 30L

    /**
     * Check if running in production mode.
     */
    fun isProductionMode(): Boolean {
        return !BuildConfig.DEBUG
    }

    /**
     * Check if fake implementations should be used.
     */
    fun shouldUseFakeImplementations(): Boolean {
        return !ENABLE_REAL_POSE_DETECTION || !ENABLE_GEMINI_API
    }
}