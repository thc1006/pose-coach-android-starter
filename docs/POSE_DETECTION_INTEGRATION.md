# MediaPipe Pose Detection Integration Guide

## Overview

This document describes the complete MediaPipe Pose detection implementation for Android with comprehensive real-time processing capabilities.

## Architecture

The implementation follows a clean repository pattern with the following key components:

### Core Components

1. **PoseRepository Interface** (`core-pose/repository/PoseRepository.kt`)
   - Abstract interface for pose detection operations
   - Supports async detection with `detectAsync(bitmap, timestampMs)`
   - LIVE_STREAM mode for real-time processing

2. **FakePoseRepository** (`core-pose/repository/FakePoseRepository.kt`)
   - Testing and development implementation
   - Generates realistic 33-point pose landmarks
   - Multi-person pose simulation
   - Variable lighting conditions support

3. **MediaPipePoseRepository** (`core-pose/repository/MediaPipePoseRepository.kt`)
   - Production MediaPipe integration
   - LIVE_STREAM mode with GPU acceleration
   - Multi-person detection (up to 5 people)
   - Performance monitoring and adaptation

4. **CameraPoseAnalyzer** (`core-pose/camera/CameraPoseAnalyzer.kt`)
   - CameraX integration for real-time camera input
   - Image format conversion (YUV_420_888, NV21)
   - Performance optimization and quality adaptation

## Performance Specifications

### Target Performance
- **Inference Time**: < 30ms per frame @720p
- **Frame Rate**: 30 FPS sustained
- **Multi-person**: Up to 5 people simultaneously
- **Memory Usage**: Stable during extended operation

### Performance Monitoring
- Real-time inference time tracking
- Frame drop detection and logging
- Automatic quality degradation when needed
- Performance metrics collection via `PerformanceTracker`

## Usage Examples

### Basic Setup

```kotlin
// Initialize repository
val poseRepository = MediaPipePoseRepository()
poseRepository.init(context, "pose_landmarker_lite.task")

// Create listener
val listener = object : PoseDetectionListener {
    override fun onPoseDetected(result: PoseLandmarkResult) {
        // Process 33-point pose landmarks
        val landmarks = result.landmarks
        val inferenceTime = result.inferenceTimeMs

        // Access specific landmarks
        val nose = landmarks[PoseLandmarks.NOSE]
        val leftShoulder = landmarks[PoseLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks[PoseLandmarks.RIGHT_SHOULDER]
    }

    override fun onPoseDetectionError(error: PoseDetectionError) {
        Log.e("Pose", "Detection error: ${error.message}")
    }
}

// Start detection
poseRepository.start(listener)
```

### CameraX Integration

```kotlin
// Create analyzer
val poseAnalyzer = CameraPoseAnalyzer(poseRepository, listener)

// Configure ImageAnalysis
val imageAnalysis = ImageAnalysis.Builder()
    .setTargetResolution(Size(720, 1280))
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()

imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context), poseAnalyzer)

// Bind to lifecycle
val cameraProvider = ProcessCameraProvider.getInstance(context).get()
val camera = cameraProvider.bindToLifecycle(
    lifecycleOwner,
    cameraSelector,
    preview,
    imageAnalysis
)
```

### Multi-Person Detection

```kotlin
// Configure for multiple people
val poseRepository = MediaPipePoseRepository()
poseRepository.configurePersonCount(3) // Detect up to 3 people
poseRepository.init(context)
```

### Performance Optimization

```kotlin
// Monitor performance
val metrics = poseRepository.getPerformanceMetrics()
if (!metrics.isPerformanceGood) {
    // Reduce quality for better performance
    poseAnalyzer.setProcessingQuality(ProcessingQuality.MEDIUM)
}

// Force adaptation if needed
if (metrics.avgInferenceTimeMs > 50) {
    poseRepository.forcePerformanceAdaptation()
}
```

## Testing Strategy

### Unit Tests
- **FakePoseRepositoryTest**: Validates fake implementation behavior
- **MediaPipePoseRepositoryTest**: Tests real implementation (mocked MediaPipe)
- **CameraPoseAnalyzerTest**: Tests camera integration
- **PoseRepositoryBenchmarkTest**: Performance validation

### Performance Benchmarks
- Inference time consistency testing
- Concurrent request handling
- Memory usage validation
- High-frequency detection testing

### Edge Cases Tested
- No pose detected scenarios
- Poor lighting conditions
- Multi-person detection
- Processing timeouts
- Memory constraints

## Error Handling

### Error Classification
- `INITIALIZATION_FAILED`: MediaPipe setup issues
- `DETECTION_FAILED`: Frame processing errors
- `MODEL_LOADING_FAILED`: Asset loading problems
- `INSUFFICIENT_LIGHTING`: Poor visibility conditions
- `PROCESSING_TIMEOUT`: Performance issues
- `GPU_ERROR`: Hardware acceleration problems
- `MEMORY_ERROR`: Resource constraints

### Recovery Strategies
- Automatic quality degradation
- GPU to CPU fallback
- Frame skipping for performance
- Memory cleanup and optimization

## Integration Points

### Existing System Integration
- **StablePoseGate**: Pose stability filtering
- **EnhancedStablePoseGate**: Advanced stability analysis
- **PerformanceTracker**: Metrics collection
- **PoseLandmarks**: 33-point landmark definitions

### Privacy Manager Integration
```kotlin
// Check privacy settings before detection
if (privacyManager.isPoseDetectionAllowed()) {
    poseRepository.start(listener)
}
```

### Multi-Person Pose Manager
```kotlin
// Future extension for multi-person tracking
class MultiPersonPoseManager {
    fun trackMultiplePeople(results: List<PoseLandmarkResult>) {
        // Person tracking and identification logic
    }
}
```

## Configuration Options

### Model Selection
- `pose_landmarker_lite.task`: Fast, lower accuracy
- `pose_landmarker_full.task`: Slower, higher accuracy
- `pose_landmarker_heavy.task`: Best accuracy, highest latency

### Quality Settings
```kotlin
// High quality (720p, 30fps)
poseAnalyzer.setProcessingQuality(ProcessingQuality.HIGH)

// Medium quality (480p, balanced)
poseAnalyzer.setProcessingQuality(ProcessingQuality.MEDIUM)

// Low quality (240p, maximum performance)
poseAnalyzer.setProcessingQuality(ProcessingQuality.LOW)
```

### Detection Parameters
```kotlin
val options = PoseLandmarkerOptions.builder()
    .setMinPoseDetectionConfidence(0.5f)
    .setMinPosePresenceConfidence(0.5f)
    .setMinTrackingConfidence(0.5f)
    .setNumPoses(3) // Multi-person
    .build()
```

## Performance Optimization Guide

### Memory Management
- Bitmap recycling after processing
- Buffer reuse for image conversion
- Proper resource cleanup in lifecycle events

### Processing Optimization
- Adaptive quality based on performance
- Frame skipping during overload
- Background thread processing
- GPU acceleration when available

### Battery Optimization
- Processing quality reduction on low battery
- Frame rate adaptation
- CPU thermal throttling detection

## Troubleshooting

### Common Issues

1. **High Inference Time**
   - Reduce target resolution
   - Switch to lighter model
   - Enable degraded mode

2. **Memory Leaks**
   - Call `cleanup()` on analyzer
   - Properly close ImageProxy objects
   - Recycle bitmaps after use

3. **GPU Errors**
   - Fallback to CPU processing
   - Check device OpenGL support
   - Update graphics drivers

4. **Detection Failures**
   - Verify lighting conditions
   - Check camera positioning
   - Validate model file integrity

### Debug Information
```kotlin
// Enable detailed logging
poseRepository.getPerformanceMetrics().let { metrics ->
    Log.d("Pose", "Avg inference: ${metrics.avgInferenceTimeMs}ms")
    Log.d("Pose", "FPS: ${metrics.avgFps}")
    Log.d("Pose", "Dropped frames: ${metrics.droppedFrames}")
}

// Check processing statistics
poseAnalyzer.getProcessingStats().let { stats ->
    Log.d("Camera", "Total frames: ${stats.totalFrames}")
    Log.d("Camera", "Drop rate: ${stats.dropRate}")
    Log.d("Camera", "Quality: ${stats.currentQuality}")
}
```

## Future Enhancements

### Planned Features
- Real-time pose tracking across frames
- Pose gesture recognition
- 3D pose estimation
- Pose comparison and scoring
- Cloud-based pose analysis

### Optimization Opportunities
- Model quantization for mobile
- Custom MediaPipe graphs
- Hardware-specific optimizations
- Pose caching and prediction

## Dependencies

### Required Dependencies
```kotlin
// MediaPipe
implementation("com.google.mediapipe:tasks-vision:0.10.14")

// CameraX
implementation("androidx.camera:camera-core:1.3.1")
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// Logging
implementation("com.jakewharton.timber:timber:5.0.1")
```

### Model Assets
Place model files in `app/src/main/assets/`:
- `pose_landmarker_lite.task` (recommended)
- `pose_landmarker_full.task` (optional)
- `pose_landmarker_heavy.task` (optional)

## License and Attribution

This implementation uses Google's MediaPipe framework:
- MediaPipe License: Apache 2.0
- Model Attribution: Google Research

## Support

For issues and questions:
1. Check performance metrics and logs
2. Verify model files and dependencies
3. Test with FakePoseRepository for isolation
4. Review error handling documentation
5. Submit issues with performance data and device information