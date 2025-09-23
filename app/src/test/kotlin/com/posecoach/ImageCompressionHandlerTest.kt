package com.posecoach.app.livecoach.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.test.AfterTest

/**
 * Unit tests for ImageCompressionHandler
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImageCompressionHandlerTest {

    private lateinit var compressionHandler: ImageCompressionHandler
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        compressionHandler = ImageCompressionHandler(testScope)
    }

    @AfterTest
    fun tearDown() {
        compressionHandler.destroy()
    }

    @Test
    fun processImageProxy_withJpegFormat_decodesSuccessfully() = testScope.runTest {
        // Mock JPEG ImageProxy
        val imageProxy = mockk<ImageProxy> {
            every { format } returns ImageFormat.JPEG
            every { width } returns 640
            every { height } returns 480
            every { planes } returns arrayOf(
                mockk {
                    every { buffer } returns ByteBuffer.wrap(createMockJpegBytes())
                }
            )
        }

        val result = compressionHandler.processImageProxy(imageProxy)

        assertTrue(result.success)
        assertTrue(result.fileSizeBytes > 0)
        assertTrue(result.processingTimeMs >= 0)
        assertNull(result.error)
    }

    @Test
    fun processImageProxy_withYuvFormat_convertsSuccessfully() = testScope.runTest {
        // Mock YUV ImageProxy
        val imageProxy = mockYuv420ImageProxy(320, 240)

        val result = compressionHandler.processImageProxy(imageProxy)

        assertTrue(result.success)
        assertTrue(result.fileSizeBytes > 0)
        assertTrue(result.processingTimeMs >= 0)
        assertNull(result.error)
    }

    @Test
    fun processImageProxy_withUnsupportedFormat_returnsError() = testScope.runTest {
        val imageProxy = mockk<ImageProxy> {
            every { format } returns 999 // Unsupported format
            every { width } returns 640
            every { height } returns 480
        }

        val result = compressionHandler.processImageProxy(imageProxy)

        assertFalse(result.success)
        assertEquals(0, result.fileSizeBytes)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Unsupported"))
    }

    @Test
    fun resizeBitmap_maintainsAspectRatio() {
        val originalBitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)

        val resized = compressionHandler.resizeBitmap(originalBitmap, 320, 240)

        assertEquals(320, resized.width)
        assertEquals(240, resized.height)

        // Clean up
        originalBitmap.recycle()
        if (resized != originalBitmap) {
            resized.recycle()
        }
    }

    @Test
    fun resizeBitmap_withPortraitImage_maintainsAspectRatio() {
        val originalBitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)

        val resized = compressionHandler.resizeBitmap(originalBitmap, 320, 240)

        // Should scale to fit within bounds while maintaining aspect ratio
        assertTrue(resized.width <= 320)
        assertTrue(resized.height <= 240)

        // Aspect ratio should be preserved (within tolerance)
        val originalRatio = 480f / 640f
        val resizedRatio = resized.width.toFloat() / resized.height.toFloat()
        assertEquals(originalRatio, resizedRatio, 0.01f)

        // Clean up
        originalBitmap.recycle()
        if (resized != originalBitmap) {
            resized.recycle()
        }
    }

    @Test
    fun resizeBitmap_withSameSize_returnsOriginal() {
        val originalBitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)

        val resized = compressionHandler.resizeBitmap(originalBitmap, 320, 240)

        // Should return the same bitmap when no resizing needed
        assertSame(originalBitmap, resized)

        // Clean up
        originalBitmap.recycle()
    }

    @Test
    fun compressBitmap_withDifferentQualities_variesFileSize() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        val lowQuality = compressionHandler.compressBitmap(bitmap, CompressionQuality.LOW.value)
        val highQuality = compressionHandler.compressBitmap(bitmap, CompressionQuality.HIGH.value)

        // High quality should produce larger file than low quality
        assertTrue(highQuality.size >= lowQuality.size)

        bitmap.recycle()
    }

    @Test
    fun compressBitmap_returnsValidJpegBytes() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        val jpegBytes = compressionHandler.compressBitmap(bitmap, 70)

        assertTrue(jpegBytes.isNotEmpty())

        // Check JPEG header (starts with 0xFF 0xD8)
        assertEquals(0xFF.toByte(), jpegBytes[0])
        assertEquals(0xD8.toByte(), jpegBytes[1])

        bitmap.recycle()
    }

    @Test
    fun getCompressionMetrics_tracksOperations() {
        val metrics = compressionHandler.getCompressionMetrics()

        assertEquals(0L, metrics.totalImages)
        assertEquals(0L, metrics.totalBytes)
        assertEquals(0f, metrics.averageCompressionRatio, 0.01f)
        assertEquals(0L, metrics.averageProcessingTimeMs)
    }

    @Test
    fun getSupportedFormats_includesCommonFormats() {
        val formats = compressionHandler.getSupportedFormats()

        val jpegFormat = formats.find { it.format == ImageFormat.JPEG }
        assertNotNull(jpegFormat)
        assertTrue(jpegFormat!!.supportedByProcessor)
        assertFalse(jpegFormat.requiresConversion)

        val yuvFormat = formats.find { it.format == ImageFormat.YUV_420_888 }
        assertNotNull(yuvFormat)
        assertTrue(yuvFormat!!.supportedByProcessor)
        assertTrue(yuvFormat.requiresConversion)
    }

    @Test
    fun destroy_cleansUpResources() {
        assertFalse(compressionHandler.isDestroyed())

        compressionHandler.destroy()

        assertTrue(compressionHandler.isDestroyed())
    }

    // Helper methods
    private fun createMockJpegBytes(): ByteArray {
        // Create minimal valid JPEG header
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // JPEG start marker
            0xFF.toByte(), 0xE0.toByte(), // APP0 marker
            0x00, 0x10, // Length
            0x4A, 0x46, 0x49, 0x46, 0x00, // "JFIF"
            0x01, 0x01, 0x01, 0x00, 0x48, 0x00, 0x48, 0x00, 0x00,
            0xFF.toByte(), 0xD9.toByte() // JPEG end marker
        )
    }

    private fun mockYuv420ImageProxy(width: Int, height: Int): ImageProxy {
        val ySize = width * height
        val uvSize = ySize / 4

        val yBuffer = ByteBuffer.allocate(ySize)
        val uBuffer = ByteBuffer.allocate(uvSize)
        val vBuffer = ByteBuffer.allocate(uvSize)

        // Fill with mock data
        repeat(ySize) { yBuffer.put(128.toByte()) }
        repeat(uvSize) { uBuffer.put(128.toByte()) }
        repeat(uvSize) { vBuffer.put(128.toByte()) }

        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        return mockk {
            every { format } returns ImageFormat.YUV_420_888
            every { this@mockk.width } returns width
            every { this@mockk.height } returns height
            every { planes } returns arrayOf(
                mockk {
                    every { buffer } returns yBuffer
                    every { pixelStride } returns 1
                    every { rowStride } returns width
                },
                mockk {
                    every { buffer } returns uBuffer
                    every { pixelStride } returns 1
                    every { rowStride } returns width / 2
                },
                mockk {
                    every { buffer } returns vBuffer
                    every { pixelStride } returns 1
                    every { rowStride } returns width / 2
                }
            )
        }
    }
}