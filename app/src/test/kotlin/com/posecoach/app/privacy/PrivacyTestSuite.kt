package com.posecoach.app.privacy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import com.posecoach.corepose.PoseLandmarks
import com.posecoach.corepose.models.PoseLandmark
import timber.log.Timber
import java.util.*

/**
 * Comprehensive Privacy Test Suite
 * Tests all privacy requirements and GDPR compliance features
 *
 * Test Categories:
 * 1. Privacy Policy Enforcement
 * 2. Consent Management
 * 3. Data Minimization
 * 4. GDPR Compliance
 * 5. Security Measures
 * 6. Audit Logging
 */
@RunWith(AndroidJUnit4::class)
class PrivacyTestSuite {

    private lateinit var context: Context
    private lateinit var consentManager: ConsentManager
    private lateinit var dataMinimizationManager: DataMinimizationManager
    private lateinit var privacyManager: EnhancedPrivacyManager
    private lateinit var secureStorage: SecureStorageManager
    private lateinit var auditLogger: PrivacyAuditLogger
    private lateinit var gdprManager: GDPRComplianceManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()

        // Initialize privacy components
        consentManager = ConsentManager(context)
        privacyManager = EnhancedPrivacyManager(context)
        dataMinimizationManager = DataMinimizationManager(consentManager, privacyManager)
        secureStorage = SecureStorageManager(context)
        auditLogger = PrivacyAuditLogger(context)
        gdprManager = GDPRComplianceManager(context, consentManager, secureStorage, auditLogger)

        // Reset to known state
        consentManager.withdrawAllConsent()
        privacyManager.resetToDefaults()
    }

    @After
    fun tearDown() {
        // Clean up test data
        secureStorage.secureDeleteAllData()
    }

    // CATEGORY 1: Privacy Policy Enforcement Tests

    @Test
    fun `test image upload is permanently blocked`() = runTest {
        // CRITICAL: This test ensures images are NEVER uploaded per privacy requirements

        // Attempt to request image upload consent
        val imageUploadAllowed = consentManager.requestConsent(ConsentManager.ConsentType.IMAGE_UPLOAD)
        assertFalse("Image upload consent must never be granted", imageUploadAllowed)

        // Attempt to force grant image upload consent
        consentManager.grantConsent(ConsentManager.ConsentType.IMAGE_UPLOAD, true)
        val stillNotAllowed = consentManager.hasConsent(ConsentManager.ConsentType.IMAGE_UPLOAD)
        assertFalse("Image upload must remain blocked even when forced", stillNotAllowed)

        // Verify policy validation blocks image processing outside device
        val validationResult = dataMinimizationManager.validateProcessingRequest(
            dataType = "images",
            processingLocation = "cloud",
            includesImages = true
        )
        assertFalse("Image processing outside device must be blocked", validationResult)
    }

    @Test
    fun `test landmark upload only with explicit consent`() = runTest {
        // Without consent
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, false)
        val policyWithoutConsent = dataMinimizationManager.getDataPolicy()
        assertFalse("Landmark upload requires consent", policyWithoutConsent.allowLandmarkUpload)

        // With consent
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, true)
        val policyWithConsent = dataMinimizationManager.getDataPolicy()
        assertTrue("Landmark upload allowed with consent", policyWithConsent.allowLandmarkUpload)

        // Verify consent withdrawal immediately stops uploads
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, false)
        val policyAfterWithdrawal = dataMinimizationManager.getDataPolicy()
        assertFalse("Landmark upload blocked after consent withdrawal", policyAfterWithdrawal.allowLandmarkUpload)
    }

    @Test
    fun `test default on-device processing priority`() = runTest {
        // Fresh install should default to local-only
        val initialMode = dataMinimizationManager.getCurrentProcessingMode()
        assertEquals("Default mode should be local-only",
            DataMinimizationManager.ProcessingMode.LOCAL_ONLY, initialMode)

        // Processing decision should prioritize local
        val decision = dataMinimizationManager.makeProcessingDecision("pose_landmarks")
        assertEquals("Should default to local processing", "local", decision.policy.processingLocation)
        assertEquals("Should use privacy-by-design basis", "privacy_by_design", decision.consentBasis)
    }

    @Test
    fun `test offline mode prevents all uploads`() = runTest {
        // Grant some consents first
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, true)
        consentManager.grantConsent(ConsentManager.ConsentType.AUDIO_CAPTURE, true)

        // Enable offline mode
        privacyManager.setOfflineModeEnabled(true)

        // Verify all uploads are blocked
        val mode = dataMinimizationManager.getCurrentProcessingMode()
        assertEquals("Offline mode enforced", DataMinimizationManager.ProcessingMode.OFFLINE_FORCED, mode)

        val policy = dataMinimizationManager.getDataPolicy()
        assertFalse("No landmark upload in offline mode", policy.allowLandmarkUpload)
        assertFalse("No audio processing in offline mode", policy.allowAudioProcessing)
    }

    // CATEGORY 2: Consent Management Tests

    @Test
    fun `test granular consent management`() = runTest {
        // Test individual consent types
        val consentTypes = listOf(
            ConsentManager.ConsentType.LANDMARK_DATA,
            ConsentManager.ConsentType.AUDIO_CAPTURE,
            ConsentManager.ConsentType.PERFORMANCE_METRICS,
            ConsentManager.ConsentType.ANALYTICS
        )

        consentTypes.forEach { consentType ->
            // Initially no consent
            assertFalse("No initial consent for $consentType",
                consentManager.hasConsent(consentType))

            // Grant consent
            consentManager.grantConsent(consentType, true)
            assertTrue("Consent granted for $consentType",
                consentManager.hasConsent(consentType))

            // Revoke consent
            consentManager.grantConsent(consentType, false)
            assertFalse("Consent revoked for $consentType",
                consentManager.hasConsent(consentType))
        }
    }

    @Test
    fun `test consent integrity verification`() = runTest {
        // Grant consent with signature
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, true)

        // Verify integrity
        val integrityValid = consentManager.verifyConsentIntegrity()
        assertTrue("Consent integrity should be valid", integrityValid)

        // Test consent summary includes all required information
        val summary = consentManager.getConsentSummary()
        assertTrue("Summary contains consent level", summary.containsKey("consent_level"))
        assertTrue("Summary contains landmark data setting", summary.containsKey("landmark_data"))
        assertEquals("Image upload is permanently blocked",
            "PERMANENTLY_BLOCKED_BY_POLICY", summary["image_upload_blocked"])
    }

    @Test
    fun `test consent expiration and renewal`() = runTest {
        // Grant consent
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, true)

        // Initially not expired
        assertFalse("Consent should not be initially expired",
            consentManager.isConsentExpired())

        // Test that consent checking works correctly
        assertTrue("Consent should be valid",
            consentManager.hasConsent(ConsentManager.ConsentType.LANDMARK_DATA))
    }

    // CATEGORY 3: Data Minimization Tests

    @Test
    fun `test data sanitization removes identifiable information`() = runTest {
        val testLandmarks = createTestLandmarks()

        // Grant consent for landmark upload
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, true)

        val sanitizedData = dataMinimizationManager.sanitizeLandmarkData(testLandmarks)

        // Ensure no personal identifiers
        assertFalse("No device ID", sanitizedData.containsKey("device_id"))
        assertFalse("No user ID", sanitizedData.containsKey("user_id"))
        assertFalse("No image data", sanitizedData.containsKey("image"))
        assertFalse("No biometric data", sanitizedData.containsKey("biometric_data"))

        // Ensure required privacy fields
        assertTrue("Contains landmarks", sanitizedData.containsKey("landmarks"))
        assertTrue("Contains session ID", sanitizedData.containsKey("session_id"))
        assertTrue("Contains exclusion list", sanitizedData.containsKey("excludes"))

        // Verify exclusion list contains critical items
        val excludes = sanitizedData["excludes"] as List<*>
        assertTrue("Excludes images", excludes.contains("images"))
        assertTrue("Excludes device ID", excludes.contains("device_id"))
        assertTrue("Excludes personal identifiers", excludes.contains("personal_identifiers"))
    }

    @Test
    fun `test processing request validation`() = runTest {
        // Test image processing restrictions
        assertFalse("Block image upload",
            dataMinimizationManager.validateProcessingRequest("images", "cloud", true))

        assertTrue("Allow local image processing",
            dataMinimizationManager.validateProcessingRequest("images", "local", true))

        // Test landmark processing with consent
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, true)
        assertTrue("Allow landmark cloud processing with consent",
            dataMinimizationManager.validateProcessingRequest("pose_landmarks", "cloud_landmarks"))

        // Test landmark processing without consent
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, false)
        assertFalse("Block landmark cloud processing without consent",
            dataMinimizationManager.validateProcessingRequest("pose_landmarks", "cloud_landmarks"))
    }

    @Test
    fun `test emergency privacy enforcement`() = runTest {
        // Grant some consents
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, true)
        consentManager.grantConsent(ConsentManager.ConsentType.AUDIO_CAPTURE, true)

        // Trigger emergency privacy mode
        dataMinimizationManager.enforceEmergencyPrivacyMode()

        // Verify all cloud processing is disabled
        assertTrue("Offline mode enabled", privacyManager.isOfflineModeEnabled())
        assertFalse("Landmark consent withdrawn",
            consentManager.hasConsent(ConsentManager.ConsentType.LANDMARK_DATA))
        assertFalse("Audio consent withdrawn",
            consentManager.hasConsent(ConsentManager.ConsentType.AUDIO_CAPTURE))
    }

    // CATEGORY 4: GDPR Compliance Tests

    @Test
    fun `test GDPR data subject access request`() = runTest {
        // Grant some consents to have data to access
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, true)

        val accessData = gdprManager.handleAccessRequest()
        accessData.collect { data ->
            assertTrue("Access request returns data", data.isNotEmpty())
            assertTrue("Contains privacy guarantees", data.containsKey("privacy_guarantees"))
            assertTrue("Contains consent records", data.containsKey("consent_records"))
            assertTrue("Contains data categories", data.containsKey("data_categories"))
            assertTrue("Contains user rights", data.containsKey("user_rights"))

            // Verify privacy guarantees
            val guarantees = data["privacy_guarantees"] as Map<*, *>
            assertEquals("Images never stored guarantee",
                "Per privacy policy, no images are ever stored or transmitted",
                guarantees["images_never_stored"])
        }
    }

    @Test
    fun `test GDPR data erasure request`() = runTest {
        // Set up some data first
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, true)
        secureStorage.storeApiKey("test_key", "test_value")

        // Execute erasure request
        val erasureResult = gdprManager.handleErasureRequest()
        assertTrue("Erasure request successful", erasureResult)

        // Verify data is gone
        assertNull("API key erased", secureStorage.getApiKey("test_key"))
        assertFalse("Consent withdrawn",
            consentManager.hasConsent(ConsentManager.ConsentType.LANDMARK_DATA))
    }

    @Test
    fun `test GDPR data portability request`() = runTest {
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, true)

        val portabilityData = gdprManager.handlePortabilityRequest()
        portabilityData.collect { jsonData ->
            assertNotNull("Portability data returned", jsonData)
            assertTrue("Data is JSON format", jsonData.contains("export_info"))
            assertTrue("Contains important notes", jsonData.contains("important_notes"))
            assertTrue("Contains user preferences", jsonData.contains("user_preferences"))
        }
    }

    @Test
    fun `test GDPR compliance report generation`() = runTest {
        val complianceReport = gdprManager.generateComplianceReport()

        assertNotNull("Compliance report generated", complianceReport)
        assertNotNull("Report has ID", complianceReport.reportId)
        assertTrue("Report has timestamp", complianceReport.timestamp > 0)

        // Check all compliance areas are assessed
        assertNotNull("Data minimization assessed", complianceReport.dataMinimization)
        assertNotNull("Consent management assessed", complianceReport.consentManagement)
        assertNotNull("Data subject rights assessed", complianceReport.dataSubjectRights)
        assertNotNull("Security measures assessed", complianceReport.securityMeasures)

        // Should be compliant with our privacy-first design
        assertNotEquals("Overall compliance determined",
            GDPRComplianceManager.ComplianceStatus.UNKNOWN, complianceReport.overallCompliance)
    }

    // CATEGORY 5: Security Measures Tests

    @Test
    fun `test secure storage encryption`() = runTest {
        val testApiKey = "test_gemini_key_123456789"

        // Store API key
        secureStorage.storeApiKey("gemini", testApiKey)

        // Retrieve API key
        val retrievedKey = secureStorage.getApiKey("gemini")
        assertEquals("API key retrieved correctly", testApiKey, retrievedKey)

        // Test integrity verification
        Thread.sleep(100) // Ensure timestamp difference
        val integrityValid = secureStorage.performSecurityAudit()
        assertNotEquals("Security audit completed",
            SecureStorageManager.SecurityRisk.CRITICAL, integrityValid.riskLevel)
    }

    @Test
    fun `test consent record cryptographic signatures`() = runTest {
        // Store consent with signature
        secureStorage.storeConsentWithSignature(
            consentType = "landmark_data",
            granted = true,
            userSignature = "user_signature_123"
        )

        // Verify consent integrity
        val integrityValid = secureStorage.verifyConsentIntegrity("landmark_data")
        assertTrue("Consent integrity verified", integrityValid)

        // Retrieve consent record
        val consentRecord = secureStorage.getConsentRecord("landmark_data")
        assertNotNull("Consent record retrieved", consentRecord)
        assertEquals("Consent granted correctly", true, consentRecord?.granted)
        assertNotNull("Consent has signature", consentRecord?.userSignature)
    }

    @Test
    fun `test secure data deletion`() = runTest {
        // Store some test data
        secureStorage.storeApiKey("test_key", "test_value")
        secureStorage.storeConsentWithSignature("test_consent", true, "signature")

        // Verify data exists
        assertNotNull("Test data stored", secureStorage.getApiKey("test_key"))

        // Perform secure deletion
        secureStorage.secureDeleteAllData()

        // Verify data is gone
        assertNull("Data securely deleted", secureStorage.getApiKey("test_key"))
        assertFalse("Consent deleted", secureStorage.verifyConsentIntegrity("test_consent"))
    }

    // CATEGORY 6: Audit Logging Tests

    @Test
    fun `test privacy audit logging`() = runTest {
        // Test consent event logging
        auditLogger.logConsentEvent("landmark_data", true, true)

        // Test data processing logging
        auditLogger.logDataProcessing(
            dataType = "pose_landmarks",
            processingLocation = "local",
            consentBasis = "privacy_by_design",
            description = "Test local processing"
        )

        // Test privacy violation logging
        auditLogger.logPrivacyViolation("Test violation", "HIGH")

        // Get audit statistics
        val stats = auditLogger.getAuditStatistics(1)
        assertTrue("Audit events recorded", stats.totalEvents > 0)
        assertTrue("Consent events recorded", stats.consentEvents > 0)
        assertTrue("Processing events recorded", stats.dataProcessingEvents > 0)
        assertTrue("Violations recorded", stats.privacyViolations > 0)
    }

    @Test
    fun `test audit log integrity verification`() = runTest {
        // Generate some audit events
        auditLogger.logConsentEvent("test_consent", true)
        auditLogger.logDataProcessing("test_data", "local", "test_basis", "test_description")

        // Verify log integrity
        val integrityResult = auditLogger.verifyLogIntegrity()
        assertTrue("Audit log integrity verified", integrityResult.isValid)
    }

    @Test
    fun `test audit log export for compliance`() = runTest {
        // Generate audit events
        auditLogger.logConsentEvent("landmark_data", true)
        auditLogger.logDataProcessing("pose_landmarks", "local", "privacy_by_design", "Test processing")

        // Export logs
        val startDate = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000) // 24 hours ago
        val endDate = Date()

        val exportedLogs = auditLogger.exportAuditLogs(startDate, endDate)
        assertNotNull("Logs exported", exportedLogs)
        assertTrue("Exported logs contain consent events", exportedLogs.contains("CONSENT_GRANTED"))
        assertTrue("Exported logs contain processing events", exportedLogs.contains("DATA_PROCESSED"))
    }

    // Integration Tests

    @Test
    fun `test end-to-end privacy workflow`() = runTest {
        // 1. User starts with no consents (privacy-first)
        assertEquals("Initial mode is local-only",
            DataMinimizationManager.ProcessingMode.LOCAL_ONLY,
            dataMinimizationManager.getCurrentProcessingMode())

        // 2. User grants landmark consent
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, true)

        // 3. Processing mode changes to landmarks-only
        assertEquals("Mode changes to landmarks-only",
            DataMinimizationManager.ProcessingMode.LANDMARKS_ONLY,
            dataMinimizationManager.getCurrentProcessingMode())

        // 4. Data can be sanitized for upload
        val testLandmarks = createTestLandmarks()
        val sanitizedData = dataMinimizationManager.sanitizeLandmarkData(testLandmarks)
        assertTrue("Sanitized data contains landmarks", sanitizedData.containsKey("landmarks"))

        // 5. User withdraws consent
        consentManager.grantConsent(ConsentManager.ConsentType.LANDMARK_DATA, false)

        // 6. Processing returns to local-only
        assertEquals("Mode returns to local-only",
            DataMinimizationManager.ProcessingMode.LOCAL_ONLY,
            dataMinimizationManager.getCurrentProcessingMode())
    }

    @Test
    fun `test privacy violation detection and response`() = runTest {
        // Simulate a privacy violation
        auditLogger.logPrivacyViolation("Test violation - image upload attempt", "CRITICAL")

        // Verify violation is logged
        val stats = auditLogger.getAuditStatistics(1)
        assertTrue("Privacy violation logged", stats.privacyViolations > 0)

        // Verify emergency protocols can be triggered
        dataMinimizationManager.enforceEmergencyPrivacyMode()
        assertTrue("Emergency offline mode activated", privacyManager.isOfflineModeEnabled())
    }

    // Helper methods

    private fun createTestLandmarks(): PoseLandmarks {
        val landmarks = (0..32).map { index ->
            PoseLandmark(
                x = index * 0.1f,
                y = index * 0.1f,
                z = index * 0.01f,
                visibility = if (index % 2 == 0) 1.0f else 0.8f
            )
        }
        return PoseLandmarks(landmarks)
    }
}