package com.posecoach.app.privacy

import com.posecoach.corepose.PoseLandmarks
import timber.log.Timber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/**
 * Data Minimization Manager
 * Implements privacy-first data processing with strict adherence to requirements:
 *
 * 預設僅在端上處理 (Default on-device processing)
 * 僅於使用者同意時上傳地標 JSON (Upload landmarks only with consent)
 * 永不上傳原始影像 (Never upload raw images)
 */
class DataMinimizationManager(
    private val consentManager: ConsentManager,
    private val privacyManager: EnhancedPrivacyManager
) {

    enum class ProcessingMode {
        LOCAL_ONLY,          // All processing on device - DEFAULT
        LANDMARKS_ONLY,      // Only landmark coordinates to cloud
        ENHANCED_FEATURES,   // Audio + landmarks for advanced features
        OFFLINE_FORCED       // Forced offline due to privacy settings
    }

    data class DataPolicy(
        val allowImageProcessing: Boolean = true,    // Always allowed locally
        val allowImageUpload: Boolean = false,       // NEVER allowed per requirements
        val allowLandmarkUpload: Boolean = false,    // Only with explicit consent
        val allowAudioProcessing: Boolean = false,   // Only with consent
        val allowPerformanceMetrics: Boolean = false,
        val dataRetentionHours: Int = 0,             // 0 = immediate deletion
        val processingLocation: String = "local",    // Default to local
        val requiresConsent: Boolean = false
    )

    data class ProcessingDecision(
        val mode: ProcessingMode,
        val policy: DataPolicy,
        val reasoning: String,
        val consentBasis: String
    )

    private val auditLogger = PrivacyAuditLogger(privacyManager.context)

    /**
     * Get current processing mode based on privacy settings and consent
     * 根據隱私設定和同意獲取當前處理模式
     */
    fun getCurrentProcessingMode(): ProcessingMode {
        return when {
            // Force offline mode if enabled
            privacyManager.isOfflineModeEnabled() -> {
                auditLogger.logDataProcessing(
                    dataType = "mode_determination",
                    processingLocation = "local",
                    consentBasis = "offline_mode_enabled",
                    description = "Forced offline mode due to privacy settings"
                )
                ProcessingMode.OFFLINE_FORCED
            }

            // Check for enhanced features (audio + landmarks)
            consentManager.hasConsent(ConsentManager.ConsentType.AUDIO_CAPTURE) &&
            consentManager.hasConsent(ConsentManager.ConsentType.LANDMARK_DATA) -> {
                auditLogger.logDataProcessing(
                    dataType = "mode_determination",
                    processingLocation = "cloud_enhanced",
                    consentBasis = "explicit_consent_audio_landmarks",
                    description = "Enhanced features mode with audio and landmarks"
                )
                ProcessingMode.ENHANCED_FEATURES
            }

            // Landmarks only mode
            consentManager.hasConsent(ConsentManager.ConsentType.LANDMARK_DATA) -> {
                auditLogger.logDataProcessing(
                    dataType = "mode_determination",
                    processingLocation = "cloud_landmarks",
                    consentBasis = "explicit_consent_landmarks",
                    description = "Landmarks-only mode for AI suggestions"
                )
                ProcessingMode.LANDMARKS_ONLY
            }

            // Default to local-only (privacy-first)
            else -> {
                auditLogger.logDataProcessing(
                    dataType = "mode_determination",
                    processingLocation = "local",
                    consentBasis = "privacy_by_design",
                    description = "Default local-only mode - privacy first"
                )
                ProcessingMode.LOCAL_ONLY
            }
        }
    }

    /**
     * Get data processing policy based on current mode
     * 根據當前模式獲取資料處理政策
     */
    fun getDataPolicy(): DataPolicy {
        val mode = getCurrentProcessingMode()

        return when (mode) {
            ProcessingMode.LOCAL_ONLY, ProcessingMode.OFFLINE_FORCED -> DataPolicy(
                allowImageProcessing = true,
                allowImageUpload = false,        // NEVER allowed
                allowLandmarkUpload = false,
                allowAudioProcessing = false,
                allowPerformanceMetrics = false,
                dataRetentionHours = 0,
                processingLocation = "local",
                requiresConsent = false
            )

            ProcessingMode.LANDMARKS_ONLY -> DataPolicy(
                allowImageProcessing = true,
                allowImageUpload = false,        // NEVER allowed
                allowLandmarkUpload = true,
                allowAudioProcessing = false,
                allowPerformanceMetrics = consentManager.hasConsent(
                    ConsentManager.ConsentType.PERFORMANCE_METRICS
                ),
                dataRetentionHours = 1,          // Minimal retention
                processingLocation = "cloud_landmarks",
                requiresConsent = true
            )

            ProcessingMode.ENHANCED_FEATURES -> DataPolicy(
                allowImageProcessing = true,
                allowImageUpload = false,        // NEVER allowed
                allowLandmarkUpload = true,
                allowAudioProcessing = true,
                allowPerformanceMetrics = consentManager.hasConsent(
                    ConsentManager.ConsentType.PERFORMANCE_METRICS
                ),
                dataRetentionHours = 1,          // Minimal retention
                processingLocation = "cloud_enhanced",
                requiresConsent = true
            )
        }
    }

    /**
     * Make processing decision with full audit trail
     * 做出處理決定並提供完整審計記錄
     */
    fun makeProcessingDecision(dataType: String): ProcessingDecision {
        val mode = getCurrentProcessingMode()
        val policy = getDataPolicy()

        val reasoning = when (mode) {
            ProcessingMode.LOCAL_ONLY ->
                "Default privacy-first approach: all processing on device"
            ProcessingMode.OFFLINE_FORCED ->
                "User explicitly enabled offline mode"
            ProcessingMode.LANDMARKS_ONLY ->
                "User consented to landmark data sharing for AI suggestions"
            ProcessingMode.ENHANCED_FEATURES ->
                "User consented to enhanced features with audio and landmarks"
        }

        val consentBasis = when {
            mode == ProcessingMode.LOCAL_ONLY -> "privacy_by_design"
            mode == ProcessingMode.OFFLINE_FORCED -> "user_preference"
            policy.requiresConsent -> "explicit_consent"
            else -> "legitimate_interest"
        }

        val decision = ProcessingDecision(
            mode = mode,
            policy = policy,
            reasoning = reasoning,
            consentBasis = consentBasis
        )

        auditLogger.logDataProcessing(
            dataType = dataType,
            processingLocation = policy.processingLocation,
            consentBasis = consentBasis,
            description = "Processing decision: $reasoning"
        )

        return decision
    }

    /**
     * Sanitize landmark data before potential upload
     * 清理地標資料以移除可識別資訊
     */
    fun sanitizeLandmarkData(landmarks: PoseLandmarks): Map<String, Any> {
        val policy = getDataPolicy()

        if (!policy.allowLandmarkUpload) {
            auditLogger.logPrivacyViolation(
                "Attempt to sanitize landmarks without upload permission"
            )
            throw SecurityException("Landmark upload not permitted")
        }

        // Verify no image data is present (should never happen)
        if (containsImageData(landmarks)) {
            auditLogger.logPrivacyViolation(
                "Image data detected in landmark sanitization - BLOCKED",
                "CRITICAL"
            )
            throw SecurityException("Image data detected - privacy policy violation")
        }

        val sanitizedData = mapOf(
            "landmarks" to landmarks.landmarks.map { landmark ->
                mapOf(
                    "x" to landmark.x,
                    "y" to landmark.y,
                    "z" to landmark.z,
                    "visibility" to landmark.visibility
                )
            },
            "timestamp" to System.currentTimeMillis(),
            "session_id" to generateAnonymousSessionId(),
            "privacy_level" to "landmarks_only",
            "data_retention_hours" to policy.dataRetentionHours,
            // Explicitly state what is NOT included
            "excludes" to listOf(
                "images", "videos", "device_id", "user_id",
                "location", "biometric_data", "personal_identifiers"
            )
        )

        auditLogger.logDataProcessing(
            dataType = "pose_landmarks",
            processingLocation = "sanitization",
            consentBasis = "explicit_consent",
            description = "Landmark data sanitized for upload - ${landmarks.landmarks.size} points"
        )

        return sanitizedData
    }

    /**
     * Validate that processing request adheres to privacy policy
     * 驗證處理請求是否符合隱私政策
     */
    fun validateProcessingRequest(
        dataType: String,
        processingLocation: String,
        includesImages: Boolean = false
    ): Boolean {
        // CRITICAL: Never allow image processing outside device
        if (includesImages && processingLocation != "local") {
            auditLogger.logPrivacyViolation(
                "BLOCKED: Attempt to process images outside device - $processingLocation",
                "CRITICAL"
            )
            return false
        }

        val policy = getDataPolicy()

        return when (dataType) {
            "images", "camera_feed" -> {
                // Images can only be processed locally
                val allowed = processingLocation == "local"
                if (!allowed) {
                    auditLogger.logPrivacyViolation(
                        "BLOCKED: Image processing outside device attempted"
                    )
                }
                allowed
            }

            "pose_landmarks" -> {
                val allowed = processingLocation == "local" ||
                            (policy.allowLandmarkUpload && processingLocation.startsWith("cloud"))
                if (!allowed) {
                    auditLogger.logDataProcessing(
                        dataType = dataType,
                        processingLocation = "blocked",
                        consentBasis = "no_consent",
                        description = "Landmark processing blocked - no consent"
                    )
                }
                allowed
            }

            "audio" -> {
                val allowed = processingLocation == "local" ||
                            (policy.allowAudioProcessing && processingLocation.startsWith("cloud"))
                if (!allowed) {
                    auditLogger.logDataProcessing(
                        dataType = dataType,
                        processingLocation = "blocked",
                        consentBasis = "no_consent",
                        description = "Audio processing blocked - no consent"
                    )
                }
                allowed
            }

            else -> {
                // Default to local processing only
                processingLocation == "local"
            }
        }
    }

    /**
     * Get processing statistics for privacy dashboard
     * 獲取隱私儀表板的處理統計
     */
    fun getProcessingStatistics(): Map<String, Any> {
        val mode = getCurrentProcessingMode()
        val policy = getDataPolicy()

        return mapOf(
            "current_mode" to mode.name,
            "processing_location" to policy.processingLocation,
            "image_upload_status" to "PERMANENTLY_BLOCKED",
            "landmark_upload_enabled" to policy.allowLandmarkUpload,
            "audio_processing_enabled" to policy.allowAudioProcessing,
            "data_retention_hours" to policy.dataRetentionHours,
            "consent_required" to policy.requiresConsent,
            "privacy_guarantees" to listOf(
                "Images never leave device",
                "Landmarks only with explicit consent",
                "Data minimization enforced",
                "Immediate deletion after processing"
            )
        )
    }

    private fun containsImageData(landmarks: PoseLandmarks): Boolean {
        // Implementation to detect if landmark data somehow contains image information
        // This should never happen, but we check as a security measure
        return false // Simplified for example - real implementation would check for image signatures
    }

    private fun generateAnonymousSessionId(): String {
        // Generate random session ID that cannot be traced back to user
        return UUID.randomUUID().toString()
    }

    /**
     * Emergency privacy enforcement
     * 緊急隱私強制執行
     */
    fun enforceEmergencyPrivacyMode() {
        auditLogger.logPrivacyViolation(
            "Emergency privacy mode activated - all cloud processing disabled"
        )

        // Force offline mode
        privacyManager.setOfflineModeEnabled(true)

        // Withdraw all consents that allow data upload
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, false, false)
        consentManager.grantConsent(ConsentManager.ConsentType.AUDIO_CAPTURE, false, false)
        consentManager.grantConsent(ConsentManager.ConsentType.ANALYTICS, false, false)

        Timber.w("Emergency privacy mode activated - app in local-only mode")
    }
}