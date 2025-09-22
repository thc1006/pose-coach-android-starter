package com.posecoach.app.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.posecoach.app.overlay.CoordinateMapper
import com.posecoach.app.overlay.FitMode
import com.posecoach.app.performance.PerformanceDegradationStrategy
import com.posecoach.app.privacy.EnhancedPrivacyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Comprehensive CameraX Manager with rotation and transformation support
 * Handles camera lifecycle, preview, image analysis, and coordinate transformations
 */
class CameraXManager(
    private val context: Context,
    private val performanceStrategy: PerformanceDegradationStrategy,
    private val privacyManager: EnhancedPrivacyManager
) {

    data class CameraState(
        val isInitialized: Boolean = false,
        val isCameraRunning: Boolean = false,
        val currentResolution: Size? = null,
        val rotation: Int = 0,
        val isFrontFacing: Boolean = true,
        val fitMode: FitMode = FitMode.CENTER_CROP,
        val previewSize: Size? = null,
        val imageAnalysisSize: Size? = null
    )

    data class TransformInfo(
        val matrix: Matrix,
        val rotation: Int,
        val isFrontFacing: Boolean,
        val cropRect: android.graphics.Rect?,
        val sourceSize: Size,
        val targetSize: Size
    )

    companion object {
        private const val TAG = "CameraXManager"

        // Preferred resolutions for different performance levels
        private val HIGH_QUALITY_RESOLUTION = Size(1280, 720)
        private val BALANCED_RESOLUTION = Size(640, 480)
        private val PERFORMANCE_RESOLUTION = Size(480, 360)
        private val LOW_POWER_RESOLUTION = Size(320, 240)

        // Analysis resolution ratios
        private const val ANALYSIS_DOWNSCALE_FACTOR = 0.5f
    }

    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _transformInfo = MutableStateFlow<TransformInfo?>(null)
    val transformInfo: StateFlow<TransformInfo?> = _transformInfo.asStateFlow()

    private val _coordinateMapper = MutableStateFlow<CoordinateMapper?>(null)
    val coordinateMapper: StateFlow<CoordinateMapper?> = _coordinateMapper.asStateFlow()

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var previewView: PreviewView? = null
    private var imageAnalysisCallback: ((ImageProxy) -> Unit)? = null

    // Camera characteristics cache
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var frontCameraId: String? = null
    private var backCameraId: String? = null

    init {
        detectAvailableCameras()
    }

    /**
     * Initialize CameraX with PreviewView
     */
    suspend fun initialize(previewView: PreviewView): Result<Unit> {
        return try {
            this.previewView = previewView

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProvider = cameraProviderFuture.get()

            _cameraState.value = _cameraState.value.copy(isInitialized = true)
            Timber.i("CameraX initialized successfully")

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize CameraX")
            Result.failure(e)
        }
    }

    /**
     * Start camera with specified configuration
     */
    suspend fun startCamera(
        lifecycleOwner: LifecycleOwner,
        preferFrontCamera: Boolean = true,
        fitMode: FitMode = FitMode.CENTER_CROP,
        imageAnalysisCallback: ((ImageProxy) -> Unit)? = null
    ): Result<Unit> {
        return try {
            val provider = cameraProvider ?: throw IllegalStateException("CameraX not initialized")
            val preview = previewView ?: throw IllegalStateException("PreviewView not set")

            this.imageAnalysisCallback = imageAnalysisCallback

            // Stop any existing camera
            provider.unbindAll()

            // Select camera
            val cameraSelector = if (preferFrontCamera && frontCameraId != null) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val isFrontFacing = preferFrontCamera && frontCameraId != null

            // Get optimal resolutions based on performance level
            val performanceLevel = performanceStrategy.getCurrentPerformanceLevel()
            val previewResolution = getOptimalPreviewResolution(performanceLevel.targetResolution)
            val analysisResolution = getOptimalAnalysisResolution(performanceLevel.targetResolution)

            // Setup Preview
            val previewResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()

            this.preview = Preview.Builder()
                .setResolutionSelector(previewResolutionSelector)
                .build()
                .also {
                    it.setSurfaceProvider(preview.surfaceProvider)
                }

            // Setup ImageAnalysis
            val analysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()

            imageAnalyzer = ImageAnalysis.Builder()
                .setResolutionSelector(analysisResolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { analyzer ->
                    analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageAnalysis(imageProxy)
                    }
                }

            // Bind to lifecycle
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                this.preview,
                imageAnalyzer
            )

            // Update state
            val rotation = preview.display?.rotation ?: Surface.ROTATION_0
            _cameraState.value = _cameraState.value.copy(
                isCameraRunning = true,
                rotation = rotation,
                isFrontFacing = isFrontFacing,
                fitMode = fitMode,
                previewSize = previewResolution,
                imageAnalysisSize = analysisResolution
            )

            // Setup coordinate mapper and transform info
            setupCoordinateMapping(preview, previewResolution, analysisResolution, isFrontFacing, rotation, fitMode)

            Timber.i("Camera started: preview=${previewResolution}, analysis=${analysisResolution}, front=${isFrontFacing}")

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start camera")
            Result.failure(e)
        }
    }

    /**
     * Stop camera and release resources
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
        camera = null
        preview = null
        imageAnalyzer = null

        _cameraState.value = _cameraState.value.copy(
            isCameraRunning = false,
            currentResolution = null
        )

        Timber.i("Camera stopped")
    }

    /**
     * Update camera rotation (e.g., device rotation change)
     */
    fun updateRotation(newRotation: Int) {
        val currentState = _cameraState.value
        if (currentState.rotation != newRotation) {
            _cameraState.value = currentState.copy(rotation = newRotation)

            // Update coordinate mapper
            updateCoordinateMapping(newRotation)

            Timber.d("Camera rotation updated: ${currentState.rotation} -> $newRotation")
        }
    }

    /**
     * Update fit mode and recalculate transformations
     */
    fun updateFitMode(newFitMode: FitMode) {
        val currentState = _cameraState.value
        if (currentState.fitMode != newFitMode) {
            _cameraState.value = currentState.copy(fitMode = newFitMode)

            // Update coordinate mapper
            _coordinateMapper.value?.updateAspectRatio(newFitMode)

            Timber.d("Fit mode updated: ${currentState.fitMode} -> $newFitMode")
        }
    }

    /**
     * Switch between front and back camera
     */
    suspend fun switchCamera(lifecycleOwner: LifecycleOwner): Result<Unit> {
        val currentState = _cameraState.value
        return startCamera(
            lifecycleOwner = lifecycleOwner,
            preferFrontCamera = !currentState.isFrontFacing,
            fitMode = currentState.fitMode,
            imageAnalysisCallback = imageAnalysisCallback
        )
    }

    /**
     * Get current transform matrix for coordinate conversion
     */
    fun getCurrentTransformMatrix(): Matrix? {
        return _transformInfo.value?.matrix
    }

    /**
     * Convert normalized coordinates to view coordinates
     */
    fun normalizedToViewCoordinates(normalizedX: Float, normalizedY: Float): Pair<Float, Float>? {
        return _coordinateMapper.value?.normalizedToPixel(normalizedX, normalizedY)
    }

    /**
     * Batch convert normalized coordinates to view coordinates
     */
    fun batchNormalizedToViewCoordinates(landmarks: List<Pair<Float, Float>>): List<Pair<Float, Float>>? {
        return _coordinateMapper.value?.batchNormalizedToPixel(landmarks)
    }

    /**
     * Check if a point is visible in current view
     */
    fun isPointVisible(normalizedX: Float, normalizedY: Float): Boolean {
        return _coordinateMapper.value?.isPointVisible(normalizedX, normalizedY) ?: false
    }

    /**
     * Release all resources
     */
    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
        Timber.i("CameraXManager released")
    }

    // Private helper methods

    private fun detectAvailableCameras() {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> frontCameraId = cameraId
                    CameraCharacteristics.LENS_FACING_BACK -> backCameraId = cameraId
                }
            }
            Timber.d("Available cameras: front=$frontCameraId, back=$backCameraId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to detect available cameras")
        }
    }

    private fun getOptimalPreviewResolution(targetResolution: Size): Size {
        // For preview, we want to maintain good quality while respecting performance constraints
        return when {
            targetResolution.width >= HIGH_QUALITY_RESOLUTION.width -> HIGH_QUALITY_RESOLUTION
            targetResolution.width >= BALANCED_RESOLUTION.width -> BALANCED_RESOLUTION
            targetResolution.width >= PERFORMANCE_RESOLUTION.width -> PERFORMANCE_RESOLUTION
            else -> LOW_POWER_RESOLUTION
        }
    }

    private fun getOptimalAnalysisResolution(targetResolution: Size): Size {
        // For analysis, we can use lower resolution to improve performance
        val scaledWidth = (targetResolution.width * ANALYSIS_DOWNSCALE_FACTOR).toInt()
        val scaledHeight = (targetResolution.height * ANALYSIS_DOWNSCALE_FACTOR).toInt()

        return Size(
            scaledWidth.coerceAtLeast(240),
            scaledHeight.coerceAtLeast(180)
        )
    }

    private fun setupCoordinateMapping(
        previewView: PreviewView,
        previewResolution: Size,
        analysisResolution: Size,
        isFrontFacing: Boolean,
        rotation: Int,
        fitMode: FitMode
    ) {
        val viewWidth = previewView.width
        val viewHeight = previewView.height

        if (viewWidth > 0 && viewHeight > 0) {
            // Create coordinate mapper for preview
            val mapper = CoordinateMapper(
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                imageWidth = previewResolution.width,
                imageHeight = previewResolution.height,
                isFrontFacing = isFrontFacing,
                rotation = rotation
            )
            mapper.updateAspectRatio(fitMode)
            _coordinateMapper.value = mapper

            // Create transform info
            val matrix = Matrix()
            createTransformMatrix(
                matrix,
                previewResolution,
                analysisResolution,
                rotation,
                isFrontFacing
            )

            _transformInfo.value = TransformInfo(
                matrix = matrix,
                rotation = rotation,
                isFrontFacing = isFrontFacing,
                cropRect = null, // Will be calculated based on fit mode
                sourceSize = analysisResolution,
                targetSize = previewResolution
            )

            Timber.d("Coordinate mapping setup: view=${viewWidth}x${viewHeight}, preview=${previewResolution}, analysis=${analysisResolution}")
        }
    }

    private fun updateCoordinateMapping(newRotation: Int) {
        val currentMapper = _coordinateMapper.value
        val currentTransform = _transformInfo.value

        if (currentMapper != null && currentTransform != null) {
            // Update transform matrix with new rotation
            val newMatrix = Matrix()
            createTransformMatrix(
                newMatrix,
                currentTransform.targetSize,
                currentTransform.sourceSize,
                newRotation,
                currentTransform.isFrontFacing
            )

            _transformInfo.value = currentTransform.copy(
                matrix = newMatrix,
                rotation = newRotation
            )

            Timber.d("Transform updated for rotation: $newRotation")
        }
    }

    private fun createTransformMatrix(
        matrix: Matrix,
        targetSize: Size,
        sourceSize: Size,
        rotation: Int,
        isFrontFacing: Boolean
    ) {
        matrix.reset()

        // Calculate scale factors
        val scaleX = targetSize.width.toFloat() / sourceSize.width.toFloat()
        val scaleY = targetSize.height.toFloat() / sourceSize.height.toFloat()

        // Apply scaling
        matrix.postScale(scaleX, scaleY)

        // Apply rotation around center
        if (rotation != 0) {
            matrix.postRotate(
                rotation.toFloat(),
                targetSize.width / 2f,
                targetSize.height / 2f
            )
        }

        // Apply mirroring for front camera
        if (isFrontFacing) {
            matrix.postScale(-1f, 1f, targetSize.width / 2f, targetSize.height / 2f)
        }
    }

    private fun processImageAnalysis(imageProxy: ImageProxy) {
        try {
            // Check if we should process this frame based on performance strategy
            if (!performanceStrategy.shouldProcessFrame()) {
                imageProxy.close()
                return
            }

            // Check privacy settings
            if (privacyManager.isOfflineModeEnabled() &&
                !privacyManager.isDataUploadAllowed(EnhancedPrivacyManager.DataType.CAMERA_IMAGES)) {
                // In offline mode, we can still process locally
                imageAnalysisCallback?.invoke(imageProxy)
            } else {
                imageAnalysisCallback?.invoke(imageProxy)
            }

        } catch (e: Exception) {
            Timber.e(e, "Error processing image analysis")
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Get camera characteristics for advanced features
     */
    fun getCameraCharacteristics(): CameraCharacteristics? {
        val cameraId = if (_cameraState.value.isFrontFacing) frontCameraId else backCameraId
        return cameraId?.let { cameraManager.getCameraCharacteristics(it) }
    }

    /**
     * Get supported resolutions for current camera
     */
    fun getSupportedResolutions(): List<Size> {
        val characteristics = getCameraCharacteristics()
        return characteristics?.let {
            val map = it.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            map?.getOutputSizes(ImageFormat.YUV_420_888)?.toList() ?: emptyList()
        } ?: emptyList()
    }

    /**
     * Calculate optimal crop rect for different fit modes
     */
    fun calculateCropRect(sourceSize: Size, targetSize: Size, fitMode: FitMode): android.graphics.Rect {
        val sourceAspect = sourceSize.width.toFloat() / sourceSize.height.toFloat()
        val targetAspect = targetSize.width.toFloat() / targetSize.height.toFloat()

        return when (fitMode) {
            FitMode.FILL -> {
                android.graphics.Rect(0, 0, sourceSize.width, sourceSize.height)
            }
            FitMode.CENTER_CROP -> {
                if (sourceAspect > targetAspect) {
                    // Source is wider, crop horizontally
                    val cropWidth = (sourceSize.height * targetAspect).toInt()
                    val offsetX = (sourceSize.width - cropWidth) / 2
                    android.graphics.Rect(offsetX, 0, offsetX + cropWidth, sourceSize.height)
                } else {
                    // Source is taller, crop vertically
                    val cropHeight = (sourceSize.width / targetAspect).toInt()
                    val offsetY = (sourceSize.height - cropHeight) / 2
                    android.graphics.Rect(0, offsetY, sourceSize.width, offsetY + cropHeight)
                }
            }
            FitMode.CENTER_INSIDE -> {
                // No cropping, just fit inside
                android.graphics.Rect(0, 0, sourceSize.width, sourceSize.height)
            }
        }
    }
}