# Testing Setup Guide - Pose Coach Android

This document provides comprehensive guidance for setting up and running tests in the Pose Coach Android project, especially in environments where only JRE (not JDK) is available.

## 🎯 Testing Philosophy

The project follows **Test-Driven Development (TDD)** principles:
1. **Red**: Write failing tests first
2. **Green**: Implement minimal code to pass
3. **Refactor**: Improve code while keeping tests green

## 🔧 Environment Requirements

### Minimal Setup (JRE Only)
- **Java Runtime Environment (JRE) 8+**
- **Bash shell** (Git Bash on Windows)
- **Android Studio** (for final compilation and device testing)

### Full Development Setup (Recommended)
- **Java Development Kit (JDK) 17+**
- **Android Studio Arctic Fox+**
- **Gradle 8.9+**
- **Android SDK 34+**

## 🚀 Quick Start Testing

### 1. Validation Scripts

We provide two main validation scripts that work without JDK:

```bash
# Validate implementation completeness
bash .claude/scripts/validate-implementation.sh

# Run TDD-style validation tests
bash .claude/scripts/run-tdd-tests.sh
```

### 2. Targeted Testing

Run specific test suites:

```bash
# Test core modules only
bash .claude/scripts/run-tdd-tests.sh core

# Test API integration
bash .claude/scripts/run-tdd-tests.sh api

# Test camera functionality
bash .claude/scripts/run-tdd-tests.sh camera

# Test live coach features
bash .claude/scripts/run-tdd-tests.sh live

# Test privacy compliance
bash .claude/scripts/run-tdd-tests.sh privacy
```

## 📋 Test Categories

### 1. Static Analysis Tests
- **File Structure Validation**: Ensures all required files exist
- **Kotlin Syntax Checking**: Basic syntax validation without compilation
- **Dependency Verification**: Checks gradle dependencies
- **Configuration Validation**: Validates manifests and configs

### 2. Implementation Tests
- **Class Structure**: Verifies proper class/object definitions
- **Interface Compliance**: Checks interface implementations
- **Package Organization**: Validates package structure
- **Import Analysis**: Reviews import statements

### 3. Integration Tests
- **Module Interaction**: Tests module boundaries
- **API Configuration**: Validates API key setup
- **Permission Checks**: Ensures proper Android permissions
- **Privacy Compliance**: Validates privacy requirements

### 4. Mock Tests
- **Unit Test Simulation**: Simulates unit test scenarios
- **Edge Case Coverage**: Tests boundary conditions
- **Error Handling**: Validates error scenarios
- **Performance Checks**: Basic performance validation

## 🏗️ Project Test Structure

```
pose-coach-android-starter/
├── app/src/test/kotlin/           # Unit tests
├── app/src/androidTest/kotlin/    # Android instrumentation tests
├── core-pose/src/test/kotlin/     # Core pose module tests
├── core-geom/src/test/kotlin/     # Geometry module tests
├── suggestions-api/src/test/kotlin/ # API module tests
├── .claude/scripts/               # Validation scripts
└── docs/testing-setup.md          # This document
```

## 📝 Writing Tests

### Unit Test Template

```kotlin
// File: app/src/test/kotlin/com/posecoach/app/pose/PoseDetectionManagerTest.kt
class PoseDetectionManagerTest {

    @Test
    fun `should initialize pose detector correctly`() {
        // Arrange
        val mockContext = mockk<Context>()
        val manager = PoseDetectionManager(mockContext)

        // Act
        val result = manager.initialize()

        // Assert
        assertTrue(result)
        verify { mockContext.getSystemService(any()) }
    }

    @Test
    fun `should handle pose detection errors gracefully`() {
        // Arrange
        val manager = PoseDetectionManager(mockContext)
        val invalidImage = mockk<ImageProxy>()

        // Act & Assert
        assertDoesNotThrow {
            manager.detectPose(invalidImage)
        }
    }
}
```

### Android Test Template

```kotlin
// File: app/src/androidTest/kotlin/com/posecoach/app/CameraIntegrationTest.kt
@RunWith(AndroidJUnit4::class)
class CameraIntegrationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun cameraPreviewStartsCorrectly() {
        onView(withId(R.id.camera_preview))
            .check(matches(isDisplayed()))
    }

    @Test
    fun poseOverlayRendersCorrectly() {
        // Wait for camera to initialize
        Thread.sleep(2000)

        onView(withId(R.id.pose_overlay))
            .check(matches(isDisplayed()))
    }
}
```

## 🔍 Testing Checklist

### Before Implementing Features
- [ ] Write failing test cases
- [ ] Define expected behavior
- [ ] Set up mock dependencies
- [ ] Validate test isolation

### During Implementation
- [ ] Run validation scripts frequently
- [ ] Ensure tests pass incrementally
- [ ] Maintain test coverage >80%
- [ ] Document test scenarios

### After Implementation
- [ ] Run full test suite
- [ ] Validate edge cases
- [ ] Check privacy compliance
- [ ] Update documentation

## 🛠️ Alternative Testing Approaches

### 1. JRE-Only Environment

When only JRE is available:

```bash
# Use our validation scripts
bash .claude/scripts/validate-implementation.sh
bash .claude/scripts/run-tdd-tests.sh

# Static analysis with grep/awk
grep -r "class.*{" app/src/main/kotlin/
find . -name "*.kt" -exec wc -l {} \;
```

### 2. Android Studio Testing

For full testing capabilities:

```bash
# Unit tests
./gradlew test

# Android instrumentation tests
./gradlew connectedAndroidTest

# Lint checking
./gradlew lint

# Coverage report
./gradlew jacocoTestReport
```

### 3. CI/CD Testing

For automated testing:

```yaml
# .github/workflows/test.yml
- name: Run Unit Tests
  run: ./gradlew test

- name: Run Validation Scripts
  run: |
    bash .claude/scripts/validate-implementation.sh
    bash .claude/scripts/run-tdd-tests.sh
```

## 📊 Test Coverage Goals

### Minimum Requirements
- **Statement Coverage**: >80%
- **Branch Coverage**: >75%
- **Function Coverage**: >80%
- **Line Coverage**: >80%

### Module-Specific Targets

| Module | Coverage Target | Critical Areas |
|--------|----------------|----------------|
| core-pose | 90% | Landmark detection, biomechanics |
| core-geom | 95% | Vector operations, angle calculations |
| suggestions-api | 85% | API calls, schema validation |
| app | 75% | UI components, integration |

## 🔐 Privacy Testing

### Required Privacy Tests
- [ ] No image upload without consent
- [ ] Landmark data encryption
- [ ] API key security
- [ ] User consent workflow
- [ ] Data retention policies

### Privacy Test Commands

```bash
# Check privacy compliance
bash .claude/scripts/run-tdd-tests.sh privacy

# Validate no hardcoded secrets
grep -r "api.*key" --exclude-dir=.git .
grep -r "password" --exclude-dir=.git .
```

## 🚨 Common Issues & Solutions

### Issue: "Permission Denied" on Scripts
```bash
# Solution: Make scripts executable
chmod +x .claude/scripts/*.sh
```

### Issue: "JDK Not Found"
```bash
# Solution: Use validation scripts instead
bash .claude/scripts/validate-implementation.sh
```

### Issue: "Gradle Daemon Issues"
```bash
# Solution: Stop and restart daemon
./gradlew --stop
./gradlew clean
```

### Issue: "MediaPipe Not Found"
```bash
# Solution: Check dependencies in build.gradle.kts
grep -n "mediapipe" app/build.gradle.kts
```

## 📈 Performance Testing

### Benchmarking Commands

```bash
# Test pose detection performance
adb shell am start -n com.posecoach.app/.MainActivity
adb shell dumpsys gfxinfo com.posecoach.app

# Memory usage monitoring
adb shell dumpsys meminfo com.posecoach.app
```

### Performance Targets
- **Pose Detection**: <50ms per frame
- **Overlay Rendering**: 60fps
- **Memory Usage**: <100MB
- **Battery Impact**: <5% per hour

## 🔧 Debugging Tests

### Debug Commands

```bash
# Verbose test output
bash -x .claude/scripts/run-tdd-tests.sh

# Check specific file
bash .claude/scripts/validate-implementation.sh | grep "MainActivity"

# Kotlin syntax check
find . -name "*.kt" -exec echo "Checking: {}" \; -exec head -5 {} \;
```

### Log Analysis

```bash
# Check Android logs
adb logcat | grep PoseCoach

# Filter MediaPipe logs
adb logcat | grep MediaPipe

# Monitor performance
adb logcat | grep -E "(fps|memory|battery)"
```

## 📚 Testing Resources

### Documentation Links
- [Android Testing Guide](https://developer.android.com/training/testing)
- [Kotlin Test Documentation](https://kotlinlang.org/docs/jvm-test-using-junit.html)
- [MediaPipe Testing](https://developers.google.com/mediapipe/framework/framework_concepts/testing)
- [CameraX Testing](https://developer.android.com/training/camerax/test)

### Testing Tools
- **JUnit 5**: Unit testing framework
- **MockK**: Kotlin mocking library
- **Espresso**: Android UI testing
- **Robolectric**: Android unit tests without devices

## 🎯 Next Steps

1. **Run Initial Validation**
   ```bash
   bash .claude/scripts/validate-implementation.sh
   ```

2. **Execute TDD Tests**
   ```bash
   bash .claude/scripts/run-tdd-tests.sh
   ```

3. **Set Up Android Studio**
   - Import project
   - Sync Gradle
   - Run unit tests

4. **Device Testing**
   - Connect Android device
   - Enable developer options
   - Run instrumentation tests

This testing setup ensures comprehensive validation of the Pose Coach Android project across different environments and development stages.