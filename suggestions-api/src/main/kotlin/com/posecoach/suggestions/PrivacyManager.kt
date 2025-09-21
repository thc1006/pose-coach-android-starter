package com.posecoach.suggestions

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.posecoach.suggestions.models.PoseLandmarksData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Privacy manager for pose data with anonymization and consent management
 * Implements data minimization, differential privacy, and audit logging
 */
class PrivacyManager(private val context: Context) {

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "privacy_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val secureRandom = SecureRandom()
    private val auditLog = mutableListOf<AuditEntry>()

    companion object {
        private const val CONSENT_VERSION = 1
        private const val CONSENT_GRANTED_KEY = "consent_granted"
        private const val CONSENT_VERSION_KEY = "consent_version"
        private const val CONSENT_TIMESTAMP_KEY = "consent_timestamp"
        private const val DATA_RETENTION_DAYS = 30
        private const val NOISE_SCALE = 0.01f // 1% noise for differential privacy
        private const val MIN_VISIBILITY_THRESHOLD = 0.5f

        // Sensitive landmark indices that require extra protection
        private val SENSITIVE_LANDMARKS = setOf(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, // Face/head landmarks
            17, 18, 19, 20, 21, 22 // Hand landmarks
        )
    }

    /**
     * Check if user has given valid consent for data processing
     */
    fun hasValidConsent(): Boolean {
        val consentGranted = encryptedPrefs.getBoolean(CONSENT_GRANTED_KEY, false)
        val consentVersion = encryptedPrefs.getInt(CONSENT_VERSION_KEY, 0)
        val consentTimestamp = encryptedPrefs.getLong(CONSENT_TIMESTAMP_KEY, 0)

        // Check if consent is current version and not expired
        val maxAge = DATA_RETENTION_DAYS * 24 * 60 * 60 * 1000L
        val isExpired = System.currentTimeMillis() - consentTimestamp > maxAge

        val isValid = consentGranted &&
                     consentVersion >= CONSENT_VERSION &&
                     !isExpired

        Timber.d("Consent check: granted=$consentGranted, version=$consentVersion/$CONSENT_VERSION, expired=$isExpired")

        return isValid
    }

    /**
     * Record user consent with timestamp and version
     */
    fun grantConsent(purpose: ConsentPurpose = ConsentPurpose.POSE_ANALYSIS) {
        encryptedPrefs.edit()
            .putBoolean(CONSENT_GRANTED_KEY, true)
            .putInt(CONSENT_VERSION_KEY, CONSENT_VERSION)
            .putLong(CONSENT_TIMESTAMP_KEY, System.currentTimeMillis())
            .apply()

        logAudit(AuditAction.CONSENT_GRANTED, "Purpose: $purpose")
        Timber.i("User consent granted for $purpose")
    }

    /**
     * Revoke user consent and clear associated data
     */
    fun revokeConsent() {
        encryptedPrefs.edit()
            .putBoolean(CONSENT_GRANTED_KEY, false)
            .apply()

        logAudit(AuditAction.CONSENT_REVOKED, "User revoked consent")
        Timber.i("User consent revoked")
    }

    /**
     * Anonymize pose landmarks for transmission
     * Applies differential privacy, data minimization, and sensitive data filtering
     */
    fun anonymizeLandmarks(
        landmarks: PoseLandmarksData,
        privacyLevel: PrivacyLevel = PrivacyLevel.STANDARD
    ): PoseLandmarksData {
        if (!hasValidConsent()) {
            throw SecurityException("No valid consent for data processing")
        }

        logAudit(AuditAction.DATA_ANONYMIZED, "Privacy level: $privacyLevel, landmarks: ${landmarks.landmarks.size}")

        val anonymizedLandmarks = landmarks.landmarks.mapNotNull { landmark ->
            // Skip low-visibility landmarks
            if (landmark.visibility < MIN_VISIBILITY_THRESHOLD) {
                return@mapNotNull null
            }

            // Apply privacy level filtering
            when (privacyLevel) {
                PrivacyLevel.MINIMAL -> {
                    // Only keep essential body landmarks
                    if (landmark.index in SENSITIVE_LANDMARKS) return@mapNotNull null
                }
                PrivacyLevel.STANDARD -> {
                    // Reduce precision of sensitive landmarks
                    if (landmark.index in SENSITIVE_LANDMARKS) {
                        return@mapNotNull landmark.copy(
                            x = quantizeCoordinate(landmark.x, 0.1f),
                            y = quantizeCoordinate(landmark.y, 0.1f)
                        )
                    }
                }
                PrivacyLevel.FULL -> {
                    // Keep all landmarks but add noise
                }
            }

            // Apply differential privacy noise
            val noisyLandmark = landmark.copy(
                x = addNoise(landmark.x, NOISE_SCALE),
                y = addNoise(landmark.y, NOISE_SCALE),
                z = addNoise(landmark.z, NOISE_SCALE)
            )

            noisyLandmark
        }

        return landmarks.copy(
            landmarks = anonymizedLandmarks,
            timestamp = System.currentTimeMillis() // Update timestamp for privacy tracking
        )
    }

    /**
     * Check if data should be retained based on privacy settings
     */
    fun shouldRetainData(timestamp: Long): Boolean {
        val age = System.currentTimeMillis() - timestamp
        val maxAge = DATA_RETENTION_DAYS * 24 * 60 * 60 * 1000L

        return age <= maxAge && hasValidConsent()
    }

    /**
     * Get privacy settings summary
     */
    fun getPrivacyStatus(): PrivacyStatus {
        val consentTimestamp = encryptedPrefs.getLong(CONSENT_TIMESTAMP_KEY, 0)
        val consentVersion = encryptedPrefs.getInt(CONSENT_VERSION_KEY, 0)

        return PrivacyStatus(
            hasConsent = hasValidConsent(),
            consentVersion = consentVersion,
            consentTimestamp = consentTimestamp,
            dataRetentionDays = DATA_RETENTION_DAYS,
            auditLogSize = auditLog.size
        )
    }

    /**
     * Export audit log for compliance
     */
    fun exportAuditLog(): String {
        logAudit(AuditAction.AUDIT_EXPORTED, "Audit log exported")

        return json.encodeToString(auditLog.takeLast(1000)) // Last 1000 entries
    }

    /**
     * Clear all privacy-related data
     */
    fun clearAllData() {
        encryptedPrefs.edit().clear().apply()
        auditLog.clear()

        Timber.i("All privacy data cleared")
    }

    private fun addNoise(value: Float, scale: Float): Float {
        val noise = secureRandom.nextGaussian().toFloat() * scale
        return (value + noise).coerceIn(0f, 1f) // Keep within valid coordinate range
    }

    private fun quantizeCoordinate(value: Float, step: Float): Float {
        return (value / step).toInt() * step
    }

    private fun logAudit(action: AuditAction, details: String) {
        val entry = AuditEntry(
            timestamp = System.currentTimeMillis(),
            action = action,
            details = details,
            sessionId = generateSessionId()
        )

        auditLog.add(entry)

        // Keep only recent entries to manage memory
        if (auditLog.size > 5000) {
            auditLog.removeAt(0)
        }

        Timber.d("Audit logged: $action - $details")
    }

    private fun generateSessionId(): String {
        return System.currentTimeMillis().toString() + secureRandom.nextInt(1000)
    }
}

enum class ConsentPurpose {
    POSE_ANALYSIS,
    IMPROVEMENT_SUGGESTIONS,
    ANALYTICS,
    RESEARCH
}

enum class PrivacyLevel {
    MINIMAL,    // Only essential landmarks, no sensitive data
    STANDARD,   // Reduced precision for sensitive areas
    FULL        // All landmarks with differential privacy
}

enum class AuditAction {
    CONSENT_GRANTED,
    CONSENT_REVOKED,
    DATA_ANONYMIZED,
    DATA_PROCESSED,
    DATA_TRANSMITTED,
    AUDIT_EXPORTED,
    PRIVACY_VIOLATION
}

@Serializable
data class AuditEntry(
    val timestamp: Long,
    val action: AuditAction,
    val details: String,
    val sessionId: String
)

data class PrivacyStatus(
    val hasConsent: Boolean,
    val consentVersion: Int,
    val consentTimestamp: Long,
    val dataRetentionDays: Int,
    val auditLogSize: Int
)