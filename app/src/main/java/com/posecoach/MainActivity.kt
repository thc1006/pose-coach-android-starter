package com.posecoach

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.posecoach.app.camera.CameraActivity
import com.posecoach.integration.TddPoseGeminiIntegrator
import kotlinx.coroutines.launch

/**
 * TDD Green Phase Implementation: MainActivity
 *
 * Following CLAUDE.md project goal:
 * "在裝置端以 MediaPipe 即時偵測人體姿態並疊骨架；以 Gemini 2.5 Structured Output 回傳 3 條可執行姿勢建議"
 *
 * METHODOLOGY: Minimal implementation to pass TDD integration tests
 * CONSTRAINTS: Privacy-first, use OverlayView only, structured output only
 */
class MainActivity : AppCompatActivity() {

    // TDD Green Phase: Basic UI components to make tests pass
    private lateinit var startButton: Button
    private lateinit var statusText: TextView
    private lateinit var privacyConsentView: LinearLayout
    private lateinit var suggestionsText: TextView

    // Privacy and permission management
    private var hasPrivacyConsent = false
    private var hasCameraPermission = false

    // TDD Green Phase: Gemini integration
    private lateinit var geminiIntegrator: TddPoseGeminiIntegrator

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        updateUIState()
        if (isGranted && hasPrivacyConsent) {
            startPoseDetection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TDD Green Phase: Minimal UI setup
        setupMinimalUI()

        // TDD Green Phase: Initialize Gemini integrator
        setupGeminiIntegration()

        // TDD Green Phase: Check existing permissions
        checkCameraPermission()

        // TDD Green Phase: Show privacy consent if needed
        if (!hasPrivacyConsent) {
            showPrivacyConsent()
        }
    }

    /**
     * TDD Green Phase: Minimal UI setup to make tests pass
     * Tests expect: Camera preview, pose detection, overlay capabilities
     */
    private fun setupMinimalUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        statusText = TextView(this).apply {
            text = "TDD Green Phase: Basic UI Ready"
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }

        startButton = Button(this).apply {
            text = "Start Pose Detection"
            isEnabled = false
            setOnClickListener {
                if (hasCameraPermission && hasPrivacyConsent) {
                    startPoseDetection()
                } else {
                    requestRequiredPermissions()
                }
            }
        }

        val testGeminiButton = Button(this).apply {
            text = "Test Gemini Suggestions"
            setOnClickListener {
                if (hasPrivacyConsent) {
                    geminiIntegrator.triggerSuggestionsManually()
                    suggestionsText.text = "🔄 Getting suggestions from Gemini..."
                } else {
                    showPrivacyConsent()
                }
            }
        }

        suggestionsText = TextView(this).apply {
            text = "Pose suggestions will appear here"
            textSize = 14f
            setPadding(16, 16, 16, 16)
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.edit_text)
        }

        privacyConsentView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        layout.addView(statusText)
        layout.addView(startButton)
        layout.addView(testGeminiButton)
        layout.addView(suggestionsText)
        layout.addView(privacyConsentView)

        setContentView(layout)
    }

    /**
     * TDD Green Phase: Setup Gemini integration
     * Tests expect: Gemini integration with structured output
     */
    private fun setupGeminiIntegration() {
        geminiIntegrator = TddPoseGeminiIntegrator(this, lifecycleScope)

        geminiIntegrator.setSuggestionsListener(object : TddPoseGeminiIntegrator.SuggestionsListener {
            override fun onSuggestionsReceived(suggestions: List<String>) {
                runOnUiThread {
                    val suggestionsDisplay = suggestions.mapIndexed { index, suggestion ->
                        "${index + 1}. $suggestion"
                    }.joinToString("\n\n")

                    suggestionsText.text = "✅ Gemini Suggestions (${suggestions.size}):\n\n$suggestionsDisplay"
                }
            }

            override fun onSuggestionsError(error: String) {
                runOnUiThread {
                    suggestionsText.text = "❌ Error getting suggestions: $error"
                }
            }
        })
    }

    /**
     * TDD Green Phase: Camera permission handling
     * Tests expect: Proper permission flow before camera access
     */
    private fun checkCameraPermission() {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        updateUIState()
    }

    /**
     * TDD Green Phase: Privacy consent implementation
     * Tests expect: Explicit consent before any data processing
     */
    private fun showPrivacyConsent() {
        AlertDialog.Builder(this)
            .setTitle("Privacy Consent Required")
            .setMessage("""
                This app processes your pose data for exercise coaching:

                • Camera captures your movements
                • MediaPipe analyzes pose landmarks locally
                • Pose data may be sent to Gemini for suggestions
                • No video is stored or transmitted

                Do you consent to this data processing?
            """.trimIndent())
            .setPositiveButton("I Consent") { _, _ ->
                hasPrivacyConsent = true
                updateUIState()
                if (hasCameraPermission) {
                    startPoseDetection()
                } else {
                    requestCameraPermission()
                }
            }
            .setNegativeButton("No Thanks") { _, _ ->
                hasPrivacyConsent = false
                updateUIState()
                showOfflineMode()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * TDD Green Phase: Request camera permission
     * Tests expect: Proper permission request flow
     */
    private fun requestCameraPermission() {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * TDD Green Phase: Request all required permissions
     */
    private fun requestRequiredPermissions() {
        if (!hasPrivacyConsent) {
            showPrivacyConsent()
        } else if (!hasCameraPermission) {
            requestCameraPermission()
        }
    }

    /**
     * TDD Green Phase: Start pose detection
     * Tests expect: Integration with CameraActivity for pose detection
     */
    private fun startPoseDetection() {
        if (hasCameraPermission && hasPrivacyConsent) {
            statusText.text = "Starting pose detection..."

            // Navigate to existing CameraActivity which has the pose detection implementation
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * TDD Green Phase: Offline mode for privacy-conscious users
     * Tests expect: App functions without cloud services
     */
    private fun showOfflineMode() {
        statusText.text = "Offline mode: Limited functionality available"
        startButton.text = "Use Offline Mode"
        startButton.isEnabled = hasCameraPermission

        if (startButton.isEnabled) {
            startButton.setOnClickListener {
                // TODO: Implement offline-only pose detection
                statusText.text = "Offline pose detection not yet implemented"
            }
        }
    }

    /**
     * TDD Green Phase: Update UI based on current state
     */
    private fun updateUIState() {
        val ready = hasCameraPermission && hasPrivacyConsent

        startButton.isEnabled = ready

        val status = when {
            !hasPrivacyConsent -> "Privacy consent required"
            !hasCameraPermission -> "Camera permission required"
            ready -> "Ready to start pose detection"
            else -> "Checking requirements..."
        }

        statusText.text = status
    }

    /**
     * TDD Green Phase: Handle back press
     */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!hasPrivacyConsent) {
            // Allow exit without consent
            @Suppress("DEPRECATION") super.onBackPressed()
        } else {
            @Suppress("DEPRECATION") super.onBackPressed()
        }
    }
}