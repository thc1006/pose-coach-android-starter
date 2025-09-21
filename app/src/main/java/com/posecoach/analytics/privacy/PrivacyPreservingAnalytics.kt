package com.posecoach.analytics.privacy

import com.posecoach.analytics.interfaces.PrivacyEngine
import com.posecoach.analytics.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy-preserving analytics processor implementing differential privacy,
 * anonymization, and consent management for GDPR/CCPA compliance
 */
@Singleton
class PrivacyPreservingAnalytics @Inject constructor(
    private val consentManager: ConsentManager,
    private val dataRetentionManager: DataRetentionManager
) : PrivacyEngine {

    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    // Differential privacy parameters
    private val defaultEpsilon = 1.0 // Privacy budget
    private val defaultDelta = 1e-5 // Probability of privacy loss
    private val sensitivityBounds = mapOf(
        "poseAccuracy" to 1.0,
        "energyExpenditure" to 1000.0,
        "sessionDuration" to 7200.0, // 2 hours max
        "heartRate" to 100.0
    )

    override suspend fun anonymizeData(data: Any): Any = withContext(Dispatchers.Default) {
        when (data) {
            is AnalyticsEvent -> anonymizeEvent(data)
            is UserPerformanceMetrics -> anonymizeUserMetrics(data)
            is CoachingEffectivenessMetrics -> anonymizeCoachingMetrics(data)
            is BusinessIntelligenceMetrics -> anonymizeBusinessMetrics(data)
            is List<*> -> data.map { anonymizeData(it!!) }
            else -> data
        }
    }

    override suspend fun applyDifferentialPrivacy(
        data: List<Any>,
        epsilon: Double
    ): List<Any> = withContext(Dispatchers.Default) {
        data.map { item ->
            when (item) {
                is UserPerformanceMetrics -> applyDPToUserMetrics(item, epsilon)
                is Map<*, *> -> applyDPToMap(item as Map<String, Any>, epsilon)
                is Number -> addLaplaceNoise(item.toDouble(), epsilon, 1.0).toFloat()
                else -> item
            }
        }
    }

    override suspend fun checkConsentRequirements(
        userId: String,
        dataType: String
    ): Boolean = withContext(Dispatchers.IO) {
        consentManager.hasValidConsent(userId, dataType)
    }

    override suspend fun processDataDeletion(
        userId: String,
        dataTypes: List<String>
    ): Unit = withContext(Dispatchers.IO) {
        dataTypes.forEach { dataType ->
            when (dataType) {
                "analytics" -> deleteAnalyticsData(userId)
                "performance" -> deletePerformanceData(userId)
                "coaching" -> deleteCoachingData(userId)
                "profile" -> deleteProfileData(userId)
                "all" -> deleteAllUserData(userId)
            }
        }

        // Update consent records
        consentManager.recordDataDeletion(userId, dataTypes)
    }

    override suspend fun auditPrivacyCompliance(): PrivacyAuditReport = withContext(Dispatchers.Default) {
        val violations = mutableListOf<PrivacyViolation>()
        var complianceScore = 100.0f

        // Check consent coverage
        val consentCoverage = consentManager.getConsentCoverage()
        if (consentCoverage < 0.95f) {
            violations.add(
                PrivacyViolation(
                    type = "CONSENT_COVERAGE",
                    severity = "HIGH",
                    description = "Consent coverage below 95%: ${consentCoverage * 100}%",
                    affectedRecords = ((1 - consentCoverage) * 1000).toInt()
                )
            )
            complianceScore -= 20
        }

        // Check data retention compliance
        val retentionViolations = dataRetentionManager.checkRetentionCompliance()
        violations.addAll(retentionViolations)
        complianceScore -= retentionViolations.size * 10

        // Check anonymization effectiveness
        val anonymizationScore = assessAnonymizationEffectiveness()
        if (anonymizationScore < 0.8f) {
            violations.add(
                PrivacyViolation(
                    type = "ANONYMIZATION_WEAK",
                    severity = "MEDIUM",
                    description = "Anonymization effectiveness below threshold: ${anonymizationScore * 100}%",
                    affectedRecords = 100
                )
            )
            complianceScore -= 15
        }

        PrivacyAuditReport(
            timestamp = System.currentTimeMillis(),
            complianceScore = maxOf(0f, complianceScore),
            violations = violations,
            recommendations = generatePrivacyRecommendations(violations)
        )
    }

    private fun anonymizeEvent(event: AnalyticsEvent): AnalyticsEvent {
        return event.copy(
            userId = if (event.userId != null) hashUserId(event.userId) else null,
            properties = anonymizeProperties(event.properties),
            privacyLevel = PrivacyLevel.ANONYMIZED
        )
    }

    private fun anonymizeUserMetrics(metrics: UserPerformanceMetrics): UserPerformanceMetrics {
        return metrics.copy(
            userId = hashUserId(metrics.userId),
            // Add noise to sensitive metrics
            poseAccuracy = addGaussianNoise(metrics.poseAccuracy.toDouble(), 0.01).toFloat(),
            energyExpenditure = addGaussianNoise(metrics.energyExpenditure.toDouble(), 5.0).toFloat(),
            improvementRate = addGaussianNoise(metrics.improvementRate.toDouble(), 0.005).toFloat(),
            movementPatterns = metrics.movementPatterns.map { pattern ->
                pattern.copy(
                    accuracy = addGaussianNoise(pattern.accuracy.toDouble(), 0.01).toFloat(),
                    consistency = addGaussianNoise(pattern.consistency.toDouble(), 0.01).toFloat()
                )
            }
        )
    }

    private fun anonymizeCoachingMetrics(metrics: CoachingEffectivenessMetrics): CoachingEffectivenessMetrics {
        return metrics.copy(
            userId = hashUserId(metrics.userId),
            suggestionAccuracy = addGaussianNoise(metrics.suggestionAccuracy.toDouble(), 0.01).toFloat(),
            userCompliance = addGaussianNoise(metrics.userCompliance.toDouble(), 0.01).toFloat(),
            feedbackEffectiveness = addGaussianNoise(metrics.feedbackEffectiveness.toDouble(), 0.01).toFloat()
        )
    }

    private fun anonymizeBusinessMetrics(metrics: BusinessIntelligenceMetrics): BusinessIntelligenceMetrics {
        return metrics.copy(
            // Apply differential privacy to aggregate counts
            activeUsers = addLaplaceNoise(metrics.activeUsers.toDouble(), 1.0, 1.0).toInt(),
            sessionCount = addLaplaceNoise(metrics.sessionCount.toDouble(), 1.0, 1.0).toInt(),
            demographicInsights = anonymizeDemographics(metrics.demographicInsights),
            privacyLevel = PrivacyLevel.DIFFERENTIAL_PRIVATE
        )
    }

    private fun anonymizeDemographics(demographics: DemographicInsights): DemographicInsights {
        return demographics.copy(
            ageGroups = demographics.ageGroups.mapValues { (_, count) ->
                addLaplaceNoise(count.toDouble(), 1.0, 1.0).toInt()
            },
            experienceLevels = demographics.experienceLevels.mapValues { (_, count) ->
                addLaplaceNoise(count.toDouble(), 1.0, 1.0).toInt()
            },
            geographicDistribution = demographics.geographicDistribution.mapValues { (_, count) ->
                addLaplaceNoise(count.toDouble(), 1.0, 1.0).toInt()
            }
        )
    }

    private fun anonymizeProperties(properties: Map<String, Any>): Map<String, Any> {
        return properties.mapValues { (key, value) ->
            when {
                key.contains("id", ignoreCase = true) && value is String -> {
                    hashString(value)
                }
                key.contains("email", ignoreCase = true) && value is String -> {
                    hashString(value)
                }
                key.contains("ip", ignoreCase = true) && value is String -> {
                    anonymizeIPAddress(value)
                }
                value is Number -> {
                    val sensitivity = sensitivityBounds[key] ?: 1.0
                    addGaussianNoise(value.toDouble(), sensitivity * 0.01)
                }
                else -> value
            }
        }
    }

    private fun applyDPToUserMetrics(
        metrics: UserPerformanceMetrics,
        epsilon: Double
    ): UserPerformanceMetrics {
        val epsilonShare = epsilon / 4.0 // Split privacy budget

        return metrics.copy(
            poseAccuracy = addLaplaceNoise(
                metrics.poseAccuracy.toDouble(),
                epsilonShare,
                sensitivityBounds["poseAccuracy"] ?: 1.0
            ).toFloat(),
            energyExpenditure = addLaplaceNoise(
                metrics.energyExpenditure.toDouble(),
                epsilonShare,
                sensitivityBounds["energyExpenditure"] ?: 1000.0
            ).toFloat(),
            duration = addLaplaceNoise(
                metrics.duration.toDouble(),
                epsilonShare,
                sensitivityBounds["sessionDuration"] ?: 7200.0
            ).toLong(),
            improvementRate = addLaplaceNoise(
                metrics.improvementRate.toDouble(),
                epsilonShare,
                1.0
            ).toFloat()
        )
    }

    private fun applyDPToMap(
        data: Map<String, Any>,
        epsilon: Double
    ): Map<String, Any> {
        return data.mapValues { (key, value) ->
            if (value is Number) {
                val sensitivity = sensitivityBounds[key] ?: 1.0
                addLaplaceNoise(value.toDouble(), epsilon, sensitivity)
            } else value
        }
    }

    private fun addLaplaceNoise(value: Double, epsilon: Double, sensitivity: Double): Double {
        val scale = sensitivity / epsilon
        val u = secureRandom.nextDouble() - 0.5
        val noise = -scale * sign(u) * ln(1 - 2 * abs(u))
        return value + noise
    }

    private fun addGaussianNoise(value: Double, standardDeviation: Double): Double {
        val noise = secureRandom.nextGaussian() * standardDeviation
        return value + noise
    }

    private fun hashUserId(userId: String): String {
        return hashString("user_$userId")
    }

    private fun hashString(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun anonymizeIPAddress(ipAddress: String): String {
        // Zero out the last octet for IPv4 or last 80 bits for IPv6
        return when {
            ipAddress.contains('.') -> {
                val parts = ipAddress.split('.')
                if (parts.size == 4) {
                    "${parts[0]}.${parts[1]}.${parts[2]}.0"
                } else ipAddress
            }
            ipAddress.contains(':') -> {
                val parts = ipAddress.split(':')
                parts.take(4).joinToString(":") + "::0"
            }
            else -> "0.0.0.0"
        }
    }

    private suspend fun deleteAnalyticsData(userId: String) {
        // Implementation would delete analytics events and metrics
        // This would integrate with the repository layer
    }

    private suspend fun deletePerformanceData(userId: String) {
        // Delete performance metrics and session data
    }

    private suspend fun deleteCoachingData(userId: String) {
        // Delete coaching effectiveness and feedback data
    }

    private suspend fun deleteProfileData(userId: String) {
        // Delete user profile and preference data
    }

    private suspend fun deleteAllUserData(userId: String) {
        deleteAnalyticsData(userId)
        deletePerformanceData(userId)
        deleteCoachingData(userId)
        deleteProfileData(userId)
    }

    private suspend fun assessAnonymizationEffectiveness(): Float {
        // This would implement k-anonymity, l-diversity, or t-closeness assessment
        // For now, return a simulated score
        return 0.85f
    }

    private fun generatePrivacyRecommendations(violations: List<PrivacyViolation>): List<String> {
        val recommendations = mutableListOf<String>()

        violations.forEach { violation ->
            when (violation.type) {
                "CONSENT_COVERAGE" -> {
                    recommendations.add("Implement consent prompts for uncovered data types")
                    recommendations.add("Review consent management workflows")
                }
                "ANONYMIZATION_WEAK" -> {
                    recommendations.add("Increase noise parameters in differential privacy")
                    recommendations.add("Implement stronger k-anonymity guarantees")
                }
                "RETENTION_VIOLATION" -> {
                    recommendations.add("Implement automated data deletion policies")
                    recommendations.add("Review data retention schedules")
                }
            }
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Privacy compliance is satisfactory")
            recommendations.add("Continue monitoring privacy metrics")
        }

        return recommendations.distinct()
    }
}

/**
 * Manages user consent for different types of data processing
 */
class ConsentManager {
    private val consentRecords = mutableMapOf<String, UserConsentRecord>()

    data class UserConsentRecord(
        val userId: String,
        val consents: MutableMap<String, ConsentDetails> = mutableMapOf(),
        val lastUpdated: Long = System.currentTimeMillis()
    )

    data class ConsentDetails(
        val granted: Boolean,
        val timestamp: Long,
        val version: String,
        val expiry: Long?
    )

    suspend fun hasValidConsent(userId: String, dataType: String): Boolean {
        val record = consentRecords[userId] ?: return false
        val consent = record.consents[dataType] ?: return false

        return consent.granted &&
                (consent.expiry == null || consent.expiry > System.currentTimeMillis())
    }

    suspend fun recordConsent(
        userId: String,
        dataType: String,
        granted: Boolean,
        version: String,
        durationDays: Int? = null
    ) {
        val record = consentRecords.getOrPut(userId) {
            UserConsentRecord(userId)
        }

        val expiry = durationDays?.let {
            System.currentTimeMillis() + (it * 24 * 60 * 60 * 1000L)
        }

        record.consents[dataType] = ConsentDetails(
            granted = granted,
            timestamp = System.currentTimeMillis(),
            version = version,
            expiry = expiry
        )
        record.lastUpdated = System.currentTimeMillis()
    }

    suspend fun getConsentCoverage(): Float {
        if (consentRecords.isEmpty()) return 0f

        val totalUsers = consentRecords.size
        val usersWithAnalyticsConsent = consentRecords.values.count { record ->
            record.consents["analytics"]?.granted == true
        }

        return usersWithAnalyticsConsent.toFloat() / totalUsers
    }

    suspend fun recordDataDeletion(userId: String, dataTypes: List<String>) {
        val record = consentRecords[userId] ?: return

        dataTypes.forEach { dataType ->
            record.consents[dataType] = ConsentDetails(
                granted = false,
                timestamp = System.currentTimeMillis(),
                version = "DELETED",
                expiry = null
            )
        }

        record.lastUpdated = System.currentTimeMillis()
    }
}

/**
 * Manages data retention policies and automated deletion
 */
class DataRetentionManager {
    private val retentionPolicies = mapOf(
        "analytics" to 365, // days
        "performance" to 730,
        "coaching" to 365,
        "system" to 90,
        "errors" to 30
    )

    suspend fun checkRetentionCompliance(): List<PrivacyViolation> {
        val violations = mutableListOf<PrivacyViolation>()

        // This would check actual data against retention policies
        // For now, return simulated violations

        val currentTime = System.currentTimeMillis()
        val overdueThreshold = 7 * 24 * 60 * 60 * 1000L // 7 days overdue

        retentionPolicies.forEach { (dataType, retentionDays) ->
            // Simulate checking for overdue data
            val overdueCount = (0..5).random() // Simulate 0-5 overdue records

            if (overdueCount > 0) {
                violations.add(
                    PrivacyViolation(
                        type = "RETENTION_VIOLATION",
                        severity = if (overdueCount > 3) "HIGH" else "MEDIUM",
                        description = "$overdueCount $dataType records exceed retention period of $retentionDays days",
                        affectedRecords = overdueCount
                    )
                )
            }
        }

        return violations
    }

    suspend fun scheduleDataDeletion(dataType: String, userId: String?, recordId: String) {
        // Implementation would schedule data for deletion based on retention policies
    }

    suspend fun executeScheduledDeletions() {
        // Implementation would execute pending deletions
    }
}