# Live Coach Module Documentation

## Overview

The Live Coach module provides real-time AI coaching functionality using Google's Gemini Live API. It enables voice-based interaction with an AI coach while analyzing pose data and camera feed in real-time.

## Architecture

### Core Components

1. **LiveCoachManager** - Main coordinator
2. **LiveApiWebSocketClient** - WebSocket communication
3. **AudioStreamManager** - Microphone audio streaming
4. **ImageSnapshotManager** - Camera image processing
5. **LiveCoachStateManager** - Session state management
6. **UI Components** - Push-to-talk button and overlay

### Data Flow

```
Camera → PoseDetection → LiveCoachManager
                            ↓
Audio Input → AudioStreamManager → WebSocket → Gemini Live API
                            ↓
Image Snapshots → ImageSnapshotManager → WebSocket → Gemini Live API
                            ↓
                       AI Responses → UI Overlay
```

## Key Features

### 1. WebSocket Session Management
- **Connection States**: DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR
- **Auto-retry Logic**: Exponential backoff (1s, 2s, 4s max)
- **Session Recovery**: Maintains state across reconnections
- **Timeout Handling**: 30-second connection timeout

### 2. Audio Streaming
- **Format**: 16kHz mono PCM
- **Chunk Size**: 1-second intervals
- **Silence Detection**: Configurable threshold-based
- **Barge-in Support**: Interrupts AI response when user speaks

### 3. Image Processing
- **Resolution**: 320x240 (low-res for API efficiency)
- **Format**: JPEG (70% quality)
- **Interval**: 1.5-second snapshots
- **Pose Integration**: Combines landmarks with visual data

### 4. Push-to-Talk UI
- **Visual States**: Connected, connecting, recording, error
- **Animation**: Pulse effect during recording
- **Touch Handling**: Press and hold interaction
- **Accessibility**: Screen reader support

## Usage

### Basic Integration

```kotlin
// Initialize Live Coach
val liveCoachManager = LiveCoachManager(context, lifecycleScope, apiKey)

// Set up UI
val overlay = LiveCoachOverlay(context)
overlay.setOnLiveCoachListener(object : LiveCoachOverlay.OnLiveCoachListener {
    override fun onStartSession() {
        liveCoachManager.startPushToTalkSession()
    }

    override fun onStopSession() {
        liveCoachManager.stopPushToTalkSession()
    }

    override fun onRetryConnection() {
        liveCoachManager.forceReconnect()
    }
})

// Connect to pose detection
val cameraIntegration = LiveCoachCameraIntegration(liveCoachManager, lifecycleScope)
poseRepository.start(cameraIntegration)

// Observe responses
lifecycleScope.launch {
    liveCoachManager.coachingResponses.collect { response ->
        overlay.showCoachingResponse(response)
    }
}
```

### Camera Integration

```kotlin
// Set up camera analysis
val imageAnalysis = ImageAnalysis.Builder()
    .setTargetResolution(Size(640, 480))
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()

imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context), cameraIntegration)
```

## Configuration

### Live API Config

```kotlin
val config = LiveApiConfig(
    model = "models/gemini-2.0-flash-exp",
    generationConfig = GenerationConfig(
        maxOutputTokens = 8192,
        temperature = 0.7f,
        responseModalities = listOf("TEXT", "AUDIO")
    ),
    systemInstruction = "You are a fitness coach providing real-time pose feedback.",
    realtimeInputConfig = RealtimeInputConfig(mediaResolution = "LOW")
)
```

### Audio Settings

```kotlin
// Configure silence detection
liveCoachManager.setSilenceDetectionEnabled(true)

// Check audio permission
if (!audioManager.hasAudioPermission()) {
    // Request RECORD_AUDIO permission
}
```

## State Management

### Connection States

- **DISCONNECTED**: Initial state, not connected
- **CONNECTING**: Attempting to establish connection
- **CONNECTED**: Successfully connected and ready
- **RECONNECTING**: Attempting to reconnect after failure
- **ERROR**: Connection failed, manual retry needed

### Session Flow

1. User presses push-to-talk button
2. System connects to Live API WebSocket
3. Audio recording starts when connected
4. Image snapshots begin capturing
5. Real-time data streams to Gemini
6. AI responses flow back to UI
7. User releases button or manually disconnects

## Error Handling

### Common Errors

1. **Permission Denied**: Audio recording permission required
2. **Network Timeout**: Poor connectivity or API issues
3. **API Key Invalid**: Check authentication credentials
4. **Quota Exceeded**: API usage limits reached
5. **Microphone Busy**: Another app using microphone

### Recovery Strategies

- Automatic retry with exponential backoff
- Graceful degradation (text-only mode)
- User-initiated force reconnection
- Error message display with actionable advice

## Testing

### Unit Tests

- **State Management**: Connection state transitions
- **Audio Processing**: Chunk generation and streaming
- **Image Processing**: Snapshot creation and encoding
- **WebSocket**: Message parsing and error handling

### Integration Tests

- **Session Lifecycle**: Full connect/record/disconnect flow
- **Barge-in**: User interruption handling
- **Timeout Recovery**: Connection failure scenarios
- **Multi-component**: Audio + video + pose coordination

### Test Execution

```bash
# Run all Live Coach tests
./gradlew app:testDebugUnitTest --tests "*livecoach*"

# Integration tests specifically
./gradlew app:testDebugUnitTest --tests "*LiveCoachIntegrationTest"

# Audio streaming tests
./gradlew app:testDebugUnitTest --tests "*AudioStreamManagerTest"
```

## Performance Considerations

### Optimization Tips

1. **Audio Chunks**: 1-second intervals balance latency vs overhead
2. **Image Resolution**: 320x240 reduces payload size significantly
3. **Compression**: JPEG 70% quality for good balance
4. **Throttling**: Snapshot interval prevents excessive API calls
5. **Memory**: Proper cleanup prevents leaks

### Resource Usage

- **Network**: ~10-50 KB/s depending on activity
- **Memory**: ~10-20 MB for buffers and state
- **CPU**: Minimal impact with efficient chunking
- **Battery**: Moderate usage during active sessions

## Troubleshooting

### Common Issues

**Connection fails immediately**
- Check API key validity
- Verify network connectivity
- Confirm WebSocket URL accessibility

**No audio detected**
- Verify RECORD_AUDIO permission granted
- Check microphone hardware functionality
- Test with device's voice recorder app

**Snapshots not working**
- Ensure camera integration is active
- Verify pose detection is running
- Check image processing pipeline

**Responses delayed**
- Check network latency
- Verify API quota availability
- Consider reducing snapshot frequency

### Debug Information

```kotlin
// Get diagnostic info
val sessionInfo = liveCoachManager.getSessionInfo()
Timber.d("Session info: $sessionInfo")

// Monitor state changes
lifecycleScope.launch {
    liveCoachManager.sessionState.collect { state ->
        Timber.d("State: ${state.connectionState}, Recording: ${state.isRecording}")
    }
}
```

## API Reference

### LiveCoachManager

```kotlin
class LiveCoachManager(context: Context, lifecycleScope: LifecycleCoroutineScope, apiKey: String)

fun startPushToTalkSession()
fun stopPushToTalkSession()
fun updatePoseLandmarks(landmarks: PoseLandmarkResult)
fun processImageWithLandmarks(imageProxy: ImageProxy, landmarks: PoseLandmarkResult)
fun triggerBargeIn()
fun forceReconnect()
fun destroy()

val sessionState: StateFlow<SessionState>
val coachingResponses: SharedFlow<String>
val transcriptions: SharedFlow<String>
val errors: SharedFlow<String>
```

### PushToTalkButton

```kotlin
class PushToTalkButton(context: Context)

fun updateConnectionState(state: ConnectionState)
fun setRecording(recording: Boolean)
fun setOnPushToTalkListener(listener: OnPushToTalkListener?)
```

### LiveCoachOverlay

```kotlin
class LiveCoachOverlay(context: Context)

fun updateConnectionState(state: ConnectionState)
fun setRecording(isRecording: Boolean)
fun showCoachingResponse(response: String)
fun showTranscription(text: String)
fun showError(error: String)
fun setOnLiveCoachListener(listener: OnLiveCoachListener?)
```

## Security Considerations

- API keys should be securely stored (not hardcoded)
- Audio data is transmitted to Google's servers
- Image snapshots are low-resolution to minimize privacy exposure
- No persistent storage of audio or image data
- WebSocket connections use TLS encryption

## Future Enhancements

1. **Offline Mode**: Local TTS/STT when network unavailable
2. **Voice Profiles**: Personalized coaching based on user voice
3. **Advanced Pose Analysis**: More detailed biomechanical feedback
4. **Session Recording**: Optional workout session logging
5. **Multi-language**: Support for non-English coaching
6. **Custom Coaching**: User-defined workout programs