# C4 Model Diagrams - Pose Coach Android Architecture

## Overview
This document provides C4 model diagrams for the Pose Coach Android application architecture, following the hierarchical approach from system context down to code-level details.

## Level 1: System Context Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             SYSTEM CONTEXT                                 │
└─────────────────────────────────────────────────────────────────────────────┘

     ┌─────────────┐                                              ┌─────────────┐
     │             │                                              │             │
     │    User     │ ◄─────────── Real-time coaching ─────────► │  Google     │
     │  (Fitness   │              suggestions and               │  Gemini 2.5 │
     │ Enthusiast) │              pose feedback                 │  Live API   │
     │             │                                              │             │
     └─────────────┘                                              └─────────────┘
           │                                                              ▲
           │                                                              │
           │ Uses mobile app for                                         │
           │ pose coaching                                                │
           ▼                                                              │
     ┌─────────────────────────────────────────────────────────────────────┐ │
     │                                                                     │ │
     │                  POSE COACH ANDROID APP                           │ │
     │                                                                     │ │
     │  • Real-time pose detection and analysis                           │ │
     │  • Privacy-first on-device processing                              │ │
     │  • AI-powered coaching suggestions                                  │ │
     │  • Performance tracking and feedback                               │ │
     │                                                                     │ │
     └─────────────────────────────────────────────────────────────────────┘ │
           │                                                              │
           │                                                              │
           ▼                                                              │
     ┌─────────────┐                                                      │
     │             │                                                      │
     │  MediaPipe  │ ◄────── Pose detection models ─────────────────────┘
     │   Tasks     │         and computer vision
     │  Vision     │         processing
     │             │
     └─────────────┘

Key Relationships:
• User interacts with Android app for fitness coaching
• App processes camera feed using MediaPipe for pose detection
• App sends anonymized data to Gemini API for coaching suggestions
• All personal data processing occurs on-device for privacy
```

## Level 2: Container Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CONTAINER DIAGRAM                                 │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────┐
                              │    User     │
                              │ (Fitness    │
                              │ Enthusiast) │
                              └─────────────┘
                                     │
                                     │ Uses mobile app
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          POSE COACH ANDROID APP                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐         │
│  │                 │    │                 │    │                 │         │
│  │   ANDROID APP   │───▶│  SUGGESTIONS    │───▶│   CORE-POSE     │         │
│  │                 │    │      API        │    │                 │         │
│  │  • UI/UX Layer  │    │  • Gemini 2.5   │    │ • MediaPipe     │         │
│  │  • Camera View  │    │    Integration  │    │   Integration   │         │
│  │  • Feedback     │    │  • Privacy      │    │ • Real-time     │         │
│  │  • Settings     │    │    Filters      │    │   Detection     │         │
│  │                 │    │  • Coaching     │    │ • Performance   │         │
│  └─────────────────┘    │    Logic        │    │   Optimization  │         │
│                         └─────────────────┘    └─────────────────┘         │
│                                  │                       │                 │
│                                  │                       ▼                 │
│                                  │              ┌─────────────────┐         │
│                                  │              │                 │         │
│                                  │              │   CORE-GEOM     │         │
│                                  │              │                 │         │
│                                  │              │ • 3D Geometry   │         │
│                                  │              │ • Calculations  │         │
│                                  │              │ • Pose Metrics  │         │
│                                  │              │ • Analysis      │         │
│                                  │              │                 │         │
│                                  │              └─────────────────┘         │
│                                  │                                          │
└─────────────────────────────────────────────────────────────────────────────┘
                                   │
                                   │ HTTPS/WebSocket
                                   │ (Anonymized data only)
                                   ▼
                              ┌─────────────┐
                              │   Google    │
                              │  Gemini 2.5 │
                              │  Live API   │
                              └─────────────┘

Container Relationships:
• Android App provides user interface and orchestrates processing
• Suggestions API handles AI integration and privacy-preserving data flow
• Core-Pose manages MediaPipe integration and real-time pose detection
• Core-Geom performs mathematical calculations and pose analysis
• External APIs provide AI capabilities while maintaining privacy
```

## Level 3: Component Diagram - Core-Pose Module

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CORE-POSE COMPONENTS                                │
└─────────────────────────────────────────────────────────────────────────────┘

     ┌─────────────┐                                              ┌─────────────┐
     │  Android    │ ──── Camera frames ────┐                    │  Core-Geom  │
     │  App Layer  │                        │                    │   Module    │
     └─────────────┘                        ▼                    └─────────────┘
                                   ┌─────────────────┐                   ▲
                                   │                 │                   │
                                   │ CameraX Manager │                   │
                                   │                 │                   │
                                   │ • Frame Capture │                   │
                                   │ • Lifecycle Mgmt│                   │
                                   │ • Format Convert│                   │
                                   └─────────────────┘                   │
                                            │                           │
                                            │ Preprocessed frames       │
                                            ▼                           │
                                   ┌─────────────────┐                   │
                                   │                 │                   │
                                   │MediaPipe Task   │                   │ Pose
                                   │    Manager      │                   │ metrics
                                   │                 │                   │ and
                                   │ • Model Loading │                   │ landmarks
                                   │ • Configuration │                   │
                                   │ • Lifecycle     │                   │
                                   └─────────────────┘                   │
                                            │                           │
                                            │ Raw pose data             │
                                            ▼                           │
                                   ┌─────────────────┐                   │
                                   │                 │                   │
                                   │ Pose Processor  │───────────────────┘
                                   │                 │
                                   │ • Validation    │
                                   │ • Filtering     │
                                   │ • Tracking      │
                                   │ • Smoothing     │
                                   └─────────────────┘
                                            │
                                            │ Validated pose data
                                            ▼
        ┌─────────────────┐        ┌─────────────────┐        ┌─────────────────┐
        │                 │        │                 │        │                 │
        │ Performance     │◄───────│ Movement        │────────▶│ Data Pipeline   │
        │ Monitor         │        │ Tracker         │        │ Manager         │
        │                 │        │                 │        │                 │
        │ • Latency Track │        │ • Temporal      │        │ • Data Flow     │
        │ • Memory Usage  │        │   Analysis      │        │ • Format Conv   │
        │ • Error Rates   │        │ • Pattern Recog │        │ • Privacy Filter│
        │ • Optimization  │        │ • Quality Assess│        │ • Output Stream │
        └─────────────────┘        └─────────────────┘        └─────────────────┘

Component Interactions:
• CameraX Manager captures and preprocesses camera frames
• MediaPipe Task Manager handles model operations and pose detection
• Pose Processor validates, filters, and enhances pose data
• Movement Tracker analyzes temporal patterns and movement quality
• Performance Monitor tracks system performance and optimization
• Data Pipeline Manager handles data flow and privacy filtering
```

## Level 3: Component Diagram - Suggestions API Module

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      SUGGESTIONS API COMPONENTS                            │
└─────────────────────────────────────────────────────────────────────────────┘

     ┌─────────────┐                                              ┌─────────────┐
     │ Core-Pose   │ ──── Pose metrics ─────┐                    │ Android App │
     │  Module     │                        │                    │   Module    │
     └─────────────┘                        ▼                    └─────────────┘
                                   ┌─────────────────┐                   ▲
                                   │                 │                   │
                                   │ Data Aggregator │                   │
                                   │                 │                   │
                                   │ • Pose Metrics  │                   │
                                   │ • Context Data  │                   │
                                   │ • Session Info  │                   │
                                   └─────────────────┘                   │
                                            │                           │
                                            │ Aggregated data           │
                                            ▼                           │
                                   ┌─────────────────┐                   │
                                   │                 │                   │ Coaching
                                   │ Privacy Filter  │                   │ suggestions
                                   │                 │                   │ and
                                   │ • Anonymization │                   │ feedback
                                   │ • Data Consent  │                   │
                                   │ • PII Removal   │                   │
                                   └─────────────────┘                   │
                                            │                           │
                                            │ Anonymized data           │
                                            ▼                           │
                                   ┌─────────────────┐                   │
                                   │                 │                   │
                                   │ Gemini Live API │                   │
                                   │    Client       │                   │
                                   │                 │                   │
                                   │ • WebSocket Mgmt│                   │
                                   │ • Request Opt   │                   │
                                   │ • Retry Logic   │                   │
                                   │ • Session Mgmt  │                   │
                                   └─────────────────┘                   │
                                            │                           │
                                            │ API responses             │
                                            ▼                           │
        ┌─────────────────┐        ┌─────────────────┐        ┌─────────────────┐
        │                 │        │                 │        │                 │
        │ Local Coaching  │◄───────│ Response Parser │────────▶│ Coaching Engine │
        │ Fallback        │        │                 │        │                 │
        │                 │        │ • JSON Parsing  │        │ • Rule Engine   │
        │ • Rule Engine   │        │ • Validation    │        │ • Suggestion    │
        │ • Basic Hints   │        │ • Structured    │        │   Ranking       │
        │ • Offline Mode  │        │   Output        │        │ • Context Aware │
        │ • Safety Check  │        │ • Error Handle  │        │ • Personalization│
        └─────────────────┘        └─────────────────┘        └─────────────────┘

Component Interactions:
• Data Aggregator collects and structures pose metrics for analysis
• Privacy Filter ensures data protection and user consent compliance
• Gemini Live API Client manages real-time communication with AI service
• Response Parser processes and validates AI-generated coaching suggestions
• Coaching Engine applies business logic and contextual enhancement
• Local Coaching Fallback provides offline capabilities and error recovery
```

## Level 3: Component Diagram - Android App Module

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        ANDROID APP COMPONENTS                              │
└─────────────────────────────────────────────────────────────────────────────┘

     ┌─────────────┐                                              ┌─────────────┐
     │    User     │ ──── Interactions ─────┐                    │ Suggestions │
     │             │                        │                    │ API Module  │
     └─────────────┘                        ▼                    └─────────────┘
                                   ┌─────────────────┐                   ▲
                                   │                 │                   │
                                   │ UI Controller   │                   │
                                   │                 │                   │
                                   │ • Activity Mgmt │                   │
                                   │ • Fragment Nav  │                   │
                                   │ • Lifecycle     │                   │
                                   └─────────────────┘                   │
                                            │                           │
                                            │ UI state updates          │
                                            ▼                           │
                                   ┌─────────────────┐                   │
                                   │                 │                   │ Coaching
                                   │ Camera Preview  │                   │ requests
                                   │   Component     │                   │ and pose
                                   │                 │                   │ data
                                   │ • Live Preview  │                   │
                                   │ • Overlay       │                   │
                                   │ • Pose Visual   │                   │
                                   └─────────────────┘                   │
                                            │                           │
                                            │ Camera frames             │
                                            ▼                           │
                                   ┌─────────────────┐                   │
                                   │                 │                   │
                                   │ Pose Coaching   │───────────────────┘
                                   │   ViewModel     │
                                   │                 │
                                   │ • State Mgmt    │
                                   │ • Data Binding  │
                                   │ • Business Logic│
                                   └─────────────────┘
                                            │
                                            │ Processed data
                                            ▼
        ┌─────────────────┐        ┌─────────────────┐        ┌─────────────────┐
        │                 │        │                 │        │                 │
        │ Feedback        │◄───────│ Settings        │────────▶│ Privacy         │
        │ Presenter       │        │ Manager         │        │ Control Center  │
        │                 │        │                 │        │                 │
        │ • Visual Cues   │        │ • User Prefs    │        │ • Consent Mgmt  │
        │ • Audio Coach   │        │ • App Config    │        │ • Data Controls │
        │ • Haptic Feed   │        │ • Performance   │        │ • Audit Logs    │
        │ • Progress Track│        │ • Notifications │        │ • Transparency  │
        └─────────────────┘        └─────────────────┘        └─────────────────┘

Component Interactions:
• UI Controller manages app navigation and user interface lifecycle
• Camera Preview Component handles real-time video display and pose overlay
• Pose Coaching ViewModel coordinates data flow and business logic
• Feedback Presenter provides multi-modal coaching feedback to users
• Settings Manager handles user preferences and app configuration
• Privacy Control Center manages data consent and user privacy controls
```

## Level 4: Code Level Diagram - MediaPipe Integration

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     CODE LEVEL - MEDIAPIPE INTEGRATION                     │
└─────────────────────────────────────────────────────────────────────────────┘

class MediaPipeTaskManager {
    - poseLandmarker: PoseLandmarker?
    - taskExecutor: ExecutorService
    - performanceMonitor: PerformanceMonitor

    + initialize(context: Context): Result<Unit>
    + detectPose(image: MPImage, timestamp: Long): Result<PoseLandmarkerResult>
    + updateConfiguration(config: MediaPipeConfig): Result<Unit>
    + release(): void
    - createPoseLandmarkerOptions(): PoseLandmarkerOptions
    - handleDetectionResult(result: PoseLandmarkerResult): void
}
                                    │
                                    │ uses
                                    ▼
interface PoseDetectionListener {
    + onPoseDetected(result: PoseLandmarkerResult): void
    + onDetectionError(error: MediaPipeError): void
    + onPerformanceUpdate(metrics: PerformanceMetrics): void
}
                                    │
                                    │ implements
                                    ▼
class PoseProcessor : PoseDetectionListener {
    - validator: PoseLandmarkValidator
    - tracker: PoseTracker
    - smoother: TemporalSmoother

    + processPose(landmarks: List<NormalizedLandmark>): ProcessedPose
    + validatePose(landmarks: List<NormalizedLandmark>): ValidationResult
    + trackMovement(currentPose: ProcessedPose): MovementAnalysis
    - applyTemporalSmoothing(poses: List<ProcessedPose>): ProcessedPose
    - calculateConfidenceScore(landmarks: List<NormalizedLandmark>): Float
}
                                    │
                                    │ uses
                                    ▼
data class ProcessedPose(
    val landmarks: List<PoseLandmark>,
    val confidence: Float,
    val timestamp: Long,
    val qualityScore: Float,
    val movementMetrics: MovementMetrics?
)

class PerformanceMetrics(
    val detectionLatencyMs: Long,
    val processingTimeMs: Long,
    val memoryUsageMB: Float,
    val frameRate: Float
)

enum class MediaPipeError {
    MODEL_NOT_LOADED,
    INFERENCE_FAILURE,
    INVALID_INPUT,
    UNKNOWN_ERROR
}

Code Structure Relationships:
• MediaPipeTaskManager orchestrates pose detection workflow
• PoseDetectionListener provides callback interface for async operations
• PoseProcessor implements business logic for pose analysis
• Data classes define structured information exchange
• Enums provide type-safe error handling and configuration
```

## Architecture Quality Attributes

### 1. Performance Characteristics
- **Latency**: <30ms pose detection with <16.67ms frame processing
- **Throughput**: 60fps real-time processing capability
- **Resource Usage**: Optimized memory allocation and CPU utilization
- **Scalability**: Adaptive quality based on device capabilities

### 2. Security & Privacy Features
- **Data Protection**: On-device processing with selective cloud communication
- **Encryption**: TLS 1.3 for network communications, encrypted local storage
- **Access Control**: Granular permissions and consent management
- **Audit Trail**: Comprehensive logging of data access and processing

### 3. Reliability & Availability
- **Error Resilience**: Comprehensive error handling and recovery mechanisms
- **Fallback Systems**: Local processing capabilities when cloud services unavailable
- **Health Monitoring**: Real-time system health tracking and alerting
- **Quality Assurance**: >80% test coverage with TDD methodology

### 4. Maintainability & Extensibility
- **Modular Design**: Clear separation of concerns across modules
- **Clean Architecture**: Dependency inversion and abstraction layers
- **Documentation**: Comprehensive architecture documentation and ADRs
- **Testing Strategy**: Automated testing with CI/CD pipeline integration

This C4 model provides a comprehensive architectural blueprint for the Pose Coach Android application, ensuring clear understanding of system structure, component relationships, and design decisions at multiple levels of abstraction.