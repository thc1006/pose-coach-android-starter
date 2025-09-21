# C4 Model Diagrams - Pose Coach Camera App

## Overview

This document presents the C4 model diagrams for the Pose Coach Camera app architecture, providing different levels of abstraction from system context down to implementation details.

## Level 1: System Context Diagram

```
                    ┌─────────────────────────────────────┐
                    │                                     │
                    │              User                   │
                    │                                     │
                    │        (Fitness Enthusiast)        │
                    └─────────────┬───────────────────────┘
                                  │
                                  │ Uses camera for
                                  │ pose coaching
                                  │
                    ┌─────────────▼───────────────────────┐
                    │                                     │
                    │        Pose Coach App               │
                    │                                     │
                    │    (Android Camera Application)     │
                    │                                     │
                    │ • Real-time pose detection          │
                    │ • On-device processing              │
                    │ • AI-powered coaching               │
                    │ • Privacy-first design              │
                    └─────────────┬───────────────────────┘
                                  │
                                  │ Requests coaching
                                  │ suggestions
                                  │
                    ┌─────────────▼───────────────────────┐
                    │                                     │
                    │         Gemini API                  │
                    │                                     │
                    │    (Google AI Service)              │
                    │                                     │
                    │ • Structured output generation      │
                    │ • Natural language processing       │
                    │ • Live conversation API             │
                    └─────────────────────────────────────┘
```

**System Context Summary:**
- **User**: Fitness enthusiast using the app for pose coaching
- **Pose Coach App**: Main Android application providing real-time pose analysis
- **Gemini API**: External AI service for generating coaching suggestions

## Level 2: Container Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Pose Coach App                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────────┐  │
│  │                 │    │                 │    │                     │  │
│  │   Android App   │────│  MediaPipe      │────│    Local Storage   │  │
│  │                 │    │  Container      │    │                     │  │
│  │ • UI Components │    │                 │    │ • SQLite Database   │  │
│  │ • Activities    │    │ • Pose Detection│    │ • Encrypted Prefs   │  │
│  │ • Fragments     │    │ • Face Tracking │    │ • File System       │  │
│  │ • View Models   │    │ • Hand Tracking │    │ • Cache Storage     │  │
│  │                 │    │ • GPU Processing│    │                     │  │
│  └─────────────────┘    └─────────────────┘    └─────────────────────┘  │
│           │                       │                         │            │
│           │ Camera frames         │ Pose data              │ Store data │
│           │                       │                         │            │
│  ┌─────────▼───────┐    ┌─────────▼─────────┐    ┌─────────▼────────┐   │
│  │                 │    │                   │    │                  │   │
│  │  CameraX        │    │  Business Logic   │    │  Privacy Engine  │   │
│  │  Container      │    │  Container        │    │                  │   │
│  │                 │    │                   │    │ • Consent Mgmt   │   │
│  │ • Preview       │    │ • Use Cases       │    │ • Data Control   │   │
│  │ • Image Capture │    │ • Domain Models   │    │ • Audit Logging  │   │
│  │ • Image Analysis│    │ • Repositories    │    │ • GDPR Compliance│   │
│  │ • Lifecycle     │    │ • Validation      │    │                  │   │
│  └─────────────────┘    └───────────────────┘    └──────────────────┘   │
│                                   │                                      │
│                                   │ API requests                         │
│                                   │                                      │
└───────────────────────────────────┼──────────────────────────────────────┘
                                    │
                                    │ HTTPS/TLS
                                    │
                    ┌───────────────▼───────────────┐
                    │                               │
                    │        Gemini API             │
                    │                               │
                    │    (External Service)         │
                    │                               │
                    │ • Text Generation             │
                    │ • Structured Output           │
                    │ • Live Conversation           │
                    │ • Voice Processing            │
                    └───────────────────────────────┘
```

## Level 3: Component Diagram - Android App Container

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Android App Container                           │
├─────────────────────────────────────────────────────────────────────────┤
│                           Presentation Layer                            │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────┐  │
│  │   MainActivity  │  │  CameraActivity │  │    SettingsActivity     │  │
│  │                 │  │                 │  │                         │  │
│  │ • Navigation    │  │ • Camera UI     │  │ • Privacy Settings      │  │
│  │ • Permissions   │  │ • Pose Overlay  │  │ • API Configuration     │  │
│  │ • App Flow      │  │ • Coaching UI   │  │ • User Preferences      │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────┘  │
│           │                     │                           │            │
│           │                     │                           │            │
│  ┌─────────▼─────────┐  ┌───────▼───────┐  ┌─────────────▼─────────────┐ │
│  │                   │  │               │  │                           │ │
│  │  CameraViewModel  │  │PoseViewModel  │  │    SettingsViewModel      │ │
│  │                   │  │               │  │                           │ │
│  │ • Camera State    │  │ • Pose State  │  │ • Privacy State           │ │
│  │ • UI Events       │  │ • Coaching    │  │ • Configuration State     │ │
│  │ • Navigation      │  │ • Analytics   │  │ • User Preferences        │ │
│  └───────────────────┘  └───────────────┘  └───────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│                            Domain Layer                                 │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────┐  │
│  │ProcessPoseUseCase│ │GenerateSuggestion│  │   ManagePrivacyUseCase  │  │
│  │                 │  │UseCase          │  │                         │  │
│  │ • Pose Pipeline │  │                 │  │ • Consent Management    │  │
│  │ • Validation    │  │ • AI Integration│  │ • Data Control          │  │
│  │ • Stability     │  │ • Caching       │  │ • Audit Logging         │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────┘  │
│           │                     │                           │            │
│           │                     │                           │            │
│  ┌─────────▼─────────┐  ┌───────▼───────┐  ┌─────────────▼─────────────┐ │
│  │                   │  │               │  │                           │ │
│  │  PoseRepository   │  │SuggestionRepo │  │   PrivacyRepository       │ │
│  │                   │  │               │  │                           │ │
│  │ • Data Agg        │  │ • API Client  │  │ • Encrypted Storage       │ │
│  │ • Local Cache     │  │ • Rate Limit  │  │ • Consent State           │ │
│  │ • Processing      │  │ • Fallback    │  │ • Data Export/Delete      │ │
│  └───────────────────┘  └───────────────┘  └───────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│                             Data Layer                                  │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────┐  │
│  │MediaPipeSource  │  │ GeminiAPISource │  │   LocalStorageSource    │  │
│  │                 │  │                 │  │                         │  │
│  │ • Pose Detector │  │ • HTTP Client   │  │ • SQLite Database       │  │
│  │ • Face Detector │  │ • Auth Manager  │  │ • Encrypted Preferences │  │
│  │ • Hand Tracker  │  │ • Retry Logic   │  │ • File System           │  │
│  │ • GPU Delegate  │  │ • Error Handler │  │ • Cache Manager         │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

## Level 4: Code Diagram - ProcessPoseUseCase Component

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        ProcessPoseUseCase                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    ProcessPoseUseCase                           │   │
│  │                                                                 │   │
│  │  + execute(imageProxy: ImageProxy): Flow<PoseResult>            │   │
│  │  - validateInput(imageProxy: ImageProxy): Boolean               │   │
│  │  - processImage(imageProxy: ImageProxy): PoseData               │   │
│  │  - applyStabilityGate(poseData: PoseData): PoseData?            │   │
│  │  - updateAnalytics(poseData: PoseData): Unit                    │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                  │                                      │
│                                  │ uses                                 │
│                                  ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      PoseProcessor                              │   │
│  │                                                                 │   │
│  │  + processPose(imageProxy: ImageProxy): PoseData                │   │
│  │  + initialize(): Result<Unit>                                   │   │
│  │  + shutdown(): Unit                                             │   │
│  │  - convertImage(imageProxy: ImageProxy): MPImage                │   │
│  │  - extractLandmarks(result: PoseLandmarkerResult): PoseData     │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                  │                                      │
│                                  │ uses                                 │
│                                  ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                     StabilityGate                               │   │
│  │                                                                 │   │
│  │  + process(poseData: PoseData): PoseData?                       │   │
│  │  - checkConfidence(poseData: PoseData): Boolean                 │   │
│  │  - applyTemporalSmoothing(poseData: PoseData): PoseData         │   │
│  │  - validateGeometry(poseData: PoseData): Boolean                │   │
│  │  - updateHistory(poseData: PoseData): Unit                      │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                  │                                      │
│                                  │ uses                                 │
│                                  ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                  PerformanceMonitor                             │   │
│  │                                                                 │   │
│  │  + recordFrameProcessingTime(duration: Long): Unit              │   │
│  │  + recordMemoryUsage(bytes: Long): Unit                         │   │
│  │  + getAverageFrameRate(): Float                                 │   │
│  │  + isPerformanceAcceptable(): Boolean                           │   │
│  │  - analyzePerformanceMetrics(): PerformanceReport               │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│                              Data Models                                │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                        PoseData                                 │   │
│  │                                                                 │   │
│  │  + landmarks: List<PosePoint>                                   │   │
│  │  + confidence: Float                                            │   │
│  │  + timestamp: Long                                              │   │
│  │  + imageSize: Size                                              │   │
│  │  + stability: StabilityLevel                                    │   │
│  │                                                                 │   │
│  │  + isStable(): Boolean                                          │   │
│  │  + getBodyPart(type: BodyPartType): List<PosePoint>             │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      PosePoint                                  │   │
│  │                                                                 │   │
│  │  + x: Float                                                     │   │
│  │  + y: Float                                                     │   │
│  │  + z: Float                                                     │   │
│  │  + visibility: Float                                            │   │
│  │  + presence: Float                                              │   │
│  │  + type: LandmarkType                                           │   │
│  │                                                                 │   │
│  │  + isVisible(): Boolean                                         │   │
│  │  + isPresent(): Boolean                                         │   │
│  │  + distanceTo(other: PosePoint): Float                          │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

## Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            Data Flow Diagram                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  [Camera] ──30fps──▶ [ImageProxy] ──process──▶ [MediaPipe]              │
│                           │                         │                   │
│                           │                         ▼                   │
│                           │                   [PoseData]                │
│                           │                         │                   │
│                           │                         ▼                   │
│                           │                 [StabilityGate]             │
│                           │                         │                   │
│                           │                         ▼                   │
│                           │                 [ValidatedPose]             │
│                           │                         │                   │
│                           │                         ▼                   │
│                           │                 [BusinessLogic]             │
│                           │                         │                   │
│                           │                         ▼                   │
│                           │                 [CoachingEngine]            │
│                           │                    │         │              │
│                           │              ┌─────▼───┐     ▼              │
│                           │              │Local    │ [GeminiAPI]        │
│                           │              │Cache    │     │              │
│                           │              └─────────┘     ▼              │
│                           │                         [Suggestions]       │
│                           │                              │              │
│                           │                              ▼              │
│                           └──────────────▶ [UI Updates] ◀────           │
│                                                                         │
│  Privacy Flow:                                                          │
│  [UserConsent] ──check──▶ [PrivacyEngine] ──allow/deny──▶ [DataFlow]    │
│       │                         │                            │          │
│       ▼                         ▼                            ▼          │
│  [ConsentDB] ◀──audit──── [AuditLogger] ◀──track──── [DataProcessor]    │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

## Deployment Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Deployment Diagram                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                     Android Device                              │   │
│  │                                                                 │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │   │
│  │  │   CPU Cores     │  │      GPU        │  │      NPU        │ │   │
│  │  │                 │  │                 │  │                 │ │   │
│  │  │ • Main Thread   │  │ • MediaPipe     │  │ • ML Models     │ │   │
│  │  │ • Background    │  │   Processing    │  │ • Pose Detection│ │   │
│  │  │   Workers       │  │ • Rendering     │  │ • Face Tracking │ │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘ │   │
│  │                                                                 │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │   │
│  │  │   App Memory    │  │ Local Storage   │  │   Camera HW     │ │   │
│  │  │                 │  │                 │  │                 │ │   │
│  │  │ • Heap (512MB)  │  │ • SQLite DB     │  │ • Front Camera  │ │   │
│  │  │ • Native (256MB)│  │ • Preferences   │  │ • Back Camera   │ │   │
│  │  │ • GPU (128MB)   │  │ • Cache Files   │  │ • Depth Sensor  │ │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘ │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                  │                                      │
│                                  │ HTTPS/TLS                           │
│                                  │                                      │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      Google Cloud                              │   │
│  │                                                                 │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │   │
│  │  │   Gemini API    │  │  Voice API      │  │   Analytics     │ │   │
│  │  │                 │  │                 │  │                 │ │   │
│  │  │ • Text Gen      │  │ • Text-to-Speech│  │ • Firebase      │ │   │
│  │  │ • Structured    │  │ • Speech-to-Text│  │ • Crash Reports │ │   │
│  │  │   Output        │  │ • Live API      │  │ • Performance   │ │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘ │   │
│  │                                                                 │   │
│  │  ┌─────────────────┐  ┌─────────────────┐                      │   │
│  │  │  Load Balancer  │  │   CDN           │                      │   │
│  │  │                 │  │                 │                      │   │
│  │  │ • Request       │  │ • Model Files   │                      │   │
│  │  │   Distribution  │  │ • Static Assets │                      │   │
│  │  │ • Health Checks │  │ • Global Cache  │                      │   │
│  │  └─────────────────┘  └─────────────────┘                      │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

## Security Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Security Architecture                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      Application Layer                          │   │
│  │                                                                 │   │
│  │  [User Input] ──validate──▶ [Input Sanitizer] ──clean──▶        │   │
│  │                                    │                            │   │
│  │                                    ▼                            │   │
│  │  [Permission Gate] ◀──check── [Privacy Engine]                  │   │
│  │          │                          │                            │   │
│  │          ▼                          ▼                            │   │
│  │  [Feature Access] ──audit──▶ [Audit Logger]                     │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                  │                                      │
│                                  ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      Data Protection Layer                      │   │
│  │                                                                 │   │
│  │  [Sensitive Data] ──encrypt──▶ [AES-256] ──store──▶             │   │
│  │                                    │                            │   │
│  │                                    ▼                            │   │
│  │  [Android Keystore] ◀──keys── [Key Manager]                     │   │
│  │          │                          │                            │   │
│  │          ▼                          ▼                            │   │
│  │  [TEE/HSM] ──protect──▶ [Encrypted Storage]                     │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                  │                                      │
│                                  ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Network Security Layer                       │   │
│  │                                                                 │   │
│  │  [API Request] ──auth──▶ [API Key Manager] ──secure──▶          │   │
│  │                                │                                │   │
│  │                                ▼                                │   │
│  │  [Certificate Pinning] ◀──verify── [TLS 1.3]                    │   │
│  │                │                    │                            │   │
│  │                ▼                    ▼                            │   │
│  │  [HTTPS Channel] ──monitor──▶ [Security Monitor]                │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  Security Controls:                                                     │
│  • Biometric Authentication (Optional)                                 │
│  • Root/Jailbreak Detection                                            │
│  • Anti-Tampering Protection                                           │
│  • Runtime Application Self-Protection (RASP)                          │
│  • Data Loss Prevention (DLP)                                          │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

## Performance Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       Performance Architecture                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Real-time Processing Pipeline (Target: <33ms per frame)                │
│                                                                         │
│  [Camera Frame] ──<1ms──▶ [Frame Queue] ──<2ms──▶ [GPU Buffer]          │
│         │                      │                      │                 │
│         │                      ▼                      ▼                 │
│         │             [Memory Pool] ──reuse──▶ [MediaPipe]             │
│         │                      │                      │                 │
│         │                      │              ┌───────▼────────┐        │
│         │                      │              │ Pose Detection │        │
│         │                      │              │                │        │
│         │                      │              │ • GPU Delegate │        │
│         │                      │              │ • Model Cache  │        │
│         │                      │              │ • Batch Proc   │        │
│         │                      │              └───────┬────────┘        │
│         │                      │                      │                 │
│         │                      ▼                      ▼                 │
│         │              [Object Recycling] ◀──return── [PoseData]        │
│         │                      │                      │                 │
│         │                      │              ┌───────▼────────┐        │
│         │                      │              │ Stability Gate │        │
│         │                      │              │                │        │
│         │                      │              │ • Confidence   │        │
│         │                      │              │ • Temporal     │        │
│         │                      │              │ • Geometric    │        │
│         │                      │              └───────┬────────┘        │
│         │                      │                      │                 │
│         └──update──▶ [UI Thread] ◀──notify──<5ms──────┘                 │
│                             │                                           │
│                             ▼                                           │
│                     [Overlay Render] ──<16ms──▶ [Display]               │
│                                                                         │
│  Performance Monitoring:                                                │
│  • Frame Rate: Target 30 FPS (33ms budget)                             │
│  • Memory Usage: < 512MB heap, < 256MB native                          │
│  • CPU Usage: < 80% sustained, < 95% peak                              │
│  • Battery: < 10% drain per hour                                       │
│  • GPU Usage: < 70% sustained                                          │
│                                                                         │
│  Optimization Strategies:                                               │
│  • Adaptive Quality (reduce resolution/FPS on thermal throttling)      │
│  • Smart Caching (LRU + prediction-based)                              │
│  • Background Processing (non-critical tasks)                          │
│  • Memory Pools (reduce GC pressure)                                   │
│  • GPU Scheduling (priority queues)                                    │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

## Summary

The C4 model diagrams provide a comprehensive view of the Pose Coach Camera app architecture:

1. **System Context**: Shows the app's relationship with users and external services
2. **Container Diagram**: Illustrates the major containers and their responsibilities
3. **Component Diagram**: Details the internal structure of the Android app
4. **Code Diagram**: Shows the detailed implementation of key components
5. **Data Flow**: Visualizes how data moves through the system
6. **Deployment**: Shows the runtime environment and infrastructure
7. **Security**: Illustrates the multi-layered security architecture
8. **Performance**: Details the real-time processing pipeline and optimization strategies

These diagrams serve as the foundation for development, ensuring all team members understand the system architecture and can make informed decisions during implementation.