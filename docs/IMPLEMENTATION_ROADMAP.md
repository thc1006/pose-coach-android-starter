# Pose Coach Android - 4-Week Implementation Roadmap

## Overview

This roadmap provides a comprehensive 4-week implementation plan for the Pose Coach Android application, following TDD methodology and SPARC principles as specified in CLAUDE.md. Each week builds incrementally toward a fully functional pose coaching camera app with real-time feedback.

## Architecture Foundation

### Core Modules
- **core-geom**: Geometry and smoothing (OneEuroFilter)
- **core-pose**: 33-point definitions, skeleton topology, pose stability gate
- **suggestions-api**: Gemini 2.5 structured output schema and client
- **app**: Android CameraX + MediaPipe integration

### Key Technologies
- **CameraX**: Camera pipeline with PreviewView + ImageAnalysis
- **MediaPipe**: Pose Landmarker in LIVE_STREAM mode
- **Material Design 3**: Modern Android UI components
- **Gemini 2.5**: Structured output for pose feedback
- **Kotlin Coroutines**: Async processing pipeline

---

## Week 1: Core Camera Implementation

### Goals
- Establish CameraX pipeline with PreviewView
- Implement basic ImageAnalysis configuration
- Create Material Design 3 UI foundation
- Handle camera permissions and lifecycle

### Day 1-2: CameraX Foundation

#### Files to Create/Modify
```
app/src/main/kotlin/com/posecoach/app/
├── camera/
│   ├── CameraXManager.kt (✓ existing - enhance)
│   ├── CameraLifecycleManager.kt (✓ existing - enhance)
│   ├── CameraConfigurationManager.kt (new)
│   └── CameraPermissionManager.kt (new)
├── ui/
│   ├── MainActivity.kt (new)
│   ├── CameraFragment.kt (new)
│   └── PermissionDialogFragment.kt (new)
└── utils/
    ├── Extensions.kt (new)
    └── Constants.kt (new)
```

#### Test Cases to Implement
```
app/src/test/kotlin/com/posecoach/app/camera/
├── CameraXManagerTest.kt
├── CameraLifecycleManagerTest.kt
├── CameraConfigurationManagerTest.kt
└── CameraPermissionManagerTest.kt

app/src/androidTest/kotlin/com/posecoach/app/
├── CameraIntegrationTest.kt
└── PermissionFlowTest.kt
```

#### Performance Benchmarks
- Camera initialization time: < 500ms
- Preview startup: < 300ms
- Memory usage: < 150MB baseline
- Frame processing: prepare for 30fps capability

#### Implementation Details

**CameraConfigurationManager.kt**
```kotlin
class CameraConfigurationManager {
    // Camera resolution optimization
    // Preview configuration
    // ImageAnalysis use case setup
    // Error handling and fallbacks
}
```

**Test-First Development Example**
```kotlin
@Test
fun `camera initialization should complete within 500ms`() = runTest {
    val startTime = System.currentTimeMillis()
    cameraManager.initialize(mockContext)
    val duration = System.currentTimeMillis() - startTime
    assertThat(duration).isLessThan(500)
}
```

### Day 3-4: Material Design 3 UI

#### Files to Create/Modify
```
app/src/main/res/
├── layout/
│   ├── activity_main.xml
│   ├── fragment_camera.xml
│   └── dialog_permission.xml
├── values/
│   ├── themes.xml (update)
│   ├── colors.xml (update)
│   └── dimens.xml (new)
└── navigation/
    └── nav_graph.xml (new)
```

#### UI Components
- **CameraPreview**: Full-screen PreviewView with overlay container
- **PermissionDialog**: Material 3 dialog for camera permissions
- **StatusBar**: Connection status and performance indicators
- **Navigation**: Fragment-based navigation with Safe Args

#### Test Cases
```
app/src/androidTest/kotlin/com/posecoach/app/ui/
├── MainActivityTest.kt
├── CameraFragmentTest.kt
└── NavigationTest.kt
```

### Day 5: ImageAnalysis Pipeline

#### Files to Create/Modify
```
app/src/main/kotlin/com/posecoach/app/
├── analysis/
│   ├── ImageAnalysisManager.kt (new)
│   ├── FrameProcessor.kt (new)
│   └── AnalysisResultHandler.kt (new)
└── models/
    └── CameraModels.kt (new)
```

#### Key Features
- ImageAnalysis use case configuration
- Frame preprocessing pipeline
- Result callback system
- Error handling and recovery

#### Performance Targets
- Frame processing latency: < 33ms (30fps)
- Memory allocation: < 10MB per frame
- CPU usage: < 40% on mid-range devices

### Day 6-7: Integration & Testing

#### Integration Points
- CameraX lifecycle with Fragment lifecycle
- ImageAnalysis with future MediaPipe integration
- UI state management with camera states
- Permission flow with camera initialization

#### Test Coverage Requirements
- Unit tests: > 85% coverage
- Integration tests: Core camera flows
- UI tests: Permission and navigation flows
- Performance tests: Benchmark compliance

#### Week 1 Validation Criteria
- [ ] Camera preview displays correctly
- [ ] Permissions handled gracefully
- [ ] ImageAnalysis pipeline ready for MediaPipe
- [ ] Material Design 3 UI components working
- [ ] Performance benchmarks met
- [ ] Test coverage > 85%

---

## Week 2: MediaPipe Integration

### Goals
- Integrate MediaPipe Pose Landmarker
- Implement LIVE_STREAM mode processing
- Create StablePoseGate logic
- Optimize for <30ms processing time

### Day 8-9: MediaPipe Setup

#### Files to Create/Modify
```
app/src/main/kotlin/com/posecoach/app/
├── mediapipe/
│   ├── MediaPipeManager.kt (new)
│   ├── PoseLandmarkerWrapper.kt (new)
│   ├── MediaPipeInitializer.kt (new)
│   └── PoseDetectionCallback.kt (new)
├── models/
│   ├── PoseModels.kt (new)
│   └── MediaPipeModels.kt (new)
└── utils/
    └── MediaPipeUtils.kt (new)
```

#### Dependencies Update
```kotlin
// app/build.gradle.kts additions
implementation("com.google.mediapipe:tasks-vision:0.10.9")
implementation("com.google.mediapipe:framework:latest_release")
```

#### Test Cases
```
app/src/test/kotlin/com/posecoach/app/mediapipe/
├── MediaPipeManagerTest.kt
├── PoseLandmarkerWrapperTest.kt
└── PoseDetectionCallbackTest.kt
```

### Day 10-11: LIVE_STREAM Implementation

#### Key Components
- **MediaPipeManager**: Lifecycle management and configuration
- **PoseLandmarkerWrapper**: Pose detection with confidence thresholds
- **PoseDetectionCallback**: Result processing and forwarding

#### Performance Optimization
```kotlin
class MediaPipeManager {
    private val detector = PoseLandmarker.createFromOptions(
        context,
        PoseLandmarkerOptions.builder()
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setOutputSegmentationMasks(false) // Performance optimization
            .setResultListener(this::onPoseDetected)
            .build()
    )
}
```

#### Integration with CameraX
```kotlin
// In ImageAnalysisManager
private fun processFrame(imageProxy: ImageProxy) {
    val mpImage = BitmapImageBuilder(imageProxy.toBitmap()).build()
    mediaPipeManager.detectAsync(mpImage, imageProxy.imageInfo.timestamp)
    imageProxy.close()
}
```

### Day 12-13: StablePoseGate Implementation

#### Files to Create/Modify
```
core-pose/src/main/kotlin/com/posecoach/core/pose/
├── stability/
│   ├── StablePoseGate.kt (enhance existing)
│   ├── PoseStabilityAnalyzer.kt (new)
│   └── StabilityThresholdManager.kt (new)
└── models/
    └── StabilityModels.kt (new)
```

#### Key Features
- Pose confidence tracking over time
- Movement threshold detection
- Stability window configuration
- Quality score calculation

#### Test Cases
```
core-pose/src/test/kotlin/com/posecoach/core/pose/stability/
├── StablePoseGateTest.kt
├── PoseStabilityAnalyzerTest.kt
└── StabilityThresholdManagerTest.kt
```

### Day 13-14: Performance Optimization & Testing

#### Performance Targets
- MediaPipe processing: < 30ms per frame
- Total pipeline latency: < 50ms
- CPU usage: < 60% on mid-range devices
- Memory usage: < 250MB total

#### Optimization Strategies
- Frame rate adaptive processing
- Background thread optimization
- Memory pool for bitmap reuse
- Confidence threshold tuning

#### Week 2 Validation Criteria
- [ ] MediaPipe processes frames in <30ms
- [ ] StablePoseGate detects stable poses correctly
- [ ] 33-point pose landmarks extracted accurately
- [ ] Performance targets met on test devices
- [ ] Integration with Week 1 camera pipeline
- [ ] Test coverage maintained > 85%

---

## Week 3: Overlay & Visualization

### Goals
- Implement overlay rendering system
- Achieve <2px coordinate transformation accuracy
- Create skeleton visualization
- Optimize rendering performance

### Day 15-16: Overlay Foundation

#### Files to Create/Modify
```
app/src/main/kotlin/com/posecoach/app/overlay/
├── PoseOverlayView.kt (✓ existing - enhance)
├── PoseOverlayManager.kt (✓ existing - enhance)
├── CoordinateMapper.kt (✓ existing - enhance)
├── OverlayRenderer.kt (new)
└── SkeletonDrawer.kt (new)
```

#### Core Features
- Custom View overlay system
- Coordinate transformation pipeline
- Multi-layer rendering support
- Performance-optimized drawing

#### Test Cases
```
app/src/test/kotlin/com/posecoach/app/overlay/
├── CoordinateMapperTest.kt
├── OverlayRendererTest.kt
├── SkeletonDrawerTest.kt
└── PoseOverlayViewTest.kt
```

### Day 17-18: Coordinate Transformation

#### Precision Requirements
- Landmark to pixel accuracy: < 2px error
- Camera resolution independence
- Device rotation handling
- Aspect ratio compensation

#### CoordinateMapper Enhancement
```kotlin
class CoordinateMapper {
    fun mapNormalizedToPixel(
        normalizedLandmark: NormalizedLandmark,
        viewWidth: Int,
        viewHeight: Int,
        imageWidth: Int,
        imageHeight: Int
    ): PixelCoordinate {
        // High-precision transformation with <2px accuracy
        // Account for camera crop and scaling
        // Handle rotation matrices
    }
}
```

#### Accuracy Testing
```kotlin
@Test
fun `coordinate mapping accuracy should be within 2 pixels`() {
    val testCases = generateTestLandmarks()
    testCases.forEach { (normalized, expectedPixel) ->
        val actualPixel = coordinateMapper.mapNormalizedToPixel(normalized, viewWidth, viewHeight, imageWidth, imageHeight)
        val distance = calculateDistance(expectedPixel, actualPixel)
        assertThat(distance).isLessThan(2.0)
    }
}
```

### Day 19-20: Skeleton Rendering

#### Files to Create/Modify
```
app/src/main/kotlin/com/posecoach/app/
├── rendering/
│   ├── SkeletonRenderer.kt (new)
│   ├── LandmarkRenderer.kt (new)
│   ├── ConnectionRenderer.kt (new)
│   └── RenderingUtils.kt (new)
└── style/
    ├── PoseStyleConfig.kt (new)
    └── ColorScheme.kt (new)
```

#### Rendering Features
- 33-point landmark visualization
- Skeleton connection lines
- Confidence-based opacity
- Smooth animation transitions
- Customizable styling

#### Performance Optimization
- Canvas drawing optimization
- Paint object reuse
- Clipping region optimization
- Frame rate adaptive rendering

### Day 21: Multi-Device Testing

#### Device Testing Matrix
- **High-end**: Pixel 7 Pro, Samsung S23 Ultra
- **Mid-range**: Pixel 6a, Samsung A54
- **Low-end**: Budget devices with Android 7+

#### Testing Scenarios
- Different screen sizes and densities
- Various camera resolutions
- Different aspect ratios (16:9, 18:9, 20:9)
- Portrait and landscape orientations

#### Week 3 Validation Criteria
- [ ] Overlay renders correctly on all test devices
- [ ] Coordinate transformation accuracy < 2px
- [ ] Skeleton visualization smooth at 30fps
- [ ] Memory usage optimized for rendering
- [ ] Multi-device compatibility verified
- [ ] Performance benchmarks maintained

---

## Week 4: Gemini & Privacy Integration

### Goals
- Implement Gemini 2.5 structured output
- Create comprehensive privacy controls
- Build consent management UI
- Complete integration testing

### Day 22-23: Gemini 2.5 Integration

#### Files to Create/Modify
```
suggestions-api/src/main/kotlin/com/posecoach/suggestions/
├── gemini/
│   ├── GeminiClient.kt (enhance existing)
│   ├── StructuredOutputHandler.kt (new)
│   └── GeminiResponseValidator.kt (new)
├── schema/
│   ├── PoseSuggestionSchema.kt (new)
│   └── SchemaValidator.kt (new)
└── models/
    └── GeminiModels.kt (enhance existing)
```

#### Structured Output Schema
```kotlin
@Serializable
data class PoseSuggestionResponse(
    val overallScore: Float,
    val suggestions: List<PoseSuggestion>,
    val strengths: List<String>,
    val improvements: List<String>,
    val confidenceLevel: Float
)

@Serializable
data class PoseSuggestion(
    val category: String,
    val priority: Priority,
    val description: String,
    val actionableSteps: List<String>
)
```

#### Test Cases
```
suggestions-api/src/test/kotlin/com/posecoach/suggestions/
├── GeminiClientTest.kt
├── StructuredOutputHandlerTest.kt
├── SchemaValidatorTest.kt
└── integration/GeminiIntegrationTest.kt
```

### Day 24-25: Privacy Controls Implementation

#### Files to Create/Modify
```
app/src/main/kotlin/com/posecoach/app/privacy/
├── PrivacyManager.kt (✓ existing - enhance)
├── ConsentManager.kt (✓ existing - enhance)
├── DataMinimizationProcessor.kt (✓ existing - enhance)
├── PrivacySettingsActivity.kt (✓ existing - enhance)
└── ui/
    ├── ConsentDialogFragment.kt (new)
    ├── PrivacyDashboardFragment.kt (new)
    └── DataControlsFragment.kt (new)
```

#### Privacy Features
- Granular consent management
- Data retention controls
- Processing transparency
- User data export/deletion
- Privacy-preserving analytics

#### UI Components
```xml
<!-- consent_dialog.xml -->
<LinearLayout>
    <!-- Data usage explanation -->
    <!-- Granular permission toggles -->
    <!-- Clear consent actions -->
</LinearLayout>
```

### Day 26-27: Consent UI & Privacy Dashboard

#### Consent Flow
1. **Initial Setup**: First-time user onboarding
2. **Runtime Permissions**: Camera and storage access
3. **Data Processing**: Pose analysis and Gemini calls
4. **Ongoing Management**: Settings and revocation

#### Privacy Dashboard Features
- Data usage summary
- Processing activity log
- Consent status overview
- Easy privacy controls access

#### Test Cases
```
app/src/androidTest/kotlin/com/posecoach/app/privacy/
├── ConsentFlowTest.kt
├── PrivacyDashboardTest.kt
├── DataControlsTest.kt
└── PrivacyIntegrationTest.kt
```

### Day 28: Integration Testing & Validation

#### End-to-End Testing Scenarios
1. **Complete Camera Flow**: Permission → Preview → Analysis → Overlay
2. **Pose Detection Pipeline**: Camera → MediaPipe → StablePoseGate → Visualization
3. **Gemini Integration**: Stable pose → Data preparation → API call → Response handling
4. **Privacy Compliance**: Consent → Data processing → User controls

#### Performance Validation
- **Total Pipeline Latency**: < 100ms (camera to overlay)
- **Memory Usage**: < 300MB peak usage
- **Battery Impact**: < 15% drain per hour
- **Network Usage**: Optimized for mobile data

#### Final Integration Points
```
app/src/main/kotlin/com/posecoach/app/integration/
├── PoseCoachIntegration.kt (✓ existing - validate)
├── EndToEndValidator.kt (new)
└── PerformanceValidator.kt (new)
```

#### Week 4 Validation Criteria
- [ ] Gemini 2.5 structured output working correctly
- [ ] Privacy controls fully functional
- [ ] Consent management compliant
- [ ] End-to-end pipeline validated
- [ ] Performance benchmarks met
- [ ] User acceptance testing passed

---

## Success Metrics & Acceptance Criteria

### Technical Performance
- **Latency**: Camera to overlay < 100ms
- **Accuracy**: Coordinate transformation < 2px error
- **Stability**: 99%+ uptime during usage sessions
- **Memory**: Peak usage < 300MB
- **Battery**: < 15% drain per hour of active use

### Code Quality
- **Test Coverage**: > 85% unit test coverage
- **Integration Tests**: All critical paths covered
- **Performance Tests**: Benchmark compliance
- **Code Review**: All PRs reviewed and approved

### User Experience
- **Onboarding**: < 30 seconds to first pose detection
- **Privacy**: Clear consent and control mechanisms
- **Feedback**: Real-time pose suggestions
- **Accessibility**: Support for accessibility services

### Compliance & Security
- **Privacy**: GDPR/CCPA compliance
- **Data Minimization**: Only necessary data collected
- **Security**: Secure API communications
- **Consent**: Granular user control

---

## Risk Mitigation & Contingency Plans

### Technical Risks
1. **MediaPipe Performance**: Fallback to reduced frame rates
2. **Gemini API Limits**: Implement rate limiting and caching
3. **Device Compatibility**: Graceful degradation strategies
4. **Memory Constraints**: Aggressive memory management

### Schedule Risks
1. **Integration Delays**: Parallel development tracks
2. **Testing Bottlenecks**: Automated testing pipeline
3. **External Dependencies**: Mock implementations ready

### Quality Risks
1. **Performance Regression**: Continuous benchmarking
2. **Privacy Compliance**: Regular compliance audits
3. **User Experience**: Frequent usability testing

---

## Conclusion

This 4-week roadmap provides a structured approach to building a production-ready Pose Coach Android application. By following TDD methodology and maintaining focus on performance, privacy, and user experience, the implementation will deliver a robust and scalable solution.

Each week builds incrementally on previous work, ensuring continuous integration and validation. The emphasis on testing, performance monitoring, and privacy compliance ensures the final product meets enterprise standards while providing an excellent user experience.