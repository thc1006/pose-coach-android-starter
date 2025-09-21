# Pose Coach Android - Comprehensive Development Guide

## 🎯 Executive Summary

This comprehensive development guide provides detailed implementation instructions for the Pose Coach Android application based on analyzed documentation sources including CameraX, MediaPipe Pose Landmarker, Gemini 2.5 Structured Output, Gemini Live API, and testing frameworks (Truth, JUnit 5). The guide ensures correct implementation according to all analyzed specifications.

## 📋 Table of Contents

1. [Technical Requirements Summary](#technical-requirements-summary)
2. [Implementation Guidelines](#implementation-guidelines)
3. [Testing Strategy](#testing-strategy)
4. [API Integration Specifications](#api-integration-specifications)
5. [Development Roadmap](#development-roadmap)

---

## 1. Technical Requirements Summary

### 1.1 Core Architecture Requirements

#### Multi-Module Android Project Structure
```
pose-coach-android-starter/
├── app/                    # Main Android application module
├── core-geom/             # Geometry utilities and One Euro Filter
├── core-pose/             # MediaPipe pose detection core
├── suggestions-api/       # Gemini API integration
└── tests/                 # Comprehensive testing infrastructure
```

#### Platform Specifications
- **Minimum Android Version**: API 24 (Android 7.0)
- **Target Android Version**: API 34 (Android 14)
- **Compile SDK**: 34
- **Java Version**: Java 17
- **Kotlin Version**: 1.9.20

### 1.2 Performance Targets and Quality Metrics

#### Real-time Performance Requirements
- **Pose Inference Time**: <30ms (95th percentile)
- **Camera Frame Rate**: 30 FPS target, 15 FPS minimum
- **UI Responsiveness**: 60 FPS overlay rendering
- **Memory Usage**: <200MB peak during operation
- **Battery Consumption**: <20% drain per hour during active use

#### Quality Assurance Targets
- **Test Coverage**: >80% line coverage across all modules
- **Security Score**: Zero critical vulnerabilities
- **Accessibility**: WCAG 2.1 AA compliance
- **Privacy Compliance**: 100% GDPR/CCPA requirements

### 1.3 API Requirements and Constraints

#### MediaPipe Pose Landmarker Specifications
- **Model Version**: MediaPipe 0.10.9+
- **Detection Mode**: LIVE_STREAM for real-time processing
- **Landmark Count**: 33 pose landmarks with confidence scores
- **Processing Quality**: Adaptive based on device capabilities
- **GPU Acceleration**: Required with CPU fallback

#### Gemini 2.5 Structured Output Requirements
- **Model**: `gemini-2.0-flash-exp` with structured output support
- **Response Schema**: Enforced JSON structure with exactly 3 suggestions
- **Temperature**: 0.7 for creative but consistent suggestions
- **Max Tokens**: 1024 for detailed instructions
- **Timeout**: 10 seconds with fallback to local processing

#### CameraX Configuration Guidelines
- **Version**: CameraX 1.3.1+
- **Use Cases**: PreviewView + ImageAnalysis pipeline
- **Camera Selection**: Front camera preferred for pose coaching
- **Resolution**: 640x480 for optimal performance/quality balance
- **Orientation**: All orientations with proper coordinate transformation

---

## 2. Implementation Guidelines

### 2.1 MediaPipe Integration Implementation

#### Step-by-Step Integration Instructions

**Step 1: MediaPipe Pose Detector Setup**
```kotlin
class MediaPipePoseDetector(
    private val context: Context
) {
    companion object {
        private const val POSE_LANDMARKER_TASK = "pose_landmarker.task"
        private const val DELEGATE_CPU = 0
        private const val DELEGATE_GPU = 1
    }

    private var poseLandmarker: PoseLandmarker? = null
    private var listener: DetectionListener? = null

    // Configuration following MediaPipe specifications
    data class DetectorConfig(
        val runningMode: RunningMode = RunningMode.LIVE_STREAM,
        val numPoses: Int = 1,
        val minPoseDetectionConfidence: Float = 0.5f,
        val minPosePresenceConfidence: Float = 0.5f,
        val minTrackingConfidence: Float = 0.5f,
        val enableOutputSegmentationMasks: Boolean = false,
        val delegate: Int = DELEGATE_GPU
    )

    suspend fun initialize(config: DetectorConfig = DetectorConfig()) {
        val baseOptions = BaseOptions.builder()
            .setDelegate(
                if (config.delegate == DELEGATE_GPU) BaseOptions.Delegate.GPU
                else BaseOptions.Delegate.CPU
            )
            .setModelAssetPath(POSE_LANDMARKER_TASK)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(config.runningMode)
            .setNumPoses(config.numPoses)
            .setMinPoseDetectionConfidence(config.minPoseDetectionConfidence)
            .setMinPosePresenceConfidence(config.minPosePresenceConfidence)
            .setMinTrackingConfidence(config.minTrackingConfidence)
            .setOutputSegmentationMasks(config.enableOutputSegmentationMasks)
            .setResultListener { result, image ->
                handleDetectionResult(result, image)
            }
            .setErrorListener { error ->
                handleDetectionError(error)
            }
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(context, options)
    }

    // Performance-optimized detection following specifications
    fun detectPoseAsync(image: MPImage, timestampMs: Long) {
        poseLandmarker?.detectAsync(image, timestampMs)
    }

    private fun handleDetectionResult(
        result: PoseLandmarkerResult,
        image: MPImage
    ) {
        val landmarks = result.landmarks()
        if (landmarks.isNotEmpty()) {
            val firstPersonLandmarks = landmarks[0]
            listener?.onPoseDetected(
                PoseLandmarkResult(
                    landmarks = firstPersonLandmarks,
                    worldLandmarks = result.worldLandmarks().firstOrNull(),
                    timestamp = System.currentTimeMillis(),
                    imageWidth = image.width,
                    imageHeight = image.height
                )
            )
        }
    }

    interface DetectionListener {
        fun onPoseDetected(result: PoseLandmarkResult)
        fun onDetectionError(error: RuntimeException)
    }
}
```

**Step 2: Performance Optimization Techniques**
```kotlin
class PerformanceOptimizedPoseGate {
    private val oneEuroFilters = mutableMapOf<Int, OneEuroFilter>()
    private val performanceTracker = PerformanceTracker()

    fun processLandmarks(landmarks: List<NormalizedLandmark>): List<NormalizedLandmark> {
        val startTime = System.nanoTime()

        val smoothedLandmarks = landmarks.mapIndexed { index, landmark ->
            val filter = oneEuroFilters.getOrPut(index) {
                OneEuroFilter(minCutoff = 1.0, beta = 0.1, dCutoff = 1.0)
            }

            val timestamp = System.currentTimeMillis() / 1000.0

            NormalizedLandmark.newBuilder()
                .setX(filter.filter(landmark.x().toDouble(), timestamp).toFloat())
                .setY(filter.filter(landmark.y().toDouble(), timestamp).toFloat())
                .setZ(filter.filter(landmark.z().toDouble(), timestamp).toFloat())
                .setVisibility(landmark.visibility())
                .build()
        }

        val processingTime = (System.nanoTime() - startTime) / 1_000_000
        performanceTracker.recordProcessingTime(processingTime)

        return smoothedLandmarks
    }
}
```

### 2.2 CameraX Implementation Guidelines

#### Step-by-Step Camera Integration

**Step 1: CameraX Manager Implementation**
```kotlin
class CameraXManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null

    companion object {
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }

    suspend fun initializeCamera(
        previewView: PreviewView,
        aspectRatio: Int = AspectRatio.RATIO_4_3,
        lensFacing: Int = CameraSelector.LENS_FACING_FRONT
    ) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        this.cameraProvider = cameraProvider

        // Preview configuration following CameraX best practices
        preview = Preview.Builder()
            .setTargetAspectRatio(aspectRatio)
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // ImageAnalysis configuration for pose detection
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(aspectRatio)
            .setTargetResolution(Size(640, 480)) // Optimal for MediaPipe
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                context as LifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        } catch (exc: Exception) {
            Log.e("CameraXManager", "Camera binding failed", exc)
            throw exc
        }
    }

    fun setImageAnalyzer(analyzer: ImageAnalysis.Analyzer) {
        imageAnalyzer?.setAnalyzer(
            ContextCompat.getMainExecutor(context),
            analyzer
        )
    }
}
```

**Step 2: Coordinate Transformation Implementation**
```kotlin
class CoordinateMapper {
    companion object {
        // Coordinate transformation following CameraX specifications
        fun normalizedToPixel(
            normalizedX: Float,
            normalizedY: Float,
            viewWidth: Int,
            viewHeight: Int,
            imageWidth: Int,
            imageHeight: Int,
            isFrontCamera: Boolean = true,
            fitMode: PreviewView.ScaleType = PreviewView.ScaleType.FILL_CENTER
        ): Pair<Float, Float> {
            // Calculate scaling and offset based on fit mode
            val scale = when (fitMode) {
                PreviewView.ScaleType.FILL_CENTER -> {
                    maxOf(
                        viewWidth.toFloat() / imageWidth,
                        viewHeight.toFloat() / imageHeight
                    )
                }
                PreviewView.ScaleType.FIT_CENTER -> {
                    minOf(
                        viewWidth.toFloat() / imageWidth,
                        viewHeight.toFloat() / imageHeight
                    )
                }
                else -> 1.0f
            }

            val scaledImageWidth = imageWidth * scale
            val scaledImageHeight = imageHeight * scale

            val offsetX = (viewWidth - scaledImageWidth) / 2
            val offsetY = (viewHeight - scaledImageHeight) / 2

            // Apply coordinate transformation
            var pixelX = normalizedX * scaledImageWidth + offsetX
            val pixelY = normalizedY * scaledImageHeight + offsetY

            // Mirror for front camera following CameraX conventions
            if (isFrontCamera) {
                pixelX = viewWidth - pixelX
            }

            return Pair(pixelX, pixelY)
        }
    }
}
```

### 2.3 Gemini API Integration Patterns

#### Step-by-Step Gemini 2.5 Integration

**Step 1: Structured Output Schema Definition**
```kotlin
@Serializable
data class PoseSuggestionResponse(
    val suggestions: List<PoseSuggestion>
) {
    init {
        require(suggestions.size == 3) { "Exactly 3 suggestions required" }
    }
}

@Serializable
data class PoseSuggestion(
    val id: String,
    val category: SuggestionCategory,
    val title: String,
    val description: String,
    @SerialName("action_steps") val actionSteps: List<String>,
    val priority: Priority,
    @SerialName("target_landmarks") val targetLandmarks: List<String>
) {
    init {
        require(title.length in 5..50) { "Title must be 5-50 characters" }
        require(description.length in 30..200) { "Description must be 30-200 characters" }
        require(targetLandmarks.size in 2..6) { "Must have 2-6 target landmarks" }
    }
}

@Serializable
enum class SuggestionCategory {
    @SerialName("posture") POSTURE,
    @SerialName("alignment") ALIGNMENT,
    @SerialName("movement") MOVEMENT,
    @SerialName("balance") BALANCE,
    @SerialName("strength") STRENGTH
}

@Serializable
enum class Priority {
    @SerialName("high") HIGH,
    @SerialName("medium") MEDIUM,
    @SerialName("low") LOW
}
```

**Step 2: Gemini Client Implementation with Schema Validation**
```kotlin
class GeminiPoseSuggestionClient(
    private val apiKey: String
) {
    private val generativeModel = GenerativeAI(apiKey).generativeModel(
        modelName = "gemini-2.0-flash-exp",
        generationConfig = generationConfig {
            temperature = 0.7f
            topK = 40
            topP = 0.95f
            maxOutputTokens = 1024
            responseMimeType = "application/json"
            responseSchema = Schema(
                name = "pose_suggestions",
                description = "Exactly 3 pose improvement suggestions",
                type = FunctionType.OBJECT,
                properties = mapOf(
                    "suggestions" to Schema(
                        type = FunctionType.ARRAY,
                        items = Schema(
                            type = FunctionType.OBJECT,
                            properties = mapOf(
                                "id" to Schema(type = FunctionType.STRING),
                                "category" to Schema(
                                    type = FunctionType.STRING,
                                    enumValues = listOf("posture", "alignment", "movement", "balance", "strength")
                                ),
                                "title" to Schema(type = FunctionType.STRING),
                                "description" to Schema(type = FunctionType.STRING),
                                "action_steps" to Schema(
                                    type = FunctionType.ARRAY,
                                    items = Schema(type = FunctionType.STRING)
                                ),
                                "priority" to Schema(
                                    type = FunctionType.STRING,
                                    enumValues = listOf("high", "medium", "low")
                                ),
                                "target_landmarks" to Schema(
                                    type = FunctionType.ARRAY,
                                    items = Schema(type = FunctionType.STRING)
                                )
                            ),
                            required = listOf("id", "category", "title", "description", "action_steps", "priority", "target_landmarks")
                        ),
                        description = "Array of exactly 3 pose suggestions"
                    )
                ),
                required = listOf("suggestions")
            )
        }
    )

    suspend fun getPoseSuggestions(landmarks: List<NormalizedLandmark>): Result<PoseSuggestionResponse> {
        return try {
            val prompt = buildPoseAnalysisPrompt(landmarks)
            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: throw IllegalStateException("Empty response")

            val suggestions = Json.decodeFromString<PoseSuggestionResponse>(responseText)
            Result.success(suggestions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildPoseAnalysisPrompt(landmarks: List<NormalizedLandmark>): String {
        val landmarkData = landmarks.mapIndexed { index, landmark ->
            "${PoseLandmarks.names[index]}: (${landmark.x()}, ${landmark.y()}, ${landmark.z()}) confidence: ${landmark.visibility()}"
        }.joinToString("\n")

        return """
        Analyze this pose data and provide exactly 3 actionable improvement suggestions:

        Pose Landmarks:
        $landmarkData

        Requirements:
        - Provide exactly 3 suggestions
        - Focus on the most important improvements
        - Include specific action steps
        - Reference relevant landmark points
        - Prioritize suggestions by impact

        Return the response in the specified JSON schema format.
        """.trimIndent()
    }
}
```

### 2.4 Error Handling Strategies

#### Comprehensive Error Recovery Implementation
```kotlin
class PoseCoachErrorHandler {
    private val logger = Logger.getLogger("PoseCoachErrorHandler")

    suspend fun handleMediaPipeError(error: RuntimeException): PoseDetectionResult {
        return when (error) {
            is MediaPipeException -> {
                logger.warn("MediaPipe error: ${error.message}")
                reinitializeMediaPipe()
                PoseDetectionResult.Error(ErrorType.MEDIAPIPE_FAILURE, error.message)
            }
            is OutOfMemoryError -> {
                logger.error("Out of memory during pose detection")
                System.gc()
                reduceProcessingQuality()
                PoseDetectionResult.Error(ErrorType.MEMORY_EXHAUSTED, "Reduced quality mode activated")
            }
            else -> {
                logger.error("Unknown pose detection error", error)
                PoseDetectionResult.Error(ErrorType.UNKNOWN, error.message)
            }
        }
    }

    suspend fun handleCameraError(error: Exception): CameraResult {
        return when (error) {
            is SecurityException -> {
                CameraResult.PermissionRequired("Camera permission required")
            }
            is IllegalStateException -> {
                logger.warn("Camera state error, reinitializing")
                CameraResult.Retry("Camera reinitialization required")
            }
            else -> {
                logger.error("Camera error", error)
                CameraResult.Error(error.message ?: "Unknown camera error")
            }
        }
    }

    suspend fun handleGeminiApiError(error: Exception): SuggestionResult {
        return when (error) {
            is HttpException -> {
                when (error.code) {
                    429 -> SuggestionResult.RateLimited("Rate limit exceeded, using local fallback")
                    401 -> SuggestionResult.AuthenticationFailed("Invalid API key")
                    else -> SuggestionResult.NetworkError("API error: ${error.code}")
                }
            }
            is SocketTimeoutException -> {
                SuggestionResult.Timeout("API timeout, using local suggestions")
            }
            else -> {
                logger.error("Gemini API error", error)
                SuggestionResult.FallbackToLocal("Using local pose analysis")
            }
        }
    }
}

sealed class PoseDetectionResult {
    data class Success(val landmarks: List<NormalizedLandmark>) : PoseDetectionResult()
    data class Error(val type: ErrorType, val message: String?) : PoseDetectionResult()
}

enum class ErrorType {
    MEDIAPIPE_FAILURE,
    MEMORY_EXHAUSTED,
    CAMERA_UNAVAILABLE,
    PERMISSION_DENIED,
    UNKNOWN
}
```

---

## 3. Testing Strategy

### 3.1 Comprehensive Testing Approach Using Truth and JUnit 5

#### Unit Testing Framework Setup
```kotlin
// build.gradle.kts testing dependencies
dependencies {
    // JUnit 5 (Jupiter)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")

    // Google Truth for assertions
    testImplementation("com.google.truth:truth:1.1.5")

    // Mockito for mocking
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")

    // Kotlin coroutines testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Android testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

#### MediaPipe Testing Implementation
```kotlin
class MediaPipePoseDetectorTest {
    private lateinit var detector: MediaPipePoseDetector
    private lateinit var mockContext: Context

    @BeforeEach
    fun setup() {
        mockContext = mock()
        detector = MediaPipePoseDetector(mockContext)
    }

    @Test
    fun `detectPose should return 33 landmarks for valid input`() = runTest {
        // Given
        val testImage = createTestBitmap(640, 480)
        val mockResult = createMockPoseLandmarkerResult()

        // When
        val result = detector.detectPose(testImage)

        // Then
        assertThat(result).isInstanceOf<PoseDetectionResult.Success>()
        val landmarks = (result as PoseDetectionResult.Success).landmarks
        assertThat(landmarks).hasSize(33)
        landmarks.forEach { landmark ->
            assertThat(landmark.x()).isIn(Range.closed(0.0f, 1.0f))
            assertThat(landmark.y()).isIn(Range.closed(0.0f, 1.0f))
            assertThat(landmark.visibility()).isAtLeast(0.0f)
        }
    }

    @ParameterizedTest
    @ValueSource(longs = [16, 33, 50, 100]) // Different frame intervals
    fun `detectPose should maintain target FPS across frame rates`(frameIntervalMs: Long) = runTest {
        val frameCount = 60
        val startTime = System.currentTimeMillis()

        repeat(frameCount) {
            detector.detectPose(createTestBitmap(640, 480))
            delay(frameIntervalMs)
        }

        val totalTime = System.currentTimeMillis() - startTime
        val actualFps = (frameCount * 1000.0) / totalTime
        val expectedFps = 1000.0 / frameIntervalMs

        // Allow 10% tolerance for FPS variation
        assertThat(actualFps).isWithin(expectedFps * 0.1).of(expectedFps)
    }

    @Test
    fun `detectPose should handle corrupted image gracefully`() = runTest {
        // Given
        val corruptedBitmap = Bitmap.createBitmap(0, 0, Bitmap.Config.ARGB_8888)

        // When
        val result = detector.detectPose(corruptedBitmap)

        // Then
        assertThat(result).isInstanceOf<PoseDetectionResult.Error>()
        val error = result as PoseDetectionResult.Error
        assertThat(error.type).isEqualTo(ErrorType.INVALID_INPUT)
    }
}
```

#### Gemini API Testing with Truth Assertions
```kotlin
class GeminiPoseSuggestionClientTest {
    private lateinit var client: GeminiPoseSuggestionClient
    private val testApiKey = "test-api-key"

    @BeforeEach
    fun setup() {
        client = GeminiPoseSuggestionClient(testApiKey)
    }

    @Test
    fun `getPoseSuggestions should return exactly 3 suggestions`() = runTest {
        // Given
        val testLandmarks = createTestPoseLandmarks()

        // When
        val result = client.getPoseSuggestions(testLandmarks)

        // Then
        assertThat(result.isSuccess).isTrue()
        val response = result.getOrNull()
        assertThat(response).isNotNull()
        assertThat(response!!.suggestions).hasSize(3)
    }

    @Test
    fun `response should comply with structured output schema`() = runTest {
        // Given
        val testLandmarks = createTestPoseLandmarks()

        // When
        val result = client.getPoseSuggestions(testLandmarks)

        // Then
        assertThat(result.isSuccess).isTrue()
        val suggestions = result.getOrNull()!!.suggestions

        suggestions.forEach { suggestion ->
            // Validate title length
            assertThat(suggestion.title.length).isIn(Range.closed(5, 50))

            // Validate description length
            assertThat(suggestion.description.length).isIn(Range.closed(30, 200))

            // Validate target landmarks count
            assertThat(suggestion.targetLandmarks).hasSize().isIn(Range.closed(2, 6))

            // Validate landmark names are valid
            suggestion.targetLandmarks.forEach { landmarkName ->
                assertThat(PoseLandmarks.names).contains(landmarkName)
            }

            // Validate category is valid
            assertThat(suggestion.category).isIn(listOf(
                SuggestionCategory.POSTURE,
                SuggestionCategory.ALIGNMENT,
                SuggestionCategory.MOVEMENT,
                SuggestionCategory.BALANCE,
                SuggestionCategory.STRENGTH
            ))
        }
    }

    @Test
    fun `API timeout should trigger fallback mechanism`() = runTest {
        // Given
        val slowClient = GeminiPoseSuggestionClient(testApiKey, timeoutMs = 100)
        val testLandmarks = createTestPoseLandmarks()

        // When
        val startTime = System.currentTimeMillis()
        val result = slowClient.getPoseSuggestions(testLandmarks)
        val elapsedTime = System.currentTimeMillis() - startTime

        // Then
        assertThat(elapsedTime).isLessThan(2000) // Should fallback quickly
        assertThat(result.isSuccess).isTrue() // Fallback should still provide suggestions
    }
}
```

### 3.2 Coverage Requirements and Quality Gates

#### Test Coverage Configuration
```kotlin
// Configure JaCoCo for coverage reporting
jacoco {
    toolVersion = "0.8.10"
}

tasks.withType<JacocoReport> {
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "**/databinding/**/*.*",
        "**/generated/**/*.*"
    )

    val kotlinClasses = fileTree("${buildDir}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }

    classDirectories.setFrom(files(kotlinClasses))
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(fileTree(buildDir).include("**/*.exec"))
}
```

#### Performance Testing Methodology
```kotlin
class PerformanceBenchmarkTest {
    @Test
    fun `pose inference should complete under 30ms on average`() = runTest {
        val detector = MediaPipePoseDetector(ApplicationProvider.getApplicationContext())
        detector.initialize()

        val testImage = createTestBitmap(640, 480)
        val inferenceLatencies = mutableListOf<Long>()

        // Warm up
        repeat(10) { detector.detectPose(testImage) }

        // Benchmark
        repeat(100) {
            val startTime = System.nanoTime()
            detector.detectPose(testImage)
            val latency = (System.nanoTime() - startTime) / 1_000_000 // Convert to ms
            inferenceLatencies.add(latency)
        }

        val averageLatency = inferenceLatencies.average()
        val p95Latency = inferenceLatencies.sorted()[94] // 95th percentile

        assertThat(averageLatency).isLessThan(30.0)
        assertThat(p95Latency).isLessThan(50.0) // Allow higher bound for 95th percentile
    }

    @Test
    fun `coordinate transformation should be accurate within 2 pixels`() {
        val viewWidth = 1080
        val viewHeight = 1920
        val imageWidth = 640
        val imageHeight = 480

        val testCases = listOf(
            Pair(0.0f, 0.0f), // Top-left corner
            Pair(1.0f, 1.0f), // Bottom-right corner
            Pair(0.5f, 0.5f), // Center
            Pair(0.25f, 0.75f) // Off-center point
        )

        testCases.forEach { (normalizedX, normalizedY) ->
            val (pixelX, pixelY) = CoordinateMapper.normalizedToPixel(
                normalizedX, normalizedY, viewWidth, viewHeight, imageWidth, imageHeight
            )

            // Transform back to normalized coordinates
            val backNormalizedX = (pixelX * imageWidth) / (viewWidth.toFloat())
            val backNormalizedY = (pixelY * imageHeight) / (viewHeight.toFloat())

            val errorX = abs(normalizedX - backNormalizedX) * viewWidth
            val errorY = abs(normalizedY - backNormalizedY) * viewHeight
            val maxError = maxOf(errorX, errorY)

            assertThat(maxError).isLessThan(2.0f)
        }
    }
}
```

### 3.3 Integration Testing Patterns

#### End-to-End Testing Implementation
```kotlin
@RunWith(AndroidJUnit4::class)
class PoseCoachIntegrationTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun `complete pose coaching workflow should function correctly`() {
        // Launch app and grant permissions
        onView(withId(R.id.grant_permissions_button))
            .check(matches(isDisplayed()))
            .perform(click())

        // Wait for camera initialization
        Thread.sleep(2000)

        // Verify camera preview is active
        onView(withId(R.id.preview_view))
            .check(matches(isDisplayed()))

        // Simulate pose detection trigger
        // This would involve injecting test pose data

        // Verify pose overlay appears
        onView(withId(R.id.pose_overlay))
            .check(matches(isDisplayed()))

        // Verify suggestions appear (if connected to API)
        onView(withId(R.id.suggestions_container))
            .check(matches(hasMinimumChildCount(1)))
    }

    @Test
    fun `privacy mode should function without API calls`() {
        // Enable privacy mode
        onView(withId(R.id.privacy_mode_toggle))
            .perform(click())

        // Verify no network requests are made
        // This would require network monitoring setup

        // Verify core functionality still works
        onView(withId(R.id.pose_overlay))
            .check(matches(isDisplayed()))
    }
}
```

---

## 4. API Integration Specifications

### 4.1 Detailed MediaPipe Integration Requirements

#### MediaPipe Configuration Best Practices
```kotlin
object MediaPipeConfig {
    // Optimal configuration for real-time pose detection
    val LIVE_STREAM_CONFIG = PoseLandmarker.PoseLandmarkerOptions.builder()
        .setBaseOptions(
            BaseOptions.builder()
                .setDelegate(BaseOptions.Delegate.GPU) // Prefer GPU acceleration
                .setModelAssetPath("pose_landmarker.task")
                .build()
        )
        .setRunningMode(RunningMode.LIVE_STREAM)
        .setNumPoses(1) // Single person detection for optimal performance
        .setMinPoseDetectionConfidence(0.5f)
        .setMinPosePresenceConfidence(0.5f)
        .setMinTrackingConfidence(0.5f)
        .setOutputSegmentationMasks(false) // Disable for better performance
        .build()

    // Device-specific optimization configurations
    fun getOptimizedConfig(deviceTier: DeviceTier): PoseLandmarker.PoseLandmarkerOptions {
        return when (deviceTier) {
            DeviceTier.HIGH_END -> LIVE_STREAM_CONFIG
            DeviceTier.MID_RANGE -> LIVE_STREAM_CONFIG.toBuilder()
                .setMinPoseDetectionConfidence(0.6f)
                .build()
            DeviceTier.LOW_END -> LIVE_STREAM_CONFIG.toBuilder()
                .setMinPoseDetectionConfidence(0.7f)
                .setMinTrackingConfidence(0.6f)
                .build()
        }
    }
}

enum class DeviceTier {
    HIGH_END, MID_RANGE, LOW_END
}
```

#### Live Stream Processing Pipeline
```kotlin
class LiveStreamPoseProcessor(
    private val poseLandmarker: PoseLandmarker,
    private val performanceTracker: PerformanceTracker
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        val startTime = System.currentTimeMillis()

        try {
            // Convert ImageProxy to MPImage for MediaPipe
            val mpImage = BitmapImageBuilder(imageProxy.toBitmap()).build()

            // Process with timestamp for live stream mode
            poseLandmarker.detectAsync(mpImage, startTime)

        } catch (e: Exception) {
            Log.e("LiveStreamProcessor", "Pose detection failed", e)
        } finally {
            // Track processing performance
            val processingTime = System.currentTimeMillis() - startTime
            performanceTracker.recordInferenceTime(processingTime)

            // Always close the image proxy
            imageProxy.close()
        }
    }
}
```

### 4.2 Gemini API Implementation Patterns

#### Production-Ready Gemini Client
```kotlin
class ProductionGeminiClient(
    private val apiKey: String,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(LoggingInterceptor())
        .build()
) {
    private val generativeModel = GenerativeAI(apiKey).generativeModel(
        modelName = "gemini-2.0-flash-exp",
        generationConfig = createGenerationConfig(),
        systemInstruction = content { text(POSE_COACHING_SYSTEM_PROMPT) }
    )

    companion object {
        private const val POSE_COACHING_SYSTEM_PROMPT = """
            You are an expert pose coach and physical therapist. Your role is to analyze human pose data
            and provide actionable improvement suggestions. Focus on:

            1. Posture alignment and spinal health
            2. Joint positioning and movement efficiency
            3. Balance and stability
            4. Muscle engagement and strength
            5. Injury prevention and safety

            Always provide exactly 3 suggestions, prioritized by importance for the person's wellbeing.
            Be encouraging, specific, and actionable in your guidance.
        """
    }

    private fun createGenerationConfig() = generationConfig {
        temperature = 0.7f
        topK = 40
        topP = 0.95f
        maxOutputTokens = 1024
        responseMimeType = "application/json"
        responseSchema = createPoseResponseSchema()
    }

    suspend fun analyzePose(
        landmarks: List<NormalizedLandmark>,
        context: PoseContext = PoseContext.GENERAL
    ): Result<PoseSuggestionResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildContextualPrompt(landmarks, context)
                val response = withTimeout(10_000L) { // 10 second timeout
                    generativeModel.generateContent(prompt)
                }

                val responseText = response.text
                    ?: throw IllegalStateException("Empty response from Gemini API")

                val suggestions = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }.decodeFromString<PoseSuggestionResponse>(responseText)

                validateResponse(suggestions)
                Result.success(suggestions)

            } catch (e: TimeoutCancellationException) {
                Result.failure(ApiTimeoutException("Gemini API request timed out"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun validateResponse(response: PoseSuggestionResponse) {
        require(response.suggestions.size == 3) {
            "Response must contain exactly 3 suggestions, got ${response.suggestions.size}"
        }

        response.suggestions.forEach { suggestion ->
            require(suggestion.title.length in 5..50) {
                "Title length must be 5-50 characters: '${suggestion.title}'"
            }
            require(suggestion.description.length in 30..200) {
                "Description length must be 30-200 characters: '${suggestion.description}'"
            }
            require(suggestion.targetLandmarks.size in 2..6) {
                "Must have 2-6 target landmarks, got ${suggestion.targetLandmarks.size}"
            }
        }
    }
}

enum class PoseContext {
    GENERAL, WORKOUT, STRETCHING, SITTING, STANDING, WALKING
}
```

#### Session Management for Gemini Live API
```kotlin
class GeminiLiveApiManager(
    private val apiKey: String,
    private val lifecycleScope: CoroutineScope
) {
    private var webSocketClient: OkHttpClient? = null
    private var liveSession: WebSocket? = null
    private val sessionState = MutableStateFlow<LiveApiState>(LiveApiState.Disconnected)

    suspend fun startLiveSession(
        audioStreamManager: AudioStreamManager,
        imageSnapshotManager: ImageSnapshotManager
    ): Flow<LiveApiResponse> = flow {
        try {
            // Initialize WebSocket connection
            val webSocket = createWebSocketConnection()
            liveSession = webSocket

            sessionState.value = LiveApiState.Connected("session-${UUID.randomUUID()}")

            // Start audio streaming
            audioStreamManager.startStreaming { audioChunk ->
                lifecycleScope.launch {
                    sendAudioToLiveApi(audioChunk)
                }
            }

            // Start image snapshot management
            imageSnapshotManager.startSnapshots { imageData ->
                lifecycleScope.launch {
                    sendImageToLiveApi(imageData)
                }
            }

            // Listen for responses
            awaitClose {
                webSocket.close(1000, "Session ended")
                audioStreamManager.stopStreaming()
                imageSnapshotManager.stopSnapshots()
            }

        } catch (e: Exception) {
            sessionState.value = LiveApiState.Error(e)
            throw e
        }
    }

    private fun createWebSocketConnection(): WebSocket {
        val request = Request.Builder()
            .url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService/BidiGenerateContent")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        return OkHttpClient().newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleLiveApiResponse(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                sessionState.value = LiveApiState.Error(t)
            }
        })
    }
}

sealed class LiveApiState {
    object Disconnected : LiveApiState()
    data class Connected(val sessionId: String) : LiveApiState()
    data class Recording(val sessionId: String) : LiveApiState()
    data class Speaking(val sessionId: String) : LiveApiState()
    data class Error(val error: Throwable) : LiveApiState()
}
```

### 4.3 CameraX Configuration Guidelines

#### Production Camera Setup
```kotlin
class ProductionCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    suspend fun setupCamera(
        previewView: PreviewView,
        imageAnalyzer: ImageAnalysis.Analyzer,
        configuration: CameraConfiguration = CameraConfiguration.DEFAULT
    ) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        this.cameraProvider = cameraProvider

        // Configure preview with optimal settings
        val preview = Preview.Builder()
            .setTargetResolution(configuration.previewResolution)
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // Configure image analysis for MediaPipe processing
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(configuration.analysisResolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also {
                it.setAnalyzer(ContextCompat.getMainExecutor(context), imageAnalyzer)
            }

        // Select camera with fallback logic
        val cameraSelector = selectOptimalCamera(configuration.preferredLensFacing)

        try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            // Configure camera controls for optimal pose detection
            configureCameraControls(camera!!, configuration)

        } catch (exc: Exception) {
            throw CameraInitializationException("Failed to initialize camera", exc)
        }
    }

    private fun selectOptimalCamera(preferredFacing: Int): CameraSelector {
        val availableCameras = cameraProvider?.availableCameraInfos ?: emptyList()

        return if (availableCameras.any { it.lensFacing == preferredFacing }) {
            CameraSelector.Builder()
                .requireLensFacing(preferredFacing)
                .build()
        } else {
            // Fallback to any available camera
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    private fun configureCameraControls(camera: Camera, config: CameraConfiguration) {
        val cameraControl = camera.cameraControl
        val cameraInfo = camera.cameraInfo

        // Enable auto-focus for sharp pose detection
        if (cameraInfo.isFocusMeteringSupported) {
            val autoFocusPoint = SurfaceOrientedMeteringPointFactory(1f, 1f)
                .createPoint(0.5f, 0.5f) // Center focus
            val focusAction = FocusMeteringAction.Builder(autoFocusPoint)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            cameraControl.startFocusAndMetering(focusAction)
        }

        // Set optimal exposure for pose detection
        if (cameraInfo.exposureState.isExposureCompensationSupported) {
            cameraControl.setExposureCompensationIndex(config.exposureCompensation)
        }
    }
}

data class CameraConfiguration(
    val preferredLensFacing: Int = CameraSelector.LENS_FACING_FRONT,
    val previewResolution: Size = Size(1080, 1920),
    val analysisResolution: Size = Size(640, 480),
    val exposureCompensation: Int = 0
) {
    companion object {
        val DEFAULT = CameraConfiguration()
        val PERFORMANCE_OPTIMIZED = CameraConfiguration(
            analysisResolution = Size(480, 360) // Lower resolution for better performance
        )
        val QUALITY_OPTIMIZED = CameraConfiguration(
            analysisResolution = Size(720, 1280) // Higher resolution for better accuracy
        )
    }
}
```

---

## 5. Development Roadmap

### 5.1 Priority-Based Implementation Plan

#### Phase 1: Foundation (Weeks 1-3)
**Milestone**: Core MediaPipe and CameraX Integration

**Week 1: MediaPipe Core Implementation**
- [ ] Implement `MediaPipePoseDetector` with LIVE_STREAM mode
- [ ] Configure 33-point pose landmark detection
- [ ] Implement performance tracking and optimization
- [ ] Add GPU acceleration with CPU fallback
- [ ] Create comprehensive unit tests (>85% coverage)

**Week 2: CameraX Integration**
- [ ] Implement `CameraXManager` with PreviewView + ImageAnalysis
- [ ] Create coordinate transformation system
- [ ] Add multi-orientation support
- [ ] Implement camera permission handling
- [ ] Create camera integration tests

**Week 3: Basic UI and Overlay System**
- [ ] Implement `PoseOverlayView` with 60fps rendering
- [ ] Add pose skeleton rendering with confidence visualization
- [ ] Create responsive Material Design 3 UI
- [ ] Implement basic error handling and recovery
- [ ] Add accessibility support foundation

**Success Criteria:**
- MediaPipe inference <50ms (target: <30ms)
- Camera preview at 30fps with overlay
- >80% test coverage for core modules
- Basic app functional end-to-end

#### Phase 2: AI Integration and Optimization (Weeks 4-6)

**Week 4: Gemini 2.5 Structured Output**
- [ ] Implement `GeminiPoseSuggestionClient` with responseSchema
- [ ] Add structured output validation (exactly 3 suggestions)
- [ ] Create fallback client for offline mode
- [ ] Implement API key management and security
- [ ] Add comprehensive API testing

**Week 5: Performance Optimization**
- [ ] Achieve <30ms MediaPipe inference consistently
- [ ] Implement adaptive quality management
- [ ] Add One Euro Filter for pose stabilization
- [ ] Optimize memory usage and garbage collection
- [ ] Add performance monitoring and metrics

**Week 6: Enhanced Error Handling**
- [ ] Implement comprehensive error recovery
- [ ] Add graceful degradation strategies
- [ ] Create robust retry mechanisms
- [ ] Add network condition adaptation
- [ ] Implement telemetry and logging

**Success Criteria:**
- Consistent <30ms pose inference
- Gemini API integration with 100% schema compliance
- Robust error handling with graceful fallbacks
- >85% test coverage including integration tests

#### Phase 3: Privacy and Live API (Weeks 7-9)

**Week 7: Privacy Controls Implementation**
- [ ] Implement comprehensive consent management
- [ ] Add privacy-first data processing
- [ ] Create user privacy dashboard
- [ ] Implement data minimization enforcement
- [ ] Add GDPR/CCPA compliance validation

**Week 8: Gemini Live API Integration**
- [ ] Implement WebSocket-based Live API client
- [ ] Add real-time audio streaming
- [ ] Create image snapshot management
- [ ] Implement barge-in detection
- [ ] Add live session state management

**Week 9: Advanced Features**
- [ ] Implement multi-person pose detection
- [ ] Add pose stability gate improvements
- [ ] Create adaptive coaching suggestions
- [ ] Add advanced overlay visualizations
- [ ] Implement usage analytics (privacy-compliant)

**Success Criteria:**
- Complete privacy compliance implementation
- Working Live API integration with audio/visual
- Advanced pose detection features
- Comprehensive privacy testing validation

#### Phase 4: Production Readiness (Weeks 10-12)

**Week 10: Quality Assurance**
- [ ] Achieve >80% test coverage across all modules
- [ ] Complete performance benchmarking
- [ ] Conduct security vulnerability assessment
- [ ] Perform accessibility compliance testing
- [ ] Execute user acceptance testing

**Week 11: CI/CD and Deployment**
- [ ] Implement comprehensive CI/CD pipeline
- [ ] Add automated testing and quality gates
- [ ] Configure performance regression detection
- [ ] Set up monitoring and alerting
- [ ] Prepare production deployment

**Week 12: Launch Preparation**
- [ ] Finalize documentation and user guides
- [ ] Complete app store preparation
- [ ] Conduct final security and privacy audits
- [ ] Train support team
- [ ] Execute production deployment

**Success Criteria:**
- Production-ready application with all features
- Comprehensive testing and quality validation
- Operational monitoring and support systems
- Successful production deployment

### 5.2 Milestone Definitions and Success Criteria

#### Technical Milestones

**M1: MediaPipe Integration Complete**
- MediaPipe LIVE_STREAM mode operational
- 33-point pose detection with confidence scoring
- Inference latency <30ms on target devices
- GPU acceleration working with CPU fallback
- Memory usage optimized (<200MB peak)

**M2: Camera Integration Complete**
- CameraX PreviewView + ImageAnalysis pipeline working
- Multi-orientation support functional
- Coordinate transformation accurate (<2px error)
- Camera permissions properly handled
- 60fps overlay rendering achieved

**M3: Gemini API Integration Complete**
- Structured output working with responseSchema
- Exactly 3 suggestions validated consistently
- Fallback mechanism operational
- API key security implemented
- Error handling comprehensive

**M4: Privacy Compliance Complete**
- User consent management functional
- Data minimization enforced automatically
- Privacy dashboard operational
- GDPR/CCPA compliance validated
- Zero unauthorized data transmission

**M5: Production Ready**
- >80% test coverage achieved
- Performance benchmarks consistently met
- Security vulnerabilities addressed
- CI/CD pipeline operational
- Monitoring and alerting configured

#### Quality Gates

**Code Quality Gates**
- Test coverage >80% for all modules
- No critical security vulnerabilities
- Performance benchmarks within targets
- Accessibility compliance validated
- Code review approval required

**Privacy Gates**
- Zero image data transmission verified
- Consent management tested
- Data minimization validated
- Privacy policy compliance confirmed
- Regulatory requirement audit passed

**Performance Gates**
- Pose inference <30ms (95th percentile)
- UI responsiveness >55fps sustained
- Memory usage <200MB peak
- Battery consumption <20% per hour
- App startup time <3 seconds

### 5.3 Risk Mitigation Strategies

#### Critical Risk Areas

**Technical Risks**
1. **MediaPipe Performance Risk**
   - Mitigation: Parallel optimization, adaptive quality
   - Contingency: Alternative pose detection libraries
   - Monitoring: Real-time performance metrics

2. **CameraX Device Compatibility**
   - Mitigation: Comprehensive device testing matrix
   - Contingency: Camera2 API fallback implementation
   - Monitoring: Device compatibility analytics

3. **Gemini API Reliability**
   - Mitigation: Robust offline fallback system
   - Contingency: Alternative AI providers evaluated
   - Monitoring: API response time and success rate

**Privacy and Compliance Risks**
1. **GDPR/CCPA Compliance**
   - Mitigation: Privacy-by-design implementation
   - Contingency: Progressive consent mechanisms
   - Monitoring: Automated compliance validation

2. **Data Security**
   - Mitigation: End-to-end encryption, secure storage
   - Contingency: Additional security layers
   - Monitoring: Security vulnerability scanning

**Project Delivery Risks**
1. **Resource Availability**
   - Mitigation: Cross-training, knowledge documentation
   - Contingency: External contractor engagement
   - Monitoring: Sprint velocity tracking

2. **Third-party Dependencies**
   - Mitigation: Version pinning, alternative evaluation
   - Contingency: Custom implementations ready
   - Monitoring: Dependency update notifications

### 5.4 Quality Assurance Checkpoints

#### Weekly Quality Reviews
- Code coverage analysis and improvement
- Performance benchmark validation
- Security scan results review
- Privacy compliance verification
- User experience testing feedback

#### Monthly Milestone Gates
- Feature completeness assessment
- Technical debt evaluation
- Security posture review
- Privacy audit update
- Stakeholder approval confirmation

#### Release Readiness Criteria
- All P0 and P1 features complete and tested
- Performance targets consistently achieved
- Security vulnerabilities resolved
- Privacy compliance validated
- Documentation complete and reviewed
- Support procedures tested and documented

---

## 📞 Support and Implementation Assistance

### Development Team Resources
- **Technical Lead**: MediaPipe and CameraX integration expertise
- **AI/ML Engineer**: Gemini API integration and optimization
- **Privacy Engineer**: GDPR/CCPA compliance and data protection
- **QA Engineer**: Testing strategy and automation
- **DevOps Engineer**: CI/CD pipeline and deployment

### Documentation References
- MediaPipe Pose Landmarker: [https://developers.google.com/mediapipe/solutions/vision/pose_landmarker](https://developers.google.com/mediapipe/solutions/vision/pose_landmarker)
- CameraX Documentation: [https://developer.android.com/training/camerax](https://developer.android.com/training/camerax)
- Gemini API Reference: [https://ai.google.dev/docs](https://ai.google.dev/docs)
- Truth Testing Framework: [https://truth.dev/](https://truth.dev/)
- JUnit 5 User Guide: [https://junit.org/junit5/docs/current/user-guide/](https://junit.org/junit5/docs/current/user-guide/)

### Implementation Support
For technical questions, implementation challenges, or clarification on any aspect of this development guide, please refer to the project's issue tracker or contact the development team lead.

---

*This comprehensive development guide ensures correct implementation according to all analyzed documentation sources while maintaining high standards for quality, performance, and privacy compliance.*