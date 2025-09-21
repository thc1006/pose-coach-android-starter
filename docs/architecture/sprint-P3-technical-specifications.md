# Sprint P3: Technical Architecture Specifications
## Production-Ready Implementation Technical Details

### Overview

This document provides detailed technical specifications for Sprint P3 implementation, including code patterns, performance requirements, and integration guidelines that align with CLAUDE.md requirements.

---

## Week 1: Production UI Implementation

### CameraX Integration Architecture

```kotlin
// File: app/src/main/kotlin/com/posecoach/camera/ProductionCameraManager.kt
class ProductionCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val CAMERA_STARTUP_TIMEOUT_MS = 100L
        private const val TARGET_FPS = 30
        private const val IMAGE_ANALYSIS_RESOLUTION = Size(640, 480)
    }

    private val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null

    fun startCamera(
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer
    ): LiveData<CameraState> {
        // Implementation with <100ms startup guarantee
    }
}
```

### OverlayView Production Implementation

```kotlin
// File: app/src/main/kotlin/com/posecoach/overlay/ProductionOverlayRenderer.kt
class ProductionOverlayRenderer(context: Context) : View(context) {
    companion object {
        private const val TARGET_FPS = 60
        private const val SKELETON_LINE_WIDTH = 4f
        private const val LANDMARK_RADIUS = 8f
    }

    private val skeletonPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = SKELETON_LINE_WIDTH
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private var currentPose: Pose? = null
    private val renderingMetrics = RenderingMetrics()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderingMetrics.startFrame()

        currentPose?.let { pose ->
            drawPoseSkeleton(canvas, pose)
        }

        renderingMetrics.endFrame()

        // Ensure 60fps by scheduling next frame
        if (renderingMetrics.shouldThrottle()) {
            postInvalidateDelayed(16) // ~60fps
        } else {
            invalidate()
        }
    }

    private fun drawPoseSkeleton(canvas: Canvas, pose: Pose) {
        // Boundary-aware rendering - never draw on camera surface
        val overlayBounds = Rect(0, 0, width, height)

        pose.allPoseLandmarks.forEach { landmark ->
            if (overlayBounds.contains(landmark.position.x.toInt(), landmark.position.y.toInt())) {
                canvas.drawCircle(
                    landmark.position.x,
                    landmark.position.y,
                    LANDMARK_RADIUS,
                    skeletonPaint
                )
            }
        }

        // Draw connections between landmarks
        PoseConnections.ALL_CONNECTIONS.forEach { connection ->
            drawConnection(canvas, pose, connection)
        }
    }
}
```

### Material Design 3 Theme Implementation

```kotlin
// File: app/src/main/kotlin/com/posecoach/ui/theme/PoseCoachTheme.kt
@Composable
fun PoseCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> darkColorScheme(
            primary = Color(0xFF6EDBFF),
            onPrimary = Color(0xFF003544),
            primaryContainer = Color(0xFF004D61),
            onPrimaryContainer = Color(0xFFBEEAFF)
        )
        else -> lightColorScheme(
            primary = Color(0xFF006780),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFBEEAFF),
            onPrimaryContainer = Color(0xFF001F28)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PoseCoachTypography,
        content = content
    )
}
```

---

## Week 2: MediaPipe Optimization

### Optimized Pose Detection Pipeline

```kotlin
// File: app/src/main/kotlin/com/posecoach/pose/OptimizedPoseDetector.kt
class OptimizedPoseDetector private constructor() {
    companion object {
        private const val INFERENCE_TIMEOUT_MS = 30L
        private const val MODEL_COMPLEXITY = 1 // Balance accuracy vs speed
        private const val MIN_DETECTION_CONFIDENCE = 0.7f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f

        @Volatile
        private var INSTANCE: OptimizedPoseDetector? = null

        fun getInstance(): OptimizedPoseDetector {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OptimizedPoseDetector().also { INSTANCE = it }
            }
        }
    }

    private val poseDetector: PoseDetector by lazy {
        val options = PoseLandmarkerOptions.builder()
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setModelComplexity(MODEL_COMPLEXITY)
            .setMinPoseDetectionConfidence(MIN_DETECTION_CONFIDENCE)
            .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
            .setResultListener(::onPoseDetectionResult)
            .setErrorListener(::onPoseDetectionError)
            .build()

        PoseDetector.createFromOptions(options)
    }

    private val inferenceTimer = InferenceTimer()

    fun detectPose(image: MPImage, timestampMs: Long): DetectionResult {
        inferenceTimer.startInference()

        return try {
            poseDetector.detectAsync(image, timestampMs)
            DetectionResult.Success(timestamp = timestampMs)
        } catch (e: Exception) {
            inferenceTimer.recordError()
            DetectionResult.Error(e.message ?: "Unknown detection error")
        }
    }

    private fun onPoseDetectionResult(result: PoseDetectionResult, input: MPImage) {
        val inferenceTime = inferenceTimer.endInference()

        if (inferenceTime > INFERENCE_TIMEOUT_MS) {
            Log.w("PoseDetector", "Inference time exceeded target: ${inferenceTime}ms")
        }

        // Process results with StablePoseGate
        val filteredPoses = StablePoseGate.filter(result.poses())

        // Notify listeners
        poseResultListeners.forEach { listener ->
            listener.onPoseDetected(filteredPoses, inferenceTime)
        }
    }
}
```

### StablePoseGate Production Implementation

```kotlin
// File: app/src/main/kotlin/com/posecoach/pose/StablePoseGate.kt
object StablePoseGate {
    private const val STABILITY_THRESHOLD = 0.15f
    private const val TEMPORAL_WINDOW_SIZE = 5
    private const val MIN_CONFIDENCE_THRESHOLD = 0.7f

    private val poseHistory = CircularBuffer<List<Pose>>(TEMPORAL_WINDOW_SIZE)
    private val stabilityMetrics = StabilityMetrics()

    fun filter(poses: List<Pose>): List<Pose> {
        if (poses.isEmpty()) return emptyList()

        // Add to history
        poseHistory.add(poses)

        if (poseHistory.size < TEMPORAL_WINDOW_SIZE) {
            return poses // Not enough history for filtering
        }

        return poses.mapNotNull { pose ->
            if (isPoseStable(pose)) {
                stabilizeKeypoints(pose)
            } else {
                null
            }
        }
    }

    private fun isPoseStable(pose: Pose): Boolean {
        val recentPoses = poseHistory.toList()

        // Calculate movement variance across temporal window
        val variance = calculateKeypointVariance(pose, recentPoses)

        stabilityMetrics.recordVariance(variance)

        return variance < STABILITY_THRESHOLD &&
               pose.allPoseLandmarks.all { it.visibility() > MIN_CONFIDENCE_THRESHOLD }
    }

    private fun stabilizeKeypoints(pose: Pose): Pose {
        // Apply temporal smoothing to reduce jitter
        val smoothedLandmarks = pose.allPoseLandmarks.map { landmark ->
            smoothLandmark(landmark)
        }

        return Pose.builder()
            .addLandmarks(smoothedLandmarks)
            .build()
    }
}
```

### Multi-Person Tracking

```kotlin
// File: app/src/main/kotlin/com/posecoach/pose/MultiPersonTracker.kt
class MultiPersonTracker {
    companion object {
        private const val PRIMARY_SUBJECT_AREA_THRESHOLD = 0.6f
        private const val TRACKING_CONFIDENCE_THRESHOLD = 0.8f
        private const val MAX_TRACKED_PERSONS = 3
    }

    private var primarySubjectId: String? = null
    private val trackedSubjects = mutableMapOf<String, TrackedPerson>()

    fun selectPrimarySubject(poses: List<Pose>): Pose? {
        if (poses.isEmpty()) return null

        // If we have a primary subject, try to maintain tracking
        primarySubjectId?.let { id ->
            trackedSubjects[id]?.let { tracked ->
                val continuousTracking = findBestMatch(tracked.lastPose, poses)
                if (continuousTracking != null) {
                    updateTrackedPerson(id, continuousTracking)
                    return continuousTracking
                }
            }
        }

        // Select new primary subject based on criteria
        val bestCandidate = selectBestCandidate(poses)
        if (bestCandidate != null) {
            val newId = generateSubjectId()
            primarySubjectId = newId
            trackedSubjects[newId] = TrackedPerson(
                id = newId,
                lastPose = bestCandidate,
                confidence = calculatePoseQuality(bestCandidate),
                firstDetectionTime = System.currentTimeMillis()
            )
        }

        return bestCandidate
    }

    private fun selectBestCandidate(poses: List<Pose>): Pose? {
        return poses
            .filter { pose ->
                calculatePoseQuality(pose) > TRACKING_CONFIDENCE_THRESHOLD
            }
            .maxByOrNull { pose ->
                // Prioritize poses in center of frame with good visibility
                calculateCenterScore(pose) * calculateVisibilityScore(pose)
            }
    }
}
```

---

## Week 3: Gemini 2.5 Production Integration

### Response Schema Implementation

```json
// File: app/src/main/assets/schemas/pose_suggestions.schema.json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["suggestions", "analysis_metadata"],
  "additionalProperties": false,
  "properties": {
    "suggestions": {
      "type": "array",
      "minItems": 3,
      "maxItems": 3,
      "items": {
        "type": "object",
        "required": ["id", "category", "title", "description", "action_steps", "priority"],
        "additionalProperties": false,
        "properties": {
          "id": {
            "type": "string",
            "pattern": "^[a-zA-Z0-9-_]+$"
          },
          "category": {
            "type": "string",
            "enum": ["posture", "alignment", "movement", "balance", "strength"]
          },
          "title": {
            "type": "string",
            "minLength": 10,
            "maxLength": 100
          },
          "description": {
            "type": "string",
            "minLength": 20,
            "maxLength": 300
          },
          "action_steps": {
            "type": "array",
            "minItems": 1,
            "maxItems": 5,
            "items": {
              "type": "string",
              "minLength": 10,
              "maxLength": 150
            }
          },
          "priority": {
            "type": "string",
            "enum": ["high", "medium", "low"]
          }
        }
      }
    },
    "analysis_metadata": {
      "type": "object",
      "required": ["timestamp", "confidence_score", "pose_quality"],
      "properties": {
        "timestamp": {
          "type": "string",
          "format": "date-time"
        },
        "confidence_score": {
          "type": "number",
          "minimum": 0,
          "maximum": 1
        },
        "pose_quality": {
          "type": "string",
          "enum": ["excellent", "good", "fair", "poor"]
        }
      }
    }
  }
}
```

### Gemini Production Client

```kotlin
// File: app/src/main/kotlin/com/posecoach/ai/GeminiPoseCoach.kt
class GeminiPoseCoach(
    private val apiKey: String,
    private val schemaValidator: ResponseSchemaValidator
) : PoseSuggestionClient {

    companion object {
        private const val MODEL_NAME = "gemini-2.5-flash"
        private const val RESPONSE_TIMEOUT_MS = 3000L
        private const val MAX_RETRIES = 2
    }

    private val generativeModel = GenerativeModel(
        modelName = MODEL_NAME,
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.3f
            topK = 32
            topP = 0.8f
            maxOutputTokens = 1000
            responseMimeType = "application/json"
            responseSchema = Schema.obj {
                // Schema definition matching pose_suggestions.schema.json
                required("suggestions", Schema.arr(Schema.obj {
                    required("id", Schema.str)
                    required("category", Schema.enum(listOf("posture", "alignment", "movement", "balance", "strength")))
                    required("title", Schema.str)
                    required("description", Schema.str)
                    required("action_steps", Schema.arr(Schema.str))
                    required("priority", Schema.enum(listOf("high", "medium", "low")))
                }))
                required("analysis_metadata", Schema.obj {
                    required("timestamp", Schema.str)
                    required("confidence_score", Schema.num)
                    required("pose_quality", Schema.enum(listOf("excellent", "good", "fair", "poor")))
                })
            }
        }
    )

    override suspend fun generateSuggestions(
        poseLandmarks: List<PoseLandmark>,
        context: CoachingContext
    ): Result<PoseSuggestions> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildPoseAnalysisPrompt(poseLandmarks, context)

            val response = withTimeout(RESPONSE_TIMEOUT_MS) {
                generativeModel.generateContent(prompt)
            }

            val responseText = response.text ?: throw IllegalStateException("Empty response from Gemini")

            // Validate response against schema
            val validationResult = schemaValidator.validate(responseText)
            if (!validationResult.isValid) {
                throw IllegalStateException("Response schema validation failed: ${validationResult.errors}")
            }

            val suggestions = Json.decodeFromString<PoseSuggestions>(responseText)

            // Ensure exactly 3 suggestions
            if (suggestions.suggestions.size != 3) {
                throw IllegalStateException("Expected exactly 3 suggestions, got ${suggestions.suggestions.size}")
            }

            Result.success(suggestions)

        } catch (e: Exception) {
            Log.e("GeminiPoseCoach", "Failed to generate suggestions", e)
            Result.failure(e)
        }
    }

    private fun buildPoseAnalysisPrompt(
        landmarks: List<PoseLandmark>,
        context: CoachingContext
    ): String {
        return """
        Analyze the following pose landmarks and provide exactly 3 actionable coaching suggestions.

        Pose Landmarks (x, y, z, visibility):
        ${landmarks.joinToString("\n") { "${it.landmarkType()}: (${it.x()}, ${it.y()}, ${it.z()}, ${it.visibility()})" }}

        Context:
        - Activity: ${context.activity}
        - User Level: ${context.userLevel}
        - Previous Feedback: ${context.previousFeedback}

        Requirements:
        1. Provide exactly 3 suggestions with different priorities (high, medium, low)
        2. Each suggestion must include specific action steps
        3. Focus on posture, alignment, movement, balance, or strength
        4. Response must follow the exact JSON schema provided
        """.trimIndent()
    }
}
```

### Offline Fallback Implementation

```kotlin
// File: app/src/main/kotlin/com/posecoach/ai/FakePoseSuggestionClient.kt
class FakePoseSuggestionClient : PoseSuggestionClient {

    private val fallbackSuggestions = listOf(
        PoseSuggestion(
            id = "posture-001",
            category = "posture",
            title = "Improve Shoulder Alignment",
            description = "Keep your shoulders relaxed and aligned over your hips for better posture.",
            actionSteps = listOf(
                "Pull your shoulder blades back and down",
                "Keep your chest open and lifted",
                "Avoid hunching forward"
            ),
            priority = "high"
        ),
        PoseSuggestion(
            id = "balance-001",
            category = "balance",
            title = "Engage Core Muscles",
            description = "Strengthen your core to improve overall stability and balance.",
            actionSteps = listOf(
                "Gently engage your abdominal muscles",
                "Keep your spine neutral",
                "Breathe steadily while maintaining engagement"
            ),
            priority = "medium"
        ),
        PoseSuggestion(
            id = "alignment-001",
            category = "alignment",
            title = "Check Hip Alignment",
            description = "Ensure your hips are level and properly aligned for optimal movement.",
            actionSteps = listOf(
                "Stand with feet hip-width apart",
                "Keep weight evenly distributed",
                "Avoid tilting to one side"
            ),
            priority = "low"
        )
    )

    override suspend fun generateSuggestions(
        poseLandmarks: List<PoseLandmark>,
        context: CoachingContext
    ): Result<PoseSuggestions> = withContext(Dispatchers.IO) {
        // Simulate network delay
        delay(500)

        val suggestions = PoseSuggestions(
            suggestions = fallbackSuggestions,
            analysisMetadata = AnalysisMetadata(
                timestamp = Clock.System.now().toString(),
                confidenceScore = 0.8,
                poseQuality = "good"
            )
        )

        Result.success(suggestions)
    }
}
```

---

## Week 4: Privacy & Consent Implementation

### Consent Management System

```kotlin
// File: app/src/main/kotlin/com/posecoach/privacy/ConsentManager.kt
class ConsentManager(
    private val context: Context,
    private val preferences: SharedPreferences
) {
    companion object {
        private const val CONSENT_VERSION = "1.0"
        private const val PREF_CONSENT_GIVEN = "consent_given"
        private const val PREF_CONSENT_VERSION = "consent_version"
        private const val PREF_CONSENT_TIMESTAMP = "consent_timestamp"
    }

    private val _consentState = MutableLiveData<ConsentState>()
    val consentState: LiveData<ConsentState> = _consentState

    fun checkConsentStatus(): ConsentState {
        val hasConsent = preferences.getBoolean(PREF_CONSENT_GIVEN, false)
        val consentVersion = preferences.getString(PREF_CONSENT_VERSION, "")

        return when {
            !hasConsent -> ConsentState.NotGiven
            consentVersion != CONSENT_VERSION -> ConsentState.UpdateRequired
            else -> ConsentState.Given
        }
    }

    fun requestConsent(activity: AppCompatActivity): Flow<ConsentResult> = flow {
        val consentDialog = ConsentDialogFragment.newInstance()

        val result = suspendCancellableCoroutine<ConsentResult> { continuation ->
            consentDialog.setConsentListener { granted, settings ->
                if (granted) {
                    saveConsentDecision(settings)
                    continuation.resume(ConsentResult.Granted(settings))
                } else {
                    continuation.resume(ConsentResult.Denied)
                }
            }

            consentDialog.show(activity.supportFragmentManager, "consent")

            continuation.invokeOnCancellation {
                if (consentDialog.isAdded) {
                    consentDialog.dismiss()
                }
            }
        }

        emit(result)
    }

    private fun saveConsentDecision(settings: ConsentSettings) {
        preferences.edit()
            .putBoolean(PREF_CONSENT_GIVEN, true)
            .putString(PREF_CONSENT_VERSION, CONSENT_VERSION)
            .putLong(PREF_CONSENT_TIMESTAMP, System.currentTimeMillis())
            .putBoolean("data_processing_consent", settings.dataProcessing)
            .putBoolean("analytics_consent", settings.analytics)
            .putBoolean("ai_analysis_consent", settings.aiAnalysis)
            .apply()

        _consentState.value = ConsentState.Given
    }

    fun revokeConsent() {
        preferences.edit()
            .putBoolean(PREF_CONSENT_GIVEN, false)
            .apply()

        _consentState.value = ConsentState.NotGiven

        // Clear all user data
        DataClearanceManager.clearAllUserData(context)
    }
}
```

### Data Minimization Engine

```kotlin
// File: app/src/main/kotlin/com/posecoach/privacy/DataMinimizationEngine.kt
object DataMinimizationEngine {

    fun extractMinimalPoseData(pose: Pose): MinimalPoseData {
        // Extract only essential landmarks, never raw image data
        val essentialLandmarks = pose.allPoseLandmarks
            .filter { landmark ->
                // Only include landmarks with high confidence
                landmark.visibility() > 0.7f
            }
            .map { landmark ->
                MinimalLandmark(
                    type = landmark.landmarkType(),
                    normalizedX = landmark.x(),
                    normalizedY = landmark.y(),
                    visibility = landmark.visibility()
                    // Note: No Z coordinate or raw pixel data
                )
            }

        return MinimalPoseData(
            landmarks = essentialLandmarks,
            timestamp = System.currentTimeMillis(),
            sessionId = generateAnonymousSessionId()
        )
    }

    fun validateDataMinimization(data: Any): ValidationResult {
        return when (data) {
            is Bitmap, is ByteArray -> {
                ValidationResult.Violation("Raw image data detected")
            }
            is MinimalPoseData -> {
                if (data.landmarks.any { it.containsRawPixelData() }) {
                    ValidationResult.Violation("Raw pixel coordinates detected")
                } else {
                    ValidationResult.Valid
                }
            }
            else -> ValidationResult.Valid
        }
    }

    private fun generateAnonymousSessionId(): String {
        // Generate anonymous, non-traceable session identifier
        return UUID.randomUUID().toString().replace("-", "").take(16)
    }
}
```

---

## Week 5: CI/CD & Quality Assurance

### GitHub Actions CI Pipeline

```yaml
# File: .github/workflows/android-ci.yml
name: Android CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Cache Gradle packages
      uses: actions/cache@v3
      with:
        path: |
          ~/.gradle/caches
          ~/.gradle/wrapper
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
        restore-keys: |
          ${{ runner.os }}-gradle-

    - name: Run unit tests
      run: ./gradlew test

    - name: Run instrumented tests
      uses: reactivecircus/android-emulator-runner@v2
      with:
        api-level: 30
        script: ./gradlew connectedAndroidTest

    - name: Generate test coverage report
      run: ./gradlew jacocoTestReport

    - name: Validate test coverage
      run: |
        COVERAGE=$(grep -o 'Total.*[0-9]\{1,3\}%' app/build/reports/jacoco/jacocoTestReport/html/index.html | grep -o '[0-9]\{1,3\}%' | grep -o '[0-9]\{1,3\}')
        if [ $COVERAGE -lt 80 ]; then
          echo "Test coverage $COVERAGE% is below required 80%"
          exit 1
        fi
        echo "Test coverage: $COVERAGE%"

  performance-test:
    runs-on: ubuntu-latest
    needs: test
    steps:
    - uses: actions/checkout@v4

    - name: Run performance benchmarks
      run: ./scripts/performance-benchmark.sh

    - name: Validate performance metrics
      run: |
        # Check inference time requirements
        python3 scripts/validate-performance.py \
          --inference-threshold 30 \
          --memory-threshold 200 \
          --fps-threshold 60

  privacy-compliance:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4

    - name: Privacy compliance audit
      run: ./scripts/privacy-audit.sh

    - name: GDPR compliance check
      run: |
        # Validate no raw image processing
        if grep -r "Bitmap\|ByteArray.*image" app/src/main/; then
          echo "Potential raw image processing detected"
          exit 1
        fi

        # Check consent management
        if ! grep -r "ConsentManager" app/src/main/; then
          echo "Consent management not found"
          exit 1
        fi

  security-scan:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4

    - name: Run security scan
      uses: securecodewarrior/github-action-add-sarif@v1
      with:
        sarif-file: 'security-scan-results.sarif'

    - name: Dependency vulnerability check
      run: ./gradlew dependencyCheckAnalyze
```

### Performance Validation Script

```bash
#!/bin/bash
# File: scripts/performance-benchmark.sh

set -e

echo "Starting performance benchmark validation..."

# Build the app in release mode
./gradlew assembleRelease

# Install on connected device/emulator
adb install app/build/outputs/apk/release/app-release.apk

# Run performance tests
echo "Running pose inference performance test..."
adb shell am instrument -w \
    -e class com.posecoach.performance.PoseInferencePerformanceTest \
    com.posecoach.test/androidx.test.runner.AndroidJUnitRunner

# Extract performance metrics
INFERENCE_TIME=$(adb shell cat /sdcard/performance_metrics.json | jq '.averageInferenceTimeMs')
MEMORY_USAGE=$(adb shell cat /sdcard/performance_metrics.json | jq '.peakMemoryMB')
FRAME_RATE=$(adb shell cat /sdcard/performance_metrics.json | jq '.averageFrameRate')

echo "Performance Results:"
echo "  Average Inference Time: ${INFERENCE_TIME}ms"
echo "  Peak Memory Usage: ${MEMORY_USAGE}MB"
echo "  Average Frame Rate: ${FRAME_RATE}fps"

# Validate against requirements
if (( $(echo "$INFERENCE_TIME > 30" | bc -l) )); then
    echo "❌ FAIL: Inference time ${INFERENCE_TIME}ms exceeds 30ms requirement"
    exit 1
fi

if (( $(echo "$MEMORY_USAGE > 200" | bc -l) )); then
    echo "❌ FAIL: Memory usage ${MEMORY_USAGE}MB exceeds 200MB requirement"
    exit 1
fi

if (( $(echo "$FRAME_RATE < 60" | bc -l) )); then
    echo "❌ FAIL: Frame rate ${FRAME_RATE}fps below 60fps requirement"
    exit 1
fi

echo "✅ All performance requirements met!"
```

---

## Integration Guidelines

### Development Workflow

1. **Feature Branch Strategy**
   - Create feature branches from `develop`
   - Use descriptive naming: `feature/week1-production-ui`
   - Regular integration with main development branch

2. **Code Review Process**
   - Minimum 2 reviewers for production code
   - Automated checks must pass before merge
   - Performance impact assessment required

3. **Testing Strategy**
   - Unit tests for all business logic (>80% coverage)
   - Integration tests for critical paths
   - Performance regression tests
   - Privacy compliance validation

### Performance Monitoring

```kotlin
// File: app/src/main/kotlin/com/posecoach/monitoring/ProductionMetrics.kt
object ProductionMetrics {

    fun trackInferencePerformance(
        inferenceTimeMs: Long,
        poseCount: Int,
        deviceInfo: DeviceInfo
    ) {
        val metrics = mapOf(
            "inference_time_ms" to inferenceTimeMs,
            "pose_count" to poseCount,
            "device_model" to deviceInfo.model,
            "api_level" to deviceInfo.apiLevel
        )

        // Log to analytics (with user consent)
        if (ConsentManager.hasAnalyticsConsent()) {
            Analytics.track("pose_inference_performance", metrics)
        }

        // Local performance monitoring
        PerformanceDatabase.insertMetrics(metrics)
    }

    fun generatePerformanceReport(): PerformanceReport {
        return PerformanceReport(
            averageInferenceTime = PerformanceDatabase.getAverageInferenceTime(),
            memoryUsageStats = PerformanceDatabase.getMemoryStats(),
            frameRateStats = PerformanceDatabase.getFrameRateStats(),
            errorRate = PerformanceDatabase.getErrorRate()
        )
    }
}
```

This technical architecture provides the detailed implementation specifications needed for Sprint P3, ensuring production-ready quality while maintaining alignment with CLAUDE.md requirements and privacy best practices.