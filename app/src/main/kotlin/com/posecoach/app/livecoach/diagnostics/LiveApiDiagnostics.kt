package com.posecoach.app.livecoach.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import com.posecoach.app.BuildConfig
import com.posecoach.app.livecoach.LiveCoachManager
import com.posecoach.app.livecoach.config.LiveApiKeyManager
import com.posecoach.app.livecoach.models.ConnectionState
import com.posecoach.app.privacy.EnhancedPrivacyManager
import timber.log.Timber

/**
 * Diagnostic utility for Gemini Live API troubleshooting
 *
 * Usage:
 * ```kotlin
 * val diagnostics = LiveApiDiagnostics(context, liveCoachManager)
 * diagnostics.runFullDiagnostics()
 * val report = diagnostics.generateReport()
 * ```
 */
class LiveApiDiagnostics(
    private val context: Context,
    private val liveCoachManager: LiveCoachManager? = null
) {

    private val apiKeyManager = LiveApiKeyManager(context)
    private val privacyManager = EnhancedPrivacyManager(context)

    data class DiagnosticResult(
        val category: String,
        val check: String,
        val status: Status,
        val message: String,
        val solution: String? = null
    ) {
        enum class Status {
            PASS,     // ✅
            WARNING,  // ⚠️
            FAIL      // ❌
        }

        fun getIcon(): String = when (status) {
            Status.PASS -> "✅"
            Status.WARNING -> "⚠️"
            Status.FAIL -> "❌"
        }
    }

    private val results = mutableListOf<DiagnosticResult>()

    /**
     * Run all diagnostic checks
     */
    fun runFullDiagnostics(): List<DiagnosticResult> {
        results.clear()

        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Timber.d("🔍 Running Gemini Live API Diagnostics")
        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // 1. API Key Checks
        checkApiKeyConfiguration()
        checkApiKeyFormat()

        // 2. Permission Checks
        checkAudioPermission()
        checkInternetPermission()
        checkNetworkStatePermission()

        // 3. Network Checks
        checkNetworkConnectivity()
        checkInternetAccess()

        // 4. Privacy Settings
        checkOfflineMode()
        checkAudioUploadPermission()
        checkPrivacyLevel()

        // 5. Live Coach State
        liveCoachManager?.let {
            checkLiveCoachConnection()
            checkRecordingState()
            checkSessionHealth()
        }

        // 6. Build Configuration
        checkBuildConfiguration()

        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Timber.d("✅ Diagnostics Complete: ${results.count { it.status == DiagnosticResult.Status.PASS }} passed, " +
                 "${results.count { it.status == DiagnosticResult.Status.WARNING }} warnings, " +
                 "${results.count { it.status == DiagnosticResult.Status.FAIL }} failed")
        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        return results
    }

    /**
     * Generate a human-readable diagnostic report
     */
    fun generateReport(): String {
        return buildString {
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📋 Gemini Live API Diagnostic Report")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()

            val grouped = results.groupBy { it.category }

            grouped.forEach { (category, checks) ->
                appendLine("## $category")
                appendLine()

                checks.forEach { result ->
                    appendLine("${result.getIcon()} ${result.check}")
                    appendLine("   Status: ${result.status}")
                    appendLine("   Message: ${result.message}")
                    result.solution?.let {
                        appendLine("   💡 Solution: $it")
                    }
                    appendLine()
                }
            }

            // Summary
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📊 Summary")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("✅ Passed: ${results.count { it.status == DiagnosticResult.Status.PASS }}")
            appendLine("⚠️ Warnings: ${results.count { it.status == DiagnosticResult.Status.WARNING }}")
            appendLine("❌ Failed: ${results.count { it.status == DiagnosticResult.Status.FAIL }}")
            appendLine()

            // Critical Issues
            val failures = results.filter { it.status == DiagnosticResult.Status.FAIL }
            if (failures.isNotEmpty()) {
                appendLine("🔴 Critical Issues to Fix:")
                failures.forEach { result ->
                    appendLine("  • ${result.check}: ${result.message}")
                    result.solution?.let { appendLine("    → $it") }
                }
            } else {
                appendLine("✅ No critical issues found!")
            }

            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
    }

    // ==================== Diagnostic Checks ====================

    private fun checkApiKeyConfiguration() {
        val apiKey = apiKeyManager.getApiKey()
        val result = when {
            apiKey.isEmpty() -> DiagnosticResult(
                category = "1. API Configuration",
                check = "API Key Present",
                status = DiagnosticResult.Status.FAIL,
                message = "No API key configured",
                solution = "Add 'gemini.api.key=YOUR_KEY' to local.properties"
            )
            else -> DiagnosticResult(
                category = "1. API Configuration",
                check = "API Key Present",
                status = DiagnosticResult.Status.PASS,
                message = "API key found: ${apiKeyManager.getObfuscatedApiKey()}"
            )
        }

        results.add(result)
        Timber.d("${result.getIcon()} API Key: ${result.message}")
    }

    private fun checkApiKeyFormat() {
        val isValid = apiKeyManager.hasValidApiKey()
        val apiKey = apiKeyManager.getApiKey()

        val result = when {
            apiKey.isEmpty() -> return // Already reported in previous check
            !apiKey.startsWith("AIza") -> DiagnosticResult(
                category = "1. API Configuration",
                check = "API Key Format",
                status = DiagnosticResult.Status.FAIL,
                message = "Invalid format: should start with 'AIza'",
                solution = "Get a valid API key from https://makersuite.google.com/app/apikey"
            )
            apiKey.length < 35 -> DiagnosticResult(
                category = "1. API Configuration",
                check = "API Key Format",
                status = DiagnosticResult.Status.FAIL,
                message = "Invalid length: ${apiKey.length} chars (should be ≥35)",
                solution = "Verify the complete API key was copied"
            )
            isValid -> DiagnosticResult(
                category = "1. API Configuration",
                check = "API Key Format",
                status = DiagnosticResult.Status.PASS,
                message = "Format valid: ${apiKey.length} chars, starts with 'AIza'"
            )
            else -> DiagnosticResult(
                category = "1. API Configuration",
                check = "API Key Format",
                status = DiagnosticResult.Status.FAIL,
                message = "Format validation failed",
                solution = "Check for invalid characters in API key"
            )
        }

        results.add(result)
        Timber.d("${result.getIcon()} API Key Format: ${result.message}")
    }

    private fun checkAudioPermission() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val result = if (hasPermission) {
            DiagnosticResult(
                category = "2. Permissions",
                check = "Audio Recording Permission",
                status = DiagnosticResult.Status.PASS,
                message = "RECORD_AUDIO permission granted"
            )
        } else {
            DiagnosticResult(
                category = "2. Permissions",
                check = "Audio Recording Permission",
                status = DiagnosticResult.Status.FAIL,
                message = "RECORD_AUDIO permission not granted",
                solution = "Request permission: ActivityCompat.requestPermissions(...)"
            )
        }

        results.add(result)
        Timber.d("${result.getIcon()} Audio Permission: ${result.message}")
    }

    private fun checkInternetPermission() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED

        val result = if (hasPermission) {
            DiagnosticResult(
                category = "2. Permissions",
                check = "Internet Permission",
                status = DiagnosticResult.Status.PASS,
                message = "INTERNET permission granted"
            )
        } else {
            DiagnosticResult(
                category = "2. Permissions",
                check = "Internet Permission",
                status = DiagnosticResult.Status.FAIL,
                message = "INTERNET permission not granted",
                solution = "Add <uses-permission android:name=\"android.permission.INTERNET\"/> to AndroidManifest.xml"
            )
        }

        results.add(result)
        Timber.d("${result.getIcon()} Internet Permission: ${result.message}")
    }

    private fun checkNetworkStatePermission() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_NETWORK_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val result = if (hasPermission) {
            DiagnosticResult(
                category = "2. Permissions",
                check = "Network State Permission",
                status = DiagnosticResult.Status.PASS,
                message = "ACCESS_NETWORK_STATE permission granted"
            )
        } else {
            DiagnosticResult(
                category = "2. Permissions",
                check = "Network State Permission",
                status = DiagnosticResult.Status.WARNING,
                message = "ACCESS_NETWORK_STATE permission not granted",
                solution = "Add <uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\"/> to AndroidManifest.xml"
            )
        }

        results.add(result)
        Timber.d("${result.getIcon()} Network State Permission: ${result.message}")
    }

    private fun checkNetworkConnectivity() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val result = if (cm == null) {
            DiagnosticResult(
                category = "3. Network",
                check = "Network Connectivity",
                status = DiagnosticResult.Status.FAIL,
                message = "ConnectivityManager not available"
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork
            val capabilities = network?.let { cm.getNetworkCapabilities(it) }

            val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

            when {
                !hasInternet -> DiagnosticResult(
                    category = "3. Network",
                    check = "Network Connectivity",
                    status = DiagnosticResult.Status.FAIL,
                    message = "No network available",
                    solution = "Connect to WiFi or mobile data"
                )
                !isConnected -> DiagnosticResult(
                    category = "3. Network",
                    check = "Network Connectivity",
                    status = DiagnosticResult.Status.WARNING,
                    message = "Network not validated (no internet access)",
                    solution = "Check internet connection"
                )
                else -> DiagnosticResult(
                    category = "3. Network",
                    check = "Network Connectivity",
                    status = DiagnosticResult.Status.PASS,
                    message = "Network connected and validated"
                )
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = cm.activeNetworkInfo

            if (networkInfo?.isConnected == true) {
                DiagnosticResult(
                    category = "3. Network",
                    check = "Network Connectivity",
                    status = DiagnosticResult.Status.PASS,
                    message = "Network connected"
                )
            } else {
                DiagnosticResult(
                    category = "3. Network",
                    check = "Network Connectivity",
                    status = DiagnosticResult.Status.FAIL,
                    message = "No network connection",
                    solution = "Connect to WiFi or mobile data"
                )
            }
        }

        results.add(result)
        Timber.d("${result.getIcon()} Network: ${result.message}")
    }

    private fun checkInternetAccess() {
        // This is a basic check - actual internet access requires network request
        val result = DiagnosticResult(
            category = "3. Network",
            check = "Internet Access",
            status = DiagnosticResult.Status.PASS,
            message = "Internet access check requires network request (not performed)"
        )

        results.add(result)
    }

    private fun checkOfflineMode() {
        val isOffline = privacyManager.isOfflineModeEnabled()

        val result = if (isOffline) {
            DiagnosticResult(
                category = "4. Privacy Settings",
                check = "Offline Mode",
                status = DiagnosticResult.Status.FAIL,
                message = "Offline mode is enabled - Live API blocked",
                solution = "Disable offline mode: privacyManager.setOfflineMode(false)"
            )
        } else {
            DiagnosticResult(
                category = "4. Privacy Settings",
                check = "Offline Mode",
                status = DiagnosticResult.Status.PASS,
                message = "Offline mode disabled - Live API allowed"
            )
        }

        results.add(result)
        Timber.d("${result.getIcon()} Offline Mode: ${result.message}")
    }

    private fun checkAudioUploadPermission() {
        val isAllowed = privacyManager.isAudioUploadAllowed()

        val result = if (isAllowed) {
            DiagnosticResult(
                category = "4. Privacy Settings",
                check = "Audio Upload Permission",
                status = DiagnosticResult.Status.PASS,
                message = "Audio upload allowed"
            )
        } else {
            DiagnosticResult(
                category = "4. Privacy Settings",
                check = "Audio Upload Permission",
                status = DiagnosticResult.Status.FAIL,
                message = "Audio upload blocked by privacy settings",
                solution = "Enable audio upload: privacyManager.setAudioUploadAllowed(true)"
            )
        }

        results.add(result)
        Timber.d("${result.getIcon()} Audio Upload: ${result.message}")
    }

    private fun checkPrivacyLevel() {
        val level = privacyManager.currentPrivacyLevel.value

        val result = DiagnosticResult(
            category = "4. Privacy Settings",
            check = "Privacy Level",
            status = DiagnosticResult.Status.PASS,
            message = "Current privacy level: ${level.name}"
        )

        results.add(result)
        Timber.d("${result.getIcon()} Privacy Level: ${result.message}")
    }

    private fun checkLiveCoachConnection() {
        val state = liveCoachManager?.getConnectionState() ?: return

        val result = when (state) {
            ConnectionState.CONNECTED -> DiagnosticResult(
                category = "5. Live Coach State",
                check = "Connection Status",
                status = DiagnosticResult.Status.PASS,
                message = "Live Coach connected"
            )
            ConnectionState.CONNECTING -> DiagnosticResult(
                category = "5. Live Coach State",
                check = "Connection Status",
                status = DiagnosticResult.Status.WARNING,
                message = "Live Coach connecting..."
            )
            ConnectionState.DISCONNECTED -> DiagnosticResult(
                category = "5. Live Coach State",
                check = "Connection Status",
                status = DiagnosticResult.Status.WARNING,
                message = "Live Coach disconnected",
                solution = "Call liveCoachManager.start() to connect"
            )
            ConnectionState.ERROR -> DiagnosticResult(
                category = "5. Live Coach State",
                check = "Connection Status",
                status = DiagnosticResult.Status.FAIL,
                message = "Live Coach in error state",
                solution = "Check previous errors and retry connection"
            )
            ConnectionState.RECONNECTING -> DiagnosticResult(
                category = "5. Live Coach State",
                check = "Connection Status",
                status = DiagnosticResult.Status.WARNING,
                message = "Live Coach reconnecting..."
            )
        }

        results.add(result)
        Timber.d("${result.getIcon()} Connection Status: ${result.message}")
    }

    private fun checkRecordingState() {
        val isRecording = liveCoachManager?.isRecording() ?: return

        val result = DiagnosticResult(
            category = "5. Live Coach State",
            check = "Recording State",
            status = DiagnosticResult.Status.PASS,
            message = if (isRecording) "Currently recording" else "Not recording"
        )

        results.add(result)
        Timber.d("${result.getIcon()} Recording: ${result.message}")
    }

    private fun checkSessionHealth() {
        val sessionInfo = liveCoachManager?.getSessionInfo() ?: return
        val isHealthy = sessionInfo["connectionHealthy"] as? Boolean ?: false

        val result = if (isHealthy) {
            DiagnosticResult(
                category = "5. Live Coach State",
                check = "Session Health",
                status = DiagnosticResult.Status.PASS,
                message = "Session healthy"
            )
        } else {
            DiagnosticResult(
                category = "5. Live Coach State",
                check = "Session Health",
                status = DiagnosticResult.Status.WARNING,
                message = "Session unhealthy - check connection",
                solution = "Try reconnecting: liveCoachManager.forceReconnect()"
            )
        }

        results.add(result)
        Timber.d("${result.getIcon()} Session Health: ${result.message}")
    }

    private fun checkBuildConfiguration() {
        val result = DiagnosticResult(
            category = "6. Build Configuration",
            check = "BuildConfig",
            status = DiagnosticResult.Status.PASS,
            message = "Debug: ${BuildConfig.DEBUG}, Version: ${BuildConfig.VERSION_NAME}"
        )

        results.add(result)
        Timber.d("${result.getIcon()} Build Config: ${result.message}")
    }

    /**
     * Quick health check - returns true if all critical checks pass
     */
    fun isHealthy(): Boolean {
        if (results.isEmpty()) {
            runFullDiagnostics()
        }

        return results.none { it.status == DiagnosticResult.Status.FAIL }
    }

    /**
     * Get a list of critical issues that must be fixed
     */
    fun getCriticalIssues(): List<DiagnosticResult> {
        return results.filter { it.status == DiagnosticResult.Status.FAIL }
    }
}
