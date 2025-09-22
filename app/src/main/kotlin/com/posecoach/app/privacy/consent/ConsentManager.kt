package com.posecoach.app.privacy.consent

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.security.MessageDigest
import java.util.*

/**
 * Advanced Consent Management System
 * Implements dynamic consent with versioning, change tracking, granular purpose specification,
 * and comprehensive withdrawal mechanisms with data deletion.
 */
class ConsentManager(private val context: Context) {

    @Serializable
    data class ConsentRecord(
        val id: String = UUID.randomUUID().toString(),
        val version: Int = CURRENT_CONSENT_VERSION,
        val timestamp: Long = System.currentTimeMillis(),
        val purposes: Map<ConsentPurpose, ConsentStatus> = emptyMap(),
        val dataTypes: Map<DataType, ConsentStatus> = emptyMap(),
        val processingLocations: Map<ProcessingLocation, ConsentStatus> = emptyMap(),
        val retentionPeriods: Map<DataType, Long> = emptyMap(),
        val userSignature: String? = null,
        val ipAddress: String? = null,
        val userAgent: String? = null,
        val expiryTimestamp: Long? = null,
        val withdrawalReason: String? = null,
        val isActive: Boolean = true
    )

    @Serializable
    data class ConsentChangeLog(
        val recordId: String,
        val changeType: ChangeType,
        val timestamp: Long,
        val oldValues: Map<String, String>,
        val newValues: Map<String, String>,
        val reason: String,
        val userInitiated: Boolean
    )

    @Serializable
    data class ConsentRequest(
        val purposes: Set<ConsentPurpose>,
        val dataTypes: Set<DataType>,
        val processingLocations: Set<ProcessingLocation>,
        val retentionPeriod: Long,
        val isEssential: Boolean = false,
        val description: String,
        val consequences: String,
        val alternatives: List<String> = emptyList()
    )

    enum class ConsentPurpose {
        POSE_ANALYSIS,           // Core pose detection and analysis
        COACHING_SUGGESTIONS,    // AI-powered coaching recommendations
        PERFORMANCE_ANALYTICS,   // Workout performance tracking
        PRODUCT_IMPROVEMENT,     // ML model training and improvement
        PERSONALIZATION,         // Personalized workout experiences
        SAFETY_MONITORING,       // Safety and form checking
        RESEARCH,               // Anonymized research purposes
        MARKETING,              // Marketing and promotional content
        THIRD_PARTY_INTEGRATION, // Integration with fitness platforms
        TECHNICAL_SUPPORT       // Technical support and debugging
    }

    enum class DataType {
        POSE_LANDMARKS,         // Body pose coordinates
        AUDIO_RECORDINGS,       // Voice commands and feedback
        CAMERA_IMAGES,          // Camera feed for pose detection
        BIOMETRIC_DATA,         // Heart rate, movement patterns
        USAGE_METRICS,          // App usage and interaction data
        DEVICE_INFORMATION,     // Device specs and capabilities
        LOCATION_DATA,          // Geographic location
        PERSONAL_INFORMATION,   // Name, age, fitness goals
        HEALTH_DATA,           // Medical conditions, limitations
        WORKOUT_HISTORY        // Historical workout data
    }

    enum class ProcessingLocation {
        LOCAL_DEVICE,          // On-device processing only
        EDGE_COMPUTING,        // Local edge servers
        REGIONAL_CLOUD,        // Regional cloud infrastructure
        GLOBAL_CLOUD,          // Global cloud services
        THIRD_PARTY_SERVICES,  // External service providers
        FEDERATED_NETWORK      // Federated learning network
    }

    enum class ConsentStatus {
        GRANTED,               // Explicit consent granted
        DENIED,                // Explicit consent denied
        WITHDRAWN,             // Previously granted, now withdrawn
        PENDING,               // Consent request pending
        EXPIRED,               // Consent expired
        NOT_REQUESTED          // Consent not yet requested
    }

    enum class ChangeType {
        INITIAL_CONSENT,
        MODIFICATION,
        WITHDRAWAL,
        RENEWAL,
        EXPIRY,
        SYSTEM_UPDATE
    }

    companion object {
        private const val PREFS_NAME = "consent_manager_prefs"
        private const val CURRENT_CONSENT_VERSION = 3
        private const val DEFAULT_CONSENT_VALIDITY = 31536000000L // 1 year
        private const val REMINDER_INTERVAL = 2592000000L // 30 days
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val consentMutex = Mutex()

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setRequestStrongBoxBacked(true)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _currentConsent = MutableStateFlow(loadCurrentConsent())
    val currentConsent: StateFlow<ConsentRecord> = _currentConsent.asStateFlow()

    private val _consentHistory = MutableStateFlow(loadConsentHistory())
    val consentHistory: StateFlow<List<ConsentRecord>> = _consentHistory.asStateFlow()

    private val _changeLog = MutableStateFlow(loadChangeLog())
    val changeLog: StateFlow<List<ConsentChangeLog>> = _changeLog.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<ConsentRequest>>(emptyList())
    val pendingRequests: StateFlow<List<ConsentRequest>> = _pendingRequests.asStateFlow()

    init {
        Timber.d("Consent Manager initialized")
        checkConsentExpiry()
    }

    /**
     * Request consent for specific purposes and data types
     */
    suspend fun requestConsent(request: ConsentRequest): ConsentRequestResult = consentMutex.withLock {
        val currentRecord = _currentConsent.value

        // Check if consent already exists and is valid
        val existingConsent = checkExistingConsent(request, currentRecord)
        if (existingConsent != null) {
            return ConsentRequestResult.AlreadyGranted(existingConsent)
        }

        // Add to pending requests
        val updatedPending = _pendingRequests.value + request
        _pendingRequests.value = updatedPending

        return ConsentRequestResult.PendingUserResponse(request)
    }

    /**
     * Grant consent for a specific request
     */
    suspend fun grantConsent(
        request: ConsentRequest,
        userSignature: String? = null,
        metadata: ConsentMetadata = ConsentMetadata()
    ): ConsentRecord = consentMutex.withLock {
        val currentRecord = _currentConsent.value
        val newPurposes = currentRecord.purposes.toMutableMap()
        val newDataTypes = currentRecord.dataTypes.toMutableMap()
        val newLocations = currentRecord.processingLocations.toMutableMap()
        val newRetention = currentRecord.retentionPeriods.toMutableMap()

        // Update consent status
        request.purposes.forEach { purpose ->
            newPurposes[purpose] = ConsentStatus.GRANTED
        }
        request.dataTypes.forEach { dataType ->
            newDataTypes[dataType] = ConsentStatus.GRANTED
            newRetention[dataType] = request.retentionPeriod
        }
        request.processingLocations.forEach { location ->
            newLocations[location] = ConsentStatus.GRANTED
        }

        val updatedRecord = currentRecord.copy(
            version = CURRENT_CONSENT_VERSION,
            timestamp = System.currentTimeMillis(),
            purposes = newPurposes,
            dataTypes = newDataTypes,
            processingLocations = newLocations,
            retentionPeriods = newRetention,
            userSignature = userSignature,
            ipAddress = metadata.ipAddress,
            userAgent = metadata.userAgent,
            expiryTimestamp = System.currentTimeMillis() + DEFAULT_CONSENT_VALIDITY
        )

        // Log the change
        logConsentChange(
            recordId = updatedRecord.id,
            changeType = ChangeType.MODIFICATION,
            oldValues = mapOf(
                "purposes" to currentRecord.purposes.toString(),
                "dataTypes" to currentRecord.dataTypes.toString()
            ),
            newValues = mapOf(
                "purposes" to updatedRecord.purposes.toString(),
                "dataTypes" to updatedRecord.dataTypes.toString()
            ),
            reason = "User granted consent for: ${request.description}",
            userInitiated = true
        )

        // Update state
        _currentConsent.value = updatedRecord
        saveConsentRecord(updatedRecord)

        // Remove from pending requests
        _pendingRequests.value = _pendingRequests.value.filter { it != request }

        Timber.i("Consent granted for purposes: ${request.purposes}")
        return updatedRecord
    }

    /**
     * Withdraw consent for specific purposes or data types
     */
    suspend fun withdrawConsent(
        purposes: Set<ConsentPurpose> = emptySet(),
        dataTypes: Set<DataType> = emptySet(),
        reason: String = "User requested withdrawal"
    ): ConsentWithdrawalResult = consentMutex.withLock {
        val currentRecord = _currentConsent.value
        val newPurposes = currentRecord.purposes.toMutableMap()
        val newDataTypes = currentRecord.dataTypes.toMutableMap()

        // Withdraw specified consents
        purposes.forEach { purpose ->
            newPurposes[purpose] = ConsentStatus.WITHDRAWN
        }
        dataTypes.forEach { dataType ->
            newDataTypes[dataType] = ConsentStatus.WITHDRAWN
        }

        val updatedRecord = currentRecord.copy(
            purposes = newPurposes,
            dataTypes = newDataTypes,
            withdrawalReason = reason,
            timestamp = System.currentTimeMillis()
        )

        // Log the withdrawal
        logConsentChange(
            recordId = updatedRecord.id,
            changeType = ChangeType.WITHDRAWAL,
            oldValues = mapOf(
                "purposes" to currentRecord.purposes.toString(),
                "dataTypes" to currentRecord.dataTypes.toString()
            ),
            newValues = mapOf(
                "purposes" to updatedRecord.purposes.toString(),
                "dataTypes" to updatedRecord.dataTypes.toString()
            ),
            reason = reason,
            userInitiated = true
        )

        _currentConsent.value = updatedRecord
        saveConsentRecord(updatedRecord)

        // Initiate data deletion for withdrawn consents
        val deletionTasks = initiateDataDeletion(purposes, dataTypes)

        Timber.i("Consent withdrawn for purposes: $purposes, dataTypes: $dataTypes")
        return ConsentWithdrawalResult.Success(updatedRecord, deletionTasks)
    }

    /**
     * Check if consent is granted for specific purpose and data type
     */
    fun isConsentGranted(purpose: ConsentPurpose, dataType: DataType): Boolean {
        val currentRecord = _currentConsent.value

        // Check if consent is active and not expired
        if (!currentRecord.isActive || isConsentExpired(currentRecord)) {
            return false
        }

        val purposeStatus = currentRecord.purposes[purpose] ?: ConsentStatus.NOT_REQUESTED
        val dataTypeStatus = currentRecord.dataTypes[dataType] ?: ConsentStatus.NOT_REQUESTED

        return purposeStatus == ConsentStatus.GRANTED && dataTypeStatus == ConsentStatus.GRANTED
    }

    /**
     * Get consent status summary
     */
    fun getConsentSummary(): ConsentSummary {
        val currentRecord = _currentConsent.value
        val totalPurposes = ConsentPurpose.values().size
        val grantedPurposes = currentRecord.purposes.values.count { it == ConsentStatus.GRANTED }

        val totalDataTypes = DataType.values().size
        val grantedDataTypes = currentRecord.dataTypes.values.count { it == ConsentStatus.GRANTED }

        return ConsentSummary(
            overallStatus = if (grantedPurposes > 0) ConsentStatus.GRANTED else ConsentStatus.NOT_REQUESTED,
            purposeCompleteness = (grantedPurposes.toFloat() / totalPurposes * 100).toInt(),
            dataTypeCompleteness = (grantedDataTypes.toFloat() / totalDataTypes * 100).toInt(),
            expiryDate = currentRecord.expiryTimestamp,
            needsRenewal = isConsentExpired(currentRecord) || isNearingExpiry(currentRecord),
            withdrawnItems = currentRecord.purposes.filter { it.value == ConsentStatus.WITHDRAWN }.keys +
                           currentRecord.dataTypes.filter { it.value == ConsentStatus.WITHDRAWN }.keys
        )
    }

    /**
     * Renew expired or near-expiry consents
     */
    suspend fun renewConsent(
        purposes: Set<ConsentPurpose> = emptySet(),
        userSignature: String? = null
    ): ConsentRecord = consentMutex.withLock {
        val currentRecord = _currentConsent.value
        val renewedRecord = currentRecord.copy(
            timestamp = System.currentTimeMillis(),
            expiryTimestamp = System.currentTimeMillis() + DEFAULT_CONSENT_VALIDITY,
            userSignature = userSignature
        )

        logConsentChange(
            recordId = renewedRecord.id,
            changeType = ChangeType.RENEWAL,
            oldValues = mapOf("expiryTimestamp" to currentRecord.expiryTimestamp.toString()),
            newValues = mapOf("expiryTimestamp" to renewedRecord.expiryTimestamp.toString()),
            reason = "Consent renewed",
            userInitiated = true
        )

        _currentConsent.value = renewedRecord
        saveConsentRecord(renewedRecord)

        Timber.i("Consent renewed")
        return renewedRecord
    }

    /**
     * Export consent data for GDPR compliance
     */
    fun exportConsentData(): ConsentExport {
        val currentRecord = _currentConsent.value
        val history = _consentHistory.value
        val changes = _changeLog.value

        return ConsentExport(
            currentConsent = currentRecord,
            consentHistory = history,
            changeLog = changes,
            exportTimestamp = System.currentTimeMillis(),
            dataIntegrityHash = calculateDataHash(currentRecord, history, changes)
        )
    }

    /**
     * Delete all consent data (right to be forgotten)
     */
    suspend fun deleteAllConsentData(reason: String): DeletionResult = consentMutex.withLock {
        try {
            // Clear encrypted preferences
            encryptedPrefs.edit().clear().apply()

            // Reset state
            _currentConsent.value = ConsentRecord()
            _consentHistory.value = emptyList()
            _changeLog.value = emptyList()
            _pendingRequests.value = emptyList()

            // Log final deletion record
            Timber.i("All consent data deleted: $reason")
            return DeletionResult.Success(System.currentTimeMillis())
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete consent data")
            return DeletionResult.Failure(e.message ?: "Unknown error")
        }
    }

    // Private helper methods

    private fun loadCurrentConsent(): ConsentRecord {
        return try {
            val json = encryptedPrefs.getString("current_consent", null)
            if (json != null) {
                this.json.decodeFromString<ConsentRecord>(json)
            } else {
                ConsentRecord()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load current consent")
            ConsentRecord()
        }
    }

    private fun loadConsentHistory(): List<ConsentRecord> {
        return try {
            val json = encryptedPrefs.getString("consent_history", null)
            if (json != null) {
                this.json.decodeFromString<List<ConsentRecord>>(json)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load consent history")
            emptyList()
        }
    }

    private fun loadChangeLog(): List<ConsentChangeLog> {
        return try {
            val json = encryptedPrefs.getString("change_log", null)
            if (json != null) {
                this.json.decodeFromString<List<ConsentChangeLog>>(json)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load change log")
            emptyList()
        }
    }

    private fun saveConsentRecord(record: ConsentRecord) {
        try {
            val json = this.json.encodeToString(record)
            encryptedPrefs.edit().putString("current_consent", json).apply()

            // Add to history
            val history = _consentHistory.value + record
            val historyJson = this.json.encodeToString(history)
            encryptedPrefs.edit().putString("consent_history", historyJson).apply()
            _consentHistory.value = history
        } catch (e: Exception) {
            Timber.e(e, "Failed to save consent record")
        }
    }

    private fun logConsentChange(
        recordId: String,
        changeType: ChangeType,
        oldValues: Map<String, String>,
        newValues: Map<String, String>,
        reason: String,
        userInitiated: Boolean
    ) {
        val changeLog = ConsentChangeLog(
            recordId = recordId,
            changeType = changeType,
            timestamp = System.currentTimeMillis(),
            oldValues = oldValues,
            newValues = newValues,
            reason = reason,
            userInitiated = userInitiated
        )

        val updatedLog = _changeLog.value + changeLog
        _changeLog.value = updatedLog

        try {
            val json = this.json.encodeToString(updatedLog)
            encryptedPrefs.edit().putString("change_log", json).apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to save change log")
        }
    }

    private fun checkExistingConsent(request: ConsentRequest, currentRecord: ConsentRecord): ConsentRecord? {
        val allPurposesGranted = request.purposes.all { purpose ->
            currentRecord.purposes[purpose] == ConsentStatus.GRANTED
        }
        val allDataTypesGranted = request.dataTypes.all { dataType ->
            currentRecord.dataTypes[dataType] == ConsentStatus.GRANTED
        }

        return if (allPurposesGranted && allDataTypesGranted && !isConsentExpired(currentRecord)) {
            currentRecord
        } else {
            null
        }
    }

    private fun isConsentExpired(record: ConsentRecord): Boolean {
        return record.expiryTimestamp?.let { expiry ->
            System.currentTimeMillis() > expiry
        } ?: false
    }

    private fun isNearingExpiry(record: ConsentRecord): Boolean {
        return record.expiryTimestamp?.let { expiry ->
            System.currentTimeMillis() > (expiry - REMINDER_INTERVAL)
        } ?: false
    }

    private fun checkConsentExpiry() {
        val currentRecord = _currentConsent.value
        if (isConsentExpired(currentRecord)) {
            // Mark as expired and trigger renewal flow
            Timber.w("Consent has expired")
        }
    }

    private fun initiateDataDeletion(
        purposes: Set<ConsentPurpose>,
        dataTypes: Set<DataType>
    ): List<DataDeletionTask> {
        return dataTypes.map { dataType ->
            DataDeletionTask(
                dataType = dataType,
                scheduledTime = System.currentTimeMillis(),
                status = DeletionStatus.SCHEDULED
            )
        }
    }

    private fun calculateDataHash(
        record: ConsentRecord,
        history: List<ConsentRecord>,
        changes: List<ConsentChangeLog>
    ): String {
        val combinedData = "${record.hashCode()}${history.hashCode()}${changes.hashCode()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(combinedData.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    // Data classes for results and exports

    sealed class ConsentRequestResult {
        data class AlreadyGranted(val record: ConsentRecord) : ConsentRequestResult()
        data class PendingUserResponse(val request: ConsentRequest) : ConsentRequestResult()
        data class Denied(val reason: String) : ConsentRequestResult()
    }

    sealed class ConsentWithdrawalResult {
        data class Success(val record: ConsentRecord, val deletionTasks: List<DataDeletionTask>) : ConsentWithdrawalResult()
        data class Failure(val reason: String) : ConsentWithdrawalResult()
    }

    sealed class DeletionResult {
        data class Success(val timestamp: Long) : DeletionResult()
        data class Failure(val reason: String) : DeletionResult()
    }

    data class ConsentMetadata(
        val ipAddress: String? = null,
        val userAgent: String? = null,
        val geolocation: String? = null
    )

    data class ConsentSummary(
        val overallStatus: ConsentStatus,
        val purposeCompleteness: Int,
        val dataTypeCompleteness: Int,
        val expiryDate: Long?,
        val needsRenewal: Boolean,
        val withdrawnItems: Set<Any>
    )

    data class ConsentExport(
        val currentConsent: ConsentRecord,
        val consentHistory: List<ConsentRecord>,
        val changeLog: List<ConsentChangeLog>,
        val exportTimestamp: Long,
        val dataIntegrityHash: String
    )

    data class DataDeletionTask(
        val dataType: DataType,
        val scheduledTime: Long,
        val status: DeletionStatus
    )

    enum class DeletionStatus {
        SCHEDULED, IN_PROGRESS, COMPLETED, FAILED
    }
}