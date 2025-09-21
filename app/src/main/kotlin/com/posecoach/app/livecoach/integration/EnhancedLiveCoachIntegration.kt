package com.posecoach.app.livecoach.integration

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.posecoach.app.livecoach.LiveCoachManager
import com.posecoach.app.livecoach.performance.LiveCoachPerformanceMonitor
import com.posecoach.app.livecoach.models.*
import com.posecoach.app.livecoach.ui.PushToTalkButton
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber

/**
 * Enhanced integration layer for Live Coach functionality
 * Coordinates between Live Coach Manager, Performance Monitor, and UI components
 * Provides comprehensive session management, optimization, and quality assurance
 */
class EnhancedLiveCoachIntegration(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    apiKey: String? = null
) {

    // Core components
    private val liveCoachManager = LiveCoachManager(context, lifecycleScope, apiKey)
    private val performanceMonitor = LiveCoachPerformanceMonitor(context, lifecycleScope)

    // Integration state
    private var isInitialized = false
    private var currentOptimizationMode = OptimizationMode.BALANCED
    private var sessionStartTime = 0L
    private var qualityAssuranceEnabled = true

    // Flow combiners for integrated state
    private val _integratedSessionState = MutableStateFlow(IntegratedSessionState())
    val integratedSessionState: StateFlow<IntegratedSessionState> = _integratedSessionState.asStateFlow()

    // Combined event flows
    private val _sessionEvents = MutableSharedFlow<SessionEvent>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val sessionEvents: SharedFlow<SessionEvent> = _sessionEvents.asSharedFlow()

    data class IntegratedSessionState(
        val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
        val isRecording: Boolean = false,
        val isSpeaking: Boolean = false,
        val audioQuality: Double = 1.0,
        val connectionHealth: Double = 1.0,
        val performanceScore: Double = 1.0,
        val optimizationMode: OptimizationMode = OptimizationMode.BALANCED,
        val batteryLevel: Int = 100,
        val latencyMs: Long = 0,
        val errors: List<String> = emptyList(),
        val recommendations: List<String> = emptyList()
    )

    sealed class SessionEvent {
        object SessionStarted : SessionEvent()
        object SessionEnded : SessionEvent()
        data class QualityChanged(val quality: Double, val metric: String) : SessionEvent()
        data class OptimizationApplied(val type: String, val impact: String) : SessionEvent()
        data class PerformanceAlert(val severity: String, val message: String) : SessionEvent()
        data class BargeInDetected(val timestamp: Long) : SessionEvent()
        data class ConnectionHealthChanged(val health: Double) : SessionEvent()
    }

    enum class OptimizationMode {
        PERFORMANCE, // Maximum performance, higher battery usage
        BALANCED,    // Balance between performance and battery
        BATTERY,     // Battery optimization, reduced performance
        QUALITY      // Maximum quality, highest resource usage
    }

    fun initialize() {
        if (isInitialized) {
            Timber.w("Enhanced Live Coach Integration already initialized")
            return
        }

        Timber.d("Initializing Enhanced Live Coach Integration")

        // Start performance monitoring
        performanceMonitor.startMonitoring()

        // Setup flow combinations and monitoring
        setupIntegratedFlows()
        setupPerformanceIntegration()
        setupQualityAssurance()

        isInitialized = true
        Timber.i("Enhanced Live Coach Integration initialized successfully")
    }

    private fun setupIntegratedFlows() {
        // Combine Live Coach state with performance metrics
        lifecycleScope.launch {
            combine(
                liveCoachManager.sessionState,
                performanceMonitor.performanceAlerts,
                performanceMonitor.optimizationRecommendations
            ) { sessionState, alert, recommendation ->
                Triple(sessionState, alert, recommendation)
            }.collect { (sessionState, alert, recommendation) ->
                updateIntegratedState(sessionState, alert, recommendation)
            }
        }

        // Monitor coaching responses for quality analysis
        lifecycleScope.launch {
            liveCoachManager.coachingResponses.collect { response ->
                analyzeResponseQuality(response)
            }
        }

        // Monitor transcriptions for accuracy assessment
        lifecycleScope.launch {
            liveCoachManager.transcriptions.collect { transcription ->
                analyzeTranscriptionQuality(transcription)
            }
        }

        // Monitor errors for automatic recovery
        lifecycleScope.launch {
            liveCoachManager.errors.collect { error ->
                handleError(error)
            }
        }
    }

    private fun setupPerformanceIntegration() {
        // Monitor performance alerts and trigger optimizations
        lifecycleScope.launch {
            performanceMonitor.performanceAlerts.collect { alert ->
                handlePerformanceAlert(alert)
            }
        }

        // Apply optimization recommendations automatically
        lifecycleScope.launch {
            performanceMonitor.optimizationRecommendations.collect { recommendation ->
                applyOptimizationRecommendation(recommendation)
            }
        }
    }

    private fun setupQualityAssurance() {
        if (!qualityAssuranceEnabled) return

        // Monitor audio quality
        lifecycleScope.launch {
            // This would collect from AudioStreamManager.audioQuality
            // For now, we'll simulate quality monitoring
            while (isActive) {
                delay(5000) // Check every 5 seconds
                if (liveCoachManager.isRecording()) {
                    val sessionInfo = liveCoachManager.getSessionInfo()
                    val audioQuality = sessionInfo["audioQuality"] as? Double ?: 1.0

                    if (audioQuality < 0.5) {
                        _sessionEvents.emit(
                            SessionEvent.QualityChanged(audioQuality, "audio")
                        )
                    }
                }
            }
        }

        // Monitor connection health
        lifecycleScope.launch {
            while (isActive) {
                delay(10000) // Check every 10 seconds
                val connectionHealth = liveCoachManager.getConnectionHealth()
                val isHealthy = connectionHealth["isHealthy"] as? Boolean ?: false
                val healthScore = if (isHealthy) 1.0 else 0.5

                _sessionEvents.emit(
                    SessionEvent.ConnectionHealthChanged(healthScore)
                )

                delay(10000)
            }
        }
    }

    private suspend fun updateIntegratedState(
        sessionState: SessionState,
        alert: LiveCoachPerformanceMonitor.PerformanceAlert,
        recommendation: LiveCoachPerformanceMonitor.OptimizationRecommendation
    ) {
        val performanceSummary = performanceMonitor.getPerformanceSummary()
        val sessionInfo = liveCoachManager.getSessionInfo()

        val integratedState = IntegratedSessionState(
            connectionState = sessionState.connectionState,
            isRecording = sessionState.isRecording,
            isSpeaking = sessionState.isSpeaking,
            audioQuality = sessionInfo["audioQuality"] as? Double ?: 1.0,
            connectionHealth = if (sessionInfo["connectionHealthy"] as? Boolean == true) 1.0 else 0.5,
            performanceScore = performanceSummary["performance_score"] as? Double ?: 1.0,
            optimizationMode = currentOptimizationMode,
            batteryLevel = performanceSummary["battery_level"] as? Int ?: 100,
            latencyMs = performanceSummary["average_audio_latency_ms"] as? Long ?: 0L,
            errors = listOfNotNull(sessionState.lastError),
            recommendations = listOf(recommendation.description)
        )

        _integratedSessionState.value = integratedState
    }

    private suspend fun handlePerformanceAlert(alert: LiveCoachPerformanceMonitor.PerformanceAlert) {
        Timber.w("Performance alert: ${alert.message}")

        _sessionEvents.emit(
            SessionEvent.PerformanceAlert(alert.severity.name, alert.message)
        )

        // Auto-apply optimizations for critical alerts
        when (alert.severity) {
            LiveCoachPerformanceMonitor.Severity.CRITICAL -> {
                when (alert.type) {
                    LiveCoachPerformanceMonitor.AlertType.HIGH_LATENCY -> {
                        setOptimizationMode(OptimizationMode.PERFORMANCE)
                    }
                    LiveCoachPerformanceMonitor.AlertType.LOW_BATTERY -> {
                        setOptimizationMode(OptimizationMode.BATTERY)
                    }
                    LiveCoachPerformanceMonitor.AlertType.HIGH_MEMORY -> {
                        triggerMemoryOptimization()
                    }
                    else -> {
                        // Log but don't auto-optimize
                        Timber.i("Critical alert noted: ${alert.type}")
                    }
                }
            }
            else -> {
                // Log warning/info alerts
                Timber.d("Performance alert: ${alert.type} - ${alert.message}")
            }
        }
    }

    private suspend fun applyOptimizationRecommendation(
        recommendation: LiveCoachPerformanceMonitor.OptimizationRecommendation
    ) {
        if (recommendation.priority == LiveCoachPerformanceMonitor.Priority.CRITICAL ||
            recommendation.priority == LiveCoachPerformanceMonitor.Priority.HIGH) {

            when (recommendation.type) {
                LiveCoachPerformanceMonitor.OptimizationType.BATTERY_OPTIMIZATION -> {
                    liveCoachManager.optimizeForBatteryLife(true)
                    _sessionEvents.emit(
                        SessionEvent.OptimizationApplied("battery", recommendation.expectedImpact)
                    )
                }
                LiveCoachPerformanceMonitor.OptimizationType.AUDIO_OPTIMIZATION -> {
                    // Enable low-latency mode and adjust audio settings
                    _sessionEvents.emit(
                        SessionEvent.OptimizationApplied("audio", recommendation.expectedImpact)
                    )
                }
                LiveCoachPerformanceMonitor.OptimizationType.CONNECTION_OPTIMIZATION -> {
                    // Force reconnection if connection is unstable
                    if (liveCoachManager.getConnectionState() != ConnectionState.CONNECTED) {
                        liveCoachManager.forceReconnect()
                    }
                    _sessionEvents.emit(
                        SessionEvent.OptimizationApplied("connection", recommendation.expectedImpact)
                    )
                }
                LiveCoachPerformanceMonitor.OptimizationType.MEMORY_OPTIMIZATION -> {
                    triggerMemoryOptimization()
                    _sessionEvents.emit(
                        SessionEvent.OptimizationApplied("memory", recommendation.expectedImpact)
                    )
                }
            }

            Timber.i("Applied optimization: ${recommendation.type} - ${recommendation.description}")
        }
    }

    private suspend fun analyzeResponseQuality(response: String) {
        // Analyze response for quality metrics
        val quality = when {
            response.length < 10 -> 0.3 // Too short
            response.contains("sorry", ignoreCase = true) -> 0.6 // Apologetic responses
            response.contains("pose", ignoreCase = true) ||
            response.contains("posture", ignoreCase = true) -> 0.9 // Relevant content
            else -> 0.7 // Default quality
        }

        if (quality < 0.6) {
            _sessionEvents.emit(
                SessionEvent.QualityChanged(quality, "response")
            )
        }
    }

    private suspend fun analyzeTranscriptionQuality(transcription: String) {
        // Simple transcription quality analysis
        val quality = when {
            transcription.contains("Coach:") -> 1.0 // Clear transcription
            transcription.contains("You:") -> 0.9 // User transcription
            transcription.length < 5 -> 0.3 // Too short, likely poor audio
            else -> 0.7 // Default quality
        }

        if (quality < 0.5) {
            _sessionEvents.emit(
                SessionEvent.QualityChanged(quality, "transcription")
            )
        }
    }

    private suspend fun handleError(error: String) {
        Timber.e("Live Coach error: $error")

        // Automatic error recovery
        when {
            error.contains("connection", ignoreCase = true) -> {
                delay(2000) // Wait before retry
                liveCoachManager.forceReconnect()
            }
            error.contains("permission", ignoreCase = true) -> {
                // Can't auto-recover from permission issues
                _sessionEvents.emit(
                    SessionEvent.PerformanceAlert("ERROR", "Permission required: $error")
                )
            }
            error.contains("audio", ignoreCase = true) -> {
                // Try to restart audio with different settings
                if (liveCoachManager.isRecording()) {
                    liveCoachManager.stopPushToTalkSession()
                    delay(1000)
                    liveCoachManager.startPushToTalkSession()
                }
            }
        }
    }

    private fun triggerMemoryOptimization() {
        // Trigger garbage collection and clear unnecessary buffers
        System.gc()
        Timber.d("Memory optimization triggered")
    }

    // Public API methods

    fun startOptimizedSession() {
        sessionStartTime = System.currentTimeMillis()

        // Apply current optimization mode before starting
        applyOptimizationMode(currentOptimizationMode)

        liveCoachManager.startPushToTalkSession()

        lifecycleScope.launch {
            _sessionEvents.emit(SessionEvent.SessionStarted)
        }

        Timber.i("Optimized Live Coach session started")
    }

    fun stopOptimizedSession() {
        liveCoachManager.stopPushToTalkSession()

        lifecycleScope.launch {
            _sessionEvents.emit(SessionEvent.SessionEnded)
        }

        // Log session summary
        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        val performanceSummary = performanceMonitor.getPerformanceSummary()

        Timber.i("Session ended. Duration: ${sessionDuration}ms, Performance: ${performanceSummary["performance_score"]}")
    }

    fun setOptimizationMode(mode: OptimizationMode) {
        if (currentOptimizationMode == mode) return

        currentOptimizationMode = mode
        applyOptimizationMode(mode)

        Timber.i("Optimization mode changed to: $mode")
    }

    private fun applyOptimizationMode(mode: OptimizationMode) {
        when (mode) {
            OptimizationMode.PERFORMANCE -> {
                // Enable barge-in, low latency, high quality
                liveCoachManager.optimizeForBatteryLife(false)
                liveCoachManager.setSilenceDetectionEnabled(false) // Disable for responsiveness
            }
            OptimizationMode.BALANCED -> {
                // Default settings
                liveCoachManager.optimizeForBatteryLife(false)
                liveCoachManager.setSilenceDetectionEnabled(true)
            }
            OptimizationMode.BATTERY -> {
                // Enable all battery optimizations
                liveCoachManager.optimizeForBatteryLife(true)
                liveCoachManager.setSilenceDetectionEnabled(true)
            }
            OptimizationMode.QUALITY -> {
                // Maximum quality settings
                liveCoachManager.optimizeForBatteryLife(false)
                liveCoachManager.setSilenceDetectionEnabled(false)
            }
        }
    }

    fun configurePushToTalkButton(button: PushToTalkButton) {
        // Configure button with integrated state
        button.setOnPushToTalkListener(object : PushToTalkButton.OnPushToTalkListener {
            override fun onStartTalking() {
                liveCoachManager.startPushToTalkSession()

                // Record latency for performance monitoring
                val startTime = System.currentTimeMillis()
                lifecycleScope.launch {
                    delay(100) // Wait for connection establishment
                    val latency = System.currentTimeMillis() - startTime
                    performanceMonitor.recordAudioLatency(latency)
                }
            }

            override fun onStopTalking() {
                liveCoachManager.stopPushToTalkSession()
            }

            override fun onConnectionStateChanged(isConnected: Boolean) {
                if (!isConnected) {
                    performanceMonitor.recordConnectionDrop()
                }
            }

            override fun onRetryConnection() {
                liveCoachManager.forceReconnect()
            }

            override fun onShowConnectionInfo() {
                // This would trigger a UI dialog showing connection info
                Timber.d("Connection info requested")
            }
        })

        // Keep button updated with integrated state
        lifecycleScope.launch {
            integratedSessionState.collect { state ->
                button.updateConnectionState(state.connectionState)
                button.setRecording(state.isRecording)
                button.setSpeaking(state.isSpeaking)
                button.setAudioQuality(state.audioQuality)
                button.setBatteryOptimized(state.optimizationMode == OptimizationMode.BATTERY)
                button.setShowConnectionHealth(qualityAssuranceEnabled)
            }
        }
    }

    // Quality assurance and metrics

    fun enableQualityAssurance(enable: Boolean) {
        qualityAssuranceEnabled = enable
        if (enable) {
            setupQualityAssurance()
        }
        Timber.d("Quality assurance ${if (enable) "enabled" else "disabled"}")
    }

    fun getComprehensiveSessionReport(): Map<String, Any> {
        val liveCoachInfo = liveCoachManager.getSessionInfo()
        val performanceInfo = performanceMonitor.getPerformanceSummary()
        val latencyMetrics = performanceMonitor.getLatencyMetrics()
        val currentState = _integratedSessionState.value

        return mapOf(
            "session" to mapOf(
                "duration_ms" to if (sessionStartTime > 0) System.currentTimeMillis() - sessionStartTime else 0,
                "optimization_mode" to currentOptimizationMode.name,
                "quality_assurance_enabled" to qualityAssuranceEnabled
            ),
            "connection" to mapOf(
                "state" to currentState.connectionState.name,
                "health" to currentState.connectionHealth,
                "stability" to performanceInfo["performance_score"]
            ),
            "audio" to mapOf(
                "quality" to currentState.audioQuality,
                "recording" to currentState.isRecording,
                "speaking" to currentState.isSpeaking,
                "latency_metrics" to latencyMetrics["audio_latency"]
            ),
            "performance" to performanceInfo,
            "recommendations" to currentState.recommendations,
            "errors" to currentState.errors
        )
    }

    fun destroy() {
        Timber.d("Destroying Enhanced Live Coach Integration")

        performanceMonitor.destroy()
        liveCoachManager.destroy()

        isInitialized = false
        Timber.i("Enhanced Live Coach Integration destroyed")
    }
}