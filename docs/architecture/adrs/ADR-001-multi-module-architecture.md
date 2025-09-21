# ADR-001: Multi-Module Android Architecture

## Status
Accepted

## Context
The Pose Coach Android application requires a scalable, maintainable architecture that supports:
- Real-time pose detection and analysis
- AI-powered coaching suggestions
- Privacy-first data handling
- High performance (<30ms inference)
- Comprehensive testing (>80% coverage)
- Future extensibility for new pose types and coaching features

We need to decide on the overall architectural approach and module structure.

## Decision
We will implement a **multi-module Android architecture** with the following structure:

```
pose-coach-android/
├── app/                    # Main application module
├── core-pose/              # Pose detection and MediaPipe integration
├── core-geom/              # 3D geometry calculations and pose metrics
├── suggestions-api/        # AI coaching suggestions and Gemini integration
└── tests/                  # Shared testing utilities and integration tests
```

### Module Responsibilities:

1. **app**: Main Android application module
   - UI/UX layer (Activities, Fragments, ViewModels)
   - Camera integration and preview
   - User interface and navigation
   - Application lifecycle management

2. **core-pose**: Pose detection core functionality
   - MediaPipe Tasks Vision integration
   - Real-time pose landmark detection
   - Performance optimization and monitoring
   - Camera frame processing pipeline

3. **core-geom**: Geometric calculations and analysis
   - 3D geometry utilities and calculations
   - Joint angle and distance measurements
   - Pose classification algorithms
   - Movement pattern analysis

4. **suggestions-api**: AI coaching integration
   - Google Gemini 2.5 Live API integration
   - Privacy-preserving data aggregation
   - Structured output processing
   - Coaching suggestion generation

5. **tests**: Testing infrastructure
   - Shared test utilities and mocks
   - Integration test scenarios
   - Performance benchmarking tools

## Rationale

### Advantages:
1. **Separation of Concerns**: Each module has a clear, focused responsibility
2. **Testability**: Modules can be tested in isolation with clear interfaces
3. **Reusability**: Core modules can be reused in other applications
4. **Parallel Development**: Teams can work on different modules simultaneously
5. **Build Performance**: Incremental compilation and faster build times
6. **Dependency Management**: Clear dependency boundaries prevent circular dependencies
7. **Privacy Architecture**: Sensitive operations isolated in specific modules

### Module Dependencies:
```
app → suggestions-api → core-pose → core-geom
     ↘ core-pose ────────────────────┘
      ↘ core-geom
```

### Technology Alignment:
- **Android Gradle Plugin**: Native support for multi-module projects
- **Kotlin Multiplatform**: Future extensibility to other platforms
- **MediaPipe**: Modular integration in dedicated core-pose module
- **Gemini API**: Isolated in suggestions-api for privacy controls

## Alternatives Considered

### 1. Monolithic Single Module
- **Pros**: Simpler initial setup, fewer build files
- **Cons**: Poor separation of concerns, difficult testing, build performance issues, tight coupling

### 2. Layered Architecture (Horizontal Modules)
- **Pros**: Clear architectural layers
- **Cons**: Less domain-focused, potential for god modules, harder to maintain

### 3. Feature-Based Modules (Vertical Slicing)
- **Pros**: Feature isolation
- **Cons**: Code duplication, unclear shared responsibilities for core functionality

## Consequences

### Positive:
- Clear separation between pose detection, geometry processing, and AI integration
- Enhanced testability with isolated module testing
- Better privacy controls with dedicated boundaries
- Improved build performance through incremental compilation
- Future extensibility for new features and platforms
- Easier code reviews and maintenance

### Negative:
- Initial setup complexity with multiple build.gradle files
- Need for careful interface design between modules
- Potential for over-engineering in early phases
- Module communication overhead (minimal in practice)

## Implementation Guidelines

### Module Interface Design:
- Use Kotlin interfaces for module boundaries
- Implement dependency inversion principle
- Provide factory methods for module instantiation
- Use data classes for inter-module communication

### Example Interface:
```kotlin
// core-pose module interface
interface PoseDetector {
    suspend fun detectPose(image: CameraFrame): Result<PoseData>
    fun getPerformanceMetrics(): PerformanceMetrics
}

// suggestions-api module interface
interface CoachingSuggestionProvider {
    suspend fun getSuggestions(poseData: PoseData): Result<List<CoachingSuggestion>>
    suspend fun initializeSession(exerciseType: ExerciseType): Result<SessionId>
}
```

### Testing Strategy:
- Unit tests within each module
- Integration tests in dedicated tests module
- Mock implementations for inter-module dependencies
- Contract testing for module interfaces

### Privacy Implementation:
- core-pose and core-geom modules operate entirely on-device
- suggestions-api module handles privacy filtering and consent
- Clear data flow boundaries enforced by module structure

## Compliance and Quality

This decision supports:
- **Privacy-First Design**: Module boundaries enforce privacy controls
- **Performance Requirements**: Optimized modules for <30ms inference
- **Testing Coverage**: Modular testing enables >80% coverage target
- **TDD Methodology**: Clear interfaces support test-driven development
- **Maintainability**: Focused modules improve code quality and maintenance

## Next Steps
1. Create module structure and build.gradle configurations
2. Define module interfaces and data contracts
3. Implement core-geom module first (no external dependencies)
4. Implement core-pose module with MediaPipe integration
5. Implement suggestions-api module with privacy controls
6. Integrate modules in main app module
7. Establish testing strategy and CI/CD pipeline

## References
- [Android Multi-Module Architecture](https://developer.android.com/topic/modularization)
- [Clean Architecture Principles](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [MediaPipe Tasks Vision](https://developers.google.com/mediapipe/solutions/vision/pose_landmarker)
- [Google Gemini API Documentation](https://ai.google.dev/docs)