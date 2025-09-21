package com.posecoach.app.camera

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.util.Size
import android.view.Surface
import com.posecoach.app.overlay.FitMode
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assert.*

/**
 * Comprehensive test suite for camera transformations and rotations.
 * Tests all rotation angles, fit modes, and coordinate mapping scenarios.
 */
class TransformationTestSuite {

    private lateinit var rotationManager: RotationTransformManager

    companion object {
        // Test constants
        private const val PIXEL_TOLERANCE = 2.0f
        private const val PERCENTAGE_TOLERANCE = 0.05f // 5%

        // Common test resolutions
        private val RESOLUTIONS = listOf(
            Size(640, 480),    // 4:3
            Size(1280, 720),   // 16:9
            Size(1920, 1080),  // 16:9 HD
            Size(480, 640),    // 3:4 portrait
            Size(720, 1280),   // 9:16 portrait
            Size(320, 240),    // Low power
            Size(1080, 1920)   // 9:16 HD portrait
        )

        // All rotation angles
        private val ROTATIONS = listOf(0, 90, 180, 270)

        // All fit modes
        private val FIT_MODES = listOf(FitMode.FILL, FitMode.CENTER_CROP, FitMode.CENTER_INSIDE)
    }

    @Before
    fun setup() {
        rotationManager = RotationTransformManager()
    }

    @Test
    fun testIdentityTransformation() {
        val sourceSize = Size(640, 480)
        val targetSize = Size(640, 480)

        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 0,
            displayRotation = Surface.ROTATION_0,
            isFrontFacing = false,
            fitMode = FitMode.FILL
        )

        val result = rotationManager.calculateTransformation(config)

        assertTrue("Identity transformation should be valid", result.isValid)
        assertEquals("No rotation for identity", 0, result.effectiveRotation)
        assertEquals("Scale X should be 1.0", 1.0f, result.scaleX, PERCENTAGE_TOLERANCE)
        assertEquals("Scale Y should be 1.0", 1.0f, result.scaleY, PERCENTAGE_TOLERANCE)
    }

    @Test
    fun testAllRotationAngles() {
        ROTATIONS.forEach { rotation ->
            testRotationAccuracy(rotation)
        }
    }

    private fun testRotationAccuracy(rotationDegrees: Int) {
        val sourceSize = Size(640, 480)
        val targetSize = Size(480, 640) // Rotated dimensions

        val displayRotation = when (rotationDegrees) {
            0 -> Surface.ROTATION_0
            90 -> Surface.ROTATION_90
            180 -> Surface.ROTATION_180
            270 -> Surface.ROTATION_270
            else -> Surface.ROTATION_0
        }

        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 90, // Common sensor orientation
            displayRotation = displayRotation,
            isFrontFacing = false,
            fitMode = FitMode.CENTER_CROP
        )

        val result = rotationManager.calculateTransformation(config)

        assertTrue("Rotation $rotationDegrees transformation should be valid", result.isValid)

        // Test corner point transformations
        val testPoints = listOf(
            PointF(0f, 0f),
            PointF(sourceSize.width.toFloat(), 0f),
            PointF(sourceSize.width.toFloat(), sourceSize.height.toFloat()),
            PointF(0f, sourceSize.height.toFloat())
        )

        val transformedPoints = rotationManager.transformPoints(result.matrix, testPoints)

        // Verify points are within target bounds
        transformedPoints.forEach { point ->
            assertTrue(
                "Point $point should be within target bounds for rotation $rotationDegrees",
                point.x >= -PIXEL_TOLERANCE && point.x <= targetSize.width + PIXEL_TOLERANCE &&
                point.y >= -PIXEL_TOLERANCE && point.y <= targetSize.height + PIXEL_TOLERANCE
            )
        }
    }

    @Test
    fun testAllFitModes() {
        FIT_MODES.forEach { fitMode ->
            testFitModeAccuracy(fitMode)
        }
    }

    private fun testFitModeAccuracy(fitMode: FitMode) {
        val sourceSize = Size(640, 480) // 4:3
        val targetSize = Size(1280, 720) // 16:9

        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 0,
            displayRotation = Surface.ROTATION_0,
            isFrontFacing = false,
            fitMode = fitMode
        )

        val result = rotationManager.calculateTransformation(config)

        assertTrue("FitMode $fitMode transformation should be valid", result.isValid)

        when (fitMode) {
            FitMode.FILL -> {
                // Should scale to fill entire target
                val expectedScaleX = targetSize.width.toFloat() / sourceSize.width.toFloat()
                val expectedScaleY = targetSize.height.toFloat() / sourceSize.height.toFloat()

                assertEquals("Fill mode scale X", expectedScaleX, result.scaleX, PERCENTAGE_TOLERANCE)
                assertEquals("Fill mode scale Y", expectedScaleY, result.scaleY, PERCENTAGE_TOLERANCE)
            }
            FitMode.CENTER_CROP -> {
                // Scales should be equal and maintain aspect ratio
                assertEquals("Center crop equal scales", result.scaleX, result.scaleY, PERCENTAGE_TOLERANCE)

                // Should crop larger dimension
                assertNotNull("Center crop should have crop rect", result.cropRect)
            }
            FitMode.CENTER_INSIDE -> {
                // Scales should be equal and fit inside target
                assertEquals("Center inside equal scales", result.scaleX, result.scaleY, PERCENTAGE_TOLERANCE)

                // Scaled dimensions should not exceed target
                val scaledWidth = sourceSize.width * result.scaleX
                val scaledHeight = sourceSize.height * result.scaleY

                assertTrue("Scaled width should fit inside", scaledWidth <= targetSize.width + PIXEL_TOLERANCE)
                assertTrue("Scaled height should fit inside", scaledHeight <= targetSize.height + PIXEL_TOLERANCE)
            }
        }
    }

    @Test
    fun testFrontFacingCameraMirroring() {
        val sourceSize = Size(640, 480)
        val targetSize = Size(640, 480)

        // Test back camera (no mirroring)
        val backCameraConfig = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 90,
            displayRotation = Surface.ROTATION_0,
            isFrontFacing = false,
            fitMode = FitMode.FILL,
            mirrorMode = RotationTransformManager.MirrorMode.AUTO
        )

        // Test front camera (should mirror)
        val frontCameraConfig = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 270,
            displayRotation = Surface.ROTATION_0,
            isFrontFacing = true,
            fitMode = FitMode.FILL,
            mirrorMode = RotationTransformManager.MirrorMode.AUTO
        )

        val backResult = rotationManager.calculateTransformation(backCameraConfig)
        val frontResult = rotationManager.calculateTransformation(frontCameraConfig)

        assertTrue("Back camera transformation should be valid", backResult.isValid)
        assertTrue("Front camera transformation should be valid", frontResult.isValid)

        // Test point mirroring - front camera should mirror horizontally
        val testPoint = PointF(100f, 240f) // Point on left side

        val backTransformed = rotationManager.transformPoint(backResult.matrix, testPoint)
        val frontTransformed = rotationManager.transformPoint(frontResult.matrix, testPoint)

        // For front camera, X coordinate should be mirrored
        assertTrue(
            "Front camera should mirror horizontally",
            abs(backTransformed.x - (targetSize.width - frontTransformed.x)) < PIXEL_TOLERANCE
        )
    }

    @Test
    fun testTransformationRoundTrip() {
        RESOLUTIONS.take(3).forEach { sourceSize ->
            RESOLUTIONS.take(3).forEach { targetSize ->
                ROTATIONS.forEach { rotation ->
                    FIT_MODES.forEach { fitMode ->
                        testRoundTripAccuracy(sourceSize, targetSize, rotation, fitMode)
                    }
                }
            }
        }
    }

    private fun testRoundTripAccuracy(
        sourceSize: Size,
        targetSize: Size,
        rotation: Int,
        fitMode: FitMode
    ) {
        val displayRotation = when (rotation) {
            90 -> Surface.ROTATION_90
            180 -> Surface.ROTATION_180
            270 -> Surface.ROTATION_270
            else -> Surface.ROTATION_0
        }

        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 90,
            displayRotation = displayRotation,
            isFrontFacing = false,
            fitMode = fitMode
        )

        val result = rotationManager.calculateTransformation(config)
        val inverseMatrix = rotationManager.createInverseMatrix(result.matrix)

        val testPoints = rotationManager.generateTestPoints(sourceSize, density = 5)
        val isValid = rotationManager.validateTransformation(
            result.matrix,
            inverseMatrix,
            testPoints,
            PIXEL_TOLERANCE
        )

        assertTrue(
            "Round trip validation failed for source=$sourceSize, target=$targetSize, rotation=$rotation, fitMode=$fitMode",
            isValid
        )
    }

    @Test
    fun testEdgeCaseResolutions() {
        // Test very small resolution
        testResolutionEdgeCase(Size(1, 1), Size(640, 480))

        // Test very large resolution
        testResolutionEdgeCase(Size(4096, 3072), Size(640, 480))

        // Test extreme aspect ratios
        testResolutionEdgeCase(Size(1920, 100), Size(640, 480)) // Very wide
        testResolutionEdgeCase(Size(100, 1920), Size(640, 480)) // Very tall
    }

    private fun testResolutionEdgeCase(sourceSize: Size, targetSize: Size) {
        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 0,
            displayRotation = Surface.ROTATION_0,
            isFrontFacing = false,
            fitMode = FitMode.CENTER_INSIDE
        )

        val result = rotationManager.calculateTransformation(config)

        assertTrue(
            "Edge case transformation should be valid for source=$sourceSize, target=$targetSize",
            result.isValid
        )

        assertTrue("Scale factors should be positive", result.scaleX > 0 && result.scaleY > 0)
        assertTrue("Scale factors should be reasonable", result.scaleX < 100 && result.scaleY < 100)
    }

    @Test
    fun testPerformanceRequirements() {
        val iterations = 1000
        val sourceSize = Size(640, 480)
        val targetSize = Size(1280, 720)

        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 90,
            displayRotation = Surface.ROTATION_90,
            isFrontFacing = true,
            fitMode = FitMode.CENTER_CROP
        )

        val startTime = System.nanoTime()

        repeat(iterations) {
            rotationManager.calculateTransformation(config)
        }

        val endTime = System.nanoTime()
        val averageTimeMs = (endTime - startTime) / 1_000_000.0 / iterations

        assertTrue(
            "Average transformation time should be under 1ms, actual: ${averageTimeMs}ms",
            averageTimeMs < 1.0
        )
    }

    @Test
    fun testBatchTransformPerformance() {
        val sourceSize = Size(640, 480)
        val targetSize = Size(640, 480)

        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 0,
            displayRotation = Surface.ROTATION_90,
            isFrontFacing = false,
            fitMode = FitMode.FILL
        )

        val result = rotationManager.calculateTransformation(config)
        val testPoints = rotationManager.generateTestPoints(sourceSize, density = 20) // ~440 points

        val startTime = System.nanoTime()
        val transformedPoints = rotationManager.transformPoints(result.matrix, testPoints)
        val endTime = System.nanoTime()

        val timeMs = (endTime - startTime) / 1_000_000.0
        val pointsPerMs = testPoints.size / timeMs

        assertEquals("All points should be transformed", testPoints.size, transformedPoints.size)
        assertTrue(
            "Batch transform should process at least 1000 points/ms, actual: ${pointsPerMs}",
            pointsPerMs >= 1000
        )
    }

    @Test
    fun testRotationNormalization() {
        // Test angle normalization
        assertEquals(0, rotationManager.normalizeRotation(0))
        assertEquals(90, rotationManager.normalizeRotation(90))
        assertEquals(180, rotationManager.normalizeRotation(180))
        assertEquals(270, rotationManager.normalizeRotation(270))
        assertEquals(0, rotationManager.normalizeRotation(360))
        assertEquals(90, rotationManager.normalizeRotation(450))
        assertEquals(270, rotationManager.normalizeRotation(-90))
        assertEquals(180, rotationManager.normalizeRotation(-180))

        // Test rotation equivalence
        assertTrue(rotationManager.areRotationsEquivalent(0, 360))
        assertTrue(rotationManager.areRotationsEquivalent(90, 450))
        assertTrue(rotationManager.areRotationsEquivalent(-90, 270))
        assertFalse(rotationManager.areRotationsEquivalent(90, 180))

        // Test rotation delta calculation
        assertEquals(90, rotationManager.calculateRotationDelta(0, 90))
        assertEquals(-90, rotationManager.calculateRotationDelta(90, 0))
        assertEquals(180, rotationManager.calculateRotationDelta(0, 180))
        assertEquals(-180, rotationManager.calculateRotationDelta(180, 0))
    }

    @Test
    fun testComplexRotationScenarios() {
        // Test rapid rotation changes
        val rotationSequence = listOf(0, 90, 180, 270, 0, 270, 180, 90)

        rotationSequence.windowed(2).forEach { (from, to) ->
            val delta = rotationManager.calculateRotationDelta(from, to)
            assertTrue(
                "Rotation delta should be reasonable: $from -> $to = $delta",
                abs(delta) <= 180
            )
        }
    }

    @Test
    fun testMemoryUsage() {
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // Create many transformation configs
        repeat(10000) {
            val config = RotationTransformManager.TransformationConfig(
                sourceSize = Size(640, 480),
                targetSize = Size(1280, 720),
                sensorOrientation = it % 360,
                displayRotation = Surface.ROTATION_0,
                isFrontFacing = it % 2 == 0,
                fitMode = FIT_MODES[it % FIT_MODES.size]
            )
            rotationManager.calculateTransformation(config)
        }

        System.gc()
        Thread.sleep(100) // Allow GC to run

        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryIncrease = finalMemory - initialMemory

        assertTrue(
            "Memory increase should be reasonable: ${memoryIncrease / 1024}KB",
            memoryIncrease < 10 * 1024 * 1024 // Less than 10MB
        )
    }

    @Test
    fun testConcurrentAccess() {
        val sourceSize = Size(640, 480)
        val targetSize = Size(1280, 720)

        val config = RotationTransformManager.TransformationConfig(
            sourceSize = sourceSize,
            targetSize = targetSize,
            sensorOrientation = 90,
            displayRotation = Surface.ROTATION_90,
            isFrontFacing = false,
            fitMode = FitMode.CENTER_CROP
        )

        // Test concurrent access (simplified for unit test)
        val results = (1..100).map {
            rotationManager.calculateTransformation(config)
        }

        // All results should be identical and valid
        results.forEach { result ->
            assertTrue("Concurrent result should be valid", result.isValid)
            assertEquals("Results should be consistent", results.first().effectiveRotation, result.effectiveRotation)
        }
    }
}