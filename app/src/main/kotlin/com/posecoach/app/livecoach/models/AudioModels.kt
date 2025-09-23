package com.posecoach.app.livecoach.models

import android.media.AudioFormat

/**
 * Audio quality information for monitoring
 */
data class AudioQualityInfo(
    val score: Double,
    val hasNoise: Boolean = false,
    val noiseLevel: Double = 0.0,
    val signalLevel: Double = 0.0,
    val isClipped: Boolean = false,
    val clippingPercentage: Double = 0.0,
    val snrRatio: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Audio quality metrics for long-term tracking
 */
data class AudioQualityMetrics(
    val samplesProcessed: Int = 0,
    val averageQuality: Double = 0.0,
    val minQuality: Double = Double.MAX_VALUE,
    val maxQuality: Double = Double.MIN_VALUE,
    val noiseDetectionCount: Int = 0,
    val clippingDetectionCount: Int = 0,
    val totalProcessingTime: Long = 0L
)

/**
 * Audio permission status enumeration
 */
enum class AudioPermissionStatus {
    GRANTED,
    DENIED,
    NOT_REQUESTED,
    PERMANENTLY_DENIED
}

/**
 * Detailed audio permission information
 */
data class AudioPermissionDetails(
    val recordAudioStatus: AudioPermissionStatus,
    val modifyAudioStatus: AudioPermissionStatus = AudioPermissionStatus.NOT_REQUESTED,
    val hasBasicPermissions: Boolean,
    val hasEnhancedPermissions: Boolean,
    val lastChecked: Long = System.currentTimeMillis()
)

/**
 * Audio profile enumeration for different quality levels
 */
enum class AudioProfile {
    LOW_QUALITY,
    STANDARD,
    HIGH_QUALITY,
    ULTRA_LOW_LATENCY
}

/**
 * Audio input configuration
 */
data class AudioInputConfiguration(
    val sampleRate: Int,
    val channelConfig: Int,
    val audioFormat: Int,
    val bufferSizeMultiplier: Int = 4
)

/**
 * Audio output configuration
 */
data class AudioOutputConfiguration(
    val sampleRate: Int,
    val channelConfig: Int,
    val audioFormat: Int,
    val bufferSizeMultiplier: Int = 4
)

/**
 * Quality monitoring configuration
 */
data class QualityConfiguration(
    val checkInterval: Long = 5000L,
    val scoreThreshold: Double = 0.3,
    val enableNoiseDetection: Boolean = true,
    val enableClippingDetection: Boolean = true
)

/**
 * Barge-in detection configuration
 */
data class BargeInConfiguration(
    val threshold: Int = 800,
    val minDurationMs: Long = 300L,
    val cooldownMs: Long = 500L,
    val enabled: Boolean = false
)

/**
 * Android 15+ specific configuration
 */
data class Android15Configuration(
    val sessionTimeoutMs: Long = 30000L,
    val permissionRequestTimeoutMs: Long = 10000L,
    val enableEnhancedFeatures: Boolean = false
)

/**
 * Audio session events
 */
sealed class AudioSessionEvent {
    object SessionStarted : AudioSessionEvent()
    object SessionEnded : AudioSessionEvent()
    data class SessionError(val error: String) : AudioSessionEvent()
    data class AudioFocusChanged(val hasFocus: Boolean) : AudioSessionEvent()
}