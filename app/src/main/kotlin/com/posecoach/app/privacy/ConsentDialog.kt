package com.posecoach.app.privacy

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import timber.log.Timber

/**
 * Granular Consent Dialog Implementation
 * Provides user-friendly interface for privacy consent management
 *
 * Features:
 * - Clear privacy impact indicators
 * - Granular control over data types
 * - Educational content about data usage
 * - GDPR-compliant consent collection
 */
class ConsentDialog(private val context: Context) {

    data class ConsentOption(
        val type: ConsentManager.ConsentType,
        val title: String,
        val description: String,
        val detailedExplanation: String,
        val isRequired: Boolean = false,
        val privacyImpact: PrivacyImpact,
        val enabled: Boolean = true
    )

    enum class PrivacyImpact {
        NONE,       // No data leaves device
        MINIMAL,    // Only anonymous metrics
        MODERATE,   // Landmark coordinates only
        HIGH,       // Audio/enhanced features
        BLOCKED     // Permanently blocked by policy
    }

    private val auditLogger = PrivacyAuditLogger(context)

    /**
     * Show comprehensive consent dialog with granular options
     * 顯示具有細化選項的綜合同意對話框
     */
    fun showGranularConsentDialog(
        onConsentResult: (Map<ConsentManager.ConsentType, Boolean>) -> Unit
    ) {
        val consentOptions = createConsentOptions()
        val dialogView = createConsentDialogView(consentOptions)

        MaterialAlertDialogBuilder(context)
            .setTitle("隱私權限設定 Privacy Settings")
            .setView(dialogView)
            .setPositiveButton("確認設定 Confirm") { _, _ ->
                val results = extractConsentResults(dialogView, consentOptions)
                auditLogger.logConsentEvent("GRANULAR_CONSENT_DIALOG_COMPLETED", true, true)
                onConsentResult(results)
            }
            .setNeutralButton("僅本地模式 Local Only") { _, _ ->
                auditLogger.logConsentEvent("LOCAL_ONLY_MODE_SELECTED", false, true)
                onConsentResult(mapOf(
                    ConsentManager.ConsentType.CAMERA_ACCESS to true // Only local camera access
                ))
            }
            .setNegativeButton("取消 Cancel") { _, _ ->
                auditLogger.logConsentEvent("CONSENT_DIALOG_CANCELLED", false, true)
                onConsentResult(emptyMap())
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Show simplified consent dialog for quick setup
     * 顯示簡化的同意對話框進行快速設定
     */
    fun showSimplifiedConsentDialog(
        onConsentResult: (ConsentManager.ConsentLevel) -> Unit
    ) {
        val options = arrayOf(
            "最大隱私 Maximum Privacy\n僅本地處理，不上傳任何資料",
            "標準隱私 Standard Privacy\n僅上傳匿名姿勢地標（推薦）",
            "完整功能 Full Features\n地標 + 音訊功能（無圖像上傳）"
        )

        MaterialAlertDialogBuilder(context)
            .setTitle("選擇隱私等級 Choose Privacy Level")
            .setMessage("""
                請選擇您偏好的隱私設定：

                重要保證：
                • 絕不上傳圖像或影片
                • 僅在您同意時上傳匿名地標資料
                • 所有處理優先在本地進行

                您隨時可以在設定中修改這些選項。
            """.trimIndent())
            .setSingleChoiceItems(options, 1) { _, _ -> } // Default to Standard Privacy
            .setPositiveButton("確認 Confirm") { dialog, _ ->
                val selectedIndex = (dialog as AlertDialog).listView.checkedItemPosition
                val selectedLevel = when (selectedIndex) {
                    0 -> ConsentManager.ConsentLevel.MINIMAL
                    1 -> ConsentManager.ConsentLevel.STANDARD
                    2 -> ConsentManager.ConsentLevel.FULL
                    else -> ConsentManager.ConsentLevel.STANDARD
                }

                auditLogger.logConsentEvent("SIMPLIFIED_CONSENT_LEVEL_SELECTED", true, true)
                onConsentResult(selectedLevel)
            }
            .setNegativeButton("僅本地 Local Only") { _, _ ->
                auditLogger.logConsentEvent("LOCAL_ONLY_SELECTED", false, true)
                onConsentResult(ConsentManager.ConsentLevel.MINIMAL)
            }
            .setCancelable(false)
            .show()
    }

    private fun createConsentOptions(): List<ConsentOption> {
        return listOf(
            ConsentOption(
                type = ConsentManager.ConsentType.CAMERA_ACCESS,
                title = "相機存取 Camera Access",
                description = "允許應用程式使用相機進行姿勢偵測",
                detailedExplanation = "相機僅用於本地姿勢分析，影像絕不會離開您的裝置。這是應用程式基本功能所必需的。",
                isRequired = true,
                privacyImpact = PrivacyImpact.NONE
            ),
            ConsentOption(
                type = ConsentManager.ConsentType.LANDMARK_DATA,
                title = "姿勢地標資料 Pose Landmark Data",
                description = "允許上傳匿名姿勢關鍵點座標以獲得 AI 建議",
                detailedExplanation = """
                    僅上傳 33 個數值座標點，不包含任何圖像或個人識別資訊。
                    資料用於提供個人化姿勢建議，處理後立即刪除。
                    您可以隨時撤銷此同意。
                """.trimIndent(),
                isRequired = false,
                privacyImpact = PrivacyImpact.MODERATE
            ),
            ConsentOption(
                type = ConsentManager.ConsentType.AUDIO_CAPTURE,
                title = "音訊功能 Audio Features",
                description = "允許語音指導和音訊回饋功能",
                detailedExplanation = """
                    啟用語音教練功能和音訊回饋。
                    音訊資料僅在啟用時處理，用於提供即時指導。
                    不會儲存或上傳音訊檔案。
                """.trimIndent(),
                isRequired = false,
                privacyImpact = PrivacyImpact.HIGH
            ),
            ConsentOption(
                type = ConsentManager.ConsentType.PERFORMANCE_METRICS,
                title = "效能指標 Performance Metrics",
                description = "允許收集匿名效能資料以改善應用程式",
                detailedExplanation = """
                    收集應用程式效能資訊（如處理速度、記憶體使用量）。
                    所有資料完全匿名，用於改善應用程式效能。
                    不包含任何個人資料或使用內容。
                """.trimIndent(),
                isRequired = false,
                privacyImpact = PrivacyImpact.MINIMAL
            ),
            ConsentOption(
                type = ConsentManager.ConsentType.ANALYTICS,
                title = "使用分析 Usage Analytics",
                description = "允許匿名使用統計以改善產品體驗",
                detailedExplanation = """
                    收集匿名的功能使用統計，幫助我們了解哪些功能最有用。
                    不追蹤個人行為或識別身份。
                    資料用於產品改進和新功能開發。
                """.trimIndent(),
                isRequired = false,
                privacyImpact = PrivacyImpact.MINIMAL
            ),
            ConsentOption(
                type = ConsentManager.ConsentType.IMAGE_UPLOAD,
                title = "圖像上傳 Image Upload",
                description = "圖像上傳功能已永久停用",
                detailedExplanation = """
                    根據我們的隱私政策，圖像和影片絕不會離開您的裝置。
                    此功能已永久停用以保護您的隱私。
                    所有圖像處理都在本地進行。
                """.trimIndent(),
                isRequired = false,
                privacyImpact = PrivacyImpact.BLOCKED,
                enabled = false
            )
        )
    }

    private fun createConsentDialogView(options: List<ConsentOption>): View {
        val scrollView = ScrollView(context)
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        // Add privacy guarantee header
        val headerText = TextView(context).apply {
            text = """
                🔒 隱私保證 Privacy Guarantee

                • 圖像絕不離開您的裝置
                • 僅在您明確同意時上傳地標資料
                • 所有處理優先在本地進行
                • 您可隨時修改這些設定
            """.trimIndent()
            textSize = 14f
            setPadding(16, 16, 16, 24)
            setBackgroundColor(0xFFF5F5F5.toInt())
        }
        mainLayout.addView(headerText)

        // Add consent options
        options.forEach { option ->
            val optionView = createConsentOptionView(option)
            mainLayout.addView(optionView)
        }

        // Add data processing explanation
        val explanationText = TextView(context).apply {
            text = """
                資料處理說明：
                • 本地優先：所有功能優先使用裝置處理
                • 透明度：清楚告知何時資料離開裝置
                • 控制權：您完全控制資料分享設定
                • 安全性：所有傳輸都使用端到端加密
            """.trimIndent()
            textSize = 12f
            alpha = 0.8f
            setPadding(16, 24, 16, 16)
        }
        mainLayout.addView(explanationText)

        scrollView.addView(mainLayout)
        return scrollView
    }

    private fun createConsentOptionView(option: ConsentOption): View {
        val optionLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
        }

        // Create switch row
        val switchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Title with privacy impact indicator
        val titleText = TextView(context).apply {
            text = "${option.title} ${getPrivacyImpactIndicator(option.privacyImpact)}"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        textLayout.addView(titleText)

        // Description
        val descText = TextView(context).apply {
            text = option.description
            textSize = 14f
            alpha = 0.8f
        }
        textLayout.addView(descText)

        // Privacy impact explanation
        val impactText = TextView(context).apply {
            text = getPrivacyImpactExplanation(option.privacyImpact)
            textSize = 12f
            alpha = 0.6f
            setTextColor(getPrivacyImpactColor(option.privacyImpact))
        }
        textLayout.addView(impactText)

        val switch = SwitchMaterial(context).apply {
            isChecked = option.isRequired
            isEnabled = option.enabled && !option.isRequired
            tag = option.type
        }

        switchRow.addView(textLayout)
        switchRow.addView(switch)
        optionLayout.addView(switchRow)

        // Add "Learn More" button
        if (option.detailedExplanation.isNotEmpty()) {
            val learnMoreButton = TextView(context).apply {
                text = "了解更多 Learn More"
                textSize = 12f
                setTextColor(0xFF2196F3.toInt())
                setPadding(0, 8, 0, 0)
                setOnClickListener {
                    showDetailedExplanation(option)
                }
            }
            optionLayout.addView(learnMoreButton)
        }

        // Add divider
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            )
            setBackgroundColor(0xFFE0E0E0.toInt())
        }
        optionLayout.addView(divider)

        return optionLayout
    }

    private fun extractConsentResults(
        dialogView: View,
        options: List<ConsentOption>
    ): Map<ConsentManager.ConsentType, Boolean> {
        val results = mutableMapOf<ConsentManager.ConsentType, Boolean>()

        options.forEach { option ->
            val switch = dialogView.findViewWithTag<SwitchMaterial>(option.type)
            if (switch != null) {
                results[option.type] = switch.isChecked
            }
        }

        // Ensure image upload is always false
        results[ConsentManager.ConsentType.IMAGE_UPLOAD] = false

        return results
    }

    private fun getPrivacyImpactIndicator(impact: PrivacyImpact): String {
        return when (impact) {
            PrivacyImpact.NONE -> "🟢"
            PrivacyImpact.MINIMAL -> "🟡"
            PrivacyImpact.MODERATE -> "🟠"
            PrivacyImpact.HIGH -> "🔴"
            PrivacyImpact.BLOCKED -> "🚫"
        }
    }

    private fun getPrivacyImpactExplanation(impact: PrivacyImpact): String {
        return when (impact) {
            PrivacyImpact.NONE -> "本地處理，資料不離開裝置"
            PrivacyImpact.MINIMAL -> "僅匿名統計資料"
            PrivacyImpact.MODERATE -> "匿名地標座標"
            PrivacyImpact.HIGH -> "增強功能，包含音訊處理"
            PrivacyImpact.BLOCKED -> "隱私政策永久停用"
        }
    }

    private fun getPrivacyImpactColor(impact: PrivacyImpact): Int {
        return when (impact) {
            PrivacyImpact.NONE -> 0xFF4CAF50.toInt()      // Green
            PrivacyImpact.MINIMAL -> 0xFFFFEB3B.toInt()   // Yellow
            PrivacyImpact.MODERATE -> 0xFFFF9800.toInt()  // Orange
            PrivacyImpact.HIGH -> 0xFFF44336.toInt()      // Red
            PrivacyImpact.BLOCKED -> 0xFF9E9E9E.toInt()   // Grey
        }
    }

    private fun showDetailedExplanation(option: ConsentOption) {
        MaterialAlertDialogBuilder(context)
            .setTitle(option.title)
            .setMessage(option.detailedExplanation)
            .setPositiveButton("了解 Got it", null)
            .show()
    }
}