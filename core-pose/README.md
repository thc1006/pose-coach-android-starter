# Core Pose Module

MediaPipe Pose Detection module with LIVE_STREAM mode for real-time pose landmark detection.

## Features

- **LIVE_STREAM Mode**: Asynchronous pose detection with result listener
- **Performance Tracking**: Real-time monitoring of inference times with target <30ms@720p
- **33 Pose Landmarks**: Full body pose detection with MediaPipe's standard landmark indices
- **Fake Implementation**: Testing support with FakePoseRepository

## Architecture

### Repository Pattern
- `PoseRepository`: Interface for pose detection operations
- `MediaPipePoseRepository`: Real implementation using MediaPipe
- `FakePoseRepository`: Test implementation with synthetic data

### Performance Monitoring
- `PerformanceTracker`: Tracks inference times, FPS, and dropped frames
- Window-based metrics calculation (default 30 frames)
- Automatic performance logging and warnings

## Usage

```kotlin
// Initialize repository
val repository = MediaPipePoseRepository()
repository.init(context, modelPath = "pose_landmarker_lite.task")

// Set up listener
val listener = object : PoseDetectionListener {
    override fun onPoseDetected(result: PoseLandmarkResult) {
        // Process landmarks
        println("Detected ${result.landmarks.size} landmarks")
        println("Inference time: ${result.inferenceTimeMs}ms")
    }

    override fun onPoseDetectionError(error: PoseDetectionError) {
        // Handle error
    }
}

// Start detection
repository.start(listener)

// Process frames
repository.detectAsync(bitmap, System.currentTimeMillis())

// Stop when done
repository.stop()
```

## MediaPipe Model

Download the pose landmarker model from:
https://developers.google.com/mediapipe/solutions/vision/pose_landmarker#models

Place the model file in:
- `app/src/main/assets/pose_landmarker_lite.task` (recommended for performance)
- Or `app/src/main/assets/pose_landmarker_full.task` (higher accuracy)
- Or `app/src/main/assets/pose_landmarker_heavy.task` (best accuracy)

## Performance Targets

- **Inference Time**: <30ms @ 720p resolution
- **Frame Rate**: 30 FPS
- **GPU Delegate**: Enabled by default for optimal performance

## Testing

Run unit tests:
```bash
./gradlew :core-pose:test
```

Test coverage includes:
- Repository initialization and lifecycle
- Fake repository behavior
- Performance tracker metrics
- Landmark validation

## Dependencies

- MediaPipe Tasks Vision: 0.10.14
- Kotlin Coroutines
- Timber for logging