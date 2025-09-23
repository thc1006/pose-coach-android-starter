package com.posecoach.app.overlay

import android.graphics.PointF
import android.util.Size
import android.view.Surface
import com.posecoach.app.camera.RotationTransformManager
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
 * Integration tests for coordinate transformation components working together.
 * These tests simulate real-world scenarios and are designed to FAIL initially
 * to expose bugs in the coordinate transformation pipeline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CoordinateTransformationIntegrationTest {

    private lateinit var coordinateMapper: CoordinateMapper
    private lateinit var enhancedMapper: EnhancedCoordinateMapper
    private lateinit var rotationManager: RotationTransformManager

    // Realistic device dimensions
    private val phonePortraitWidth = 1080
    private val phonePortraitHeight = 2340
    private val phoneLandscapeWidth = 2340
    private val phoneLandscapeHeight = 1080

    // Typical camera preview dimensions
    private val cameraWidth = 720
    private val cameraHeight = 1280

    private val errorTolerance = 3.0f

    @Before
    fun setup() {
        // Mock Timber to avoid log errors
        mockkStatic(Timber::class)
        every { Timber.d(any<String>()) } just Runs
        every { Timber.e(any<Throwable>(), any<String>()) } just Runs
        every { Timber.e(any<String>()) } just Runs
        every { Timber.v(any<String>()) } just Runs
        every { Timber.w(any<String>()) } just Runs

        rotationManager = RotationTransformManager()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test real world pose landmarks - portrait mode standing person`() {
        // ARRANGE: Portrait mode setup
        coordinateMapper = CoordinateMapper(
            viewWidth = phonePortraitWidth,
            viewHeight = phonePortraitHeight,
            imageWidth = cameraWidth,
            imageHeight = cameraHeight,
            isFrontFacing = false,
            rotation = 0
        )

        // Simulate a standing person's key landmarks in normalized coordinates
        val personLandmarks = mapOf(
            "nose" to Pair(0.5f, 0.15f),      // Center-top (head)
            "leftShoulder" to Pair(0.4f, 0.3f),   // Left shoulder
            "rightShoulder" to Pair(0.6f, 0.3f),  // Right shoulder
            "leftElbow" to Pair(0.35f, 0.45f),    // Left elbow
            "rightElbow" to Pair(0.65f, 0.45f),   // Right elbow
            "leftWrist" to Pair(0.3f, 0.6f),      // Left wrist
            "rightWrist" to Pair(0.7f, 0.6f),     // Right wrist
            "leftHip" to Pair(0.45f, 0.65f),      // Left hip
            "rightHip" to Pair(0.55f, 0.65f),     // Right hip
            "leftKnee" to Pair(0.44f, 0.8f),      // Left knee
            "rightKnee" to Pair(0.56f, 0.8f),     // Right knee
            "leftAnkle" to Pair(0.43f, 0.95f),    // Left ankle
            "rightAnkle" to Pair(0.57f, 0.95f)    // Right ankle
        )

        // ACT: Transform all landmarks
        val transformedLandmarks = personLandmarks.mapValues { (_, landmark) ->
            coordinateMapper.normalizedToPixel(landmark.first, landmark.second)
        }

        // ASSERT: Verify person remains upright and proportional
        val nose = transformedLandmarks["nose"]!!
        val leftShoulder = transformedLandmarks["leftShoulder"]!!
        val rightShoulder = transformedLandmarks["rightShoulder"]!!
        val leftHip = transformedLandmarks["leftHip"]!!
        val rightHip = transformedLandmarks["rightHip"]!!
        val leftAnkle = transformedLandmarks["leftAnkle"]!!
        val rightAnkle = transformedLandmarks["rightAnkle"]!!

        // Person should be roughly vertical (shoulders/hips roughly horizontal)
        val shoulderHeightDiff = abs(leftShoulder.second - rightShoulder.second)
        val hipHeightDiff = abs(leftHip.second - rightHip.second)

        assertTrue(
            "Shoulders should be roughly at same height: left=${leftShoulder.second}, right=${rightShoulder.second}, diff=$shoulderHeightDiff",
            shoulderHeightDiff <= errorTolerance * 2
        )
        assertTrue(
            "Hips should be roughly at same height: left=${leftHip.second}, right=${rightHip.second}, diff=$hipHeightDiff",
            hipHeightDiff <= errorTolerance * 2
        )

        // Person should be oriented correctly (head above shoulders above hips above ankles)
        assertTrue("Head should be above shoulders", nose.second < leftShoulder.second)
        assertTrue("Shoulders should be above hips", leftShoulder.second < leftHip.second)
        assertTrue("Hips should be above ankles", leftHip.second < leftAnkle.second)

        // Body should be roughly centered
        val bodyCenter = (leftShoulder.first + rightShoulder.first) / 2f
        val screenCenter = phonePortraitWidth / 2f
        val centeringError = abs(bodyCenter - screenCenter)

        assertTrue(
            "Person should be roughly centered: body center=$bodyCenter, screen center=$screenCenter, error=$centeringError",
            centeringError <= phonePortraitWidth * 0.1f // Within 10% of screen width
        )
    }

    @Test
    fun `test landscape mode rotation - person appears correctly oriented`() {
        // ARRANGE: Landscape mode setup (device rotated 90°)
        coordinateMapper = CoordinateMapper(
            viewWidth = phoneLandscapeWidth,  // Swapped dimensions
            viewHeight = phoneLandscapeHeight,
            imageWidth = cameraWidth,
            imageHeight = cameraHeight,
            isFrontFacing = false,
            rotation = 90
        )

        // Same person landmarks as portrait test
        val headPoint = Pair(0.5f, 0.15f)
        val leftFootPoint = Pair(0.43f, 0.95f)
        val rightFootPoint = Pair(0.57f, 0.95f)

        // ACT: Transform landmarks in landscape mode
        val (headX, headY) = coordinateMapper.normalizedToPixel(headPoint.first, headPoint.second)
        val (leftFootX, leftFootY) = coordinateMapper.normalizedToPixel(leftFootPoint.first, leftFootPoint.second)
        val (rightFootX, rightFootY) = coordinateMapper.normalizedToPixel(rightFootPoint.first, rightFootPoint.second)

        // ASSERT: In landscape mode with 90° rotation, person should still appear upright
        // The exact transformation depends on the rotation implementation

        // Head should still be distinguishable from feet
        val headToLeftFootDistance = kotlin.math.sqrt(
            (headX - leftFootX) * (headX - leftFootX) + (headY - leftFootY) * (headY - leftFootY)
        )
        val feetDistance = kotlin.math.sqrt(
            (leftFootX - rightFootX) * (leftFootX - rightFootX) + (leftFootY - rightFootY) * (leftFootY - rightFootY)
        )

        assertTrue(
            "Head should be significantly farther from feet than feet are from each other",
            headToLeftFootDistance > feetDistance * 2
        )

        // All points should be within screen bounds
        val allX = listOf(headX, leftFootX, rightFootX)
        val allY = listOf(headY, leftFootY, rightFootY)

        for (x in allX) {
            assertTrue("X coordinate should be within landscape screen bounds: $x",
                x >= -errorTolerance && x <= phoneLandscapeWidth + errorTolerance)
        }
        for (y in allY) {
            assertTrue("Y coordinate should be within landscape screen bounds: $y",
                y >= -errorTolerance && y <= phoneLandscapeHeight + errorTolerance)
        }
    }

    @Test
    fun `test front camera mirroring - person appears correctly flipped`() {
        // ARRANGE: Front camera setup
        coordinateMapper = CoordinateMapper(
            viewWidth = phonePortraitWidth,
            viewHeight = phonePortraitHeight,
            imageWidth = cameraWidth,
            imageHeight = cameraHeight,
            isFrontFacing = true,
            rotation = 0
        )

        // Person raising left hand (from their perspective)
        val leftWrist = Pair(0.2f, 0.4f)   // Person's left (far left in image)
        val rightWrist = Pair(0.8f, 0.6f)  // Person's right (far right in image)
        val nose = Pair(0.5f, 0.2f)        // Center of face

        // ACT: Transform with front camera (should mirror horizontally)
        val (leftWristX, leftWristY) = coordinateMapper.normalizedToPixel(leftWrist.first, leftWrist.second)
        val (rightWristX, rightWristY) = coordinateMapper.normalizedToPixel(rightWrist.first, rightWrist.second)
        val (noseX, noseY) = coordinateMapper.normalizedToPixel(nose.first, nose.second)

        // ASSERT: With front camera, left wrist should appear on right side of screen
        assertTrue(
            "With front camera mirroring, person's left wrist should appear on right side of screen: leftWristX=$leftWristX, rightWristX=$rightWristX",
            leftWristX > rightWristX
        )

        // Nose should remain centered
        val expectedNoseX = phonePortraitWidth / 2f
        val noseError = abs(noseX - expectedNoseX)
        assertTrue(
            "Nose should remain centered with front camera: expected=$expectedNoseX, actual=$noseX, error=$noseError",
            noseError <= errorTolerance
        )

        // Y coordinates should not be affected by horizontal mirroring
        val expectedLeftWristY = leftWrist.second * phonePortraitHeight
        val expectedRightWristY = rightWrist.second * phonePortraitHeight
        val leftYError = abs(leftWristY - expectedLeftWristY)
        val rightYError = abs(rightWristY - expectedRightWristY)

        assertTrue(
            "Y coordinates should not be affected by mirroring: leftWrist Y error=$leftYError",
            leftYError <= errorTolerance
        )
        assertTrue(
            "Y coordinates should not be affected by mirroring: rightWrist Y error=$rightYError",
            rightYError <= errorTolerance
        )
    }

    @Test
    fun `test enhanced mapper vs basic mapper consistency`() {
        // ARRANGE: Both mappers with same configuration
        coordinateMapper = CoordinateMapper(
            viewWidth = phonePortraitWidth,
            viewHeight = phonePortraitHeight,
            imageWidth = cameraWidth,
            imageHeight = cameraHeight,
            isFrontFacing = false,
            rotation = 0
        )

        enhancedMapper = EnhancedCoordinateMapper(
            viewWidth = phonePortraitWidth,
            viewHeight = phonePortraitHeight,
            imageWidth = cameraWidth,
            imageHeight = cameraHeight,
            isFrontFacing = false,
            rotation = 0
        )

        // Test landmarks
        val testLandmarks = listOf(
            Pair(0.0f, 0.0f),
            Pair(0.5f, 0.5f),
            Pair(1.0f, 1.0f),
            Pair(0.25f, 0.75f),
            Pair(0.75f, 0.25f)
        )

        // ACT: Transform with both mappers
        val basicResults = testLandmarks.map { (x, y) ->
            coordinateMapper.normalizedToPixel(x, y)
        }
        val enhancedResults = testLandmarks.map { (x, y) ->
            enhancedMapper.normalizedToPixel(x, y)
        }

        // ASSERT: Results should be consistent between mappers
        for (i in testLandmarks.indices) {
            val (basicX, basicY) = basicResults[i]
            val (enhancedX, enhancedY) = enhancedResults[i]

            val errorX = abs(basicX - enhancedX)
            val errorY = abs(basicY - enhancedY)

            assertTrue(
                "Mapper consistency X error at landmark ${testLandmarks[i]}: basic=$basicX, enhanced=$enhancedX, error=$errorX",
                errorX <= errorTolerance
            )
            assertTrue(
                "Mapper consistency Y error at landmark ${testLandmarks[i]}: basic=$basicY, enhanced=$enhancedY, error=$errorY",
                errorY <= errorTolerance
            )
        }
    }

    @Test
    fun `test coordinate transform with rotation manager integration`() {
        // ARRANGE: Rotation manager configuration
        val config = RotationTransformManager.TransformationConfig(
            sourceSize = Size(cameraWidth, cameraHeight),
            targetSize = Size(phonePortraitWidth, phonePortraitHeight),
            sensorOrientation = 90,
            displayRotation = Surface.ROTATION_0,
            isFrontFacing = false,
            fitMode = FitMode.CENTER_CROP
        )

        val transformResult = rotationManager.calculateTransformation(config)

        // Coordinate mapper with same configuration
        coordinateMapper = CoordinateMapper(
            viewWidth = phonePortraitWidth,
            viewHeight = phonePortraitHeight,
            imageWidth = cameraWidth,
            imageHeight = cameraHeight,
            isFrontFacing = false,
            rotation = 0
        )

        // Test center point
        val centerNormalized = Pair(0.5f, 0.5f)
        val centerPixelCoordinate = coordinateMapper.normalizedToPixel(centerNormalized.first, centerNormalized.second)

        // Transform using rotation manager
        val centerSourcePoint = PointF(cameraWidth / 2f, cameraHeight / 2f)
        val centerTransformed = rotationManager.transformPoint(transformResult.matrix, centerSourcePoint)

        // ASSERT: Both approaches should yield similar results for center point
        val errorX = abs(centerPixelCoordinate.first - centerTransformed.x)
        val errorY = abs(centerPixelCoordinate.second - centerTransformed.y)

        assertTrue(
            "Center point X should be consistent between coordinate mapper and rotation manager: mapper=${centerPixelCoordinate.first}, rotationMgr=${centerTransformed.x}, error=$errorX",
            errorX <= errorTolerance * 3 // Allow more tolerance for different transformation approaches
        )
        assertTrue(
            "Center point Y should be consistent between coordinate mapper and rotation manager: mapper=${centerPixelCoordinate.second}, rotationMgr=${centerTransformed.y}, error=$errorY",
            errorY <= errorTolerance * 3
        )
    }

    @Test
    fun `test device rotation change - landmarks remain consistent`() {
        // ARRANGE: Start in portrait mode
        var mapper = CoordinateMapper(
            viewWidth = phonePortraitWidth,
            viewHeight = phonePortraitHeight,
            imageWidth = cameraWidth,
            imageHeight = cameraHeight,
            isFrontFacing = false,
            rotation = 0
        )

        val testLandmark = Pair(0.5f, 0.3f) // Face landmark
        val (portraitX, portraitY) = mapper.normalizedToPixel(testLandmark.first, testLandmark.second)

        // ACT: Rotate to landscape mode
        mapper = CoordinateMapper(
            viewWidth = phoneLandscapeWidth,
            viewHeight = phoneLandscapeHeight,
            imageWidth = cameraWidth,
            imageHeight = cameraHeight,
            isFrontFacing = false,
            rotation = 90
        )

        val (landscapeX, landscapeY) = mapper.normalizedToPixel(testLandmark.first, testLandmark.second)

        // ASSERT: Landmark should maintain relative position in rotated view
        // This test checks that the transformation properly handles rotation

        // The landmark should still be in the upper portion of the screen
        val portraitRelativeY = portraitY / phonePortraitHeight
        val landscapeRelativeY = landscapeY / phoneLandscapeHeight

        // Relative positions should be roughly maintained (within reasonable tolerance)
        val relativeYError = abs(portraitRelativeY - landscapeRelativeY)

        assertTrue(
            "Landmark should maintain relative Y position after rotation: portrait=$portraitRelativeY, landscape=$landscapeRelativeY, error=$relativeYError",
            relativeYError <= 0.2f // Allow 20% variation due to aspect ratio changes
        )

        // Both coordinates should be within their respective screen bounds
        assertTrue("Portrait X should be within bounds", portraitX >= 0 && portraitX <= phonePortraitWidth)
        assertTrue("Portrait Y should be within bounds", portraitY >= 0 && portraitY <= phonePortraitHeight)
        assertTrue("Landscape X should be within bounds", landscapeX >= 0 && landscapeX <= phoneLandscapeWidth)
        assertTrue("Landscape Y should be within bounds", landscapeY >= 0 && landscapeY <= phoneLandscapeHeight)
    }

    @Test
    fun `test pose skeleton maintains connectivity across rotations`() {
        // ARRANGE: Skeleton connections (simplified)
        val skeletonConnections = listOf(
            // Head to shoulders
            Pair("nose", "leftShoulder"),
            Pair("nose", "rightShoulder"),
            // Arm connections
            Pair("leftShoulder", "leftElbow"),
            Pair("leftElbow", "leftWrist"),
            Pair("rightShoulder", "rightElbow"),
            Pair("rightElbow", "rightWrist"),
            // Torso connections
            Pair("leftShoulder", "leftHip"),
            Pair("rightShoulder", "rightHip"),
            // Leg connections
            Pair("leftHip", "leftKnee"),
            Pair("leftKnee", "leftAnkle"),
            Pair("rightHip", "rightKnee"),
            Pair("rightKnee", "rightAnkle")
        )

        val landmarks = mapOf(
            "nose" to Pair(0.5f, 0.15f),
            "leftShoulder" to Pair(0.4f, 0.3f),
            "rightShoulder" to Pair(0.6f, 0.3f),
            "leftElbow" to Pair(0.35f, 0.45f),
            "rightElbow" to Pair(0.65f, 0.45f),
            "leftWrist" to Pair(0.3f, 0.6f),
            "rightWrist" to Pair(0.7f, 0.6f),
            "leftHip" to Pair(0.45f, 0.65f),
            "rightHip" to Pair(0.55f, 0.65f),
            "leftKnee" to Pair(0.44f, 0.8f),
            "rightKnee" to Pair(0.56f, 0.8f),
            "leftAnkle" to Pair(0.43f, 0.95f),
            "rightAnkle" to Pair(0.57f, 0.95f)
        )

        // Test multiple rotations
        val rotations = listOf(0, 90, 180, 270)

        for (rotation in rotations) {
            // ARRANGE: Mapper for this rotation
            val (width, height) = if (rotation == 90 || rotation == 270) {
                Pair(phoneLandscapeWidth, phoneLandscapeHeight)
            } else {
                Pair(phonePortraitWidth, phonePortraitHeight)
            }

            val mapper = CoordinateMapper(
                viewWidth = width,
                viewHeight = height,
                imageWidth = cameraWidth,
                imageHeight = cameraHeight,
                isFrontFacing = false,
                rotation = rotation
            )

            // ACT: Transform all landmarks
            val transformedLandmarks = landmarks.mapValues { (_, landmark) ->
                mapper.normalizedToPixel(landmark.first, landmark.second)
            }

            // ASSERT: Verify skeleton connectivity is maintained
            for ((joint1, joint2) in skeletonConnections) {
                val point1 = transformedLandmarks[joint1]!!
                val point2 = transformedLandmarks[joint2]!!

                val distance = kotlin.math.sqrt(
                    (point1.first - point2.first) * (point1.first - point2.first) +
                    (point1.second - point2.second) * (point1.second - point2.second)
                )

                // Distance should be reasonable (not too close, not too far)
                assertTrue(
                    "Skeleton connection $joint1-$joint2 should have reasonable distance at rotation $rotation°: distance=$distance",
                    distance > 10f && distance < kotlin.math.min(width, height) // Between 10px and screen dimension
                )

                // Both points should be within bounds
                assertTrue(
                    "Joint $joint1 should be within bounds at rotation $rotation°: ${point1.first}, ${point1.second}",
                    point1.first >= -errorTolerance && point1.first <= width + errorTolerance &&
                    point1.second >= -errorTolerance && point1.second <= height + errorTolerance
                )
            }
        }
    }
}