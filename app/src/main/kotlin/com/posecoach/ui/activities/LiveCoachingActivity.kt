package com.posecoach.ui.activities

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.posecoach.R
import com.posecoach.databinding.ActivityLiveCoachingBinding
import com.posecoach.services.GeminiLiveService
import com.posecoach.services.PoseAnalysisService
import com.posecoach.ui.components.*
import com.posecoach.ui.viewmodels.LiveCoachingViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Main activity for live coaching with Gemini Live API integration
 * Features:
 * - 15-minute audio sessions with automatic resumption
 * - Real-time pose detection and coaching
 * - Voice activity detection and transcription
 * - Tool execution for pose analysis
 * - Session management and error handling
 */
class LiveCoachingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLiveCoachingBinding
    private lateinit var viewModel: LiveCoachingViewModel
    private lateinit var cameraExecutor: ExecutorService

    // Services
    private var geminiLiveService: GeminiLiveService? = null
    private var poseAnalysisService: PoseAnalysisService? = null
    private var isGeminiServiceBound = false
    private var isPoseServiceBound = false

    // Camera components
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            initializeServices()
        } else {
            showPermissionError()
        }
    }

    // Service connections
    private val geminiServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as GeminiLiveService.LocalBinder
            geminiLiveService = binder.getService()
            isGeminiServiceBound = true
            setupGeminiServiceCallbacks()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isGeminiServiceBound = false
            geminiLiveService = null
        }
    }

    private val poseServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as PoseAnalysisService.LocalBinder
            poseAnalysisService = binder.getService()
            isPoseServiceBound = true
            setupPoseServiceCallbacks()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isPoseServiceBound = false
            poseAnalysisService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveCoachingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[LiveCoachingViewModel::class.java]

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Setup UI components
        setupUI()

        // Request permissions
        requestPermissions()
    }

    private fun setupUI() {
        // Configure push-to-talk button
        binding.pushToTalkButton.apply {
            setOnTalkStartListener { startTalking() }
            setOnTalkEndListener { stopTalking() }
            setOnLongPressListener { showVADSettings() }
        }

        // Configure other buttons
        binding.settingsButton.setOnClickListener { openSettings() }
        binding.vadSettingsButton.setOnClickListener { showVADSettings() }
        binding.audioDeviceButton.setOnClickListener { showAudioDeviceSelector() }

        // Setup connection status
        binding.connectionStatus.setOnConnectionRetryListener { retryConnection() }

        // Setup session timer
        binding.sessionTimer.setOnSessionLimitListener { handleSessionLimit() }

        // Observe ViewModel
        observeViewModel()
    }

    private fun observeViewModel() {
        // Connection status
        viewModel.connectionStatus.observe(this) { status ->
            binding.connectionStatus.updateStatus(status)
            updateUIForConnectionStatus(status)
        }

        // Session state
        viewModel.sessionState.observe(this) { state ->
            binding.sessionTimer.updateSessionState(state)
            binding.pushToTalkButton.isEnabled = state.isActive
        }

        // Transcription
        viewModel.transcription.observe(this) { text ->
            binding.transcriptionText.text = text
        }

        // Voice activity
        viewModel.voiceActivity.observe(this) { activity ->
            binding.voiceWaveform.updateActivity(activity)
        }

        // Pose suggestions
        viewModel.poseSuggestions.observe(this) { suggestions ->
            if (suggestions.isNotEmpty()) {
                binding.coachingSuggestions.showSuggestions(suggestions)
                binding.coachingSuggestions.visibility = View.VISIBLE
            } else {
                binding.coachingSuggestions.visibility = View.GONE
            }
        }

        // Tool execution
        viewModel.toolExecution.observe(this) { execution ->
            if (execution.isActive) {
                binding.toolExecutionIndicator.show(execution)
            } else {
                binding.toolExecutionIndicator.hide()
            }
        }

        // Errors
        viewModel.error.observe(this) { error ->
            error?.let { showError(it) }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            initializeServices()
        }
    }

    private fun initializeServices() {
        // Start and bind Gemini Live Service
        val geminiIntent = Intent(this, GeminiLiveService::class.java)
        startService(geminiIntent)
        bindService(geminiIntent, geminiServiceConnection, Context.BIND_AUTO_CREATE)

        // Start and bind Pose Analysis Service
        val poseIntent = Intent(this, PoseAnalysisService::class.java)
        startService(poseIntent)
        bindService(poseIntent, poseServiceConnection, Context.BIND_AUTO_CREATE)

        // Initialize camera
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }

            // Image analysis for pose detection
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        poseAnalysisService?.analyzePose(imageProxy)
                    }
                }

            // Select back camera as default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

            } catch (exc: Exception) {
                showError("Failed to start camera: ${exc.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupGeminiServiceCallbacks() {
        geminiLiveService?.apply {
            setConnectionStatusCallback { status ->
                lifecycleScope.launch {
                    viewModel.updateConnectionStatus(status)
                }
            }

            setTranscriptionCallback { text ->
                lifecycleScope.launch {
                    viewModel.updateTranscription(text)
                }
            }

            setVoiceActivityCallback { activity ->
                lifecycleScope.launch {
                    viewModel.updateVoiceActivity(activity)
                }
            }

            setToolExecutionCallback { execution ->
                lifecycleScope.launch {
                    viewModel.updateToolExecution(execution)
                }
            }

            setSessionStateCallback { state ->
                lifecycleScope.launch {
                    viewModel.updateSessionState(state)
                }
            }

            setErrorCallback { error ->
                lifecycleScope.launch {
                    viewModel.setError(error)
                }
            }
        }
    }

    private fun setupPoseServiceCallbacks() {
        poseAnalysisService?.apply {
            setPoseDetectionCallback { poses ->
                lifecycleScope.launch {
                    binding.poseOverlay.updatePoses(poses)
                    // Send pose data to Gemini for analysis
                    geminiLiveService?.analyzePoseData(poses)
                }
            }

            setCoachingSuggestionsCallback { suggestions ->
                lifecycleScope.launch {
                    viewModel.updatePoseSuggestions(suggestions)
                }
            }
        }
    }

    // UI Event Handlers
    private fun startTalking() {
        geminiLiveService?.startVoiceInput()
        viewModel.startTalking()
    }

    private fun stopTalking() {
        geminiLiveService?.stopVoiceInput()
        viewModel.stopTalking()
    }

    private fun showVADSettings() {
        // TODO: Show VAD sensitivity settings dialog
        Toast.makeText(this, "VAD Settings", Toast.LENGTH_SHORT).show()
    }

    private fun showAudioDeviceSelector() {
        // TODO: Show audio device selection dialog
        Toast.makeText(this, "Audio Device Selection", Toast.LENGTH_SHORT).show()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun retryConnection() {
        geminiLiveService?.reconnect()
    }

    private fun handleSessionLimit() {
        Snackbar.make(
            binding.snackbarContainer,
            "Session limit reached. Starting new session...",
            Snackbar.LENGTH_LONG
        ).show()
        geminiLiveService?.restartSession()
    }

    private fun updateUIForConnectionStatus(status: ConnectionStatus) {
        when (status) {
            ConnectionStatus.DISCONNECTED -> {
                binding.pushToTalkButton.isEnabled = false
                binding.voiceWaveform.visibility = View.GONE
            }
            ConnectionStatus.CONNECTING -> {
                binding.pushToTalkButton.isEnabled = false
                binding.voiceWaveform.visibility = View.VISIBLE
            }
            ConnectionStatus.CONNECTED -> {
                binding.pushToTalkButton.isEnabled = true
                binding.voiceWaveform.visibility = View.VISIBLE
            }
            ConnectionStatus.RESUMING -> {
                binding.pushToTalkButton.isEnabled = false
                showResumingMessage()
            }
            ConnectionStatus.ERROR -> {
                binding.pushToTalkButton.isEnabled = false
                binding.voiceWaveform.visibility = View.GONE
            }
        }
    }

    private fun showResumingMessage() {
        Snackbar.make(
            binding.snackbarContainer,
            "Resuming session...",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showError(error: String) {
        Snackbar.make(
            binding.snackbarContainer,
            error,
            Snackbar.LENGTH_LONG
        ).setAction("Retry") {
            retryConnection()
        }.show()
    }

    private fun showPermissionError() {
        Snackbar.make(
            binding.snackbarContainer,
            "Camera and microphone permissions are required for live coaching",
            Snackbar.LENGTH_INDEFINITE
        ).setAction("Grant") {
            requestPermissions()
        }.show()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Unbind services
        if (isGeminiServiceBound) {
            unbindService(geminiServiceConnection)
            isGeminiServiceBound = false
        }

        if (isPoseServiceBound) {
            unbindService(poseServiceConnection)
            isPoseServiceBound = false
        }

        // Shutdown camera executor
        cameraExecutor.shutdown()
    }

    override fun onPause() {
        super.onPause()
        // Pause voice input when app goes to background
        geminiLiveService?.pauseVoiceInput()
    }

    override fun onResume() {
        super.onResume()
        // Resume voice input when app comes back to foreground
        geminiLiveService?.resumeVoiceInput()
    }
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RESUMING,
    ERROR
}