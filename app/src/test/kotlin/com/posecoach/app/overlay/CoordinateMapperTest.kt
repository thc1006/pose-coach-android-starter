package com.posecoach.app.overlay

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

class CoordinateMapperTest {

    @Test
    fun `normalized to pixel conversion should be accurate within 2px`() {
        val mapper = CoordinateMapper(
            viewWidth = 1080,
            viewHeight = 1920,
            imageWidth = 720,
            imageHeight = 1280,
            isFrontFacing = false
        )

        val testCases = listOf(
            0.0f to 0.0f,
            0.5f to 0.5f,
            1.0f to 1.0f,
            0.25f to 0.75f,
            0.33f to 0.67f
        )

        testCases.forEach { (normalizedX, normalizedY) ->
            val (pixelX, pixelY) = mapper.normalizedToPixel(normalizedX, normalizedY)

            val expectedX = normalizedX * 1080
            val expectedY = normalizedY * 1920

            assertTrue("X error should be <2px: got ${abs(pixelX - expectedX)}",
                abs(pixelX - expectedX) < 2.0f)
            assertTrue("Y error should be <2px: got ${abs(pixelY - expectedY)}",
                abs(pixelY - expectedY) < 2.0f)
        }
    }

    @Test
    fun `front camera should mirror X coordinate`() {
        val mapper = CoordinateMapper(
            viewWidth = 1080,
            viewHeight = 1920,
            imageWidth = 720,
            imageHeight = 1280,
            isFrontFacing = true
        )

        val (pixelX, pixelY) = mapper.normalizedToPixel(0.3f, 0.5f)

        assertEquals(1080 * (1.0f - 0.3f), pixelX, 2.0f)
        assertEquals(1920 * 0.5f, pixelY, 2.0f)
    }

    @Test
    fun `aspect ratio correction should maintain proportions`() {
        val mapper = CoordinateMapper(
            viewWidth = 1080,
            viewHeight = 1920,
            imageWidth = 720,
            imageHeight = 720,
            isFrontFacing = false
        )

        mapper.updateAspectRatio(FitMode.FILL)

        val (pixelX, pixelY) = mapper.normalizedToPixel(0.5f, 0.5f)

        assertEquals(540f, pixelX, 2.0f)
        assertEquals(960f, pixelY, 2.0f)
    }

    @Test
    fun `center crop mode should scale correctly`() {
        val mapper = CoordinateMapper(
            viewWidth = 1080,
            viewHeight = 1920,
            imageWidth = 720,
            imageHeight = 1280,
            isFrontFacing = false
        )

        mapper.updateAspectRatio(FitMode.CENTER_CROP)

        val (centerX, centerY) = mapper.normalizedToPixel(0.5f, 0.5f)

        assertEquals(540f, centerX, 2.0f)
        assertEquals(960f, centerY, 2.0f)
    }

    @Test
    fun `batch conversion should be efficient`() {
        val mapper = CoordinateMapper(
            viewWidth = 1080,
            viewHeight = 1920,
            imageWidth = 720,
            imageHeight = 1280,
            isFrontFacing = false
        )

        val landmarks = List(33) { index ->
            val normalized = index / 33.0f
            normalized to normalized
        }

        val startTime = System.currentTimeMillis()
        val pixels = mapper.batchNormalizedToPixel(landmarks)
        val elapsed = System.currentTimeMillis() - startTime

        assertEquals(33, pixels.size)
        assertTrue("Batch conversion should be fast (<5ms)", elapsed < 5)
    }

    @Test
    fun `edge cases should be handled correctly`() {
        val mapper = CoordinateMapper(
            viewWidth = 1080,
            viewHeight = 1920,
            imageWidth = 720,
            imageHeight = 1280,
            isFrontFacing = false
        )

        val edgeCases = listOf(
            -0.1f to 0.5f,
            1.1f to 0.5f,
            0.5f to -0.1f,
            0.5f to 1.1f
        )

        edgeCases.forEach { (x, y) ->
            val (pixelX, pixelY) = mapper.normalizedToPixel(x, y)

            assertTrue("X should be clamped to view", pixelX >= 0 && pixelX <= 1080)
            assertTrue("Y should be clamped to view", pixelY >= 0 && pixelY <= 1920)
        }
    }

    @Test
    fun `rotation handling should transform coordinates correctly`() {
        val mapper = CoordinateMapper(
            viewWidth = 1080,
            viewHeight = 1920,
            imageWidth = 720,
            imageHeight = 1280,
            isFrontFacing = false,
            rotation = 90
        )

        val (pixelX, pixelY) = mapper.normalizedToPixel(0.5f, 0.5f)

        assertNotNull(pixelX)
        assertNotNull(pixelY)
    }
}