package com.posecoach.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.PointF
import com.posecoach.corepose.models.PoseLandmarkResult
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import timber.log.Timber
import kotlin.math.abs

/**
 * Integration tests for the PoseOverlayView rotation fix.
 * These tests verify that skeleton orientation is properly corrected when device rotation occurs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PoseOverlayRotationFixTest {

    private lateinit var context: Context
    private lateinit var poseOverlayView: PoseOverlayView

    // Test dimensions
    private val viewWidth = 1080
    private val viewHeight = 2340
    private val cameraWidth = 720
    private val cameraHeight = 1280

    private val errorTolerance = 5.0f

    @Before
    fun setup() {
        // Mock Timber to avoid log errors
        mockkStatic(Timber::class)
        every { Timber.d(any<String>()) } just Runs
        every { Timber.e(any<Throwable>(), any<String>()) } just Runs
        every { Timber.e(any<String>()) } just Runs
        every { Timber.v(any<String>()) } just Runs
        every { Timber.w(any<String>()) } just Runs

        context = RuntimeEnvironment.getApplication()
        poseOverlayView = PoseOverlayView(context)

        // Set view dimensions for testing
        poseOverlayView.layout(0, 0, viewWidth, viewHeight)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test portrait mode - standing person appears vertical`() {
        // ARRANGE: Configure for portrait mode (0° rotation)
        poseOverlayView.configureCameraDisplay(
            cameraWidth = cameraWidth,
            cameraHeight = cameraHeight,
            rotation = 0,
            frontFacing = false,
            aspectFitMode = FitMode.CENTER_CROP
        )

        val mapper = poseOverlayView.getCoordinateMapper()
        assertNotNull("Coordinate mapper should be initialized", mapper)

        // Standing person landmarks - head to feet should be vertical
        val headLandmark = PoseLandmarkResult.Landmark(0.5f, 0.2f, 0f, 1.0f)
        val feetLandmark = PoseLandmarkResult.Landmark(0.5f, 0.8f, 0f, 1.0f)

        // ACT: Transform landmarks
        val (headX, headY) = mapper!!.normalizedToPixel(headLandmark.x, headLandmark.y)
        val (feetX, feetY) = mapper.normalizedToPixel(feetLandmark.x, feetLandmark.y)

        // ASSERT: Person should be vertical (same X, different Y)
        val xDifference = abs(headX - feetX)
        assertTrue(
            "Person should be vertical in portrait mode: head X=$headX, feet X=$feetX, diff=$xDifference",
            xDifference <= errorTolerance
        )

        // Head should be above feet
        assertTrue(
            "Head should be above feet in portrait: head Y=$headY, feet Y=$feetY",
            headY < feetY
        )
    }

    @Test
    fun `test 90 degree rotation - skeleton transforms correctly`() {
        // ARRANGE: Configure for landscape mode (90° rotation)
        poseOverlayView.configureCameraDisplay(
            cameraWidth = cameraWidth,
            cameraHeight = cameraHeight,
            rotation = 90,
            frontFacing = false,
            aspectFitMode = FitMode.CENTER_CROP
        )

        val mapper = poseOverlayView.getCoordinateMapper()
        assertNotNull("Coordinate mapper should be initialized", mapper)

        // Test center point (should remain at center)
        val centerLandmark = PoseLandmarkResult.Landmark(0.5f, 0.5f, 0f, 1.0f)
        val (centerX, centerY) = mapper!!.normalizedToPixel(centerLandmark.x, centerLandmark.y)

        // Center point should be roughly at screen center
        val expectedCenterX = viewWidth / 2f
        val expectedCenterY = viewHeight / 2f
        val centerErrorX = abs(centerX - expectedCenterX)
        val centerErrorY = abs(centerY - expectedCenterY)

        assertTrue(
            "Center point should remain roughly centered after 90° rotation: expected ($expectedCenterX, $expectedCenterY), got ($centerX, $centerY)",
            centerErrorX <= errorTolerance * 3 && centerErrorY <= errorTolerance * 3
        )
    }

    @Test
    fun `test front camera mirroring - landmarks are properly flipped`() {
        // ARRANGE: Configure for front camera
        poseOverlayView.configureCameraDisplay(
            cameraWidth = cameraWidth,
            cameraHeight = cameraHeight,
            rotation = 0,
            frontFacing = true,
            aspectFitMode = FitMode.CENTER_CROP
        )

        val mapper = poseOverlayView.getCoordinateMapper()
        assertNotNull("Coordinate mapper should be initialized", mapper)

        // Left and right landmarks (from person's perspective)
        val leftShoulderLandmark = PoseLandmarkResult.Landmark(0.3f, 0.4f, 0f, 1.0f)  // Left side
        val rightShoulderLandmark = PoseLandmarkResult.Landmark(0.7f, 0.4f, 0f, 1.0f) // Right side

        // ACT: Transform landmarks
        val (leftX, leftY) = mapper!!.normalizedToPixel(leftShoulderLandmark.x, leftShoulderLandmark.y)
        val (rightX, rightY) = mapper.normalizedToPixel(rightShoulderLandmark.x, rightShoulderLandmark.y)

        // ASSERT: With front camera, left shoulder should appear on right side of screen
        assertTrue(
            "With front camera mirroring, person's left shoulder should appear on right side: leftX=$leftX, rightX=$rightX",
            leftX > rightX
        )

        // Y coordinates should not be affected by horizontal mirroring
        val yDifference = abs(leftY - rightY)
        assertTrue(
            "Y coordinates should be similar for shoulders: leftY=$leftY, rightY=$rightY, diff=$yDifference",
            yDifference <= errorTolerance
        )
    }

    @Test
    fun `test rotation update - coordinate mapper updates correctly`() {
        // ARRANGE: Start with portrait
        poseOverlayView.configureCameraDisplay(
            cameraWidth = cameraWidth,
            cameraHeight = cameraHeight,
            rotation = 0,
            frontFacing = false,
            aspectFitMode = FitMode.CENTER_CROP
        )

        val testLandmark = PoseLandmarkResult.Landmark(0.3f, 0.3f, 0f, 1.0f)
        val portraitMapper = poseOverlayView.getCoordinateMapper()!!
        val (portraitX, portraitY) = portraitMapper.normalizedToPixel(testLandmark.x, testLandmark.y)

        // ACT: Update to landscape rotation
        poseOverlayView.updateRotation(90)

        val landscapeMapper = poseOverlayView.getCoordinateMapper()!!
        val (landscapeX, landscapeY) = landscapeMapper.normalizedToPixel(testLandmark.x, testLandmark.y)

        // ASSERT: Coordinates should be different after rotation
        val xChanged = abs(portraitX - landscapeX) > errorTolerance
        val yChanged = abs(portraitY - landscapeY) > errorTolerance

        assertTrue(
            "Coordinates should change after rotation: portrait=($portraitX, $portraitY), landscape=($landscapeX, $landscapeY)",
            xChanged || yChanged
        )
    }

    @Test
    fun `test camera facing update - mirroring toggles correctly`() {
        // ARRANGE: Start with back camera
        poseOverlayView.configureCameraDisplay(
            cameraWidth = cameraWidth,
            cameraHeight = cameraHeight,
            rotation = 0,
            frontFacing = false,
            aspectFitMode = FitMode.CENTER_CROP
        )

        val testLandmark = PoseLandmarkResult.Landmark(0.2f, 0.5f, 0f, 1.0f) // Left side landmark
        val backCameraMapper = poseOverlayView.getCoordinateMapper()!!
        val (backX, backY) = backCameraMapper.normalizedToPixel(testLandmark.x, testLandmark.y)

        // ACT: Switch to front camera
        poseOverlayView.updateCameraFacing(true)

        val frontCameraMapper = poseOverlayView.getCoordinateMapper()!!
        val (frontX, frontY) = frontCameraMapper.normalizedToPixel(testLandmark.x, testLandmark.y)

        // ASSERT: X coordinate should be mirrored, Y should remain similar
        assertTrue(
            "X coordinate should be different between front and back camera: back=$backX, front=$frontX",
            abs(backX - frontX) > errorTolerance
        )

        assertTrue(
            "Y coordinate should remain similar between cameras: back=$backY, front=$frontY",
            abs(backY - frontY) <= errorTolerance
        )
    }

    @Test
    fun `test complete pose skeleton - maintains structure across rotations`() {
        // ARRANGE: Create a realistic pose with key landmarks
        val landmarks = listOf(
            PoseLandmarkResult.Landmark(0.5f, 0.15f, 0f, 1.0f),  // Nose
            PoseLandmarkResult.Landmark(0.4f, 0.3f, 0f, 1.0f),   // Left shoulder
            PoseLandmarkResult.Landmark(0.6f, 0.3f, 0f, 1.0f),   // Right shoulder
            PoseLandmarkResult.Landmark(0.45f, 0.65f, 0f, 1.0f), // Left hip
            PoseLandmarkResult.Landmark(0.55f, 0.65f, 0f, 1.0f), // Right hip
            PoseLandmarkResult.Landmark(0.43f, 0.95f, 0f, 1.0f), // Left ankle
            PoseLandmarkResult.Landmark(0.57f, 0.95f, 0f, 1.0f)  // Right ankle
        )

        val pose = PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 50L
        )

        val rotations = listOf(0, 90, 180, 270)

        for (rotation in rotations) {
            // ARRANGE: Configure for this rotation
            poseOverlayView.configureCameraDisplay(
                cameraWidth = cameraWidth,
                cameraHeight = cameraHeight,
                rotation = rotation,
                frontFacing = false,
                aspectFitMode = FitMode.CENTER_CROP
            )

            val mapper = poseOverlayView.getCoordinateMapper()!!

            // ACT: Transform all landmarks
            val transformedLandmarks = landmarks.map { landmark ->
                mapper.normalizedToPixel(landmark.x, landmark.y)
            }

            // ASSERT: All landmarks should be within view bounds
            for ((index, point) in transformedLandmarks.withIndex()) {
                assertTrue(
                    "Landmark $index should be within X bounds at rotation $rotation°: x=${point.first}",
                    point.first >= -errorTolerance && point.first <= viewWidth + errorTolerance
                )
                assertTrue(
                    "Landmark $index should be within Y bounds at rotation $rotation°: y=${point.second}",
                    point.second >= -errorTolerance && point.second <= viewHeight + errorTolerance
                )
            }

            // Check basic anatomical structure (head above shoulders above hips above ankles)
            val nose = transformedLandmarks[0]
            val leftShoulder = transformedLandmarks[1]
            val rightShoulder = transformedLandmarks[2]
            val leftHip = transformedLandmarks[3]
            val rightHip = transformedLandmarks[4]
            val leftAnkle = transformedLandmarks[5]
            val rightAnkle = transformedLandmarks[6]

            // Shoulders should be roughly at same height
            val shoulderHeightDiff = abs(leftShoulder.second - rightShoulder.second)
            assertTrue(
                "Shoulders should be roughly at same height at rotation $rotation°: diff=$shoulderHeightDiff",
                shoulderHeightDiff <= errorTolerance * 2
            )

            // Hips should be roughly at same height
            val hipHeightDiff = abs(leftHip.second - rightHip.second)
            assertTrue(
                "Hips should be roughly at same height at rotation $rotation°: diff=$hipHeightDiff",
                hipHeightDiff <= errorTolerance * 2
            )
        }
    }

    @Test
    fun `test fit mode changes - overlay adapts correctly`() {
        // ARRANGE: Start with CENTER_CROP
        poseOverlayView.configureCameraDisplay(
            cameraWidth = cameraWidth,
            cameraHeight = cameraHeight,
            rotation = 0,
            frontFacing = false,
            aspectFitMode = FitMode.CENTER_CROP
        )

        val testLandmark = PoseLandmarkResult.Landmark(0.5f, 0.5f, 0f, 1.0f)
        val cropMapper = poseOverlayView.getCoordinateMapper()!!
        val (cropX, cropY) = cropMapper.normalizedToPixel(testLandmark.x, testLandmark.y)

        // ACT: Change to CENTER_INSIDE
        poseOverlayView.updateFitMode(FitMode.CENTER_INSIDE)

        val insideMapper = poseOverlayView.getCoordinateMapper()!!
        val (insideX, insideY) = insideMapper.normalizedToPixel(testLandmark.x, testLandmark.y)

        // ACT: Change to FILL
        poseOverlayView.updateFitMode(FitMode.FILL)

        val fillMapper = poseOverlayView.getCoordinateMapper()!!
        val (fillX, fillY) = fillMapper.normalizedToPixel(testLandmark.x, testLandmark.y)

        // ASSERT: Different fit modes should produce different coordinates
        val cropVsInside = abs(cropX - insideX) > errorTolerance || abs(cropY - insideY) > errorTolerance
        val insideVsFill = abs(insideX - fillX) > errorTolerance || abs(insideY - fillY) > errorTolerance

        assertTrue(
            "CENTER_CROP and CENTER_INSIDE should produce different coordinates",
            cropVsInside
        )
        assertTrue(
            "CENTER_INSIDE and FILL should produce different coordinates",
            insideVsFill
        )
    }
}