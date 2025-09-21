#!/bin/bash

# Comprehensive Testing Script for PoseCoach Android App
# This script runs all testing suites with coverage, performance, and compliance validation

set -e  # Exit on any error

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
COVERAGE_THRESHOLD=80
PERFORMANCE_THRESHOLD=80
PRIVACY_COMPLIANCE_THRESHOLD=90
MAX_MEMORY_USAGE_MB=256

# Utility functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_banner() {
    echo "=================================================================="
    echo "           PoseCoach Comprehensive Testing Suite"
    echo "=================================================================="
    echo "Coverage Requirement: ≥${COVERAGE_THRESHOLD}%"
    echo "Performance Requirement: ≥${PERFORMANCE_THRESHOLD}/100"
    echo "Privacy Compliance: ≥${PRIVACY_COMPLIANCE_THRESHOLD}%"
    echo "Memory Limit: ≤${MAX_MEMORY_USAGE_MB}MB"
    echo "=================================================================="
    echo ""
}

check_prerequisites() {
    log_info "Checking prerequisites..."

    # Check if gradlew exists
    if [ ! -f "./gradlew" ]; then
        log_error "gradlew not found. Please run this script from the project root."
        exit 1
    fi

    # Check Android SDK
    if [ -z "$ANDROID_HOME" ]; then
        log_error "ANDROID_HOME not set. Please install Android SDK."
        exit 1
    fi

    # Check Java version
    java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$java_version" -lt 17 ]; then
        log_error "Java 17 or higher required. Current version: $java_version"
        exit 1
    fi

    log_success "Prerequisites check passed"
}

run_code_quality_checks() {
    log_info "Running code quality checks..."

    echo "Running ktlint..."
    ./gradlew ktlintCheck || {
        log_error "ktlint check failed"
        return 1
    }

    echo "Running detekt..."
    ./gradlew detekt || {
        log_error "detekt check failed"
        return 1
    }

    echo "Running lint..."
    ./gradlew lint || {
        log_error "lint check failed"
        return 1
    }

    log_success "Code quality checks passed"
}

run_unit_tests() {
    log_info "Running unit tests with coverage..."

    # Clean previous test results
    ./gradlew clean

    # Run unit tests for all modules
    ./gradlew :tests:testDebugUnitTest \
              :core-pose:testDebugUnitTest \
              :core-geom:testDebugUnitTest \
              :suggestions-api:testDebugUnitTest \
              :app:testDebugUnitTest || {
        log_error "Unit tests failed"
        return 1
    }

    # Generate merged coverage report
    ./gradlew jacocoMergedReport || {
        log_error "Coverage report generation failed"
        return 1
    }

    # Verify coverage requirements
    ./gradlew jacocoCoverageVerification || {
        log_error "Coverage verification failed - below ${COVERAGE_THRESHOLD}% threshold"
        return 1
    }

    log_success "Unit tests passed with adequate coverage"
}

run_integration_tests() {
    log_info "Running integration tests..."

    # Check if emulator is running
    if ! adb devices | grep -q "emulator"; then
        log_warning "No emulator detected. Starting emulator..."
        start_emulator || {
            log_error "Failed to start emulator"
            return 1
        }
    fi

    # Run MediaPipe integration tests
    ./gradlew :tests:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.posecoach.testing.integration.MediaPipeIntegrationTests || {
        log_error "MediaPipe integration tests failed"
        return 1
    }

    # Run CameraX integration tests
    ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.posecoach.app.camera.CameraXIntegrationTest || {
        log_error "CameraX integration tests failed"
        return 1
    }

    log_success "Integration tests passed"
}

run_performance_tests() {
    log_info "Running performance tests..."

    # Run performance benchmarks
    ./gradlew :app:connectedBenchmarkAndroidTest || {
        log_error "Performance benchmarks failed"
        return 1
    }

    # Run memory leak detection
    ./gradlew :tests:memoryLeakDetection || {
        log_error "Memory leak detection failed"
        return 1
    }

    # Run performance regression tests
    ./gradlew :tests:performanceRegressionTest || {
        log_error "Performance regression tests failed"
        return 1
    }

    log_success "Performance tests passed"
}

run_device_compatibility_tests() {
    log_info "Running device compatibility tests..."

    # Test on different API levels (if available)
    api_levels=(30 31 33 34)

    for api_level in "${api_levels[@]}"; do
        log_info "Testing compatibility for API level $api_level..."

        # Create and start emulator for this API level
        create_test_emulator "$api_level" || {
            log_warning "Could not create emulator for API $api_level, skipping..."
            continue
        }

        # Run compatibility tests
        ./gradlew :tests:deviceCompatibilityTest"$api_level" || {
            log_warning "Compatibility tests failed for API $api_level"
        }

        # Clean up emulator
        cleanup_test_emulator "$api_level"
    done

    log_success "Device compatibility tests completed"
}

run_privacy_compliance_tests() {
    log_info "Running privacy compliance tests..."

    # Run privacy compliance validation
    ./gradlew :tests:privacyComplianceTest || {
        log_error "Privacy compliance tests failed"
        return 1
    }

    # Run security analysis
    ./gradlew securityCheck || {
        log_error "Security analysis failed"
        return 1
    }

    # Check for hardcoded secrets
    log_info "Scanning for secrets in code..."
    if command -v trufflehog &> /dev/null; then
        trufflehog filesystem . --fail || {
            log_error "Secret detection found issues"
            return 1
        }
    else
        log_warning "TruffleHog not found, skipping secret detection"
    fi

    log_success "Privacy compliance tests passed"
}

run_e2e_tests() {
    log_info "Running end-to-end user journey tests..."

    # Ensure emulator is in clean state
    adb shell pm clear com.posecoach.camera || true

    # Run E2E test suite
    ./gradlew :tests:e2eUserJourneyTest || {
        log_error "E2E tests failed"

        # Capture screenshot on failure
        adb shell screencap /sdcard/e2e_failure.png
        adb pull /sdcard/e2e_failure.png ./e2e_failure.png
        log_info "Screenshot saved as e2e_failure.png"

        return 1
    }

    log_success "End-to-end tests passed"
}

start_emulator() {
    log_info "Starting Android emulator..."

    # Create AVD if it doesn't exist
    avd_name="test_emulator_api34"
    if ! avdmanager list avd | grep -q "$avd_name"; then
        log_info "Creating AVD: $avd_name"
        echo "no" | avdmanager create avd \
            --force \
            --name "$avd_name" \
            --package "system-images;android-34;google_apis;x86_64" \
            --tag "google_apis" \
            --abi "x86_64"
    fi

    # Start emulator
    emulator -avd "$avd_name" -no-audio -no-window -gpu swiftshader_indirect -no-snapshot -wipe-data &

    # Wait for emulator to be ready
    log_info "Waiting for emulator to start..."
    adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed | tr -d "\r") ]]; do sleep 1; done; input keyevent 82'

    log_success "Emulator started and ready"
}

create_test_emulator() {
    local api_level=$1
    local avd_name="test_api${api_level}"

    log_info "Creating test emulator for API $api_level..."

    # Check if system image is available
    if ! sdkmanager --list | grep -q "system-images;android-${api_level};google_apis;x86_64"; then
        log_warning "System image for API $api_level not available"
        return 1
    fi

    # Create AVD
    echo "no" | avdmanager create avd \
        --force \
        --name "$avd_name" \
        --package "system-images;android-${api_level};google_apis;x86_64" || return 1

    # Start emulator
    emulator -avd "$avd_name" -no-audio -no-window -gpu swiftshader_indirect -no-snapshot &
    local emulator_pid=$!

    # Wait for boot
    adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed | tr -d "\r") ]]; do sleep 1; done'

    echo "$emulator_pid" > "/tmp/emulator_${api_level}.pid"
    return 0
}

cleanup_test_emulator() {
    local api_level=$1
    local pid_file="/tmp/emulator_${api_level}.pid"

    if [ -f "$pid_file" ]; then
        local emulator_pid=$(cat "$pid_file")
        kill "$emulator_pid" 2>/dev/null || true
        rm "$pid_file"
    fi

    adb devices | grep emulator | cut -f1 | xargs -I {} adb -s {} emu kill || true
}

generate_test_report() {
    log_info "Generating comprehensive test report..."

    local report_dir="build/reports/comprehensive"
    mkdir -p "$report_dir"

    local report_file="$report_dir/test-report.md"

    cat > "$report_file" << EOF
# PoseCoach Comprehensive Test Report

Generated: $(date)

## Test Results Summary

### Unit Tests
- **Status**: $unit_test_status
- **Coverage**: $coverage_percentage%
- **Requirement**: ≥${COVERAGE_THRESHOLD}%

### Integration Tests
- **Status**: $integration_test_status
- **MediaPipe Tests**: $mediapipe_test_status
- **CameraX Tests**: $camerax_test_status

### Performance Tests
- **Status**: $performance_test_status
- **Memory Usage**: $memory_usage MB (Max: ${MAX_MEMORY_USAGE_MB}MB)
- **Performance Score**: $performance_score/100

### Device Compatibility
- **Status**: $compatibility_test_status
- **Tested API Levels**: ${tested_api_levels[@]}

### Privacy Compliance
- **Status**: $privacy_test_status
- **Compliance Score**: $privacy_score%
- **Requirement**: ≥${PRIVACY_COMPLIANCE_THRESHOLD}%

### End-to-End Tests
- **Status**: $e2e_test_status
- **User Journey Coverage**: $e2e_coverage%

## Quality Gates
- [ ] Unit test coverage ≥ ${COVERAGE_THRESHOLD}%
- [ ] Performance score ≥ ${PERFORMANCE_THRESHOLD}/100
- [ ] Privacy compliance ≥ ${PRIVACY_COMPLIANCE_THRESHOLD}%
- [ ] Memory usage ≤ ${MAX_MEMORY_USAGE_MB}MB
- [ ] All critical tests passing

## Recommendations
$test_recommendations

EOF

    log_success "Test report generated: $report_file"
}

cleanup() {
    log_info "Cleaning up test environment..."

    # Kill any running emulators
    adb devices | grep emulator | cut -f1 | xargs -I {} adb -s {} emu kill || true

    # Clean up temporary files
    rm -f /tmp/emulator_*.pid

    log_success "Cleanup completed"
}

# Main execution
main() {
    print_banner

    # Set up cleanup trap
    trap cleanup EXIT

    # Initialize test status variables
    unit_test_status="PENDING"
    integration_test_status="PENDING"
    performance_test_status="PENDING"
    compatibility_test_status="PENDING"
    privacy_test_status="PENDING"
    e2e_test_status="PENDING"

    # Track overall success
    overall_success=true

    # Run test suite
    check_prerequisites || exit 1

    if run_code_quality_checks; then
        log_success "Code quality checks passed"
    else
        log_error "Code quality checks failed"
        overall_success=false
    fi

    if run_unit_tests; then
        unit_test_status="PASSED"
        log_success "Unit tests completed"
    else
        unit_test_status="FAILED"
        overall_success=false
    fi

    if run_integration_tests; then
        integration_test_status="PASSED"
        log_success "Integration tests completed"
    else
        integration_test_status="FAILED"
        overall_success=false
    fi

    if run_performance_tests; then
        performance_test_status="PASSED"
        log_success "Performance tests completed"
    else
        performance_test_status="FAILED"
        overall_success=false
    fi

    if run_device_compatibility_tests; then
        compatibility_test_status="PASSED"
        log_success "Device compatibility tests completed"
    else
        compatibility_test_status="FAILED"
        overall_success=false
    fi

    if run_privacy_compliance_tests; then
        privacy_test_status="PASSED"
        log_success "Privacy compliance tests completed"
    else
        privacy_test_status="FAILED"
        overall_success=false
    fi

    if run_e2e_tests; then
        e2e_test_status="PASSED"
        log_success "End-to-end tests completed"
    else
        e2e_test_status="FAILED"
        overall_success=false
    fi

    # Generate final report
    generate_test_report

    # Final result
    echo ""
    echo "=================================================================="
    if [ "$overall_success" = true ]; then
        log_success "ALL TESTS PASSED - Ready for deployment"
        echo "✅ Unit Tests: $unit_test_status"
        echo "✅ Integration Tests: $integration_test_status"
        echo "✅ Performance Tests: $performance_test_status"
        echo "✅ Compatibility Tests: $compatibility_test_status"
        echo "✅ Privacy Compliance: $privacy_test_status"
        echo "✅ E2E Tests: $e2e_test_status"
        exit 0
    else
        log_error "SOME TESTS FAILED - Cannot deploy"
        echo "❌ Unit Tests: $unit_test_status"
        echo "❌ Integration Tests: $integration_test_status"
        echo "❌ Performance Tests: $performance_test_status"
        echo "❌ Compatibility Tests: $compatibility_test_status"
        echo "❌ Privacy Compliance: $privacy_test_status"
        echo "❌ E2E Tests: $e2e_test_status"
        exit 1
    fi
}

# Run the main function
main "$@"