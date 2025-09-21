# Test Automation Configuration & CI/CD Pipeline

## Gradle Test Configuration

### Module-Level Test Setup

```kotlin
// tests/build.gradle.kts
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("jacoco")
    id("androidx.benchmark")
}

android {
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Performance testing configuration
        testInstrumentationRunnerArguments["androidx.benchmark.enabledRules"] = "Macrobenchmark"
        testInstrumentationRunnerArguments["androidx.benchmark.output.enable"] = "true"
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true

            all {
                // Parallel test execution
                maxParallelForks = Runtime.getRuntime().availableProcessors().div(2)
                forkEvery = 100

                // Memory settings
                minHeapSize = "512m"
                maxHeapSize = "2048m"

                // Timeout settings
                systemProperty("junit.jupiter.execution.timeout.default", "30s")
                systemProperty("junit.jupiter.execution.timeout.test", "60s")
            }
        }

        animationsDisabled = true

        // Device test configuration
        devices {
            create("pixel6") {
                dimension = "api"
                abiFilters += listOf("x86_64")
            }
        }
    }

    // Code coverage configuration
    buildTypes {
        debug {
            isTestCoverageEnabled = true
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }
}

dependencies {
    // Core testing framework
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Mocking
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("io.mockk:mockk:1.13.8")

    // Property-based testing
    testImplementation("io.kotest:kotest-property:5.7.2")

    // Android testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")

    // Performance testing
    androidTestImplementation("androidx.benchmark:benchmark-junit4:1.2.2")
    androidTestImplementation("androidx.tracing:tracing-perfetto:1.0.0")

    // UI testing
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.4")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.2.0")

    // Network testing
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.github.tomakehurst:wiremock:3.0.1")
}

// Jacoco coverage configuration
jacoco {
    toolVersion = "0.8.10"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/AutoValue_*.*",
        "**/*_GeneratedInjector.class",
        "**/*Module_*Factory.class"
    )

    val debugTree = fileTree("${buildDir}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }

    val mainSrc = "${project.projectDir}/src/main/kotlin"

    sourceDirectories.setFrom(files([mainSrc]))
    classDirectories.setFrom(files([debugTree]))
    executionData.setFrom(fileTree(buildDir) {
        include("jacoco/testDebugUnitTest.exec")
    })
}

// Custom test tasks
tasks.register("runUnitTests") {
    group = "verification"
    description = "Run all unit tests with coverage"
    dependsOn("testDebugUnitTest", "jacocoTestReport")
}

tasks.register("runIntegrationTests") {
    group = "verification"
    description = "Run integration tests"
    dependsOn("connectedDebugAndroidTest")
}

tasks.register("runPerformanceTests") {
    group = "verification"
    description = "Run performance benchmark tests"
    dependsOn("connectedBenchmarkAndroidTest")
}

tasks.register("runAllTests") {
    group = "verification"
    description = "Run complete test suite"
    dependsOn("runUnitTests", "runIntegrationTests", "runPerformanceTests")
}
```

## GitHub Actions CI/CD Pipeline

### Main Workflow Configuration

```yaml
# .github/workflows/comprehensive-testing.yml
name: Comprehensive Testing Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]
  schedule:
    # Run performance tests nightly at 2 AM UTC
    - cron: '0 2 * * *'

env:
  GRADLE_OPTS: -Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2
  JAVA_VERSION: 17

jobs:
  unit-tests:
    name: Unit Tests & Coverage
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - name: Checkout Code
        uses: actions/checkout@v4
        with:
          fetch-depth: 0 # Needed for SonarCloud

      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/gradle-build-action@v2
        with:
          cache-read-only: false

      - name: Run Unit Tests
        run: |
          ./gradlew runUnitTests \
            --parallel \
            --build-cache \
            --configuration-cache

      - name: Generate Coverage Reports
        run: |
          ./gradlew jacocoTestReport \
            jacocoTestCoverageVerification

      - name: Verify Coverage Thresholds
        run: |
          ./gradlew jacocoTestCoverageVerification

      - name: Upload Coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          token: ${{ secrets.CODECOV_TOKEN }}
          file: ./build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml
          fail_ci_if_error: true

      - name: SonarCloud Scan
        uses: SonarSource/sonarcloud-github-action@master
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}

      - name: Upload Test Results
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: unit-test-results
          path: |
            **/build/reports/tests/
            **/build/reports/jacoco/
          retention-days: 30

  integration-tests:
    name: Integration Tests
    runs-on: macos-latest
    timeout-minutes: 45
    strategy:
      matrix:
        api-level: [24, 28, 33]
        target: [default, google_apis]

    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/gradle-build-action@v2

      - name: AVD Cache
        uses: actions/cache@v3
        id: avd-cache
        with:
          path: |
            ~/.android/avd/*
            ~/.android/adb*
          key: avd-${{ matrix.api-level }}-${{ matrix.target }}

      - name: Create AVD and Generate Snapshot
        if: steps.avd-cache.outputs.cache-hit != 'true'
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api-level }}
          target: ${{ matrix.target }}
          arch: x86_64
          profile: Nexus 6
          force-avd-creation: false
          emulator-options: -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none
          disable-animations: true
          script: echo "Generated AVD snapshot for caching."

      - name: Run Integration Tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api-level }}
          target: ${{ matrix.target }}
          arch: x86_64
          profile: Nexus 6
          force-avd-creation: false
          emulator-options: -no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none
          disable-animations: true
          script: |
            adb shell settings put global window_animation_scale 0.0
            adb shell settings put global transition_animation_scale 0.0
            adb shell settings put global animator_duration_scale 0.0
            ./gradlew runIntegrationTests \
              --stacktrace \
              -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest

      - name: Upload Integration Test Results
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: integration-test-results-${{ matrix.api-level }}-${{ matrix.target }}
          path: |
            **/build/reports/androidTests/
            **/build/outputs/androidTest-results/
          retention-days: 30

  performance-tests:
    name: Performance Tests
    runs-on: macos-latest
    timeout-minutes: 60
    if: github.event_name == 'schedule' || contains(github.event.pull_request.labels.*.name, 'performance')

    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/gradle-build-action@v2

      - name: Run Performance Tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 33
          target: google_apis
          arch: x86_64
          profile: Nexus 6
          force-avd-creation: false
          emulator-options: -no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim
          disable-animations: true
          script: |
            ./gradlew runPerformanceTests \
              -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark \
              -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.output.enable=true

      - name: Parse Performance Results
        run: |
          python3 scripts/parse_benchmark_results.py \
            --input build/outputs/connected_android_test_additional_output/ \
            --output performance-report.json

      - name: Upload Performance Report
        uses: actions/upload-artifact@v3
        with:
          name: performance-report
          path: performance-report.json
          retention-days: 90

      - name: Performance Regression Check
        run: |
          python3 scripts/performance_regression_check.py \
            --current performance-report.json \
            --baseline performance-baseline.json \
            --threshold 0.15

  privacy-compliance:
    name: Privacy & Security Tests
    runs-on: ubuntu-latest
    timeout-minutes: 20

    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/gradle-build-action@v2

      - name: Run Privacy Tests
        run: |
          ./gradlew test \
            --tests "*PrivacyTest" \
            --tests "*SecurityTest" \
            --tests "*ComplianceTest"

      - name: Static Security Analysis
        run: |
          ./gradlew detekt
          ./gradlew ktlintCheck

      - name: Dependency Vulnerability Scan
        run: |
          ./gradlew dependencyCheckAnalyze

      - name: API Key Security Check
        run: |
          python3 scripts/check_api_key_security.py \
            --source-dir src/ \
            --exclude-dirs test/

      - name: Upload Security Reports
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: security-reports
          path: |
            build/reports/detekt/
            build/reports/ktlint/
            build/reports/dependency-check/
          retention-days: 30

  ui-tests:
    name: UI & UX Tests
    runs-on: macos-latest
    timeout-minutes: 40

    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/gradle-build-action@v2

      - name: Run UI Tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 33
          target: google_apis
          arch: x86_64
          profile: Nexus 6
          force-avd-creation: false
          emulator-options: -no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim
          disable-animations: true
          script: |
            ./gradlew connectedDebugAndroidTest \
              -Pandroid.testInstrumentationRunnerArguments.annotation=androidx.test.filters.LargeTest

      - name: Screenshot Tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 33
          target: google_apis
          arch: x86_64
          profile: Nexus 6
          script: |
            ./gradlew executeScreenshotTests

      - name: Upload UI Test Results
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: ui-test-results
          path: |
            **/build/reports/androidTests/
            **/screenshots/
          retention-days: 30

  quality-gates:
    name: Quality Gates
    runs-on: ubuntu-latest
    needs: [unit-tests, integration-tests, privacy-compliance]
    if: always()

    steps:
      - name: Download All Artifacts
        uses: actions/download-artifact@v3

      - name: Evaluate Quality Gates
        run: |
          python3 scripts/evaluate_quality_gates.py \
            --coverage-threshold 80 \
            --performance-threshold 30 \
            --privacy-compliance required

      - name: Generate Quality Report
        run: |
          python3 scripts/generate_quality_report.py \
            --output quality-report.html

      - name: Upload Quality Report
        uses: actions/upload-artifact@v3
        with:
          name: quality-report
          path: quality-report.html
          retention-days: 90

  release-readiness:
    name: Release Readiness Check
    runs-on: ubuntu-latest
    needs: [quality-gates, performance-tests, ui-tests]
    if: github.ref == 'refs/heads/main' && success()

    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Comprehensive Readiness Check
        run: |
          python3 scripts/release_readiness_check.py \
            --check-coverage \
            --check-performance \
            --check-privacy \
            --check-ui

      - name: Create Release Notes
        if: success()
        run: |
          python3 scripts/generate_release_notes.py \
            --version ${{ github.sha }} \
            --output release-notes.md

      - name: Tag Release Candidate
        if: success()
        run: |
          git tag -a "rc-${{ github.sha }}" -m "Release candidate ${{ github.sha }}"
          git push origin "rc-${{ github.sha }}"
```

## Quality Gate Scripts

### Coverage Verification Script

```python
# scripts/evaluate_quality_gates.py
#!/usr/bin/env python3

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

class QualityGateEvaluator:
    def __init__(self, coverage_threshold, performance_threshold, privacy_required):
        self.coverage_threshold = coverage_threshold
        self.performance_threshold = performance_threshold
        self.privacy_required = privacy_required
        self.failures = []

    def evaluate_coverage(self, coverage_file):
        """Evaluate code coverage against threshold."""
        try:
            tree = ET.parse(coverage_file)
            root = tree.getroot()

            # Extract overall coverage percentage
            counter = root.find(".//counter[@type='INSTRUCTION']")
            if counter is not None:
                covered = int(counter.get('covered', 0))
                missed = int(counter.get('missed', 0))
                total = covered + missed

                if total > 0:
                    coverage_percent = (covered / total) * 100
                    print(f"Code Coverage: {coverage_percent:.2f}%")

                    if coverage_percent < self.coverage_threshold:
                        self.failures.append(
                            f"Code coverage {coverage_percent:.2f}% below threshold {self.coverage_threshold}%"
                        )
                    return True

        except Exception as e:
            self.failures.append(f"Failed to parse coverage report: {e}")
            return False

        self.failures.append("Coverage data not found")
        return False

    def evaluate_performance(self, performance_file):
        """Evaluate performance metrics against thresholds."""
        try:
            with open(performance_file, 'r') as f:
                data = json.load(f)

            inference_times = data.get('inference_times', [])
            if inference_times:
                avg_inference = sum(inference_times) / len(inference_times)
                p95_inference = sorted(inference_times)[int(len(inference_times) * 0.95)]

                print(f"Average Inference Time: {avg_inference:.2f}ms")
                print(f"P95 Inference Time: {p95_inference:.2f}ms")

                if p95_inference > self.performance_threshold:
                    self.failures.append(
                        f"P95 inference time {p95_inference:.2f}ms exceeds threshold {self.performance_threshold}ms"
                    )
                    return False

            return True

        except Exception as e:
            self.failures.append(f"Failed to parse performance report: {e}")
            return False

    def evaluate_privacy_compliance(self, test_results_dir):
        """Evaluate privacy compliance test results."""
        try:
            privacy_test_files = list(Path(test_results_dir).glob("**/TEST-*Privacy*Test.xml"))

            if not privacy_test_files and self.privacy_required:
                self.failures.append("Privacy compliance tests not found")
                return False

            total_tests = 0
            failed_tests = 0

            for test_file in privacy_test_files:
                tree = ET.parse(test_file)
                root = tree.getroot()

                tests = int(root.get('tests', 0))
                failures = int(root.get('failures', 0))
                errors = int(root.get('errors', 0))

                total_tests += tests
                failed_tests += failures + errors

            if failed_tests > 0:
                self.failures.append(f"Privacy compliance tests failed: {failed_tests}/{total_tests}")
                return False

            print(f"Privacy Compliance: {total_tests} tests passed")
            return True

        except Exception as e:
            self.failures.append(f"Failed to evaluate privacy compliance: {e}")
            return False

    def run_evaluation(self):
        """Run all quality gate evaluations."""
        print("=== Quality Gate Evaluation ===")

        # Find coverage report
        coverage_files = list(Path(".").glob("**/jacocoTestReport.xml"))
        if coverage_files:
            self.evaluate_coverage(coverage_files[0])
        else:
            self.failures.append("Coverage report not found")

        # Find performance report
        performance_files = list(Path(".").glob("**/performance-report.json"))
        if performance_files:
            self.evaluate_performance(performance_files[0])

        # Find test results
        test_result_dirs = list(Path(".").glob("**/test-results"))
        if test_result_dirs:
            self.evaluate_privacy_compliance(test_result_dirs[0])

        # Report results
        if self.failures:
            print("\n❌ Quality Gate Failures:")
            for failure in self.failures:
                print(f"  - {failure}")
            return False
        else:
            print("\n✅ All Quality Gates Passed!")
            return True

def main():
    parser = argparse.ArgumentParser(description="Evaluate quality gates")
    parser.add_argument("--coverage-threshold", type=float, default=80.0,
                      help="Minimum code coverage percentage")
    parser.add_argument("--performance-threshold", type=float, default=30.0,
                      help="Maximum inference time in milliseconds")
    parser.add_argument("--privacy-compliance", choices=["required", "optional"],
                      default="required", help="Privacy compliance requirement")

    args = parser.parse_args()

    evaluator = QualityGateEvaluator(
        coverage_threshold=args.coverage_threshold,
        performance_threshold=args.performance_threshold,
        privacy_required=(args.privacy_compliance == "required")
    )

    success = evaluator.run_evaluation()
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()
```

## Local Development Setup

### Pre-commit Hooks Configuration

```yaml
# .pre-commit-config.yaml
repos:
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v4.4.0
    hooks:
      - id: trailing-whitespace
      - id: end-of-file-fixer
      - id: check-yaml
      - id: check-json
      - id: check-merge-conflict
      - id: check-case-conflict

  - repo: https://github.com/macisamuele/language-formatters-pre-commit-hooks
    rev: v2.10.0
    hooks:
      - id: pretty-format-kotlin
        args: [--autofix]

  - repo: local
    hooks:
      - id: ktlint
        name: ktlint
        entry: ./gradlew ktlintCheck
        language: system
        files: \.kt$

      - id: detekt
        name: detekt
        entry: ./gradlew detekt
        language: system
        files: \.kt$

      - id: unit-tests
        name: unit-tests
        entry: ./gradlew testDebugUnitTest
        language: system
        pass_filenames: false
        stages: [commit]

      - id: api-key-check
        name: api-key-security-check
        entry: python3 scripts/check_api_key_security.py
        language: system
        args: [--source-dir, src/, --exclude-dirs, test/]
```

### IDE Configuration

```kotlin
// .idea/runConfigurations/All_Tests.xml
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="All Tests" type="GradleRunConfiguration" factoryName="Gradle">
    <ExternalSystemSettings>
      <option name="executionName" />
      <option name="externalProjectPath" value="$PROJECT_DIR$" />
      <option name="externalSystemIdString" value="GRADLE" />
      <option name="scriptParameters" value="--parallel --build-cache --configuration-cache" />
      <option name="taskDescriptions">
        <list />
      </option>
      <option name="taskNames">
        <list>
          <option value="runAllTests" />
        </list>
      </option>
      <option name="vmOptions" value="-Xmx4g" />
    </ExternalSystemSettings>
    <GradleScriptDebugEnabled>true</GradleScriptDebugEnabled>
    <method v="2" />
  </configuration>
</component>
```

This automation configuration provides:

1. **Comprehensive CI/CD pipeline** with parallel execution
2. **Quality gates** with configurable thresholds
3. **Performance regression detection**
4. **Privacy compliance validation**
5. **Automated security scanning**
6. **Pre-commit hooks** for code quality
7. **IDE integration** for developer productivity
8. **Artifact management** for test results and reports

The configuration ensures >80% coverage requirements while maintaining fast feedback loops and comprehensive quality assurance.