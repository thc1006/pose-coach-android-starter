package com.posecoach.app.privacy

/**
 * Consent Version Manager
 * Handles consent versioning and updates for GDPR compliance
 */
object ConsentVersionManager {
    const val CURRENT_CONSENT_VERSION = 2
    const val CONSENT_EXPIRY_DAYS = 365 // 1 year

    data class ConsentVersionInfo(
        val version: Int,
        val introducedFeatures: List<String>,
        val requiredReconsent: Boolean
    )

    fun checkConsentVersionUpdate(currentVersion: Int): ConsentVersionInfo? {
        return when {
            currentVersion < 1 -> ConsentVersionInfo(
                version = 1,
                introducedFeatures = listOf("基本姿勢偵測", "地標資料上傳"),
                requiredReconsent = true
            )
            currentVersion < 2 -> ConsentVersionInfo(
                version = 2,
                introducedFeatures = listOf("語音指導", "即時建議", "效能分析"),
                requiredReconsent = true
            )
            else -> null
        }
    }

    fun isConsentExpired(consentTimestamp: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        val expiryTime = consentTimestamp + (CONSENT_EXPIRY_DAYS * 24 * 60 * 60 * 1000L)
        return currentTime > expiryTime
    }
}