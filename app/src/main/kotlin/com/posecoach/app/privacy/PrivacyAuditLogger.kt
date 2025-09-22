package com.posecoach.app.privacy

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.gson.Gson
import java.security.MessageDigest
import android.util.Base64

/**
 * Privacy Audit Logger
 * Comprehensive logging system for privacy compliance and security monitoring
 *
 * Features:
 * - GDPR-compliant audit trails
 * - Real-time privacy violation detection
 * - Secure log storage with integrity verification
 * - Automated log rotation and cleanup
 * - Export capabilities for compliance reports
 */
class PrivacyAuditLogger(val context: Context) {

    data class AuditEvent(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long,
        val eventType: EventType,
        val description: String,
        val dataType: String? = null,
        val processingLocation: String? = null,
        val consentBasis: String? = null,
        val userAction: Boolean = false,
        val severity: Severity = Severity.INFO,
        val sessionId: String = "session-${System.currentTimeMillis()}",
        val integrityHash: String = ""
    ) {
        fun withIntegrityHash(): AuditEvent {
            val dataForHash = "$id$timestamp$eventType$description$dataType$processingLocation$consentBasis$userAction$severity$sessionId"
            val hash = "hash-${dataForHash.hashCode().toString().takeLast(8)}"
            return copy(integrityHash = hash)
        }
    }

    enum class EventType {
        CONSENT_GRANTED,
        CONSENT_WITHDRAWN,
        DATA_PROCESSED,
        DATA_UPLOADED,
        DATA_DELETED,
        PRIVACY_SETTING_CHANGED,
        COMPLIANCE_REQUEST,
        SECURITY_EVENT,
        POLICY_VIOLATION,
        ACCESS_REQUEST,
        ERASURE_REQUEST,
        PORTABILITY_REQUEST,
        SYSTEM_STARTUP,
        SYSTEM_SHUTDOWN,
        ERROR_OCCURRED
    }

    enum class Severity {
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }

    private val _auditEvents = MutableSharedFlow<AuditEvent>()
    val auditEvents: SharedFlow<AuditEvent> = _auditEvents.asSharedFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentSessionId: String = UUID.randomUUID().toString()

    // File paths
    private val auditDir = File(context.filesDir, "audit_logs")
    private val currentLogFile = File(auditDir, "privacy_audit.log")
    private val integrityFile = File(auditDir, "log_integrity.hash")

    init {
        if (!auditDir.exists()) {
            auditDir.mkdirs()
        }
        logSystemEvent("AUDIT_LOGGER_INITIALIZED", "Privacy audit logger started")
    }

    /**
     * Log consent-related events
     * 記錄同意相關事件
     */
    fun logConsentEvent(
        consentType: String,
        granted: Boolean,
        userInitiated: Boolean = true
    ) {
        val event = AuditEvent(
            timestamp = System.currentTimeMillis(),
            eventType = if (granted) EventType.CONSENT_GRANTED else EventType.CONSENT_WITHDRAWN,
            description = "Consent ${if (granted) "granted" else "withdrawn"} for $consentType",
            dataType = consentType,
            consentBasis = "explicit_consent",
            userAction = userInitiated,
            severity = Severity.INFO
        ).withIntegrityHash()

        logEvent(event)
    }

    /**
     * Log data processing activities
     * 記錄資料處理活動
     */
    fun logDataProcessing(
        dataType: String,
        processingLocation: String,
        consentBasis: String,
        description: String
    ) {
        val event = AuditEvent(
            timestamp = System.currentTimeMillis(),
            eventType = EventType.DATA_PROCESSED,
            description = description,
            dataType = dataType,
            processingLocation = processingLocation,
            consentBasis = consentBasis,
            severity = if (processingLocation == "local") Severity.INFO else Severity.WARNING
        ).withIntegrityHash()

        logEvent(event)
    }

    /**
     * Log data upload events (should only be landmarks with consent)
     * 記錄資料上傳事件（應該只有經同意的地標資料）
     */
    fun logDataUpload(dataType: String, destination: String, consentVerified: Boolean = true) {
        val severity = when {
            dataType.contains("image", ignoreCase = true) -> Severity.CRITICAL
            !consentVerified -> Severity.ERROR
            else -> Severity.WARNING
        }

        val event = AuditEvent(
            timestamp = System.currentTimeMillis(),
            eventType = EventType.DATA_UPLOADED,
            description = "Data uploaded: $dataType to $destination (consent verified: $consentVerified)",
            dataType = dataType,
            processingLocation = destination,
            consentBasis = if (consentVerified) "explicit_consent" else "NO_CONSENT",
            severity = severity
        ).withIntegrityHash()

        logEvent(event)

        // Trigger immediate security check for image uploads
        if (dataType.contains("image", ignoreCase = true)) {
            logPrivacyViolation(
                "CRITICAL: Image upload detected - This should NEVER happen per privacy policy",
                "CRITICAL"
            )
        }
    }

    /**
     * Log privacy violations with immediate escalation
     * 記錄隱私違規並立即上報
     */
    fun logPrivacyViolation(description: String, severity: String = "HIGH") {
        val event = AuditEvent(
            timestamp = System.currentTimeMillis(),
            eventType = EventType.POLICY_VIOLATION,
            description = "PRIVACY VIOLATION: $description (Severity: $severity)",
            severity = when (severity) {
                "CRITICAL" -> Severity.CRITICAL
                "HIGH" -> Severity.ERROR
                "MEDIUM" -> Severity.WARNING
                else -> Severity.INFO
            }
        ).withIntegrityHash()

        logEvent(event)

        // Also log to system for immediate attention
        Timber.e("PRIVACY VIOLATION: $description")

        // Trigger immediate security protocols for critical violations
        if (severity == "CRITICAL") {
            triggerSecurityProtocols(event)
        }
    }

    /**
     * Log GDPR compliance requests
     * 記錄 GDPR 合規請求
     */
    fun logComplianceRequest(requestType: String, status: String) {
        val event = AuditEvent(
            timestamp = System.currentTimeMillis(),
            eventType = EventType.COMPLIANCE_REQUEST,
            description = "GDPR request: $requestType - $status",
            dataType = "compliance_request",
            processingLocation = "local",
            consentBasis = "legal_obligation",
            severity = Severity.INFO
        ).withIntegrityHash()

        logEvent(event)
    }

    /**
     * Log system events
     * 記錄系統事件
     */
    fun logSystemEvent(eventType: String, description: String) {
        val event = AuditEvent(
            timestamp = System.currentTimeMillis(),
            eventType = when (eventType) {
                "STARTUP" -> EventType.SYSTEM_STARTUP
                "SHUTDOWN" -> EventType.SYSTEM_SHUTDOWN
                "ERROR" -> EventType.ERROR_OCCURRED
                else -> EventType.SECURITY_EVENT
            },
            description = description,
            severity = Severity.INFO
        ).withIntegrityHash()

        logEvent(event)
    }

    /**
     * Main event logging method
     * 主要事件記錄方法
     */
    private fun logEvent(event: AuditEvent) {
        // Emit to flow for real-time monitoring
        _auditEvents.tryEmit(event)

        // Log to Timber for debug builds
        when (event.severity) {
            Severity.DEBUG -> Timber.d("Privacy Audit [${event.eventType}]: ${event.description}")
            Severity.INFO -> Timber.i("Privacy Audit [${event.eventType}]: ${event.description}")
            Severity.WARNING -> Timber.w("Privacy Audit [${event.eventType}]: ${event.description}")
            Severity.ERROR -> Timber.e("Privacy Audit [${event.eventType}]: ${event.description}")
            Severity.CRITICAL -> Timber.wtf("Privacy Audit [${event.eventType}]: ${event.description}")
        }

        // Write to secure audit file asynchronously
        scope.launch {
            writeToAuditFile(event)
        }
    }

    /**
     * Write event to secure audit file
     * 將事件寫入安全審計檔案
     */
    private suspend fun writeToAuditFile(event: AuditEvent) {
        try {
            val logEntry = formatLogEntry(event)

            synchronized(currentLogFile) {
                currentLogFile.appendText(logEntry + "\n")
                updateIntegrityFile()
            }

            // Rotate log files if they get too large
            if (currentLogFile.length() > 5 * 1024 * 1024) { // 5MB
                rotateLogFile()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to write audit log")
        }
    }

    /**
     * Format log entry with all required information
     * 格式化包含所有必要資訊的記錄條目
     */
    private fun formatLogEntry(event: AuditEvent): String {
        return buildString {
            append(dateFormat.format(Date(event.timestamp)))
            append(" | ")
            append(event.severity.name.padEnd(8))
            append(" | ")
            append(event.eventType.name.padEnd(20))
            append(" | ")
            append(event.description)

            event.dataType?.let { append(" | DataType: $it") }
            event.processingLocation?.let { append(" | Location: $it") }
            event.consentBasis?.let { append(" | ConsentBasis: $it") }
            append(" | SessionId: ${event.sessionId}")

            if (event.userAction) {
                append(" | USER_INITIATED")
            }

            append(" | ID: ${event.id}")
            append(" | Hash: ${event.integrityHash}")
        }
    }

    /**
     * Rotate log files when they become too large
     * 當記錄檔案變得過大時進行輪換
     */
    private fun rotateLogFile() {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
            val rotatedFile = File(auditDir, "privacy_audit_$timestamp.log")

            synchronized(currentLogFile) {
                currentLogFile.renameTo(rotatedFile)
                updateIntegrityFile()
            }

            // Keep only last 10 rotated files
            val rotatedFiles = auditDir.listFiles { file ->
                file.name.startsWith("privacy_audit_") && file.name.endsWith(".log")
            }?.sortedByDescending { it.lastModified() }

            rotatedFiles?.drop(10)?.forEach { it.delete() }

            Timber.i("Audit log rotated: ${rotatedFile.name}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to rotate audit log")
        }
    }

    /**
     * Update integrity verification file
     * 更新完整性驗證檔案
     */
    private fun updateIntegrityFile() {
        try {
            val allLogFiles = auditDir.listFiles { file ->
                file.name.endsWith(".log")
            }?.toList() ?: emptyList()

            val integrityData = allLogFiles.map { file ->
                mapOf(
                    "filename" to file.name,
                    "size" to file.length(),
                    "lastModified" to file.lastModified(),
                    "hash" to calculateFileHash(file)
                )
            }

            val integrityJson = gson.toJson(integrityData)
            integrityFile.writeText(integrityJson)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update integrity file")
        }
    }

    /**
     * Export audit logs for compliance reporting
     * 匯出審計記錄以進行合規報告
     */
    fun exportAuditLogs(
        startDate: Date,
        endDate: Date,
        eventTypes: List<EventType>? = null
    ): String {
        val logs = StringBuilder()

        try {
            auditDir.listFiles { file ->
                file.name.endsWith(".log")
            }?.forEach { file ->
                file.readLines().forEach { line ->
                    if (isLineInDateRange(line, startDate, endDate) &&
                        (eventTypes == null || isLineOfEventType(line, eventTypes))) {
                        logs.appendLine(line)
                    }
                }
            }

            logComplianceRequest("EXPORT_AUDIT_LOGS", "COMPLETED")
        } catch (e: Exception) {
            Timber.e(e, "Failed to export audit logs")
            logSystemEvent("ERROR", "Failed to export audit logs: ${e.message}")
        }

        return logs.toString()
    }

    /**
     * Verify log integrity for compliance audits
     * 驗證記錄完整性以進行合規審計
     */
    fun verifyLogIntegrity(): LogIntegrityResult {
        return try {
            if (!integrityFile.exists()) {
                return LogIntegrityResult(
                    isValid = false,
                    message = "Integrity file not found"
                )
            }

            val integrityJson = integrityFile.readText()
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
            val storedIntegrity: List<Map<String, Any>> = gson.fromJson(integrityJson, type)

            val currentFiles = auditDir.listFiles { file ->
                file.name.endsWith(".log")
            }?.toList() ?: emptyList()

            // Verify each file
            for (fileData in storedIntegrity) {
                val filename = fileData["filename"] as String
                val storedHash = fileData["hash"] as String

                val currentFile = currentFiles.find { it.name == filename }
                if (currentFile == null) {
                    return LogIntegrityResult(
                        isValid = false,
                        message = "Log file missing: $filename"
                    )
                }

                val currentHash = calculateFileHash(currentFile)
                if (currentHash != storedHash) {
                    logPrivacyViolation("Log integrity violation detected: $filename")
                    return LogIntegrityResult(
                        isValid = false,
                        message = "Hash mismatch for: $filename"
                    )
                }
            }

            LogIntegrityResult(isValid = true, message = "All logs verified")
        } catch (e: Exception) {
            Timber.e(e, "Log integrity verification failed")
            LogIntegrityResult(
                isValid = false,
                message = "Verification failed: ${e.message}"
            )
        }
    }

    /**
     * Get audit statistics for privacy dashboard
     * 獲取隱私儀表板的審計統計
     */
    fun getAuditStatistics(days: Int = 7): AuditStatistics {
        val startTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        val events = mutableListOf<AuditEvent>()

        try {
            auditDir.listFiles { file ->
                file.name.endsWith(".log")
            }?.forEach { file ->
                file.readLines().forEach { line ->
                    val event = parseLogLineToEvent(line)
                    if (event != null && event.timestamp >= startTime) {
                        events.add(event)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to collect audit statistics")
        }

        return AuditStatistics(
            totalEvents = events.size,
            consentEvents = events.count { it.eventType in listOf(EventType.CONSENT_GRANTED, EventType.CONSENT_WITHDRAWN) },
            dataProcessingEvents = events.count { it.eventType == EventType.DATA_PROCESSED },
            privacyViolations = events.count { it.eventType == EventType.POLICY_VIOLATION },
            complianceRequests = events.count { it.eventType == EventType.COMPLIANCE_REQUEST },
            severityBreakdown = events.groupBy { it.severity }.mapValues { it.value.size },
            lastViolation = events.filter { it.eventType == EventType.POLICY_VIOLATION }
                .maxByOrNull { it.timestamp }?.timestamp
        )
    }

    // Helper methods

    private fun getCurrentSessionId(): String = currentSessionId

    private fun triggerSecurityProtocols(event: AuditEvent) {
        // In a real implementation, this would trigger immediate security responses
        Timber.wtf("CRITICAL PRIVACY VIOLATION - Security protocols triggered: ${event.description}")
    }

    private fun isLineInDateRange(line: String, startDate: Date, endDate: Date): Boolean {
        return try {
            val timestampStr = line.substring(0, 23) // Extract timestamp
            val timestamp = dateFormat.parse(timestampStr)
            timestamp != null && timestamp.after(startDate) && timestamp.before(endDate)
        } catch (e: Exception) {
            false
        }
    }

    private fun isLineOfEventType(line: String, eventTypes: List<EventType>): Boolean {
        return eventTypes.any { eventType ->
            line.contains("| ${eventType.name.padEnd(20)} |")
        }
    }

    private fun calculateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(file.readBytes())
        return Base64.encodeToString(hashBytes, Base64.DEFAULT)
    }

    private fun createHash(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray())
        return Base64.encodeToString(hashBytes, Base64.DEFAULT)
    }

    private fun parseLogLineToEvent(line: String): AuditEvent? {
        return try {
            // Parse log line back to AuditEvent (simplified)
            val parts = line.split(" | ")
            if (parts.size >= 4) {
                val timestamp = dateFormat.parse(parts[0])?.time ?: 0L
                val severity = Severity.valueOf(parts[1].trim())
                val eventType = EventType.valueOf(parts[2].trim())
                val description = parts[3]

                AuditEvent(
                    timestamp = timestamp,
                    eventType = eventType,
                    description = description,
                    severity = severity
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // Data classes

    data class LogIntegrityResult(
        val isValid: Boolean,
        val message: String
    )

    data class AuditStatistics(
        val totalEvents: Int,
        val consentEvents: Int,
        val dataProcessingEvents: Int,
        val privacyViolations: Int,
        val complianceRequests: Int,
        val severityBreakdown: Map<Severity, Int>,
        val lastViolation: Long?
    )
}