# ADR-002: MediaPipe for Pose Detection

## Status
Accepted

## Context
The Pose Coach Android application requires real-time pose detection capabilities with:
- <30ms inference latency for responsive feedback
- High accuracy pose landmark detection (33 points)
- On-device processing for privacy compliance
- Efficient resource utilization on mobile devices
- Support for various Android device capabilities
- Integration with camera feed for real-time analysis

We need to select a pose detection solution that meets these performance and privacy requirements.

## Decision
We will use **Google MediaPipe Tasks Vision Pose Landmarker** as our primary pose detection solution.

### Specific Implementation:
- **MediaPipe Tasks Vision 0.10.14** (or latest stable version)
- **Pose Landmarker model** with 33 landmark points
- **On-device inference** with GPU acceleration when available
- **Live stream processing mode** for real-time camera integration
- **Confidence thresholding** for quality assurance

### Configuration:
```kotlin
val options = PoseLandmarkerOptions.builder()
    .setBaseOptions(
        BaseOptions.builder()
            .setModelAssetPath("pose_landmarker.task")
            .setDelegate(BaseOptions.Delegate.GPU) // Fallback to CPU
            .build()
    )
    .setRunningMode(RunningMode.LIVE_STREAM)
    .setResultListener(this::processPoseResult)
    .setErrorListener(this::handleError)
    .setNumPoses(1)
    .setMinPoseDetectionConfidence(0.5f)
    .setMinPosePresenceConfidence(0.5f)
    .setMinTrackingConfidence(0.5f)
    .build()
```

## Rationale

### Technical Advantages:
1. **Performance**: Optimized for mobile inference with <30ms typical latency
2. **Accuracy**: State-of-the-art pose detection with confidence scores
3. **On-Device**: Complete privacy compliance with local processing
4. **Hardware Acceleration**: GPU delegation for improved performance
5. **Comprehensive Landmarks**: 33 pose landmarks for detailed analysis
6. **Real-Time**: Live stream mode for camera integration
7. **Cross-Platform**: Consistent results across Android devices

### Privacy Benefits:
- **No Data Transmission**: All processing occurs on-device
- **No Model Training**: Pre-trained models eliminate data collection needs
- **Zero Cloud Dependency**: Fully functional offline
- **User Control**: Complete user control over data processing

### Performance Characteristics:
- **Inference Time**: 15-25ms on modern Android devices
- **Memory Usage**: ~50-100MB model size
- **CPU Utilization**: Efficient multi-threading
- **Battery Impact**: Optimized for mobile power consumption

### Integration Benefits:
- **CameraX Compatibility**: Native integration with Android CameraX
- **Lifecycle Aware**: Proper Android lifecycle management
- **Error Handling**: Comprehensive error reporting and recovery
- **Configuration Flexibility**: Adjustable parameters for different use cases

## Alternatives Considered

### 1. TensorFlow Lite with Custom Models
- **Pros**: Full control over model architecture, potential for smaller models
- **Cons**: Requires ML expertise, longer development time, maintenance burden, no guarantee of better performance

### 2. Firebase ML Kit Pose Detection
- **Pros**: Easy integration, Google ecosystem
- **Cons**: Cloud dependency for advanced features, privacy concerns, limited customization

### 3. OpenPose or Similar Open Source Solutions
- **Pros**: Open source, customizable
- **Cons**: Complex mobile optimization, performance challenges, integration complexity

### 4. Custom Computer Vision Solution
- **Pros**: Complete control
- **Cons**: Extremely complex, long development time, inferior accuracy, performance challenges

### 5. Cloud-Based Pose Detection APIs
- **Pros**: Potentially higher accuracy, no local processing
- **Cons**: Privacy concerns, network dependency, latency issues, cost implications

## Technical Implementation

### Model Configuration:
```kotlin
class MediaPipeConfiguration {
    companion object {
        const val MODEL_PATH = "pose_landmarker.task"
        const val RUNNING_MODE = RunningMode.LIVE_STREAM
        const val NUM_POSES = 1
        const val MIN_POSE_DETECTION_CONFIDENCE = 0.5f
        const val MIN_POSE_PRESENCE_CONFIDENCE = 0.5f
        const val MIN_TRACKING_CONFIDENCE = 0.5f
        const val OUTPUT_SEGMENTATION_MASKS = false
    }
}
```

### Performance Optimization:
```kotlin
class PerformanceOptimizedPoseDetector {
    private fun optimizeForDevice(deviceCapabilities: DeviceCapabilities) {
        val options = when (deviceCapabilities.performanceLevel) {
            HIGH_PERFORMANCE -> {
                // Use highest quality settings
                PoseLandmarkerOptions.builder()
                    .setMinPoseDetectionConfidence(0.7f)
                    .setMinTrackingConfidence(0.5f)
                    .build()
            }
            MEDIUM_PERFORMANCE -> {
                // Balanced settings
                PoseLandmarkerOptions.builder()
                    .setMinPoseDetectionConfidence(0.6f)
                    .setMinTrackingConfidence(0.4f)
                    .build()
            }
            LOW_PERFORMANCE -> {
                // Optimized for performance
                PoseLandmarkerOptions.builder()
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinTrackingConfidence(0.3f)
                    .build()
            }
        }
    }
}
```

## Consequences

### Positive:
- **Privacy Compliance**: Fully on-device processing
- **Performance**: Meets <30ms inference requirement
- **Reliability**: Production-tested Google solution
- **Maintenance**: Minimal maintenance burden
- **Integration**: Seamless Android integration
- **Accuracy**: High-quality pose detection
- **Resource Efficiency**: Optimized for mobile devices

### Negative:
- **Model Size**: ~50-100MB addition to APK size
- **Vendor Lock-in**: Dependency on Google's MediaPipe ecosystem
- **Limited Customization**: Cannot modify core detection algorithms
- **Android Only**: Solution specific to Android platform
- **Version Dependencies**: Need to manage MediaPipe version updates

### Risk Mitigation:
- **APK Size**: Use Android App Bundle for dynamic delivery
- **Vendor Lock-in**: Abstract pose detection behind interfaces for future flexibility
- **Customization**: Implement post-processing for custom analysis
- **Version Management**: Establish clear update and testing procedures

## Quality Assurance

### Testing Strategy:
```kotlin
@Test
fun `pose detection should complete within latency requirements`() = runBlocking {
    val testFrames = generateTestFrames(100)
    val latencies = mutableListOf<Long>()

    testFrames.forEach { frame ->
        val startTime = System.nanoTime()
        val result = poseDetector.detectPose(frame)
        val endTime = System.nanoTime()

        val latencyMs = (endTime - startTime) / 1_000_000
        latencies.add(latencyMs)
    }

    val averageLatency = latencies.average()
    assertThat(averageLatency).isLessThan(30.0) // <30ms requirement
}
```

### Performance Monitoring:
- Real-time latency tracking
- Memory usage monitoring
- Error rate measurement
- Device capability correlation

## Integration Points

### CameraX Integration:
```kotlin
private val imageAnalyzer = ImageAnalysis.Analyzer { imageProxy ->
    val mpImage = imageProxy.toMPImage()
    val timestamp = SystemClock.uptimeMillis()

    poseLandmarker.detectAsync(mpImage, timestamp)
    imageProxy.close()
}
```

### Error Handling:
- Network-independent operation
- Graceful degradation for low-confidence detections
- Recovery mechanisms for initialization failures
- Fallback strategies for unsupported devices

## Compliance and Standards

This decision supports:
- **Privacy-First Design**: On-device processing aligns with privacy requirements
- **Performance Standards**: Meets <30ms inference latency requirement
- **Quality Standards**: Professional-grade pose detection accuracy
- **Maintainability**: Reduces custom ML development and maintenance
- **Testing Strategy**: Enables comprehensive testing of pose detection pipeline

## Future Considerations

### Potential Enhancements:
- Multi-person pose detection for group exercises
- Custom model fine-tuning for specific exercise types
- Integration with newer MediaPipe capabilities
- Performance optimization for emerging hardware

### Migration Path:
- Interface abstraction allows for future pose detection solution changes
- Performance benchmarking framework enables objective comparison
- Modular architecture supports gradual migration if needed

## References
- [MediaPipe Pose Landmarker](https://developers.google.com/mediapipe/solutions/vision/pose_landmarker)
- [MediaPipe Android Integration](https://developers.google.com/mediapipe/framework/getting_started/android)
- [CameraX Documentation](https://developer.android.com/training/camerax)
- [Android Performance Best Practices](https://developer.android.com/topic/performance)