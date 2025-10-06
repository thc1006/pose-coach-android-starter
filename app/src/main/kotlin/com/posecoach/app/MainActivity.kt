package com.posecoach.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.posecoach.app.pose.PoseDetectionManager
import timber.log.Timber
import com.posecoach.app.overlay.PoseOverlayView
import com.posecoach.app.overlay.FitMode
import com.posecoach.app.suggestions.SuggestionManager
import com.posecoach.app.privacy.ConsentManager
import com.posecoach.app.R
import com.posecoach.app.livecoach.LiveCoachManager
import com.posecoach.gemini.live.models.SessionState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Main Activity with CameraX PreviewView for pose detection
 * Following TDD implementation according to CLAUDE.md requirements
 */
class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: PoseOverlayView
    private lateinit var cameraExecutor: ExecutorService

    // UI components
    private lateinit var suggestionsPanel: View
    private lateinit var suggestion1: TextView
    private lateinit var suggestion2: TextView
    private lateinit var suggestion3: TextView
    private lateinit var statusText: TextView
    private var liveCoachButton: FloatingActionButton? = null
    private var liveCoachStatus: TextView? = null
    private var cameraSwitchButton: FloatingActionButton? = null

    // LIVE API state
    private var isLiveCoachActive = false

    // Core managers following CLAUDE.md architecture
    private lateinit var poseDetectionManager: PoseDetectionManager
    private lateinit var suggestionManager: SuggestionManager
    private lateinit var consentManager: ConsentManager
    private var liveCoachManager: LiveCoachManager? = null

    // Camera components
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            initializeCamera()
        } else {
            showPermissionError()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize consent manager first (CLAUDE.md privacy requirement)
        consentManager = ConsentManager(this)

        // Check consent before proceeding
        if (!consentManager.hasUserConsent()) {
            consentManager.showConsentDialog { granted ->
                if (granted) {
                    setupActivity()
                } else {
                    finish()
                }
            }
        } else {
            setupActivity()
        }
    }

    private fun setupActivity() {
        setContentView(R.layout.activity_main)

        // Initialize UI components
        previewView = findViewById(R.id.camera_preview)
        overlayView = findViewById(R.id.pose_overlay)
        suggestionsPanel = findViewById(R.id.suggestions_panel)
        suggestion1 = findViewById(R.id.suggestion_1)
        suggestion2 = findViewById(R.id.suggestion_2)
        suggestion3 = findViewById(R.id.suggestion_3)
        statusText = findViewById(R.id.status_text)

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize core managers
        Timber.i("Initializing PoseDetectionManager...")
        poseDetectionManager = PoseDetectionManager(this, lifecycleScope)
        Timber.i("Initializing SuggestionManager...")
        suggestionManager = SuggestionManager(this, lifecycleScope)

        // Initialize LIVE API manager with API key from BuildConfig
        initializeLiveCoachManager()

        // Setup pose detection callbacks
        setupPoseDetectionCallbacks()

        // Setup LIVE API UI controls
        setupLiveCoachUI()

        // Setup camera switch button
        setupCameraSwitchButton()

        // Request camera permissions
        requestCameraPermissions()
    }

    private fun setupPoseDetectionCallbacks() {
        lifecycleScope.launch {
            poseDetectionManager.poseLandmarks.collect { landmarks ->
                // Log pose detection for debugging
                Timber.i("MainActivity: Received pose with ${landmarks.landmarks.size} landmarks")

                // Update overlay with pose skeleton (CLAUDE.md requirement: OverlayView)
                overlayView.updatePose(landmarks)

                // Send to Gemini for suggestions (CLAUDE.md requirement: responseSchema usage)
                suggestionManager.analyzePose(landmarks)

                // Show status to user
                runOnUiThread {
                    statusText.text = "Pose detected: ${landmarks.landmarks.size} landmarks"
                }
            }
        }

        lifecycleScope.launch {
            suggestionManager.suggestions.collect { suggestions ->
                // Display exactly 3 suggestions (CLAUDE.md requirement)
                displaySuggestions(suggestions.take(3))
            }
        }

        lifecycleScope.launch {
            poseDetectionManager.processingErrors.collect { error ->
                Timber.e("Pose detection error: $error")
                showError(error)
                // Update status to show the error
                runOnUiThread {
                    statusText.text = "Error: $error"
                }
            }
        }

        lifecycleScope.launch {
            suggestionManager.analysisErrors.collect { error ->
                showError(error)
            }
        }
    }

    private fun requestCameraPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO  // For Gemini Live API
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            initializeCamera()
        }
    }

    private fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Bind camera use cases to lifecycle
     * This is called during initialization and when switching cameras
     */
    private fun bindCamera() {
        val provider = cameraProvider ?: run {
            Timber.e("Camera provider not initialized")
            return
        }

        // Build camera selector based on current lens facing
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        // Preview use case
        preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        // Image analysis for pose detection
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analyzer ->
                analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                    // Determine if front facing based on lens facing
                    val isFrontFacing = lensFacing == CameraSelector.LENS_FACING_FRONT

                    // Configure overlay with camera parameters for proper rotation
                    overlayView.configureCameraDisplay(
                        cameraWidth = imageProxy.width,
                        cameraHeight = imageProxy.height,
                        rotation = imageProxy.imageInfo.rotationDegrees,
                        frontFacing = isFrontFacing,
                        aspectFitMode = FitMode.CENTER_CROP
                    )

                    // Process frame with MediaPipe pose detection
                    try {
                        poseDetectionManager.processFrame(imageProxy)
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing frame")
                        imageProxy.close()
                    }
                }
            }

        try {
            // Unbind all use cases before rebinding
            provider.unbindAll()

            // Bind use cases to camera lifecycle
            camera = provider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalysis
            )

            // Update status
            val cameraType = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "Front" else "Back"
            statusText.text = "$cameraType Camera - ${getString(R.string.pose_detection_active)}"

            Timber.i("Camera bound successfully: $cameraType camera")

        } catch (exc: Exception) {
            Timber.e(exc, "Failed to bind camera")
            showError(getString(R.string.failed_to_start_camera, exc.message))
        }
    }

    private fun displaySuggestions(suggestions: List<String>) {
        if (suggestions.isEmpty()) {
            suggestionsPanel.visibility = View.GONE
            return
        }

        // Show exactly 3 suggestions as per CLAUDE.md requirement
        suggestion1.text = suggestions.getOrNull(0) ?: ""
        suggestion2.text = suggestions.getOrNull(1) ?: ""
        suggestion3.text = suggestions.getOrNull(2) ?: ""

        suggestionsPanel.visibility = View.VISIBLE
    }

    private fun showPermissionError() {
        Toast.makeText(
            this,
            getString(R.string.camera_permission_required),
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // LIVE API Integration Methods

    private fun initializeLiveCoachManager() {
        try {
            val apiKey = BuildConfig.GEMINI_LIVE_API_KEY
            if (apiKey.isNotEmpty()) {
                liveCoachManager = LiveCoachManager(
                    context = this,
                    lifecycleScope = lifecycleScope,
                    apiKey = apiKey
                )

                setupLiveCoachCallbacks()
                Timber.i("LiveCoachManager initialized successfully")
            } else {
                Timber.w("Gemini Live API key not configured")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize LiveCoachManager")
            showError("Live coaching not available: ${e.message}")
        }
    }

    private fun setupCameraSwitchButton() {
        // Find the camera switch button
        cameraSwitchButton = try {
            findViewById(R.id.camera_switch_button)
        } catch (e: Exception) {
            Timber.w("Camera switch button not found in layout")
            null
        }

        // Setup click listener for camera switching
        cameraSwitchButton?.setOnClickListener {
            switchCamera()
        }

        Timber.d("Camera switch button setup complete")
    }

    private fun setupLiveCoachUI() {
        // Try to find the FAB in layout, or add programmatically if needed
        liveCoachButton = try {
            findViewById(R.id.live_coach_button)
        } catch (e: Exception) {
            null
        }

        liveCoachStatus = try {
            findViewById(R.id.live_coach_status)
        } catch (e: Exception) {
            null
        }

        // Setup click listener for LIVE API toggle
        liveCoachButton?.setOnClickListener {
            toggleLiveCoach()
        }

        // Initially hide the status
        liveCoachStatus?.visibility = View.GONE
    }

    /**
     * Switch between front and back cameras
     * Implements camera switching with proper overlay mirroring
     */
    private fun switchCamera() {
        // Toggle lens facing
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

        val cameraType = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "front" else "back"
        Timber.i("Switching to $cameraType camera")

        // Rebind camera with new lens facing
        bindCamera()

        // Update overlay view for proper mirroring
        val isFrontFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
        overlayView.updateCameraFacing(isFrontFacing)

        // Provide user feedback
        Toast.makeText(
            this,
            "Switched to $cameraType camera",
            Toast.LENGTH_SHORT
        ).show()

        Timber.i("Camera switch completed: $cameraType camera active")
    }

    private fun setupLiveCoachCallbacks() {
        liveCoachManager?.let { manager ->
            // Monitor session state
            lifecycleScope.launch {
                manager.sessionState.collectLatest { state ->
                    updateLiveCoachUI(state)
                }
            }

            // Monitor coaching responses
            lifecycleScope.launch {
                manager.coachingResponses.collectLatest { response ->
                    displayLiveCoachResponse(response)
                }
            }

            // Monitor transcriptions
            lifecycleScope.launch {
                manager.transcriptions.collectLatest { transcription ->
                    Timber.d("User said: $transcription")
                    liveCoachStatus?.text = "You: $transcription"
                }
            }

            // Monitor errors
            lifecycleScope.launch {
                manager.errors.collectLatest { error ->
                    Timber.e("Live coach error: $error")
                    showError("Live coach: $error")
                }
            }

            // Send pose landmarks to LIVE API when available
            lifecycleScope.launch {
                poseDetectionManager.poseLandmarks.collectLatest { landmarks ->
                    if (isLiveCoachActive) {
                        manager.updatePoseLandmarks(landmarks)
                    }
                }
            }
        }
    }

    private fun toggleLiveCoach() {
        if (isLiveCoachActive) {
            stopLiveCoach()
        } else {
            startLiveCoach()
        }
    }

    private fun startLiveCoach() {
        liveCoachManager?.let { manager ->
            lifecycleScope.launch {
                try {
                    manager.connect()
                    isLiveCoachActive = true
                    liveCoachButton?.setImageResource(android.R.drawable.ic_media_pause)
                    Toast.makeText(this@MainActivity, "Live coach started", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to start live coach")
                    showError("Failed to start live coach: ${e.message}")
                }
            }
        } ?: run {
            showError("Live coach not available")
        }
    }

    private fun stopLiveCoach() {
        liveCoachManager?.let { manager ->
            lifecycleScope.launch {
                try {
                    manager.disconnect()
                    isLiveCoachActive = false
                    liveCoachButton?.setImageResource(android.R.drawable.ic_btn_speak_now)
                    liveCoachStatus?.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Live coach stopped", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to stop live coach")
                    showError("Failed to stop live coach: ${e.message}")
                }
            }
        }
    }

    private fun updateLiveCoachUI(state: SessionState) {
        when (state) {
            SessionState.DISCONNECTED -> {
                liveCoachButton?.setImageResource(android.R.drawable.ic_btn_speak_now)
                liveCoachStatus?.visibility = View.GONE
                isLiveCoachActive = false
            }
            SessionState.CONNECTING -> {
                liveCoachStatus?.text = "Connecting to Live API..."
                liveCoachStatus?.visibility = View.VISIBLE
            }
            SessionState.CONNECTED -> {
                liveCoachButton?.setImageResource(android.R.drawable.ic_media_pause)
                liveCoachStatus?.text = "Live coach connected"
                liveCoachStatus?.visibility = View.VISIBLE
            }
            SessionState.SETUP_PENDING -> {
                liveCoachStatus?.text = "Setting up session..."
                liveCoachStatus?.visibility = View.VISIBLE
            }
            SessionState.SETUP_COMPLETE -> {
                liveCoachStatus?.text = "Ready for interaction"
                liveCoachStatus?.visibility = View.VISIBLE
            }
            SessionState.ACTIVE -> {
                liveCoachStatus?.text = "Session active"
                liveCoachStatus?.visibility = View.VISIBLE
            }
            SessionState.DISCONNECTING -> {
                liveCoachStatus?.text = "Disconnecting..."
                liveCoachStatus?.visibility = View.VISIBLE
            }
            SessionState.ERROR -> {
                liveCoachButton?.setImageResource(android.R.drawable.ic_btn_speak_now)
                liveCoachStatus?.text = "Error"
                liveCoachStatus?.visibility = View.VISIBLE
                isLiveCoachActive = false
            }
        }
    }

    private fun displayLiveCoachResponse(response: String) {
        // Display the coaching response in the UI
        runOnUiThread {
            // You could display this in a dedicated area or as an overlay
            liveCoachStatus?.text = "Coach: $response"
            liveCoachStatus?.visibility = View.VISIBLE

            // Optionally also show as a toast for important messages
            if (response.contains("important", ignoreCase = true) ||
                response.contains("warning", ignoreCase = true)) {
                Toast.makeText(this, response, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (::poseDetectionManager.isInitialized) {
            poseDetectionManager.cleanup()
        }
        liveCoachManager?.disconnect()
    }
}
