package com.posecoach.testing.mocks.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.posecoach.testing.mocks.MockService
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mock camera manager for testing CameraX integration
 * Simulates camera operations including:
 * - Camera initialization and configuration
 * - Image capture and processing
 * - Camera permission handling
 * - Various camera error conditions
 */
class MockCameraManager : MockService {

    private val interactionCount = AtomicInteger(0)
    private val _cameraState = MutableStateFlow(CameraState.UNINITIALIZED)
    private val _currentFrame = MutableStateFlow<ImageProxy?>(null)

    // Test configuration
    var shouldSimulatePermissionDenied = false
    var shouldSimulateInitializationFailure = false
    var shouldSimulateFrameProcessingErrors = false
    var frameCaptureLatencyMs = 0L
    var customFrameSequence: List<MockImageProxy>? = null
    private var frameSequenceIndex = 0

    val cameraState: StateFlow<CameraState> = _cameraState
    val currentFrame: StateFlow<ImageProxy?> = _currentFrame

    suspend fun initialize(): Result<Unit> {
        interactionCount.incrementAndGet()

        return when {
            shouldSimulatePermissionDenied -> {
                Timber.w("MockCameraManager: Simulating permission denied")
                _cameraState.value = CameraState.PERMISSION_DENIED
                Result.failure(SecurityException("Camera permission denied"))
            }
            shouldSimulateInitializationFailure -> {
                Timber.w("MockCameraManager: Simulating initialization failure")
                _cameraState.value = CameraState.ERROR
                Result.failure(Exception("Camera initialization failed"))
            }
            else -> {
                simulateDelay(200) // Simulate camera startup time
                _cameraState.value = CameraState.READY
                Timber.d("MockCameraManager: Initialized successfully")
                Result.success(Unit)
            }
        }
    }

    suspend fun startCapture(): Result<Unit> {
        interactionCount.incrementAndGet()

        if (_cameraState.value != CameraState.READY) {
            return Result.failure(IllegalStateException("Camera not ready"))
        }

        _cameraState.value = CameraState.CAPTURING
        startFrameCapture()

        return Result.success(Unit)
    }

    suspend fun stopCapture() {
        interactionCount.incrementAndGet()
        _cameraState.value = CameraState.READY
        _currentFrame.value = null
        Timber.d("MockCameraManager: Capture stopped")
    }

    suspend fun release() {
        interactionCount.incrementAndGet()
        _cameraState.value = CameraState.UNINITIALIZED
        _currentFrame.value = null
        Timber.d("MockCameraManager: Released")
    }

    private suspend fun startFrameCapture() {
        if (frameCaptureLatencyMs > 0) {
            simulateDelay(frameCaptureLatencyMs)
        }

        if (shouldSimulateFrameProcessingErrors) {
            _cameraState.value = CameraState.ERROR
            return
        }

        val frame = when {
            customFrameSequence != null -> {
                val sequence = customFrameSequence!!
                if (sequence.isNotEmpty()) {
                    val frame = sequence[frameSequenceIndex % sequence.size]
                    frameSequenceIndex++
                    frame
                } else null
            }
            else -> generateMockFrame()
        }

        _currentFrame.value = frame
    }

    private fun generateMockFrame(): MockImageProxy {
        return MockImageProxy(
            width = 1920,
            height = 1080,
            format = android.graphics.ImageFormat.YUV_420_888,
            timestamp = System.nanoTime(),
            rotationDegrees = 0
        )
    }

    // Test utility methods

    /**
     * Configure the mock to simulate specific test scenarios
     */
    fun configureTestScenario(scenario: TestScenario) {
        when (scenario) {
            TestScenario.PERMISSION_DENIED -> {
                shouldSimulatePermissionDenied = true
            }
            TestScenario.INITIALIZATION_FAILURE -> {
                shouldSimulateInitializationFailure = true
            }
            TestScenario.FRAME_PROCESSING_ERRORS -> {
                shouldSimulateFrameProcessingErrors = true
            }
            TestScenario.HIGH_LATENCY -> {
                frameCaptureLatencyMs = 1000L
            }
            TestScenario.CUSTOM_FRAMES -> {
                customFrameSequence = generateTestFrameSequence()
            }
            TestScenario.NORMAL -> {
                reset()
            }
        }
    }

    /**
     * Set custom frame sequence for testing specific scenarios
     */
    fun setCustomFrameSequence(frames: List<MockImageProxy>) {
        customFrameSequence = frames
        frameSequenceIndex = 0
    }

    /**
     * Set frame capture latency for performance testing
     */
    fun setFrameCaptureLatency(latencyMs: Long) {
        frameCaptureLatencyMs = latencyMs
    }

    private fun generateTestFrameSequence(): List<MockImageProxy> {
        return listOf(
            MockImageProxy(1920, 1080, android.graphics.ImageFormat.YUV_420_888, System.nanoTime(), 0),
            MockImageProxy(1920, 1080, android.graphics.ImageFormat.YUV_420_888, System.nanoTime(), 90),
            MockImageProxy(1920, 1080, android.graphics.ImageFormat.YUV_420_888, System.nanoTime(), 180),
            MockImageProxy(1920, 1080, android.graphics.ImageFormat.YUV_420_888, System.nanoTime(), 270)
        )
    }

    private suspend fun simulateDelay(ms: Long) {
        kotlinx.coroutines.delay(ms)
    }

    // MockService implementation

    override fun reset() {
        shouldSimulatePermissionDenied = false
        shouldSimulateInitializationFailure = false
        shouldSimulateFrameProcessingErrors = false
        frameCaptureLatencyMs = 0L
        customFrameSequence = null
        frameSequenceIndex = 0
        interactionCount.set(0)
        _cameraState.value = CameraState.UNINITIALIZED
        _currentFrame.value = null
    }

    override fun getInteractionCount(): Int = interactionCount.get()

    enum class CameraState {
        UNINITIALIZED,
        PERMISSION_DENIED,
        READY,
        CAPTURING,
        ERROR
    }

    enum class TestScenario {
        NORMAL,
        PERMISSION_DENIED,
        INITIALIZATION_FAILURE,
        FRAME_PROCESSING_ERRORS,
        HIGH_LATENCY,
        CUSTOM_FRAMES
    }
}

/**
 * Mock implementation of ImageProxy for testing
 */
class MockImageProxy(
    private val mockWidth: Int,
    private val mockHeight: Int,
    private val mockFormat: Int,
    private val mockTimestamp: Long,
    private val mockRotationDegrees: Int
) : ImageProxy {

    override fun close() {
        // Mock implementation - no resources to close
    }

    override fun getCropRect(): android.graphics.Rect {
        return android.graphics.Rect(0, 0, mockWidth, mockHeight)
    }

    override fun getFormat(): Int = mockFormat

    override fun getHeight(): Int = mockHeight

    override fun getWidth(): Int = mockWidth

    override fun getPlanes(): Array<ImageProxy.PlaneProxy> {
        // Return mock planes for YUV_420_888 format
        return arrayOf(
            MockPlaneProxy(mockWidth * mockHeight),  // Y plane
            MockPlaneProxy(mockWidth * mockHeight / 4),  // U plane
            MockPlaneProxy(mockWidth * mockHeight / 4)   // V plane
        )
    }

    override fun getImageInfo(): androidx.camera.core.ImageInfo {
        return MockImageInfo(mockTimestamp, mockRotationDegrees)
    }

    override fun setCropRect(rect: android.graphics.Rect?) {
        // Mock implementation
    }

    /**
     * Mock implementation of PlaneProxy
     */
    private class MockPlaneProxy(private val bufferSize: Int) : ImageProxy.PlaneProxy {

        override fun getRowStride(): Int = bufferSize

        override fun getPixelStride(): Int = 1

        override fun getBuffer(): java.nio.ByteBuffer {
            return java.nio.ByteBuffer.allocate(bufferSize)
        }
    }

    /**
     * Mock implementation of ImageInfo
     */
    private class MockImageInfo(
        private val mockTimestamp: Long,
        private val mockRotationDegrees: Int
    ) : androidx.camera.core.ImageInfo {

        override fun getTimestamp(): Long = mockTimestamp

        override fun getRotationDegrees(): Int = mockRotationDegrees

        override fun getTagBundle(): androidx.camera.core.impl.TagBundle {
            return androidx.camera.core.impl.TagBundle.emptyBundle()
        }
    }
}