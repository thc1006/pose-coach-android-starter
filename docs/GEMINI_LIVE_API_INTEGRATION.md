# Gemini Live API Integration for Pose Coach Android

## Overview

This implementation provides a complete, production-ready integration of Google's Gemini Live API for real-time pose coaching with audio feedback. The system follows the official API specifications exactly and includes comprehensive error handling, audio optimization, and seamless pose analysis integration.

## Architecture

### Core Components

1. **LiveApiWebSocketClient** - WebSocket client implementing complete Gemini Live API protocol
2. **AudioProcessor** - Real-time audio capture, processing, and Voice Activity Detection
3. **EphemeralTokenManager** - Secure token management with automatic refresh
4. **LiveApiSessionManager** - Complete session lifecycle management
5. **PoseAnalysisTools** - Function declarations and pose coaching integration
6. **ErrorRecoveryManager** - Comprehensive error handling and recovery
7. **AdaptiveAudioManager** - Audio quality optimization and adaptation
8. **LiveApiManager** - Main entry point for the complete system

### Key Features

#### Live API Protocol Compliance
- **WebSocket Connection**: `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent`
- **Model**: `gemini-2.0-flash-exp` with native multilingual audio support
- **Audio Format**: 16-bit PCM, 16kHz mono input / 24kHz output
- **Message Types**: All official message types implemented
  - `BidiGenerateContentSetup`
  - `BidiGenerateContentClientContent`
  - `BidiGenerateContentRealtimeInput`
  - `BidiGenerateContentToolResponse`
  - Server responses handling

#### Security Implementation
- **Ephemeral Tokens**: 30-minute expiry with secure refresh
- **No API Keys in Client**: Backend-only API key usage
- **Encrypted Storage**: Android EncryptedSharedPreferences
- **Token Refresh**: Automatic refresh 5 minutes before expiry
- **Session Windows**: 1-minute new session grace period

#### Audio Pipeline
- **Voice Activity Detection**: Real-time VAD with adaptive thresholds
- **PCM Encoding**: 16-bit PCM at 16kHz for optimal Live API performance
- **Adaptive Quality**: Dynamic quality adjustment based on network conditions
- **Audio Enhancement**: Noise suppression, AGC, echo cancellation
- **Real-time Streaming**: 20ms frame buffering for low latency

#### Session Management
- **Lifecycle Handling**: Complete setup → active → cleanup lifecycle
- **Reconnection Logic**: Automatic reconnection with exponential backoff
- **Context Compression**: Automatic context window management
- **Session Resumption**: Support for 10-minute connection limits
- **GoAway Handling**: Proper session termination and restart

#### Pose Integration
- **Function Declarations**: Complete pose analysis tool definitions
- **Real-time Processing**: Pose landmark analysis and feedback
- **Exercise-Specific**: Specialized analysis for squats, pushups, planks, etc.
- **Safety Focus**: Emphasis on proper form and injury prevention
- **Progress Tracking**: Repetition counting and quality scoring

#### Error Recovery
- **Circuit Breaker Pattern**: Automatic failure protection
- **Retry Logic**: Configurable retry with exponential backoff
- **Graceful Degradation**: Fallback modes for poor conditions
- **Health Monitoring**: Continuous system health assessment
- **Recovery Actions**: Automatic recovery from various error conditions

## Implementation Guide

### 1. Basic Setup

```kotlin
// Initialize the Live API Manager
val liveApiManager = LiveApiManager(
    context = context,
    config = LiveApiConfig(
        model = "models/gemini-2.0-flash-exp",
        voiceName = "Aoede",
        responseModalities = listOf("AUDIO"),
        systemInstruction = "You are an expert pose coach...",
        enableVoiceActivityDetection = true
    )
)
```

### 2. Start Coaching Session

```kotlin
// Start complete pose coaching session
lifecycleScope.launch {
    val result = liveApiManager.startPoseCoachingSession()
    if (result.isSuccess) {
        println("Session started: ${result.getOrNull()}")
    } else {
        println("Failed: ${result.exceptionOrNull()?.message}")
    }
}
```

### 3. Process Pose Data

```kotlin
// Process pose landmarks from MediaPipe
val landmarks = listOf(
    PoseLandmark(x = 0.5f, y = 0.3f, z = 0.1f, visibility = 0.9f),
    // ... more landmarks
)

lifecycleScope.launch {
    liveApiManager.processPoseLandmarks(landmarks)
}
```

### 4. Handle Responses

```kotlin
// Observe audio responses
liveApiManager.audioResponse.collect { audioData ->
    // Audio is automatically played
    println("Received audio: ${audioData.size} bytes")
}

// Observe text responses
liveApiManager.textResponse.collect { text ->
    println("AI Coach: $text")
}

// Observe pose analysis
liveApiManager.poseAnalysisResults.collect { result ->
    if (result.needsCorrection) {
        println("Correction needed: ${result.feedback}")
    }
}
```

### 5. Configure Audio

```kotlin
liveApiManager.configureAudio(
    enableVAD = true,
    vadThreshold = 0.02f,
    enableAdaptiveQuality = true,
    enableNoiseSuppression = true,
    enableGainControl = true,
    enableEchoCancellation = true
)
```

### 6. Monitor System Health

```kotlin
// Monitor system status
liveApiManager.systemStatus.collect { status ->
    if (!status.isHealthy) {
        println("Warnings: ${status.warnings}")
    }
}

// Get detailed statistics
val stats = liveApiManager.getSystemStatistics()
println("Audio Quality: ${stats.audioQuality}")
println("Session Duration: ${stats.sessionDuration}ms")
```

## Backend Requirements

### Ephemeral Token Endpoint

Your backend must provide an endpoint for ephemeral token generation:

```kotlin
// Backend endpoint (example)
POST /api/gemini/ephemeral-token
{
    "model": "models/gemini-2.0-flash-exp",
    "displayName": "Pose Coach Session",
    "ttl": "1800s"
}

// Response
{
    "access_token": "ephemeral_token_here",
    "expires_in": 1800
}
```

### Security Considerations

1. **Never expose API keys in client code**
2. **Validate all token requests on backend**
3. **Implement rate limiting**
4. **Use HTTPS for all communications**
5. **Validate client authenticity**

## Configuration Options

### LiveApiConfig Parameters

```kotlin
data class LiveApiConfig(
    val model: String = "models/gemini-2.0-flash-exp",
    val voiceName: String = "Aoede", // Multilingual voice
    val responseModalities: List<String> = listOf("AUDIO"),
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val systemInstruction: String? = null,
    val tools: List<Tool>? = null,
    val enableVoiceActivityDetection: Boolean = true,
    val audioBufferSizeMs: Int = 100,
    val reconnectMaxAttempts: Int = 3,
    val reconnectDelayMs: Long = 1000
)
```

### Audio Configuration

```kotlin
// Audio quality levels
enum class AudioQualityLevel {
    LOW,    // Basic quality, maximum compatibility
    MEDIUM, // Balanced quality and performance
    HIGH    // Best quality, requires good connection
}

// Voice Activity Detection
configureVAD(
    enabled = true,
    threshold = 0.02f,    // Sensitivity threshold
    hangoverMs = 500      // Continue after voice stops
)
```

## Performance Optimization

### Memory Management
- Automatic cleanup of resources
- Efficient audio buffering
- Context window compression
- Garbage collection optimization

### Network Efficiency
- Adaptive quality based on connection
- Circuit breaker for failures
- Exponential backoff for retries
- Efficient message batching

### Audio Optimization
- Real-time audio processing
- Adaptive sample rates
- Noise reduction algorithms
- Echo cancellation

## Error Handling

### Error Types and Recovery

```kotlin
sealed class LiveApiError : Exception() {
    data class ConnectionError(override val message: String) : LiveApiError()
    data class AuthenticationError(override val message: String) : LiveApiError()
    data class SessionError(override val message: String) : LiveApiError()
    data class AudioError(override val message: String) : LiveApiError()
    data class ProtocolError(override val message: String) : LiveApiError()
    data class RateLimitError(override val message: String, val retryAfterMs: Long?) : LiveApiError()
}
```

### Recovery Strategies

1. **Connection Failures**: Automatic reconnection with exponential backoff
2. **Audio Issues**: Adaptive quality reduction and audio enhancement
3. **Token Expiry**: Automatic token refresh
4. **Rate Limiting**: Intelligent backoff and retry
5. **Session Timeout**: Automatic session resumption
6. **Critical Errors**: Complete system restart

## Testing

### Unit Tests
- Complete test coverage for all components
- Mock implementations for external dependencies
- Performance and memory leak testing
- Error condition simulation

### Integration Tests
- End-to-end session testing
- Audio pipeline validation
- Error recovery verification
- Performance benchmarking

## Monitoring and Analytics

### System Metrics
- Session success rates
- Audio quality statistics
- Error frequency and types
- Performance metrics
- User engagement analytics

### Health Monitoring
- Real-time system health checks
- Automatic issue detection
- Performance degradation alerts
- Recovery success tracking

## Best Practices

### Development
1. Always handle errors gracefully
2. Implement proper cleanup in lifecycle methods
3. Use coroutines for all async operations
4. Follow Android audio guidelines
5. Test on various devices and network conditions

### Production
1. Monitor system health continuously
2. Implement proper logging and analytics
3. Have fallback mechanisms for critical failures
4. Regular token rotation and security audits
5. Performance optimization based on real usage

## Troubleshooting

### Common Issues

1. **Connection Failures**
   - Check network connectivity
   - Verify token validity
   - Review firewall settings

2. **Audio Problems**
   - Verify microphone permissions
   - Check audio format compatibility
   - Monitor VAD sensitivity

3. **Poor Quality**
   - Network bandwidth issues
   - Audio interference
   - Device capability limitations

4. **Session Timeouts**
   - Normal after 10 minutes
   - Automatic reconnection should occur
   - Check token refresh logic

### Debug Tools

```kotlin
// Enable debug logging
Timber.plant(Timber.DebugTree())

// Get system statistics
val stats = liveApiManager.getSystemStatistics()
println("Debug info: $stats")

// Monitor error recovery
liveApiManager.recoveryState.collect { state ->
    println("Recovery state: $state")
}
```

## Future Enhancements

### Planned Features
1. Multi-language support expansion
2. Advanced pose analysis algorithms
3. Personalized coaching adaptations
4. Social features and progress sharing
5. Integration with fitness trackers

### Performance Improvements
1. Edge computing for pose analysis
2. Predictive audio buffering
3. Advanced compression algorithms
4. Battery optimization techniques

## Support and Resources

- [Official Gemini Live API Documentation](https://ai.google.dev/api/live)
- [MediaPipe Pose Documentation](https://developers.google.com/mediapipe/solutions/vision/pose_landmarker)
- [Android Audio Development Guide](https://developer.android.com/guide/topics/media/mediarecorder)

For issues or questions, refer to the project's issue tracker or documentation.