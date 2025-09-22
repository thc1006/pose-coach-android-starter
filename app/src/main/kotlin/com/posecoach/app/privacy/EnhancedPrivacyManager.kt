package com.posecoach.app.privacy

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * 增強的隱私管理器
 * 支援圖像上傳控制、離線模式、地標專用模式等隱私功能
 */
class EnhancedPrivacyManager(private val context: Context) {

    data class PrivacySettings(
        val allowImageUpload: Boolean = false,           // 是否允許上傳圖像
        val allowLandmarkUpload: Boolean = true,         // 是否允許上傳姿勢地標
        val allowAudioUpload: Boolean = false,           // 是否允許上傳音訊
        val offlineModeEnabled: Boolean = false,         // 是否啟用離線模式
        val allowAnalytics: Boolean = false,             // 是否允許分析資料
        val dataRetentionDays: Int = 0,                  // 資料保留天數 (0=不保留)
        val requireExplicitConsent: Boolean = true,      // 是否需要明確同意
        val showPrivacyIndicator: Boolean = true,        // 是否顯示隱私指示器
        val allowPerformanceMetrics: Boolean = true      // 是否允許效能指標
    )

    enum class PrivacyLevel {
        MAXIMUM_PRIVACY,    // 最大隱私：僅本地處理
        HIGH_PRIVACY,       // 高隱私：僅地標資料
        BALANCED,           // 平衡：選擇性上傳
        CONVENIENCE         // 便利：完整功能
    }

    enum class DataType {
        POSE_LANDMARKS,     // 姿勢地標
        CAMERA_IMAGES,      // 相機圖像
        AUDIO_RECORDINGS,   // 音訊錄音
        PERFORMANCE_METRICS,// 效能指標
        USAGE_ANALYTICS     // 使用分析
    }

    companion object {
        private const val PREFS_NAME = "enhanced_privacy_prefs"
        private const val KEY_PRIVACY_SETTINGS = "privacy_settings"
        private const val KEY_CONSENT_TIMESTAMP = "consent_timestamp"
        private const val KEY_CONSENT_VERSION = "consent_version"
        private const val CURRENT_CONSENT_VERSION = 2

        // 預設隱私等級設定
        val PRIVACY_LEVEL_SETTINGS = mapOf(
            PrivacyLevel.MAXIMUM_PRIVACY to PrivacySettings(
                allowImageUpload = false,
                allowLandmarkUpload = false,
                allowAudioUpload = false,
                offlineModeEnabled = true,
                allowAnalytics = false,
                dataRetentionDays = 0,
                requireExplicitConsent = true,
                showPrivacyIndicator = true,
                allowPerformanceMetrics = false
            ),
            PrivacyLevel.HIGH_PRIVACY to PrivacySettings(
                allowImageUpload = false,
                allowLandmarkUpload = true,
                allowAudioUpload = false,
                offlineModeEnabled = false,
                allowAnalytics = false,
                dataRetentionDays = 1,
                requireExplicitConsent = true,
                showPrivacyIndicator = true,
                allowPerformanceMetrics = true
            ),
            PrivacyLevel.BALANCED to PrivacySettings(
                allowImageUpload = false,  // 預設不上傳圖像
                allowLandmarkUpload = true,
                allowAudioUpload = true,
                offlineModeEnabled = false,
                allowAnalytics = false,
                dataRetentionDays = 7,
                requireExplicitConsent = true,
                showPrivacyIndicator = true,
                allowPerformanceMetrics = true
            ),
            PrivacyLevel.CONVENIENCE to PrivacySettings(
                allowImageUpload = true,
                allowLandmarkUpload = true,
                allowAudioUpload = true,
                offlineModeEnabled = false,
                allowAnalytics = true,
                dataRetentionDays = 30,
                requireExplicitConsent = true,
                showPrivacyIndicator = true,
                allowPerformanceMetrics = true
            )
        )
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "Failed to create encrypted preferences, using regular prefs")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _privacySettings = MutableStateFlow(loadPrivacySettings())
    val privacySettings: StateFlow<PrivacySettings> = _privacySettings.asStateFlow()

    private val _currentPrivacyLevel = MutableStateFlow(determinePrivacyLevel())
    val currentPrivacyLevel: StateFlow<PrivacyLevel> = _currentPrivacyLevel.asStateFlow()

    init {
        Timber.d("Enhanced Privacy Manager initialized")
        logPrivacySettings()
    }

    /**
     * Get the application context for other privacy components
     */
    fun getContext(): Context {
        return context
    }

    private fun loadPrivacySettings(): PrivacySettings {
        val defaultSettings = PRIVACY_LEVEL_SETTINGS[PrivacyLevel.HIGH_PRIVACY]!!

        return try {
            PrivacySettings(
                allowImageUpload = encryptedPrefs.getBoolean("allow_image_upload", defaultSettings.allowImageUpload),
                allowLandmarkUpload = encryptedPrefs.getBoolean("allow_landmark_upload", defaultSettings.allowLandmarkUpload),
                allowAudioUpload = encryptedPrefs.getBoolean("allow_audio_upload", defaultSettings.allowAudioUpload),
                offlineModeEnabled = encryptedPrefs.getBoolean("offline_mode_enabled", defaultSettings.offlineModeEnabled),
                allowAnalytics = encryptedPrefs.getBoolean("allow_analytics", defaultSettings.allowAnalytics),
                dataRetentionDays = encryptedPrefs.getInt("data_retention_days", defaultSettings.dataRetentionDays),
                requireExplicitConsent = encryptedPrefs.getBoolean("require_explicit_consent", defaultSettings.requireExplicitConsent),
                showPrivacyIndicator = encryptedPrefs.getBoolean("show_privacy_indicator", defaultSettings.showPrivacyIndicator),
                allowPerformanceMetrics = encryptedPrefs.getBoolean("allow_performance_metrics", defaultSettings.allowPerformanceMetrics)
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load privacy settings, using defaults")
            defaultSettings
        }
    }

    private fun savePrivacySettings(settings: PrivacySettings) {
        try {
            with(encryptedPrefs.edit()) {
                putBoolean("allow_image_upload", settings.allowImageUpload)
                putBoolean("allow_landmark_upload", settings.allowLandmarkUpload)
                putBoolean("allow_audio_upload", settings.allowAudioUpload)
                putBoolean("offline_mode_enabled", settings.offlineModeEnabled)
                putBoolean("allow_analytics", settings.allowAnalytics)
                putInt("data_retention_days", settings.dataRetentionDays)
                putBoolean("require_explicit_consent", settings.requireExplicitConsent)
                putBoolean("show_privacy_indicator", settings.showPrivacyIndicator)
                putBoolean("allow_performance_metrics", settings.allowPerformanceMetrics)
                putLong(KEY_CONSENT_TIMESTAMP, System.currentTimeMillis())
                putInt(KEY_CONSENT_VERSION, CURRENT_CONSENT_VERSION)
                apply()
            }
            Timber.d("Privacy settings saved successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save privacy settings")
        }
    }

    private fun determinePrivacyLevel(): PrivacyLevel {
        val current = _privacySettings.value

        return PRIVACY_LEVEL_SETTINGS.entries.find { (_, settings) ->
            settings == current
        }?.key ?: PrivacyLevel.BALANCED
    }

    /**
     * 設定隱私等級
     */
    fun setPrivacyLevel(level: PrivacyLevel) {
        val settings = PRIVACY_LEVEL_SETTINGS[level] ?: return
        updatePrivacySettings(settings)
        _currentPrivacyLevel.value = level
        Timber.i("Privacy level set to: $level")
    }

    /**
     * 更新隱私設定
     */
    fun updatePrivacySettings(settings: PrivacySettings) {
        _privacySettings.value = settings
        savePrivacySettings(settings)
        _currentPrivacyLevel.value = determinePrivacyLevel()
        logPrivacySettings()
    }

    /**
     * 檢查是否允許特定資料類型的上傳
     */
    fun isDataUploadAllowed(dataType: DataType): Boolean {
        val settings = _privacySettings.value

        if (settings.offlineModeEnabled) {
            return false // 離線模式不允許任何上傳
        }

        return when (dataType) {
            DataType.POSE_LANDMARKS -> settings.allowLandmarkUpload
            DataType.CAMERA_IMAGES -> settings.allowImageUpload
            DataType.AUDIO_RECORDINGS -> settings.allowAudioUpload
            DataType.PERFORMANCE_METRICS -> settings.allowPerformanceMetrics
            DataType.USAGE_ANALYTICS -> settings.allowAnalytics
        }
    }

    /**
     * 檢查是否需要顯示同意對話框
     */
    fun needsConsentDialog(): Boolean {
        val lastConsentVersion = encryptedPrefs.getInt(KEY_CONSENT_VERSION, 0)
        val lastConsentTime = encryptedPrefs.getLong(KEY_CONSENT_TIMESTAMP, 0)

        return _privacySettings.value.requireExplicitConsent &&
               (lastConsentVersion < CURRENT_CONSENT_VERSION || lastConsentTime == 0L)
    }

    /**
     * 顯示隱私同意對話框
     */
    fun showConsentDialog(
        onAccept: (PrivacyLevel) -> Unit,
        onDecline: () -> Unit
    ) {
        if (!needsConsentDialog()) {
            onAccept(_currentPrivacyLevel.value)
            return
        }

        val options = arrayOf(
            "最大隱私 (僅本地處理)",
            "高隱私 (僅姿勢資料)",
            "平衡模式 (選擇性功能)",
            "便利模式 (完整功能)"
        )

        AlertDialog.Builder(context)
            .setTitle("隱私設定")
            .setMessage(
                """
                請選擇您的隱私偏好：

                • 最大隱私：所有處理均在本地進行
                • 高隱私：僅上傳姿勢地標資料
                • 平衡模式：音訊 + 地標，無圖像
                • 便利模式：完整 AI 功能

                您隨時可以在設定中修改這些選項。
                """.trimIndent()
            )
            .setSingleChoiceItems(options, 1) { _, which ->
                // 預設選中高隱私
            }
            .setPositiveButton("確認") { dialog, _ ->
                val selectedIndex = (dialog as AlertDialog).listView.checkedItemPosition
                val selectedLevel = when (selectedIndex) {
                    0 -> PrivacyLevel.MAXIMUM_PRIVACY
                    1 -> PrivacyLevel.HIGH_PRIVACY
                    2 -> PrivacyLevel.BALANCED
                    3 -> PrivacyLevel.CONVENIENCE
                    else -> PrivacyLevel.HIGH_PRIVACY
                }

                setPrivacyLevel(selectedLevel)
                markConsentGiven()
                onAccept(selectedLevel)
            }
            .setNegativeButton("僅本地模式") { _, _ ->
                setPrivacyLevel(PrivacyLevel.MAXIMUM_PRIVACY)
                markConsentGiven()
                onDecline()
            }
            .setCancelable(false)
            .show()
    }

    private fun markConsentGiven() {
        with(encryptedPrefs.edit()) {
            putLong(KEY_CONSENT_TIMESTAMP, System.currentTimeMillis())
            putInt(KEY_CONSENT_VERSION, CURRENT_CONSENT_VERSION)
            apply()
        }
        Timber.d("Privacy consent marked as given")
    }

    /**
     * 啟用/停用離線模式
     */
    fun setOfflineModeEnabled(enabled: Boolean) {
        val currentSettings = _privacySettings.value
        updatePrivacySettings(currentSettings.copy(offlineModeEnabled = enabled))
        Timber.i("Offline mode: ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * 設定圖像上傳權限
     */
    fun setImageUploadAllowed(allowed: Boolean) {
        val currentSettings = _privacySettings.value
        updatePrivacySettings(currentSettings.copy(allowImageUpload = allowed))
        Timber.i("Image upload: ${if (allowed) "allowed" else "blocked"}")
    }

    /**
     * 設定音訊上傳權限
     */
    fun setAudioUploadAllowed(allowed: Boolean) {
        val currentSettings = _privacySettings.value
        updatePrivacySettings(currentSettings.copy(allowAudioUpload = allowed))
        Timber.i("Audio upload: ${if (allowed) "allowed" else "blocked"}")
    }

    /**
     * 檢查是否處於離線模式
     */
    fun isOfflineModeEnabled(): Boolean {
        return _privacySettings.value.offlineModeEnabled
    }

    /**
     * 檢查是否允許圖像上傳
     */
    fun isImageUploadAllowed(): Boolean {
        return isDataUploadAllowed(DataType.CAMERA_IMAGES)
    }

    /**
     * 檢查是否允許音訊上傳
     */
    fun isAudioUploadAllowed(): Boolean {
        return isDataUploadAllowed(DataType.AUDIO_RECORDINGS)
    }

    /**
     * 檢查是否允許地標上傳
     */
    fun isLandmarkUploadAllowed(): Boolean {
        return isDataUploadAllowed(DataType.POSE_LANDMARKS)
    }

    /**
     * 獲取隱私狀態摘要
     */
    fun getPrivacyStatusSummary(): String {
        val settings = _privacySettings.value
        val level = _currentPrivacyLevel.value

        return buildString {
            appendLine("隱私等級: ${getPrivacyLevelDisplayName(level)}")
            appendLine("離線模式: ${if (settings.offlineModeEnabled) "啟用" else "停用"}")
            appendLine("圖像上傳: ${if (settings.allowImageUpload) "允許" else "封鎖"}")
            appendLine("音訊上傳: ${if (settings.allowAudioUpload) "允許" else "封鎖"}")
            appendLine("地標上傳: ${if (settings.allowLandmarkUpload) "允許" else "封鎖"}")
            appendLine("效能指標: ${if (settings.allowPerformanceMetrics) "允許" else "封鎖"}")
        }
    }

    private fun getPrivacyLevelDisplayName(level: PrivacyLevel): String {
        return when (level) {
            PrivacyLevel.MAXIMUM_PRIVACY -> "最大隱私"
            PrivacyLevel.HIGH_PRIVACY -> "高隱私"
            PrivacyLevel.BALANCED -> "平衡模式"
            PrivacyLevel.CONVENIENCE -> "便利模式"
        }
    }

    /**
     * 重置隱私設定
     */
    fun resetToDefaults() {
        setPrivacyLevel(PrivacyLevel.HIGH_PRIVACY)
        Timber.i("Privacy settings reset to defaults")
    }

    /**
     * 檢查雲端錯誤是否應該影響核心功能
     */
    fun shouldCloudErrorsAffectCore(): Boolean {
        // 在離線模式或最大隱私模式下，雲端錯誤不應影響核心功能
        return !(_privacySettings.value.offlineModeEnabled ||
                _currentPrivacyLevel.value == PrivacyLevel.MAXIMUM_PRIVACY)
    }

    /**
     * 記錄隱私設定到日誌
     */
    private fun logPrivacySettings() {
        val settings = _privacySettings.value
        Timber.d("Privacy Settings:")
        Timber.d("  Level: ${_currentPrivacyLevel.value}")
        Timber.d("  Offline Mode: ${settings.offlineModeEnabled}")
        Timber.d("  Image Upload: ${settings.allowImageUpload}")
        Timber.d("  Audio Upload: ${settings.allowAudioUpload}")
        Timber.d("  Landmark Upload: ${settings.allowLandmarkUpload}")
        Timber.d("  Performance Metrics: ${settings.allowPerformanceMetrics}")
        Timber.d("  Analytics: ${settings.allowAnalytics}")
    }
}