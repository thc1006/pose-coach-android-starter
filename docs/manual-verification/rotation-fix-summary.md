# Rotation Fix Summary - Double Rotation Issue

## Problem Description
The pose skeleton was appearing rotated 90° on screen. When a person stood upright, the skeleton displayed sideways (horizontal instead of vertical).

## Root Cause Analysis
**Double Rotation Processing**:
1. **First Rotation**: MediaPipe's `ImageProcessingOptions.setRotationDegrees()` at line 100-105 in `MediaPipePoseRepository.kt` handles device rotation during inference
2. **Second Rotation**: `EnhancedCoordinateMapper` applies rotation matrix transformation again at line 532 in `EnhancedCoordinateMapper.kt`
3. **Result**: The skeleton gets rotated twice, causing 90° misalignment

## Solution Implemented
**Remove redundant rotation in coordinate mapper** - MediaPipe already returns correctly oriented landmarks.

### Modified Files

#### 1. `app/src/main/kotlin/com/posecoach/app/overlay/PoseOverlayView.kt`

**Changes**:
- **Line 74-87**: Modified `configureCameraDisplay()` to force rotation=0 when creating `EnhancedCoordinateMapper`
- **Line 89-106**: Updated `updateCoordinateMapper()` to use rotation=0 with explanatory comment
- **Line 137**: Updated debug text to show "Rotation: ${deviceRotation}° (not applied to mapper)"
- **Line 142-146**: Added debug display of first landmark coordinates
- **Line 305-316**: Modified `updateRotation()` to not apply rotation to mapper
- **Line 318-328**: Modified `updateCameraFacing()` to rebuild mapper without rotation

**Key Code Changes**:
```kotlin
// Before:
coordinateMapper = EnhancedCoordinateMapper(
    viewWidth = width,
    viewHeight = height,
    imageWidth = cameraImageWidth,
    imageHeight = cameraImageHeight,
    isFrontFacing = isFrontFacing,
    rotation = deviceRotation  // ❌ This caused double rotation
)

// After:
coordinateMapper = EnhancedCoordinateMapper(
    viewWidth = width,
    viewHeight = height,
    imageWidth = cameraImageWidth,
    imageHeight = cameraImageHeight,
    isFrontFacing = isFrontFacing,
    rotation = 0  // ✅ Force to 0 to prevent double rotation
)
```

#### 2. `core-pose/src/main/kotlin/com/posecoach/corepose/repository/MediaPipePoseRepository.kt`

**Changes**:
- **Line 99-107**: Added comprehensive comment explaining MediaPipe's automatic rotation handling
- **Import**: Added `ImageProcessingOptions` import (already existed but now documented)

**Documentation Added**:
```kotlin
// IMPORTANT: MediaPipe Tasks Vision API's setRotationDegrees() automatically handles rotation
// The returned landmarks will already be in the correct orientation (normalized 0.0-1.0)
// Therefore, downstream coordinate mappers should NOT apply rotation transformations again
// to avoid double rotation issues (e.g., upright person appearing sideways)
```

### What Was NOT Changed

**`EnhancedCoordinateMapper.kt`** - No modifications needed. The class still supports rotation for other use cases, we simply pass `rotation=0` when creating instances.

## Technical Explanation

### MediaPipe's Rotation Handling
MediaPipe Tasks Vision API processes rotation at the inference stage:
```kotlin
val imageProcessingOptions = ImageProcessingOptions.builder()
    .setRotationDegrees(rotationDegrees)  // Handles 0°, 90°, 180°, 270°
    .build()
poseLandmarker?.detectAsync(mpImage, imageProcessingOptions, timestampMs)
```

The returned `PoseLandmarkResult` contains landmarks that are already in the correct orientation:
- Coordinates are normalized (0.0-1.0)
- Rotation is already compensated
- No further rotation processing needed

### Coordinate Mapping Flow (After Fix)
1. **Camera Frame** → (device rotation: 90°)
2. **MediaPipe Inference** → applies rotation via `ImageProcessingOptions`
3. **Landmarks Output** → normalized coordinates (0.0-1.0) in correct orientation
4. **EnhancedCoordinateMapper** → scales/offsets to screen pixels (NO rotation)
5. **PoseOverlayView** → draws skeleton at correct position

## Verification Steps

### Build Verification
```bash
# Core-pose module compiles successfully
./gradlew :core-pose:compileDebugKotlin
# Result: BUILD SUCCESSFUL
```

### Manual Testing Checklist
- [ ] Portrait mode: Person standing upright → skeleton aligned vertically
- [ ] Landscape mode: Person standing → skeleton still correctly oriented
- [ ] Front camera: Mirroring works correctly without rotation artifacts
- [ ] Back camera: No mirroring, correct orientation
- [ ] 90° rotation: Skeleton matches body position
- [ ] 180° rotation: Skeleton matches body position
- [ ] 270° rotation: Skeleton matches body position

### Debug Information
The overlay now displays:
```
Overlay Active
Landmarks: 33
View: 1080x2400
Camera: 640x480
Rotation: 90° (not applied to mapper)
First landmark: (0.523, 0.245)
```

## Performance Impact
**None** - Removing redundant rotation processing may slightly improve performance:
- One less matrix transformation per frame
- Eliminates rotation matrix calculations in `EnhancedCoordinateMapper`
- Cache invalidation avoided when rotation changes

## Rollback Plan
If issues occur, revert these changes:
```bash
git checkout HEAD -- app/src/main/kotlin/com/posecoach/app/overlay/PoseOverlayView.kt
git checkout HEAD -- core-pose/src/main/kotlin/com/posecoach/corepose/repository/MediaPipePoseRepository.kt
```

## Related Documentation
- MediaPipe Tasks Vision API: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker
- ImageProcessingOptions: https://developers.google.com/mediapipe/api/solutions/java/com/google/mediapipe/tasks/vision/core/ImageProcessingOptions
- Android Coordinate Systems: https://source.android.com/docs/core/camera/camera3_crop_reprocess

## Future Considerations
1. Add unit tests for coordinate transformation without rotation
2. Add integration tests for all device orientations
3. Document coordinate system flow in architecture diagrams
4. Consider adding runtime validation to detect double-rotation scenarios

## Author Notes
- Fix implements the "single source of truth" principle for rotation
- MediaPipe is the authoritative source for rotation handling
- Downstream components should trust MediaPipe's coordinate system
- This approach aligns with MediaPipe's documented best practices
