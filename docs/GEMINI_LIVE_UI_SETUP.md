# Gemini Live API Android UI - Complete Implementation

## Overview

This project provides a complete Android UI implementation for integrating with Google's Gemini Live API for real-time voice coaching and pose analysis. The implementation follows the official Gemini Live API specifications and includes all required components for production-ready deployment.

## Features Implemented

### 🎤 Live Coaching Interface
- **Push-to-talk button** with visual feedback and session management
- **Voice activity visualization** with real-time waveform display
- **Connection status indicators** showing all connection states
- **Real-time transcription display** with automatic scrolling
- **Tool execution status** indicators for pose analysis

### ⏱️ Session Management UI
- **15-minute session timer** with visual countdown
- **Connection health indicators** with signal strength
- **Automatic reconnection notifications** with retry options
- **Session resumption progress** with 10-minute intervals
- **Token refresh notifications** for authentication

### 🎧 Audio Controls
- **Microphone permission handling** with user-friendly prompts
- **Audio input device selection** for different hardware
- **VAD sensitivity controls** for voice detection tuning
- **Audio quality indicators** with real-time monitoring

### 🧘 Pose Integration UI
- **Real-time pose tool execution** display with progress
- **Voice coaching suggestions overlay** with priority-based display
- **Multi-modal feedback** combining voice and visual cues
- **Pose correction visualization** with angle measurements and arrows

### 🔐 Security UI
- **Token refresh notifications** with status indicators
- **Connection security indicators** showing encryption status
- **Privacy controls** for voice data handling
- **Encrypted storage** for authentication tokens

### 🚨 Error Handling UI
- **Network error recovery** prompts with clear actions
- **Session timeout notifications** with automatic restart
- **Graceful degradation** indicators for partial failures
- **Connection retry** mechanisms with exponential backoff

## Project Structure

```
app/src/main/
├── kotlin/com/posecoach/
│   ├── ui/
│   │   ├── activities/
│   │   │   └── LiveCoachingActivity.kt          # Main coaching interface
│   │   ├── components/
│   │   │   ├── PushToTalkButton.kt              # Custom push-to-talk button
│   │   │   ├── VoiceWaveformView.kt             # Voice activity visualization
│   │   │   ├── ConnectionStatusView.kt          # Connection status display
│   │   │   ├── SessionTimerView.kt              # 15-minute session timer
│   │   │   ├── PoseOverlayView.kt               # Pose visualization overlay
│   │   │   ├── CoachingSuggestionsView.kt       # Real-time suggestions
│   │   │   └── ToolExecutionIndicator.kt        # Tool execution status
│   │   └── viewmodels/
│   │       └── LiveCoachingViewModel.kt         # UI state management
│   ├── services/
│   │   ├── GeminiLiveService.kt                 # Gemini Live API integration
│   │   └── PoseAnalysisService.kt               # Pose detection service
│   ├── auth/
│   │   └── TokenManager.kt                      # Ephemeral token management
│   └── network/
│       └── GeminiLiveClient.kt                  # WebSocket client
├── res/
│   ├── layout/
│   │   ├── activity_live_coaching.xml           # Main activity layout
│   │   └── item_coaching_suggestion.xml         # Suggestion item layout
│   ├── drawable/                                # UI icons and backgrounds
│   ├── values/
│   │   ├── colors.xml                           # Color definitions
│   │   ├── strings.xml                          # Text resources
│   │   └── themes.xml                           # UI themes
│   └── AndroidManifest.xml                      # App configuration
```

## Key Components Documentation

### LiveCoachingActivity
The main activity orchestrating the live coaching experience:
- Manages camera preview for pose detection
- Coordinates between Gemini Live service and pose analysis
- Handles all UI interactions and state updates
- Implements proper lifecycle management for services

### GeminiLiveService
Foreground service implementing Gemini Live API integration:
- Manages 15-minute sessions with automatic resumption every 10 minutes
- Handles real-time audio streaming with VAD
- Implements ephemeral token authentication with auto-refresh
- Provides tool execution interface for pose analysis
- Maintains WebSocket connection with proper error handling

### PushToTalkButton
Custom view for voice input control:
- Visual feedback with ripple effects and scaling animations
- Haptic feedback for better user experience
- Long-press detection for settings access
- Session state indication with color changes
- Accessibility support for voice commands

### VoiceWaveformView
Real-time voice activity visualization:
- Live amplitude visualization with smooth animations
- Voice Activity Detection indicator with confidence levels
- Frequency-based color coding for different voice ranges
- Idle animation when no voice input detected
- Configurable sensitivity settings

### SessionTimerView
15-minute session management with visual countdown:
- Circular progress indicator showing remaining time
- Color-coded warnings (green → yellow → red)
- Session resumption status with animation
- Auto-restart capability when session expires
- Integration with Gemini Live session limits

### PoseOverlayView
Real-time pose visualization and correction:
- Skeleton rendering with confidence-based opacity
- Joint detection with accuracy indicators
- Pose correction arrows and angle measurements
- Form quality assessment with color coding
- Real-time coaching suggestions overlay

## API Integration Details

### Gemini Live API Compliance
- **Session Duration**: Enforces 15-minute session limits per API specs
- **Audio Format**: Uses 16kHz PCM mono audio as required
- **Token Management**: Implements ephemeral token pattern with proper refresh
- **Tool Calling**: Supports pose analysis function calls
- **WebSocket Protocol**: Follows official WebSocket message format

### Authentication Flow
1. **API Key Storage**: Securely stored using Android Keystore
2. **Ephemeral Token Generation**: Automated token requests with 15-minute TTL
3. **Token Refresh**: Automatic refresh 5 minutes before expiry
4. **Session Resumption**: New token generation for 10-minute intervals

### Voice Activity Detection
- **Real-time Processing**: 10ms audio chunks for low latency
- **Amplitude Analysis**: RMS calculation for voice detection
- **Threshold Configuration**: User-adjustable sensitivity
- **Visual Feedback**: Live waveform with detection indicators

## Setup Instructions

### 1. Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 24+ (minimum)
- Camera and microphone permissions
- Network connectivity

### 2. API Configuration
```kotlin
// In your application initialization
val tokenManager = TokenManager(context)
tokenManager.setApiKey("your_gemini_api_key_here")
```

### 3. Permissions Setup
All required permissions are already configured in AndroidManifest.xml:
- `CAMERA` - For pose detection
- `RECORD_AUDIO` - For voice input
- `INTERNET` - For API communication
- `ACCESS_NETWORK_STATE` - For connection monitoring

### 4. Build Configuration
The build.gradle.kts includes all necessary dependencies:
- CameraX for pose detection
- OkHttp for WebSocket communication
- Encrypted SharedPreferences for secure storage
- Coroutines for async operations

## Usage Guide

### Starting a Coaching Session
1. **Grant Permissions**: App requests camera and microphone access
2. **Initialize Services**: Background services start automatically
3. **Connect to API**: Ephemeral token generated and WebSocket connected
4. **Begin Coaching**: Push-to-talk for voice interaction, pose detection runs continuously

### Voice Interaction
- **Hold to Talk**: Press and hold the central button
- **Voice Detection**: Waveform shows real-time voice activity
- **Transcription**: Speech-to-text appears in real-time
- **AI Response**: Gemini provides voice and text coaching feedback

### Pose Analysis
- **Real-time Detection**: Pose landmarks updated at 10 FPS
- **Form Assessment**: Visual indicators show posture quality
- **Coaching Suggestions**: AI-generated tips appear as overlays
- **Correction Guidance**: Arrows and angles show needed adjustments

### Session Management
- **15-Minute Limit**: Visual countdown shows remaining time
- **Auto-Resumption**: Sessions restart automatically every 10 minutes
- **Connection Monitoring**: Signal strength and status indicators
- **Error Recovery**: Automatic reconnection with user notifications

## Security Considerations

### Token Security
- Ephemeral tokens with 15-minute expiry
- Encrypted storage using Android Keystore
- Automatic token refresh before expiry
- No persistent storage of API keys

### Voice Data Privacy
- Real-time streaming only (no local storage)
- Encrypted WebSocket connection (WSS)
- Voice data automatically deleted after session
- User control over voice input activation

### Network Security
- Certificate pinning for API connections
- Connection health monitoring
- Automatic retry with exponential backoff
- Graceful degradation for network issues

## Performance Optimizations

### Audio Processing
- Efficient voice activity detection algorithms
- Minimal CPU usage during idle periods
- Optimized audio buffer management
- Real-time processing without blocking UI

### Camera Performance
- 10 FPS pose detection for smooth experience
- Background processing on separate thread
- Efficient memory management for camera frames
- Optimized ML model inference

### UI Responsiveness
- Coroutines for all async operations
- Smooth animations with hardware acceleration
- Efficient view recycling and updates
- Minimal main thread blocking

## Testing

### Unit Tests
- Token management functionality
- Voice activity detection algorithms
- Pose analysis calculations
- Session management logic

### Integration Tests
- API connection and authentication
- Service communication and callbacks
- UI state management and updates
- Error handling and recovery

### Manual Testing Scenarios
1. **Network Interruption**: Test reconnection behavior
2. **Session Timeout**: Verify 15-minute limit handling
3. **Permission Denial**: Test graceful degradation
4. **Background/Foreground**: Test service lifecycle
5. **Multiple Sessions**: Test token refresh cycles

## Troubleshooting

### Common Issues
1. **Connection Failures**: Check API key and network connectivity
2. **Audio Not Detected**: Verify microphone permissions and VAD sensitivity
3. **Pose Detection Issues**: Ensure good lighting and camera permissions
4. **Session Timeouts**: Normal behavior after 15 minutes, auto-restarts

### Debug Logging
Enable debug logging in development:
```kotlin
// In Application class
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
}
```

### Performance Monitoring
Monitor key metrics:
- Session duration and restart frequency
- Voice detection accuracy and latency
- Pose detection frame rate and accuracy
- Network connection stability and recovery

## Production Deployment

### Release Preparation
1. **Remove Debug Code**: Disable all debug logging
2. **Optimize Build**: Enable R8/ProGuard minification
3. **Test Thoroughly**: Run full regression test suite
4. **Monitor Performance**: Set up crash reporting and analytics

### Monitoring and Analytics
- Session success rates and duration
- Voice detection accuracy metrics
- Pose analysis performance
- User engagement and retention

This implementation provides a complete, production-ready Android UI for Gemini Live API integration with comprehensive pose coaching capabilities.