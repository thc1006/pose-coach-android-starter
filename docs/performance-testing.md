# Performance Benchmark Test Suite

This document describes the comprehensive performance benchmark tests created for the Pose Coach application following TDD (Test-Driven Development) principles.

## Overview

The performance test suite ensures the application meets the requirements specified in CLAUDE.md:

- **ML Kit pose detection latency**: <100ms
- **Frame processing throughput**: 15-30 FPS
- **WebSocket connection time**: <3s
- **Voice response latency**: <2s
- **Memory efficiency**: Stable memory usage with minimal leaks
- **Battery optimization**: Efficient power consumption

## Test Files Structure

### 1. Unit Tests (`app/src/test/kotlin/com/posecoach/performance/`)

#### PoseDetectionPerformanceTest.kt
- **Purpose**: Tests ML Kit pose detection performance
- **Key Tests**:
  - Single frame detection latency (<100ms)
  - Frame processing throughput (15-30 FPS)
  - Memory usage during continuous detection
  - CPU usage benchmarks
  - Concurrent pose detection streams
  - Accuracy under time pressure

#### LiveApiPerformanceTest.kt
- **Purpose**: Tests Live API integration performance
- **Key Tests**:
  - WebSocket connection establishment (<3s)
  - Voice response latency (<2s)
  - Audio streaming performance
  - Network resilience and reconnection
  - Concurrent voice sessions
  - Message throughput
  - Memory usage under load

#### CoordinateMapperPerformanceTest.kt
- **Purpose**: Tests coordinate transformation performance
- **Key Tests**:
  - Single landmark transformation speed (<1ms)
  - Batch processing performance (33 landmarks <5ms)
  - Memory efficiency for large landmark sets
  - Concurrent transformations
  - Rotation transformation performance
  - Precision across different screen sizes
  - Memory allocation patterns

### 2. Android Tests (`app/src/androidTest/kotlin/com/posecoach/performance/`)

#### EndToEndPerformanceTest.kt
- **Purpose**: Tests complete system performance
- **Key Tests**:
  - Complete voice coach session performance
  - Memory leak detection during long sessions
  - Battery usage optimization
  - System performance under stress
  - Error recovery scenarios
  - Resource management

## TDD Approach

All tests follow the TDD RED-GREEN-REFACTOR cycle:

1. **RED**: Write failing tests that define performance requirements
2. **GREEN**: Implement minimal code to pass the tests
3. **REFACTOR**: Optimize for performance while maintaining test coverage

## Performance Thresholds

| Component | Metric | Threshold | Test Location |
|-----------|--------|-----------|---------------|
| Pose Detection | Latency | <100ms | PoseDetectionPerformanceTest |
| Pose Detection | Throughput | 15-30 FPS | PoseDetectionPerformanceTest |
| Live API | Connection | <3s | LiveApiPerformanceTest |
| Live API | Voice Response | <2s | LiveApiPerformanceTest |
| Coordinate Mapping | Single Transform | <1ms | CoordinateMapperPerformanceTest |
| Coordinate Mapping | Batch Transform | <5ms (33 landmarks) | CoordinateMapperPerformanceTest |
| Memory | Leak Threshold | <20MB increase | EndToEndPerformanceTest |
| Battery | Drain Rate | <2% per 2 minutes | EndToEndPerformanceTest |
| CPU | Average Usage | <60% | EndToEndPerformanceTest |

## Mock Strategy

The tests use MockK for mocking dependencies:
- **Pose Detection**: Mock ML Kit pose detector responses
- **Live API**: Mock WebSocket connections and audio processing
- **Coordinate Mapping**: Mock landmark data generation
- **System Monitoring**: Mock battery, memory, and CPU monitors

## Running the Tests

### Unit Tests
```bash
./gradlew :app:testDebugUnitTest --tests "com.posecoach.performance.*"
```

### Android Tests
```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.posecoach.performance.*"
```

### Individual Test Classes
```bash
# Pose detection performance
./gradlew :app:testDebugUnitTest --tests "com.posecoach.performance.PoseDetectionPerformanceTest"

# Live API performance
./gradlew :app:testDebugUnitTest --tests "com.posecoach.performance.LiveApiPerformanceTest"

# Coordinate mapping performance
./gradlew :app:testDebugUnitTest --tests "com.posecoach.performance.CoordinateMapperPerformanceTest"

# End-to-end performance
./gradlew :app:connectedDebugAndroidTest --tests "com.posecoach.performance.EndToEndPerformanceTest"
```

## Monitoring and Metrics

Each test includes comprehensive monitoring:

- **Memory Profiling**: Heap usage tracking and leak detection
- **CPU Monitoring**: Usage patterns and thermal throttling
- **Network Analysis**: Latency, throughput, and resilience
- **Battery Optimization**: Power consumption measurement
- **Performance Regression**: Baseline comparison capabilities

## Best Practices

1. **Realistic Test Data**: Use actual image dimensions and landmark counts
2. **Statistical Validity**: Multiple runs with variance analysis
3. **Resource Cleanup**: Proper test isolation and cleanup
4. **Error Handling**: Graceful degradation testing
5. **Documentation**: Clear test descriptions and expected outcomes

## Integration with CI/CD

The performance tests are designed to integrate with continuous integration:

- **Automated Execution**: Run on every commit
- **Performance Regression Detection**: Compare against baselines
- **Report Generation**: Detailed performance metrics
- **Alert Thresholds**: Fail builds on performance degradation

## Future Enhancements

1. **Real Device Testing**: Execute on various Android devices
2. **Performance Profiling**: Integration with Android profiling tools
3. **Automated Optimization**: AI-driven performance tuning
4. **User Experience Metrics**: Perceived performance measurements
5. **A/B Testing**: Performance comparison between implementations

This comprehensive test suite ensures the Pose Coach application maintains high performance standards while providing reliable real-time pose detection and voice coaching capabilities.