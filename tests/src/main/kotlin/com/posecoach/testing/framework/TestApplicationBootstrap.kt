package com.posecoach.testing.framework

import com.posecoach.testing.framework.coverage.CoverageTracker
import com.posecoach.testing.framework.performance.PerformanceTestOrchestrator
import com.posecoach.testing.framework.privacy.PrivacyComplianceValidator
import com.posecoach.testing.mocks.MockServiceRegistry
import timber.log.Timber

/**
 * Bootstrap class for initializing comprehensive testing framework
 * Coordinates setup of:
 * - Coverage tracking
 * - Performance monitoring
 * - Privacy compliance validation
 * - Mock service registry
 */
object TestApplicationBootstrap {

    private var isInitialized = false

    fun initialize() {
        if (isInitialized) {
            Timber.w("TestApplicationBootstrap already initialized")
            return
        }

        try {
            initializeCoverageTracking()
            initializePerformanceMonitoring()
            initializePrivacyValidation()
            initializeMockRegistry()

            isInitialized = true
            Timber.i("TestApplicationBootstrap initialization complete")

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize TestApplicationBootstrap")
            throw RuntimeException("Test framework initialization failed", e)
        }
    }

    private fun initializeCoverageTracking() {
        CoverageTracker.initialize()
        Timber.d("Coverage tracking initialized")
    }

    private fun initializePerformanceMonitoring() {
        PerformanceTestOrchestrator.initialize()
        Timber.d("Performance monitoring initialized")
    }

    private fun initializePrivacyValidation() {
        PrivacyComplianceValidator.initialize()
        Timber.d("Privacy compliance validation initialized")
    }

    private fun initializeMockRegistry() {
        MockServiceRegistry.initialize()
        Timber.d("Mock service registry initialized")
    }

    fun shutdown() {
        if (!isInitialized) return

        try {
            CoverageTracker.generateReport()
            PerformanceTestOrchestrator.shutdown()
            PrivacyComplianceValidator.generateComplianceReport()
            MockServiceRegistry.clearAll()

            isInitialized = false
            Timber.i("TestApplicationBootstrap shutdown complete")

        } catch (e: Exception) {
            Timber.e(e, "Error during TestApplicationBootstrap shutdown")
        }
    }
}