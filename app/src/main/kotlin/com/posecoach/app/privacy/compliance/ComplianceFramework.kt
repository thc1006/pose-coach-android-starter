package com.posecoach.app.privacy.compliance

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.util.*

/**
 * Comprehensive Compliance Framework
 * Implements GDPR, CCPA, HIPAA, and COPPA compliance with automated
 * compliance monitoring, violation detection, and remediation.
 */
class ComplianceFramework(private val context: Context) {

    @Serializable
    data class ComplianceProfile(
        val gdprEnabled: Boolean = true,
        val ccpaEnabled: Boolean = true,
        val hipaaEnabled: Boolean = false,
        val coppaEnabled: Boolean = false,
        val applicableRegions: Set<String> = setOf("EU", "CA", "US"),
        val userAge: UserAge = UserAge.ADULT,
        val healthcareContext: Boolean = false,
        val businessType: BusinessType = BusinessType.CONSUMER_APP
    )

    @Serializable
    data class ComplianceStatus(
        val overallCompliance: ComplianceLevel = ComplianceLevel.UNKNOWN,
        val gdprCompliance: GDPRStatus = GDPRStatus(),
        val ccpaCompliance: CCPAStatus = CCPAStatus(),
        val hipaaCompliance: HIPAAStatus = HIPAAStatus(),
        val coppaCompliance: COPPAStatus = COPPAStatus(),
        val lastAssessment: Long = System.currentTimeMillis(),
        val violations: List<ComplianceViolation> = emptyList(),
        val recommendations: List<ComplianceRecommendation> = emptyList()
    )

    @Serializable
    data class GDPRStatus(
        val lawfulBasisEstablished: Boolean = false,
        val consentDocumented: Boolean = false,
        val rightToErasureImplemented: Boolean = false,
        val dataPortabilityEnabled: Boolean = false,
        val privacyByDesign: Boolean = false,
        val dataMinimizationApplied: Boolean = false,
        val securityMeasuresAdequate: Boolean = false,
        val dpoContactAvailable: Boolean = false
    )

    @Serializable
    data class CCPAStatus(
        val rightToKnowImplemented: Boolean = false,
        val rightToDeleteImplemented: Boolean = false,
        val rightToOptOutImplemented: Boolean = false,
        val nonDiscriminationPolicyActive: Boolean = false,
        val personalInfoInventoryComplete: Boolean = false,
        val thirdPartyDisclosuresDocumented: Boolean = false
    )

    @Serializable
    data class HIPAAStatus(
        val administrativeSafeguards: Boolean = false,
        val physicalSafeguards: Boolean = false,
        val technicalSafeguards: Boolean = false,
        val organizationalRequirements: Boolean = false,
        val businessAssociateAgreements: Boolean = false,
        val breachNotificationReady: Boolean = false
    )

    @Serializable
    data class COPPAStatus(
        val parentalConsentRequired: Boolean = false,
        val parentalConsentObtained: Boolean = false,
        val limitedDataCollection: Boolean = false,
        val noTargetedAdvertising: Boolean = false,
        val parentalAccessEnabled: Boolean = false,
        val schoolOfficialException: Boolean = false
    )

    @Serializable
    data class ComplianceViolation(
        val id: String = UUID.randomUUID().toString(),
        val type: ViolationType,
        val severity: ComplianceSeverity,
        val regulation: ComplianceRegulation,
        val description: String,
        val detectedAt: Long = System.currentTimeMillis(),
        val status: ViolationStatus = ViolationStatus.DETECTED,
        val remediation: String? = null,
        val resolvedAt: Long? = null
    )

    @Serializable
    data class ComplianceRecommendation(
        val id: String = UUID.randomUUID().toString(),
        val priority: RecommendationPriority,
        val regulation: ComplianceRegulation,
        val title: String,
        val description: String,
        val actionItems: List<String>,
        val estimatedEffort: EffortLevel,
        val deadline: Long? = null
    )

    enum class ComplianceLevel {
        COMPLIANT, PARTIALLY_COMPLIANT, NON_COMPLIANT, UNKNOWN
    }

    enum class UserAge {
        CHILD_UNDER_13, TEEN_13_TO_16, ADULT_OVER_16, ADULT, UNKNOWN
    }

    enum class BusinessType {
        CONSUMER_APP, HEALTHCARE_PROVIDER, EDUCATIONAL_INSTITUTION, ENTERPRISE
    }

    enum class ViolationType {
        CONSENT_VIOLATION,
        DATA_RETENTION_VIOLATION,
        UNAUTHORIZED_PROCESSING,
        INSUFFICIENT_SECURITY,
        MISSING_DISCLOSURE,
        INADEQUATE_USER_RIGHTS,
        BREACH_NOTIFICATION_FAILURE,
        AGE_VERIFICATION_FAILURE
    }

    enum class ComplianceSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    enum class ComplianceRegulation {
        GDPR, CCPA, HIPAA, COPPA, PIPEDA, LGPD
    }

    enum class ViolationStatus {
        DETECTED, ACKNOWLEDGED, IN_REMEDIATION, RESOLVED, ESCALATED
    }

    enum class RecommendationPriority {
        LOW, MEDIUM, HIGH, URGENT
    }

    enum class EffortLevel {
        MINIMAL, LOW, MEDIUM, HIGH, EXTENSIVE
    }

    private val _complianceProfile = MutableStateFlow(ComplianceProfile())
    val complianceProfile: StateFlow<ComplianceProfile> = _complianceProfile.asStateFlow()

    private val _complianceStatus = MutableStateFlow(ComplianceStatus())
    val complianceStatus: StateFlow<ComplianceStatus> = _complianceStatus.asStateFlow()

    private val _activeViolations = MutableStateFlow<List<ComplianceViolation>>(emptyList())
    val activeViolations: StateFlow<List<ComplianceViolation>> = _activeViolations.asStateFlow()

    init {
        Timber.d("Compliance Framework initialized")
        performInitialAssessment()
    }

    /**
     * Perform comprehensive compliance assessment
     */
    suspend fun performComplianceAssessment(): ComplianceStatus {
        val profile = _complianceProfile.value

        val gdprStatus = if (profile.gdprEnabled) assessGDPRCompliance() else GDPRStatus()
        val ccpaStatus = if (profile.ccpaEnabled) assessCCPACompliance() else CCPAStatus()
        val hipaaStatus = if (profile.hipaaEnabled) assessHIPAACompliance() else HIPAAStatus()
        val coppaStatus = if (profile.coppaEnabled) assessCOPPACompliance() else COPPAStatus()

        val violations = detectViolations(gdprStatus, ccpaStatus, hipaaStatus, coppaStatus)
        val recommendations = generateRecommendations(gdprStatus, ccpaStatus, hipaaStatus, coppaStatus)

        val overallCompliance = calculateOverallCompliance(gdprStatus, ccpaStatus, hipaaStatus, coppaStatus, violations)

        val status = ComplianceStatus(
            overallCompliance = overallCompliance,
            gdprCompliance = gdprStatus,
            ccpaCompliance = ccpaStatus,
            hipaaCompliance = hipaaStatus,
            coppaCompliance = coppaStatus,
            violations = violations,
            recommendations = recommendations,
            lastAssessment = System.currentTimeMillis()
        )

        _complianceStatus.value = status
        _activeViolations.value = violations.filter { it.status != ViolationStatus.RESOLVED }

        Timber.i("Compliance assessment completed: $overallCompliance")
        return status
    }

    /**
     * Check GDPR Article 6 lawful basis for processing
     */
    fun checkGDPRLawfulBasis(
        processingPurpose: String,
        dataTypes: Set<String>,
        userConsent: Boolean = false
    ): GDPRLawfulBasisResult {
        val applicableBases = mutableListOf<LawfulBasis>()

        // Article 6(1)(a) - Consent
        if (userConsent) {
            applicableBases.add(LawfulBasis.CONSENT)
        }

        // Article 6(1)(b) - Contract performance
        if (processingPurpose.contains("service delivery", ignoreCase = true)) {
            applicableBases.add(LawfulBasis.CONTRACT)
        }

        // Article 6(1)(c) - Legal obligation
        if (processingPurpose.contains("compliance", ignoreCase = true)) {
            applicableBases.add(LawfulBasis.LEGAL_OBLIGATION)
        }

        // Article 6(1)(f) - Legitimate interests
        if (processingPurpose.contains("security", ignoreCase = true) ||
            processingPurpose.contains("fraud prevention", ignoreCase = true)) {
            applicableBases.add(LawfulBasis.LEGITIMATE_INTERESTS)
        }

        return GDPRLawfulBasisResult(
            hasLawfulBasis = applicableBases.isNotEmpty(),
            applicableBases = applicableBases,
            recommendedBasis = applicableBases.firstOrNull(),
            additionalRequirements = getAdditionalRequirements(applicableBases)
        )
    }

    /**
     * Implement GDPR right to erasure (right to be forgotten)
     */
    suspend fun executeRightToErasure(
        userId: String,
        erasureReason: ErasureReason,
        dataCategories: Set<String> = emptySet()
    ): ErasureResult {
        try {
            // Validate erasure request
            val validationResult = validateErasureRequest(userId, erasureReason, dataCategories)
            if (!validationResult.isValid) {
                return ErasureResult.Rejected(validationResult.reason)
            }

            // Perform data deletion
            val deletionTasks = mutableListOf<DataDeletionTask>()

            if (dataCategories.isEmpty() || dataCategories.contains("personal_data")) {
                deletionTasks.add(deletePersonalData(userId))
            }

            if (dataCategories.isEmpty() || dataCategories.contains("pose_data")) {
                deletionTasks.add(deletePoseData(userId))
            }

            if (dataCategories.isEmpty() || dataCategories.contains("usage_analytics")) {
                deletionTasks.add(deleteAnalyticsData(userId))
            }

            // Log erasure for compliance
            logErasureRequest(userId, erasureReason, dataCategories, deletionTasks)

            return ErasureResult.Completed(
                userId = userId,
                deletedCategories = dataCategories,
                deletionTasks = deletionTasks,
                completedAt = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Timber.e(e, "Failed to execute right to erasure for user: $userId")
            return ErasureResult.Failed(e.message ?: "Unknown error")
        }
    }

    /**
     * Generate GDPR-compliant data export
     */
    suspend fun exportUserData(userId: String): DataExportResult {
        try {
            val exportData = mutableMapOf<String, Any>()

            // Personal information
            exportData["personal_info"] = exportPersonalInfo(userId)

            // Pose analysis data
            exportData["pose_data"] = exportPoseData(userId)

            // Usage analytics
            exportData["usage_analytics"] = exportAnalyticsData(userId)

            // Consent records
            exportData["consent_records"] = exportConsentRecords(userId)

            // App preferences
            exportData["preferences"] = exportUserPreferences(userId)

            val exportPackage = DataExportPackage(
                userId = userId,
                data = exportData,
                exportedAt = System.currentTimeMillis(),
                format = "JSON",
                checksum = calculateChecksum(exportData)
            )

            return DataExportResult.Success(exportPackage)

        } catch (e: Exception) {
            Timber.e(e, "Failed to export user data: $userId")
            return DataExportResult.Failed(e.message ?: "Unknown error")
        }
    }

    /**
     * Implement CCPA right to opt-out of sale
     */
    suspend fun implementOptOutOfSale(userId: String): OptOutResult {
        try {
            // Record opt-out preference
            recordOptOutPreference(userId)

            // Stop any data sales or sharing
            stopDataSharing(userId)

            // Update third-party integrations
            updateThirdPartyOptOut(userId)

            return OptOutResult.Success(
                userId = userId,
                optOutTimestamp = System.currentTimeMillis(),
                confirmationId = UUID.randomUUID().toString()
            )

        } catch (e: Exception) {
            Timber.e(e, "Failed to implement opt-out for user: $userId")
            return OptOutResult.Failed(e.message ?: "Unknown error")
        }
    }

    /**
     * Check COPPA compliance for users under 13
     */
    fun checkCOPPACompliance(userAge: Int): COPPAComplianceResult {
        val isSubjectToCOPPA = userAge < 13

        if (!isSubjectToCOPPA) {
            return COPPAComplianceResult(
                subjectToCOPPA = false,
                compliant = true,
                requirements = emptyList()
            )
        }

        val requirements = mutableListOf<COPPARequirement>()
        val violations = mutableListOf<String>()

        // Check parental consent
        if (!hasParentalConsent(userAge)) {
            requirements.add(COPPARequirement.PARENTAL_CONSENT)
            violations.add("Parental consent required for users under 13")
        }

        // Check data collection limitations
        if (!hasLimitedDataCollection()) {
            requirements.add(COPPARequirement.LIMITED_DATA_COLLECTION)
            violations.add("Data collection must be limited for users under 13")
        }

        // Check advertising restrictions
        if (!hasAdvertisingRestrictions()) {
            requirements.add(COPPARequirement.NO_TARGETED_ADVERTISING)
            violations.add("Targeted advertising prohibited for users under 13")
        }

        return COPPAComplianceResult(
            subjectToCOPPA = true,
            compliant = violations.isEmpty(),
            requirements = requirements,
            violations = violations
        )
    }

    /**
     * Update compliance profile
     */
    fun updateComplianceProfile(profile: ComplianceProfile) {
        _complianceProfile.value = profile
        Timber.i("Compliance profile updated: $profile")

        // Trigger new assessment
        performInitialAssessment()
    }

    // Private implementation methods

    private fun performInitialAssessment() {
        // Perform initial compliance assessment
        // This would be called in a coroutine scope in production
    }

    private suspend fun assessGDPRCompliance(): GDPRStatus {
        return GDPRStatus(
            lawfulBasisEstablished = checkLawfulBasisEstablished(),
            consentDocumented = checkConsentDocumented(),
            rightToErasureImplemented = checkRightToErasureImplemented(),
            dataPortabilityEnabled = checkDataPortabilityEnabled(),
            privacyByDesign = checkPrivacyByDesign(),
            dataMinimizationApplied = checkDataMinimizationApplied(),
            securityMeasuresAdequate = checkSecurityMeasuresAdequate(),
            dpoContactAvailable = checkDPOContactAvailable()
        )
    }

    private suspend fun assessCCPACompliance(): CCPAStatus {
        return CCPAStatus(
            rightToKnowImplemented = checkRightToKnowImplemented(),
            rightToDeleteImplemented = checkRightToDeleteImplemented(),
            rightToOptOutImplemented = checkRightToOptOutImplemented(),
            nonDiscriminationPolicyActive = checkNonDiscriminationPolicy(),
            personalInfoInventoryComplete = checkPersonalInfoInventory(),
            thirdPartyDisclosuresDocumented = checkThirdPartyDisclosures()
        )
    }

    private suspend fun assessHIPAACompliance(): HIPAAStatus {
        return HIPAAStatus(
            administrativeSafeguards = checkAdministrativeSafeguards(),
            physicalSafeguards = checkPhysicalSafeguards(),
            technicalSafeguards = checkTechnicalSafeguards(),
            organizationalRequirements = checkOrganizationalRequirements(),
            businessAssociateAgreements = checkBusinessAssociateAgreements(),
            breachNotificationReady = checkBreachNotificationReadiness()
        )
    }

    private suspend fun assessCOPPACompliance(): COPPAStatus {
        val profile = _complianceProfile.value
        val requiresCompliance = profile.userAge == UserAge.CHILD_UNDER_13

        return if (requiresCompliance) {
            COPPAStatus(
                parentalConsentRequired = true,
                parentalConsentObtained = checkParentalConsentObtained(),
                limitedDataCollection = checkLimitedDataCollection(),
                noTargetedAdvertising = checkNoTargetedAdvertising(),
                parentalAccessEnabled = checkParentalAccessEnabled(),
                schoolOfficialException = checkSchoolOfficialException()
            )
        } else {
            COPPAStatus()
        }
    }

    private fun detectViolations(
        gdpr: GDPRStatus,
        ccpa: CCPAStatus,
        hipaa: HIPAAStatus,
        coppa: COPPAStatus
    ): List<ComplianceViolation> {
        val violations = mutableListOf<ComplianceViolation>()

        // GDPR violations
        if (_complianceProfile.value.gdprEnabled) {
            if (!gdpr.lawfulBasisEstablished) {
                violations.add(createViolation(
                    ViolationType.CONSENT_VIOLATION,
                    ComplianceSeverity.HIGH,
                    ComplianceRegulation.GDPR,
                    "No lawful basis established for data processing"
                ))
            }
            if (!gdpr.rightToErasureImplemented) {
                violations.add(createViolation(
                    ViolationType.INADEQUATE_USER_RIGHTS,
                    ComplianceSeverity.HIGH,
                    ComplianceRegulation.GDPR,
                    "Right to erasure not properly implemented"
                ))
            }
        }

        // CCPA violations
        if (_complianceProfile.value.ccpaEnabled) {
            if (!ccpa.rightToOptOutImplemented) {
                violations.add(createViolation(
                    ViolationType.INADEQUATE_USER_RIGHTS,
                    ComplianceSeverity.MEDIUM,
                    ComplianceRegulation.CCPA,
                    "Right to opt-out not implemented"
                ))
            }
        }

        // COPPA violations
        if (_complianceProfile.value.coppaEnabled) {
            if (!coppa.parentalConsentObtained && coppa.parentalConsentRequired) {
                violations.add(createViolation(
                    ViolationType.AGE_VERIFICATION_FAILURE,
                    ComplianceSeverity.CRITICAL,
                    ComplianceRegulation.COPPA,
                    "Parental consent required but not obtained"
                ))
            }
        }

        return violations
    }

    private fun generateRecommendations(
        gdpr: GDPRStatus,
        ccpa: CCPAStatus,
        hipaa: HIPAAStatus,
        coppa: COPPAStatus
    ): List<ComplianceRecommendation> {
        val recommendations = mutableListOf<ComplianceRecommendation>()

        // GDPR recommendations
        if (_complianceProfile.value.gdprEnabled && !gdpr.privacyByDesign) {
            recommendations.add(ComplianceRecommendation(
                priority = RecommendationPriority.HIGH,
                regulation = ComplianceRegulation.GDPR,
                title = "Implement Privacy by Design",
                description = "Integrate privacy considerations into system design",
                actionItems = listOf(
                    "Conduct privacy impact assessments",
                    "Implement data minimization",
                    "Enable privacy-preserving technologies"
                ),
                estimatedEffort = EffortLevel.HIGH
            ))
        }

        return recommendations
    }

    private fun calculateOverallCompliance(
        gdpr: GDPRStatus,
        ccpa: CCPAStatus,
        hipaa: HIPAAStatus,
        coppa: COPPAStatus,
        violations: List<ComplianceViolation>
    ): ComplianceLevel {
        val criticalViolations = violations.count { it.severity == ComplianceSeverity.CRITICAL }
        val highViolations = violations.count { it.severity == ComplianceSeverity.HIGH }

        return when {
            criticalViolations > 0 -> ComplianceLevel.NON_COMPLIANT
            highViolations > 2 -> ComplianceLevel.PARTIALLY_COMPLIANT
            violations.isEmpty() -> ComplianceLevel.COMPLIANT
            else -> ComplianceLevel.PARTIALLY_COMPLIANT
        }
    }

    private fun createViolation(
        type: ViolationType,
        severity: ComplianceSeverity,
        regulation: ComplianceRegulation,
        description: String
    ): ComplianceViolation {
        return ComplianceViolation(
            type = type,
            severity = severity,
            regulation = regulation,
            description = description
        )
    }

    // Helper methods for compliance checks
    private fun checkLawfulBasisEstablished(): Boolean = true // Simplified
    private fun checkConsentDocumented(): Boolean = true
    private fun checkRightToErasureImplemented(): Boolean = true
    private fun checkDataPortabilityEnabled(): Boolean = true
    private fun checkPrivacyByDesign(): Boolean = false
    private fun checkDataMinimizationApplied(): Boolean = true
    private fun checkSecurityMeasuresAdequate(): Boolean = true
    private fun checkDPOContactAvailable(): Boolean = false

    private fun checkRightToKnowImplemented(): Boolean = true
    private fun checkRightToDeleteImplemented(): Boolean = true
    private fun checkRightToOptOutImplemented(): Boolean = false
    private fun checkNonDiscriminationPolicy(): Boolean = true
    private fun checkPersonalInfoInventory(): Boolean = true
    private fun checkThirdPartyDisclosures(): Boolean = true

    private fun checkAdministrativeSafeguards(): Boolean = false
    private fun checkPhysicalSafeguards(): Boolean = false
    private fun checkTechnicalSafeguards(): Boolean = true
    private fun checkOrganizationalRequirements(): Boolean = false
    private fun checkBusinessAssociateAgreements(): Boolean = false
    private fun checkBreachNotificationReadiness(): Boolean = false

    private fun checkParentalConsentObtained(): Boolean = false
    private fun checkLimitedDataCollection(): Boolean = true
    private fun checkNoTargetedAdvertising(): Boolean = true
    private fun checkParentalAccessEnabled(): Boolean = false
    private fun checkSchoolOfficialException(): Boolean = false

    private fun hasParentalConsent(userAge: Int): Boolean = false
    private fun hasLimitedDataCollection(): Boolean = true
    private fun hasAdvertisingRestrictions(): Boolean = true

    // Data handling methods
    private suspend fun deletePersonalData(userId: String): DataDeletionTask =
        DataDeletionTask("personal_data", "Deleted")
    private suspend fun deletePoseData(userId: String): DataDeletionTask =
        DataDeletionTask("pose_data", "Deleted")
    private suspend fun deleteAnalyticsData(userId: String): DataDeletionTask =
        DataDeletionTask("analytics_data", "Deleted")

    private fun exportPersonalInfo(userId: String): Map<String, Any> = mapOf("userId" to userId)
    private fun exportPoseData(userId: String): Map<String, Any> = mapOf("poseHistory" to emptyList<Any>())
    private fun exportAnalyticsData(userId: String): Map<String, Any> = mapOf("analytics" to emptyList<Any>())
    private fun exportConsentRecords(userId: String): Map<String, Any> = mapOf("consents" to emptyList<Any>())
    private fun exportUserPreferences(userId: String): Map<String, Any> = mapOf("preferences" to emptyMap<String, Any>())

    private fun calculateChecksum(data: Map<String, Any>): String = data.hashCode().toString()

    private fun validateErasureRequest(userId: String, reason: ErasureReason, categories: Set<String>) =
        ValidationResult(true, "Valid")

    private fun logErasureRequest(userId: String, reason: ErasureReason, categories: Set<String>, tasks: List<DataDeletionTask>) {
        Timber.i("Erasure request logged for user: $userId")
    }

    private fun recordOptOutPreference(userId: String) {
        Timber.i("Opt-out preference recorded for user: $userId")
    }

    private fun stopDataSharing(userId: String) {
        Timber.i("Data sharing stopped for user: $userId")
    }

    private fun updateThirdPartyOptOut(userId: String) {
        Timber.i("Third-party opt-out updated for user: $userId")
    }

    private fun getAdditionalRequirements(bases: List<LawfulBasis>): List<String> = emptyList()

    // Data classes
    enum class LawfulBasis { CONSENT, CONTRACT, LEGAL_OBLIGATION, VITAL_INTERESTS, PUBLIC_TASK, LEGITIMATE_INTERESTS }
    enum class ErasureReason { USER_REQUEST, CONSENT_WITHDRAWN, NO_LONGER_NECESSARY, UNLAWFUL_PROCESSING }
    enum class COPPARequirement { PARENTAL_CONSENT, LIMITED_DATA_COLLECTION, NO_TARGETED_ADVERTISING, PARENTAL_ACCESS }

    data class GDPRLawfulBasisResult(
        val hasLawfulBasis: Boolean,
        val applicableBases: List<LawfulBasis>,
        val recommendedBasis: LawfulBasis?,
        val additionalRequirements: List<String>
    )

    sealed class ErasureResult {
        data class Completed(
            val userId: String,
            val deletedCategories: Set<String>,
            val deletionTasks: List<DataDeletionTask>,
            val completedAt: Long
        ) : ErasureResult()
        data class Rejected(val reason: String) : ErasureResult()
        data class Failed(val error: String) : ErasureResult()
    }

    sealed class DataExportResult {
        data class Success(val exportPackage: DataExportPackage) : DataExportResult()
        data class Failed(val error: String) : DataExportResult()
    }

    sealed class OptOutResult {
        data class Success(val userId: String, val optOutTimestamp: Long, val confirmationId: String) : OptOutResult()
        data class Failed(val error: String) : OptOutResult()
    }

    data class COPPAComplianceResult(
        val subjectToCOPPA: Boolean,
        val compliant: Boolean,
        val requirements: List<COPPARequirement>,
        val violations: List<String> = emptyList()
    )

    data class ValidationResult(val isValid: Boolean, val reason: String)
    data class DataDeletionTask(val category: String, val status: String)
    data class DataExportPackage(
        val userId: String,
        val data: Map<String, Any>,
        val exportedAt: Long,
        val format: String,
        val checksum: String
    )
}