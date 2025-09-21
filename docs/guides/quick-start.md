# Pose Coach Android - Quick Start Guide

## Overview

This quick start guide will help you integrate the Pose Coach Android SDK into your application. You'll learn how to set up pose detection, get AI coaching suggestions, and implement privacy controls.

## Prerequisites

- Android Studio Arctic Fox or later
- Android API level 21 (Android 5.0) or higher
- Camera permissions in your AndroidManifest.xml
- Internet permission for AI suggestions (optional)

## Installation

### 1. Add Dependencies

Add the following to your app-level `build.gradle.kts`:

```kotlin
dependencies {
    // Pose Coach Core
    implementation("com.posecoach:core-pose:1.0.0")
    implementation("com.posecoach:core-geom:1.0.0")
    implementation("com.posecoach:suggestions-api:1.0.0")

    // CameraX for camera integration
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // MediaPipe for pose detection
    implementation("com.google.mediapipe:tasks-vision:0.10.5")

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Optional: Gemini AI for suggestions
    implementation("com.google.ai.client.generativeai:generativeai:0.1.2")
}
```

### 2. Add Permissions

Add required permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />

<!-- Optional: For improved performance -->
<uses-feature android:name="android.hardware.camera" android:required="true" />
<uses-feature android:name="android.hardware.camera.autofocus" />
```

## Basic Implementation

### 1. Set Up Camera Preview

Create a basic camera preview with pose detection:

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var poseRepository: MediaPipePoseRepository
    private lateinit var cameraProvider: ProcessCameraProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        poseRepository = MediaPipePoseRepository(this)

        // Check camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(
                    ContextCompat.getMainExecutor(this),
                    PoseAnalyzer(poseRepository) { result ->
                        // Handle pose detection result
                        handlePoseResult(result)
                    }
                )
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun handlePoseResult(result: PoseLandmarkResult?) {
        result?.let { poseResult ->
            // Update UI with pose landmarks
            runOnUiThread {
                updatePoseOverlay(poseResult.landmarks)
            }

            // Get AI suggestions (optional)
            if (shouldGetSuggestions(poseResult)) {
                getSuggestions(poseResult.landmarks)
            }
        }
    }

    companion object {
        private const val TAG = "PoseCoach"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
```

### 2. Create Pose Analyzer

Implement the pose analysis logic:

```kotlin
class PoseAnalyzer(
    private val poseRepository: MediaPipePoseRepository,
    private val onPoseDetected: (PoseLandmarkResult?) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            lifecycleScope.launch {
                val result = poseRepository.detectPose(
                    image = mediaImage,
                    rotationDegrees = imageProxy.imageInfo.rotationDegrees
                )
                onPoseDetected(result)
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }
}
```

### 3. Set Up AI Suggestions

Configure the suggestions client with privacy settings:

```kotlin
class SuggestionsManager(
    private val context: Context,
    private val apiKey: String? = null
) {
    private val suggestionsClient: PoseSuggestionClient by lazy {
        if (apiKey != null) {
            PrivacyAwareSuggestionsClient(
                delegate = GeminiPoseSuggestionClient(apiKey),
                privacySettings = PrivacySettings(
                    allowApiCalls = true,
                    anonymizeLandmarks = true,
                    requireExplicitConsent = false
                )
            )
        } else {
            FakePoseSuggestionClient() // Local fallback
        }
    }

    suspend fun getSuggestions(landmarks: List<PoseLandmark>): List<PoseSuggestion> {
        val landmarksData = PoseLandmarksData(
            landmarks = landmarks.map { landmark ->
                PoseLandmarksData.LandmarkPoint(
                    index = landmark.index,
                    x = landmark.x,
                    y = landmark.y,
                    z = landmark.z,
                    visibility = landmark.visibility,
                    presence = landmark.presence
                )
            }
        )

        return try {
            suggestionsClient.getPoseSuggestions(landmarksData)
                .getOrElse { emptyList() }
        } catch (e: Exception) {
            Log.e("SuggestionsManager", "Failed to get suggestions", e)
            emptyList()
        }
    }
}
```

### 4. Display Suggestions

Create a simple UI to display coaching suggestions:

```kotlin
class SuggestionsAdapter : RecyclerView.Adapter<SuggestionsAdapter.ViewHolder>() {
    private var suggestions: List<PoseSuggestion> = emptyList()

    fun updateSuggestions(newSuggestions: List<PoseSuggestion>) {
        suggestions = newSuggestions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suggestion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(suggestions[position])
    }

    override fun getItemCount(): Int = suggestions.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.titleText)
        private val instructionText: TextView = itemView.findViewById(R.id.instructionText)

        fun bind(suggestion: PoseSuggestion) {
            titleText.text = suggestion.title
            instructionText.text = suggestion.instruction
        }
    }
}
```

## Privacy Configuration

### 1. Set Up Privacy Settings

Configure privacy preferences based on user consent:

```kotlin
class PrivacyManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("privacy_prefs", Context.MODE_PRIVATE)

    fun getPrivacySettings(): PrivacySettings {
        return PrivacySettings(
            allowApiCalls = prefs.getBoolean("allow_api_calls", false),
            allowDataTransmission = prefs.getBoolean("allow_data_transmission", false),
            anonymizeLandmarks = prefs.getBoolean("anonymize_landmarks", true),
            limitDataPrecision = prefs.getBoolean("limit_data_precision", false),
            requireExplicitConsent = prefs.getBoolean("require_explicit_consent", true),
            localProcessingOnly = prefs.getBoolean("local_processing_only", false)
        )
    }

    fun updatePrivacySettings(settings: PrivacySettings) {
        prefs.edit()
            .putBoolean("allow_api_calls", settings.allowApiCalls)
            .putBoolean("allow_data_transmission", settings.allowDataTransmission)
            .putBoolean("anonymize_landmarks", settings.anonymizeLandmarks)
            .putBoolean("limit_data_precision", settings.limitDataPrecision)
            .putBoolean("require_explicit_consent", settings.requireExplicitConsent)
            .putBoolean("local_processing_only", settings.localProcessingOnly)
            .apply()
    }
}
```

### 2. Implement Consent Dialog

Create a consent dialog for privacy transparency:

```kotlin
class ConsentDialog : DialogFragment() {
    interface ConsentListener {
        fun onConsentGiven(settings: PrivacySettings)
        fun onConsentDenied()
    }

    private var listener: ConsentListener? = null

    fun setConsentListener(listener: ConsentListener) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle("Privacy Consent")
            .setMessage("""
                To provide personalized coaching suggestions, we can:

                • Analyze your pose data locally on your device
                • Send anonymized pose data to our AI service
                • Process suggestions entirely on your device

                You can change these settings anytime in Privacy Settings.
            """.trimIndent())
            .setPositiveButton("Allow AI Suggestions") { _, _ ->
                listener?.onConsentGiven(
                    PrivacySettings(
                        allowApiCalls = true,
                        allowDataTransmission = true,
                        anonymizeLandmarks = true
                    )
                )
            }
            .setNeutralButton("Local Only") { _, _ ->
                listener?.onConsentGiven(
                    PrivacySettings(
                        allowApiCalls = false,
                        localProcessingOnly = true
                    )
                )
            }
            .setNegativeButton("Deny") { _, _ ->
                listener?.onConsentDenied()
            }
            .create()
    }
}
```

## Layout Files

### activity_main.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.camera.view.PreviewView
        android:id="@+id/previewView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintBottom_toTopOf="@+id/suggestionsRecyclerView"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintVertical_weight="2" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/suggestionsRecyclerView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_margin="16dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/previewView"
        app:layout_constraintVertical_weight="1" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

### item_suggestion.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="8dp"
    app:cardCornerRadius="8dp"
    app:cardElevation="4dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <TextView
            android:id="@+id/titleText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="16sp"
            android:textStyle="bold"
            android:textColor="@android:color/black" />

        <TextView
            android:id="@+id/instructionText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textSize="14sp"
            android:textColor="@android:color/darker_gray" />

    </LinearLayout>

</androidx.cardview.widget.CardView>
```

## API Key Configuration

### 1. Add API Key (Optional)

If you want to use Gemini AI suggestions, add your API key to `local.properties`:

```properties
# local.properties
GEMINI_API_KEY=your_api_key_here
```

### 2. Access API Key in Code

```kotlin
class ApiKeyManager {
    companion object {
        fun getGeminiApiKey(): String? {
            return BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }
        }
    }
}
```

### 3. Update build.gradle.kts

```kotlin
android {
    defaultConfig {
        // Load API key from local.properties
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())

        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${properties.getProperty("GEMINI_API_KEY", "")}\""
        )
    }
}
```

## Testing Your Integration

### 1. Test Pose Detection

Verify pose detection is working:

```kotlin
// In your MainActivity
private fun testPoseDetection() {
    Log.d(TAG, "Pose detection started")
    // Point camera at a person and check logs for pose landmarks
}
```

### 2. Test Suggestions

Verify AI suggestions are working:

```kotlin
// In your SuggestionsManager
suspend fun testSuggestions() {
    val testLandmarks = createTestLandmarks()
    val suggestions = getSuggestions(testLandmarks)
    Log.d(TAG, "Got ${suggestions.size} suggestions")
}
```

### 3. Test Privacy Controls

Verify privacy settings are respected:

```kotlin
// Test local-only mode
val privacySettings = PrivacySettings(localProcessingOnly = true)
val client = PrivacyAwareSuggestionsClient(delegate, privacySettings)
// Should not make API calls
```

## Next Steps

1. **Performance Optimization**: See [Performance Guide](performance-optimization.md)
2. **Advanced Camera Setup**: See [CameraX Integration Guide](camerax-integration.md)
3. **Custom Overlays**: See [Overlay Customization Guide](overlay-customization.md)
4. **Privacy Implementation**: See [Privacy Implementation Guide](privacy-implementation.md)

## Troubleshooting

### Common Issues

**Camera not starting:**
- Check camera permissions
- Verify device has camera capability
- Check for conflicting camera usage

**Pose detection not working:**
- Ensure good lighting conditions
- Verify MediaPipe model is loaded
- Check device performance capabilities

**No AI suggestions:**
- Verify API key configuration
- Check internet connectivity
- Review privacy settings

**Performance issues:**
- Reduce camera resolution
- Adjust analysis frequency
- Enable GPU processing if available

### Getting Help

- Check the [API Documentation](../api/)
- Review [Common Issues](troubleshooting.md)
- Submit issues on [GitHub](https://github.com/posecoach/android/issues)

---

You now have a basic Pose Coach integration! The app will detect poses in real-time and provide AI-powered coaching suggestions while respecting user privacy preferences.