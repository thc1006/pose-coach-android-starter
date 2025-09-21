/*
 * Copyright 2024 Pose Coach Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.posecoach.gemini.live.examples

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.posecoach.gemini.live.LiveApiManager
import com.posecoach.gemini.live.models.LiveApiConfig
import com.posecoach.gemini.live.models.SessionState
import com.posecoach.gemini.live.session.PoseLandmark
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Example Activity demonstrating complete Gemini Live API integration
 * for real-time pose coaching with audio feedback
 */
class PoseCoachingActivity : ComponentActivity() {

    private lateinit var liveApiManager: LiveApiManager
    private var isSessionActive = false

    // Permission handling
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeLiveApi()
        } else {
            Toast.makeText(this, "Permissions required for pose coaching", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pose_coaching)

        checkPermissionsAndInitialize()
    }

    override fun onDestroy() {
        super.onDestroy()
        liveApiManager.cleanup()
    }

    private fun checkPermissionsAndInitialize() {
        val requiredPermissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET
        )

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            initializeLiveApi()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun initializeLiveApi() {
        try {
            // Configure Live API for pose coaching
            val config = LiveApiConfig(
                model = "models/gemini-2.0-flash-exp",
                voiceName = "Aoede",
                responseModalities = listOf("AUDIO"),
                temperature = 0.7,
                systemInstruction = """
                    You are an expert fitness coach specializing in real-time pose correction.

                    Guidelines:
                    - Provide immediate, actionable feedback
                    - Use encouraging and supportive language
                    - Focus on safety and proper form
                    - Keep audio responses under 10 seconds
                    - Adapt coaching style based on user progress

                    You have access to real-time pose analysis data. Use this information
                    to provide specific, targeted coaching advice.
                """.trimIndent(),
                enableVoiceActivityDetection = true,
                audioBufferSizeMs = 100
            )

            liveApiManager = LiveApiManager(
                context = this,
                config = config,
                scope = lifecycleScope
            )

            setupLiveApiObservers()
            configureAudioSettings()

        } catch (e: Exception) {
            Timber.e(e, "Error initializing Live API")
            Toast.makeText(this, "Failed to initialize AI coach", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupLiveApiObservers() {
        // Monitor session state
        liveApiManager.sessionState
            .onEach { state ->
                handleSessionStateChange(state)
            }
            .launchIn(lifecycleScope)

        // Handle audio responses from AI coach
        liveApiManager.audioResponse
            .onEach { audioData ->
                Timber.d("Received audio response: ${audioData.size} bytes")
                // Audio is automatically played by the LiveApiManager
            }
            .launchIn(lifecycleScope)

        // Handle text responses
        liveApiManager.textResponse
            .onEach { text ->
                Timber.d("AI Coach: $text")
                // Could display text feedback in UI
                showCoachingFeedback(text)
            }
            .launchIn(lifecycleScope)

        // Handle pose analysis results
        liveApiManager.poseAnalysisResults
            .onEach { result ->
                handlePoseAnalysisResult(result)
            }
            .launchIn(lifecycleScope)

        // Monitor voice activity
        liveApiManager.voiceActivity
            .onEach { isActive ->
                updateVoiceActivityIndicator(isActive)
            }
            .launchIn(lifecycleScope)

        // Handle errors
        liveApiManager.sessionErrors
            .onEach { error ->
                Timber.e("Live API Error: ${error.message}")
                showErrorMessage("Coach AI error: ${error.message}")
            }
            .launchIn(lifecycleScope)

        // Monitor system status
        liveApiManager.systemStatus
            .onEach { status ->
                updateSystemStatusUI(status)
            }
            .launchIn(lifecycleScope)
    }

    private fun configureAudioSettings() {
        liveApiManager.configureAudio(
            enableVAD = true,
            vadThreshold = 0.02f,
            enableAdaptiveQuality = true,
            enableNoiseSuppression = true,
            enableGainControl = true,
            enableEchoCancellation = true
        )
    }

    private fun handleSessionStateChange(state: SessionState) {
        when (state) {
            SessionState.CONNECTING -> {
                showStatusMessage("Connecting to AI coach...")
            }
            SessionState.CONNECTED -> {
                showStatusMessage("Connected! Setting up session...")
            }
            SessionState.SETUP_COMPLETE -> {
                showStatusMessage("AI coach ready!")
                isSessionActive = true
            }
            SessionState.ACTIVE -> {
                showStatusMessage("Coaching session active")
                isSessionActive = true
            }
            SessionState.DISCONNECTING -> {
                showStatusMessage("Disconnecting...")
                isSessionActive = false
            }
            SessionState.DISCONNECTED -> {
                showStatusMessage("Disconnected")
                isSessionActive = false
            }
            SessionState.ERROR -> {
                showErrorMessage("Connection error - attempting recovery")
                isSessionActive = false
            }
            else -> {
                Timber.d("Session state: $state")
            }
        }
    }

    private fun handlePoseAnalysisResult(result: com.posecoach.gemini.live.session.PoseAnalysisResult) {
        if (result.needsCorrection) {
            // Show visual feedback for pose correction
            showPoseCorrectionFeedback(result)
        }

        // Update confidence indicator
        updatePoseConfidenceIndicator(result.confidence)
    }

    // Example: Start coaching session
    fun startCoachingSession() {
        lifecycleScope.launch {
            try {
                val result = liveApiManager.startPoseCoachingSession()
                if (result.isSuccess) {
                    Timber.i("Coaching session started: ${result.getOrNull()}")
                    sendWelcomeMessage()
                } else {
                    val error = result.exceptionOrNull()
                    Timber.e("Failed to start session: ${error?.message}")
                    showErrorMessage("Failed to start coaching: ${error?.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error starting coaching session")
                showErrorMessage("Error starting session: ${e.message}")
            }
        }
    }

    // Example: Stop coaching session
    fun stopCoachingSession() {
        lifecycleScope.launch {
            try {
                liveApiManager.stopPoseCoachingSession()
                showStatusMessage("Coaching session ended")
            } catch (e: Exception) {
                Timber.e(e, "Error stopping session")
            }
        }
    }

    // Example: Send message to AI coach
    fun sendMessageToCoach(message: String) {
        if (!isSessionActive) {
            showErrorMessage("No active coaching session")
            return
        }

        lifecycleScope.launch {
            try {
                val result = liveApiManager.sendMessageToCoach(message)
                if (result.isFailure) {
                    showErrorMessage("Failed to send message: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error sending message")
                showErrorMessage("Error sending message: ${e.message}")
            }
        }
    }

    // Example: Process pose landmarks from MediaPipe
    fun processPoseFromCamera(landmarks: List<PoseLandmark>) {
        if (!isSessionActive) return

        lifecycleScope.launch {
            try {
                liveApiManager.processPoseLandmarks(landmarks)
            } catch (e: Exception) {
                Timber.e(e, "Error processing pose landmarks")
            }
        }
    }

    // Example: Handle emergency situations
    fun handleEmergency(type: com.posecoach.gemini.live.EmergencyType) {
        lifecycleScope.launch {
            try {
                liveApiManager.handleEmergency(type)
                showStatusMessage("Emergency handled: $type")
            } catch (e: Exception) {
                Timber.e(e, "Error handling emergency")
            }
        }
    }

    // Example: Get system statistics
    fun getSystemStats() {
        val stats = liveApiManager.getSystemStatistics()
        Timber.d("System Stats: $stats")

        // Could display in debug UI
        showSystemStats(stats)
    }

    private fun sendWelcomeMessage() {
        sendMessageToCoach(
            "Hello! I'm ready to start my workout. Please provide guidance and corrections as needed."
        )
    }

    // UI Helper Methods (implement based on your UI framework)
    private fun showStatusMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        Timber.i("Status: $message")
    }

    private fun showErrorMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        Timber.e("Error: $message")
    }

    private fun showCoachingFeedback(feedback: String) {
        runOnUiThread {
            // Update UI with coaching feedback
            // Could show in a dedicated feedback area
            Timber.i("Coaching: $feedback")
        }
    }

    private fun showPoseCorrectionFeedback(result: com.posecoach.gemini.live.session.PoseAnalysisResult) {
        runOnUiThread {
            // Show visual pose correction indicators
            // Highlight body parts that need adjustment
            Timber.i("Pose correction needed: ${result.feedback}")
        }
    }

    private fun updateVoiceActivityIndicator(isActive: Boolean) {
        runOnUiThread {
            // Update voice activity indicator in UI
            // Could change color or show animation
        }
    }

    private fun updatePoseConfidenceIndicator(confidence: Float) {
        runOnUiThread {
            // Update pose confidence indicator
            // Could use progress bar or color coding
        }
    }

    private fun updateSystemStatusUI(status: com.posecoach.gemini.live.SystemStatus) {
        runOnUiThread {
            // Update system health indicators
            // Show warnings if any
            if (status.warnings.isNotEmpty()) {
                Timber.w("System warnings: ${status.warnings}")
            }
        }
    }

    private fun showSystemStats(stats: com.posecoach.gemini.live.SystemStatistics) {
        runOnUiThread {
            // Display system statistics in debug UI
            Timber.d("Audio Quality: ${stats.audioQuality}")
            Timber.d("Session Duration: ${stats.sessionDuration}ms")
            Timber.d("Audio Data Sent: ${stats.audioDataSent} bytes")
            Timber.d("Error Counts: ${stats.errorCounts}")
        }
    }

    // Example button click handlers (connect to your UI)
    fun onStartButtonClicked() {
        startCoachingSession()
    }

    fun onStopButtonClicked() {
        stopCoachingSession()
    }

    fun onEmergencyButtonClicked() {
        handleEmergency(com.posecoach.gemini.live.EmergencyType.POOR_CONNECTION)
    }

    fun onStatsButtonClicked() {
        getSystemStats()
    }

    // Example voice commands
    fun onVoiceCommand(command: String) {
        when (command.lowercase()) {
            "start workout" -> startCoachingSession()
            "stop workout" -> stopCoachingSession()
            "how am i doing" -> sendMessageToCoach("How is my form? Can you give me feedback on my current pose?")
            "what's next" -> sendMessageToCoach("What exercise should I do next?")
            "slower pace" -> sendMessageToCoach("Can you guide me at a slower pace?")
            "faster pace" -> sendMessageToCoach("I'm ready for a faster pace")
            else -> sendMessageToCoach(command)
        }
    }
}