# Pose Coach Camera App - Production Architecture

## Executive Summary

This document defines the production architecture for the Pose Coach Camera app, designed for on-device first processing with MediaPipe, CameraX integration, privacy-first design, and structured output from Gemini 2.5. The architecture follows clean architecture principles with clear separation of concerns, optimized for real-time pose estimation and coaching.

## Architecture Overview

### High-Level Architecture Principles

1. **Privacy-First Design**: All pose processing happens on-device first
2. **Real-Time Performance**: Optimized for 30+ FPS camera processing
3. **Modular Design**: Clear separation between camera, pose estimation, and AI coaching
4. **Scalable Foundation**: Ready for future enhancements and features
5. **Security by Design**: Secure API management and data encryption

## 1. Layered Architecture Design

### 1.1 Presentation Layer (UI/UX)
```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                       │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ │
│ │   MainActivity  │ │  CameraActivity │ │ SettingsActivity│ │
│ │                 │ │                 │ │                 │ │
│ │ - App Entry     │ │ - Camera UI     │ │ - Privacy       │ │
│ │ - Navigation    │ │ - Overlay       │ │ - Preferences   │ │
│ │ - Permissions   │ │ - Controls      │ │ - API Settings  │ │
│ └─────────────────┘ └─────────────────┘ └─────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│                     UI COMPONENTS                           │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ │
│ │  CameraPreview  │ │   PoseOverlay   │ │ CoachingPanel   │ │
│ │                 │ │                 │ │                 │ │
│ │ - PreviewView   │ │ - Pose Points   │ │ - Suggestions   │ │
│ │ - Controls      │ │ - Skeleton      │ │ - Voice UI      │ │
│ │ - Status        │ │ - Animations    │ │ - Progress      │ │
│ └─────────────────┘ └─────────────────┘ └─────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

**Key Components:**
- **MainActivity**: App entry point, navigation, permission handling
- **CameraActivity**: Main camera interface with pose overlay
- **SettingsActivity**: Privacy settings, API configuration
- **CameraPreview**: CameraX preview component
- **PoseOverlay**: Real-time pose visualization
- **CoachingPanel**: AI suggestions display

### 1.2 Domain Layer (Business Logic)
```
┌─────────────────────────────────────────────────────────────┐
│                     DOMAIN LAYER                            │
├─────────────────────────────────────────────────────────────┤
│                    USE CASES                                │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ │
│ │ProcessPoseUseCase│ │GenerateSuggestion│ │ManagePrivacyUse │ │
│ │                 │ │UseCase          │ │Case             │ │
│ │ - Pose Detection│ │                 │ │                 │ │
│ │ - Stability     │ │ - Gemini API    │ │ - Consent Mgmt  │ │
│ │ - Validation    │ │ - Structured    │ │ - Data Control  │ │
│ │ - Analytics     │ │   Output        │ │ - Export/Delete │ │
│ └─────────────────┘ └─────────────────┘ └─────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│                    DOMAIN MODELS                           │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ │
│ │   PoseData      │ │  CoachingSuggestion │ │ PrivacySettings │ │
│ │                 │ │                 │ │                 │ │
│ │ - Landmarks     │ │ - Type          │ │ - Consent Level │ │
│ │ - Confidence    │ │ - Message       │ │ - Data Retention│ │
│ │ - Timestamp     │ │ - Priority      │ │ - API Usage     │ │
│ │ - Stability     │ │ - Actions       │ │ - Local Storage │ │
│ └─────────────────┘ └─────────────────┘ └─────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

**Key Components:**
- **ProcessPoseUseCase**: Core pose processing logic
- **GenerateSuggestionUseCase**: AI coaching logic
- **ManagePrivacyUseCase**: Privacy and consent management
- **Domain Models**: Pure business objects without dependencies

### 1.3 Data Layer (Repositories & Data Sources)
```
┌─────────────────────────────────────────────────────────────┐
│                      DATA LAYER                             │
├─────────────────────────────────────────────────────────────┤
│                   REPOSITORIES                              │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ │
│ │  PoseRepository │ │SuggestionRepo   │ │PrivacyRepository│ │
│ │                 │ │                 │ │                 │ │
│ │ - Local Cache   │ │ - API Client    │ │ - Preferences   │ │
│ │ - Processing    │ │ - Rate Limiting │ │ - Encryption    │ │
│ │ - Aggregation   │ │ - Fallback      │ │ - Audit Logs    │ │
│ └─────────────────┘ └─────────────────┘ └─────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│                   DATA SOURCES                              │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ │
│ │ MediaPipeSource │ │  GeminiAPISource│ │LocalStorageSource│ │
│ │                 │ │                 │ │                 │ │
│ │ - Pose Detection│ │ - Structured    │ │ - SQLite        │ │
│ │ - Face Detection│ │   Output        │ │ - Preferences   │ │
│ │ - Hand Tracking │ │ - Voice API     │ │ - Cache         │ │
│ └─────────────────┘ └─────────────────┘ └─────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

**Key Components:**
- **PoseRepository**: Manages pose data and processing
- **SuggestionRepository**: Handles AI API integration
- **PrivacyRepository**: Manages user consent and data control
- **Data Sources**: External integrations (MediaPipe, Gemini, Storage)

## 2. Component Architecture

### 2.1 CameraX Pipeline Components

```kotlin
// Core CameraX Architecture
┌─────────────────────────────────────────────────────────────┐
│                  CAMERAX PIPELINE                           │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐    ┌─────────────────┐    ┌──────────┐  │
│ │  CameraProvider │────│   Preview       │────│PreviewView│ │
│ │                 │    │   UseCase       │    │          │  │
│ └─────────────────┘    └─────────────────┘    └──────────┘  │
│          │                       │                          │
│          │              ┌─────────────────┐                 │
│          └──────────────│ ImageAnalysis   │                 │
│                         │ UseCase         │                 │
│                         │                 │                 │
│                         │ - MediaPipe     │                 │
│                         │ - Pose Detection│                 │
│                         │ - Frame Rate    │                 │
│                         │   Management    │                 │
│                         └─────────────────┘                 │
└─────────────────────────────────────────────────────────────┘
```

**Implementation:**
```kotlin
class CameraXManager @Inject constructor(
    private val context: Context,
    private val poseProcessor: PoseProcessor,
    private val performanceMonitor: CameraPerformanceMonitor
) {

    private lateinit var cameraProvider: ProcessCameraProvider
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null

    suspend fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        this.cameraProvider = cameraProvider

        // Configure preview
        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetRotation(previewView.display.rotation)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        // Configure image analysis for pose detection
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetRotation(previewView.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also {
                it.setAnalyzer(
                    ContextCompat.getMainExecutor(context),
                    PoseImageAnalyzer(poseProcessor, performanceMonitor)
                )
            }

        // Bind use cases to camera
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }
}
```

### 2.2 MediaPipe Integration Module

```kotlin
// MediaPipe Integration Architecture
┌─────────────────────────────────────────────────────────────┐
│                MEDIAPIPE INTEGRATION                        │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐    ┌─────────────────┐    ┌──────────┐  │
│ │ PoseProcessor   │────│MediaPipeManager │────│MPPoseNet │  │
│ │                 │    │                 │    │          │  │
│ │ - Frame Queue   │    │ - Model Loading │    │ - Model  │  │
│ │ - Thread Mgmt   │    │ - GPU Delegate  │    │ - GPU    │  │
│ │ - Result Cache  │    │ - Memory Mgmt   │    │ - NPU    │  │
│ └─────────────────┘    └─────────────────┘    └──────────┘  │
│          │                       │                          │
│          │              ┌─────────────────┐                 │
│          └──────────────│ StabilityGate   │                 │
│                         │                 │                 │
│                         │ - Confidence    │                 │
│                         │ - Temporal      │                 │
│                         │ - Geometric     │                 │
│                         │ - Validation    │                 │
│                         └─────────────────┘                 │
└─────────────────────────────────────────────────────────────┘
```

**Implementation:**
```kotlin
class MediaPipeManager @Inject constructor(
    private val context: Context,
    private val stabilityGate: StabilityGate
) {

    private lateinit var poseDetector: PoseLandmarker
    private val executorService = Executors.newSingleThreadExecutor()

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker.task")
                .setDelegate(BaseOptions.Delegate.GPU) // Prefer GPU
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener(::handlePoseResult)
                .setErrorListener(::handlePoseError)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            poseDetector = PoseLandmarker.createFromOptions(context, options)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun processPose(imageProxy: ImageProxy, timestampMs: Long): Job {
        return CoroutineScope(Dispatchers.Main).launch {
            val mpImage = imageProxy.toMPImage()
            poseDetector.detectAsync(mpImage, timestampMs)
        }
    }

    private fun handlePoseResult(
        result: PoseLandmarkerResult,
        input: MPImage,
        timestampMs: Long
    ) {
        val poseLandmarks = result.landmarks().firstOrNull()
        poseLandmarks?.let { landmarks ->
            val poseData = PoseData(
                landmarks = landmarks.map { it.toPosePoint() },
                confidence = result.worldLandmarks().firstOrNull()?.averageConfidence() ?: 0f,
                timestamp = timestampMs,
                imageSize = Size(input.width, input.height)
            )

            // Apply stability gate
            stabilityGate.process(poseData)
        }
    }
}
```

### 2.3 Overlay Rendering System

```kotlin
// Pose Overlay Architecture
┌─────────────────────────────────────────────────────────────┐
│                   OVERLAY SYSTEM                            │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐    ┌─────────────────┐    ┌──────────┐  │
│ │  OverlayView    │────│  PoseRenderer   │────│Animation │  │
│ │                 │    │                 │    │Engine    │  │
│ │ - Custom View   │    │ - Skeleton      │    │          │  │
│ │ - Canvas API    │    │ - Landmarks     │    │ - Smooth │  │
│ │ - Performance   │    │ - Connections   │    │ - Easing │  │
│ └─────────────────┘    └─────────────────┘    └──────────┘  │
│          │                       │                          │
│          │              ┌─────────────────┐                 │
│          └──────────────│CoordinateMapper │                 │
│                         │                 │                 │
│                         │ - Camera Space  │                 │
│                         │ - Screen Space  │                 │
│                         │ - Rotation      │                 │
│                         │ - Scaling       │                 │
│                         └─────────────────┘                 │
└─────────────────────────────────────────────────────────────┘
```

### 2.4 Gemini API Client with Structured Output

```kotlin
// Gemini API Integration Architecture
┌─────────────────────────────────────────────────────────────┐
│                 GEMINI API CLIENT                           │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐    ┌─────────────────┐    ┌──────────┐  │
│ │ GeminiClient    │────│PromptBuilder    │────│Structured│  │
│ │                 │    │                 │    │Output    │  │
│ │ - Rate Limiting │    │ - Pose Context  │    │Parser    │  │
│ │ - Retry Logic   │    │ - User Profile  │    │          │  │
│ │ - Error Handling│    │ - Exercise Type │    │ - JSON   │  │
│ └─────────────────┘    └─────────────────┘    └──────────┘  │
│          │                       │                          │
│          │              ┌─────────────────┐                 │
│          └──────────────│ VoiceAPIClient  │                 │
│                         │                 │                 │
│                         │ - TTS/STT       │                 │
│                         │ - Live API      │                 │
│                         │ - Audio Stream  │                 │
│                         │ - Conversation  │                 │
│                         └─────────────────┘                 │
└─────────────────────────────────────────────────────────────┘
```

**Implementation:**
```kotlin
data class CoachingSuggestion(
    val type: SuggestionType,
    val title: String,
    val message: String,
    val priority: Priority,
    val actions: List<SuggestionAction>,
    val confidence: Float,
    val targetBodyPart: BodyPart?
)

class GeminiAPIClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val apiKeyManager: APIKeyManager,
    private val rateLimiter: RateLimiter
) {

    suspend fun generateCoachingSuggestion(
        poseData: PoseData,
        exerciseContext: ExerciseContext,
        userProfile: UserProfile
    ): Result<CoachingSuggestion> = withContext(Dispatchers.IO) {

        // Rate limiting
        rateLimiter.acquire()

        val prompt = PromptBuilder()
            .withPoseData(poseData)
            .withExerciseContext(exerciseContext)
            .withUserProfile(userProfile)
            .withStructuredOutputSchema()
            .build()

        try {
            val request = createGeminiRequest(prompt)
            val response = httpClient.newCall(request).await()

            if (response.isSuccessful) {
                val suggestion = parseStructuredOutput(response.body!!.string())
                Result.success(suggestion)
            } else {
                Result.failure(APIException("API call failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseStructuredOutput(responseBody: String): CoachingSuggestion {
        // Parse JSON response using structured output schema
        val jsonObject = Gson().fromJson(responseBody, JsonObject::class.java)
        val content = jsonObject.getAsJsonObject("candidates")
            .getAsJsonArray("content")
            .get(0).asJsonObject
            .getAsJsonObject("parts")
            .get(0).asJsonObject
            .get("text").asString

        return Gson().fromJson(content, CoachingSuggestion::class.java)
    }
}
```

### 2.5 Privacy and Consent Management

```kotlin
// Privacy Management Architecture
┌─────────────────────────────────────────────────────────────┐
│                PRIVACY MANAGEMENT                           │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐    ┌─────────────────┐    ┌──────────┐  │
│ │ConsentManager   │────│PrivacyPolicy    │────│DataAudit │  │
│ │                 │    │Engine           │    │Logger    │  │
│ │ - User Consent  │    │                 │    │          │  │
│ │ - Granular      │    │ - Data Flow     │    │ - Access │  │
│ │   Controls      │    │ - Retention     │    │ - Changes│  │
│ │ - Opt-out       │    │ - Export/Delete │    │ - Usage  │  │
│ └─────────────────┘    └─────────────────┘    └──────────┘  │
│          │                       │                          │
│          │              ┌─────────────────┐                 │
│          └──────────────│EncryptedStorage │                 │
│                         │                 │                 │
│                         │ - Local Data    │                 │
│                         │ - API Keys      │                 │
│                         │ - User Prefs    │                 │
│                         │ - AES-256       │                 │
│                         └─────────────────┘                 │
└─────────────────────────────────────────────────────────────┘
```

## 3. Data Flow Architecture

### 3.1 Camera → MediaPipe → Processing Pipeline

```
Camera Frame → ImageProxy → MediaPipe → PoseData → StabilityGate → Domain Layer
     │               │           │          │            │             │
     └── 30 FPS ──── YUV ──── GPU ──── Landmarks ──── Validation ── UseCase
```

**Data Flow Steps:**
1. **Camera Capture**: CameraX captures frames at 30 FPS
2. **Image Conversion**: ImageProxy converted to MediaPipe format
3. **Pose Detection**: MediaPipe processes frame on GPU/NPU
4. **Stability Validation**: Multiple confidence checks applied
5. **Domain Processing**: Clean pose data passed to business logic

### 3.2 Pose Data → Stability Gate → Gemini Suggestions

```
PoseData → ConfidenceCheck → TemporalSmoothing → GeometricValidation → CoachingEngine
    │            │                  │                    │                  │
    └── Raw ─── Filter ─── Smooth ─── Validate ─── Generate Suggestions
```

**Stability Gate Implementation:**
```kotlin
class StabilityGate @Inject constructor(
    private val config: StabilityConfig
) {
    private val poseHistory = CircularBuffer<PoseData>(size = 10)
    private val smoothingFilter = KalmanFilter()

    fun process(poseData: PoseData): PoseData? {
        // 1. Confidence threshold check
        if (poseData.confidence < config.minConfidence) return null

        // 2. Add to history for temporal analysis
        poseHistory.add(poseData)

        // 3. Temporal smoothing
        val smoothedPose = if (poseHistory.size >= 3) {
            smoothingFilter.apply(poseData, poseHistory.recent(3))
        } else {
            poseData
        }

        // 4. Geometric validation
        val isGeometricallyValid = validatePoseGeometry(smoothedPose)

        return if (isGeometricallyValid) smoothedPose else null
    }
}
```

### 3.3 Privacy-Aware Data Routing

```
User Data → ConsentCheck → LocalProcessing → OptionalAPI → EncryptedStorage
    │           │               │               │              │
    └── Input ── Permission ── OnDevice ── UserChoice ── SecureStore
```

## 4. Performance Architecture

### 4.1 Memory Management Strategies

```kotlin
// Memory Management Architecture
┌─────────────────────────────────────────────────────────────┐
│                MEMORY MANAGEMENT                            │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐    ┌─────────────────┐    ┌──────────┐  │
│ │ObjectPoolManager│────│FrameRecycler    │────│GCMonitor │  │
│ │                 │    │                 │    │          │  │
│ │ - Bitmap Pool   │    │ - ImageProxy    │    │ - Heap   │  │
│ │ - Array Pool    │    │ - Buffer Reuse  │    │ - Timing │  │
│ │ - Model Cache   │    │ - Memory Maps   │    │ - Alerts │  │
│ └─────────────────┘    └─────────────────┘    └──────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**Implementation:**
```kotlin
class MemoryManager @Inject constructor() {

    private val bitmapPool = BitmapPool(maxSize = 50 * 1024 * 1024) // 50MB
    private val arrayPool = ArrayPool<ByteArray>()

    fun getBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        return bitmapPool.get(width, height, config)
            ?: Bitmap.createBitmap(width, height, config)
    }

    fun recycleBitmap(bitmap: Bitmap) {
        if (bitmap.isRecycled.not()) {
            bitmapPool.put(bitmap)
        }
    }

    fun getByteArray(size: Int): ByteArray {
        return arrayPool.get(size) ?: ByteArray(size)
    }

    fun recycleByteArray(array: ByteArray) {
        arrayPool.put(array)
    }
}
```

### 4.2 Thread Management for Real-Time Processing

```kotlin
// Thread Architecture
┌─────────────────────────────────────────────────────────────┐
│                 THREAD MANAGEMENT                           │
├─────────────────────────────────────────────────────────────┤
│ Main Thread     │ Camera Thread   │ ML Thread      │ API    │
│                 │                 │                │ Thread │
│ ┌─────────────┐ │ ┌─────────────┐ │ ┌────────────┐ │ ┌────┐ │
│ │ UI Updates  │ │ │ Frame       │ │ │ MediaPipe  │ │ │API │ │
│ │ Overlay     │ │ │ Capture     │ │ │ Processing │ │ │Call│ │
│ │ Rendering   │ │ │ Preview     │ │ │ Inference  │ │ │    │ │
│ └─────────────┘ │ └─────────────┘ │ └────────────┘ │ └────┘ │
└─────────────────────────────────────────────────────────────┘
```

**Implementation:**
```kotlin
@Singleton
class ThreadManager @Inject constructor() {

    val mainExecutor = ContextCompat.getMainExecutor(context)

    val cameraExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "CameraX-Thread").apply {
            priority = Thread.MAX_PRIORITY
        }
    }

    val mlExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "MediaPipe-Thread").apply {
            priority = Thread.NORM_PRIORITY + 1
        }
    }

    val apiExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "API-Thread").apply {
            priority = Thread.NORM_PRIORITY
        }
    }
}
```

### 4.3 Caching Strategies for Suggestions

```kotlin
class SuggestionCache @Inject constructor(
    private val encryptedStorage: EncryptedStorage
) {

    private val memoryCache = LruCache<String, CoachingSuggestion>(100)
    private val diskCache = DiskLruCache.open(cacheDir, 1, 1, 10 * 1024 * 1024)

    suspend fun getSuggestion(key: String): CoachingSuggestion? {
        // 1. Check memory cache first
        memoryCache.get(key)?.let { return it }

        // 2. Check disk cache
        return withContext(Dispatchers.IO) {
            diskCache.get(key)?.let { snapshot ->
                val suggestion = Json.decodeFromString<CoachingSuggestion>(
                    snapshot.getInputStream(0).readText()
                )
                memoryCache.put(key, suggestion)
                suggestion
            }
        }
    }

    suspend fun putSuggestion(key: String, suggestion: CoachingSuggestion) {
        memoryCache.put(key, suggestion)

        withContext(Dispatchers.IO) {
            val editor = diskCache.edit(key)
            editor?.let {
                it.newOutputStream(0).writer().use { writer ->
                    writer.write(Json.encodeToString(suggestion))
                }
                it.commit()
            }
        }
    }
}
```

### 4.4 Battery Optimization Techniques

```kotlin
class BatteryOptimizer @Inject constructor(
    private val powerManager: PowerManager,
    private val performanceMonitor: PerformanceMonitor
) {

    private var isLowPowerMode = false

    fun adaptToDeviceState() {
        val batteryLevel = getBatteryLevel()
        val thermalState = getThermalState()

        when {
            batteryLevel < 20 || thermalState == THERMAL_STATE_CRITICAL -> {
                enableAggressiveOptimization()
            }
            batteryLevel < 50 || thermalState == THERMAL_STATE_MODERATE -> {
                enableModerateOptimization()
            }
            else -> {
                enableFullPerformance()
            }
        }
    }

    private fun enableAggressiveOptimization() {
        isLowPowerMode = true
        // Reduce frame rate to 15 FPS
        // Use CPU instead of GPU for inference
        // Disable non-essential features
        // Increase batching intervals
    }

    private fun enableModerateOptimization() {
        isLowPowerMode = false
        // Reduce frame rate to 20 FPS
        // Use GPU but with reduced precision
        // Cache more aggressively
    }

    private fun enableFullPerformance() {
        isLowPowerMode = false
        // Full 30 FPS
        // GPU with full precision
        // Real-time processing
    }
}
```

## 5. Security Architecture

### 5.1 API Key Management

```kotlin
// API Key Security Architecture
┌─────────────────────────────────────────────────────────────┐
│                API KEY MANAGEMENT                           │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐    ┌─────────────────┐    ┌──────────┐  │
│ │ KeyManager      │────│EncryptedPrefs  │────│KeyStore  │  │
│ │                 │    │                 │    │          │  │
│ │ - Key Rotation  │    │ - AES-256       │    │ - TEE    │  │
│ │ - Validation    │    │ - Salt/IV       │    │ - HSM    │  │
│ │ - Audit Logs    │    │ - Local Only    │    │ - Biometric│  │
│ └─────────────────┘    └─────────────────┘    └──────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**Implementation:**
```kotlin
@Singleton
class APIKeyManager @Inject constructor(
    private val encryptedPrefs: EncryptedSharedPreferences,
    private val keyStore: AndroidKeyStore
) {

    private companion object {
        const val GEMINI_API_KEY = "gemini_api_key"
        const val KEY_ALIAS = "pose_coach_key"
    }

    suspend fun setAPIKey(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Validate key format
            if (!isValidAPIKey(key)) {
                return@withContext Result.failure(InvalidKeyException())
            }

            // Encrypt and store
            val encryptedKey = keyStore.encrypt(KEY_ALIAS, key.toByteArray())
            encryptedPrefs.edit()
                .putString(GEMINI_API_KEY, Base64.encodeToString(encryptedKey, Base64.DEFAULT))
                .apply()

            // Log audit event (without key value)
            auditLogger.logKeyUpdate("API key updated", getCurrentUserId())

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAPIKey(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val encryptedKey = encryptedPrefs.getString(GEMINI_API_KEY, null)
                ?: return@withContext Result.failure(NoKeyException())

            val decryptedBytes = keyStore.decrypt(
                KEY_ALIAS,
                Base64.decode(encryptedKey, Base64.DEFAULT)
            )

            Result.success(String(decryptedBytes))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isValidAPIKey(key: String): Boolean {
        return key.matches(Regex("^AIza[0-9A-Za-z-_]{35}$"))
    }
}
```

### 5.2 Data Encryption for Sensitive Information

```kotlin
class DataEncryption @Inject constructor(
    private val keyStore: AndroidKeyStore
) {

    suspend fun encryptPoseData(poseData: PoseData): EncryptedPoseData {
        val serializedData = Json.encodeToString(poseData)
        val encryptedBytes = keyStore.encrypt("pose_data_key", serializedData.toByteArray())

        return EncryptedPoseData(
            data = Base64.encodeToString(encryptedBytes, Base64.DEFAULT),
            timestamp = System.currentTimeMillis(),
            userId = getCurrentUserId()
        )
    }

    suspend fun decryptPoseData(encryptedData: EncryptedPoseData): PoseData {
        val encryptedBytes = Base64.decode(encryptedData.data, Base64.DEFAULT)
        val decryptedBytes = keyStore.decrypt("pose_data_key", encryptedBytes)
        val serializedData = String(decryptedBytes)

        return Json.decodeFromString<PoseData>(serializedData)
    }
}
```

### 5.3 Secure Communication Channels

```kotlin
class SecureAPIClient @Inject constructor(
    private val certificatePinner: CertificatePinner,
    private val apiKeyManager: APIKeyManager
) {

    private val httpClient = OkHttpClient.Builder()
        .certificatePinner(certificatePinner)
        .connectionSpecs(listOf(
            ConnectionSpec.MODERN_TLS,
            ConnectionSpec.COMPATIBLE_TLS
        ))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .addInterceptor(AuthInterceptor(apiKeyManager))
        .addInterceptor(LoggingInterceptor())
        .build()

    suspend fun makeSecureRequest(request: APIRequest): Result<APIResponse> {
        return try {
            val httpRequest = request.toHttpRequest()
            val response = httpClient.newCall(httpRequest).await()

            if (response.isSuccessful) {
                Result.success(APIResponse.fromHttpResponse(response))
            } else {
                Result.failure(APIException("Request failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### 5.4 Privacy-Preserving Analytics

```kotlin
class PrivacyPreservingAnalytics @Inject constructor(
    private val consentManager: ConsentManager,
    private val dataMinimizer: DataMinimizer
) {

    suspend fun trackPoseSession(session: PoseSession) {
        if (!consentManager.hasAnalyticsConsent()) return

        val minimizedData = dataMinimizer.minimize(session)
        val anonymizedData = anonymizeData(minimizedData)

        // Local analytics only
        localAnalyticsDB.insert(anonymizedData)
    }

    private fun anonymizeData(data: MinimizedPoseSession): AnonymizedPoseSession {
        return AnonymizedPoseSession(
            sessionId = generateRandomId(),
            duration = data.duration,
            exerciseType = data.exerciseType,
            improvementScore = data.improvementScore,
            deviceType = "android", // Generic device type only
            timestamp = roundToHour(data.timestamp) // Reduce precision
        )
    }
}
```

## Architecture Decision Records (ADRs)

### ADR-001: On-Device First Processing
**Decision**: Use MediaPipe for on-device pose estimation as the primary processing method.

**Rationale**:
- Privacy: No pose data leaves device unless explicitly consented
- Performance: Reduces latency and network dependency
- Cost: No per-request API costs for basic pose detection
- Reliability: Works offline

**Consequences**:
- Increased app size due to ML models
- Higher device computational requirements
- Need for fallback mechanisms

### ADR-002: CameraX for Camera Management
**Decision**: Use CameraX instead of Camera2 API directly.

**Rationale**:
- Consistency across Android versions
- Built-in lifecycle management
- Simplified preview and analysis use cases
- Better testing support

**Consequences**:
- Additional dependency
- Some advanced camera features may be abstracted
- Learning curve for team

### ADR-003: Structured Output from Gemini
**Decision**: Use Gemini's structured output capabilities for coaching suggestions.

**Rationale**:
- Consistent response format
- Easier parsing and validation
- Type safety in responses
- Better error handling

**Consequences**:
- Dependency on Gemini's specific structured output format
- Need for fallback parsing mechanisms
- Potential API changes impact

### ADR-004: Privacy-First Architecture
**Decision**: Implement granular consent management with local-first data processing.

**Rationale**:
- Regulatory compliance (GDPR, CCPA)
- User trust and transparency
- Competitive advantage
- Ethical responsibility

**Consequences**:
- Increased complexity in data flow
- Additional UI for consent management
- Need for audit trails
- Potential feature limitations

## Technology Evaluation Matrix

| Technology | Pros | Cons | Decision |
|------------|------|------|----------|
| **MediaPipe** | On-device, fast, accurate | Model size, battery usage | ✅ Chosen |
| **TensorFlow Lite** | Flexible, custom models | Complexity, setup time | ❌ Rejected |
| **ML Kit** | Easy integration, Google | Limited customization | ❌ Rejected |
| **CameraX** | Modern, consistent | Newer, some limitations | ✅ Chosen |
| **Camera2** | Full control, performance | Complexity, fragmentation | ❌ Rejected |
| **Gemini API** | Advanced AI, structured output | Cost, network dependency | ✅ Chosen |
| **OpenAI API** | Powerful, flexible | Cost, no structured output | ❌ Rejected |

## Deployment Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  DEPLOYMENT PIPELINE                        │
├─────────────────────────────────────────────────────────────┤
│ Development → Testing → Staging → Production                │
│      │           │        │           │                     │
│   ┌─────┐    ┌─────┐   ┌─────┐    ┌─────┐                   │
│   │Local│    │CI/CD│   │Alpha│    │Play │                   │
│   │Dev  │    │Tests│   │Test │    │Store│                   │
│   └─────┘    └─────┘   └─────┘    └─────┘                   │
└─────────────────────────────────────────────────────────────┘
```

## Monitoring and Analytics Architecture

```
┌─────────────────────────────────────────────────────────────┐
│               MONITORING & ANALYTICS                        │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐    ┌─────────────────┐    ┌──────────┐  │
│ │PerformanceMonitor│───│ CrashReporter   │────│Analytics │  │
│ │                 │    │                 │    │Dashboard │  │
│ │ - Frame Rate    │    │ - Stack Traces  │    │          │  │
│ │ - Memory Usage  │    │ - Error Context │    │ - KPIs   │  │
│ │ - Battery       │    │ - User Actions  │    │ - Trends │  │
│ │ - API Latency   │    │ - Device Info   │    │ - Alerts │  │
│ └─────────────────┘    └─────────────────┘    └──────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## Conclusion

This production architecture provides a robust, scalable foundation for the Pose Coach Camera app with the following key benefits:

1. **Privacy-First Design**: All pose processing happens on-device with explicit user consent for any cloud features
2. **High Performance**: Optimized for real-time processing with efficient memory and battery management
3. **Modular Structure**: Clean separation of concerns enables easy testing and future enhancements
4. **Security**: Comprehensive security measures including encrypted storage and secure API communication
5. **Scalability**: Architecture supports future features like multiple exercise types, social features, and advanced analytics

The architecture aligns with modern Android development best practices while maintaining the specific requirements for pose estimation, privacy, and AI integration.