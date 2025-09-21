package com.posecoach.app.privacy.consent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsentManagerTest {

    private lateinit var context: Context
    private lateinit var consentManager: ConsentManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        consentManager = ConsentManager(context)
    }

    @Test
    fun testConsentRequestCreation() = runBlocking {
        // Given a consent request
        val request = ConsentManager.ConsentRequest(
            purposes = setOf(ConsentManager.ConsentPurpose.POSE_ANALYSIS, ConsentManager.ConsentPurpose.COACHING_SUGGESTIONS),
            dataTypes = setOf(ConsentManager.DataType.POSE_LANDMARKS, ConsentManager.DataType.USAGE_METRICS),
            processingLocations = setOf(ConsentManager.ProcessingLocation.LOCAL_DEVICE, ConsentManager.ProcessingLocation.REGIONAL_CLOUD),
            retentionPeriod = 86400000L, // 1 day
            description = "AI-powered pose analysis and coaching"
        )

        // When requesting consent
        val result = consentManager.requestConsent(request)

        // Then should create pending request
        when (result) {
            is ConsentManager.ConsentRequestResult.PendingUserResponse -> {
                assertEquals("Should return the same request", request, result.request)
            }
            is ConsentManager.ConsentRequestResult.AlreadyGranted -> {
                // This is also valid if consent was previously granted
                assertNotNull("Should have existing consent record", result.record)
            }
            else -> fail("Unexpected result type: $result")
        }
    }

    @Test
    fun testConsentGranting() = runBlocking {
        // Given a consent request
        val request = ConsentManager.ConsentRequest(
            purposes = setOf(ConsentManager.ConsentPurpose.POSE_ANALYSIS),
            dataTypes = setOf(ConsentManager.DataType.POSE_LANDMARKS),
            processingLocations = setOf(ConsentManager.ProcessingLocation.LOCAL_DEVICE),
            retentionPeriod = 86400000L,
            description = "Basic pose analysis"
        )

        // When granting consent
        val consentRecord = consentManager.grantConsent(
            request = request,
            userSignature = "user_signature_hash",
            metadata = ConsentManager.ConsentMetadata(
                ipAddress = "192.168.1.1",
                userAgent = "TestAgent/1.0"
            )
        )

        // Then should create valid consent record
        assertNotNull("Consent record should be created", consentRecord.id)
        assertEquals("Should have current version", ConsentManager.CURRENT_CONSENT_VERSION, consentRecord.version)
        assertTrue("Should be active", consentRecord.isActive)
        assertEquals("Should store user signature", "user_signature_hash", consentRecord.userSignature)
        assertEquals("Should store IP address", "192.168.1.1", consentRecord.ipAddress)

        // And consent should be granted for specified purposes
        request.purposes.forEach { purpose ->
            assertEquals("Purpose should be granted", ConsentManager.ConsentStatus.GRANTED, consentRecord.purposes[purpose])
        }

        request.dataTypes.forEach { dataType ->
            assertEquals("Data type should be granted", ConsentManager.ConsentStatus.GRANTED, consentRecord.dataTypes[dataType])
        }
    }

    @Test
    fun testConsentWithdrawal() = runBlocking {
        // Given existing consent
        val request = ConsentManager.ConsentRequest(
            purposes = setOf(ConsentManager.ConsentPurpose.POSE_ANALYSIS, ConsentManager.ConsentPurpose.PERFORMANCE_ANALYTICS),
            dataTypes = setOf(ConsentManager.DataType.POSE_LANDMARKS, ConsentManager.DataType.USAGE_METRICS),
            processingLocations = setOf(ConsentManager.ProcessingLocation.LOCAL_DEVICE),
            retentionPeriod = 86400000L,
            description = "Pose analysis and analytics"
        )

        consentManager.grantConsent(request)

        // When withdrawing specific consent
        val withdrawalResult = consentManager.withdrawConsent(
            purposes = setOf(ConsentManager.ConsentPurpose.PERFORMANCE_ANALYTICS),
            dataTypes = setOf(ConsentManager.DataType.USAGE_METRICS),
            reason = "User no longer wants analytics"
        )

        // Then should successfully withdraw consent
        when (withdrawalResult) {
            is ConsentManager.ConsentWithdrawalResult.Success -> {
                val updatedRecord = withdrawalResult.record
                assertEquals("Analytics purpose should be withdrawn",
                           ConsentManager.ConsentStatus.WITHDRAWN,
                           updatedRecord.purposes[ConsentManager.ConsentPurpose.PERFORMANCE_ANALYTICS])
                assertEquals("Usage metrics should be withdrawn",
                           ConsentManager.ConsentStatus.WITHDRAWN,
                           updatedRecord.dataTypes[ConsentManager.DataType.USAGE_METRICS])
                assertEquals("Should store withdrawal reason",
                           "User no longer wants analytics",
                           updatedRecord.withdrawalReason)

                // But other consents should remain
                assertEquals("Pose analysis should remain granted",
                           ConsentManager.ConsentStatus.GRANTED,
                           updatedRecord.purposes[ConsentManager.ConsentPurpose.POSE_ANALYSIS])

                // Should initiate data deletion
                assertFalse("Should have deletion tasks", withdrawalResult.deletionTasks.isEmpty())
            }
            else -> fail("Withdrawal should succeed")
        }
    }

    @Test
    fun testConsentStatusChecking() = runBlocking {
        // Given granted consent for specific purpose and data type
        val request = ConsentManager.ConsentRequest(
            purposes = setOf(ConsentManager.ConsentPurpose.COACHING_SUGGESTIONS),
            dataTypes = setOf(ConsentManager.DataType.POSE_LANDMARKS),
            processingLocations = setOf(ConsentManager.ProcessingLocation.LOCAL_DEVICE),
            retentionPeriod = 86400000L,
            description = "AI coaching"
        )

        consentManager.grantConsent(request)

        // When checking consent status
        val isGranted = consentManager.isConsentGranted(
            ConsentManager.ConsentPurpose.COACHING_SUGGESTIONS,
            ConsentManager.DataType.POSE_LANDMARKS
        )
        val isNotGranted = consentManager.isConsentGranted(
            ConsentManager.ConsentPurpose.MARKETING,
            ConsentManager.DataType.PERSONAL_INFORMATION
        )

        // Then should return correct status
        assertTrue("Should be granted for coaching with pose landmarks", isGranted)
        assertFalse("Should not be granted for marketing with personal info", isNotGranted)
    }

    @Test
    fun testConsentRenewal() = runBlocking {
        // Given existing consent
        val request = ConsentManager.ConsentRequest(
            purposes = setOf(ConsentManager.ConsentPurpose.POSE_ANALYSIS),
            dataTypes = setOf(ConsentManager.DataType.POSE_LANDMARKS),
            processingLocations = setOf(ConsentManager.ProcessingLocation.LOCAL_DEVICE),
            retentionPeriod = 86400000L,
            description = "Pose analysis"
        )

        val originalRecord = consentManager.grantConsent(request)

        // When renewing consent
        val renewedRecord = consentManager.renewConsent(
            purposes = setOf(ConsentManager.ConsentPurpose.POSE_ANALYSIS),
            userSignature = "renewed_signature"
        )

        // Then should update consent with new timestamp
        assertNotEquals("Should have new timestamp", originalRecord.timestamp, renewedRecord.timestamp)
        assertEquals("Should have renewed signature", "renewed_signature", renewedRecord.userSignature)
        assertTrue("Should extend expiry", renewedRecord.expiryTimestamp!! > originalRecord.expiryTimestamp!!)
    }

    @Test
    fun testConsentVersioning() = runBlocking {
        // Given consent with previous version
        val request = ConsentManager.ConsentRequest(
            purposes = setOf(ConsentManager.ConsentPurpose.POSE_ANALYSIS),
            dataTypes = setOf(ConsentManager.DataType.POSE_LANDMARKS),
            processingLocations = setOf(ConsentManager.ProcessingLocation.LOCAL_DEVICE),
            retentionPeriod = 86400000L,
            description = "Basic pose analysis"
        )

        val record1 = consentManager.grantConsent(request)

        // When granting updated consent
        val updatedRequest = request.copy(
            purposes = setOf(ConsentManager.ConsentPurpose.POSE_ANALYSIS, ConsentManager.ConsentPurpose.COACHING_SUGGESTIONS)
        )
        val record2 = consentManager.grantConsent(updatedRequest)

        // Then should maintain version history
        assertEquals("Should maintain current version", ConsentManager.CURRENT_CONSENT_VERSION, record2.version)
        assertTrue("Should have newer timestamp", record2.timestamp > record1.timestamp)
    }

    @Test
    fun testConsentSummaryGeneration() {
        // Given various consent states
        // This would be set up with different consent records

        // When getting consent summary
        val summary = consentManager.getConsentSummary()

        // Then should provide comprehensive overview
        assertNotNull("Should have overall status", summary.overallStatus)
        assertTrue("Purpose completeness should be 0-100", summary.purposeCompleteness in 0..100)
        assertTrue("Data type completeness should be 0-100", summary.dataTypeCompleteness in 0..100)
        assertNotNull("Should indicate if renewal needed", summary.needsRenewal)
    }

    @Test
    fun testConsentDataExport() {
        // When exporting consent data
        val export = consentManager.exportConsentData()

        // Then should include all relevant information
        assertNotNull("Should have current consent", export.currentConsent)
        assertNotNull("Should have consent history", export.consentHistory)
        assertNotNull("Should have change log", export.changeLog)
        assertTrue("Should have recent export timestamp",
                  export.exportTimestamp > System.currentTimeMillis() - 60000) // Within last minute
        assertNotNull("Should have integrity hash", export.dataIntegrityHash)
    }

    @Test
    fun testConsentDeletion() = runBlocking {
        // Given existing consent data
        val request = ConsentManager.ConsentRequest(
            purposes = setOf(ConsentManager.ConsentPurpose.POSE_ANALYSIS),
            dataTypes = setOf(ConsentManager.DataType.POSE_LANDMARKS),
            processingLocations = setOf(ConsentManager.ProcessingLocation.LOCAL_DEVICE),
            retentionPeriod = 86400000L,
            description = "Test consent"
        )

        consentManager.grantConsent(request)

        // When deleting all consent data
        val deletionResult = consentManager.deleteAllConsentData("User requested data deletion")

        // Then should successfully delete all data
        when (deletionResult) {
            is ConsentManager.DeletionResult.Success -> {
                assertTrue("Should have deletion timestamp", deletionResult.timestamp > 0)

                // Verify data is actually deleted
                val summary = consentManager.getConsentSummary()
                assertEquals("Should reset to not requested",
                           ConsentManager.ConsentStatus.NOT_REQUESTED,
                           summary.overallStatus)
            }
            is ConsentManager.DeletionResult.Failure -> {
                fail("Deletion should not fail: ${deletionResult.reason}")
            }
        }
    }

    @Test
    fun testConsentChangeLogging() = runBlocking {
        // Given consent operations
        val request = ConsentManager.ConsentRequest(
            purposes = setOf(ConsentManager.ConsentPurpose.POSE_ANALYSIS),
            dataTypes = setOf(ConsentManager.DataType.POSE_LANDMARKS),
            processingLocations = setOf(ConsentManager.ProcessingLocation.LOCAL_DEVICE),
            retentionPeriod = 86400000L,
            description = "Pose analysis"
        )

        // When performing consent operations
        consentManager.grantConsent(request)
        consentManager.withdrawConsent(
            purposes = setOf(ConsentManager.ConsentPurpose.POSE_ANALYSIS),
            reason = "Changed mind"
        )

        // Then should log all changes
        val changeLog = consentManager.changeLog.value
        assertTrue("Should have logged changes", changeLog.isNotEmpty())

        val grantChange = changeLog.find { it.changeType == ConsentManager.ChangeType.MODIFICATION }
        val withdrawChange = changeLog.find { it.changeType == ConsentManager.ChangeType.WITHDRAWAL }

        assertNotNull("Should log consent grant", grantChange)
        assertNotNull("Should log consent withdrawal", withdrawChange)

        withdrawChange?.let {
            assertEquals("Should log withdrawal reason", "Changed mind", it.reason)
            assertTrue("Should mark as user initiated", it.userInitiated)
        }
    }

    @Test
    fun testConsentExpiry() = runBlocking {
        // Given consent with short expiry
        val request = ConsentManager.ConsentRequest(
            purposes = setOf(ConsentManager.ConsentPurpose.POSE_ANALYSIS),
            dataTypes = setOf(ConsentManager.DataType.POSE_LANDMARKS),
            processingLocations = setOf(ConsentManager.ProcessingLocation.LOCAL_DEVICE),
            retentionPeriod = 1000L, // 1 second
            description = "Short-lived consent"
        )

        val record = consentManager.grantConsent(request)

        // When checking consent after expiry
        Thread.sleep(1100) // Wait for expiry

        val isStillGranted = consentManager.isConsentGranted(
            ConsentManager.ConsentPurpose.POSE_ANALYSIS,
            ConsentManager.DataType.POSE_LANDMARKS
        )

        // Then should be expired
        assertFalse("Expired consent should not be granted", isStillGranted)
    }
}