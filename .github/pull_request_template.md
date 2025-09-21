## Pull Request Summary

### 📋 Changes Made
<!-- Provide a clear and concise description of what changes you've made -->

- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Performance improvement
- [ ] Code refactoring
- [ ] Documentation update
- [ ] Test improvement

### 🎯 Problem Statement
<!-- Describe the problem or feature request this PR addresses -->

**Closes #** (issue number)

### 💡 Solution Description
<!-- Explain your solution and why you chose this approach -->

### 🧪 Testing Strategy
<!-- Describe how you tested your changes -->

#### Unit Tests
- [ ] Added/updated unit tests
- [ ] All unit tests pass locally
- [ ] Test coverage ≥80%

#### Integration Tests
- [ ] Added/updated integration tests
- [ ] All integration tests pass locally

#### Manual Testing
- [ ] Tested on Android API 24 (minimum supported)
- [ ] Tested on Android API 34 (target)
- [ ] Tested camera functionality
- [ ] Tested pose detection accuracy
- [ ] Tested UI responsiveness

#### Device Testing
<!-- Check all that apply -->
- [ ] Phone (portrait)
- [ ] Phone (landscape)
- [ ] Tablet
- [ ] Different screen densities
- [ ] Low-end device performance

### 🔒 Security Checklist
<!-- Ensure all security requirements are met -->

- [ ] No hardcoded secrets or API keys
- [ ] Proper input validation implemented
- [ ] Authentication/authorization handled correctly
- [ ] Data encryption maintained for sensitive data
- [ ] Privacy compliance verified (GDPR, etc.)
- [ ] Network security best practices followed

### 📱 Performance Impact
<!-- Describe any performance implications -->

#### Performance Metrics
- [ ] APK size impact: ±___MB
- [ ] Memory usage impact: ±___MB
- [ ] Pose detection latency: ___ms
- [ ] UI rendering: No jank introduced

#### Benchmarks
- [ ] Performance benchmarks run
- [ ] No significant regression detected
- [ ] Battery usage optimized

### 🎨 UI/UX Changes
<!-- Include screenshots or screen recordings for UI changes -->

#### Before
<!-- Screenshot/recording of current behavior -->

#### After
<!-- Screenshot/recording of new behavior -->

#### Accessibility
- [ ] Content descriptions added
- [ ] Keyboard navigation supported
- [ ] Color contrast verified
- [ ] TalkBack tested

### 📋 Definition of Done Checklist

#### Code Quality
- [ ] Code follows Kotlin style guidelines
- [ ] No ktlint violations
- [ ] No detekt issues
- [ ] No Android lint warnings
- [ ] Code is self-documenting with appropriate comments

#### Testing Requirements
- [ ] Unit test coverage ≥80%
- [ ] Integration tests pass
- [ ] Manual testing completed
- [ ] Performance regression tests pass

#### Security & Privacy
- [ ] Security scan passes
- [ ] No exposed sensitive data
- [ ] Privacy requirements met
- [ ] Encryption properly implemented

#### Documentation
- [ ] Code is well-documented
- [ ] API changes documented
- [ ] README updated (if needed)
- [ ] Architecture decisions recorded

#### Deployment Readiness
- [ ] All CI/CD checks pass
- [ ] Quality gates satisfied
- [ ] Release notes prepared (if applicable)
- [ ] Backward compatibility maintained

### 🚀 Deployment Notes
<!-- Any special deployment considerations -->

- [ ] Database migrations needed
- [ ] Configuration changes required
- [ ] Feature flags to be enabled
- [ ] Rollback plan prepared

### 📊 Quality Metrics
<!-- These will be automatically populated by CI/CD -->

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| Test Coverage | ≥80% | _%_ | ⏳ |
| APK Size | <100MB | _MB | ⏳ |
| Security Scan | Pass | ⏳ | ⏳ |
| Performance | No regression | ⏳ | ⏳ |

### 🔗 Related Links
<!-- Add links to related issues, documentation, or external resources -->

- Issue: #
- Design Document:
- Technical Specification:
- Related PRs:

### 👥 Reviewers
<!-- Tag specific people for review -->

**Required Reviewers:**
- [ ] @tech-lead (technical review)
- [ ] @security-team (security review for sensitive changes)
- [ ] @ui-ux-team (UI/UX review for interface changes)

**Optional Reviewers:**
- [ ] @domain-expert
- [ ] @performance-team

### 📝 Additional Notes
<!-- Any additional context, concerns, or questions for reviewers -->

---

## For Reviewers

### Review Checklist
- [ ] Code quality and style
- [ ] Test coverage and quality
- [ ] Security considerations
- [ ] Performance impact
- [ ] Documentation completeness
- [ ] Breaking changes identified
- [ ] Deployment risks assessed

### Review Focus Areas
<!-- Highlight specific areas that need attention -->

- **Security:** Pay special attention to data handling and network communications
- **Performance:** Verify pose detection performance and UI responsiveness
- **Compatibility:** Ensure backward compatibility and cross-device support

<!--
Thank you for contributing to Pose Coach! 🎉
Your attention to quality and detail helps us build a better product.
-->