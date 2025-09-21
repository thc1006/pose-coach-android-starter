package com.posecoach.testing.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.google.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

/**
 * Comprehensive test suite for audio processing compliance with Gemini Live API specifications.
 *
 * Tests cover:
 * - 16-bit PCM 16kHz input validation
 * - Voice Activity Detection accuracy
 * - Real-time streaming performance
 * - Audio quality adaptation testing
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AudioProcessingTestSuite {

    private lateinit var testScope: TestScope
    private lateinit var audioProcessor: TestAudioProcessor
    private lateinit var mockAudioRecord: AudioRecord
    private lateinit var audioStreamFlow: MutableSharedFlow<ByteArray>
    private lateinit var vadResultFlow: MutableSharedFlow<VoiceActivityResult>

    // Audio format constants for Gemini Live API
    companion object {
        const val SAMPLE_RATE = 16000 // 16kHz
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BITS_PER_SAMPLE = 16
        const val BUFFER_SIZE_MULTIPLIER = 4
        const val VAD_THRESHOLD = 0.1f
        const val SILENCE_THRESHOLD_MS = 1000L
    }

    data class VoiceActivityResult(
        val hasVoice: Boolean,
        val confidence: Float,
        val energyLevel: Float,
        val timestamp: Long
    )

    @Before
    fun setup() {
        testScope = TestScope()
        mockAudioRecord = mockk(relaxed = true)
        audioStreamFlow = MutableSharedFlow()
        vadResultFlow = MutableSharedFlow()
        audioProcessor = TestAudioProcessor(
            audioStreamFlow = audioStreamFlow,
            vadResultFlow = vadResultFlow,
            scope = testScope
        )

        // Mock AudioRecord behavior
        every { mockAudioRecord.sampleRate } returns SAMPLE_RATE
        every { mockAudioRecord.channelConfiguration } returns CHANNEL_CONFIG
        every { mockAudioRecord.audioFormat } returns AUDIO_FORMAT
        every { mockAudioRecord.state } returns AudioRecord.STATE_INITIALIZED
        every { mockAudioRecord.recordingState } returns AudioRecord.RECORDSTATE_STOPPED
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test 16-bit PCM 16kHz format validation`() = testScope.runTest {
        // Test correct audio format parameters
        val audioConfig = AudioConfig(
            sampleRate = SAMPLE_RATE,
            channelConfig = CHANNEL_CONFIG,
            audioFormat = AUDIO_FORMAT
        )

        val isValidFormat = audioProcessor.validateAudioFormat(audioConfig)
        assertThat(isValidFormat).isTrue()

        // Test invalid sample rates
        val invalidSampleRates = listOf(8000, 22050, 44100, 48000)
        invalidSampleRates.forEach { sampleRate ->
            val invalidConfig = audioConfig.copy(sampleRate = sampleRate)
            assertThat(audioProcessor.validateAudioFormat(invalidConfig)).isFalse()
        }

        // Test invalid audio formats
        val invalidFormats = listOf(
            AudioFormat.ENCODING_PCM_8BIT,
            AudioFormat.ENCODING_PCM_FLOAT,
            AudioFormat.ENCODING_PCM_24BIT_PACKED,
            AudioFormat.ENCODING_PCM_32BIT
        )
        invalidFormats.forEach { format ->
            val invalidConfig = audioConfig.copy(audioFormat = format)
            assertThat(audioProcessor.validateAudioFormat(invalidConfig)).isFalse()
        }

        // Test invalid channel configurations
        val invalidChannels = listOf(
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.CHANNEL_IN_FRONT_BACK
        )
        invalidChannels.forEach { channel ->
            val invalidConfig = audioConfig.copy(channelConfig = channel)
            assertThat(audioProcessor.validateAudioFormat(invalidConfig)).isFalse()
        }
    }

    @Test
    fun `test audio buffer size calculation`() = testScope.runTest {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        val calculatedBufferSize = audioProcessor.calculateOptimalBufferSize()

        // Buffer size should be at least minimum required
        assertThat(calculatedBufferSize).isAtLeast(minBufferSize)

        // Should be a multiple of frame size for efficient processing
        val frameSize = BITS_PER_SAMPLE / 8 // bytes per sample
        assertThat(calculatedBufferSize % frameSize).isEqualTo(0)

        // Should provide reasonable latency (< 100ms worth of audio)
        val maxLatencyBytes = (SAMPLE_RATE * frameSize * 0.1).toInt() // 100ms
        assertThat(calculatedBufferSize).isAtMost(maxLatencyBytes * BUFFER_SIZE_MULTIPLIER)
    }

    @Test
    fun `test real-time audio streaming performance`() = testScope.runTest {
        val bufferSize = audioProcessor.calculateOptimalBufferSize()
        val testDurationMs = 5000L // 5 seconds
        val expectedChunks = (testDurationMs * SAMPLE_RATE / 1000) / (bufferSize / 2) // 16-bit = 2 bytes per sample

        val receivedChunks = AtomicInteger(0)
        val latencies = mutableListOf<Long>()
        val droppedFrames = AtomicInteger(0)

        // Start audio processing
        val job = launch {
            audioStreamFlow.collect { audioData ->
                val receiveTime = System.nanoTime()
                val chunkNumber = receivedChunks.incrementAndGet()

                // Calculate expected timestamp for this chunk
                val expectedTime = (chunkNumber * bufferSize / 2 * 1000L / SAMPLE_RATE) * 1_000_000 // nanoseconds
                val actualLatency = (receiveTime - expectedTime) / 1_000_000 // milliseconds

                latencies.add(actualLatency)

                // Check for dropped frames (gaps in audio)
                if (actualLatency > 100) { // > 100ms latency indicates potential drop
                    droppedFrames.incrementAndGet()
                }

                // Validate audio data format
                assertThat(audioData.size).isEqualTo(bufferSize)
                assertThat(audioData.size % 2).isEqualTo(0) // 16-bit alignment
            }
        }

        // Simulate real-time audio generation
        val audioGenerator = launch {
            val chunkIntervalMs = (bufferSize / 2 * 1000L) / SAMPLE_RATE
            var chunkCount = 0

            while (chunkCount * chunkIntervalMs < testDurationMs) {
                val audioChunk = generateTestAudioChunk(bufferSize, 440.0f) // 440Hz tone
                audioStreamFlow.emit(audioChunk)
                chunkCount++
                delay(chunkIntervalMs)
            }
        }

        audioGenerator.join()
        delay(1000) // Allow processing to complete
        job.cancel()

        // Validate performance metrics
        assertThat(receivedChunks.get()).isAtLeast((expectedChunks * 0.95).toInt()) // Allow 5% tolerance
        assertThat(droppedFrames.get()).isAtMost((expectedChunks * 0.01).toInt()) // < 1% dropped frames

        val averageLatency = latencies.average()
        assertThat(averageLatency).isLessThan(50.0) // < 50ms average latency
    }

    @Test
    fun `test Voice Activity Detection accuracy`() = testScope.runTest {
        val testCases = listOf(
            // (audio type, expected VAD result, confidence threshold)
            Triple("silence", false, 0.9f),
            Triple("speech", true, 0.8f),
            Triple("music", false, 0.7f),
            Triple("noise", false, 0.6f),
            Triple("whisper", true, 0.5f)
        )

        val vadResults = mutableListOf<VoiceActivityResult>()
        val job = launch {
            vadResultFlow.collect { result ->
                vadResults.add(result)
            }
        }

        testCases.forEach { (audioType, expectedVoice, minConfidence) ->
            vadResults.clear()

            val testAudio = when (audioType) {
                "silence" -> generateSilence(SAMPLE_RATE) // 1 second of silence
                "speech" -> generateSpeechLikeAudio(SAMPLE_RATE)
                "music" -> generateMusicLikeAudio(SAMPLE_RATE)
                "noise" -> generateWhiteNoise(SAMPLE_RATE)
                "whisper" -> generateWhisperLikeAudio(SAMPLE_RATE)
                else -> generateSilence(SAMPLE_RATE)
            }

            audioProcessor.processAudioForVAD(testAudio)
            delay(100) // Allow processing

            assertThat(vadResults).isNotEmpty()

            val finalResult = vadResults.last()
            assertThat(finalResult.hasVoice).isEqualTo(expectedVoice)
            if (expectedVoice) {
                assertThat(finalResult.confidence).isAtLeast(minConfidence)
            }
        }

        job.cancel()
    }

    @Test
    fun `test adaptive audio quality based on network conditions`() = testScope.runTest {
        val networkConditions = listOf(
            NetworkCondition("excellent", 1000, 10), // 1Gbps, 10ms RTT
            NetworkCondition("good", 100, 50),       // 100Mbps, 50ms RTT
            NetworkCondition("fair", 10, 150),       // 10Mbps, 150ms RTT
            NetworkCondition("poor", 1, 500)         // 1Mbps, 500ms RTT
        )

        networkConditions.forEach { condition ->
            val audioSettings = audioProcessor.adaptAudioQuality(condition)

            when (condition.quality) {
                "excellent" -> {
                    assertThat(audioSettings.compressionLevel).isEqualTo(0) // No compression
                    assertThat(audioSettings.chunkSizeMs).isEqualTo(100) // Smaller chunks
                    assertThat(audioSettings.enableNoiseReduction).isFalse()
                }
                "good" -> {
                    assertThat(audioSettings.compressionLevel).isEqualTo(1) // Light compression
                    assertThat(audioSettings.chunkSizeMs).isEqualTo(200)
                    assertThat(audioSettings.enableNoiseReduction).isFalse()
                }
                "fair" -> {
                    assertThat(audioSettings.compressionLevel).isEqualTo(3) // Moderate compression
                    assertThat(audioSettings.chunkSizeMs).isEqualTo(500)
                    assertThat(audioSettings.enableNoiseReduction).isTrue()
                }
                "poor" -> {
                    assertThat(audioSettings.compressionLevel).isEqualTo(5) // High compression
                    assertThat(audioSettings.chunkSizeMs).isEqualTo(1000)
                    assertThat(audioSettings.enableNoiseReduction).isTrue()
                }
            }
        }
    }

    @Test
    fun `test audio encoding compliance with Base64 requirements`() = testScope.runTest {
        val testAudio = generateTestAudioChunk(1024, 440.0f)

        // Test Base64 encoding
        val encodedAudio = audioProcessor.encodeAudioToBase64(testAudio)

        // Validate Base64 format
        assertThat(encodedAudio).isNotEmpty()
        assertThat(encodedAudio.matches(Regex("^[A-Za-z0-9+/]*={0,2}$"))).isTrue()

        // Validate encoding length (Base64 increases size by ~33%)
        val expectedEncodedLength = ((testAudio.size + 2) / 3) * 4
        assertThat(encodedAudio.length).isEqualTo(expectedEncodedLength)

        // Test round-trip encoding/decoding
        val decodedAudio = audioProcessor.decodeAudioFromBase64(encodedAudio)
        assertThat(decodedAudio).isEqualTo(testAudio)
    }

    @Test
    fun `test audio chunk timing and synchronization`() = testScope.runTest {
        val chunkSizeMs = 200L // 200ms chunks
        val chunkSizeBytes = ((SAMPLE_RATE * chunkSizeMs / 1000) * 2).toInt() // 16-bit
        val numberOfChunks = 10

        val timestamps = mutableListOf<Long>()
        val job = launch {
            audioStreamFlow.collect { audioData ->
                timestamps.add(System.currentTimeMillis())
                assertThat(audioData.size).isEqualTo(chunkSizeBytes)
            }
        }

        val startTime = System.currentTimeMillis()

        // Send audio chunks at precise intervals
        repeat(numberOfChunks) { index ->
            val expectedTime = startTime + (index * chunkSizeMs)
            val currentTime = System.currentTimeMillis()
            val delay = maxOf(0, expectedTime - currentTime)

            if (delay > 0) {
                delay(delay)
            }

            val audioChunk = generateTestAudioChunk(chunkSizeBytes, 440.0f)
            audioStreamFlow.emit(audioChunk)
        }

        delay(500) // Allow final processing
        job.cancel()

        // Validate timing accuracy
        assertThat(timestamps).hasSize(numberOfChunks)

        for (i in 1 until timestamps.size) {
            val actualInterval = timestamps[i] - timestamps[i - 1]
            val expectedInterval = chunkSizeMs
            val tolerance = 50L // 50ms tolerance

            assertThat(actualInterval).isWithin(tolerance).of(expectedInterval)
        }
    }

    @Test
    fun `test audio format conversion and resampling`() = testScope.runTest {
        // Test conversion from common formats to required 16kHz 16-bit PCM
        val testFormats = listOf(
            AudioFormat(44100, 16, 1), // CD quality
            AudioFormat(48000, 16, 1), // Professional audio
            AudioFormat(22050, 16, 1), // Half CD quality
            AudioFormat(8000, 16, 1)   // Phone quality
        )

        testFormats.forEach { inputFormat ->
            val inputAudio = generateTestAudioChunk(
                (inputFormat.sampleRate * 0.5).toInt(), // 0.5 seconds
                440.0f,
                inputFormat.sampleRate
            )

            val convertedAudio = audioProcessor.convertToRequiredFormat(inputAudio, inputFormat)

            // Validate output format
            val expectedOutputSamples = (SAMPLE_RATE * 0.5).toInt() // 0.5 seconds at 16kHz
            val expectedOutputBytes = expectedOutputSamples * 2 // 16-bit

            assertThat(convertedAudio.size).isEqualTo(expectedOutputBytes)

            // Validate audio quality (should preserve frequency content)
            val inputFrequency = calculateDominantFrequency(inputAudio, inputFormat.sampleRate)
            val outputFrequency = calculateDominantFrequency(convertedAudio, SAMPLE_RATE)

            assertThat(outputFrequency).isWithin(10.0f).of(inputFrequency)
        }
    }

    @Test
    fun `test audio processing under memory pressure`() = testScope.runTest {
        val largeDurationSeconds = 30
        val totalSamples = SAMPLE_RATE * largeDurationSeconds
        val chunkSize = 4096 // Large chunks to stress memory
        val expectedChunks = totalSamples / (chunkSize / 2)

        val processedChunks = AtomicInteger(0)
        val memorySnapshots = mutableListOf<Long>()

        val job = launch {
            audioStreamFlow.collect { audioData ->
                processedChunks.incrementAndGet()

                // Take memory snapshot every 10 chunks
                if (processedChunks.get() % 10 == 0) {
                    val usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                    memorySnapshots.add(usedMemory)
                }

                // Simulate processing
                audioProcessor.processAudioChunk(audioData)
            }
        }

        // Generate and send large amount of audio data
        repeat(expectedChunks) { index ->
            val audioChunk = generateTestAudioChunk(chunkSize, 440.0f + (index % 100))
            audioStreamFlow.emit(audioChunk)

            if (index % 100 == 0) {
                delay(10) // Small pause to allow GC
            }
        }

        delay(1000) // Allow processing to complete
        job.cancel()

        // Validate memory usage remained stable
        assertThat(processedChunks.get()).isEqualTo(expectedChunks)
        assertThat(memorySnapshots.size).isGreaterThan(2)

        // Memory should not grow unbounded (allow 50% variance)
        val initialMemory = memorySnapshots.first()
        val finalMemory = memorySnapshots.last()
        val memoryGrowth = (finalMemory - initialMemory).toFloat() / initialMemory

        assertThat(memoryGrowth).isLessThan(0.5f) // Less than 50% memory growth
    }

    // Helper methods for generating test audio data
    private fun generateTestAudioChunk(sizeBytes: Int, frequency: Float, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val samples = sizeBytes / 2 // 16-bit samples
        val audioData = ByteArray(sizeBytes)
        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until samples) {
            val time = i.toFloat() / sampleRate
            val amplitude = sin(2 * PI * frequency * time).toFloat()
            val sample = (amplitude * Short.MAX_VALUE).toInt().toShort()
            buffer.putShort(sample)
        }

        return audioData
    }

    private fun generateSilence(durationSamples: Int): ByteArray {
        return ByteArray(durationSamples * 2) // All zeros for 16-bit silence
    }

    private fun generateSpeechLikeAudio(durationSamples: Int): ByteArray {
        // Generate formant-like frequencies typical of speech
        val audioData = ByteArray(durationSamples * 2)
        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until durationSamples) {
            val time = i.toFloat() / SAMPLE_RATE
            // Mix multiple formants for speech-like character
            val f1 = sin(2 * PI * 800 * time) * 0.5f  // First formant
            val f2 = sin(2 * PI * 1200 * time) * 0.3f // Second formant
            val f3 = sin(2 * PI * 2500 * time) * 0.2f // Third formant

            val amplitude = (f1 + f2 + f3) * 0.7f
            val sample = (amplitude * Short.MAX_VALUE).toInt().toShort()
            buffer.putShort(sample)
        }

        return audioData
    }

    private fun generateMusicLikeAudio(durationSamples: Int): ByteArray {
        // Generate chord-like audio (C major chord)
        val audioData = ByteArray(durationSamples * 2)
        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until durationSamples) {
            val time = i.toFloat() / SAMPLE_RATE
            val c = sin(2 * PI * 261.63 * time) // C4
            val e = sin(2 * PI * 329.63 * time) // E4
            val g = sin(2 * PI * 392.00 * time) // G4

            val amplitude = (c + e + g) / 3.0f * 0.8f
            val sample = (amplitude * Short.MAX_VALUE).toInt().toShort()
            buffer.putShort(sample)
        }

        return audioData
    }

    private fun generateWhiteNoise(durationSamples: Int): ByteArray {
        val audioData = ByteArray(durationSamples * 2)
        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until durationSamples) {
            val amplitude = (Math.random() - 0.5) * 0.1 // Low-level noise
            val sample = (amplitude * Short.MAX_VALUE).toInt().toShort()
            buffer.putShort(sample)
        }

        return audioData
    }

    private fun generateWhisperLikeAudio(durationSamples: Int): ByteArray {
        // Generate low-amplitude speech-like audio
        val speechAudio = generateSpeechLikeAudio(durationSamples)
        val buffer = ByteBuffer.wrap(speechAudio).order(ByteOrder.LITTLE_ENDIAN)

        // Reduce amplitude to whisper level
        for (i in 0 until durationSamples) {
            val sample = buffer.getShort(i * 2)
            val whisperSample = (sample * 0.3f).toInt().toShort()
            buffer.putShort(i * 2, whisperSample)
        }

        return speechAudio
    }

    private fun calculateDominantFrequency(audioData: ByteArray, sampleRate: Int): Float {
        // Simple FFT-like frequency detection (simplified for testing)
        val samples = audioData.size / 2
        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)

        var maxMagnitude = 0.0
        var dominantFreq = 0.0f

        // Test frequencies from 50Hz to 4000Hz
        for (freq in 50..4000 step 10) {
            var magnitude = 0.0

            for (i in 0 until samples) {
                val sample = buffer.getShort(i * 2).toFloat() / Short.MAX_VALUE
                val time = i.toFloat() / sampleRate
                val cosine = cos(2 * PI * freq * time)
                magnitude += sample * cosine
            }

            magnitude = abs(magnitude)
            if (magnitude > maxMagnitude) {
                maxMagnitude = magnitude
                dominantFreq = freq.toFloat()
            }
        }

        return dominantFreq
    }

    // Test helper classes
    data class AudioConfig(
        val sampleRate: Int,
        val channelConfig: Int,
        val audioFormat: Int
    )

    data class AudioFormat(
        val sampleRate: Int,
        val bitsPerSample: Int,
        val channels: Int
    )

    data class NetworkCondition(
        val quality: String,
        val bandwidthMbps: Int,
        val rttMs: Int
    )

    data class AudioSettings(
        val compressionLevel: Int,
        val chunkSizeMs: Long,
        val enableNoiseReduction: Boolean
    )

    // Test implementation of audio processor
    private class TestAudioProcessor(
        private val audioStreamFlow: MutableSharedFlow<ByteArray>,
        private val vadResultFlow: MutableSharedFlow<VoiceActivityResult>,
        private val scope: CoroutineScope
    ) {
        fun validateAudioFormat(config: AudioConfig): Boolean {
            return config.sampleRate == SAMPLE_RATE &&
                   config.channelConfig == CHANNEL_CONFIG &&
                   config.audioFormat == AUDIO_FORMAT
        }

        fun calculateOptimalBufferSize(): Int {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            return maxOf(minBufferSize, 2048) * BUFFER_SIZE_MULTIPLIER
        }

        fun processAudioForVAD(audioData: ByteArray) {
            scope.launch {
                val energyLevel = calculateEnergyLevel(audioData)
                val hasVoice = energyLevel > VAD_THRESHOLD
                val confidence = if (hasVoice) minOf(energyLevel * 2, 1.0f) else maxOf(1.0f - energyLevel * 2, 0.0f)

                vadResultFlow.emit(VoiceActivityResult(
                    hasVoice = hasVoice,
                    confidence = confidence,
                    energyLevel = energyLevel,
                    timestamp = System.currentTimeMillis()
                ))
            }
        }

        fun adaptAudioQuality(networkCondition: NetworkCondition): AudioSettings {
            return when (networkCondition.quality) {
                "excellent" -> AudioSettings(0, 100, false)
                "good" -> AudioSettings(1, 200, false)
                "fair" -> AudioSettings(3, 500, true)
                "poor" -> AudioSettings(5, 1000, true)
                else -> AudioSettings(3, 500, true)
            }
        }

        fun encodeAudioToBase64(audioData: ByteArray): String {
            return android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP)
        }

        fun decodeAudioFromBase64(encodedAudio: String): ByteArray {
            return android.util.Base64.decode(encodedAudio, android.util.Base64.NO_WRAP)
        }

        fun convertToRequiredFormat(audioData: ByteArray, inputFormat: AudioFormat): ByteArray {
            // Simplified resampling (in real implementation, use proper DSP)
            val inputSamples = audioData.size / 2
            val resampleRatio = SAMPLE_RATE.toFloat() / inputFormat.sampleRate
            val outputSamples = (inputSamples * resampleRatio).toInt()
            val outputData = ByteArray(outputSamples * 2)

            val inputBuffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
            val outputBuffer = ByteBuffer.wrap(outputData).order(ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until outputSamples) {
                val inputIndex = (i / resampleRatio).toInt()
                if (inputIndex < inputSamples) {
                    val sample = inputBuffer.getShort(inputIndex * 2)
                    outputBuffer.putShort(sample)
                }
            }

            return outputData
        }

        fun processAudioChunk(audioData: ByteArray) {
            // Simulate audio processing work
            calculateEnergyLevel(audioData)
        }

        private fun calculateEnergyLevel(audioData: ByteArray): Float {
            val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
            var energy = 0.0
            val samples = audioData.size / 2

            for (i in 0 until samples) {
                val sample = buffer.getShort(i * 2).toFloat() / Short.MAX_VALUE
                energy += sample * sample
            }

            return sqrt(energy / samples).toFloat()
        }
    }
}