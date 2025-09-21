package com.posecoach.app.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.posecoach.app.overlay.FitMode
import com.posecoach.app.overlay.PoseOverlayView
import com.posecoach.app.performance.PerformanceDegradationStrategy
import com.posecoach.app.performance.PerformanceMetrics
import com.posecoach.app.privacy.EnhancedPrivacyManager
import com.posecoach.app.multipose.MultiPersonPoseManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Complete example demonstrating the CameraX pipeline with comprehensive rotation and transformation support.
 * Shows integration with performance monitoring, privacy management, and pose detection.
 */
class CameraXPipelineExample : AppCompatActivity() {

    // UI Components
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: PoseOverlayView
    private lateinit var switchCameraButton: FloatingActionButton
    private lateinit var performanceIndicator: View

    // Core Components
    private lateinit var performanceMetrics: PerformanceMetrics
    private lateinit var performanceStrategy: PerformanceDegradationStrategy
    private lateinit var privacyManager: EnhancedPrivacyManager
    private lateinit var multiPersonManager: MultiPersonPoseManager
    private lateinit var cameraPoseIntegration: CameraPoseIntegration

    // Permission handling
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeCamera()
        } else {
            showPermissionDeniedMessage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupViews()
        initializeComponents()
        setupObservers()
        checkPermissionsAndStart()
    }

    private fun setupViews() {
        // In a real implementation, this would use proper layout files
        // For this example, we'll create views programmatically for demonstration

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        overlayView = PoseOverlayView(this)

        switchCameraButton = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            setOnClickListener { switchCamera() }
        }

        performanceIndicator = View(this)

        // Setup layout (simplified for example)
        setupLayout()
    }

    private fun setupLayout() {
        // Create a simple layout programmatically
        val rootLayout = androidx.constraintlayout.widget.ConstraintLayout(this)

        // Add preview view
        rootLayout.addView(previewView)

        // Add overlay view
        rootLayout.addView(overlayView)

        // Add switch camera button
        rootLayout.addView(switchCameraButton)

        // Add performance indicator
        rootLayout.addView(performanceIndicator)

        setContentView(rootLayout)

        // Setup constraints (simplified)
        val layoutParams = previewView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        layoutParams.width = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
        layoutParams.height = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
        previewView.layoutParams = layoutParams

        Timber.d("Views setup completed")
    }

    private fun initializeComponents() {
        // Initialize performance components
        performanceMetrics = PerformanceMetrics()
        performanceStrategy = PerformanceDegradationStrategy(performanceMetrics)

        // Initialize privacy manager
        privacyManager = EnhancedPrivacyManager(this)

        // Initialize multi-person pose manager
        multiPersonManager = MultiPersonPoseManager()

        // Initialize camera-pose integration
        cameraPoseIntegration = CameraPoseIntegration(
            performanceStrategy = performanceStrategy,
            privacyManager = privacyManager,
            multiPersonManager = multiPersonManager
        )

        Timber.i("All components initialized")
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            // Observe privacy settings changes
            privacyManager.privacySettings.collect { settings ->
                handlePrivacySettingsChange(settings)
            }
        }

        lifecycleScope.launch {
            // Observe performance levels
            performanceStrategy.currentLevel.collect { level ->
                handlePerformanceLevelChange(level)
            }
        }

        lifecycleScope.launch {
            // Observe current poses
            cameraPoseIntegration.currentPoses.collect { poses ->
                handlePoseUpdate(poses)
            }
        }

        lifecycleScope.launch {
            // Observe performance metrics and alerts
            val performanceFlow = cameraPoseIntegration.getPerformanceMetrics()
            val alertsFlow = cameraPoseIntegration.getPerformanceAlerts()

            if (performanceFlow != null && alertsFlow != null) {
                combine(performanceFlow, alertsFlow) { metrics, alerts ->
                    Pair(metrics, alerts)
                }.collect { (metrics, alerts) ->
                    handlePerformanceUpdate(metrics, alerts)
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val requiredPermissions = arrayOf(Manifest.permission.CAMERA)

        val missingPermissions = requiredPermissions.filter { permission ->
            checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            initializeCamera()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun initializeCamera() {
        if (!cameraPoseIntegration.isInitialized.value) {
            // Show privacy consent dialog if needed
            if (privacyManager.needsConsentDialog()) {
                showPrivacyConsentDialog()
            } else {
                startCameraInitialization()
            }
        }
    }

    private fun showPrivacyConsentDialog() {
        privacyManager.showConsentDialog(
            onAccept = { privacyLevel ->
                Timber.i("Privacy level accepted: $privacyLevel")
                startCameraInitialization()
            },
            onDecline = {
                Timber.i("Privacy dialog declined - using maximum privacy")
                startCameraInitialization()
            }
        )
    }

    private fun startCameraInitialization() {
        val callback = object : CameraPoseIntegration.PoseDetectionCallback {
            override fun onPoseDetected(result: CameraPoseIntegration.PoseDetectionResult) {
                handlePoseDetectionResult(result)
            }

            override fun onPoseDetectionError(error: Throwable) {
                Timber.e(error, "Pose detection error")
                showError("Pose detection error: ${error.message}")
            }

            override fun onPerformanceUpdate(metrics: CameraPerformanceMonitor.FrameMetrics) {
                // Handle individual frame metrics if needed
            }
        }

        cameraPoseIntegration.initialize(
            context = this,
            lifecycleOwner = this,
            previewView = previewView,
            overlayView = overlayView,
            callback = callback
        )

        cameraPoseIntegration.startPoseDetection(this)
    }

    private fun switchCamera() {
        if (cameraPoseIntegration.isInitialized.value) {
            cameraPoseIntegration.switchCamera()
        }
    }

    private fun handlePrivacySettingsChange(settings: EnhancedPrivacyManager.PrivacySettings) {
        Timber.d("Privacy settings changed: $settings")

        // Update UI based on privacy settings
        if (settings.showPrivacyIndicator) {
            // Show privacy indicator
            performanceIndicator.visibility = View.VISIBLE
        } else {
            performanceIndicator.visibility = View.GONE
        }

        // Update pose detection settings based on privacy
        cameraPoseIntegration.updatePoseSettings(
            maxDetectedPoses = if (settings.offlineModeEnabled) 1 else 5,
            confidenceThreshold = 0.7f,
            enableMultiPerson = !settings.offlineModeEnabled
        )
    }

    private fun handlePerformanceLevelChange(level: PerformanceDegradationStrategy.Level) {
        Timber.d("Performance level changed: $level")

        // Update UI to reflect performance level
        val levelInfo = PerformanceDegradationStrategy.PERFORMANCE_LEVELS[level]
        if (levelInfo != null) {
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Performance: ${levelInfo.description}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Adjust fit mode based on performance level
        val fitMode = when (level) {
            PerformanceDegradationStrategy.Level.HIGH_QUALITY -> FitMode.CENTER_CROP
            PerformanceDegradationStrategy.Level.BALANCED -> FitMode.CENTER_CROP
            PerformanceDegradationStrategy.Level.PERFORMANCE -> FitMode.CENTER_INSIDE
            PerformanceDegradationStrategy.Level.LOW_POWER -> FitMode.FILL
        }

        cameraPoseIntegration.updateFitMode(fitMode)
    }

    private fun handlePoseUpdate(poses: List<CameraPoseIntegration.PoseData>) {
        Timber.d("Poses updated: ${poses.size} detected")

        // Update UI elements based on pose detection
        runOnUiThread {
            val poseCount = poses.count { it.visible }
            if (poseCount > 0) {
                // Show pose count or other indicators
                performanceIndicator.alpha = 1.0f
            } else {
                performanceIndicator.alpha = 0.5f
            }
        }
    }

    private fun handlePoseDetectionResult(result: CameraPoseIntegration.PoseDetectionResult) {
        // Log performance metrics
        if (result.processingTimeMs > 50.0) {
            Timber.w("Slow pose detection: ${result.processingTimeMs}ms")
        }

        // Process poses for application logic
        result.poses.forEach { pose ->
            if (pose.confidence > 0.8f) {
                Timber.d("High confidence pose detected: ${pose.personId}")
            }
        }
    }

    private fun handlePerformanceUpdate(
        metrics: CameraPerformanceMonitor.PerformanceSnapshot,
        alerts: CameraPerformanceMonitor.PerformanceAlerts
    ) {
        // Update performance indicator
        runOnUiThread {
            val color = when {
                alerts.lowFpsAlert || alerts.highLatencyAlert -> android.graphics.Color.RED
                alerts.memoryPressureAlert -> android.graphics.Color.YELLOW
                else -> android.graphics.Color.GREEN
            }
            performanceIndicator.setBackgroundColor(color)
        }

        // Show alerts if necessary
        if (alerts.lowFpsAlert) {
            showPerformanceAlert("Low FPS detected (${metrics.currentFps.toInt()})")
        }

        if (alerts.memoryPressureAlert) {
            showPerformanceAlert("High memory usage (${metrics.averageMemoryUsageMB.toInt()}MB)")
        }
    }

    private fun showPermissionDeniedMessage() {
        Snackbar.make(
            previewView,
            "Camera permission is required for pose detection",
            Snackbar.LENGTH_LONG
        ).setAction("Settings") {
            // Open app settings
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        }.show()
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showPerformanceAlert(message: String) {
        runOnUiThread {
            Snackbar.make(previewView, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        cameraPoseIntegration.onPermissionResult(requestCode, permissions, grantResults, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraPoseIntegration.cleanup()
        Timber.i("Camera pipeline example destroyed")
    }

    /**
     * Public API for testing and external usage
     */
    fun runPerformanceBenchmark() {
        lifecycleScope.launch {
            val results = cameraPoseIntegration.runPerformanceBenchmark()
            if (results != null) {
                Timber.i("Performance benchmark completed:")
                Timber.i("  Overall score: ${results.overallPerformanceScore}")
                Timber.i("  Accuracy score: ${results.overallAccuracyScore}")
                Timber.i("  Benchmarks: ${results.benchmarks.size}")
                Timber.i("  Accuracy tests: ${results.accuracyTests.size}")

                showPerformanceAlert(
                    "Benchmark: Performance ${(results.overallPerformanceScore * 100).toInt()}%, " +
                    "Accuracy ${(results.overallAccuracyScore * 100).toInt()}%"
                )
            }
        }
    }

    fun getCurrentCameraState(): CameraXManager.CameraState? {
        return cameraPoseIntegration.getCameraManager()?.cameraState?.value
    }

    fun getPerformanceReport(): String {
        // This would get the actual performance report
        return "Performance Report:\n" +
               "FPS: ${performanceStrategy.getCurrentPerformanceLevel().frameSkipRatio}\n" +
               "Level: ${performanceStrategy.currentLevel.value}\n" +
               "Privacy: ${privacyManager.currentPrivacyLevel.value}"
    }
}