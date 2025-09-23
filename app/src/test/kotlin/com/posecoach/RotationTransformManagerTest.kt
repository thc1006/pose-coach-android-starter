package com.posecoach.app.camera

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.util.Size
import android.view.Surface
import com.posecoach.app.overlay.FitMode
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber
import kotlin.math.abs

/**
 * TDD test suite for RotationTransformManager.
 * These tests are designed to FAIL initially to expose rotation transformation bugs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RotationTransformManagerTest {

    private lateinit var transformManager: RotationTransformManager

    // Test dimensions
    private val sourceSize = Size(720, 1280)
    private val targetSize = Size(1080, 2340)

    // Error tolerance
    private val errorTolerance = 2.0f

    @Before
    fun setup() {
        // Mock Timber to avoid log errors
        mockkStatic(Timber::class)
        every { Timber.d(any<String>()) } just Runs
        every { Timber.e(any<Throwable>(), any<String>()) } just Runs
        every { Timber.e(any<String>()) } just Runs
        every { Timber.v(any<String>()) } just Runs
        every { Timber.w(any<String>()) } just Runs

        transformManager = RotationTransformManager()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test portrait mode no rotation transformation`() {
        // ARRANGE: Portrait mode configuration (0° rotation)
        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 90,
            displayRotation = Surface.ROTATION_0,
            isFrontFacing = false,
            fitMode = FitMode.CENTER_CROP
        )

        // ACT: Calculate transformation
        val result = transformManager.calculateTransformation(config)

        // ASSERT: Should produce valid transformation
        assertTrue("Transformation should be valid", result.isValid)
        assertNotNull("Matrix should not be null", result.matrix)
        assertEquals("Display rotation should match input", Surface.ROTATION_0, result.rotationDegrees)

        // Test center point transformation
        val centerPoint = PointF(sourceSize.width / 2f, sourceSize.height / 2f)
        val transformedCenter = transformManager.transformPoint(result.matrix, centerPoint)

        val expectedCenterX = targetSize.width / 2f
        val expectedCenterY = targetSize.height / 2f

        val errorX = abs(transformedCenter.x - expectedCenterX)
        val errorY = abs(transformedCenter.y - expectedCenterY)

        assertTrue(
            "Center point X transformation error: expected $expectedCenterX, got ${transformedCenter.x}, error $errorX",
            errorX <= errorTolerance
        )
        assertTrue(
            "Center point Y transformation error: expected $expectedCenterY, got ${transformedCenter.y}, error $errorY",
            errorY <= errorTolerance
        )
    }

    @Test
    fun `test landscape mode 90 degree rotation transformation`() {
        // ARRANGE: Landscape mode configuration (90° rotation)
        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = Size(targetSize.height, targetSize.width), // Swapped for landscape
            sensorOrientation = 90,
            displayRotation = Surface.ROTATION_90,
            isFrontFacing = false,
            fitMode = FitMode.CENTER_CROP
        )

        // ACT: Calculate transformation
        val result = transformManager.calculateTransformation(config)

        // ASSERT: Should handle 90° rotation correctly
        assertTrue("Transformation should be valid for 90° rotation", result.isValid)

        // Test that center remains at center after rotation
        val centerPoint = PointF(sourceSize.width / 2f, sourceSize.height / 2f)
        val transformedCenter = transformManager.transformPoint(result.matrix, centerPoint)

        val expectedCenterX = config.targetSize.width / 2f
        val expectedCenterY = config.targetSize.height / 2f

        val errorX = abs(transformedCenter.x - expectedCenterX)
        val errorY = abs(transformedCenter.y - expectedCenterY)

        assertTrue(
            "90° rotation center X error: expected $expectedCenterX, got ${transformedCenter.x}, error $errorX",
            errorX <= errorTolerance
        )
        assertTrue(
            "90° rotation center Y error: expected $expectedCenterY, got ${transformedCenter.y}, error $errorY",
            errorY <= errorTolerance
        )
    }

    @Test
    fun `test front camera mirroring transformation`() {
        // ARRANGE: Front camera configuration with mirroring
        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 270, // Typical front camera orientation
            displayRotation = Surface.ROTATION_0,
            isFrontFacing = true,
            fitMode = FitMode.CENTER_CROP,
            mirrorMode = RotationTransformManager.MirrorMode.AUTO
        )

        // ACT: Calculate transformation
        val result = transformManager.calculateTransformation(config)

        // ASSERT: Should handle front camera mirroring
        assertTrue("Front camera transformation should be valid", result.isValid)

        // Test left side point becomes right side (mirroring)
        val leftPoint = PointF(sourceSize.width * 0.2f, sourceSize.height / 2f)
        val transformedLeft = transformManager.transformPoint(result.matrix, leftPoint)

        // With mirroring, left side should map to right side of target
        val expectedRightSide = targetSize.width * 0.8f  // Mirrored position

        val errorX = abs(transformedLeft.x - expectedRightSide)

        assertTrue(
            "Front camera mirroring error: left point should map to right side. Expected ~$expectedRightSide, got ${transformedLeft.x}, error $errorX",
            errorX <= errorTolerance * 5 // Allow more tolerance for mirroring
        )
    }

    @Test
    fun `test 180 degree rotation transformation`() {
        // ARRANGE: 180° rotation configuration
        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 90,
            displayRotation = Surface.ROTATION_180,
            isFrontFacing = false,
            fitMode = FitMode.CENTER_CROP
        )

        // ACT: Calculate transformation
        val result = transformManager.calculateTransformation(config)

        // ASSERT: Should handle 180° rotation
        assertTrue("180° rotation transformation should be valid", result.isValid)

        // Test top-left point becomes bottom-right
        val topLeftPoint = PointF(0f, 0f)
        val transformedTopLeft = transformManager.transformPoint(result.matrix, topLeftPoint)

        // After 180° rotation, top-left should map to bottom-right area
        val expectedX = targetSize.width.toFloat()
        val expectedY = targetSize.height.toFloat()

        val errorX = abs(transformedTopLeft.x - expectedX)
        val errorY = abs(transformedTopLeft.y - expectedY)

        assertTrue(
            "180° rotation point transformation X error: expected $expectedX, got ${transformedTopLeft.x}, error $errorX",
            errorX <= errorTolerance * 2 // Allow more tolerance for rotation
        )
        assertTrue(
            "180° rotation point transformation Y error: expected $expectedY, got ${transformedTopLeft.y}, error $errorY",
            errorY <= errorTolerance * 2
        )
    }

    @Test
    fun `test 270 degree rotation transformation`() {
        // ARRANGE: 270° rotation configuration
        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 90,
            displayRotation = Surface.ROTATION_270,
            isFrontFacing = false,
            fitMode = FitMode.CENTER_CROP
        )

        // ACT: Calculate transformation
        val result = transformManager.calculateTransformation(config)

        // ASSERT: Should handle 270° rotation
        assertTrue("270° rotation transformation should be valid", result.isValid)

        // Center should remain at center
        val centerPoint = PointF(sourceSize.width / 2f, sourceSize.height / 2f)
        val transformedCenter = transformManager.transformPoint(result.matrix, centerPoint)

        val expectedCenterX = targetSize.width / 2f
        val expectedCenterY = targetSize.height / 2f

        val errorX = abs(transformedCenter.x - expectedCenterX)
        val errorY = abs(transformedCenter.y - expectedCenterY)

        assertTrue(
            "270° rotation center X error: expected $expectedCenterX, got ${transformedCenter.x}, error $errorX",
            errorX <= errorTolerance
        )
        assertTrue(
            "270° rotation center Y error: expected $expectedCenterY, got ${transformedCenter.y}, error $errorY",
            errorY <= errorTolerance
        )
    }

    @Test
    fun `test FILL mode scaling behavior`() {
        // ARRANGE: FILL mode configuration
        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 90,
            displayRotation = Surface.ROTATION_0,
            isFrontFacing = false,
            fitMode = FitMode.FIT_XY
        )

        // ACT: Calculate transformation
        val result = transformManager.calculateTransformation(config)

        // ASSERT: Should scale to fill entire target
        assertTrue("FILL mode transformation should be valid", result.isValid)

        // Test corner mapping
        val corners = listOf(
            PointF(0f, 0f),                                           // Top-left
            PointF(sourceSize.width.toFloat(), 0f),                   // Top-right
            PointF(sourceSize.width.toFloat(), sourceSize.height.toFloat()), // Bottom-right
            PointF(0f, sourceSize.height.toFloat())                   // Bottom-left
        )

        val transformedCorners = transformManager.transformPoints(result.matrix, corners)

        // In FILL mode, corners should map to target corners (allowing for some rounding error)
        val expectedCorners = listOf(
            PointF(0f, 0f),
            PointF(targetSize.width.toFloat(), 0f),
            PointF(targetSize.width.toFloat(), targetSize.height.toFloat()),
            PointF(0f, targetSize.height.toFloat())
        )

        for (i in corners.indices) {
            val errorX = abs(transformedCorners[i].x - expectedCorners[i].x)
            val errorY = abs(transformedCorners[i].y - expectedCorners[i].y)

            assertTrue(
                "FILL mode corner $i X error: expected ${expectedCorners[i].x}, got ${transformedCorners[i].x}, error $errorX",
                errorX <= errorTolerance * 2
            )
            assertTrue(
                "FILL mode corner $i Y error: expected ${expectedCorners[i].y}, got ${transformedCorners[i].y}, error $errorY",
                errorY <= errorTolerance * 2
            )
        }
    }

    @Test
    fun `test CENTER_CROP maintains aspect ratio`() {
        // ARRANGE: CENTER_CROP configuration
        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 90,
            displayRotation = Surface.ROTATION_0,
            isFrontFacing = false,
            fitMode = FitMode.CENTER_CROP
        )

        // ACT: Calculate transformation
        val result = transformManager.calculateTransformation(config)

        // ASSERT: Should maintain aspect ratio
        assertTrue("CENTER_CROP transformation should be valid", result.isValid)

        // Check that aspect ratio is preserved by testing that a square becomes a square
        val squareSize = 100f
        val squareCorners = listOf(
            PointF(sourceSize.width / 2f - squareSize / 2, sourceSize.height / 2f - squareSize / 2),
            PointF(sourceSize.width / 2f + squareSize / 2, sourceSize.height / 2f - squareSize / 2),
            PointF(sourceSize.width / 2f + squareSize / 2, sourceSize.height / 2f + squareSize / 2),
            PointF(sourceSize.width / 2f - squareSize / 2, sourceSize.height / 2f + squareSize / 2)
        )

        val transformedSquare = transformManager.transformPoints(result.matrix, squareCorners)

        // Calculate dimensions of transformed square
        val transformedWidth = abs(transformedSquare[1].x - transformedSquare[0].x)
        val transformedHeight = abs(transformedSquare[2].y - transformedSquare[1].y)

        // In CENTER_CROP with uniform scaling, width and height should be scaled by same factor
        val scaleRatio = abs(transformedWidth - transformedHeight) / transformedWidth

        assertTrue(
            "CENTER_CROP should preserve aspect ratio. Width: $transformedWidth, Height: $transformedHeight, Ratio diff: $scaleRatio",
            scaleRatio <= 0.1f // Allow 10% difference for aspect ratio preservation
        )
    }

    @Test
    fun `test CENTER_INSIDE fits entire source`() {
        // ARRANGE: CENTER_INSIDE configuration
        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 90,
            displayRotation = Surface.ROTATION_0,
            isFrontFacing = false,
            fitMode = FitMode.CENTER_INSIDE
        )

        // ACT: Calculate transformation
        val result = transformManager.calculateTransformation(config)

        // ASSERT: Should fit entire source inside target
        assertTrue("CENTER_INSIDE transformation should be valid", result.isValid)

        // All corners should be within target bounds
        val corners = listOf(
            PointF(0f, 0f),
            PointF(sourceSize.width.toFloat(), 0f),
            PointF(sourceSize.width.toFloat(), sourceSize.height.toFloat()),
            PointF(0f, sourceSize.height.toFloat())
        )

        val transformedCorners = transformManager.transformPoints(result.matrix, corners)

        for (i in corners.indices) {
            assertTrue(
                "CENTER_INSIDE corner $i X should be within bounds: ${transformedCorners[i].x}",
                transformedCorners[i].x >= -errorTolerance && transformedCorners[i].x <= targetSize.width + errorTolerance
            )
            assertTrue(
                "CENTER_INSIDE corner $i Y should be within bounds: ${transformedCorners[i].y}",
                transformedCorners[i].y >= -errorTolerance && transformedCorners[i].y <= targetSize.height + errorTolerance
            )
        }
    }

    @Test
    fun `test batch point transformation efficiency`() {
        // ARRANGE: Configuration and test points
        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 90,
            displayRotation = Surface.ROTATION_0,
            isFrontFacing = false,
            fitMode = FitMode.CENTER_CROP
        )

        val result = transformManager.calculateTransformation(config)

        // Generate many test points (simulating pose landmarks)
        val testPoints = (0..32).map { i ->
            PointF(
                (i % 10) * sourceSize.width / 10f,
                (i / 10) * sourceSize.height / 10f
            )
        }

        // ACT: Transform individually vs batch
        val startTime = System.nanoTime()
        val individualResults = testPoints.map { point ->
            transformManager.transformPoint(result.matrix, point)
        }
        val individualTime = System.nanoTime() - startTime

        val batchStartTime = System.nanoTime()
        val batchResults = transformManager.transformPoints(result.matrix, testPoints)
        val batchTime = System.nanoTime() - batchStartTime

        // ASSERT: Results should match
        assertEquals("Batch and individual result count should match", individualResults.size, batchResults.size)

        for (i in individualResults.indices) {
            val errorX = abs(individualResults[i].x - batchResults[i].x)
            val errorY = abs(individualResults[i].y - batchResults[i].y)

            assertTrue(
                "Batch vs individual X error at point $i: error $errorX",
                errorX <= errorTolerance
            )
            assertTrue(
                "Batch vs individual Y error at point $i: error $errorY",
                errorY <= errorTolerance
            )
        }

        // Batch should be faster or at least comparable
        assertTrue(
            "Batch processing should be efficient. Individual: ${individualTime}ns, Batch: ${batchTime}ns",
            batchTime <= individualTime * 2 // Allow batch to be up to 2x slower due to overhead
        )
    }

    @Test
    fun `test inverse matrix round-trip accuracy`() {
        // ARRANGE: Configuration and transformation
        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 90,
            displayRotation = Surface.ROTATION_0,
            isFrontFacing = false,
            fitMode = FitMode.CENTER_CROP
        )

        val result = transformManager.calculateTransformation(config)
        val inverseMatrix = transformManager.createInverseMatrix(result.matrix)

        // Test points
        val testPoints = listOf(
            PointF(0f, 0f),
            PointF(sourceSize.width / 2f, sourceSize.height / 2f),
            PointF(sourceSize.width.toFloat(), sourceSize.height.toFloat()),
            PointF(sourceSize.width * 0.25f, sourceSize.height * 0.75f)
        )

        for (originalPoint in testPoints) {
            // ACT: Forward and inverse transformation
            val forwardPoint = transformManager.transformPoint(result.matrix, originalPoint)
            val roundTripPoint = transformManager.transformPoint(inverseMatrix, forwardPoint)

            // ASSERT: Should return close to original
            val errorX = abs(originalPoint.x - roundTripPoint.x)
            val errorY = abs(originalPoint.y - roundTripPoint.y)

            assertTrue(
                "Round-trip X error for (${originalPoint.x}, ${originalPoint.y}): error $errorX",
                errorX <= errorTolerance
            )
            assertTrue(
                "Round-trip Y error for (${originalPoint.x}, ${originalPoint.y}): error $errorY",
                errorY <= errorTolerance
            )
        }
    }

    @Test
    fun `test transformation validation with test points`() {
        // ARRANGE: Configuration and transformation
        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 90,
            displayRotation = Surface.ROTATION_0,
            isFrontFacing = false,
            fitMode = FitMode.CENTER_CROP
        )

        val result = transformManager.calculateTransformation(config)
        val inverseMatrix = transformManager.createInverseMatrix(result.matrix)

        // ACT: Generate test points and validate
        val testPoints = transformManager.generateTestPoints(sourceSize, density = 5)
        val isValid = transformManager.validateTransformation(
            result.matrix,
            inverseMatrix,
            testPoints,
            tolerance = errorTolerance
        )

        // ASSERT: Validation should pass
        assertTrue(
            "Transformation validation should pass with generated test points",
            isValid
        )

        // Test points should be sufficient
        assertTrue(
            "Should generate sufficient test points",
            testPoints.size >= 25 // 5x5 grid + corners + center
        )
    }

    @Test
    fun `test rotation utility functions accuracy`() {
        // Test rotation degree conversion
        assertEquals(0, transformManager.getRotationDegrees(Surface.ROTATION_0))
        assertEquals(90, transformManager.getRotationDegrees(Surface.ROTATION_90))
        assertEquals(180, transformManager.getRotationDegrees(Surface.ROTATION_180))
        assertEquals(270, transformManager.getRotationDegrees(Surface.ROTATION_270))

        // Test rotation normalization
        assertEquals(0, transformManager.normalizeRotation(360))
        assertEquals(90, transformManager.normalizeRotation(450))
        assertEquals(270, transformManager.normalizeRotation(-90))
        assertEquals(180, transformManager.normalizeRotation(-180))

        // Test rotation equivalence
        assertTrue(transformManager.areRotationsEquivalent(0, 360))
        assertTrue(transformManager.areRotationsEquivalent(90, 450))
        assertTrue(transformManager.areRotationsEquivalent(-90, 270))

        // Test rotation delta calculation
        assertEquals(90, transformManager.calculateRotationDelta(0, 90))
        assertEquals(-90, transformManager.calculateRotationDelta(90, 0))
        assertEquals(180, transformManager.calculateRotationDelta(0, 180))
        assertEquals(-90, transformManager.calculateRotationDelta(0, 270)) // Shortest path
    }
}