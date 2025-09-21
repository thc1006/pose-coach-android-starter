package com.posecoach.app.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.OrientationEventListener
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import com.posecoach.app.overlay.FitMode
import com.posecoach.app.performance.PerformanceDegradationStrategy
import com.posecoach.app.privacy.EnhancedPrivacyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Comprehensive camera lifecycle manager with permissions, rotation handling, and performance management.
 * Provides a complete camera solution with proper Android lifecycle integration.
 */
class CameraLifecycleManager(
    private val context: Context,
    private val performanceStrategy: PerformanceDegradationStrategy,
    private val privacyManager: EnhancedPrivacyManager
) : DefaultLifecycleObserver {

    data class CameraConfiguration(
        val preferFrontCamera: Boolean = true,
        val fitMode: FitMode = FitMode.CENTER_CROP,
        val enableRotationHandling: Boolean = true,
        val enablePerformanceOptimization: Boolean = true,
        val autoStartOnResume: Boolean = true
    )

    data class PermissionState(
        val hasCameraPermission: Boolean,
        val shouldShowRationale: Boolean,
        val permissionDeniedPermanently: Boolean
    )

    interface CameraCallback {
        fun onCameraReady(cameraManager: CameraXManager)
        fun onCameraError(error: Throwable)
        fun onPermissionRequired(requiredPermissions: List<String>)
        fun onPermissionDenied(deniedPermissions: List<String>)
        fun onRotationChanged(newRotation: Int)
    }

    companion object {
        private const val TAG = "CameraLifecycleManager"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001

        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA
        )
    }

    private val _permissionState = MutableStateFlow(PermissionState(false, false, false))
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _currentRotation = MutableStateFlow(0)
    val currentRotation: StateFlow<Int> = _currentRotation.asStateFlow()

    private var cameraManager: CameraXManager? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var configuration: CameraConfiguration = CameraConfiguration()
    private var callback: CameraCallback? = null

    // Coroutine scope for lifecycle-aware operations
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Orientation handling
    private var orientationEventListener: OrientationEventListener? = null
    private var displayManager: DisplayManager? = null
    private var displayListener: DisplayManager.DisplayListener? = null

    init {
        setupDisplayRotationListener()
    }

    /**
     * Initialize camera lifecycle manager with configuration
     */
    fun initialize(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        configuration: CameraConfiguration = CameraConfiguration(),
        callback: CameraCallback? = null
    ) {
        this.lifecycleOwner = lifecycleOwner
        this.previewView = previewView
        this.configuration = configuration
        this.callback = callback

        // Add this as lifecycle observer
        lifecycleOwner.lifecycle.addObserver(this)

        // Initialize camera manager
        cameraManager = CameraXManager(context, performanceStrategy, privacyManager)

        // Setup orientation listener if enabled
        if (configuration.enableRotationHandling) {
            setupOrientationListener()
        }

        Timber.i("Camera lifecycle manager initialized")
    }

    /**
     * Check and request camera permissions
     */
    fun checkPermissions(activity: Activity? = null): Boolean {
        val hasPermissions = checkCameraPermissions()
        updatePermissionState(activity)

        if (!hasPermissions && activity != null) {
            requestCameraPermissions(activity)
        }

        return hasPermissions
    }

    /**
     * Start camera with current configuration
     */
    fun startCamera() {
        if (!_permissionState.value.hasCameraPermission) {
            Timber.w("Cannot start camera: permissions not granted")
            callback?.onPermissionRequired(REQUIRED_PERMISSIONS.toList())
            return
        }

        val owner = lifecycleOwner ?: run {
            Timber.e("Cannot start camera: no lifecycle owner")
            return
        }

        val preview = previewView ?: run {
            Timber.e("Cannot start camera: no preview view")
            return
        }

        val manager = cameraManager ?: run {
            Timber.e("Cannot start camera: manager not initialized")
            return
        }

        lifecycleScope.launch {
            try {
                // Initialize camera if needed
                if (!manager.cameraState.value.isInitialized) {
                    val initResult = manager.initialize(preview)
                    if (initResult.isFailure) {
                        callback?.onCameraError(initResult.exceptionOrNull() ?: Exception("Camera initialization failed"))
                        return@launch
                    }
                }

                // Start camera
                val startResult = manager.startCamera(
                    lifecycleOwner = owner,
                    preferFrontCamera = configuration.preferFrontCamera,
                    fitMode = configuration.fitMode,
                    imageAnalysisCallback = { imageProxy ->
                        // Handle image analysis callback
                        handleImageAnalysis(imageProxy)
                    }
                )

                if (startResult.isSuccess) {
                    _isActive.value = true
                    callback?.onCameraReady(manager)
                    Timber.i("Camera started successfully")
                } else {
                    callback?.onCameraError(startResult.exceptionOrNull() ?: Exception("Camera start failed"))
                }

            } catch (e: Exception) {
                Timber.e(e, "Error starting camera")
                callback?.onCameraError(e)
            }
        }
    }

    /**
     * Stop camera
     */
    fun stopCamera() {
        cameraManager?.stopCamera()
        _isActive.value = false
        Timber.i("Camera stopped")
    }

    /**
     * Switch between front and back camera
     */
    fun switchCamera() {
        val owner = lifecycleOwner ?: return
        val manager = cameraManager ?: return

        if (!_isActive.value) return

        lifecycleScope.launch {
            try {
                val result = manager.switchCamera(owner)
                if (result.isFailure) {
                    callback?.onCameraError(result.exceptionOrNull() ?: Exception("Camera switch failed"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Error switching camera")
                callback?.onCameraError(e)
            }
        }
    }

    /**
     * Update fit mode
     */
    fun updateFitMode(fitMode: FitMode) {
        configuration = configuration.copy(fitMode = fitMode)
        cameraManager?.updateFitMode(fitMode)
    }

    /**
     * Handle permission results
     */
    fun onPermissionResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
        activity: Activity
    ) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            val deniedPermissions = mutableListOf<String>()

            permissions.forEachIndexed { index, permission ->
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permission)
                }
            }

            updatePermissionState(activity)

            if (deniedPermissions.isEmpty()) {
                // All permissions granted
                if (configuration.autoStartOnResume) {
                    startCamera()
                }
            } else {
                callback?.onPermissionDenied(deniedPermissions)
            }
        }
    }

    // Lifecycle callbacks

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        Timber.d("Camera lifecycle: onCreate")
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Timber.d("Camera lifecycle: onStart")

        // Enable orientation listener
        orientationEventListener?.enable()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        Timber.d("Camera lifecycle: onResume")

        if (configuration.autoStartOnResume && _permissionState.value.hasCameraPermission) {
            startCamera()
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        Timber.d("Camera lifecycle: onPause")

        stopCamera()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Timber.d("Camera lifecycle: onStop")

        // Disable orientation listener
        orientationEventListener?.disable()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        Timber.d("Camera lifecycle: onDestroy")

        cleanup()
    }

    // Private helper methods

    private fun checkCameraPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestCameraPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            REQUIRED_PERMISSIONS,
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    private fun updatePermissionState(activity: Activity?) {
        val hasPermissions = checkCameraPermissions()
        val shouldShowRationale = activity?.let { act ->
            REQUIRED_PERMISSIONS.any { permission ->
                ActivityCompat.shouldShowRequestPermissionRationale(act, permission)
            }
        } ?: false

        val deniedPermanently = !hasPermissions && !shouldShowRationale

        _permissionState.value = PermissionState(
            hasCameraPermission = hasPermissions,
            shouldShowRationale = shouldShowRationale,
            permissionDeniedPermanently = deniedPermanently
        )
    }

    private fun setupOrientationListener() {
        orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val newRotation = when (orientation) {
                    in 45..134 -> 270
                    in 135..224 -> 180
                    in 225..314 -> 90
                    else -> 0
                }

                if (newRotation != _currentRotation.value) {
                    _currentRotation.value = newRotation
                    cameraManager?.updateRotation(newRotation)
                    callback?.onRotationChanged(newRotation)
                }
            }
        }
    }

    private fun setupDisplayRotationListener() {
        displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}

            override fun onDisplayChanged(displayId: Int) {
                val display = displayManager?.getDisplay(displayId)
                if (display?.displayId == Display.DEFAULT_DISPLAY) {
                    val newRotation = display.rotation
                    if (newRotation != _currentRotation.value) {
                        _currentRotation.value = newRotation
                        cameraManager?.updateRotation(newRotation)
                        callback?.onRotationChanged(newRotation)
                    }
                }
            }
        }

        displayManager?.registerDisplayListener(displayListener, null)
    }

    private fun handleImageAnalysis(imageProxy: androidx.camera.core.ImageProxy) {
        try {
            // Performance check
            if (configuration.enablePerformanceOptimization) {
                if (!performanceStrategy.shouldProcessFrame()) {
                    imageProxy.close()
                    return
                }
            }

            // Privacy check
            if (privacyManager.isOfflineModeEnabled()) {
                // Process locally only
                processImageLocally(imageProxy)
            } else {
                // Can process with cloud features if allowed
                processImageWithCloudFeatures(imageProxy)
            }

        } catch (e: Exception) {
            Timber.e(e, "Error handling image analysis")
        } finally {
            imageProxy.close()
        }
    }

    private fun processImageLocally(imageProxy: androidx.camera.core.ImageProxy) {
        // Implement local-only image processing
        // This would integrate with pose detection pipeline
        Timber.d("Processing image locally: ${imageProxy.width}x${imageProxy.height}")
    }

    private fun processImageWithCloudFeatures(imageProxy: androidx.camera.core.ImageProxy) {
        // Implement image processing with cloud features
        // This would integrate with cloud-based pose analysis
        Timber.d("Processing image with cloud features: ${imageProxy.width}x${imageProxy.height}")
    }

    private fun cleanup() {
        // Stop camera
        stopCamera()

        // Release camera manager
        cameraManager?.release()
        cameraManager = null

        // Cleanup orientation listener
        orientationEventListener?.disable()
        orientationEventListener = null

        // Cleanup display listener
        displayListener?.let { listener ->
            displayManager?.unregisterDisplayListener(listener)
        }
        displayListener = null

        // Remove lifecycle observer
        lifecycleOwner?.lifecycle?.removeObserver(this)

        Timber.i("Camera lifecycle manager cleaned up")
    }

    /**
     * Get current camera manager (for advanced usage)
     */
    fun getCameraManager(): CameraXManager? = cameraManager

    /**
     * Check if camera is currently active
     */
    fun isCameraActive(): Boolean = _isActive.value

    /**
     * Get current camera configuration
     */
    fun getCurrentConfiguration(): CameraConfiguration = configuration
}