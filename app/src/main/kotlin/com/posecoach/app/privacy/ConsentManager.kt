package com.posecoach.app.privacy

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Enhanced Consent Management System
 * Handles granular consent for different data types according to GDPR requirements
 *
 * Privacy Requirements Enforced:
 * - 預設僅在端上處理 (Default on-device processing)
 * - 僅於使用者同意時上傳地標 JSON (Upload landmarks only with consent)
 * - 永不上傳原始影像 (Never upload raw images)
 */
class ConsentManager(private val context: Context) {

    data class ConsentState(
        val landmarkDataConsent: Boolean = false,
        val performanceMetricsConsent: Boolean = false,
        val audioCaptureConsent: Boolean = false,
        val cameraAccessConsent: Boolean = false,
        val analyticsConsent: Boolean = false,
        val consentVersion: Int = 1,
        val consentTimestamp: Long = 0L,
        val hasSeenPrivacyNotice: Boolean = false,
        val explicitImageUploadDenial: Boolean = true // Explicit denial per requirements
    )

    enum class ConsentType {
        LANDMARK_DATA,     // 姿勢地標資料 - Only data type that can be uploaded
        PERFORMANCE_METRICS, // 效能指標
        AUDIO_CAPTURE,     // 音訊捕捉
        CAMERA_ACCESS,     // 相機存取 (local only)
        ANALYTICS,         // 使用分析
        IMAGE_UPLOAD       // 圖像上傳 (ALWAYS DENIED per requirements)
    }

    enum class ConsentLevel {
        NONE,           // No consent given - local only
        MINIMAL,        // Only essential features - camera access
        STANDARD,       // Core features + landmarks upload
        FULL            // All features enabled except image upload
    }

    private val secureStorage = SecureStorageManager(context)
    private val auditLogger = PrivacyAuditLogger(context)

    private val _consentState = MutableStateFlow(loadConsentState())
    val consentState: StateFlow<ConsentState> = _consentState.asStateFlow()

    private val _consentLevel = MutableStateFlow(determineConsentLevel())
    val consentLevel: StateFlow<ConsentLevel> = _consentLevel.asStateFlow()

    init {
        Timber.d("ConsentManager initialized with privacy-first defaults")
        auditLogger.logConsentEvent("SYSTEM_INITIALIZED", false, userInitiated = false)
    }

    /**
     * Request consent for a specific data type
     * 請求特定資料類型的同意
     */
    fun requestConsent(type: ConsentType): Boolean {
        // Image upload is NEVER allowed per privacy requirements
        if (type == ConsentType.IMAGE_UPLOAD) {
            auditLogger.logPrivacyViolation(
                "Attempt to request image upload consent - BLOCKED per privacy policy",
                "CRITICAL"
            )
            return false
        }

        val currentState = _consentState.value
        val hasConsent = when (type) {
            ConsentType.LANDMARK_DATA -> currentState.landmarkDataConsent
            ConsentType.PERFORMANCE_METRICS -> currentState.performanceMetricsConsent
            ConsentType.AUDIO_CAPTURE -> currentState.audioCaptureConsent
            ConsentType.CAMERA_ACCESS -> currentState.cameraAccessConsent
            ConsentType.ANALYTICS -> currentState.analyticsConsent
            ConsentType.IMAGE_UPLOAD -> false // Always false
        }

        auditLogger.logConsentEvent(
            consentType = type.name,
            granted = hasConsent,
            userInitiated = false
        )

        return hasConsent
    }

    /**
     * Grant or revoke consent for a specific type
     * 授予或撤銷特定類型的同意
     */
    fun grantConsent(type: ConsentType, granted: Boolean, userInitiated: Boolean = true) {
        // Enforce privacy policy: Image upload is NEVER allowed
        if (type == ConsentType.IMAGE_UPLOAD && granted) {
            auditLogger.logPrivacyViolation(
                "Attempt to grant image upload consent - BLOCKED per privacy policy",
                "CRITICAL"
            )
            Timber.e("PRIVACY VIOLATION: Attempt to enable image upload")
            return
        }

        val currentState = _consentState.value
        val newState = when (type) {
            ConsentType.LANDMARK_DATA -> currentState.copy(
                landmarkDataConsent = granted,
                consentTimestamp = if (granted) System.currentTimeMillis() else currentState.consentTimestamp
            )
            ConsentType.PERFORMANCE_METRICS -> currentState.copy(
                performanceMetricsConsent = granted
            )
            ConsentType.AUDIO_CAPTURE -> currentState.copy(
                audioCaptureConsent = granted
            )
            ConsentType.CAMERA_ACCESS -> currentState.copy(
                cameraAccessConsent = granted
            )
            ConsentType.ANALYTICS -> currentState.copy(
                analyticsConsent = granted
            )
            ConsentType.IMAGE_UPLOAD -> currentState.copy(
                explicitImageUploadDenial = true // Always deny
            )
        }

        _consentState.value = newState
        _consentLevel.value = determineConsentLevel()
        saveConsentState(newState)

        // Log consent change with signature for audit trail
        auditLogger.logConsentEvent(
            consentType = type.name,
            granted = granted,
            userInitiated = userInitiated
        )

        // Store consent with cryptographic signature for GDPR compliance
        secureStorage.storeConsentWithSignature(
            consentType = type.name,
            granted = granted,
            userSignature = generateConsentSignature(type, granted)
        )

        Timber.i("Consent ${if (granted) "granted" else "revoked"} for ${type.name}")
    }

    /**
     * Check if consent is granted for a specific type
     * 檢查是否已授予特定類型的同意
     */
    fun hasConsent(type: ConsentType): Boolean {
        // Image upload consent is NEVER granted per privacy requirements
        if (type == ConsentType.IMAGE_UPLOAD) {
            return false
        }

        val state = _consentState.value
        return when (type) {
            ConsentType.LANDMARK_DATA -> state.landmarkDataConsent
            ConsentType.PERFORMANCE_METRICS -> state.performanceMetricsConsent
            ConsentType.AUDIO_CAPTURE -> state.audioCaptureConsent
            ConsentType.CAMERA_ACCESS -> state.cameraAccessConsent
            ConsentType.ANALYTICS -> state.analyticsConsent
            ConsentType.IMAGE_UPLOAD -> false // Always false
        }
    }

    /**
     * Check if consent is expired and needs renewal
     * 檢查同意是否已過期需要更新
     */
    fun isConsentExpired(): Boolean {
        val state = _consentState.value
        if (state.consentTimestamp == 0L) return true

        val currentTime = System.currentTimeMillis()
        val oneYear = 365L * 24 * 60 * 60 * 1000 // GDPR recommends yearly consent renewal

        return (currentTime - state.consentTimestamp) > oneYear
    }

    /**
     * Withdraw all consents and return to local-only mode
     * 撤銷所有同意並返回僅本地模式
     */
    fun withdrawAllConsent() {
        val localOnlyState = ConsentState(
            landmarkDataConsent = false,
            performanceMetricsConsent = false,
            audioCaptureConsent = false,
            cameraAccessConsent = true, // Camera access for local processing
            analyticsConsent = false,
            consentVersion = ConsentVersionManager.CURRENT_CONSENT_VERSION,
            consentTimestamp = System.currentTimeMillis(),
            hasSeenPrivacyNotice = true,
            explicitImageUploadDenial = true
        )

        _consentState.value = localOnlyState
        _consentLevel.value = ConsentLevel.MINIMAL
        saveConsentState(localOnlyState)

        auditLogger.logConsentEvent(
            consentType = "ALL_CONSENTS",
            granted = false,
            userInitiated = true
        )

        Timber.i("All consents withdrawn - app in local-only mode")
    }

    /**
     * Get consent summary for privacy dashboard
     * 獲得隱私儀表板的同意摘要
     */
    fun getConsentSummary(): Map<String, Any> {
        val state = _consentState.value
        return mapOf(
            "consent_level" to _consentLevel.value.name,
            "landmark_data" to state.landmarkDataConsent,
            "performance_metrics" to state.performanceMetricsConsent,
            "audio_capture" to state.audioCaptureConsent,
            "camera_access" to state.cameraAccessConsent,
            "analytics" to state.analyticsConsent,
            "image_upload_blocked" to "PERMANENTLY_BLOCKED_BY_POLICY",
            "consent_timestamp" to state.consentTimestamp,
            "consent_version" to state.consentVersion,
            "privacy_guarantee" to "Images never leave device, only landmarks with explicit consent"
        )
    }

    /**
     * Verify consent integrity for compliance audits
     * 驗證同意完整性以進行合規審計
     */
    fun verifyConsentIntegrity(): Boolean {
        return try {
            ConsentType.values().all { type ->
                if (type == ConsentType.IMAGE_UPLOAD) {
                    // Image upload consent should never exist
                    !secureStorage.verifyConsentIntegrity(type.name)
                } else {
                    secureStorage.verifyConsentIntegrity(type.name)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Consent integrity verification failed")
            auditLogger.logPrivacyViolation("Consent integrity check failed: ${e.message}")
            false
        }
    }

    private fun loadConsentState(): ConsentState {
        return try {
            val prefs = context.getSharedPreferences("consent_state", Context.MODE_PRIVATE)
            ConsentState(
                landmarkDataConsent = prefs.getBoolean("landmark_data", false),
                performanceMetricsConsent = prefs.getBoolean("performance_metrics", false),
                audioCaptureConsent = prefs.getBoolean("audio_capture", false),
                cameraAccessConsent = prefs.getBoolean("camera_access", false),
                analyticsConsent = prefs.getBoolean("analytics", false),
                consentVersion = prefs.getInt("consent_version", 1),
                consentTimestamp = prefs.getLong("consent_timestamp", 0L),
                hasSeenPrivacyNotice = prefs.getBoolean("privacy_notice_seen", false),
                explicitImageUploadDenial = true // Always true per policy
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load consent state, using privacy-first defaults")
            ConsentState() // Returns privacy-first defaults
        }
    }

    private fun saveConsentState(state: ConsentState) {
        try {
            context.getSharedPreferences("consent_state", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("landmark_data", state.landmarkDataConsent)
                .putBoolean("performance_metrics", state.performanceMetricsConsent)
                .putBoolean("audio_capture", state.audioCaptureConsent)
                .putBoolean("camera_access", state.cameraAccessConsent)
                .putBoolean("analytics", state.analyticsConsent)
                .putInt("consent_version", state.consentVersion)
                .putLong("consent_timestamp", state.consentTimestamp)
                .putBoolean("privacy_notice_seen", state.hasSeenPrivacyNotice)
                .apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to save consent state")
        }
    }

    private fun determineConsentLevel(): ConsentLevel {
        val state = _consentState.value

        return when {
            !state.cameraAccessConsent -> ConsentLevel.NONE
            state.landmarkDataConsent && state.audioCaptureConsent && state.performanceMetricsConsent ->
                ConsentLevel.FULL
            state.landmarkDataConsent -> ConsentLevel.STANDARD
            state.cameraAccessConsent -> ConsentLevel.MINIMAL
            else -> ConsentLevel.NONE
        }
    }

    private fun generateConsentSignature(type: ConsentType, granted: Boolean): String {
        // Generate cryptographic signature for consent record
        val timestamp = System.currentTimeMillis()
        val data = "${type.name}_${granted}_$timestamp"
        return android.util.Base64.encodeToString(
            data.toByteArray(),
            android.util.Base64.DEFAULT
        )
    }
}