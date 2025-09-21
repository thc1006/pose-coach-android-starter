# Week 4 Implementation Templates

## Enhanced GeminiClient.kt Template

```kotlin
package com.posecoach.suggestions.gemini

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.posecoach.core.pose.models.PoseDetectionResult
import com.posecoach.suggestions.models.*
import com.posecoach.suggestions.schema.PoseSuggestionSchema
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import timber.log.Timber

class GeminiClient(
    private val apiKey: String,
    private val config: GeminiConfig = GeminiConfig.default()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash-exp",
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = config.temperature
            topK = config.topK
            topP = config.topP
            maxOutputTokens = config.maxOutputTokens
            responseMimeType = "application/json"
            responseSchema = PoseSuggestionSchema.getSchema()
        }
    )

    suspend fun analyzePose(
        poseResult: PoseDetectionResult,
        context: AnalysisContext = AnalysisContext.default()
    ): Result<PoseSuggestionResponse> {
        return try {
            withTimeout(config.timeoutMs) {
                val prompt = buildAnalysisPrompt(poseResult, context)
                val response = generativeModel.generateContent(prompt)

                val responseText = response.text ?: throw Exception("Empty response from Gemini")
                val suggestion = json.decodeFromString<PoseSuggestionResponse>(responseText)

                Timber.d("Gemini analysis completed - Score: ${suggestion.overallScore}")
                Result.success(suggestion)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze pose with Gemini")
            Result.failure(e)
        }
    }

    private fun buildAnalysisPrompt(
        poseResult: PoseDetectionResult,
        context: AnalysisContext
    ): String {
        val landmarksJson = json.encodeToString(
            PoseDetectionResult.serializer(),
            poseResult
        )

        return """
            You are an expert fitness coach analyzing a person's pose for photography and exercise form.

            POSE DATA:
            ${landmarksJson}

            ANALYSIS CONTEXT:
            - Activity: ${context.activityType}
            - Focus Areas: ${context.focusAreas.joinToString(", ")}
            - User Experience Level: ${context.experienceLevel}
            - Goal: ${context.goal}

            REQUIREMENTS:
            1. Analyze the 33-point pose landmarks for:
               - Overall posture and alignment
               - Balance and stability
               - Symmetry between left and right sides
               - Key joint angles and positioning

            2. Provide actionable feedback in the specified JSON schema

            3. Score the pose from 0.0 to 1.0 where:
               - 0.9-1.0: Excellent pose, minimal adjustments needed
               - 0.7-0.8: Good pose, minor improvements possible
               - 0.5-0.6: Moderate issues, clear adjustments needed
               - 0.0-0.4: Significant form issues, major corrections required

            4. Focus on the most impactful improvements first

            5. Provide specific, actionable steps for improvement

            RESPOND WITH VALID JSON ONLY - NO ADDITIONAL TEXT OR EXPLANATION.
        """.trimIndent()
    }

    suspend fun validateResponse(response: PoseSuggestionResponse): ValidationResult {
        return try {
            val issues = mutableListOf<String>()

            // Validate score range
            if (response.overallScore !in 0f..1f) {
                issues.add("Overall score must be between 0.0 and 1.0")
            }

            // Validate confidence level
            if (response.confidenceLevel !in 0f..1f) {
                issues.add("Confidence level must be between 0.0 and 1.0")
            }

            // Validate suggestions
            if (response.suggestions.isEmpty()) {
                issues.add("At least one suggestion must be provided")
            }

            response.suggestions.forEach { suggestion ->
                if (suggestion.description.isBlank()) {
                    issues.add("Suggestion description cannot be empty")
                }
                if (suggestion.actionableSteps.isEmpty()) {
                    issues.add("Each suggestion must have actionable steps")
                }
            }

            if (issues.isEmpty()) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid(issues)
            }
        } catch (e: Exception) {
            ValidationResult.Invalid(listOf("Validation error: ${e.message}"))
        }
    }
}

data class GeminiConfig(
    val temperature: Float = 0.3f,
    val topK: Int = 32,
    val topP: Float = 0.95f,
    val maxOutputTokens: Int = 2048,
    val timeoutMs: Long = 10000L
) {
    companion object {
        fun default() = GeminiConfig()

        fun creative() = GeminiConfig(
            temperature = 0.7f,
            topP = 0.9f
        )

        fun conservative() = GeminiConfig(
            temperature = 0.1f,
            topK = 16
        )
    }
}

data class AnalysisContext(
    val activityType: String = "General Photography",
    val focusAreas: List<String> = listOf("posture", "balance", "alignment"),
    val experienceLevel: String = "Beginner",
    val goal: String = "Improve pose for better photos"
) {
    companion object {
        fun default() = AnalysisContext()

        fun fitness() = AnalysisContext(
            activityType = "Fitness Exercise",
            focusAreas = listOf("form", "safety", "effectiveness"),
            goal = "Optimize exercise form"
        )

        fun photography() = AnalysisContext(
            activityType = "Portrait Photography",
            focusAreas = listOf("aesthetics", "balance", "expression"),
            goal = "Create compelling portrait poses"
        )
    }
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val issues: List<String>) : ValidationResult()
}
```

## ConsentManager.kt Template

```kotlin
package com.posecoach.app.privacy.consent

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.*

class ConsentManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "consent_preferences",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val _consentState = MutableStateFlow(loadConsentState())
    val consentState: Flow<ConsentState> = _consentState

    private val _privacyEvents = MutableStateFlow<List<PrivacyEvent>>(emptyList())
    val privacyEvents: Flow<List<PrivacyEvent>> = _privacyEvents

    fun requestConsent(
        dataTypes: List<DataType>,
        purpose: ProcessingPurpose,
        isRequired: Boolean = false
    ): ConsentRequest {
        val requestId = UUID.randomUUID().toString()

        val request = ConsentRequest(
            id = requestId,
            dataTypes = dataTypes,
            purpose = purpose,
            isRequired = isRequired,
            requestTime = System.currentTimeMillis(),
            explanation = generateConsentExplanation(dataTypes, purpose)
        )

        logPrivacyEvent(
            PrivacyEvent.ConsentRequested(
                requestId = requestId,
                dataTypes = dataTypes,
                purpose = purpose
            )
        )

        return request
    }

    fun grantConsent(requestId: String, granted: Boolean): ConsentResult {
        val currentState = _consentState.value
        val updatedConsents = currentState.consents.toMutableMap()

        val consentRecord = ConsentRecord(
            requestId = requestId,
            granted = granted,
            grantTime = System.currentTimeMillis(),
            expiryTime = System.currentTimeMillis() + CONSENT_VALIDITY_PERIOD,
            version = CONSENT_VERSION
        )

        updatedConsents[requestId] = consentRecord

        val newState = currentState.copy(
            consents = updatedConsents,
            lastUpdated = System.currentTimeMillis()
        )

        saveConsentState(newState)
        _consentState.value = newState

        logPrivacyEvent(
            PrivacyEvent.ConsentGranted(
                requestId = requestId,
                granted = granted
            )
        )

        return if (granted) {
            ConsentResult.Granted(consentRecord)
        } else {
            ConsentResult.Denied(requestId)
        }
    }

    fun hasValidConsent(dataTypes: List<DataType>, purpose: ProcessingPurpose): Boolean {
        val currentState = _consentState.value
        val currentTime = System.currentTimeMillis()

        return currentState.consents.values.any { consent ->
            consent.granted &&
            consent.expiryTime > currentTime &&
            consent.version >= MINIMUM_CONSENT_VERSION
        }
    }

    fun revokeConsent(requestId: String): Boolean {
        val currentState = _consentState.value
        val updatedConsents = currentState.consents.toMutableMap()

        return if (updatedConsents.containsKey(requestId)) {
            val existingConsent = updatedConsents[requestId]!!
            updatedConsents[requestId] = existingConsent.copy(
                granted = false,
                revocationTime = System.currentTimeMillis()
            )

            val newState = currentState.copy(
                consents = updatedConsents,
                lastUpdated = System.currentTimeMillis()
            )

            saveConsentState(newState)
            _consentState.value = newState

            logPrivacyEvent(
                PrivacyEvent.ConsentRevoked(requestId = requestId)
            )

            true
        } else {
            false
        }
    }

    fun revokeAllConsents() {
        val currentState = _consentState.value
        val currentTime = System.currentTimeMillis()

        val revokedConsents = currentState.consents.mapValues { (_, consent) ->
            consent.copy(
                granted = false,
                revocationTime = currentTime
            )
        }

        val newState = currentState.copy(
            consents = revokedConsents,
            lastUpdated = currentTime
        )

        saveConsentState(newState)
        _consentState.value = newState

        logPrivacyEvent(PrivacyEvent.AllConsentsRevoked)
    }

    fun getConsentSummary(): ConsentSummary {
        val currentState = _consentState.value
        val currentTime = System.currentTimeMillis()

        val activeConsents = currentState.consents.values.count {
            it.granted && it.expiryTime > currentTime
        }
        val expiredConsents = currentState.consents.values.count {
            it.granted && it.expiryTime <= currentTime
        }
        val revokedConsents = currentState.consents.values.count {
            !it.granted && it.revocationTime != null
        }

        return ConsentSummary(
            totalRequests = currentState.consents.size,
            activeConsents = activeConsents,
            expiredConsents = expiredConsents,
            revokedConsents = revokedConsents,
            lastActivity = currentState.lastUpdated
        )
    }

    private fun generateConsentExplanation(
        dataTypes: List<DataType>,
        purpose: ProcessingPurpose
    ): String {
        val dataTypeNames = dataTypes.joinToString(", ") { it.displayName }

        return when (purpose) {
            ProcessingPurpose.POSE_ANALYSIS ->
                "We need to process your $dataTypeNames to analyze your pose and provide feedback."
            ProcessingPurpose.AI_COACHING ->
                "Your $dataTypeNames will be used to generate personalized coaching suggestions."
            ProcessingPurpose.PERFORMANCE_ANALYTICS ->
                "We collect $dataTypeNames to help improve app performance and user experience."
            ProcessingPurpose.RESEARCH ->
                "Your anonymized $dataTypeNames may be used for research to improve our pose analysis."
        }
    }

    private fun loadConsentState(): ConsentState {
        return try {
            val consentJson = encryptedPrefs.getString(CONSENT_STATE_KEY, null)
            if (consentJson != null) {
                json.decodeFromString(ConsentState.serializer(), consentJson)
            } else {
                ConsentState()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load consent state")
            ConsentState()
        }
    }

    private fun saveConsentState(state: ConsentState) {
        try {
            val consentJson = json.encodeToString(ConsentState.serializer(), state)
            encryptedPrefs.edit()
                .putString(CONSENT_STATE_KEY, consentJson)
                .apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to save consent state")
        }
    }

    private fun logPrivacyEvent(event: PrivacyEvent) {
        val currentEvents = _privacyEvents.value.toMutableList()
        currentEvents.add(event)

        // Keep only recent events
        if (currentEvents.size > MAX_PRIVACY_EVENTS) {
            currentEvents.removeFirst()
        }

        _privacyEvents.value = currentEvents
        Timber.d("Privacy event logged: ${event.javaClass.simpleName}")
    }

    companion object {
        private const val CONSENT_STATE_KEY = "consent_state"
        private const val CONSENT_VALIDITY_PERIOD = 365L * 24 * 60 * 60 * 1000 // 1 year
        private const val CONSENT_VERSION = 1
        private const val MINIMUM_CONSENT_VERSION = 1
        private const val MAX_PRIVACY_EVENTS = 100
    }
}

@Serializable
data class ConsentState(
    val consents: Map<String, ConsentRecord> = emptyMap(),
    val lastUpdated: Long = System.currentTimeMillis()
)

@Serializable
data class ConsentRecord(
    val requestId: String,
    val granted: Boolean,
    val grantTime: Long,
    val expiryTime: Long,
    val version: Int,
    val revocationTime: Long? = null
)

data class ConsentRequest(
    val id: String,
    val dataTypes: List<DataType>,
    val purpose: ProcessingPurpose,
    val isRequired: Boolean,
    val requestTime: Long,
    val explanation: String
)

sealed class ConsentResult {
    data class Granted(val consent: ConsentRecord) : ConsentResult()
    data class Denied(val requestId: String) : ConsentResult()
}

data class ConsentSummary(
    val totalRequests: Int,
    val activeConsents: Int,
    val expiredConsents: Int,
    val revokedConsents: Int,
    val lastActivity: Long
)

enum class DataType(val displayName: String) {
    CAMERA_FEED("Camera Feed"),
    POSE_LANDMARKS("Pose Data"),
    DEVICE_INFO("Device Information"),
    USAGE_ANALYTICS("Usage Analytics"),
    PERFORMANCE_METRICS("Performance Data")
}

enum class ProcessingPurpose {
    POSE_ANALYSIS,
    AI_COACHING,
    PERFORMANCE_ANALYTICS,
    RESEARCH
}

sealed class PrivacyEvent {
    data class ConsentRequested(
        val requestId: String,
        val dataTypes: List<DataType>,
        val purpose: ProcessingPurpose,
        val timestamp: Long = System.currentTimeMillis()
    ) : PrivacyEvent()

    data class ConsentGranted(
        val requestId: String,
        val granted: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    ) : PrivacyEvent()

    data class ConsentRevoked(
        val requestId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : PrivacyEvent()

    data class AllConsentsRevoked(
        val timestamp: Long = System.currentTimeMillis()
    ) : PrivacyEvent()

    data class DataProcessed(
        val dataType: DataType,
        val purpose: ProcessingPurpose,
        val timestamp: Long = System.currentTimeMillis()
    ) : PrivacyEvent()
}
```

## PrivacyDashboardActivity.kt Template

```kotlin
package com.posecoach.app.privacy.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.posecoach.app.databinding.ActivityPrivacyDashboardBinding
import com.posecoach.app.privacy.consent.ConsentManager
import com.posecoach.app.privacy.ui.adapters.ConsentAdapter
import com.posecoach.app.privacy.ui.adapters.PrivacyEventAdapter
import com.posecoach.app.privacy.viewmodel.PrivacyDashboardViewModel
import kotlinx.coroutines.launch

class PrivacyDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacyDashboardBinding
    private val viewModel: PrivacyDashboardViewModel by viewModels()

    private lateinit var consentAdapter: ConsentAdapter
    private lateinit var privacyEventAdapter: PrivacyEventAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacyDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerViews()
        setupClickListeners()
        observeViewModel()

        viewModel.loadPrivacyData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Privacy Dashboard"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupRecyclerViews() {
        consentAdapter = ConsentAdapter(
            onRevokeClick = { consentId ->
                viewModel.revokeConsent(consentId)
            },
            onDetailsClick = { consent ->
                showConsentDetails(consent)
            }
        )

        privacyEventAdapter = PrivacyEventAdapter()

        binding.recyclerViewConsents.apply {
            layoutManager = LinearLayoutManager(this@PrivacyDashboardActivity)
            adapter = consentAdapter
        }

        binding.recyclerViewEvents.apply {
            layoutManager = LinearLayoutManager(this@PrivacyDashboardActivity)
            adapter = privacyEventAdapter
        }
    }

    private fun setupClickListeners() {
        binding.buttonRevokeAll.setOnClickListener {
            showRevokeAllDialog()
        }

        binding.buttonExportData.setOnClickListener {
            viewModel.exportPrivacyData()
        }

        binding.buttonDeleteData.setOnClickListener {
            showDeleteDataDialog()
        }

        binding.cardDataSummary.setOnClickListener {
            showDataUsageSummary()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.consentSummary.collect { summary ->
                        updateConsentSummary(summary)
                    }
                }

                launch {
                    viewModel.consents.collect { consents ->
                        consentAdapter.submitList(consents)
                    }
                }

                launch {
                    viewModel.privacyEvents.collect { events ->
                        privacyEventAdapter.submitList(events)
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        updateUIState(state)
                    }
                }
            }
        }
    }

    private fun updateConsentSummary(summary: ConsentSummary) {
        binding.apply {
            textActiveConsents.text = summary.activeConsents.toString()
            textTotalRequests.text = summary.totalRequests.toString()
            textExpiredConsents.text = summary.expiredConsents.toString()
            textRevokedConsents.text = summary.revokedConsents.toString()

            val lastActivityText = if (summary.lastActivity > 0) {
                android.text.format.DateUtils.getRelativeTimeSpanString(summary.lastActivity)
            } else {
                "No activity"
            }
            textLastActivity.text = lastActivityText
        }
    }

    private fun updateUIState(state: PrivacyDashboardUiState) {
        binding.progressBar.visibility = if (state.isLoading) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }

        state.errorMessage?.let { message ->
            showError(message)
        }

        state.successMessage?.let { message ->
            showSuccess(message)
        }
    }

    private fun showConsentDetails(consent: ConsentRecord) {
        ConsentDetailsDialogFragment.newInstance(consent)
            .show(supportFragmentManager, "consent_details")
    }

    private fun showRevokeAllDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Revoke All Consents")
            .setMessage("This will revoke all your privacy consents. You may be asked to provide consent again to continue using the app.")
            .setPositiveButton("Revoke All") { _, _ ->
                viewModel.revokeAllConsents()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteDataDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete All Data")
            .setMessage("This will permanently delete all your data. This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteAllData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDataUsageSummary() {
        DataUsageSummaryDialogFragment()
            .show(supportFragmentManager, "data_usage")
    }

    private fun showError(message: String) {
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .show()
    }

    private fun showSuccess(message: String) {
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
```

## End-to-End Integration Test Template

```kotlin
package com.posecoach.app.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import com.posecoach.app.ui.MainActivity
import com.posecoach.core.pose.models.PoseDetectionResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EndToEndIntegrationTest {

    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.CAMERA
    )

    private lateinit var integrationValidator: EndToEndValidator

    @Before
    fun setup() {
        integrationValidator = EndToEndValidator(activityRule.activity)
    }

    @Test
    fun `complete pipeline should process pose within performance targets`() = runTest {
        // Initialize all components
        val startTime = System.currentTimeMillis()

        integrationValidator.initializeCameraPipeline()
        integrationValidator.initializeMediaPipe()
        integrationValidator.initializeOverlay()
        integrationValidator.initializeGeminiClient()

        val initializationTime = System.currentTimeMillis() - startTime
        assertThat(initializationTime).isLessThan(2000) // < 2s initialization

        // Test complete pipeline
        val pipelineResult = integrationValidator.runCompletePipeline()

        // Validate performance
        assertThat(pipelineResult.totalLatency).isLessThan(100) // < 100ms end-to-end
        assertThat(pipelineResult.cameraToMediaPipe).isLessThan(50) // < 50ms camera processing
        assertThat(pipelineResult.overlayAccuracy).isLessThan(2.0) // < 2px overlay accuracy

        // Validate functional correctness
        assertThat(pipelineResult.poseDetected).isTrue()
        assertThat(pipelineResult.overlayRendered).isTrue()
        assertThat(pipelineResult.suggestionsGenerated).isTrue()
    }

    @Test
    fun `privacy controls should be functional throughout pipeline`() = runTest {
        // Test consent flow
        val consentResult = integrationValidator.testConsentFlow()
        assertThat(consentResult.consentGranted).isTrue()

        // Test data processing with consent
        val processingResult = integrationValidator.testDataProcessingWithConsent()
        assertThat(processingResult.processingAllowed).isTrue()
        assertThat(processingResult.privacyEventLogged).isTrue()

        // Test consent revocation
        val revocationResult = integrationValidator.testConsentRevocation()
        assertThat(revocationResult.processingBlocked).isTrue()
    }

    @Test
    fun `system should handle errors gracefully`() = runTest {
        // Test camera failure recovery
        integrationValidator.simulateCameraFailure()
        val cameraRecovery = integrationValidator.testCameraRecovery()
        assertThat(cameraRecovery.recovered).isTrue()

        // Test MediaPipe failure recovery
        integrationValidator.simulateMediaPipeFailure()
        val mediapipeRecovery = integrationValidator.testMediaPipeRecovery()
        assertThat(mediapipeRecovery.recovered).isTrue()

        // Test network failure for Gemini
        integrationValidator.simulateNetworkFailure()
        val networkRecovery = integrationValidator.testGeminiRecovery()
        assertThat(networkRecovery.fallbackActivated).isTrue()
    }

    @Test
    fun `memory usage should remain within bounds`() = runTest {
        val memoryMonitor = integrationValidator.startMemoryMonitoring()

        // Run intensive operations
        repeat(100) {
            integrationValidator.processTestFrame()
        }

        val memoryStats = memoryMonitor.getStats()
        assertThat(memoryStats.peakUsageMB).isLessThan(300) // < 300MB peak
        assertThat(memoryStats.memoryLeaks).isEmpty()
    }
}

class EndToEndValidator(private val activity: MainActivity) {

    fun runCompletePipeline(): PipelineResult {
        val startTime = System.currentTimeMillis()

        // Simulate camera frame
        val cameraTime = simulateCameraFrame()

        // Process through MediaPipe
        val mediapipeTime = processFrameWithMediaPipe()

        // Render overlay
        val overlayTime = renderOverlay()

        // Generate suggestions
        val geminiTime = generateSuggestions()

        val totalTime = System.currentTimeMillis() - startTime

        return PipelineResult(
            totalLatency = totalTime,
            cameraToMediaPipe = cameraTime + mediapipeTime,
            overlayAccuracy = measureOverlayAccuracy(),
            poseDetected = true,
            overlayRendered = true,
            suggestionsGenerated = true
        )
    }

    // Additional validator methods...
}

data class PipelineResult(
    val totalLatency: Long,
    val cameraToMediaPipe: Long,
    val overlayAccuracy: Double,
    val poseDetected: Boolean,
    val overlayRendered: Boolean,
    val suggestionsGenerated: Boolean
)
```

## Performance Validation Templates

### PerformanceValidator.kt
```kotlin
package com.posecoach.app.integration

class PerformanceValidator {

    fun validateCameraPerformance(): CameraPerformanceReport {
        // Test camera initialization time
        // Test frame processing rate
        // Test memory usage during camera operations
        // Test battery impact
    }

    fun validateMediaPipePerformance(): MediaPipePerformanceReport {
        // Test pose detection latency
        // Test accuracy vs speed tradeoffs
        // Test memory usage during processing
        // Test CPU utilization
    }

    fun validateOverlayPerformance(): OverlayPerformanceReport {
        // Test coordinate transformation accuracy
        // Test rendering frame rate
        // Test memory usage during rendering
    }

    fun validateGeminiPerformance(): GeminiPerformanceReport {
        // Test API response times
        // Test structured output validation
        // Test error handling performance
        // Test rate limiting compliance
    }
}
```