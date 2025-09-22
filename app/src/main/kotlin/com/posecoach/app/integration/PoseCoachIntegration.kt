package com.posecoach.app.integration

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.posecoach.app.privacy.PrivacyManager
import com.posecoach.app.ui.SuggestionsOverlayView
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.repository.PoseDetectionListener
import com.posecoach.corepose.repository.PoseRepository
import com.posecoach.suggestions.ApiKeyManager
import com.posecoach.suggestions.SuggestionsOrchestrator
import kotlinx.coroutines.launch
import timber.log.Timber

class PoseCoachIntegration(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val suggestionsOverlay: SuggestionsOverlayView
) : PoseDetectionListener {

    private val apiKeyManager = ApiKeyManager(context)
    private val privacyManager = PrivacyManager(context)
    private val clientFactory = com.posecoach.suggestions.PoseSuggestionClientFactory(context)
    private val suggestionsOrchestrator = SuggestionsOrchestrator(
        context,
        clientFactory,
        privacyEnabled = true
    )

    private var isInitialized = false

    fun initialize() {
        if (isInitialized) return

        setupSuggestionsFlow()
        setupPrivacyCheck()
        setupRetryListener()

        isInitialized = true
        Timber.d("PoseCoachIntegration initialized")
    }

    private fun setupSuggestionsFlow() {
        lifecycleScope.launch {
            suggestionsOrchestrator.suggestionsFlow.collect { result ->
                if (result.isSuccess) {
                    val response = result.getOrNull()!!
                    if (response.suggestions.isNotEmpty()) {
                        suggestionsOverlay.showSuggestions(response.suggestions)
                    } else {
                        suggestionsOverlay.showLoading()
                    }
                } else {
                    val error = result.exceptionOrNull()
                    suggestionsOverlay.showError(
                        error?.message ?: "Failed to get suggestions"
                    )
                }
            }
        }
    }

    private fun setupPrivacyCheck() {
        if (!privacyManager.hasApiConsent()) {
            privacyManager.showConsentDialog(
                onAccept = {
                    lifecycleScope.launch {
                        suggestionsOrchestrator.updateClient()
                    }
                    Timber.d("User accepted AI suggestions")
                },
                onDecline = {
                    Timber.d("User declined AI suggestions, using local-only mode")
                }
            )
        }
    }

    private fun setupRetryListener() {
        suggestionsOverlay.setOnRetryListener {
            Timber.d("Retrying suggestions request")
            suggestionsOrchestrator.retryLastRequest()
        }
    }

    override fun onPoseDetected(result: PoseLandmarkResult) {
        if (!isInitialized) return

        lifecycleScope.launch {
            suggestionsOrchestrator.processPoseLandmarks(result)
        }
    }

    override fun onPoseDetectionError(error: com.posecoach.corepose.models.PoseDetectionError) {
        Timber.e("Pose detection error: ${error.message}")
        suggestionsOverlay.showError("Pose detection failed")
    }

    fun onPrivacySettingsChanged() {
        lifecycleScope.launch {
            suggestionsOrchestrator.updateClient()
        }
        Timber.d("Privacy settings updated")
    }

    fun reset() {
        suggestionsOrchestrator.reset()
        suggestionsOverlay.clear()
    }

    fun destroy() {
        suggestionsOrchestrator.onDestroy()
    }

    // Helper functions for demonstration
    companion object {
        fun createWithRepository(
            context: Context,
            lifecycleScope: LifecycleCoroutineScope,
            suggestionsOverlay: SuggestionsOverlayView,
            poseRepository: PoseRepository
        ): PoseCoachIntegration {
            val integration = PoseCoachIntegration(context, lifecycleScope, suggestionsOverlay)
            integration.initialize()

            // TODO: Fix method name - start method not available
            // poseRepository.start(integration)

            return integration
        }
    }
}