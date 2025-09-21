# ADR-003: Gemini 2.5 Live API for AI Coaching

## Status
Accepted

## Context
The Pose Coach Android application requires intelligent, real-time coaching suggestions based on pose analysis. The system needs:
- Real-time AI-powered coaching suggestions
- Structured output for consistent response parsing
- Privacy-preserving data transmission
- Low-latency responses for immediate feedback
- Fallback mechanisms for offline scenarios
- Cost-effective AI integration

We need to select an AI service that provides high-quality coaching suggestions while maintaining user privacy and system performance.

## Decision
We will integrate **Google Gemini 2.5 Live API** as our primary AI coaching solution with privacy-preserving data handling.

### Specific Implementation:
- **Gemini 2.5 Live API** via WebSocket connection
- **Structured output** using JSON schema constraints
- **Privacy-first data aggregation** with anonymization
- **Streaming responses** for real-time coaching
- **Local fallback** with rule-based coaching system
- **Session management** for context-aware suggestions

### Integration Architecture:
```kotlin
class GeminiLiveApiClient {
    private var webSocket: WebSocket? = null
    private val responseFlow = MutableSharedFlow<GeminiResponse>()

    suspend fun initializeSession(exerciseType: ExerciseType): Result<SessionId>
    suspend fun sendPoseAnalysisRequest(request: PoseAnalysisRequest): Flow<GeminiResponse>
    suspend fun closeSession()
}
```

## Rationale

### AI Capabilities:
1. **Advanced Understanding**: Superior comprehension of movement patterns and exercise form
2. **Contextual Awareness**: Ability to provide personalized coaching based on exercise context
3. **Natural Language**: Human-like coaching suggestions that feel natural
4. **Structured Output**: Reliable JSON response format for consistent parsing
5. **Real-Time Processing**: Live API enables immediate feedback
6. **Multi-Modal Understanding**: Can process complex pose data relationships

### Technical Advantages:
1. **Live API**: Real-time streaming responses for immediate coaching
2. **Structured Output**: Enforced JSON schema for reliable parsing
3. **Scalability**: Google Cloud infrastructure for reliable service
4. **Integration**: Well-documented REST and WebSocket APIs
5. **Performance**: Optimized for low-latency applications
6. **Reliability**: Enterprise-grade service level agreements

### Privacy Implementation:
```kotlin
class PrivacyPreservingDataPipeline {
    fun createAnonymizedRequest(poseData: PoseData): PoseAnalysisRequest {
        return PoseAnalysisRequest(
            exerciseType = poseData.exerciseType,
            jointAngles = anonymizeAngles(poseData.jointAngles),
            movementPatterns = extractPatterns(poseData.sequence),
            qualityMetrics = aggregateMetrics(poseData.quality),
            // Remove all personally identifiable information
            sessionHash = generateAnonymousHash(poseData.sessionId)
        )
    }
}
```

## Alternatives Considered

### 1. OpenAI GPT-4/ChatGPT API
- **Pros**: High-quality language understanding, good API documentation
- **Cons**: No real-time streaming, higher costs, no structured output guarantees, privacy concerns

### 2. Anthropic Claude API
- **Pros**: Strong reasoning capabilities, good safety features
- **Cons**: Limited real-time capabilities, higher costs, no specialized fitness knowledge

### 3. Local Large Language Models (LLaMA, etc.)
- **Pros**: Complete privacy, no network dependency
- **Cons**: Significant resource requirements, inferior quality, complex deployment, maintenance burden

### 4. Custom Rule-Based System
- **Pros**: Complete control, no external dependencies, privacy-compliant
- **Cons**: Limited intelligence, high maintenance, poor user experience, scalability issues

### 5. Azure OpenAI Service
- **Pros**: Enterprise features, Microsoft ecosystem integration
- **Cons**: Similar limitations to OpenAI, cost considerations, no specialized fitness capabilities

### 6. Specialized Fitness AI APIs
- **Pros**: Domain-specific knowledge
- **Cons**: Limited availability, unclear quality, integration complexity, vendor lock-in risks

## Technical Implementation

### Structured Output Schema:
```kotlin
@Serializable
data class CoachingResponse(
    val sessionId: String,
    val timestamp: Long,
    val analysis: PoseAnalysis,
    val suggestions: List<CoachingSuggestion>,
    val confidence: Float,
    val metadata: ResponseMetadata
)

@Serializable
data class CoachingSuggestion(
    val id: String,
    val type: SuggestionType,
    val message: String,
    val priority: Priority,
    val targetArea: BodyPart,
    val expectedImprovement: String,
    val visualCues: List<VisualCue>?
)
```

### Privacy-Preserving Request Format:
```kotlin
@Serializable
data class PoseAnalysisRequest(
    val exerciseType: ExerciseType,
    val anonymizedPoseData: AnonymizedPoseData,
    val movementContext: MovementContext,
    val qualityIndicators: QualityMetrics,
    val sessionContext: AnonymousSessionContext
)
```

### Prompt Engineering:
```kotlin
class PromptEngineering {
    fun createCoachingPrompt(data: PoseAnalysisRequest): StructuredPrompt {
        return StructuredPrompt(
            systemPrompt = """
            You are an expert fitness coach specializing in ${data.exerciseType}.
            Analyze the provided anonymized pose data and movement patterns.
            Provide specific, actionable coaching suggestions in JSON format.

            Focus on:
            1. Immediate form corrections for safety
            2. Performance improvements
            3. Movement efficiency

            Response must match the CoachingResponse schema exactly.
            Provide 1-3 suggestions ordered by priority.
            """.trimIndent(),

            userPrompt = buildUserPrompt(data),
            outputSchema = CoachingResponseSchema,
            constraints = PromptConstraints(
                maxSuggestions = 3,
                requirePriority = true,
                includeVisualCues = true
            )
        )
    }
}
```

## Privacy and Data Protection

### Data Anonymization Process:
1. **Remove PII**: Strip all personally identifiable information
2. **Normalize Coordinates**: Use relative positioning instead of absolute coordinates
3. **Aggregate Metrics**: Send statistical summaries instead of raw data
4. **Session Hashing**: Anonymous session identifiers
5. **Temporal Smoothing**: Remove timing patterns that could identify users

### Consent Management:
```kotlin
enum class DataProcessingConsent {
    ON_DEVICE_ONLY,           // No cloud processing
    ANONYMIZED_COACHING,      // Anonymized data for AI coaching
    ANALYTICS_SHARING,        // Anonymized data for system improvement
    FULL_CLOUD_PROCESSING     // Full cloud integration with consent
}
```

### Data Flow Controls:
- **Granular Consent**: Users control exactly what data is processed
- **Revocable Permissions**: Instant consent withdrawal
- **Audit Trail**: Complete logging of data processing decisions
- **Transparency**: Clear explanations of data usage

## Fallback Strategy

### Local Coaching Fallback:
```kotlin
class LocalCoachingFallback {
    private val ruleEngine = PoseAnalysisRuleEngine()

    fun generateLocalSuggestions(poseData: PoseData): List<CoachingSuggestion> {
        return ruleEngine.analyze(poseData).map { issue ->
            CoachingSuggestion(
                id = generateId(),
                type = SuggestionType.FORM_CORRECTION,
                message = generateMessage(issue),
                priority = issue.severity.toPriority(),
                targetArea = issue.bodyPart,
                expectedImprovement = issue.expectedImprovement
            )
        }
    }
}
```

### Fallback Triggers:
- Network connectivity issues
- API service unavailability
- User consent withdrawal
- Privacy mode activation
- Rate limiting or quota exceeded

## Performance Considerations

### Response Time Optimization:
- **WebSocket Connection**: Persistent connection for reduced latency
- **Request Batching**: Efficient bundling of pose analysis requests
- **Caching Strategy**: Local caching of common coaching patterns
- **Progressive Enhancement**: Basic suggestions immediately, detailed analysis following

### Resource Management:
```kotlin
class RequestOptimizer {
    fun optimizeRequest(request: PoseAnalysisRequest): OptimizedRequest {
        return OptimizedRequest(
            compressedData = compressPoseData(request.poseData),
            priorityLevel = calculatePriority(request),
            batchingStrategy = determineBatchingStrategy(request)
        )
    }
}
```

## Cost Management

### Cost Optimization Strategies:
1. **Request Deduplication**: Avoid sending similar requests
2. **Intelligent Batching**: Combine related requests when possible
3. **Local Processing First**: Use AI for complex analysis only
4. **Caching**: Store and reuse common coaching suggestions
5. **Rate Limiting**: Prevent excessive API usage

### Usage Monitoring:
```kotlin
class ApiUsageMonitor {
    fun trackUsage(request: PoseAnalysisRequest, response: CoachingResponse) {
        metrics.record(
            timestamp = System.currentTimeMillis(),
            requestSize = request.estimateTokens(),
            responseSize = response.estimateTokens(),
            latency = response.processingTime,
            userId = request.anonymousUserId
        )
    }
}
```

## Quality Assurance

### Response Validation:
```kotlin
class ResponseValidator {
    fun validateResponse(response: String): ValidationResult {
        return try {
            val parsed = Json.decodeFromString<CoachingResponse>(response)
            ValidationResult.Success(parsed)
        } catch (e: Exception) {
            ValidationResult.Error("Invalid response format: ${e.message}")
        }
    }
}
```

### Testing Strategy:
- **Unit Tests**: Response parsing and validation
- **Integration Tests**: End-to-end API communication
- **Contract Tests**: API response schema validation
- **Performance Tests**: Latency and throughput benchmarks
- **Privacy Tests**: Data anonymization verification

## Consequences

### Positive:
- **Intelligent Coaching**: High-quality, contextual fitness guidance
- **Real-Time Feedback**: Immediate coaching suggestions during exercise
- **Scalable Solution**: Leverages Google's infrastructure
- **Structured Responses**: Reliable parsing and integration
- **Privacy Compliant**: Anonymized data processing
- **Professional Quality**: Enterprise-grade AI capabilities

### Negative:
- **External Dependency**: Reliance on Google's service availability
- **Cost Implications**: API usage costs scale with user base
- **Network Dependency**: Requires internet connection for AI features
- **Privacy Complexity**: Need for sophisticated anonymization
- **Rate Limiting**: Potential service limitations during peak usage

### Risk Mitigation:
- **Fallback System**: Local coaching capabilities for offline scenarios
- **Cost Controls**: Usage monitoring and optimization strategies
- **Privacy Protection**: Comprehensive anonymization and consent management
- **Service Monitoring**: Health checks and error recovery procedures

## Future Considerations

### Potential Enhancements:
- **Fine-Tuning**: Custom model training for specific exercise types
- **Multi-Modal Input**: Integration of additional sensor data
- **Personalization**: User-specific coaching style adaptation
- **Advanced Analytics**: Long-term progress tracking and analysis

### Migration Strategy:
- **Interface Abstraction**: Easy migration to alternative AI services
- **A/B Testing**: Gradual rollout and performance comparison
- **Hybrid Approach**: Combination of multiple AI services for optimal results

## Compliance and Standards

This decision supports:
- **Privacy-First Design**: Comprehensive data protection and anonymization
- **Performance Requirements**: Real-time coaching with acceptable latency
- **Quality Standards**: Professional-grade AI coaching capabilities
- **Cost Effectiveness**: Balanced feature quality with sustainable costs
- **User Experience**: Natural, helpful coaching suggestions

## References
- [Google Gemini API Documentation](https://ai.google.dev/docs)
- [Gemini Live API Guide](https://ai.google.dev/docs/live_api)
- [JSON Schema for Structured Output](https://json-schema.org/)
- [Privacy Engineering Best Practices](https://www.privacypatterns.org/)
- [WebSocket Integration Patterns](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API)