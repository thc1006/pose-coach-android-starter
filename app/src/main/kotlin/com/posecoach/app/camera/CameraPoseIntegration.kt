package com.posecoach.app.camera

import android.graphics.Matrix
import android.graphics.PointF
import android.util.Size
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.posecoach.app.overlay.CoordinateMapper
import com.posecoach.app.overlay.FitMode
import com.posecoach.app.overlay.PoseOverlayView
import com.posecoach.app.performance.PerformanceDegradationStrategy
import com.posecoach.app.privacy.EnhancedPrivacyManager
import com.posecoach.app.multipose.MultiPersonPoseManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

/**
 * Integration layer between CameraX pipeline and pose detection/overlay systems.
 * Provides seamless coordinate transformation and pose rendering capabilities.
 */
class CameraPoseIntegration(
    private val performanceStrategy: PerformanceDegradationStrategy,
    private val privacyManager: EnhancedPrivacyManager,
    private val multiPersonManager: MultiPersonPoseManager
) {

    data class PoseDetectionResult(
        val poses: List<PoseData>,
        val timestamp: Long,
        val imageSize: Size,
        val processingTimeMs: Double,
        val confidence: Float
    )

    data class PoseData(
        val personId: String,
        val landmarks: List<PointF>, // Normalized coordinates (0.0-1.0)
        val confidence: Float,
        val boundingBox: android.graphics.RectF?, // Normalized coordinates
        val visible: Boolean = true
    )

    interface PoseDetectionCallback {
        fun onPoseDetected(result: PoseDetectionResult)
        fun onPoseDetectionError(error: Throwable)
        fun onPerformanceUpdate(metrics: CameraPerformanceMonitor.FrameMetrics)
    }

    private val integrationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var cameraLifecycleManager: CameraLifecycleManager? = null
    private var performanceMonitor: CameraPerformanceMonitor? = null
    private var overlayView: PoseOverlayView? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _currentPoses = MutableStateFlow<List<PoseData>>(emptyList())
    val currentPoses: StateFlow<List<PoseData>> = _currentPoses.asStateFlow()

    private var poseDetectionCallback: PoseDetectionCallback? = null

    /**
     * Initialize the camera-pose integration system
     */
    fun initialize(
        context: android.content.Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        overlayView: PoseOverlayView,
        callback: PoseDetectionCallback? = null
    ) {
        this.overlayView = overlayView
        this.poseDetectionCallback = callback

        // Initialize performance monitor
        performanceMonitor = CameraPerformanceMonitor()

        // Initialize camera lifecycle manager
        cameraLifecycleManager = CameraLifecycleManager(
            context = context,
            performanceStrategy = performanceStrategy,
            privacyManager = privacyManager
        )

        // Setup camera with pose detection callback
        val cameraConfig = CameraLifecycleManager.CameraConfiguration(
            preferFrontCamera = true,
            fitMode = FitMode.CENTER_CROP,
            enableRotationHandling = true,
            enablePerformanceOptimization = true,
            autoStartOnResume = true
        )

        val cameraCallback = object : CameraLifecycleManager.CameraCallback {
            override fun onCameraReady(cameraManager: CameraXManager) {
                setupPoseDetectionPipeline(cameraManager)
                _isInitialized.value = true
                Timber.i("Camera-pose integration ready")
            }

            override fun onCameraError(error: Throwable) {
                Timber.e(error, "Camera error in pose integration")
                poseDetectionCallback?.onPoseDetectionError(error)
            }

            override fun onPermissionRequired(requiredPermissions: List<String>) {
                Timber.w("Camera permissions required: $requiredPermissions")
            }

            override fun onPermissionDenied(deniedPermissions: List<String>) {
                Timber.e("Camera permissions denied: $deniedPermissions")
            }

            override fun onRotationChanged(newRotation: Int) {
                handleRotationChange(newRotation)
            }
        }

        cameraLifecycleManager?.initialize(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            configuration = cameraConfig,
            callback = cameraCallback
        )

        Timber.i("Camera-pose integration initialized")
    }

    /**
     * Start pose detection with camera
     */
    fun startPoseDetection(activity: android.app.Activity) {
        cameraLifecycleManager?.let { manager ->
            if (manager.checkPermissions(activity)) {
                manager.startCamera()
            }
        }
    }

    /**
     * Stop pose detection
     */
    fun stopPoseDetection() {
        cameraLifecycleManager?.stopCamera()
        _currentPoses.value = emptyList()
        overlayView?.clearPoses()
    }

    /**
     * Update fit mode and refresh coordinate mappings
     */
    fun updateFitMode(fitMode: FitMode) {
        cameraLifecycleManager?.updateFitMode(fitMode)
        refreshOverlayCoordinates()
    }

    /**
     * Switch camera and update pose detection
     */
    fun switchCamera() {
        integrationScope.launch {
            cameraLifecycleManager?.switchCamera()
            // Clear current poses as camera orientation may change
            _currentPoses.value = emptyList()
            overlayView?.clearPoses()
        }
    }

    /**
     * Get current performance metrics
     */
    fun getPerformanceMetrics(): StateFlow<CameraPerformanceMonitor.PerformanceSnapshot>? {
        return performanceMonitor?.performanceSnapshot
    }

    /**
     * Get performance alerts
     */
    fun getPerformanceAlerts(): StateFlow<CameraPerformanceMonitor.PerformanceAlerts>? {
        return performanceMonitor?.performanceAlerts
    }

    /**
     * Run performance benchmark
     */
    fun runPerformanceBenchmark(): CameraPerformanceMonitor.PerformanceTestSuite? {
        return performanceMonitor?.runPerformanceTestSuite()
    }

    /**
     * Update pose detection settings
     */
    fun updatePoseSettings(
        @Suppress("UNUSED_PARAMETER") maxDetectedPoses: Int = 5,
        @Suppress("UNUSED_PARAMETER") confidenceThreshold: Float = 0.5f,
        @Suppress("UNUSED_PARAMETER") enableMultiPerson: Boolean = true
    ) {
        // TODO: Update settings when method is available
        // multiPersonManager.updateSettings(
        //     maxDetectedPoses = maxDetectedPoses,
        //     confidenceThreshold = confidenceThreshold,
        //     enableTracking = enableMultiPerson
        // )
    }

    /**
     * Handle permission results
     */
    fun onPermissionResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
        activity: android.app.Activity
    ) {
        cameraLifecycleManager?.onPermissionResult(requestCode, permissions, grantResults, activity)
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopPoseDetection()
        cameraLifecycleManager = null
        performanceMonitor = null
        overlayView = null
        _isInitialized.value = false
        Timber.i("Camera-pose integration cleaned up")
    }

    // Private helper methods

    private fun setupPoseDetectionPipeline(cameraManager: CameraXManager) {
        // Setup coordinate mapper observation
        cameraManager.coordinateMapper.onEach { mapper ->
            if (mapper != null) {
                updateOverlayCoordinateMapper(mapper)
            }
        }

        // Setup transform info observation
        cameraManager.transformInfo.onEach { transformInfo ->
            if (transformInfo != null) {
                updateOverlayTransform(transformInfo)
            }
        }

        // Setup camera state observation
        cameraManager.cameraState.onEach { state ->
            handleCameraStateChange(state)
        }
    }

    private fun handleImageAnalysis(@Suppress("UNUSED_PARAMETER") imageProxy: ImageProxy) {
        val startTime = System.nanoTime()

        try {
            // Check if we should process this frame
            if (!performanceStrategy.shouldProcessFrame()) {
                performanceMonitor?.recordDroppedFrame()
                return
            }

            // Get current camera state
            val cameraManager = cameraLifecycleManager?.getCameraManager()
            val state = cameraManager?.cameraState?.value ?: return

            // Perform pose detection
            val transformationStartTime = System.nanoTime()
            val poseResult = performPoseDetection(imageProxy, state)
            val transformationEndTime = System.nanoTime()

            // Update poses
            if (poseResult != null) {
                _currentPoses.value = poseResult.poses
                updateOverlayPoses(poseResult.poses, state)
                poseDetectionCallback?.onPoseDetected(poseResult)
            }

            // Record performance metrics
            performanceMonitor?.recordFrameMetrics(
                imageProxy = imageProxy,
                processingStartTime = startTime,
                transformationStartTime = transformationStartTime,
                transformationEndTime = transformationEndTime,
                rotation = state.rotation,
                fitMode = state.fitMode,
                transformationAccuracy = calculateTransformationAccuracy(poseResult)
            )

        } catch (e: Exception) {
            Timber.e(e, "Error in pose detection pipeline")
            poseDetectionCallback?.onPoseDetectionError(e)
        }
    }

    private fun performPoseDetection(
        imageProxy: ImageProxy,
        cameraState: CameraXManager.CameraState
    ): PoseDetectionResult? {
        // This would integrate with actual pose detection implementation
        // For now, return mock data for demonstration

        val imageSize = Size(imageProxy.width, imageProxy.height)
        val processingTimeMs = 15.0 // Mock processing time

        // Mock pose data (would come from actual pose detection)
        val mockPoses = if (cameraState.isCameraRunning) {
            listOf(
                PoseData(
                    personId = "person_1",
                    landmarks = generateMockLandmarks(),
                    confidence = 0.85f,
                    boundingBox = android.graphics.RectF(0.2f, 0.1f, 0.8f, 0.9f),
                    visible = true
                )
            )
        } else {
            emptyList()
        }

        return PoseDetectionResult(
            poses = mockPoses,
            timestamp = System.currentTimeMillis(),
            imageSize = imageSize,
            processingTimeMs = processingTimeMs,
            confidence = 0.85f
        )
    }

    private fun generateMockLandmarks(): List<PointF> {
        // Generate mock pose landmarks for demonstration
        return listOf(
            PointF(0.5f, 0.2f),  // Head
            PointF(0.4f, 0.4f),  // Left shoulder
            PointF(0.6f, 0.4f),  // Right shoulder
            PointF(0.3f, 0.6f),  // Left elbow
            PointF(0.7f, 0.6f),  // Right elbow
            PointF(0.2f, 0.8f),  // Left wrist
            PointF(0.8f, 0.8f),  // Right wrist
            PointF(0.45f, 0.7f), // Left hip
            PointF(0.55f, 0.7f), // Right hip
            PointF(0.4f, 0.85f), // Left knee
            PointF(0.6f, 0.85f), // Right knee
            PointF(0.35f, 1.0f), // Left ankle
            PointF(0.65f, 1.0f)  // Right ankle
        )
    }

    private fun updateOverlayCoordinateMapper(@Suppress("UNUSED_PARAMETER") mapper: CoordinateMapper) {
        overlayView?.setCoordinateMapper(mapper)
    }

    private fun updateOverlayTransform(@Suppress("UNUSED_PARAMETER") transformInfo: CameraXManager.TransformInfo) {
        overlayView?.setTransformMatrix(transformInfo.matrix)
    }

    private fun updateOverlayPoses(@Suppress("UNUSED_PARAMETER") poses: List<PoseData>, @Suppress("UNUSED_PARAMETER") cameraState: CameraXManager.CameraState) {
        // Update overlay with the most confident pose if available
        val bestPose = poses.maxByOrNull { it.confidence }
        if (bestPose != null) {
            // Convert PoseData to PoseLandmarkResult for overlay
            // TODO: Implement proper conversion when PoseLandmarkResult structure is finalized
            // overlayView?.updatePose(convertToPoseLandmarkResult(bestPose))
        }
    }

    private fun handleCameraStateChange(@Suppress("UNUSED_PARAMETER") state: CameraXManager.CameraState) {
        if (!state.isCameraRunning) {
            _currentPoses.value = emptyList()
            overlayView?.clearPoses()
        }
    }

    private fun handleRotationChange(@Suppress("UNUSED_PARAMETER") newRotation: Int) {
        // Clear poses during rotation to avoid display artifacts
        overlayView?.clearPoses()

        // Refresh coordinate mappings after rotation
        refreshOverlayCoordinates()

        Timber.d("Handled rotation change to: $newRotation degrees")
    }

    private fun refreshOverlayCoordinates() {
        val cameraManager = cameraLifecycleManager?.getCameraManager()
        val mapper = cameraManager?.coordinateMapper?.value
        val transformInfo = cameraManager?.transformInfo?.value

        if (mapper != null) {
            updateOverlayCoordinateMapper(mapper)
        }

        if (transformInfo != null) {
            updateOverlayTransform(transformInfo)
        }
    }

    private fun calculateTransformationAccuracy(poseResult: PoseDetectionResult?): Float {
        // Calculate transformation accuracy based on pose consistency
        // This is a simplified implementation
        return if (poseResult != null && poseResult.poses.isNotEmpty()) {
            val avgConfidence = poseResult.poses.map { it.confidence }.average()
            avgConfidence.toFloat()
        } else {
            1.0f
        }
    }

    /**
     * Extension function for PoseOverlayView to integrate with camera pipeline
     */
    private fun PoseOverlayView.setCoordinateMapper(@Suppress("UNUSED_PARAMETER") mapper: CoordinateMapper) {
        // This would be implemented in the actual PoseOverlayView
        // Setting the coordinate mapper for proper pose rendering
    }

    private fun PoseOverlayView.setTransformMatrix(@Suppress("UNUSED_PARAMETER") matrix: Matrix) {
        // This would be implemented in the actual PoseOverlayView
        // Setting the transformation matrix for pose rendering
    }

    // TODO: Implement when PoseOverlayView has updatePoses method
    // private fun PoseOverlayView.updatePoses(poses: List<Any>) {
    //     // This would be implemented in the actual PoseOverlayView
    //     // Updating the poses for rendering
    // }

    private fun PoseOverlayView.clearPoses() {
        // This would be implemented in the actual PoseOverlayView
        // Clearing all poses from the overlay
    }

    // Data class for overlay pose (would be defined in PoseOverlayView)
    private data class OverlayPose(
        val personId: String,
        val landmarks: List<PointF>,
        val confidence: Float,
        val boundingBox: android.graphics.RectF?,
        val visible: Boolean
    )
}