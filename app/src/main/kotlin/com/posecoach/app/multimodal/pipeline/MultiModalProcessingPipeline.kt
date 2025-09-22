package com.posecoach.app.multimodal.pipeline

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LifecycleCoroutineScope
import com.posecoach.app.multimodal.*
import com.posecoach.app.multimodal.enhanced.EnhancedGeminiMultiModalClient
import com.posecoach.app.multimodal.models.*
import com.posecoach.app.multimodal.processors.*
import com.posecoach.app.privacy.EnhancedPrivacyManager
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.*
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.mutableListOf

/**
 * Multi-Modal Processing Pipeline
 *
 * Orchestrates real-time parallel processing across multiple input streams:
 * - Manages concurrent processing of different modalities
 * - Handles backpressure and resource management
 * - Provides performance monitoring and optimization
 * - Coordinates data flow between processing stages
 * - Implements adaptive processing based on system load
 */
class MultiModalProcessingPipeline(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val privacyManager: EnhancedPrivacyManager,
    apiKey: String
) {

    companion object {
        private const val INPUT_BUFFER_SIZE = 50
        private const val PROCESSING_BUFFER_SIZE = 20
        private const val OUTPUT_BUFFER_SIZE = 30
        private const val MAX_CONCURRENT_PROCESSORS = 4
        private const val BACKPRESSURE_THRESHOLD = 0.8f
        private const val PERFORMANCE_SAMPLE_INTERVAL = 1000L // 1 second
        private const val ADAPTIVE_ADJUSTMENT_INTERVAL = 5000L // 5 seconds
    }

    // Core processing components
    private val fusionEngine = MultiModalFusionEngine(context, lifecycleScope, privacyManager)
    private val multiModalPrivacyManager = MultiModalPrivacyManager(context, privacyManager)
    private val geminiClient = EnhancedGeminiMultiModalClient(apiKey, privacyManager, multiModalPrivacyManager)

    // Processing channels for different stages
    private val inputChannel = Channel<RawMultiModalInput>(INPUT_BUFFER_SIZE)
    private val processingChannel = Channel<ProcessingJob>(PROCESSING_BUFFER_SIZE)
    private val outputChannel = Channel<ProcessedMultiModalResult>(OUTPUT_BUFFER_SIZE)

    // Performance monitoring
    private val performanceMetrics = PerformanceMetricsCollector()
    private val resourceMonitor = ResourceMonitor()
    private val adaptiveController = AdaptiveProcessingController()

    // Pipeline state
    private val _pipelineState = MutableStateFlow(PipelineState.IDLE)
    val pipelineState: StateFlow<PipelineState> = _pipelineState.asStateFlow()

    private val _throughputMetrics = MutableStateFlow(ThroughputMetrics())
    val throughputMetrics: StateFlow<ThroughputMetrics> = _throughputMetrics.asStateFlow()

    private val _processedResults = MutableSharedFlow<ProcessedMultiModalResult>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val processedResults: SharedFlow<ProcessedMultiModalResult> = _processedResults.asSharedFlow()

    // Processing jobs
    private val processingJobs = mutableListOf<Job>()
    private val processingId = AtomicLong(0)

    enum class PipelineState {
        IDLE, STARTING, RUNNING, THROTTLED, ERROR, SHUTDOWN
    }

    init {
        setupPipelineStages()
        startPerformanceMonitoring()
        Timber.d("MultiModalProcessingPipeline initialized")
    }

    /**
     * Start the multi-modal processing pipeline
     */
    suspend fun startPipeline() {
        if (_pipelineState.value == PipelineState.RUNNING) {
            Timber.w("Pipeline already running")
            return
        }

        try {
            _pipelineState.value = PipelineState.STARTING

            // Start all pipeline stages
            startInputProcessingStage()
            startModalityProcessingStage()
            startFusionProcessingStage()
            startOutputProcessingStage()

            _pipelineState.value = PipelineState.RUNNING
            Timber.i("Multi-modal processing pipeline started successfully")

        } catch (e: Exception) {
            _pipelineState.value = PipelineState.ERROR
            Timber.e(e, "Failed to start processing pipeline")
        }
    }

    /**
     * Stop the processing pipeline gracefully
     */
    suspend fun stopPipeline() {
        try {
            _pipelineState.value = PipelineState.SHUTDOWN

            // Cancel all processing jobs
            processingJobs.forEach { it.cancel() }
            processingJobs.clear()

            // Close channels
            inputChannel.close()
            processingChannel.close()
            outputChannel.close()

            // Shutdown components
            fusionEngine.shutdown()

            _pipelineState.value = PipelineState.IDLE
            Timber.i("Multi-modal processing pipeline stopped")

        } catch (e: Exception) {
            Timber.e(e, "Error stopping processing pipeline")
        }
    }

    /**
     * Submit pose landmarks for processing
     */
    suspend fun submitPoseLandmarks(
        landmarks: PoseLandmarkResult,
        associatedImage: Bitmap? = null
    ) {
        if (_pipelineState.value != PipelineState.RUNNING) {
            Timber.w("Pipeline not running, ignoring pose landmarks")
            return
        }

        try {
            val input = RawMultiModalInput(
                id = generateInputId(),
                timestamp = System.currentTimeMillis(),
                poseLandmarks = landmarks,
                associatedImage = associatedImage,
                inputType = InputType.POSE_LANDMARKS
            )

            inputChannel.trySend(input).also { result ->
                if (result.isFailure) {
                    Timber.w("Input channel full, dropping pose landmarks")
                    performanceMetrics.recordDroppedInput("pose")
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error submitting pose landmarks")
        }
    }

    /**
     * Submit audio data for processing
     */
    suspend fun submitAudioData(
        audioData: ByteArray,
        sampleRate: Int,
        channels: Int
    ) {
        if (_pipelineState.value != PipelineState.RUNNING) {
            Timber.w("Pipeline not running, ignoring audio data")
            return
        }

        try {
            val input = RawMultiModalInput(
                id = generateInputId(),
                timestamp = System.currentTimeMillis(),
                audioData = audioData,
                audioSampleRate = sampleRate,
                audioChannels = channels,
                inputType = InputType.AUDIO_DATA
            )

            inputChannel.trySend(input).also { result ->
                if (result.isFailure) {
                    Timber.w("Input channel full, dropping audio data")
                    performanceMetrics.recordDroppedInput("audio")
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error submitting audio data")
        }
    }

    /**
     * Submit visual scene for processing
     */
    suspend fun submitVisualScene(
        image: Bitmap,
        landmarks: PoseLandmarkResult? = null
    ) {
        if (_pipelineState.value != PipelineState.RUNNING) {
            Timber.w("Pipeline not running, ignoring visual scene")
            return
        }

        try {
            val input = RawMultiModalInput(
                id = generateInputId(),
                timestamp = System.currentTimeMillis(),
                sceneImage = image,
                poseLandmarks = landmarks,
                inputType = InputType.VISUAL_SCENE
            )

            inputChannel.trySend(input).also { result ->
                if (result.isFailure) {
                    Timber.w("Input channel full, dropping visual scene")
                    performanceMetrics.recordDroppedInput("vision")
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error submitting visual scene")
        }
    }

    // Pipeline stage implementations

    private fun setupPipelineStages() {
        Timber.d("Setting up multi-modal processing pipeline stages")
    }

    private fun startInputProcessingStage() {
        val job = lifecycleScope.launch {
            try {
                while (isActive) {
                    val input = inputChannel.receive()
                    val startTime = System.currentTimeMillis()

                    // Apply privacy filtering
                    val privacyFilteredInput = privacyFilterInput(input)

                    // Create processing job
                    val processingJob = ProcessingJob(
                        id = generateProcessingId(),
                        rawInput = input,
                        filteredInput = privacyFilteredInput,
                        priority = calculatePriority(input),
                        startTime = startTime
                    )

                    // Submit for processing
                    processingChannel.send(processingJob)
                    performanceMetrics.recordInputProcessed(input.inputType.name)
                }
            } catch (e: CancellationException) {
                Timber.d("Input processing stage cancelled")
            } catch (e: Exception) {
                Timber.e(e, "Error in input processing stage")
            }
        }
        processingJobs.add(job)
    }

    private fun startModalityProcessingStage() {
        // Start multiple parallel processors
        repeat(MAX_CONCURRENT_PROCESSORS) { processorId ->
            val job = lifecycleScope.launch {
                try {
                    while (isActive) {
                        val processingJob = processingChannel.receive()
                        val result = processModalityJob(processingJob, processorId)

                        outputChannel.send(result)
                        performanceMetrics.recordJobProcessed(processingJob.priority.name)
                    }
                } catch (e: CancellationException) {
                    Timber.d("Modality processing stage $processorId cancelled")
                } catch (e: Exception) {
                    Timber.e(e, "Error in modality processing stage $processorId")
                }
            }
            processingJobs.add(job)
        }
    }

    private fun startFusionProcessingStage() {
        val job = lifecycleScope.launch {
            try {
                // Collect and batch process results for fusion
                val batchCollector = mutableListOf<ProcessedMultiModalResult>()
                var lastFusionTime = System.currentTimeMillis()

                while (isActive) {
                    select<Unit> {
                        outputChannel.onReceive { result ->
                            batchCollector.add(result)

                            // Process batch when size or time threshold is reached
                            val currentTime = System.currentTimeMillis()
                            if (batchCollector.size >= 3 || currentTime - lastFusionTime > 500L) {
                                processFusionBatch(batchCollector.toList())
                                batchCollector.clear()
                                lastFusionTime = currentTime
                            }
                        }

                        onTimeout(200L) {
                            // Process accumulated batch on timeout
                            if (batchCollector.isNotEmpty()) {
                                processFusionBatch(batchCollector.toList())
                                batchCollector.clear()
                                lastFusionTime = System.currentTimeMillis()
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                Timber.d("Fusion processing stage cancelled")
            } catch (e: Exception) {
                Timber.e(e, "Error in fusion processing stage")
            }
        }
        processingJobs.add(job)
    }

    private fun startOutputProcessingStage() {
        val job = lifecycleScope.launch {
            try {
                _processedResults.collect { result ->
                    performanceMetrics.recordOutputGenerated(result.resultType.name)

                    // Apply adaptive throttling if needed
                    if (shouldThrottleOutput()) {
                        delay(50L) // Brief throttle
                    }
                }
            } catch (e: CancellationException) {
                Timber.d("Output processing stage cancelled")
            } catch (e: Exception) {
                Timber.e(e, "Error in output processing stage")
            }
        }
        processingJobs.add(job)
    }

    // Processing methods

    private suspend fun privacyFilterInput(
        input: RawMultiModalInput
    ): MultiModalPrivacyManager.PrivacyFilteredMultiModalInput {
        return try {
            // Convert raw input to multi-modal input format
            val multiModalInput = convertToMultiModalInput(input)

            // Apply privacy filtering
            multiModalPrivacyManager.filterMultiModalInput(multiModalInput)

        } catch (e: Exception) {
            Timber.e(e, "Error applying privacy filtering")
            createEmptyFilteredInput(input)
        }
    }

    private suspend fun processModalityJob(
        job: ProcessingJob,
        processorId: Int
    ): ProcessedMultiModalResult {
        val startTime = System.currentTimeMillis()

        return try {
            // Determine processing type based on input
            val result = when (job.rawInput.inputType) {
                InputType.POSE_LANDMARKS -> processPoseInput(job)
                InputType.AUDIO_DATA -> processAudioInput(job)
                InputType.VISUAL_SCENE -> processVisualInput(job)
                InputType.MULTI_MODAL -> processMultiModalInput(job)
            }

            val processingTime = System.currentTimeMillis() - startTime
            performanceMetrics.recordProcessingTime(job.rawInput.inputType.name, processingTime)

            result.copy(
                processingTimeMs = processingTime,
                processorId = processorId
            )

        } catch (e: Exception) {
            Timber.e(e, "Error processing modality job ${job.id}")
            ProcessedMultiModalResult(
                id = job.id,
                success = false,
                resultType = ResultType.ERROR,
                errorMessage = "Processing failed: ${e.message}",
                processingTimeMs = System.currentTimeMillis() - startTime,
                processorId = processorId
            )
        }
    }

    private suspend fun processPoseInput(job: ProcessingJob): ProcessedMultiModalResult {
        val landmarks = job.rawInput.poseLandmarks
            ?: return createErrorResult(job.id, "No pose landmarks provided")

        val associatedImage = job.rawInput.associatedImage

        // Process through fusion engine
        fusionEngine.processPoseLandmarks(landmarks, associatedImage)

        return ProcessedMultiModalResult(
            id = job.id,
            success = true,
            resultType = ResultType.POSE_ANALYSIS,
            data = mapOf("landmarks" to landmarks),
            confidence = landmarks.landmarks.map { it.visibility }.average().toFloat()
        )
    }

    private suspend fun processAudioInput(job: ProcessingJob): ProcessedMultiModalResult {
        val audioData = job.rawInput.audioData
            ?: return createErrorResult(job.id, "No audio data provided")

        val sampleRate = job.rawInput.audioSampleRate ?: 44100
        val channels = job.rawInput.audioChannels ?: 1

        // Process through fusion engine
        fusionEngine.processAudioSignal(audioData, sampleRate, channels)

        return ProcessedMultiModalResult(
            id = job.id,
            success = true,
            resultType = ResultType.AUDIO_ANALYSIS,
            data = mapOf(
                "sampleRate" to sampleRate,
                "channels" to channels,
                "dataSize" to audioData.size
            ),
            confidence = 0.8f
        )
    }

    private suspend fun processVisualInput(job: ProcessingJob): ProcessedMultiModalResult {
        val image = job.rawInput.sceneImage
            ?: return createErrorResult(job.id, "No scene image provided")

        val landmarks = job.rawInput.poseLandmarks

        // Process through fusion engine
        fusionEngine.processVisualScene(image, landmarks)

        return ProcessedMultiModalResult(
            id = job.id,
            success = true,
            resultType = ResultType.VISUAL_ANALYSIS,
            data = mapOf(
                "imageWidth" to image.width,
                "imageHeight" to image.height,
                "hasLandmarks" to (landmarks != null)
            ),
            confidence = 0.7f
        )
    }

    private suspend fun processMultiModalInput(job: ProcessingJob): ProcessedMultiModalResult {
        // Enhanced processing for multi-modal inputs
        val geminiResult = geminiClient.analyzeMultiModalInput(job.filteredInput)

        return ProcessedMultiModalResult(
            id = job.id,
            success = geminiResult.success,
            resultType = ResultType.MULTI_MODAL_ANALYSIS,
            data = mapOf("geminiResult" to geminiResult),
            confidence = geminiResult.confidence,
            errorMessage = geminiResult.errorMessage
        )
    }

    private suspend fun processFusionBatch(results: List<ProcessedMultiModalResult>) {
        try {
            if (results.isEmpty()) return

            Timber.d("Processing fusion batch with ${results.size} results")

            // Group results by modality and apply temporal fusion
            val fusedResult = createFusedResult(results)

            // Emit fused result
            _processedResults.emit(fusedResult)

            performanceMetrics.recordFusionBatch(results.size)

        } catch (e: Exception) {
            Timber.e(e, "Error processing fusion batch")
        }
    }

    // Performance monitoring

    private fun startPerformanceMonitoring() {
        lifecycleScope.launch {
            while (isActive) {
                delay(PERFORMANCE_SAMPLE_INTERVAL)
                updateThroughputMetrics()
                checkBackpressure()
            }
        }

        lifecycleScope.launch {
            while (isActive) {
                delay(ADAPTIVE_ADJUSTMENT_INTERVAL)
                adaptiveController.adjustProcessingParameters(
                    performanceMetrics.getMetrics(),
                    resourceMonitor.getResourceUsage()
                )
            }
        }
    }

    private fun updateThroughputMetrics() {
        val metrics = performanceMetrics.getMetrics()
        _throughputMetrics.value = ThroughputMetrics(
            inputsPerSecond = metrics.inputsPerSecond,
            outputsPerSecond = metrics.outputsPerSecond,
            averageLatencyMs = metrics.averageLatencyMs,
            backpressureLevel = metrics.backpressureLevel,
            resourceUsage = resourceMonitor.getResourceUsage()
        )
    }

    private fun checkBackpressure() {
        val inputBufferUsage = inputChannel.tryReceive().isSuccess
        val processingBufferUsage = processingChannel.tryReceive().isSuccess
        val outputBufferUsage = outputChannel.tryReceive().isSuccess

        val backpressureLevel = when {
            inputBufferUsage && processingBufferUsage && outputBufferUsage -> 0.9f
            inputBufferUsage && processingBufferUsage -> 0.7f
            inputBufferUsage || processingBufferUsage -> 0.5f
            else -> 0.2f
        }

        if (backpressureLevel > BACKPRESSURE_THRESHOLD) {
            _pipelineState.value = PipelineState.THROTTLED
            Timber.w("Pipeline experiencing backpressure: $backpressureLevel")
        } else if (_pipelineState.value == PipelineState.THROTTLED) {
            _pipelineState.value = PipelineState.RUNNING
            Timber.i("Pipeline backpressure resolved")
        }
    }

    // Utility methods

    private fun generateInputId(): String = "input_${System.nanoTime()}"
    private fun generateProcessingId(): String = "proc_${processingId.incrementAndGet()}"

    private fun calculatePriority(input: RawMultiModalInput): ProcessingPriority {
        return when (input.inputType) {
            InputType.POSE_LANDMARKS -> ProcessingPriority.HIGH
            InputType.VISUAL_SCENE -> ProcessingPriority.MEDIUM
            InputType.AUDIO_DATA -> ProcessingPriority.MEDIUM
            InputType.MULTI_MODAL -> ProcessingPriority.HIGH
        }
    }

    private fun shouldThrottleOutput(): Boolean {
        return _pipelineState.value == PipelineState.THROTTLED ||
               resourceMonitor.getCpuUsage() > 0.8f
    }

    private fun convertToMultiModalInput(
        input: RawMultiModalInput
    ): MultiModalInput {
        return MultiModalInput(
            timestamp = input.timestamp,
            inputId = input.id,
            poseLandmarks = input.poseLandmarks,
            visualContext = null, // Would be populated by vision analyzer
            audioSignal = null, // Would be populated by audio processor
            environmentContext = null, // Would be populated by environment analyzer
            userContext = null // Would be populated by user context manager
        )
    }

    private fun createEmptyFilteredInput(
        input: RawMultiModalInput
    ): MultiModalPrivacyManager.PrivacyFilteredMultiModalInput {
        return MultiModalPrivacyManager.PrivacyFilteredMultiModalInput(
            timestamp = input.timestamp,
            inputId = input.id,
            poseLandmarks = null,
            visualContext = null,
            audioSignal = null,
            environmentContext = null,
            userContext = null,
            privacyMetadata = MultiModalPrivacyManager.PrivacyMetadata(
                appliedFilters = listOf("privacy_block"),
                dataRetentionTime = 0L,
                encryptionApplied = false,
                anonymizationLevel = 1.0f
            )
        )
    }

    private fun createErrorResult(id: String, message: String): ProcessedMultiModalResult {
        return ProcessedMultiModalResult(
            id = id,
            success = false,
            resultType = ResultType.ERROR,
            errorMessage = message
        )
    }

    private fun createFusedResult(results: List<ProcessedMultiModalResult>): ProcessedMultiModalResult {
        val successfulResults = results.filter { it.success }
        val overallConfidence = if (successfulResults.isNotEmpty()) {
            successfulResults.map { it.confidence }.average().toFloat()
        } else 0f

        return ProcessedMultiModalResult(
            id = "fused_${System.nanoTime()}",
            success = successfulResults.isNotEmpty(),
            resultType = ResultType.FUSED_ANALYSIS,
            data = mapOf(
                "sourceResults" to successfulResults.size,
                "totalResults" to results.size,
                "fusionTime" to System.currentTimeMillis()
            ),
            confidence = overallConfidence
        )
    }

    // Supporting classes

    private inner class PerformanceMetricsCollector {
        private val inputCounts = ConcurrentHashMap<String, AtomicLong>()
        private val outputCounts = ConcurrentHashMap<String, AtomicLong>()
        private val droppedCounts = ConcurrentHashMap<String, AtomicLong>()
        private val processingTimes = ConcurrentHashMap<String, MutableList<Long>>()

        fun recordInputProcessed(type: String) {
            inputCounts.getOrPut(type) { AtomicLong(0) }.incrementAndGet()
        }

        fun recordOutputGenerated(type: String) {
            outputCounts.getOrPut(type) { AtomicLong(0) }.incrementAndGet()
        }

        fun recordDroppedInput(type: String) {
            droppedCounts.getOrPut(type) { AtomicLong(0) }.incrementAndGet()
        }

        fun recordProcessingTime(type: String, timeMs: Long) {
            processingTimes.getOrPut(type) { mutableListOf() }.add(timeMs)
        }

        fun recordJobProcessed(priority: String) {
            outputCounts.getOrPut("jobs_$priority") { AtomicLong(0) }.incrementAndGet()
        }

        fun recordFusionBatch(size: Int) {
            outputCounts.getOrPut("fusion_batches") { AtomicLong(0) }.incrementAndGet()
        }

        fun getMetrics(): PipelineMetrics {
            return PipelineMetrics(
                inputsPerSecond = inputCounts.values.sumOf { it.get() }.toFloat(),
                outputsPerSecond = outputCounts.values.sumOf { it.get() }.toFloat(),
                averageLatencyMs = processingTimes.values.flatten().average().toFloat(),
                backpressureLevel = 0.3f // Placeholder
            )
        }
    }

    private inner class ResourceMonitor {
        fun getResourceUsage(): ResourceUsage {
            return ResourceUsage(
                cpuUsage = getCpuUsage(),
                memoryUsage = getMemoryUsage(),
                batteryLevel = getBatteryLevel()
            )
        }

        fun getCpuUsage(): Float = 0.5f // Placeholder
        private fun getMemoryUsage(): Float = 0.4f // Placeholder
        private fun getBatteryLevel(): Float = 0.8f // Placeholder
    }

    private inner class AdaptiveProcessingController {
        fun adjustProcessingParameters(
            metrics: PipelineMetrics,
            resourceUsage: ResourceUsage
        ) {
            // Implement adaptive processing logic
            if (resourceUsage.cpuUsage > 0.8f) {
                // Reduce processing quality or frequency
                Timber.d("High CPU usage detected, adapting processing parameters")
            }
        }
    }

    // Data classes

    data class RawMultiModalInput(
        val id: String,
        val timestamp: Long,
        val inputType: InputType,
        val poseLandmarks: PoseLandmarkResult? = null,
        val associatedImage: Bitmap? = null,
        val audioData: ByteArray? = null,
        val audioSampleRate: Int? = null,
        val audioChannels: Int? = null,
        val sceneImage: Bitmap? = null
    )

    data class ProcessingJob(
        val id: String,
        val rawInput: RawMultiModalInput,
        val filteredInput: MultiModalPrivacyManager.PrivacyFilteredMultiModalInput,
        val priority: ProcessingPriority,
        val startTime: Long
    )

    data class ProcessedMultiModalResult(
        val id: String,
        val success: Boolean,
        val resultType: ResultType,
        val data: Map<String, Any>? = null,
        val confidence: Float = 0f,
        val processingTimeMs: Long = 0L,
        val processorId: Int = 0,
        val errorMessage: String? = null
    )

    data class ThroughputMetrics(
        val inputsPerSecond: Float = 0f,
        val outputsPerSecond: Float = 0f,
        val averageLatencyMs: Float = 0f,
        val backpressureLevel: Float = 0f,
        val resourceUsage: ResourceUsage = ResourceUsage()
    )

    data class PipelineMetrics(
        val inputsPerSecond: Float,
        val outputsPerSecond: Float,
        val averageLatencyMs: Float,
        val backpressureLevel: Float
    )

    data class ResourceUsage(
        val cpuUsage: Float = 0f,
        val memoryUsage: Float = 0f,
        val batteryLevel: Float = 1f
    )

    enum class InputType {
        POSE_LANDMARKS,
        AUDIO_DATA,
        VISUAL_SCENE,
        MULTI_MODAL
    }

    enum class ResultType {
        POSE_ANALYSIS,
        AUDIO_ANALYSIS,
        VISUAL_ANALYSIS,
        MULTI_MODAL_ANALYSIS,
        FUSED_ANALYSIS,
        ERROR
    }

    enum class ProcessingPriority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}