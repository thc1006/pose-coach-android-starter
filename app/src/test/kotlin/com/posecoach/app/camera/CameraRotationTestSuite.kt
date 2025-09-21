package com.posecoach.app.camera

import android.graphics.Matrix
import android.graphics.PointF
import android.util.Size
import android.view.Surface
import com.posecoach.app.overlay.FitMode
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assert.*

/**
 * Comprehensive test suite specifically focused on rotation scenarios and edge cases.
 * Tests device rotation, camera sensor orientation, and coordinate system transformations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class CameraRotationTestSuite {

    private lateinit var rotationManager: RotationTransformManager

    companion object {
        private const val ROTATION_TOLERANCE = 1.0f
        private const val COORDINATE_TOLERANCE = 2.0f

        // Test device configurations
        private val PORTRAIT_DEVICE = Size(480, 854)
        private val LANDSCAPE_DEVICE = Size(854, 480)

        // Common camera resolutions
        private val CAMERA_4_3 = Size(640, 480)
        private val CAMERA_16_9 = Size(1280, 720)
        private val CAMERA_SQUARE = Size(640, 640)
    }

    @Before
    fun setup() {
        rotationManager = RotationTransformManager()
    }

    @Test
    fun testPortraitToLandscapeRotation() {
        val testCases = listOf(
            RotationTestCase(
                deviceOrientation = Surface.ROTATION_0,
                sensorOrientation = 90,
                expectedEffectiveRotation = 90,
                description = "Portrait to Landscape (0° -> 90°)"
            ),
            RotationTestCase(
                deviceOrientation = Surface.ROTATION_90,
                sensorOrientation = 90,
                expectedEffectiveRotation = 180,
                description = "Portrait to Landscape (90° -> 180°)"
            ),
            RotationTestCase(
                deviceOrientation = Surface.ROTATION_180,
                sensorOrientation = 90,
                expectedEffectiveRotation = 270,
                description = "Portrait to Landscape (180° -> 270°)"
            ),
            RotationTestCase(
                deviceOrientation = Surface.ROTATION_270,
                sensorOrientation = 90,
                expectedEffectiveRotation = 0,
                description = "Portrait to Landscape (270° -> 0°)"
            )
        )

        testCases.forEach { testCase ->
            val config = RotationTransformManager.TransformationConfig(
                sourceSize = CAMERA_16_9,
                targetSize = PORTRAIT_DEVICE,
                sensorOrientation = testCase.sensorOrientation,
                displayRotation = testCase.deviceOrientation,
                isFrontFacing = false,
                fitMode = FitMode.CENTER_CROP
            )

            val result = rotationManager.calculateTransformation(config)

            assertEquals(
                "${testCase.description} - effective rotation",
                testCase.expectedEffectiveRotation,
                result.effectiveRotation
            )

            assertTrue(
                "${testCase.description} - transformation should be valid",
                result.isValid
            )
        }
    }

    @Test
    fun testFrontCameraRotationBehavior() {
        // Front camera has different rotation behavior due to mirroring
        val frontCameraTests = listOf(
            Triple(Surface.ROTATION_0, 270, 270),   // 0° + 270° = 270°
            Triple(Surface.ROTATION_90, 270, 0),    // 90° + 270° = 360° = 0°
            Triple(Surface.ROTATION_180, 270, 90),  // 180° + 270° = 450° = 90°
            Triple(Surface.ROTATION_270, 270, 180)  // 270° + 270° = 540° = 180°
        )

        frontCameraTests.forEach { (displayRotation, sensorOrientation, expectedRotation) ->
            val config = RotationTransformManager.TransformationConfig(
                sourceSize = CAMERA_16_9,
                targetSize = PORTRAIT_DEVICE,
                sensorOrientation = sensorOrientation,
                displayRotation = displayRotation,
                isFrontFacing = true,
                fitMode = FitMode.CENTER_CROP
            )

            val result = rotationManager.calculateTransformation(config)

            assertEquals(
                "Front camera rotation for display $displayRotation",
                expectedRotation,
                result.effectiveRotation
            )
        }
    }

    @Test
    fun testRapidRotationSequence() {
        // Test rapid rotation changes (common in real usage)
        val rotationSequence = listOf(
            Surface.ROTATION_0,
            Surface.ROTATION_90,
            Surface.ROTATION_180,
            Surface.ROTATION_270,
            Surface.ROTATION_0,
            Surface.ROTATION_270,
            Surface.ROTATION_180,
            Surface.ROTATION_90
        )

        var previousResult: RotationTransformManager.TransformationResult? = null

        rotationSequence.forEach { rotation ->
            val config = RotationTransformManager.TransformationConfig(
                sourceSize = CAMERA_4_3,
                targetSize = PORTRAIT_DEVICE,
                sensorOrientation = 90,
                displayRotation = rotation,
                isFrontFacing = false,
                fitMode = FitMode.CENTER_CROP
            )

            val result = rotationManager.calculateTransformation(config)

            assertTrue(
                "Rapid rotation $rotation should produce valid result",
                result.isValid
            )

            // Check transformation consistency
            if (previousResult != null && previousResult.rotationDegrees == result.rotationDegrees) {
                assertEquals(
                    "Identical rotations should produce identical transforms",
                    previousResult.effectiveRotation,
                    result.effectiveRotation
                )
            }

            previousResult = result
        }
    }

    @Test
    fun testCoordinateConsistencyAcrossRotations() {
        val testPoint = PointF(0.3f, 0.7f) // Test point in normalized coordinates

        val rotations = listOf(0, 90, 180, 270)
        val results = mutableMapOf<Int, PointF>()

        rotations.forEach { rotation ->
            val displayRotation = when (rotation) {
                90 -> Surface.ROTATION_90
                180 -> Surface.ROTATION_180
                270 -> Surface.ROTATION_270
                else -> Surface.ROTATION_0
            }

            val config = RotationTransformManager.TransformationConfig(
                sourceSize = CAMERA_SQUARE, // Square to simplify rotation analysis
                targetSize = Size(600, 600), // Square target
                sensorOrientation = 0,
                displayRotation = displayRotation,
                isFrontFacing = false,
                fitMode = FitMode.FILL
            )

            val result = rotationManager.calculateTransformation(config)
            val transformedPoint = rotationManager.transformPoint(result.matrix, testPoint)

            results[rotation] = transformedPoint

            // Verify point is within bounds
            assertTrue(
                "Point should be within bounds for rotation $rotation: $transformedPoint",
                transformedPoint.x >= -COORDINATE_TOLERANCE &&
                transformedPoint.x <= 600 + COORDINATE_TOLERANCE &&
                transformedPoint.y >= -COORDINATE_TOLERANCE &&
                transformedPoint.y <= 600 + COORDINATE_TOLERANCE
            )
        }

        // Test rotation relationships
        val point0 = results[0]!!
        val point90 = results[90]!!
        val point180 = results[180]!!
        val point270 = results[270]!!

        // 180° rotation should be opposite
        assertEquals(
            "180° rotation X coordinate",
            600 - point0.x,
            point180.x,
            COORDINATE_TOLERANCE
        )
        assertEquals(
            "180° rotation Y coordinate",
            600 - point0.y,
            point180.y,
            COORDINATE_TOLERANCE
        )
    }

    @Test
    fun testAspectRatioPreservationDuringRotation() {
        val sourceSize = Size(1920, 1080) // 16:9
        val targetSize = Size(480, 854)   // Portrait device

        listOf(0, 90, 180, 270).forEach { rotation ->
            val displayRotation = when (rotation) {
                90 -> Surface.ROTATION_90
                180 -> Surface.ROTATION_180
                270 -> Surface.ROTATION_270
                else -> Surface.ROTATION_0
            }

            FitMode.values().forEach { fitMode ->
                val config = RotationTransformManager.TransformationConfig(
                    sourceSize = sourceSize,
                    targetSize = targetSize,
                    sensorOrientation = 90,
                    displayRotation = displayRotation,
                    isFrontFacing = false,
                    fitMode = fitMode
                )

                val result = rotationManager.calculateTransformation(config)

                when (fitMode) {
                    FitMode.CENTER_CROP, FitMode.CENTER_INSIDE -> {
                        // Should maintain aspect ratio
                        assertEquals(
                            "Aspect ratio should be maintained for $fitMode at $rotation°",
                            result.scaleX,
                            result.scaleY,
                            0.001f
                        )
                    }
                    FitMode.FILL -> {
                        // May distort aspect ratio - just verify scales are positive
                        assertTrue(
                            "Scale factors should be positive for FILL mode",
                            result.scaleX > 0 && result.scaleY > 0
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testRotationDeltaCalculations() {
        val testCases = listOf(
            Triple(0, 90, 90),
            Triple(90, 180, 90),
            Triple(180, 270, 90),
            Triple(270, 0, 90),
            Triple(0, 270, -90),
            Triple(270, 180, -90),
            Triple(0, 180, 180),
            Triple(180, 0, -180),
            Triple(45, 315, -90),
            Triple(315, 45, 90)
        )

        testCases.forEach { (from, to, expectedDelta) ->
            val actualDelta = rotationManager.calculateRotationDelta(from, to)
            assertEquals(
                "Rotation delta from $from° to $to°",
                expectedDelta,
                actualDelta
            )
        }
    }

    @Test
    fun testMirroringConsistency() {
        val testPoint = PointF(0.2f, 0.5f) // Point on left side

        val config = RotationTransformManager.TransformationConfig(
            sourceSize = CAMERA_16_9,
            targetSize = PORTRAIT_DEVICE,
            sensorOrientation = 270,
            displayRotation = Surface.ROTATION_0,
            isFrontFacing = true,
            fitMode = FitMode.FILL,
            mirrorMode = RotationTransformManager.MirrorMode.HORIZONTAL
        )

        val result = rotationManager.calculateTransformation(config)
        val transformedPoint = rotationManager.transformPoint(result.matrix, testPoint)

        // For horizontal mirroring, X coordinate should be flipped
        val expectedX = PORTRAIT_DEVICE.width - (testPoint.x * PORTRAIT_DEVICE.width)

        assertEquals(
            "Horizontally mirrored X coordinate",
            expectedX,
            transformedPoint.x,
            COORDINATE_TOLERANCE
        )
    }

    @Test
    fun testPerformanceUnderRotation() {
        val iterations = 100
        val config = RotationTransformManager.TransformationConfig(
            sourceSize = CAMERA_16_9,
            targetSize = PORTRAIT_DEVICE,
            sensorOrientation = 90,
            displayRotation = Surface.ROTATION_90,
            isFrontFacing = false,
            fitMode = FitMode.CENTER_CROP
        )

        val startTime = System.nanoTime()

        repeat(iterations) {
            val result = rotationManager.calculateTransformation(config)
            assertTrue("Transformation should be valid", result.isValid)

            // Test point transformation
            val testPoints = listOf(
                PointF(0f, 0f),
                PointF(1f, 1f),
                PointF(0.5f, 0.5f)
            )
            rotationManager.transformPoints(result.matrix, testPoints)
        }

        val endTime = System.nanoTime()
        val averageTimeMs = (endTime - startTime) / 1_000_000.0 / iterations

        assertTrue(
            "Rotation transformation should be fast: ${averageTimeMs}ms",
            averageTimeMs < 1.0
        )
    }

    @Test
    fun testRotationRoundTripAccuracy() {
        val testConfigurations = listOf(
            RotationTransformManager.TransformationConfig(
                sourceSize = CAMERA_4_3,
                targetSize = PORTRAIT_DEVICE,
                sensorOrientation = 90,
                displayRotation = Surface.ROTATION_90,
                isFrontFacing = false,
                fitMode = FitMode.CENTER_CROP
            ),
            RotationTransformManager.TransformationConfig(
                sourceSize = CAMERA_16_9,
                targetSize = LANDSCAPE_DEVICE,
                sensorOrientation = 270,
                displayRotation = Surface.ROTATION_270,
                isFrontFacing = true,
                fitMode = FitMode.CENTER_INSIDE
            )
        )

        testConfigurations.forEach { config ->
            val result = rotationManager.calculateTransformation(config)
            val inverseMatrix = rotationManager.createInverseMatrix(result.matrix)
            val testPoints = rotationManager.generateTestPoints(config.sourceSize, density = 10)

            val isAccurate = rotationManager.validateTransformation(
                result.matrix,
                inverseMatrix,
                testPoints,
                tolerance = COORDINATE_TOLERANCE
            )

            assertTrue(
                "Round trip should be accurate for config: $config",
                isAccurate
            )
        }
    }

    @Test
    fun testEdgeCaseRotations() {
        // Test non-standard rotation angles
        val edgeCaseAngles = listOf(1, 45, 89, 91, 179, 181, 269, 271, 359, 361, -1, -90)

        edgeCaseAngles.forEach { angle ->
            val normalizedAngle = rotationManager.normalizeRotation(angle)
            assertTrue(
                "Normalized angle should be 0-359: $angle -> $normalizedAngle",
                normalizedAngle in 0..359
            )

            // Test transformation with edge case angle
            val config = RotationTransformManager.TransformationConfig(
                sourceSize = CAMERA_4_3,
                targetSize = PORTRAIT_DEVICE,
                sensorOrientation = angle,
                displayRotation = Surface.ROTATION_0,
                isFrontFacing = false,
                fitMode = FitMode.FILL
            )

            val result = rotationManager.calculateTransformation(config)
            assertTrue(
                "Edge case rotation $angle should produce valid result",
                result.isValid
            )
        }
    }

    @Test
    fun testMemoryStabilityDuringRotation() {
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // Simulate rapid rotation changes
        repeat(1000) { iteration ->
            val rotation = when (iteration % 4) {
                0 -> Surface.ROTATION_0
                1 -> Surface.ROTATION_90
                2 -> Surface.ROTATION_180
                else -> Surface.ROTATION_270
            }

            val config = RotationTransformManager.TransformationConfig(
                sourceSize = CAMERA_16_9,
                targetSize = PORTRAIT_DEVICE,
                sensorOrientation = 90,
                displayRotation = rotation,
                isFrontFacing = iteration % 2 == 0,
                fitMode = FitMode.values()[iteration % FitMode.values().size]
            )

            rotationManager.calculateTransformation(config)
        }

        System.gc()
        Thread.sleep(100)

        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryIncrease = finalMemory - initialMemory

        assertTrue(
            "Memory usage should remain stable during rotation: ${memoryIncrease / 1024}KB increase",
            memoryIncrease < 5 * 1024 * 1024 // Less than 5MB increase
        )
    }

    // Helper data classes

    private data class RotationTestCase(
        val deviceOrientation: Int,
        val sensorOrientation: Int,
        val expectedEffectiveRotation: Int,
        val description: String
    )
}