# Privacy Controls Implementation Examples

## Overview

This document provides comprehensive examples for implementing privacy controls in the Pose Coach Android application. These examples demonstrate how to create user-friendly privacy interfaces while maintaining strict data protection standards.

## Core Privacy Components

### 1. Privacy Settings Manager

Implement a centralized privacy settings manager:

```kotlin
class PrivacySettingsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("pose_coach_privacy", Context.MODE_PRIVATE)
    private val listeners = mutableSetOf<PrivacySettingsListener>()

    interface PrivacySettingsListener {
        fun onPrivacySettingsChanged(settings: PrivacySettings)
    }

    fun getPrivacySettings(): PrivacySettings {
        return PrivacySettings(
            allowApiCalls = prefs.getBoolean(KEY_ALLOW_API_CALLS, false),
            allowDataTransmission = prefs.getBoolean(KEY_ALLOW_DATA_TRANSMISSION, false),
            anonymizeLandmarks = prefs.getBoolean(KEY_ANONYMIZE_LANDMARKS, true),
            limitDataPrecision = prefs.getBoolean(KEY_LIMIT_DATA_PRECISION, false),
            requireExplicitConsent = prefs.getBoolean(KEY_REQUIRE_EXPLICIT_CONSENT, true),
            localProcessingOnly = prefs.getBoolean(KEY_LOCAL_PROCESSING_ONLY, true),
            dataRetentionDays = prefs.getInt(KEY_DATA_RETENTION_DAYS, 30),
            allowAnalytics = prefs.getBoolean(KEY_ALLOW_ANALYTICS, false)
        )
    }

    fun updatePrivacySettings(settings: PrivacySettings) {
        prefs.edit()
            .putBoolean(KEY_ALLOW_API_CALLS, settings.allowApiCalls)
            .putBoolean(KEY_ALLOW_DATA_TRANSMISSION, settings.allowDataTransmission)
            .putBoolean(KEY_ANONYMIZE_LANDMARKS, settings.anonymizeLandmarks)
            .putBoolean(KEY_LIMIT_DATA_PRECISION, settings.limitDataPrecision)
            .putBoolean(KEY_REQUIRE_EXPLICIT_CONSENT, settings.requireExplicitConsent)
            .putBoolean(KEY_LOCAL_PROCESSING_ONLY, settings.localProcessingOnly)
            .putInt(KEY_DATA_RETENTION_DAYS, settings.dataRetentionDays)
            .putBoolean(KEY_ALLOW_ANALYTICS, settings.allowAnalytics)
            .apply()

        // Notify listeners
        listeners.forEach { it.onPrivacySettingsChanged(settings) }
    }

    fun addListener(listener: PrivacySettingsListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: PrivacySettingsListener) {
        listeners.remove(listener)
    }

    companion object {
        private const val KEY_ALLOW_API_CALLS = "allow_api_calls"
        private const val KEY_ALLOW_DATA_TRANSMISSION = "allow_data_transmission"
        private const val KEY_ANONYMIZE_LANDMARKS = "anonymize_landmarks"
        private const val KEY_LIMIT_DATA_PRECISION = "limit_data_precision"
        private const val KEY_REQUIRE_EXPLICIT_CONSENT = "require_explicit_consent"
        private const val KEY_LOCAL_PROCESSING_ONLY = "local_processing_only"
        private const val KEY_DATA_RETENTION_DAYS = "data_retention_days"
        private const val KEY_ALLOW_ANALYTICS = "allow_analytics"
    }
}
```

### 2. Enhanced Privacy Settings Data Class

Extended privacy settings with granular controls:

```kotlin
data class PrivacySettings(
    val allowApiCalls: Boolean = false,
    val allowDataTransmission: Boolean = false,
    val anonymizeLandmarks: Boolean = true,
    val limitDataPrecision: Boolean = false,
    val requireExplicitConsent: Boolean = true,
    val localProcessingOnly: Boolean = true,
    val dataRetentionDays: Int = 30,
    val allowAnalytics: Boolean = false,
    val encryptLocalData: Boolean = true,
    val shareAggregatedData: Boolean = false,
    val allowResearchParticipation: Boolean = false
) {
    // Validation methods
    fun isValid(): Boolean {
        return dataRetentionDays in 1..365
    }

    // Convenience methods
    fun isStrictMode(): Boolean {
        return !allowApiCalls && localProcessingOnly && requireExplicitConsent
    }

    fun isBalancedMode(): Boolean {
        return allowApiCalls && anonymizeLandmarks && !shareAggregatedData
    }

    fun isOptimizedMode(): Boolean {
        return allowApiCalls && allowDataTransmission && allowAnalytics
    }

    // Create preset configurations
    companion object {
        fun strictPrivacy() = PrivacySettings(
            allowApiCalls = false,
            allowDataTransmission = false,
            anonymizeLandmarks = true,
            limitDataPrecision = true,
            requireExplicitConsent = true,
            localProcessingOnly = true,
            allowAnalytics = false
        )

        fun balancedPrivacy() = PrivacySettings(
            allowApiCalls = true,
            allowDataTransmission = true,
            anonymizeLandmarks = true,
            limitDataPrecision = false,
            requireExplicitConsent = false,
            localProcessingOnly = false,
            allowAnalytics = false
        )

        fun optimizedExperience() = PrivacySettings(
            allowApiCalls = true,
            allowDataTransmission = true,
            anonymizeLandmarks = false,
            limitDataPrecision = false,
            requireExplicitConsent = false,
            localProcessingOnly = false,
            allowAnalytics = true,
            shareAggregatedData = true
        )
    }
}
```

## Privacy UI Components

### 1. Comprehensive Privacy Settings Activity

Create a full-featured privacy settings screen:

```kotlin
class PrivacySettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPrivacySettingsBinding
    private lateinit var privacyManager: PrivacySettingsManager
    private var currentSettings = PrivacySettings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        privacyManager = PrivacySettingsManager(this)
        currentSettings = privacyManager.getPrivacySettings()

        setupUI()
        loadCurrentSettings()
    }

    private fun setupUI() {
        // Setup toolbar
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Setup preset buttons
        binding.strictModeButton.setOnClickListener {
            applyPreset(PrivacySettings.strictPrivacy())
        }

        binding.balancedModeButton.setOnClickListener {
            applyPreset(PrivacySettings.balancedPrivacy())
        }

        binding.optimizedModeButton.setOnClickListener {
            applyPreset(PrivacySettings.optimizedExperience())
        }

        // Setup individual setting toggles
        setupToggleListeners()

        // Setup data retention slider
        binding.dataRetentionSlider.addOnChangeListener { _, value, _ ->
            currentSettings = currentSettings.copy(dataRetentionDays = value.toInt())
            binding.dataRetentionLabel.text = "Data retention: ${value.toInt()} days"
        }

        // Setup save button
        binding.saveButton.setOnClickListener {
            saveSettings()
        }

        // Setup reset button
        binding.resetButton.setOnClickListener {
            showResetConfirmationDialog()
        }

        // Setup export/import buttons
        binding.exportButton.setOnClickListener {
            exportPrivacySettings()
        }

        binding.importButton.setOnClickListener {
            importPrivacySettings()
        }
    }

    private fun setupToggleListeners() {
        binding.allowApiCallsToggle.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(allowApiCalls = isChecked)
            updateDependentSettings()
        }

        binding.allowDataTransmissionToggle.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(allowDataTransmission = isChecked)
            updateDependentSettings()
        }

        binding.anonymizeLandmarksToggle.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(anonymizeLandmarks = isChecked)
        }

        binding.limitDataPrecisionToggle.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(limitDataPrecision = isChecked)
        }

        binding.requireExplicitConsentToggle.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(requireExplicitConsent = isChecked)
        }

        binding.localProcessingOnlyToggle.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(localProcessingOnly = isChecked)
            updateDependentSettings()
        }

        binding.allowAnalyticsToggle.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(allowAnalytics = isChecked)
        }

        binding.encryptLocalDataToggle.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(encryptLocalData = isChecked)
        }

        binding.shareAggregatedDataToggle.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(shareAggregatedData = isChecked)
        }

        binding.allowResearchParticipationToggle.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(allowResearchParticipation = isChecked)
        }
    }

    private fun loadCurrentSettings() {
        binding.allowApiCallsToggle.isChecked = currentSettings.allowApiCalls
        binding.allowDataTransmissionToggle.isChecked = currentSettings.allowDataTransmission
        binding.anonymizeLandmarksToggle.isChecked = currentSettings.anonymizeLandmarks
        binding.limitDataPrecisionToggle.isChecked = currentSettings.limitDataPrecision
        binding.requireExplicitConsentToggle.isChecked = currentSettings.requireExplicitConsent
        binding.localProcessingOnlyToggle.isChecked = currentSettings.localProcessingOnly
        binding.allowAnalyticsToggle.isChecked = currentSettings.allowAnalytics
        binding.encryptLocalDataToggle.isChecked = currentSettings.encryptLocalData
        binding.shareAggregatedDataToggle.isChecked = currentSettings.shareAggregatedData
        binding.allowResearchParticipationToggle.isChecked = currentSettings.allowResearchParticipation

        binding.dataRetentionSlider.value = currentSettings.dataRetentionDays.toFloat()
        binding.dataRetentionLabel.text = "Data retention: ${currentSettings.dataRetentionDays} days"

        updateDependentSettings()
        updatePrivacyLevelIndicator()
    }

    private fun applyPreset(preset: PrivacySettings) {
        currentSettings = preset
        loadCurrentSettings()

        // Show confirmation
        Snackbar.make(binding.root, "Preset applied", Snackbar.LENGTH_SHORT).show()
    }

    private fun updateDependentSettings() {
        // Disable data transmission if API calls are disabled
        binding.allowDataTransmissionToggle.isEnabled = currentSettings.allowApiCalls
        if (!currentSettings.allowApiCalls) {
            binding.allowDataTransmissionToggle.isChecked = false
            currentSettings = currentSettings.copy(allowDataTransmission = false)
        }

        // Disable API-dependent features if local processing only
        val isApiEnabled = !currentSettings.localProcessingOnly
        binding.allowApiCallsToggle.isEnabled = isApiEnabled
        binding.shareAggregatedDataToggle.isEnabled = isApiEnabled

        if (currentSettings.localProcessingOnly) {
            binding.allowApiCallsToggle.isChecked = false
            binding.shareAggregatedDataToggle.isChecked = false
            currentSettings = currentSettings.copy(
                allowApiCalls = false,
                shareAggregatedData = false
            )
        }

        updatePrivacyLevelIndicator()
    }

    private fun updatePrivacyLevelIndicator() {
        val (level, color) = when {
            currentSettings.isStrictMode() -> "Strict Privacy" to ContextCompat.getColor(this, R.color.privacy_strict)
            currentSettings.isBalancedMode() -> "Balanced Privacy" to ContextCompat.getColor(this, R.color.privacy_balanced)
            currentSettings.isOptimizedMode() -> "Optimized Experience" to ContextCompat.getColor(this, R.color.privacy_optimized)
            else -> "Custom Settings" to ContextCompat.getColor(this, R.color.privacy_custom)
        }

        binding.privacyLevelIndicator.text = level
        binding.privacyLevelIndicator.setTextColor(color)
    }

    private fun saveSettings() {
        if (!currentSettings.isValid()) {
            showValidationError()
            return
        }

        privacyManager.updatePrivacySettings(currentSettings)

        Snackbar.make(binding.root, "Privacy settings saved", Snackbar.LENGTH_SHORT).show()

        // Return result to indicate settings were changed
        setResult(Activity.RESULT_OK)
    }

    private fun showValidationError() {
        AlertDialog.Builder(this)
            .setTitle("Invalid Settings")
            .setMessage("Data retention must be between 1 and 365 days.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Privacy Settings")
            .setMessage("This will reset all privacy settings to their default values. Are you sure?")
            .setPositiveButton("Reset") { _, _ ->
                applyPreset(PrivacySettings.strictPrivacy())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportPrivacySettings() {
        // Create JSON representation of settings
        val settingsJson = Gson().toJson(currentSettings)

        // Share or save the settings
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, settingsJson)
            putExtra(Intent.EXTRA_SUBJECT, "Pose Coach Privacy Settings")
        }

        startActivity(Intent.createChooser(intent, "Export Privacy Settings"))
    }

    private fun importPrivacySettings() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/*"
        }

        importSettingsLauncher.launch(intent)
    }

    private val importSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val settingsJson = inputStream?.bufferedReader().use { it?.readText() }
                    val importedSettings = Gson().fromJson(settingsJson, PrivacySettings::class.java)

                    if (importedSettings.isValid()) {
                        applyPreset(importedSettings)
                        Snackbar.make(binding.root, "Settings imported successfully", Snackbar.LENGTH_SHORT).show()
                    } else {
                        showImportError("Invalid settings in imported file")
                    }
                } catch (e: Exception) {
                    showImportError("Failed to import settings: ${e.message}")
                }
            }
        }
    }

    private fun showImportError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Import Failed")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
```

### 2. Privacy Consent Dialog

Implement a comprehensive consent dialog:

```kotlin
class PrivacyConsentDialog : DialogFragment() {
    interface ConsentListener {
        fun onConsentGiven(settings: PrivacySettings)
        fun onConsentDenied()
        fun onMoreInfoRequested()
    }

    private var listener: ConsentListener? = null
    private var isFirstTime: Boolean = true

    fun setConsentListener(listener: ConsentListener) {
        this.listener = listener
    }

    fun setFirstTime(isFirstTime: Boolean) {
        this.isFirstTime = isFirstTime
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogPrivacyConsentBinding.inflate(layoutInflater)

        setupConsentOptions(binding)

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .setCancelable(false)
            .create()
    }

    private fun setupConsentOptions(binding: DialogPrivacyConsentBinding) {
        // Set up consent text based on whether this is first time
        if (isFirstTime) {
            binding.consentTitle.text = "Welcome to Pose Coach"
            binding.consentMessage.text = """
                To provide you with the best coaching experience, we need to understand how you'd like us to handle your data.

                Your privacy is our priority. You can change these settings anytime.
            """.trimIndent()
        } else {
            binding.consentTitle.text = "Privacy Consent Required"
            binding.consentMessage.text = """
                We need your consent to process pose data for AI-powered suggestions.

                Please choose your preferred privacy level.
            """.trimIndent()
        }

        // Set up preset options
        binding.strictPrivacyCard.setOnClickListener {
            animateSelection(binding.strictPrivacyCard)
            binding.confirmButton.text = "Use Strict Privacy"
            binding.confirmButton.setOnClickListener {
                listener?.onConsentGiven(PrivacySettings.strictPrivacy())
                dismiss()
            }
        }

        binding.balancedPrivacyCard.setOnClickListener {
            animateSelection(binding.balancedPrivacyCard)
            binding.confirmButton.text = "Use Balanced Privacy"
            binding.confirmButton.setOnClickListener {
                listener?.onConsentGiven(PrivacySettings.balancedPrivacy())
                dismiss()
            }
        }

        binding.optimizedExperienceCard.setOnClickListener {
            animateSelection(binding.optimizedExperienceCard)
            binding.confirmButton.text = "Use Optimized Experience"
            binding.confirmButton.setOnClickListener {
                listener?.onConsentGiven(PrivacySettings.optimizedExperience())
                dismiss()
            }
        }

        // Set up action buttons
        binding.moreInfoButton.setOnClickListener {
            listener?.onMoreInfoRequested()
        }

        binding.customizeButton.setOnClickListener {
            // Open detailed privacy settings
            val intent = Intent(requireContext(), PrivacySettingsActivity::class.java)
            startActivity(intent)
            dismiss()
        }

        binding.denyButton.setOnClickListener {
            showDenyConfirmation()
        }

        // Initially disable confirm button
        binding.confirmButton.isEnabled = false
        binding.confirmButton.alpha = 0.5f
    }

    private fun animateSelection(selectedCard: View) {
        // Reset all cards
        val cards = listOf(
            dialog?.findViewById<View>(R.id.strictPrivacyCard),
            dialog?.findViewById<View>(R.id.balancedPrivacyCard),
            dialog?.findViewById<View>(R.id.optimizedExperienceCard)
        )

        cards.forEach { card ->
            card?.animate()
                ?.scaleX(1.0f)
                ?.scaleY(1.0f)
                ?.alpha(0.7f)
                ?.setDuration(200)
                ?.start()
        }

        // Animate selected card
        selectedCard.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .alpha(1.0f)
            .setDuration(200)
            .start()

        // Enable confirm button
        dialog?.findViewById<Button>(R.id.confirmButton)?.let { button ->
            button.isEnabled = true
            button.animate()
                .alpha(1.0f)
                .setDuration(200)
                .start()
        }
    }

    private fun showDenyConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Denial")
            .setMessage("""
                By denying consent, you will only be able to use basic pose detection without AI suggestions.

                You can change this decision anytime in Settings.
            """.trimIndent())
            .setPositiveButton("Confirm Denial") { _, _ ->
                listener?.onConsentDenied()
                dismiss()
            }
            .setNegativeButton("Go Back", null)
            .show()
    }
}
```

### 3. Privacy Status Indicator

Create a persistent privacy status indicator:

```kotlin
class PrivacyStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val statusIcon: ImageView
    private val statusText: TextView
    private val detailsButton: Button

    private var privacySettings: PrivacySettings? = null
    private var onDetailsClickListener: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(16, 8, 16, 8)

        // Inflate layout
        LayoutInflater.from(context).inflate(R.layout.view_privacy_status, this, true)

        statusIcon = findViewById(R.id.statusIcon)
        statusText = findViewById(R.id.statusText)
        detailsButton = findViewById(R.id.detailsButton)

        detailsButton.setOnClickListener {
            onDetailsClickListener?.invoke()
        }
    }

    fun updatePrivacyStatus(settings: PrivacySettings) {
        this.privacySettings = settings

        val (statusMessage, iconRes, backgroundColor) = when {
            settings.isStrictMode() -> Triple(
                "Strict Privacy: All processing local",
                R.drawable.ic_privacy_strict,
                ContextCompat.getColor(context, R.color.privacy_strict_bg)
            )
            settings.isBalancedMode() -> Triple(
                "Balanced Privacy: Anonymized data only",
                R.drawable.ic_privacy_balanced,
                ContextCompat.getColor(context, R.color.privacy_balanced_bg)
            )
            settings.isOptimizedMode() -> Triple(
                "Optimized Experience: Full AI features",
                R.drawable.ic_privacy_optimized,
                ContextCompat.getColor(context, R.color.privacy_optimized_bg)
            )
            else -> Triple(
                "Custom Privacy Settings",
                R.drawable.ic_privacy_custom,
                ContextCompat.getColor(context, R.color.privacy_custom_bg)
            )
        }

        statusText.text = statusMessage
        statusIcon.setImageResource(iconRes)
        setBackgroundColor(backgroundColor)

        // Add visual indicator for data transmission status
        if (settings.allowDataTransmission) {
            addTransmissionIndicator()
        } else {
            removeTransmissionIndicator()
        }
    }

    private fun addTransmissionIndicator() {
        // Add small dot indicator for active data transmission
        val indicator = View(context).apply {
            layoutParams = LayoutParams(12, 12).apply {
                marginStart = 8
            }
            background = ContextCompat.getDrawable(context, R.drawable.transmission_indicator)
        }

        // Animate the indicator
        val animation = AlphaAnimation(0.3f, 1.0f).apply {
            duration = 1000
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        indicator.startAnimation(animation)

        addView(indicator)
    }

    private fun removeTransmissionIndicator() {
        // Remove any existing transmission indicators
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            if (child.tag == "transmission_indicator") {
                removeViewAt(i)
            }
        }
    }

    fun setOnDetailsClickListener(listener: () -> Unit) {
        onDetailsClickListener = listener
    }

    fun showPrivacyAlert(message: String) {
        // Briefly highlight the status view to draw attention
        val originalColor = (background as? ColorDrawable)?.color ?: Color.TRANSPARENT
        val alertColor = ContextCompat.getColor(context, R.color.privacy_alert)

        val colorAnimation = ValueAnimator.ofArgb(originalColor, alertColor, originalColor)
        colorAnimation.duration = 1000
        colorAnimation.addUpdateListener { animator ->
            setBackgroundColor(animator.animatedValue as Int)
        }
        colorAnimation.start()

        // Show temporary message
        val originalText = statusText.text
        statusText.text = message

        Handler(Looper.getMainLooper()).postDelayed({
            statusText.text = originalText
        }, 3000)
    }
}
```

## Data Anonymization Examples

### 1. Landmark Anonymization

Implement pose data anonymization:

```kotlin
class PoseDataAnonymizer {
    companion object {
        fun anonymizeLandmarks(landmarks: List<PoseLandmark>, settings: PrivacySettings): List<PoseLandmark> {
            if (!settings.anonymizeLandmarks) {
                return landmarks
            }

            return landmarks.map { landmark ->
                val anonymized = landmark.copy(
                    x = if (settings.limitDataPrecision) roundToPrecision(landmark.x, 0.1f) else landmark.x,
                    y = if (settings.limitDataPrecision) roundToPrecision(landmark.y, 0.1f) else landmark.y,
                    z = if (settings.limitDataPrecision) roundToPrecision(landmark.z, 0.1f) else landmark.z
                )

                // Remove facial landmarks for additional privacy
                if (isFacialLandmark(landmark.index)) {
                    anonymized.copy(
                        x = 0f,
                        y = 0f,
                        z = 0f,
                        visibility = 0f
                    )
                } else {
                    anonymized
                }
            }
        }

        private fun roundToPrecision(value: Float, precision: Float): Float {
            return (value / precision).roundToInt() * precision
        }

        private fun isFacialLandmark(index: Int): Boolean {
            // MediaPipe pose landmarks 0-10 are facial features
            return index in 0..10
        }

        fun generateHashedId(landmarks: List<PoseLandmark>): String {
            // Generate a consistent but anonymized identifier
            val keyPoints = landmarks.take(5) // Use only key body points
            val hashInput = keyPoints.joinToString("") {
                "${(it.x * 100).toInt()}_${(it.y * 100).toInt()}"
            }
            return hashInput.hashCode().toString(16)
        }
    }
}
```

### 2. Local Processing Fallback

Implement local-only suggestion generation:

```kotlin
class LocalSuggestionGenerator : PoseSuggestionClient {
    private val basicSuggestions = mapOf(
        "posture" to listOf(
            PoseSuggestion(
                title = "Straighten Your Back",
                instruction = "Keep your spine aligned for better posture",
                targetLandmarks = listOf("left_shoulder", "right_shoulder", "nose")
            ),
            PoseSuggestion(
                title = "Level Your Shoulders",
                instruction = "Ensure both shoulders are at the same height",
                targetLandmarks = listOf("left_shoulder", "right_shoulder")
            )
        ),
        "balance" to listOf(
            PoseSuggestion(
                title = "Center Your Weight",
                instruction = "Distribute weight evenly between both feet",
                targetLandmarks = listOf("left_ankle", "right_ankle")
            )
        )
    )

    override suspend fun getPoseSuggestions(landmarks: PoseLandmarksData): Result<PoseSuggestionsResponse> {
        return try {
            val suggestions = analyzePostureLocally(landmarks.landmarks)
            Result.success(PoseSuggestionsResponse(suggestions))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun analyzePostureLocally(landmarks: List<PoseLandmarksData.LandmarkPoint>): List<PoseSuggestion> {
        val suggestions = mutableListOf<PoseSuggestion>()

        // Simple posture analysis
        val shoulderImbalance = detectShoulderImbalance(landmarks)
        if (shoulderImbalance > 0.1f) {
            suggestions.addAll(basicSuggestions["posture"] ?: emptyList())
        }

        val balanceIssue = detectBalanceIssue(landmarks)
        if (balanceIssue > 0.15f) {
            suggestions.addAll(basicSuggestions["balance"] ?: emptyList())
        }

        return suggestions.take(3) // Limit to 3 suggestions
    }

    private fun detectShoulderImbalance(landmarks: List<PoseLandmarksData.LandmarkPoint>): Float {
        val leftShoulder = landmarks.find { it.index == 11 } // Left shoulder
        val rightShoulder = landmarks.find { it.index == 12 } // Right shoulder

        return if (leftShoulder != null && rightShoulder != null) {
            kotlin.math.abs(leftShoulder.y - rightShoulder.y)
        } else {
            0f
        }
    }

    private fun detectBalanceIssue(landmarks: List<PoseLandmarksData.LandmarkPoint>): Float {
        val leftAnkle = landmarks.find { it.index == 27 } // Left ankle
        val rightAnkle = landmarks.find { it.index == 28 } // Right ankle

        return if (leftAnkle != null && rightAnkle != null) {
            kotlin.math.abs(leftAnkle.x - rightAnkle.x)
        } else {
            0f
        }
    }
}
```

## Privacy-Aware Workflow Examples

### 1. Consent-Based Workflow

Implement workflow that respects user consent:

```kotlin
class PrivacyAwareWorkflowManager(
    private val privacyManager: PrivacySettingsManager,
    private val consentManager: ConsentManager
) {
    suspend fun processUserAction(action: UserAction): ActionResult {
        val privacySettings = privacyManager.getPrivacySettings()

        return when (action) {
            is GenerateSuggestionsAction -> {
                handleSuggestionsRequest(action, privacySettings)
            }
            is AnalyzePerformanceAction -> {
                handlePerformanceAnalysis(action, privacySettings)
            }
            is ShareDataAction -> {
                handleDataSharing(action, privacySettings)
            }
            else -> ActionResult.Success
        }
    }

    private suspend fun handleSuggestionsRequest(
        action: GenerateSuggestionsAction,
        settings: PrivacySettings
    ): ActionResult {
        // Check if explicit consent is required
        if (settings.requireExplicitConsent) {
            val consentGiven = consentManager.requestConsent(
                ConsentType.AI_SUGGESTIONS,
                "Generate personalized pose suggestions"
            )

            if (!consentGiven) {
                return ActionResult.ConsentDenied("User denied consent for AI suggestions")
            }
        }

        // Check if API calls are allowed
        if (!settings.allowApiCalls) {
            return processLocally(action)
        }

        // Process with appropriate privacy controls
        val anonymizedData = if (settings.anonymizeLandmarks) {
            PoseDataAnonymizer.anonymizeLandmarks(action.landmarks, settings)
        } else {
            action.landmarks
        }

        return processWithPrivacy(anonymizedData, settings)
    }

    private suspend fun processLocally(action: GenerateSuggestionsAction): ActionResult {
        val localProcessor = LocalSuggestionGenerator()
        return try {
            val result = localProcessor.getPoseSuggestions(action.landmarksData)
            ActionResult.Success(result.getOrThrow())
        } catch (e: Exception) {
            ActionResult.Error("Local processing failed: ${e.message}")
        }
    }

    private suspend fun handleDataSharing(
        action: ShareDataAction,
        settings: PrivacySettings
    ): ActionResult {
        if (!settings.shareAggregatedData) {
            return ActionResult.Blocked("Data sharing disabled by privacy settings")
        }

        // Show additional consent dialog for data sharing
        val consentGiven = consentManager.requestConsent(
            ConsentType.DATA_SHARING,
            "Share anonymized data to improve the service"
        )

        return if (consentGiven) {
            processDataSharing(action, settings)
        } else {
            ActionResult.ConsentDenied("User denied consent for data sharing")
        }
    }
}
```

### 2. Privacy Audit Trail

Implement privacy action logging:

```kotlin
class PrivacyAuditLogger(private val context: Context) {
    private val database = PrivacyAuditDatabase.getInstance(context)

    fun logPrivacyAction(action: PrivacyAction) {
        lifecycleScope.launch(Dispatchers.IO) {
            val auditEntry = PrivacyAuditEntry(
                timestamp = System.currentTimeMillis(),
                action = action.type,
                description = action.description,
                consentGiven = action.consentGiven,
                dataAnonymized = action.dataAnonymized,
                processingMode = action.processingMode
            )

            database.auditDao().insertAuditEntry(auditEntry)

            // Clean up old entries (keep only last 90 days)
            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90)
            database.auditDao().deleteOldEntries(cutoffTime)
        }
    }

    suspend fun getAuditLog(days: Int = 30): List<PrivacyAuditEntry> {
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        return database.auditDao().getAuditEntries(cutoffTime)
    }

    fun exportAuditLog(): String {
        return runBlocking {
            val entries = getAuditLog(90) // Last 90 days
            Gson().toJson(entries)
        }
    }
}

data class PrivacyAction(
    val type: String,
    val description: String,
    val consentGiven: Boolean,
    val dataAnonymized: Boolean,
    val processingMode: String
)

@Entity(tableName = "privacy_audit")
data class PrivacyAuditEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val action: String,
    val description: String,
    val consentGiven: Boolean,
    val dataAnonymized: Boolean,
    val processingMode: String
)
```

## Testing Privacy Controls

### 1. Privacy Settings Test

Create comprehensive tests for privacy functionality:

```kotlin
@Test
class PrivacyControlsTest {
    private lateinit var privacyManager: PrivacySettingsManager
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockContext = mockk()
        privacyManager = PrivacySettingsManager(mockContext)
    }

    @Test
    fun `strict privacy mode disables all external calls`() {
        val strictSettings = PrivacySettings.strictPrivacy()

        assertFalse(strictSettings.allowApiCalls)
        assertFalse(strictSettings.allowDataTransmission)
        assertTrue(strictSettings.localProcessingOnly)
        assertTrue(strictSettings.requireExplicitConsent)
    }

    @Test
    fun `anonymization properly removes facial landmarks`() {
        val originalLandmarks = createTestLandmarks()
        val settings = PrivacySettings(anonymizeLandmarks = true)

        val anonymized = PoseDataAnonymizer.anonymizeLandmarks(originalLandmarks, settings)

        // Check that facial landmarks are zeroed out
        anonymized.filter { it.index in 0..10 }.forEach { landmark ->
            assertEquals(0f, landmark.x)
            assertEquals(0f, landmark.y)
            assertEquals(0f, landmark.z)
            assertEquals(0f, landmark.visibility)
        }
    }

    @Test
    fun `privacy aware client respects local only setting`() = runTest {
        val localOnlySettings = PrivacySettings(localProcessingOnly = true)
        val mockDelegate = mockk<PoseSuggestionClient>()

        val privacyClient = PrivacyAwareSuggestionsClient(mockDelegate, localOnlySettings)

        val result = privacyClient.getPoseSuggestions(createTestLandmarksData())

        // Verify delegate was never called
        verify(exactly = 0) { mockDelegate.getPoseSuggestions(any()) }
        assertTrue(result.isSuccess)
    }

    private fun createTestLandmarks(): List<PoseLandmark> {
        return (0..32).map { index ->
            PoseLandmark(
                index = index,
                x = 0.5f,
                y = 0.5f,
                z = 0f,
                visibility = 1f,
                presence = 1f
            )
        }
    }
}
```

This comprehensive privacy controls implementation provides users with granular control over their data while maintaining transparency about how their information is processed. The modular design allows for easy customization and extension based on specific privacy requirements.

---

These examples demonstrate how to implement robust privacy controls that respect user preferences while providing a seamless experience. The key is to make privacy settings easily understandable and accessible to users.