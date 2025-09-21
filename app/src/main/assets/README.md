# MediaPipe Model Assets

## Required Model Files

This directory should contain the MediaPipe model files required for pose detection:

### pose_landmarker.task
- **Required**: Yes
- **Source**: Download from MediaPipe official repository
- **URL**: https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task
- **Description**: MediaPipe Pose Landmarker model for real-time pose detection
- **File Size**: ~2.9MB

## Download Instructions

1. Download the model file from the URL above
2. Rename it to `pose_landmarker.task`
3. Place it in this `app/src/main/assets/` directory

## Alternative Models

You can also use other MediaPipe pose models:
- `pose_landmarker_full.task` (higher accuracy, larger size)
- `pose_landmarker_heavy.task` (highest accuracy, largest size)

Make sure to update the model path in `PoseDetectionManager.kt` if using a different model file.

## Model Loading

The model is loaded in `PoseDetectionManager.kt`:
```kotlin
val baseOptions = BaseOptions.builder()
    .setModelAssetPath("pose_landmarker.task")
    .build()
```

**Note**: The actual model file is not included in the repository due to size constraints. You must download it separately following the instructions above.