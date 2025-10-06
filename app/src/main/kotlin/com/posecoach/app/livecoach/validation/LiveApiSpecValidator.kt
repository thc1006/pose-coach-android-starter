package com.posecoach.app.livecoach.validation

import android.media.AudioFormat
import timber.log.Timber

/**
 * Validator for Gemini Live API specification compliance
 *
 * Ensures that audio and connection configurations meet the official
 * requirements defined in the Live API documentation.
 *
 * Reference: https://ai.google.dev/gemini-api/docs/live-guide
 *
 * This validator helps catch configuration errors early and provides
 * clear error messages referencing the official specification.
 */
object LiveApiSpecValidator {

    private const val TAG = "LiveApiSpecValidator"

    /**
     * Official Live API specification constants
     */
    object Spec {
        // Audio format requirements
        const val REQUIRED_INPUT_SAMPLE_RATE = 16000  // 16kHz
        const val REQUIRED_INPUT_BIT_DEPTH = AudioFormat.ENCODING_PCM_16BIT  // 16-bit
        const val REQUIRED_INPUT_CHANNELS = AudioFormat.CHANNEL_IN_MONO  // Mono

        const val EXPECTED_OUTPUT_SAMPLE_RATE = 24000  // 24kHz (fixed by API)

        const val MIME_TYPE_PREFIX = "audio/pcm"
        const val REQUIRED_MIME_TYPE = "audio/pcm;rate=16000"

        // Connection requirements
        const val API_VERSION = "v1alpha"
        const val REQUIRED_MODEL = "models/gemini-2.0-flash-exp"

        // Endpoint
        const val WEBSOCKET_HOST = "generativelanguage.googleapis.com"
        const val WEBSOCKET_PATH = "/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"

        // Context limits
        const val MAX_AUDIO_SESSION_MINUTES = 15
        const val NATIVE_AUDIO_CONTEXT_TOKENS = 128_000
        const val OTHER_CONTEXT_TOKENS = 32_000
    }

    /**
     * Validation result with detailed error information
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<ValidationError> = emptyList(),
        val warnings: List<String> = emptyList()
    ) {
        fun throwIfInvalid() {
            if (!isValid) {
                val errorMessage = buildString {
                    appendLine("Live API Specification Validation Failed:")
                    errors.forEach { error ->
                        appendLine("  ❌ ${error.field}: ${error.message}")
                        appendLine("     Expected: ${error.expected}")
                        appendLine("     Actual: ${error.actual}")
                        appendLine("     Reference: ${error.reference}")
                    }
                }
                throw LiveApiValidationException(errorMessage, errors)
            }
        }

        fun logWarnings() {
            warnings.forEach { warning ->
                Timber.w("$TAG: ⚠️ $warning")
            }
        }
    }

    /**
     * Validation error details
     */
    data class ValidationError(
        val field: String,
        val message: String,
        val expected: String,
        val actual: String,
        val reference: String = "https://ai.google.dev/gemini-api/docs/live-guide"
    )

    /**
     * Exception thrown when validation fails
     */
    class LiveApiValidationException(
        message: String,
        val errors: List<ValidationError>
    ) : IllegalStateException(message)

    /**
     * Validate input audio configuration
     */
    fun validateInputAudioConfig(
        sampleRate: Int,
        channelConfig: Int,
        audioFormat: Int
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        // Validate sample rate
        if (sampleRate != Spec.REQUIRED_INPUT_SAMPLE_RATE) {
            errors.add(
                ValidationError(
                    field = "Input Sample Rate",
                    message = "Live API requires 16kHz audio input",
                    expected = "${Spec.REQUIRED_INPUT_SAMPLE_RATE} Hz",
                    actual = "$sampleRate Hz",
                    reference = "https://ai.google.dev/gemini-api/docs/live-guide#audio-format"
                )
            )
        }

        // Validate audio format (bit depth)
        if (audioFormat != Spec.REQUIRED_INPUT_BIT_DEPTH) {
            errors.add(
                ValidationError(
                    field = "Audio Format",
                    message = "Live API requires 16-bit PCM encoding",
                    expected = "AudioFormat.ENCODING_PCM_16BIT",
                    actual = audioFormat.toString(),
                    reference = "https://ai.google.dev/gemini-api/docs/live-guide#audio-format"
                )
            )
        }

        // Validate channel configuration
        if (channelConfig != Spec.REQUIRED_INPUT_CHANNELS) {
            errors.add(
                ValidationError(
                    field = "Channel Config",
                    message = "Live API requires mono (single channel) audio",
                    expected = "AudioFormat.CHANNEL_IN_MONO",
                    actual = channelConfig.toString(),
                    reference = "https://ai.google.dev/gemini-api/docs/live-guide#audio-format"
                )
            )
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    /**
     * Validate output audio configuration
     */
    fun validateOutputAudioConfig(
        sampleRate: Int
    ): ValidationResult {
        val warnings = mutableListOf<String>()

        // Live API outputs at 24kHz - this is informational
        if (sampleRate != Spec.EXPECTED_OUTPUT_SAMPLE_RATE) {
            warnings.add(
                "Output sample rate is $sampleRate Hz. Live API outputs audio at ${Spec.EXPECTED_OUTPUT_SAMPLE_RATE} Hz."
            )
        }

        return ValidationResult(
            isValid = true,  // Not a hard requirement
            warnings = warnings
        )
    }

    /**
     * Validate MIME type format
     */
    fun validateMimeType(mimeType: String): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (!mimeType.startsWith(Spec.MIME_TYPE_PREFIX)) {
            errors.add(
                ValidationError(
                    field = "MIME Type Prefix",
                    message = "MIME type must be audio/pcm",
                    expected = "audio/pcm;rate=XXXX",
                    actual = mimeType,
                    reference = "https://ai.google.dev/gemini-api/docs/live-guide#audio-format"
                )
            )
        }

        if (!mimeType.contains("rate=")) {
            errors.add(
                ValidationError(
                    field = "MIME Type Rate Parameter",
                    message = "MIME type MUST include sample rate parameter",
                    expected = Spec.REQUIRED_MIME_TYPE,
                    actual = mimeType,
                    reference = "https://ai.google.dev/gemini-api/docs/live-guide#audio-format"
                )
            )
        } else {
            // Extract and validate rate value
            val rateMatch = Regex("rate=(\\d+)").find(mimeType)
            val rateValue = rateMatch?.groupValues?.get(1)?.toIntOrNull()
            if (rateValue != Spec.REQUIRED_INPUT_SAMPLE_RATE) {
                errors.add(
                    ValidationError(
                        field = "MIME Type Rate Value",
                        message = "Sample rate in MIME type must be 16000",
                        expected = "rate=16000",
                        actual = "rate=$rateValue",
                        reference = "https://ai.google.dev/gemini-api/docs/live-guide#audio-format"
                    )
                )
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    /**
     * Validate model name
     */
    fun validateModelName(model: String): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (model != Spec.REQUIRED_MODEL) {
            errors.add(
                ValidationError(
                    field = "Model Name",
                    message = "Live API requires gemini-2.0-flash-exp model",
                    expected = Spec.REQUIRED_MODEL,
                    actual = model,
                    reference = "https://ai.google.dev/gemini-api/docs/live-guide#model"
                )
            )
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    /**
     * Validate WebSocket endpoint URL
     */
    fun validateWebSocketUrl(url: String): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (!url.contains(Spec.WEBSOCKET_HOST)) {
            errors.add(
                ValidationError(
                    field = "WebSocket Host",
                    message = "Invalid WebSocket host",
                    expected = Spec.WEBSOCKET_HOST,
                    actual = url,
                    reference = "https://ai.google.dev/gemini-api/docs/live-guide#websocket"
                )
            )
        }

        if (!url.contains(Spec.API_VERSION)) {
            errors.add(
                ValidationError(
                    field = "API Version",
                    message = "WebSocket URL must use v1alpha (NOT v1beta)",
                    expected = "URL containing '${Spec.API_VERSION}'",
                    actual = url,
                    reference = "https://ai.google.dev/gemini-api/docs/live-guide#websocket"
                )
            )
        }

        if (url.contains("v1beta")) {
            errors.add(
                ValidationError(
                    field = "API Version",
                    message = "Incorrect API version: v1beta is not supported for Live API",
                    expected = Spec.API_VERSION,
                    actual = "v1beta",
                    reference = "https://ai.google.dev/gemini-api/docs/live-guide#websocket"
                )
            )
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    /**
     * Validate complete configuration
     */
    fun validateCompleteConfig(
        inputSampleRate: Int,
        inputChannelConfig: Int,
        inputAudioFormat: Int,
        outputSampleRate: Int,
        mimeType: String,
        model: String,
        websocketUrl: String
    ): ValidationResult {
        val allErrors = mutableListOf<ValidationError>()
        val allWarnings = mutableListOf<String>()

        // Validate each component
        validateInputAudioConfig(inputSampleRate, inputChannelConfig, inputAudioFormat).let {
            allErrors.addAll(it.errors)
            allWarnings.addAll(it.warnings)
        }

        validateOutputAudioConfig(outputSampleRate).let {
            allErrors.addAll(it.errors)
            allWarnings.addAll(it.warnings)
        }

        validateMimeType(mimeType).let {
            allErrors.addAll(it.errors)
            allWarnings.addAll(it.warnings)
        }

        validateModelName(model).let {
            allErrors.addAll(it.errors)
            allWarnings.addAll(it.warnings)
        }

        validateWebSocketUrl(websocketUrl).let {
            allErrors.addAll(it.errors)
            allWarnings.addAll(it.warnings)
        }

        val result = ValidationResult(
            isValid = allErrors.isEmpty(),
            errors = allErrors,
            warnings = allWarnings
        )

        // Log results
        if (result.isValid) {
            Timber.i("$TAG: ✅ Live API configuration is compliant with specification")
            result.logWarnings()
        } else {
            Timber.e("$TAG: ❌ Live API configuration validation failed:")
            result.errors.forEach { error ->
                Timber.e("$TAG:   ${error.field}: ${error.message}")
            }
        }

        return result
    }

    /**
     * Quick compliance check (logs to console, doesn't throw)
     */
    fun checkCompliance(
        inputSampleRate: Int,
        inputChannelConfig: Int,
        inputAudioFormat: Int,
        outputSampleRate: Int,
        mimeType: String,
        model: String,
        websocketUrl: String
    ): Boolean {
        val result = validateCompleteConfig(
            inputSampleRate,
            inputChannelConfig,
            inputAudioFormat,
            outputSampleRate,
            mimeType,
            model,
            websocketUrl
        )
        return result.isValid
    }
}
