package com.posecoach.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posecoach.ui.activities.ConnectionStatus
import com.posecoach.ui.components.*
import kotlinx.coroutines.launch

/**
 * ViewModel for LiveCoachingActivity
 * Manages UI state and coordinates between services and UI components
 */
class LiveCoachingViewModel : ViewModel() {

    // Connection status
    private val _connectionStatus = MutableLiveData<ConnectionStatus>()
    val connectionStatus: LiveData<ConnectionStatus> = _connectionStatus

    // Session state
    private val _sessionState = MutableLiveData<SessionState>()
    val sessionState: LiveData<SessionState> = _sessionState

    // Transcription
    private val _transcription = MutableLiveData<String>()
    val transcription: LiveData<String> = _transcription

    // Voice activity
    private val _voiceActivity = MutableLiveData<VoiceActivity>()
    val voiceActivity: LiveData<VoiceActivity> = _voiceActivity

    // Pose suggestions
    private val _poseSuggestions = MutableLiveData<List<CoachingSuggestion>>()
    val poseSuggestions: LiveData<List<CoachingSuggestion>> = _poseSuggestions

    // Tool execution
    private val _toolExecution = MutableLiveData<ToolExecution>()
    val toolExecution: LiveData<ToolExecution> = _toolExecution

    // Error messages
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Talking state
    private val _isTalking = MutableLiveData<Boolean>()
    val isTalking: LiveData<Boolean> = _isTalking

    // Audio device state
    private val _audioDevice = MutableLiveData<AudioDevice>()
    val audioDevice: LiveData<AudioDevice> = _audioDevice

    // VAD sensitivity
    private val _vadSensitivity = MutableLiveData<Float>()
    val vadSensitivity: LiveData<Float> = _vadSensitivity

    // Session statistics
    private val _sessionStats = MutableLiveData<SessionStatistics>()
    val sessionStats: LiveData<SessionStatistics> = _sessionStats

    init {
        // Initialize with default values
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _sessionState.value = SessionState.INACTIVE
        _transcription.value = ""
        _isTalking.value = false
        _vadSensitivity.value = 0.5f
        _poseSuggestions.value = emptyList()
        _sessionStats.value = SessionStatistics()
    }

    // Connection status updates
    fun updateConnectionStatus(status: ConnectionStatus) {
        viewModelScope.launch {
            _connectionStatus.value = status

            // Clear error when successfully connected
            if (status == ConnectionStatus.CONNECTED) {
                _error.value = null
            }
        }
    }

    // Session state updates
    fun updateSessionState(state: SessionState) {
        viewModelScope.launch {
            _sessionState.value = state

            // Update statistics
            val currentStats = _sessionStats.value ?: SessionStatistics()
            when (state) {
                SessionState.ACTIVE -> {
                    _sessionStats.value = currentStats.copy(
                        sessionsStarted = currentStats.sessionsStarted + 1,
                        lastSessionStart = System.currentTimeMillis()
                    )
                }
                SessionState.EXPIRED -> {
                    _sessionStats.value = currentStats.copy(
                        sessionsCompleted = currentStats.sessionsCompleted + 1
                    )
                }
                else -> { /* No stats update needed */ }
            }
        }
    }

    // Transcription updates
    fun updateTranscription(text: String) {
        viewModelScope.launch {
            _transcription.value = text

            // Update word count in statistics
            val currentStats = _sessionStats.value ?: SessionStatistics()
            val wordCount = text.split("\\s+".toRegex()).size
            _sessionStats.value = currentStats.copy(totalWordsTranscribed = wordCount)
        }
    }

    // Voice activity updates
    fun updateVoiceActivity(activity: VoiceActivity) {
        viewModelScope.launch {
            _voiceActivity.value = activity

            // Update statistics
            if (activity.isActive) {
                val currentStats = _sessionStats.value ?: SessionStatistics()
                _sessionStats.value = currentStats.copy(
                    totalVoiceDetections = currentStats.totalVoiceDetections + 1
                )
            }
        }
    }

    // Pose suggestions updates
    fun updatePoseSuggestions(suggestions: List<CoachingSuggestion>) {
        viewModelScope.launch {
            _poseSuggestions.value = suggestions

            // Update statistics
            val currentStats = _sessionStats.value ?: SessionStatistics()
            _sessionStats.value = currentStats.copy(
                totalSuggestions = currentStats.totalSuggestions + suggestions.size
            )
        }
    }

    // Tool execution updates
    fun updateToolExecution(execution: ToolExecution) {
        viewModelScope.launch {
            _toolExecution.value = execution

            // Update statistics
            if (execution.status == ToolExecutionStatus.COMPLETED) {
                val currentStats = _sessionStats.value ?: SessionStatistics()
                _sessionStats.value = currentStats.copy(
                    toolExecutions = currentStats.toolExecutions + 1
                )
            }
        }
    }

    // Error handling
    fun setError(error: String) {
        viewModelScope.launch {
            _error.value = error
        }
    }

    fun clearError() {
        viewModelScope.launch {
            _error.value = null
        }
    }

    // Talking state
    fun startTalking() {
        viewModelScope.launch {
            _isTalking.value = true
        }
    }

    fun stopTalking() {
        viewModelScope.launch {
            _isTalking.value = false
        }
    }

    // Audio device management
    fun updateAudioDevice(device: AudioDevice) {
        viewModelScope.launch {
            _audioDevice.value = device
        }
    }

    // VAD sensitivity
    fun updateVADSensitivity(sensitivity: Float) {
        viewModelScope.launch {
            _vadSensitivity.value = sensitivity.coerceIn(0f, 1f)
        }
    }

    // Add a single pose suggestion
    fun addPoseSuggestion(suggestion: CoachingSuggestion) {
        viewModelScope.launch {
            val currentSuggestions = _poseSuggestions.value ?: emptyList()
            val updatedSuggestions = currentSuggestions + suggestion

            // Keep only the most recent 5 suggestions
            val limitedSuggestions = updatedSuggestions.takeLast(5)
            _poseSuggestions.value = limitedSuggestions
        }
    }

    // Clear all suggestions
    fun clearPoseSuggestions() {
        viewModelScope.launch {
            _poseSuggestions.value = emptyList()
        }
    }

    // Session statistics
    fun getSessionDuration(): Long {
        val stats = _sessionStats.value ?: return 0L
        val startTime = stats.lastSessionStart
        return if (startTime > 0) {
            System.currentTimeMillis() - startTime
        } else {
            0L
        }
    }

    fun resetSessionStatistics() {
        viewModelScope.launch {
            _sessionStats.value = SessionStatistics()
        }
    }

    // Utility methods
    fun isConnected(): Boolean {
        return _connectionStatus.value == ConnectionStatus.CONNECTED
    }

    fun isSessionActive(): Boolean {
        return _sessionState.value == SessionState.ACTIVE
    }

    fun canStartTalking(): Boolean {
        return isConnected() && isSessionActive() && !(_isTalking.value ?: false)
    }

    // Handle connection quality updates
    fun updateConnectionQuality(quality: Float) {
        viewModelScope.launch {
            val currentStats = _sessionStats.value ?: SessionStatistics()
            _sessionStats.value = currentStats.copy(
                averageConnectionQuality = (currentStats.averageConnectionQuality + quality) / 2f
            )
        }
    }

    // Handle network errors
    fun handleNetworkError(error: Throwable) {
        viewModelScope.launch {
            val errorMessage = when {
                error.message?.contains("timeout", ignoreCase = true) == true ->
                    "Connection timeout. Please check your internet connection."
                error.message?.contains("network", ignoreCase = true) == true ->
                    "Network error. Please check your connection and try again."
                error.message?.contains("auth", ignoreCase = true) == true ->
                    "Authentication failed. Please restart the session."
                else -> "Connection error: ${error.message}"
            }

            _error.value = errorMessage
            _connectionStatus.value = ConnectionStatus.ERROR
        }
    }
}

/**
 * Data class for session statistics
 */
data class SessionStatistics(
    val sessionsStarted: Int = 0,
    val sessionsCompleted: Int = 0,
    val totalVoiceDetections: Int = 0,
    val totalWordsTranscribed: Int = 0,
    val totalSuggestions: Int = 0,
    val toolExecutions: Int = 0,
    val averageConnectionQuality: Float = 0f,
    val lastSessionStart: Long = 0L
)

/**
 * Data class for audio device information
 */
data class AudioDevice(
    val id: String,
    val name: String,
    val type: AudioDeviceType,
    val isConnected: Boolean = false
)

/**
 * Audio device types
 */
enum class AudioDeviceType {
    BUILT_IN_MIC,
    BLUETOOTH_HEADSET,
    WIRED_HEADSET,
    USB_MIC,
    UNKNOWN
}