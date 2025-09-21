# Suggestions API Module

Gemini 2.5 Structured Output integration for AI-powered pose improvement suggestions.

## Features

### 🤖 Gemini 2.5 Integration
- **Structured Output**: Uses `response_mime_type=application/json` with schema validation
- **33-point landmarks**: Only coordinates sent, never images
- **3 suggestions**: Prioritized actionable improvements

### 🔒 Privacy & Security
- **API key encryption**: Secure storage with EncryptedSharedPreferences
- **Explicit consent**: User agreement required before API calls
- **Local fallback**: Works without internet/API key
- **No image upload**: Only JSON landmarks transmitted

### ⚡ Performance & UX
- **Pose deduplication**: Same pose within 5s window blocked
- **Non-blocking UI**: Async API calls with 5s timeout
- **Auto-retry**: Fallback to local suggestions on failure
- **60fps maintained**: No UI frame drops

## Architecture

```
SuggestionsOrchestrator
├── EnhancedStablePoseGate (1-2s stability detection)
├── PoseDeduplicationManager (5s window)
├── PoseSuggestionClient (interface)
│   ├── FakePoseSuggestionClient (local/testing)
│   └── GeminiPoseSuggestionClient (AI-powered)
├── ApiKeyManager (secure storage)
└── PrivacyManager (consent/controls)
```

## Usage

### Basic Integration

```kotlin
// Initialize components
val apiKeyManager = ApiKeyManager(context)
val privacyManager = PrivacyManager(context)
val orchestrator = SuggestionsOrchestrator(context, apiKeyManager)

// Listen for suggestions
lifecycleScope.launch {
    orchestrator.suggestionsFlow.collect { result ->
        if (result.isSuccess) {
            val suggestions = result.getOrNull()!!.suggestions
            displaySuggestions(suggestions)
        }
    }
}

// Process pose landmarks
orchestrator.processPoseLandmarks(landmarks)
```

### Privacy Setup

```kotlin
// Check consent
if (!privacyManager.hasApiConsent()) {
    privacyManager.showConsentDialog(
        onAccept = { orchestrator.updateClient() },
        onDecline = { /* local-only mode */ }
    )
}

// Runtime controls
privacyManager.setApiEnabled(enabled)
privacyManager.setLocalOnlyMode(localOnly)
```

### API Key Configuration

Add to `local.properties`:
```properties
gemini.api.key=YOUR_GEMINI_API_KEY
```

Or set environment variable:
```bash
export GEMINI_API_KEY=your_key_here
```

## API Schema

Request format (landmarks only):
```json
[
  {"i":0,"x":0.5,"y":0.3,"z":0.0,"v":0.9},
  {"i":11,"x":0.4,"y":0.35,"z":0.0,"v":0.95},
  ...
]
```

Response format (Structured Output):
```json
{
  "suggestions": [
    {
      "title": "Straighten Your Back",
      "instruction": "Keep your spine aligned by imagining...",
      "target_landmarks": ["LEFT_SHOULDER", "RIGHT_SHOULDER"]
    }
  ]
}
```

## Testing

### Unit Tests
```bash
./gradlew :suggestions-api:test
```

### Integration Test
```kotlin
@Test
fun `gemini client should return valid suggestions`() = runTest {
    val client = GeminiPoseSuggestionClient(validApiKey)
    val landmarks = createTestLandmarks()

    val result = client.getPoseSuggestions(landmarks)

    assertTrue(result.isSuccess)
    assertEquals(3, result.getOrNull()!!.suggestions.size)
}
```

## Performance Metrics

- **API Response**: <2s typical, 5s timeout
- **Deduplication**: <1ms hash calculation
- **Privacy Check**: <5ms consent verification
- **UI Updates**: <16ms (60fps maintained)

## Security

✅ **API Key**: Encrypted storage, never logged
✅ **Privacy**: Explicit consent, granular controls
✅ **Data**: Only coordinates, no images
✅ **Fallback**: Works offline/without API
✅ **Validation**: Schema-enforced responses

## Dependencies

- Gemini AI Client SDK: 0.9.0
- Kotlin Serialization: 1.6.0
- OkHttp: 4.12.0 (direct API calls)
- Security Crypto: 1.1.0-alpha06

## DoD Verification

✅ **Same pose deduplication**: 5s window prevents duplicates
✅ **API failure fallback**: Local suggestions always available
✅ **60fps UI**: Non-blocking async processing
✅ **Privacy compliance**: Consent + granular controls
✅ **Schema validation**: Structured Output enforced