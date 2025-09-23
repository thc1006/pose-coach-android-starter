package com.posecoach.app.livecoach.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.posecoach.app.livecoach.models.AudioPermissionDetails
import com.posecoach.app.livecoach.models.AudioPermissionStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * AudioPermissionManager handles audio permission checking and management.
 * Separated from AudioStreamManager for better modularity (<150 lines).
 *
 * Features:
 * - Basic and enhanced audio permission checking
 * - Android 15+ permission handling
 * - Permission status monitoring
 * - Timeout-based permission requests
 */
class AudioPermissionManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioPermissionManager"
        private const val DEFAULT_PERMISSION_TIMEOUT_MS = 10000L
    }

    private val _permissionStatus = MutableSharedFlow<AudioPermissionDetails>(
        replay = 1,
        extraBufferCapacity = 3
    )
    val permissionStatus: SharedFlow<AudioPermissionDetails> = _permissionStatus.asSharedFlow()

    /**
     * Check basic audio recording permission
     */
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check enhanced audio permissions for Android 15+
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun hasEnhancedAudioPermissions(): Boolean {
        val recordPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val modifyPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

        return recordPermission && modifyPermission
    }

    /**
     * Get detailed current permission status
     */
    fun getCurrentPermissionStatus(): AudioPermissionDetails {
        val recordStatus = getPermissionStatus(Manifest.permission.RECORD_AUDIO)
        val modifyStatus = getPermissionStatus(Manifest.permission.MODIFY_AUDIO_SETTINGS)

        val hasBasic = recordStatus == AudioPermissionStatus.GRANTED
        val hasEnhanced = hasBasic && modifyStatus == AudioPermissionStatus.GRANTED

        return AudioPermissionDetails(
            recordAudioStatus = recordStatus,
            modifyAudioStatus = modifyStatus,
            hasBasicPermissions = hasBasic,
            hasEnhancedPermissions = hasEnhanced
        )
    }

    /**
     * Check and emit current permission status
     */
    suspend fun checkAndEmitStatus() {
        val status = getCurrentPermissionStatus()
        _permissionStatus.emit(status)
        Timber.d("$TAG: Permission status updated - Basic: ${status.hasBasicPermissions}, Enhanced: ${status.hasEnhancedPermissions}")
    }

    /**
     * Request permission with timeout (mock implementation for testing)
     */
    suspend fun requestPermissionWithTimeout(timeoutMs: Long = DEFAULT_PERMISSION_TIMEOUT_MS): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            // In real implementation, this would trigger permission request dialog
            // For now, return current permission status
            hasAudioPermission()
        } ?: false
    }

    /**
     * Validate permission state consistency
     */
    fun validatePermissionState(): Boolean {
        val status = getCurrentPermissionStatus()

        // Basic validation: if we have enhanced permissions, we should have basic ones too
        if (status.hasEnhancedPermissions && !status.hasBasicPermissions) {
            Timber.w("$TAG: Inconsistent permission state detected")
            return false
        }

        return true
    }

    /**
     * Check if permission was permanently denied
     */
    fun isPermissionPermanentlyDenied(permission: String): Boolean {
        // This would typically check if shouldShowRequestPermissionRationale returns false
        // and the permission is denied, indicating permanent denial
        // Simplified implementation for this refactor
        return getPermissionStatus(permission) == AudioPermissionStatus.PERMANENTLY_DENIED
    }

    /**
     * Get status for a specific permission
     */
    private fun getPermissionStatus(permission: String): AudioPermissionStatus {
        return when (ContextCompat.checkSelfPermission(context, permission)) {
            PackageManager.PERMISSION_GRANTED -> AudioPermissionStatus.GRANTED
            PackageManager.PERMISSION_DENIED -> {
                // In real implementation, would check shouldShowRequestPermissionRationale
                // to distinguish between regular denial and permanent denial
                AudioPermissionStatus.DENIED
            }
            else -> AudioPermissionStatus.NOT_REQUESTED
        }
    }

    /**
     * Log current permission state for debugging
     */
    fun logPermissionState() {
        val status = getCurrentPermissionStatus()
        Timber.d("$TAG: Current permission state:")
        Timber.d("  Record Audio: ${status.recordAudioStatus}")
        Timber.d("  Modify Audio Settings: ${status.modifyAudioStatus}")
        Timber.d("  Has Basic Permissions: ${status.hasBasicPermissions}")
        Timber.d("  Has Enhanced Permissions: ${status.hasEnhancedPermissions}")
    }
}