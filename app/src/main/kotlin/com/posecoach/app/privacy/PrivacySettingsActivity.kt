package com.posecoach.app.privacy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 隱私設定介面
 * 允許用戶配置隱私偏好、離線模式等設定
 */
class PrivacySettingsActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, PrivacySettingsActivity::class.java)
        }
    }

    private lateinit var privacyManager: EnhancedPrivacyManager

    // UI 組件
    private lateinit var privacyLevelSpinner: Spinner
    private lateinit var offlineModeSwitch: Switch
    private lateinit var imageUploadSwitch: Switch
    private lateinit var audioUploadSwitch: Switch
    private lateinit var landmarkUploadSwitch: Switch
    private lateinit var performanceMetricsSwitch: Switch
    private lateinit var analyticsSwitch: Switch
    private lateinit var dataRetentionSpinner: Spinner
    private lateinit var privacyIndicatorSwitch: Switch
    private lateinit var statusText: TextView
    private lateinit var resetButton: Button
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createUI()

        privacyManager = EnhancedPrivacyManager(this)
        setupUIListeners()
        loadCurrentSettings()

        observePrivacyChanges()
    }

    private fun createUI() {
        // 創建程式化 UI (避免需要 XML 資源)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // 標題
        val titleText = TextView(this).apply {
            text = "隱私設定"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        mainLayout.addView(titleText)

        // 隱私等級選擇
        mainLayout.addView(createSectionLabel("隱私等級"))
        privacyLevelSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@PrivacySettingsActivity,
                android.R.layout.simple_spinner_item,
                arrayOf("最大隱私", "高隱私", "平衡模式", "便利模式")
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        mainLayout.addView(privacyLevelSpinner)

        // 離線模式
        mainLayout.addView(createSectionLabel("基本設定"))
        offlineModeSwitch = createSwitchRow(mainLayout, "離線模式", "所有處理在本地進行，不連接雲端服務")

        // 資料上傳設定
        mainLayout.addView(createSectionLabel("資料上傳權限"))
        imageUploadSwitch = createSwitchRow(mainLayout, "圖像上傳", "允許上傳相機圖像到 AI 服務")
        audioUploadSwitch = createSwitchRow(mainLayout, "音訊上傳", "允許上傳麥克風音訊到 AI 服務")
        landmarkUploadSwitch = createSwitchRow(mainLayout, "姿勢地標上傳", "允許上傳姿勢關鍵點資料")

        // 其他設定
        mainLayout.addView(createSectionLabel("其他選項"))
        performanceMetricsSwitch = createSwitchRow(mainLayout, "效能指標", "收集效能資料以改善體驗")
        analyticsSwitch = createSwitchRow(mainLayout, "使用分析", "收集使用統計以改善產品")
        privacyIndicatorSwitch = createSwitchRow(mainLayout, "隱私指示器", "顯示資料使用狀態")

        // 資料保留期限
        mainLayout.addView(createSectionLabel("資料保留"))
        dataRetentionSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@PrivacySettingsActivity,
                android.R.layout.simple_spinner_item,
                arrayOf("不保留", "1天", "7天", "30天")
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        mainLayout.addView(dataRetentionSpinner)

        // 狀態顯示
        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 32, 0, 16)
        }
        mainLayout.addView(statusText)

        // 按鈕
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        resetButton = Button(this).apply {
            text = "重置為預設"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        buttonLayout.addView(resetButton)

        saveButton = Button(this).apply {
            text = "儲存設定"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        buttonLayout.addView(saveButton)

        mainLayout.addView(buttonLayout)

        // 設置為內容視圖
        setContentView(ScrollView(this).apply {
            addView(mainLayout)
        })
    }

    private fun createSectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 18f
            setPadding(0, 24, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    private fun createSwitchRow(parent: LinearLayout, title: String, description: String): Switch {
        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val switchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 16f
        }
        textLayout.addView(titleText)

        val descText = TextView(this).apply {
            text = description
            textSize = 12f
            alpha = 0.7f
        }
        textLayout.addView(descText)

        val switch = Switch(this)

        switchLayout.addView(textLayout)
        switchLayout.addView(switch)
        rowLayout.addView(switchLayout)

        parent.addView(rowLayout)
        return switch
    }

    private fun setupUIListeners() {
        privacyLevelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val level = when (position) {
                    0 -> EnhancedPrivacyManager.PrivacyLevel.MAXIMUM_PRIVACY
                    1 -> EnhancedPrivacyManager.PrivacyLevel.HIGH_PRIVACY
                    2 -> EnhancedPrivacyManager.PrivacyLevel.BALANCED
                    3 -> EnhancedPrivacyManager.PrivacyLevel.CONVENIENCE
                    else -> EnhancedPrivacyManager.PrivacyLevel.HIGH_PRIVACY
                }
                applyPrivacyLevel(level)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 監聽各個開關變化
        offlineModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateOfflineMode(isChecked)
        }

        resetButton.setOnClickListener {
            showResetConfirmDialog()
        }

        saveButton.setOnClickListener {
            saveCurrentSettings()
        }
    }

    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            privacyManager.privacySettings.collect { settings ->
                updateUIFromSettings(settings)
                updateStatusText(settings)
            }
        }
    }

    private fun updateUIFromSettings(settings: EnhancedPrivacyManager.PrivacySettings) {
        // 更新 UI 控制項狀態
        offlineModeSwitch.isChecked = settings.offlineModeEnabled
        imageUploadSwitch.isChecked = settings.allowImageUpload
        audioUploadSwitch.isChecked = settings.allowAudioUpload
        landmarkUploadSwitch.isChecked = settings.allowLandmarkUpload
        performanceMetricsSwitch.isChecked = settings.allowPerformanceMetrics
        analyticsSwitch.isChecked = settings.allowAnalytics
        privacyIndicatorSwitch.isChecked = settings.showPrivacyIndicator

        // 設定資料保留期限
        val retentionIndex = when (settings.dataRetentionDays) {
            0 -> 0
            1 -> 1
            7 -> 2
            30 -> 3
            else -> 0
        }
        dataRetentionSpinner.setSelection(retentionIndex)

        // 更新控制項啟用狀態
        updateControlsEnabled(settings)
    }

    private fun updateControlsEnabled(settings: EnhancedPrivacyManager.PrivacySettings) {
        val isOffline = settings.offlineModeEnabled

        // 離線模式時停用雲端相關設定
        imageUploadSwitch.isEnabled = !isOffline
        audioUploadSwitch.isEnabled = !isOffline
        landmarkUploadSwitch.isEnabled = !isOffline
        analyticsSwitch.isEnabled = !isOffline
        dataRetentionSpinner.isEnabled = !isOffline
    }

    private fun updateStatusText(settings: EnhancedPrivacyManager.PrivacySettings) {
        val status = buildString {
            appendLine("目前狀態:")
            if (settings.offlineModeEnabled) {
                appendLine("• 離線模式：所有處理在本地進行")
            } else {
                appendLine("• 已連接：可使用雲端 AI 功能")
                if (settings.allowLandmarkUpload && !settings.allowImageUpload && !settings.allowAudioUpload) {
                    appendLine("• 地標專用模式：僅上傳姿勢資料")
                }
            }

            val enabledFeatures = mutableListOf<String>()
            if (settings.allowImageUpload) enabledFeatures.add("圖像")
            if (settings.allowAudioUpload) enabledFeatures.add("音訊")
            if (settings.allowLandmarkUpload) enabledFeatures.add("地標")

            if (enabledFeatures.isNotEmpty()) {
                appendLine("• 允許上傳：${enabledFeatures.joinToString("、")}")
            }
        }

        statusText.text = status
    }

    private fun applyPrivacyLevel(level: EnhancedPrivacyManager.PrivacyLevel) {
        privacyManager.setPrivacyLevel(level)
    }

    private fun updateOfflineMode(enabled: Boolean) {
        privacyManager.setOfflineModeEnabled(enabled)
    }

    private fun saveCurrentSettings() {
        val settings = EnhancedPrivacyManager.PrivacySettings(
            allowImageUpload = imageUploadSwitch.isChecked,
            allowLandmarkUpload = landmarkUploadSwitch.isChecked,
            allowAudioUpload = audioUploadSwitch.isChecked,
            offlineModeEnabled = offlineModeSwitch.isChecked,
            allowAnalytics = analyticsSwitch.isChecked,
            dataRetentionDays = when (dataRetentionSpinner.selectedItemPosition) {
                0 -> 0
                1 -> 1
                2 -> 7
                3 -> 30
                else -> 0
            },
            requireExplicitConsent = true,
            showPrivacyIndicator = privacyIndicatorSwitch.isChecked,
            allowPerformanceMetrics = performanceMetricsSwitch.isChecked
        )

        privacyManager.updatePrivacySettings(settings)

        Toast.makeText(this, "隱私設定已儲存", Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_OK)
    }

    private fun showResetConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("重置隱私設定")
            .setMessage("確定要重置為預設的隱私設定嗎？")
            .setPositiveButton("確定") { _, _ ->
                privacyManager.resetToDefaults()
                Toast.makeText(this, "已重置為預設設定", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun observePrivacyChanges() {
        lifecycleScope.launch {
            privacyManager.currentPrivacyLevel.collect { level ->
                val levelIndex = when (level) {
                    EnhancedPrivacyManager.PrivacyLevel.MAXIMUM_PRIVACY -> 0
                    EnhancedPrivacyManager.PrivacyLevel.HIGH_PRIVACY -> 1
                    EnhancedPrivacyManager.PrivacyLevel.BALANCED -> 2
                    EnhancedPrivacyManager.PrivacyLevel.CONVENIENCE -> 3
                }
                privacyLevelSpinner.setSelection(levelIndex)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        setResult(Activity.RESULT_OK)
    }
}