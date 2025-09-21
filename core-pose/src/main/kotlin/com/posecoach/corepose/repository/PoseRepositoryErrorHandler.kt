package com.posecoach.corepose.repository

import com.posecoach.corepose.models.PoseDetectionError
import timber.log.Timber

/**
 * Centralized error handling for pose detection operations.
 * Provides consistent error classification and recovery strategies.
 */
object PoseRepositoryErrorHandler {

    enum class ErrorType {
        INITIALIZATION_FAILED,
        DETECTION_FAILED,
        MODEL_LOADING_FAILED,
        INSUFFICIENT_LIGHTING,
        NO_POSE_DETECTED,
        PROCESSING_TIMEOUT,
        GPU_ERROR,
        MEMORY_ERROR,
        UNKNOWN_ERROR
    }

    data class ErrorContext(
        val type: ErrorType,
        val originalError: Throwable?,
        val message: String,
        val isRecoverable: Boolean,
        val suggestedAction: String
    )

    /**
     * Classifies and handles pose detection errors.
     */
    fun handleError(
        error: Throwable,
        operation: String,
        listener: PoseDetectionListener?
    ): ErrorContext {
        val errorContext = classifyError(error, operation)

        Timber.e(error, "Pose detection error in $operation: ${errorContext.message}")

        listener?.onPoseDetectionError(
            PoseDetectionError(
                message = "${errorContext.type}: ${errorContext.message}",
                cause = error
            )
        )

        logRecoveryGuidance(errorContext)
        return errorContext
    }

    /**
     * Handles timeout scenarios during pose detection.
     */
    fun handleTimeout(
        operationTimeMs: Long,
        timeoutThresholdMs: Long,
        listener: PoseDetectionListener?
    ) {
        val message = "Operation timed out: ${operationTimeMs}ms > ${timeoutThresholdMs}ms"
        val errorContext = ErrorContext(
            type = ErrorType.PROCESSING_TIMEOUT,
            originalError = null,
            message = message,
            isRecoverable = true,
            suggestedAction = "Reduce image resolution or model complexity"
        )

        Timber.w(message)
        listener?.onPoseDetectionError(
            PoseDetectionError(message = message)
        )
    }

    /**
     * Handles cases where no pose is detected.
     */
    fun handleNoPoseDetected(
        frameCount: Int,
        consecutiveFailures: Int,
        listener: PoseDetectionListener?
    ) {
        if (consecutiveFailures > 10) {
            val message = "No pose detected in $consecutiveFailures consecutive frames"
            val errorContext = ErrorContext(
                type = ErrorType.NO_POSE_DETECTED,
                originalError = null,
                message = message,
                isRecoverable = true,
                suggestedAction = "Check lighting conditions and camera positioning"
            )

            Timber.w(message)
            listener?.onPoseDetectionError(
                PoseDetectionError(message = message)
            )
        }
    }

    /**
     * Handles insufficient lighting conditions.
     */
    fun handleInsufficientLighting(
        averageVisibility: Float,
        listener: PoseDetectionListener?
    ) {
        if (averageVisibility < 0.3f) {
            val message = "Poor lighting conditions detected (avg visibility: $averageVisibility)"
            val errorContext = ErrorContext(
                type = ErrorType.INSUFFICIENT_LIGHTING,
                originalError = null,
                message = message,
                isRecoverable = true,
                suggestedAction = "Improve lighting conditions"
            )

            Timber.w(message)
            listener?.onPoseDetectionError(
                PoseDetectionError(message = message)
            )
        }
    }

    private fun classifyError(error: Throwable, operation: String): ErrorContext {
        return when {
            error.message?.contains("GPU") == true ||
            error.message?.contains("OpenGL") == true -> {
                ErrorContext(
                    type = ErrorType.GPU_ERROR,
                    originalError = error,
                    message = "GPU processing error during $operation",
                    isRecoverable = true,
                    suggestedAction = "Fallback to CPU processing or restart detection"
                )
            }

            error.message?.contains("OutOfMemory") == true ||
            error is OutOfMemoryError -> {
                ErrorContext(
                    type = ErrorType.MEMORY_ERROR,
                    originalError = error,
                    message = "Insufficient memory for $operation",
                    isRecoverable = true,
                    suggestedAction = "Reduce image resolution or clear memory"
                )
            }

            error.message?.contains("model") == true ||
            error.message?.contains("asset") == true -> {
                ErrorContext(
                    type = ErrorType.MODEL_LOADING_FAILED,
                    originalError = error,
                    message = "Failed to load pose detection model",
                    isRecoverable = false,
                    suggestedAction = "Check model file exists and is accessible"
                )
            }

            operation == "initialization" -> {
                ErrorContext(
                    type = ErrorType.INITIALIZATION_FAILED,
                    originalError = error,
                    message = "Failed to initialize pose detection",
                    isRecoverable = false,
                    suggestedAction = "Check MediaPipe installation and permissions"
                )
            }

            operation == "detection" -> {
                ErrorContext(
                    type = ErrorType.DETECTION_FAILED,
                    originalError = error,
                    message = "Pose detection processing failed",
                    isRecoverable = true,
                    suggestedAction = "Retry with different frame or check image format"
                )
            }

            else -> {
                ErrorContext(
                    type = ErrorType.UNKNOWN_ERROR,
                    originalError = error,
                    message = "Unknown error during $operation: ${error.message}",
                    isRecoverable = true,
                    suggestedAction = "Restart pose detection or report issue"
                )
            }
        }
    }

    private fun logRecoveryGuidance(errorContext: ErrorContext) {
        if (errorContext.isRecoverable) {
            Timber.i("Recovery guidance: ${errorContext.suggestedAction}")
        } else {
            Timber.e("Non-recoverable error: ${errorContext.message}. ${errorContext.suggestedAction}")
        }
    }

    /**
     * Checks if the system is in a state that might cause detection issues.
     */
    fun performHealthCheck(): List<String> {
        val issues = mutableListOf<String>()

        // Check memory availability
        val runtime = Runtime.getRuntime()
        val freeMemory = runtime.freeMemory()
        val totalMemory = runtime.totalMemory()
        val usedMemory = totalMemory - freeMemory
        val memoryUsagePercent = (usedMemory.toDouble() / totalMemory) * 100

        if (memoryUsagePercent > 85) {
            issues.add("High memory usage: ${memoryUsagePercent.toInt()}%")
        }

        // Check if system is under load
        val availableProcessors = runtime.availableProcessors()
        if (availableProcessors < 2) {
            issues.add("Limited processing capacity: $availableProcessors cores")
        }

        return issues
    }
}