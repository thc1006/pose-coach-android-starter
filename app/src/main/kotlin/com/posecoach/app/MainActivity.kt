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
import com.posecoach.app.overlay.PoseOverlayView
import com.posecoach.app.suggestions.SuggestionManager
import com.posecoach.app.privacy.ConsentManager
import com.posecoach.app.R
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    // Core managers following CLAUDE.md architecture
    private lateinit var poseDetectionManager: PoseDetectionManager
    private lateinit var suggestionManager: SuggestionManager
    private lateinit var consentManager: ConsentManager

    // Camera components
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null

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
        poseDetectionManager = PoseDetectionManager(this, lifecycleScope)
        suggestionManager = SuggestionManager(this, lifecycleScope)

        // Setup pose detection callbacks
        setupPoseDetectionCallbacks()

        // Request camera permissions
        requestCameraPermissions()
    }

    private fun setupPoseDetectionCallbacks() {
        lifecycleScope.launch {
            poseDetectionManager.poseLandmarks.collect { landmarks ->
                // Update overlay with pose skeleton (CLAUDE.md requirement: OverlayView)
                overlayView.updatePose(landmarks)

                // Send to Gemini for suggestions (CLAUDE.md requirement: responseSchema usage)
                suggestionManager.analyzePose(landmarks)
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
                showError(error)
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
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview use case
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Image analysis for pose detection
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analyzer ->
                    analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                        // Process frame with MediaPipe pose detection
                        poseDetectionManager.processFrame(imageProxy)
                    }
                }

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera lifecycle
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )

                statusText.text = getString(R.string.pose_detection_active)

            } catch (exc: Exception) {
                showError(getString(R.string.failed_to_start_camera, exc.message))
            }

        }, ContextCompat.getMainExecutor(this))
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (::poseDetectionManager.isInitialized) {
            poseDetectionManager.cleanup()
        }
    }
}
