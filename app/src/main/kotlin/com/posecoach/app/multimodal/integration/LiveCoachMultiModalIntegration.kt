package com.posecoach.app.multimodal.integration

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.lifecycle.LifecycleCoroutineScope
import com.posecoach.app.livecoach.LiveCoachManager
import com.posecoach.app.multimodal.MultiModalFusionEngine
import com.posecoach.app.multimodal.enhanced.EnhancedGeminiMultiModalClient
import com.posecoach.app.multimodal.pipeline.MultiModalProcessingPipeline
import com.posecoach.app.multimodal.processors.*
import com.posecoach.app.privacy.EnhancedPrivacyManager
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber

/**
 * Live Coach Multi-Modal Integration
 *
 * Integrates the multi-modal AI system with the existing LiveCoachManager:
 * - Extends LiveCoachManager with multi-modal capabilities
 * - Coordinates data flow between pose detection and multi-modal analysis
 * - Enhances coaching intelligence with comprehensive understanding
 * - Maintains compatibility with existing pose detection systems
 * - Provides seamless integration with privacy framework
 */
class LiveCoachMultiModalIntegration(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val liveCoachManager: LiveCoachManager,
    private val privacyManager: EnhancedPrivacyManager,
    apiKey: String
) {

    companion object {
        private const val INTEGRATION_SYNC_INTERVAL = 100L // 100ms
        private const val ANALYSIS_DEBOUNCE_TIME = 500L // 500ms
        private const val MAX_PENDING_ANALYSES = 5
    }

    // Multi-modal components
    private val processingPipeline = MultiModalProcessingPipeline(
        context, lifecycleScope, privacyManager, apiKey
    )
    private val fusionEngine = MultiModalFusionEngine(context, lifecycleScope, privacyManager)
    private val multiModalPrivacyManager = MultiModalPrivacyManager(context, privacyManager)
    private val geminiClient = EnhancedGeminiMultiModalClient(apiKey, privacyManager, multiModalPrivacyManager)

    // Integration state
    private val _integrationState = MutableStateFlow(IntegrationState.IDLE)
    val integrationState: StateFlow<IntegrationState> = _integrationState.asStateFlow()

    private val _enhancedInsights = MutableSharedFlow<EnhancedCoachingInsight>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val enhancedInsights: SharedFlow<EnhancedCoachingInsight> = _enhancedInsights.asSharedFlow()

    private val _multiModalMetrics = MutableStateFlow(MultiModalMetrics())
    val multiModalMetrics: StateFlow<MultiModalMetrics> = _multiModalMetrics.asStateFlow()

    // Integration coordination
    private val pendingAnalyses = mutableMapOf<String, PendingAnalysis>()
    private var lastPoseUpdate = 0L
    private var integrationJob: Job? = null

    enum class IntegrationState {
        IDLE, INITIALIZING, ACTIVE, ENHANCED, ERROR, SHUTDOWN
    }

    init {
        setupIntegration()
        Timber.d("LiveCoachMultiModalIntegration initialized")
    }

    /**
     * Start the multi-modal integration
     */
    suspend fun startIntegration() {
        try {
            _integrationState.value = IntegrationState.INITIALIZING

            // Start the processing pipeline
            processingPipeline.startPipeline()

            // Setup data flow coordination
            setupDataFlowCoordination()

            // Start integration monitoring
            startIntegrationMonitoring()

            _integrationState.value = IntegrationState.ACTIVE
            Timber.i("Multi-modal integration started successfully")

        } catch (e: Exception) {
            _integrationState.value = IntegrationState.ERROR
            Timber.e(e, "Failed to start multi-modal integration")
        }
    }

    /**
     * Stop the multi-modal integration
     */
    suspend fun stopIntegration() {
        try {
            _integrationState.value = IntegrationState.SHUTDOWN

            // Stop pipeline
            processingPipeline.stopPipeline()

            // Cancel integration job
            integrationJob?.cancel()

            // Clear pending analyses
            pendingAnalyses.clear()

            _integrationState.value = IntegrationState.IDLE
            Timber.i("Multi-modal integration stopped")

        } catch (e: Exception) {
            Timber.e(e, "Error stopping multi-modal integration")
        }
    }

    /**
     * Process pose landmarks with multi-modal enhancement
     */
    suspend fun processPoseWithMultiModal(
        landmarks: PoseLandmarkResult,
        imageProxy: ImageProxy? = null
    ) {
        if (_integrationState.value != IntegrationState.ACTIVE) {
            return
        }

        try {
            val currentTime = System.currentTimeMillis()

            // Update LiveCoachManager with original pose data
            liveCoachManager.updatePoseLandmarks(landmarks)

            // Extract image if available and allowed by privacy
            val bitmap = imageProxy?.let { proxy ->
                if (privacyManager.isImageUploadAllowed()) {
                    convertImageProxyToBitmap(proxy)
                } else null
            }

            // Submit to multi-modal pipeline
            processingPipeline.submitPoseLandmarks(landmarks, bitmap)

            // Update metrics
            updatePoseMetrics(landmarks, currentTime)

            lastPoseUpdate = currentTime

        } catch (e: Exception) {
            Timber.e(e, "Error processing pose with multi-modal")
        }
    }

    /**
     * Process audio with multi-modal context
     */
    suspend fun processAudioWithContext(
        audioData: ByteArray,
        sampleRate: Int,
        channels: Int
    ) {
        if (_integrationState.value != IntegrationState.ACTIVE) {
            return
        }

        try {
            // Submit to multi-modal pipeline
            processingPipeline.submitAudioData(audioData, sampleRate, channels)

            // Update metrics
            updateAudioMetrics(audioData.size)

        } catch (e: Exception) {
            Timber.e(e, "Error processing audio with context")
        }
    }

    /**
     * Process visual scene with pose context
     */
    suspend fun processVisualSceneWithPose(
        image: Bitmap,
        landmarks: PoseLandmarkResult? = null
    ) {
        if (_integrationState.value != IntegrationState.ACTIVE) {
            return
        }

        try {
            // Submit to multi-modal pipeline
            processingPipeline.submitVisualScene(image, landmarks)

            // Update metrics
            updateVisualMetrics(image)

        } catch (e: Exception) {
            Timber.e(e, "Error processing visual scene with pose")
        }
    }

    /**
     * Get enhanced coaching recommendations
     */
    suspend fun getEnhancedRecommendations(
        context: CoachingContext
    ): List<EnhancedRecommendation> {
        return try {
            if (_integrationState.value != IntegrationState.ACTIVE) {
                return emptyList()
            }

            // Get current multi-modal insights
            val recentInsights = getRecentMultiModalInsights()

            // Generate enhanced recommendations using Gemini
            val analysisResult = recentInsights.firstOrNull()?.analysisResult
            if (analysisResult != null) {
                val geminiResult = geminiClient.generateContextualRecommendations(
                    analysisResult,
                    context.userContext,
                    context.environmentContext
                )

                if (geminiResult.success && geminiResult.recommendations != null) {
                    geminiResult.recommendations.recommendations.map { rec ->
                        EnhancedRecommendation(
                            title = rec.title,
                            description = rec.description,
                            implementationSteps = rec.implementationSteps,
                            priority = rec.priority,
                            category = rec.category,
                            confidence = geminiResult.confidence,
                            multiModalEvidence = listOf("multi_modal_analysis"),
                            estimatedTimeMinutes = rec.estimatedTimeMinutes
                        )
                    }
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }

        } catch (e: Exception) {
            Timber.e(e, "Error getting enhanced recommendations")
            emptyList()
        }
    }

    /**
     * Get integration status for monitoring
     */
    fun getIntegrationStatus(): IntegrationStatus {
        return IntegrationStatus(
            state = _integrationState.value,
            pipelineState = processingPipeline.pipelineState.value,
            liveCoachState = liveCoachManager.getConnectionState(),
            privacyLevel = privacyManager.currentPrivacyLevel.value,
            metrics = _multiModalMetrics.value,
            isEnhanced = _integrationState.value == IntegrationState.ENHANCED
        )
    }

    // Setup and coordination methods

    private fun setupIntegration() {
        // Monitor LiveCoachManager state
        lifecycleScope.launch {
            liveCoachManager.coachingResponses.collect { response ->
                enhanceCoachingResponse(response)
            }
        }

        // Monitor multi-modal processing results
        lifecycleScope.launch {
            processingPipeline.processedResults.collect { result ->
                processMultiModalResult(result)
            }
        }

        // Monitor fusion engine insights
        lifecycleScope.launch {
            fusionEngine.insights.collect { insight ->
                processFusionInsight(insight)
            }
        }
    }

    private fun setupDataFlowCoordination() {
        integrationJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    coordinateDataFlow()
                    delay(INTEGRATION_SYNC_INTERVAL)
                } catch (e: Exception) {
                    Timber.e(e, "Error in data flow coordination")
                }
            }
        }
    }

    private fun startIntegrationMonitoring() {
        lifecycleScope.launch {
            while (isActive) {
                try {
                    updateIntegrationMetrics()
                    checkEnhancementOpportunities()
                    delay(1000L) // 1 second monitoring interval
                } catch (e: Exception) {
                    Timber.e(e, "Error in integration monitoring")
                }
            }
        }
    }

    // Processing methods

    private suspend fun coordinateDataFlow() {
        // Check for pending analyses that can be enhanced
        val currentTime = System.currentTimeMillis()

        pendingAnalyses.values.removeAll { analysis ->
            currentTime - analysis.timestamp > 5000L // Remove old analyses
        }

        // Check if we have sufficient data for enhanced analysis
        if (shouldTriggerEnhancedAnalysis()) {
            triggerEnhancedAnalysis()
        }
    }

    private suspend fun enhanceCoachingResponse(response: String) {
        try {
            // Get recent multi-modal context
            val recentInsights = getRecentMultiModalInsights()

            if (recentInsights.isNotEmpty()) {
                val enhancedInsight = EnhancedCoachingInsight(
                    originalResponse = response,
                    multiModalContext = recentInsights.map { it.analysisResult }.filterNotNull(),
                    enhancementLevel = calculateEnhancementLevel(recentInsights),
                    confidence = recentInsights.map { it.confidence }.average().toFloat(),
                    timestamp = System.currentTimeMillis()
                )

                _enhancedInsights.emit(enhancedInsight)

                if (enhancedInsight.enhancementLevel > 0.7f) {
                    _integrationState.value = IntegrationState.ENHANCED
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error enhancing coaching response")
        }
    }

    private suspend fun processMultiModalResult(
        result: MultiModalProcessingPipeline.ProcessedMultiModalResult
    ) {
        try {
            if (!result.success) {
                Timber.w("Multi-modal processing failed: ${result.errorMessage}")
                return
            }

            // Store result for coordination
            val analysis = ProcessedAnalysis(
                id = result.id,
                resultType = result.resultType,
                analysisResult = extractAnalysisResult(result),
                confidence = result.confidence,
                timestamp = System.currentTimeMillis()
            )

            pendingAnalyses[result.id] = PendingAnalysis(
                analysis = analysis,
                timestamp = System.currentTimeMillis()
            )

            // Update metrics
            updateProcessingMetrics(result)

        } catch (e: Exception) {
            Timber.e(e, "Error processing multi-modal result")
        }
    }

    private suspend fun processFusionInsight(insight: MultiModalFusionEngine.FusedInsight) {
        try {
            // Extract actionable recommendations
            val recommendations = insight.recommendations.map { rec ->
                EnhancedRecommendation(
                    title = rec.title,
                    description = rec.description,
                    implementationSteps = rec.implementationSteps,
                    priority = rec.priority.name,
                    category = rec.category,
                    confidence = insight.confidence,
                    multiModalEvidence = insight.insights.map { it.modality },
                    estimatedTimeMinutes = null
                )
            }

            // Create enhanced insight
            val enhancedInsight = EnhancedCoachingInsight(
                originalResponse = "Multi-modal fusion analysis",
                multiModalContext = listOf(), // Would be populated with analysis results
                enhancementLevel = insight.confidence,
                confidence = insight.confidence,
                timestamp = insight.timestamp,
                recommendations = recommendations
            )

            _enhancedInsights.emit(enhancedInsight)

        } catch (e: Exception) {
            Timber.e(e, "Error processing fusion insight")
        }
    }

    // Analysis and enhancement methods

    private fun shouldTriggerEnhancedAnalysis(): Boolean {
        val currentTime = System.currentTimeMillis()

        // Check if we have recent data from multiple modalities
        val recentAnalyses = pendingAnalyses.values.filter {
            currentTime - it.timestamp < ANALYSIS_DEBOUNCE_TIME
        }

        val modalityTypes = recentAnalyses.map { it.analysis.resultType }.toSet()

        return modalityTypes.size >= 2 && // At least 2 different modalities
               recentAnalyses.size <= MAX_PENDING_ANALYSES &&
               currentTime - lastPoseUpdate < 1000L // Recent pose data
    }

    private suspend fun triggerEnhancedAnalysis() {
        try {
            // Collect recent analyses
            val recentAnalyses = pendingAnalyses.values.map { it.analysis }

            // Perform cross-modal validation and fusion
            val fusedAnalysis = performCrossModalFusion(recentAnalyses)

            // Generate enhanced coaching insights
            if (fusedAnalysis != null) {
                val enhancedInsight = generateEnhancedInsight(fusedAnalysis)
                _enhancedInsights.emit(enhancedInsight)
            }

            // Clear processed analyses
            pendingAnalyses.clear()

        } catch (e: Exception) {
            Timber.e(e, "Error triggering enhanced analysis")
        }
    }

    private suspend fun performCrossModalFusion(
        analyses: List<ProcessedAnalysis>
    ): FusedAnalysis? {
        return try {
            if (analyses.isEmpty()) return null

            val overallConfidence = analyses.map { it.confidence }.average().toFloat()
            val modalityTypes = analyses.map { it.resultType }.toSet()

            FusedAnalysis(
                confidence = overallConfidence,
                modalityTypes = modalityTypes,
                analysisResults = analyses,
                fusionTimestamp = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Timber.e(e, "Error performing cross-modal fusion")
            null
        }
    }

    private fun generateEnhancedInsight(fusedAnalysis: FusedAnalysis): EnhancedCoachingInsight {
        return EnhancedCoachingInsight(
            originalResponse = "Cross-modal analysis complete",
            multiModalContext = listOf(), // Would be populated with actual context
            enhancementLevel = fusedAnalysis.confidence,
            confidence = fusedAnalysis.confidence,
            timestamp = fusedAnalysis.fusionTimestamp,
            recommendations = generateFusedRecommendations(fusedAnalysis)
        )
    }

    private fun generateFusedRecommendations(fusedAnalysis: FusedAnalysis): List<EnhancedRecommendation> {
        // Generate recommendations based on fused analysis
        return listOf(
            EnhancedRecommendation(
                title = "Multi-Modal Insight",
                description = "Comprehensive analysis across ${fusedAnalysis.modalityTypes.size} modalities",
                implementationSteps = listOf("Review integrated feedback", "Apply recommended adjustments"),
                priority = "MEDIUM",
                category = "integration",
                confidence = fusedAnalysis.confidence,
                multiModalEvidence = fusedAnalysis.modalityTypes.map { it.name }
            )
        )
    }

    // Metrics and monitoring

    private fun updatePoseMetrics(landmarks: PoseLandmarkResult, timestamp: Long) {
        val currentMetrics = _multiModalMetrics.value
        _multiModalMetrics.value = currentMetrics.copy(
            poseUpdatesCount = currentMetrics.poseUpdatesCount + 1,
            lastPoseUpdateTime = timestamp,
            averagePoseConfidence = (currentMetrics.averagePoseConfidence + landmarks.confidence) / 2f
        )
    }

    private fun updateAudioMetrics(dataSize: Int) {
        val currentMetrics = _multiModalMetrics.value
        _multiModalMetrics.value = currentMetrics.copy(
            audioSamplesCount = currentMetrics.audioSamplesCount + 1,
            totalAudioDataSize = currentMetrics.totalAudioDataSize + dataSize
        )
    }

    private fun updateVisualMetrics(image: Bitmap) {
        val currentMetrics = _multiModalMetrics.value
        _multiModalMetrics.value = currentMetrics.copy(
            visualSamplesCount = currentMetrics.visualSamplesCount + 1,
            lastImageResolution = "${image.width}x${image.height}"
        )
    }

    private fun updateProcessingMetrics(result: MultiModalProcessingPipeline.ProcessedMultiModalResult) {
        val currentMetrics = _multiModalMetrics.value
        _multiModalMetrics.value = currentMetrics.copy(
            processedResultsCount = currentMetrics.processedResultsCount + 1,
            averageProcessingTime = (currentMetrics.averageProcessingTime + result.processingTimeMs) / 2f,
            averageConfidence = (currentMetrics.averageConfidence + result.confidence) / 2f
        )
    }

    private fun updateIntegrationMetrics() {
        val pipelineMetrics = processingPipeline.throughputMetrics.value
        val currentMetrics = _multiModalMetrics.value

        _multiModalMetrics.value = currentMetrics.copy(
            pipelineThroughput = pipelineMetrics.inputsPerSecond,
            systemLatency = pipelineMetrics.averageLatencyMs,
            resourceUsage = pipelineMetrics.resourceUsage.cpuUsage
        )
    }

    private fun checkEnhancementOpportunities() {
        val metrics = _multiModalMetrics.value

        // Check if we have sufficient multi-modal data for enhancement
        val hasMultiModalData = metrics.poseUpdatesCount > 0 &&
                               (metrics.audioSamplesCount > 0 || metrics.visualSamplesCount > 0)

        if (hasMultiModalData && _integrationState.value == IntegrationState.ACTIVE) {
            // Opportunity for enhancement detected
            _integrationState.value = IntegrationState.ENHANCED
        }
    }

    // Utility methods

    private fun getRecentMultiModalInsights(): List<ProcessedAnalysis> {
        val cutoffTime = System.currentTimeMillis() - 5000L // Last 5 seconds
        return pendingAnalyses.values
            .filter { it.timestamp > cutoffTime }
            .map { it.analysis }
    }

    private fun calculateEnhancementLevel(insights: List<ProcessedAnalysis>): Float {
        if (insights.isEmpty()) return 0f

        val modalityCount = insights.map { it.resultType }.toSet().size
        val avgConfidence = insights.map { it.confidence }.average().toFloat()

        return (modalityCount / 4f + avgConfidence) / 2f // Max 4 modalities
    }

    private fun convertImageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        // Simplified conversion - in production would use proper image conversion
        return null // Placeholder
    }

    private fun extractAnalysisResult(
        result: MultiModalProcessingPipeline.ProcessedMultiModalResult
    ): EnhancedGeminiMultiModalClient.MultiModalAnalysisResponse? {
        // Extract analysis result from processed result
        return result.data?.get("geminiResult") as? EnhancedGeminiMultiModalClient.GeminiMultiModalAnalysisResult
            ?.analysisResult
    }

    // Data classes

    data class CoachingContext(
        val userContext: UserContextData? = null,
        val environmentContext: EnvironmentContextData? = null,
        val sessionDuration: Long = 0L,
        val workoutType: String = "general"
    )

    data class EnhancedCoachingInsight(
        val originalResponse: String,
        val multiModalContext: List<EnhancedGeminiMultiModalClient.MultiModalAnalysisResponse>,
        val enhancementLevel: Float,
        val confidence: Float,
        val timestamp: Long,
        val recommendations: List<EnhancedRecommendation> = emptyList()
    )

    data class EnhancedRecommendation(
        val title: String,
        val description: String,
        val implementationSteps: List<String>,
        val priority: String,
        val category: String,
        val confidence: Float,
        val multiModalEvidence: List<String>,
        val estimatedTimeMinutes: Int? = null
    )

    data class IntegrationStatus(
        val state: IntegrationState,
        val pipelineState: MultiModalProcessingPipeline.PipelineState,
        val liveCoachState: Any, // LiveCoachManager connection state
        val privacyLevel: EnhancedPrivacyManager.PrivacyLevel,
        val metrics: MultiModalMetrics,
        val isEnhanced: Boolean
    )

    data class MultiModalMetrics(
        val poseUpdatesCount: Long = 0L,
        val audioSamplesCount: Long = 0L,
        val visualSamplesCount: Long = 0L,
        val processedResultsCount: Long = 0L,
        val lastPoseUpdateTime: Long = 0L,
        val averagePoseConfidence: Float = 0f,
        val totalAudioDataSize: Long = 0L,
        val lastImageResolution: String = "0x0",
        val averageProcessingTime: Float = 0f,
        val averageConfidence: Float = 0f,
        val pipelineThroughput: Float = 0f,
        val systemLatency: Float = 0f,
        val resourceUsage: Float = 0f
    )

    data class ProcessedAnalysis(
        val id: String,
        val resultType: MultiModalProcessingPipeline.ResultType,
        val analysisResult: EnhancedGeminiMultiModalClient.MultiModalAnalysisResponse?,
        val confidence: Float,
        val timestamp: Long
    )

    data class PendingAnalysis(
        val analysis: ProcessedAnalysis,
        val timestamp: Long
    )

    data class FusedAnalysis(
        val confidence: Float,
        val modalityTypes: Set<MultiModalProcessingPipeline.ResultType>,
        val analysisResults: List<ProcessedAnalysis>,
        val fusionTimestamp: Long
    )
}