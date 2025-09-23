package com.posecoach.app.overlay

import android.graphics.Matrix
import android.graphics.PointF
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test suite for RotationHandler following TDD principles.
 * Tests rotation logic functionality (<200 lines target).
 */
class RotationHandlerTest {

    private lateinit var rotationHandler: RotationHandler

    @Before
    fun setUp() {
        // This test will fail until RotationHandler is implemented
        rotationHandler = RotationHandler()
    }

    @Test
    fun `should handle 0 degree rotation`() {
        // Given
        val point = PointF(100f, 100f)
        val viewWidth = 400
        val viewHeight = 600

        // When
        rotationHandler.setRotation(0, viewWidth, viewHeight)
        val rotatedPoint = rotationHandler.rotatePoint(point)

        // Then
        assertEquals(100f, rotatedPoint.x, 0.1f)
        assertEquals(100f, rotatedPoint.y, 0.1f)
    }

    @Test
    fun `should handle 90 degree rotation`() {
        // Given
        val point = PointF(100f, 100f)
        val viewWidth = 400
        val viewHeight = 600

        // When
        rotationHandler.setRotation(90, viewWidth, viewHeight)
        val rotatedPoint = rotationHandler.rotatePoint(point)

        // Then
        // 90 degree rotation should swap coordinates and adjust
        assertTrue(rotatedPoint.x != 100f || rotatedPoint.y != 100f)
    }

    @Test
    fun `should handle 180 degree rotation`() {
        // Given
        val point = PointF(100f, 100f)
        val viewWidth = 400
        val viewHeight = 600

        // When
        rotationHandler.setRotation(180, viewWidth, viewHeight)
        val rotatedPoint = rotationHandler.rotatePoint(point)

        // Then
        // 180 degree rotation should invert coordinates
        assertEquals(300f, rotatedPoint.x, 5f) // viewWidth - x
        assertEquals(500f, rotatedPoint.y, 5f) // viewHeight - y
    }

    @Test
    fun `should handle 270 degree rotation`() {
        // Given
        val point = PointF(100f, 100f)
        val viewWidth = 400
        val viewHeight = 600

        // When
        rotationHandler.setRotation(270, viewWidth, viewHeight)
        val rotatedPoint = rotationHandler.rotatePoint(point)

        // Then
        // 270 degree rotation should swap and invert coordinates
        assertTrue(rotatedPoint.x != 100f || rotatedPoint.y != 100f)
    }

    @Test
    fun `should handle arbitrary rotation angles`() {
        // Given
        val point = PointF(100f, 100f)
        val viewWidth = 400
        val viewHeight = 600

        // When
        rotationHandler.setRotation(45, viewWidth, viewHeight)
        val rotatedPoint = rotationHandler.rotatePoint(point)

        // Then
        assertTrue(rotatedPoint.x != 100f || rotatedPoint.y != 100f)
    }

    @Test
    fun `should normalize rotation angles`() {
        // Given
        val viewWidth = 400
        val viewHeight = 600

        // When
        rotationHandler.setRotation(450, viewWidth, viewHeight) // 450 = 90 degrees
        val normalizedAngle = rotationHandler.getNormalizedRotation()

        // Then
        assertEquals(90, normalizedAngle)
    }

    @Test
    fun `should handle negative rotation angles`() {
        // Given
        val viewWidth = 400
        val viewHeight = 600

        // When
        rotationHandler.setRotation(-90, viewWidth, viewHeight)
        val normalizedAngle = rotationHandler.getNormalizedRotation()

        // Then
        assertEquals(270, normalizedAngle) // -90 = 270 degrees
    }

    @Test
    fun `should rotate multiple points consistently`() {
        // Given
        val points = listOf(
            PointF(0f, 0f),
            PointF(100f, 100f),
            PointF(200f, 300f)
        )
        val viewWidth = 400
        val viewHeight = 600

        // When
        rotationHandler.setRotation(90, viewWidth, viewHeight)
        val rotatedPoints = rotationHandler.rotatePoints(points)

        // Then
        assertEquals(points.size, rotatedPoints.size)
        // All points should be transformed consistently
        rotatedPoints.forEach { point ->
            assertTrue(point.x >= 0f && point.x <= viewWidth)
            assertTrue(point.y >= 0f && point.y <= viewHeight)
        }
    }

    @Test
    fun `should get rotation matrix`() {
        // Given
        val viewWidth = 400
        val viewHeight = 600

        // When
        rotationHandler.setRotation(90, viewWidth, viewHeight)
        val matrix = rotationHandler.getRotationMatrix()

        // Then
        assertTrue(matrix.isIdentity.not()) // Should not be identity matrix for 90 degrees
    }

    @Test
    fun `should reset rotation`() {
        // Given
        val viewWidth = 400
        val viewHeight = 600
        rotationHandler.setRotation(90, viewWidth, viewHeight)

        // When
        rotationHandler.resetRotation()

        // Then
        assertEquals(0, rotationHandler.getNormalizedRotation())
    }

    @Test
    fun `should handle view dimension changes`() {
        // Given
        val point = PointF(100f, 100f)
        rotationHandler.setRotation(90, 400, 600)

        // When
        rotationHandler.updateViewDimensions(800, 1200)
        val rotatedPoint = rotationHandler.rotatePoint(point)

        // Then
        // Point should be rotated according to new dimensions
        assertTrue(rotatedPoint.x >= 0f && rotatedPoint.x <= 800)
        assertTrue(rotatedPoint.y >= 0f && rotatedPoint.y <= 1200)
    }
}