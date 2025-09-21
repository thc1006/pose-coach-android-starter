package com.posecoach.app.camera

import android.content.Context
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.posecoach.app.overlay.EnhancedCoordinateMapper
import com.posecoach.app.overlay.FitMode
import com.posecoach.app.overlay.PoseOverlayView
import com.posecoach.app.performance.PerformanceDegradationStrategy
import com.posecoach.app.privacy.EnhancedPrivacyManager
import com.posecoach.corepose.camera.CameraPoseAnalyzer
import com.posecoach.corepose.models.PoseDetectionError
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.repository.MediaPipePoseRepository
import com.posecoach.corepose.repository.PoseDetectionListener
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Complete camera integration manager that coordinates CameraX, MediaPipe pose detection,
 * and real-time overlay rendering for optimal 60fps performance.
 */
class CameraIntegrationManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : PoseDetectionListener {

    interface IntegrationCallback {
        fun onSystemReady()
        fun onSystemError(error: Throwable)
        fun onPoseDetected(pose: PoseLandmarkResult)
        fun onPerformanceUpdate(metrics: PerformanceMetrics)
    }

    // Core components
    private lateinit var cameraManager: CameraLifecycleManager
    private lateinit var poseRepository: MediaPipePoseRepository
    private lateinit var poseAnalyzer: CameraPoseAnalyzer
    private lateinit var performanceStrategy: PerformanceDegradationStrategy
    private lateinit var privacyManager: EnhancedPrivacyManager

    // UI components
    private var previewView: PreviewView? = null
    private var overlayView: PoseOverlayView? = null

    // Configuration
    private var configuration = IntegrationConfiguration()
    private var callback: IntegrationCallback? = null

    // State
    private var isInitialized = false
    private var isActive = false
    private var frameCount = 0L
    private var lastPerformanceUpdate = 0L

    data class IntegrationConfiguration(
        val preferFrontCamera: Boolean = true,
        val fitMode: FitMode = FitMode.CENTER_CROP,
        val enablePerformanceOptimization: Boolean = true,
        val enablePrivacyMode: Boolean = false,
        val targetFps: Int = 60,
        val maxRenderFps: Int = 60
    )

    data class PerformanceMetrics(
        val cameraFps: Int,
        val poseDetectionFps: Int,
        val overlayRenderFps: Int,
        val averageInferenceTime: Double,
        val frameDropRate: Float,
        val memoryUsage: Long
    )

    /**
     * Initialize the complete camera integration system
     */
    suspend fun initialize(
        previewView: PreviewView,
        overlayView: PoseOverlayView,
        configuration: IntegrationConfiguration = IntegrationConfiguration(),
        callback: IntegrationCallback? = null
    ): Result<Unit> {
        return try {
            this.previewView = previewView
            this.overlayView = overlayView
            this.configuration = configuration
            this.callback = callback

            // Initialize core components
            initializeComponents()

            // Setup camera system
            setupCameraSystem()

            // Setup pose detection
            setupPoseDetection()

            // Setup overlay system
            setupOverlaySystem()

            isInitialized = true
            Timber.i("Camera integration system initialized successfully")

            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize camera integration system")
            Result.failure(e)
        }
    }

    /**
     * Start the complete camera and pose detection system
     */
    suspend fun start(): Result<Unit> {
        if (!isInitialized) {
            return Result.failure(IllegalStateException("System not initialized"))
        }

        return try {
            // Start camera
            cameraManager.startCamera()

            // Wait for camera to be ready and start pose detection
            observeSystemState()

            isActive = true
            callback?.onSystemReady()

            Timber.i("Camera integration system started")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to start camera integration system")
            callback?.onSystemError(e)
            Result.failure(e)
        }
    }

    /**
     * Stop the complete system
     */
    fun stop() {
        isActive = false

        cameraManager.stopCamera()
        poseRepository.release()
        overlayView?.clear()

        Timber.i("Camera integration system stopped")
    }

    /**
     * Switch between front and back camera
     */
    suspend fun switchCamera(): Result<Unit> {
        return if (isActive) {
            cameraManager.switchCamera()
        } else {
            Result.failure(IllegalStateException("System not active"))
        }
    }

    /**
     * Update fit mode for camera preview
     */
    fun updateFitMode(fitMode: FitMode) {
        cameraManager.updateFitMode(fitMode)
        configuration = configuration.copy(fitMode = fitMode)
    }

    /**
     * Get current system performance metrics
     */
    fun getPerformanceMetrics(): PerformanceMetrics {
        val cameraManager = cameraManager.getCameraManager()
        val poseMetrics = poseRepository.getPerformanceMetrics()
        val overlayStats = overlayView?.getPerformanceStats()

        return PerformanceMetrics(
            cameraFps = calculateCameraFps(),
            poseDetectionFps = (1000.0 / poseMetrics.averageInferenceTime).toInt(),
            overlayRenderFps = overlayStats?.currentFps ?: 0,
            averageInferenceTime = poseMetrics.averageInferenceTime,
            frameDropRate = poseMetrics.frameSkipRate,
            memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        )
    }

    // Private implementation methods

    private fun initializeComponents() {
        // Initialize performance strategy
        performanceStrategy = PerformanceDegradationStrategy(context)

        // Initialize privacy manager
        privacyManager = EnhancedPrivacyManager(context)

        // Initialize pose repository
        poseRepository = MediaPipePoseRepository(context, this)

        // Initialize pose analyzer
        poseAnalyzer = CameraPoseAnalyzer(poseRepository, this)

        // Initialize camera lifecycle manager
        cameraManager = CameraLifecycleManager(
            context = context,
            performanceStrategy = performanceStrategy,
            privacyManager = privacyManager
        )

        Timber.d("Core components initialized")
    }

    private fun setupCameraSystem() {
        val cameraConfig = CameraLifecycleManager.CameraConfiguration(
            preferFrontCamera = configuration.preferFrontCamera,
            fitMode = configuration.fitMode,
            enableRotationHandling = true,
            enablePerformanceOptimization = configuration.enablePerformanceOptimization,
            autoStartOnResume = false
        )

        cameraManager.initialize(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView!!,
            configuration = cameraConfig,
            callback = object : CameraLifecycleManager.CameraCallback {
                override fun onCameraReady(cameraManager: CameraXManager) {
                    handleCameraReady(cameraManager)
                }

                override fun onCameraError(error: Throwable) {
                    callback?.onSystemError(error)
                }

                override fun onPermissionRequired(requiredPermissions: List<String>) {
                    // Handle in UI layer
                }

                override fun onPermissionDenied(deniedPermissions: List<String>) {
                    callback?.onSystemError(SecurityException("Camera permission denied"))
                }

                override fun onRotationChanged(newRotation: Int) {
                    handleRotationChange(newRotation)
                }
            }
        )

        Timber.d("Camera system configured")
    }

    private fun setupPoseDetection() {
        // Configure pose repository
        poseRepository.configure(
            minDetectionConfidence = 0.5f,
            minPresenceConfidence = 0.5f
        )

        // Configure pose analyzer
        poseAnalyzer.setProcessingQuality(
            when (configuration.targetFps) {
                60 -> CameraPoseAnalyzer.ProcessingQuality.HIGH
                30 -> CameraPoseAnalyzer.ProcessingQuality.MEDIUM
                else -> CameraPoseAnalyzer.ProcessingQuality.LOW
            }
        )

        Timber.d("Pose detection configured")
    }

    private fun setupOverlaySystem() {
        overlayView?.apply {
            setShowPerformance(true)
            setShowDebugInfo(false)
            setMaxRenderFps(configuration.maxRenderFps)
            setVisualQuality(1.2f, 1.0f, true)

            if (configuration.enablePrivacyMode) {
                setPrivacyManager(privacyManager)
            }
        }

        Timber.d("Overlay system configured")
    }

    private fun observeSystemState() {
        lifecycleOwner.lifecycleScope.launch {
            cameraManager.isActive.collect { isActive ->
                if (isActive) {
                    startPerformanceMonitoring()
                }
            }
        }

        lifecycleOwner.lifecycleScope.launch {
            cameraManager.currentRotation.collect { rotation ->
                handleRotationChange(rotation)
            }
        }
    }

    private fun handleCameraReady(cameraManager: CameraXManager) {
        lifecycleOwner.lifecycleScope.launch {
            cameraManager.coordinateMapper.collect { mapper ->
                mapper?.let {
                    overlayView?.setCoordinateMapper(it)
                    Timber.d("Coordinate mapper connected to overlay")
                }
            }
        }
    }

    private fun handleRotationChange(newRotation: Int) {
        // Update any rotation-dependent components
        Timber.d("Handling rotation change: $newRotation")
    }

    private fun startPerformanceMonitoring() {
        lifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(1000) // Update every second

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastPerformanceUpdate > 1000) {
                    val metrics = getPerformanceMetrics()
                    callback?.onPerformanceUpdate(metrics)

                    // Auto-optimize if performance is poor
                    if (configuration.enablePerformanceOptimization) {
                        optimizePerformanceIfNeeded(metrics)
                    }

                    lastPerformanceUpdate = currentTime
                }
            }
        }
    }

    private fun optimizePerformanceIfNeeded(metrics: PerformanceMetrics) {
        when {
            metrics.overlayRenderFps < 30 -> {
                overlayView?.setMaxRenderFps(30)
                Timber.i("Reduced overlay FPS to 30 for performance")
            }
            metrics.averageInferenceTime > 33 -> {
                poseAnalyzer.setProcessingQuality(CameraPoseAnalyzer.ProcessingQuality.MEDIUM)
                Timber.i("Reduced pose detection quality for performance")
            }
            metrics.frameDropRate > 0.1f -> {
                poseAnalyzer.setProcessingQuality(CameraPoseAnalyzer.ProcessingQuality.LOW)
                Timber.i("Further reduced processing quality due to frame drops")
            }
        }
    }

    private fun calculateCameraFps(): Int {
        // Calculate based on frame count and time
        return if (frameCount > 0) {
            val elapsedMs = System.currentTimeMillis() - lastPerformanceUpdate
            if (elapsedMs > 0) {
                ((frameCount * 1000) / elapsedMs).toInt()
            } else 0
        } else 0
    }

    // PoseDetectionListener implementation
    override fun onPoseDetected(result: PoseLandmarkResult) {
        frameCount++

        // Update overlay
        overlayView?.updatePose(result)

        // Notify callback
        callback?.onPoseDetected(result)
    }

    override fun onPoseDetectionError(error: PoseDetectionError) {
        Timber.w("Pose detection error: ${error.message}")

        // Only propagate serious errors to avoid spam
        if (!error.message.contains("No pose detected", ignoreCase = true)) {
            callback?.onSystemError(Exception(error.message, error.cause))
        }
    }

    /**
     * Release all resources
     */
    fun release() {
        stop()
        poseAnalyzer.cleanup()
        Timber.i("Camera integration system released")
    }
}