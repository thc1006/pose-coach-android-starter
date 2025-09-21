# CI/CD Pipeline Setup Guide

This guide provides comprehensive instructions for setting up and configuring the CI/CD pipeline for the Pose Coach Android application.

## 📋 Overview

The CI/CD pipeline is designed to meet the Definition of Done requirements:
- Unit tests pass with >80% coverage
- CI green: assemble, lint, test
- README/CHANGELOG updates with metrics
- Privacy checklist validation

## 🚀 Pipeline Components

### 1. Main CI/CD Pipeline (`ci-cd-pipeline.yml`)

**Triggers:**
- Push to main, develop, feature/*, hotfix/* branches
- Pull requests to main, develop
- Release published
- Manual workflow dispatch

**Jobs:**
1. **Build Validation** - Setup and cache validation
2. **Code Quality** - Lint analysis and static code analysis
3. **Unit Testing** - Tests with coverage enforcement (>80%)
4. **Privacy & Security** - Automated privacy checklist and security scans
5. **Build APKs** - Multi-variant builds (debug/release)
6. **Integration Testing** - Device farm testing on multiple API levels
7. **Performance Benchmarking** - Performance validation
8. **Deployment** - Staged rollout to Google Play Store
9. **Post-Deployment** - Validation and monitoring
10. **Report Generation** - Consolidated metrics and reports

### 2. Security & Privacy Pipeline (`security-scan.yml`)

**Triggers:**
- Weekly schedule (Monday 2 AM)
- Manual dispatch
- Push to main with source changes

**Features:**
- Privacy checklist automation
- SAST security scanning with CodeQL
- OWASP dependency vulnerability checks
- Android-specific security analysis
- Mobile Security Framework (MobSF) integration

### 3. Performance Monitoring (`performance-monitoring.yml`)

**Triggers:**
- Schedule: Mon, Wed, Fri at 4 AM
- Manual dispatch with benchmark type selection
- Push to main with source changes

**Benchmarks:**
- Startup time measurement
- UI rendering performance
- Pose detection algorithm performance
- Memory usage analysis
- Regression detection against baseline

### 4. Release Management (`release-management.yml`)

**Triggers:**
- GitHub releases (published/edited)
- Manual dispatch with deployment options

**Features:**
- Pre-release validation
- Multi-ABI release builds
- Google Play Store deployment
- Staged rollout management
- Emergency rollback capabilities

## 🔧 Setup Instructions

### Prerequisites

1. **GitHub Repository Secrets**
   ```
   GOOGLE_PLAY_SERVICE_ACCOUNT_JSON    # Play Store API access
   RELEASE_KEYSTORE_BASE64             # Release signing keystore
   RELEASE_KEYSTORE_PASSWORD           # Keystore password
   RELEASE_KEY_ALIAS                   # Key alias
   RELEASE_KEY_PASSWORD                # Key password
   SLACK_WEBHOOK_URL                   # Optional: Slack notifications
   ```

2. **Google Play Console Setup**
   - Create service account with Play Developer API access
   - Download service account JSON key
   - Encode as base64 and store in secrets

3. **Android Keystore**
   - Generate release keystore
   - Encode as base64 and store in secrets

### Environment Setup

1. **Enable GitHub Actions**
   ```bash
   # Repository Settings > Actions > General
   # Allow all actions and reusable workflows
   ```

2. **Branch Protection Rules**
   ```bash
   # Settings > Branches > Add rule for main branch
   # Require status checks to pass before merging
   # Require branches to be up to date before merging
   # Include administrators
   ```

3. **Environment Protection**
   ```bash
   # Settings > Environments
   # Create environments: play-store-internal, play-store-alpha, play-store-beta, play-store-production
   # Add required reviewers for production environment
   ```

### Gradle Configuration

Add to `app/build.gradle`:

```kotlin
android {
    buildTypes {
        release {
            signingConfig signingConfigs.release
            // Other release configuration
        }
    }

    signingConfigs {
        release {
            storeFile file('../release-keystore.jks')
            storePassword System.getenv('KEYSTORE_PASSWORD')
            keyAlias System.getenv('KEY_ALIAS')
            keyPassword System.getenv('KEY_PASSWORD')
        }
    }
}

// Code coverage
apply plugin: 'jacoco'

jacoco {
    toolVersion = "0.8.8"
}

tasks.register('jacocoTestReport', JacocoReport) {
    dependsOn 'testDebugUnitTest'

    reports {
        xml.required = true
        html.required = true
    }

    def fileFilter = [
        '**/R.class',
        '**/R$*.class',
        '**/BuildConfig.*',
        '**/Manifest*.*',
        '**/*Test*.*',
        'android/**/*.*'
    ]

    def debugTree = fileTree(dir: "${buildDir}/intermediates/javac/debug", excludes: fileFilter)
    def kotlinDebugTree = fileTree(dir: "${buildDir}/tmp/kotlin-classes/debug", excludes: fileFilter)

    classDirectories.setFrom(files([debugTree, kotlinDebugTree]))
    executionData.setFrom(fileTree(dir: buildDir, includes: ['jacoco/testDebugUnitTest.exec']))
    sourceDirectories.setFrom(files(['src/main/java', 'src/main/kotlin']))
}

tasks.register('jacocoTestCoverageVerification', JacocoCoverageVerification) {
    dependsOn 'jacocoTestReport'

    violationRules {
        rule {
            limit {
                minimum = 0.80 // 80% coverage requirement
            }
        }
    }
}
```

## 🔍 Quality Gates

### Code Coverage
- **Threshold**: 80% minimum
- **Tool**: JaCoCo
- **Enforcement**: Pipeline fails if below threshold
- **Reporting**: HTML and XML reports generated

### Static Analysis
- **Lint**: Android lint with custom rules
- **Detekt**: Kotlin static analysis
- **CodeQL**: SAST security scanning

### Testing Strategy
- **Unit Tests**: JUnit + Mockito
- **Integration Tests**: Instrumented tests on emulators
- **Performance Tests**: Macrobenchmark library
- **Security Tests**: Automated vulnerability scanning

### Privacy Validation
- **Automated Checklist**: Permission and consent validation
- **Data Flow Analysis**: Tracking data collection practices
- **Compliance**: GDPR and CCPA readiness checks

## 📊 Monitoring & Reporting

### Build Metrics
- Build success/failure rates
- Build duration trends
- Artifact sizes
- Test execution times

### Quality Metrics
- Code coverage percentages
- Lint issue counts
- Security vulnerability counts
- Performance regression detection

### Deployment Metrics
- Deployment success rates
- Rollback frequency
- Time to production
- Staged rollout progression

## 🚨 Alerting & Notifications

### Critical Alerts
- Production deployment failures
- Security vulnerabilities (CVSS 7+)
- Coverage drops below 70%
- Performance regressions >20%

### Warning Alerts
- Coverage drops below 80%
- Lint issues increase >50%
- Build duration increases >100%
- Test flakiness detected

### Info Notifications
- Successful deployments
- Weekly security scan results
- Performance benchmark results
- Dependency updates available

## 🔄 Workflow Optimization

### Caching Strategy
- Gradle dependencies cached
- Android SDK components cached
- Build cache enabled
- Test results cached

### Parallel Execution
- Matrix builds for multiple ABIs
- Parallel test execution
- Concurrent quality checks
- Independent artifact generation

### Resource Management
- Timeouts configured for all jobs
- Resource-intensive jobs on powerful runners
- Cleanup of temporary artifacts
- Retention policies for artifacts

## 🛠️ Maintenance

### Regular Tasks
- Update dependencies monthly
- Review and update security configurations
- Monitor pipeline performance metrics
- Update documentation

### Quarterly Reviews
- Analyze pipeline efficiency
- Review quality gate thresholds
- Update performance baselines
- Security posture assessment

## 📚 Additional Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Android CI/CD Best Practices](https://developer.android.com/studio/build/building-cmdline)
- [Google Play Developer API](https://developers.google.com/android-publisher)
- [Fastlane for Android](https://docs.fastlane.tools/getting-started/android/setup/)

## 🆘 Troubleshooting

### Common Issues

1. **Keystore Issues**
   - Verify base64 encoding is correct
   - Check keystore password and alias
   - Ensure keystore file is accessible

2. **Play Store Deployment**
   - Verify service account permissions
   - Check package name matches
   - Ensure version code is incremented

3. **Test Failures**
   - Check emulator configuration
   - Verify test dependencies
   - Review test environment setup

4. **Coverage Issues**
   - Exclude generated code from coverage
   - Verify test execution
   - Check JaCoCo configuration

### Support Channels
- GitHub Issues for pipeline problems
- Team Slack for urgent assistance
- Documentation updates via pull requests

---

*This setup guide ensures a robust, secure, and efficient CI/CD pipeline that meets all Definition of Done requirements while providing comprehensive monitoring and quality assurance.*