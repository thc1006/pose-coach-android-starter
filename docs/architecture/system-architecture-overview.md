# Pose Coach Android - System Architecture Overview

## Project Overview
The Pose Coach Android application is a privacy-first, on-device pose analysis and coaching system that leverages MediaPipe for real-time pose detection and Google's Gemini 2.5 for intelligent coaching suggestions.

## Architecture Principles

### 1. Privacy-First Design
- **On-device processing as default**: All pose analysis occurs locally on the device
- **Selective cloud processing**: Only anonymized, aggregated data sent to cloud when explicitly consented
- **Zero-knowledge architecture**: Raw video data never leaves the device
- **Granular consent management**: Users control what data is processed and where

### 2. Performance Requirements
- **<30ms inference latency**: Real-time pose detection and analysis
- **60fps camera processing**: Smooth video experience
- **Efficient memory usage**: Optimized for mobile devices
- **Battery optimization**: Intelligent power management

### 3. Modular Architecture
- **Separation of concerns**: Clear boundaries between modules
- **Dependency inversion**: High-level modules don't depend on low-level modules
- **Testability**: Each module can be tested in isolation
- **Scalability**: Easy to add new features and pose types

### 4. Quality Assurance
- **>80% test coverage**: Comprehensive testing strategy
- **TDD methodology**: Test-driven development workflow
- **Continuous integration**: Automated testing and validation
- **Performance monitoring**: Real-time performance tracking

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                           APP MODULE                            │
├─────────────────────────────────────────────────────────────────┤
│  UI Layer (Activities, Fragments, ViewModels)                  │
│  - Camera Integration & Preview                                │
│  - Real-time Pose Visualization                               │
│  - Coaching Interface & Feedback                              │
│  - Privacy Controls & Settings                                │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                      SUGGESTIONS-API                           │
├─────────────────────────────────────────────────────────────────┤
│  - Gemini 2.5 Integration                                     │
│  - Live API Connection                                         │
│  - Structured Output Processing                               │
│  - Privacy-Preserving Data Pipeline                           │
│  - Coaching Logic & Rules Engine                              │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                        CORE-POSE                               │
├─────────────────────────────────────────────────────────────────┤
│  - MediaPipe Integration                                       │
│  - Real-time Pose Detection                                   │
│  - Pose Landmark Processing                                   │
│  - Movement Analysis & Tracking                               │
│  - Performance Optimization                                   │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                       CORE-GEOM                                │
├─────────────────────────────────────────────────────────────────┤
│  - 3D Geometry Calculations                                   │
│  - Angle & Distance Measurements                              │
│  - Pose Classification Algorithms                             │
│  - Mathematical Utilities                                     │
└─────────────────────────────────────────────────────────────────┘

```

## Module Dependencies

```
app → suggestions-api → core-pose → core-geom
     ↘ core-pose ────────────────────┘
      ↘ core-geom
```

## Technology Stack

### Core Technologies
- **Android SDK 34** (minSdk 24, targetSdk 34)
- **Kotlin 1.9.20** with Coroutines for asynchronous processing
- **CameraX 1.3.0** for camera management and real-time processing
- **MediaPipe Tasks Vision 0.10.14** for pose detection
- **Google AI Generative AI 0.9.0** for Gemini integration

### Architecture Components
- **MVVM Architecture** with ViewModels and LiveData/StateFlow
- **Dependency Injection** using Hilt/Dagger (to be configured)
- **Repository Pattern** for data access abstraction
- **Clean Architecture** with clear layer separation

### Security & Privacy
- **EncryptedSharedPreferences** for sensitive data storage
- **Certificate Pinning** for API communications
- **ProGuard/R8** for code obfuscation
- **Runtime Permissions** with clear privacy explanations

## Next Steps
1. Detailed module architecture design
2. Data flow specification
3. Integration point definitions
4. Privacy architecture design
5. Testing strategy development