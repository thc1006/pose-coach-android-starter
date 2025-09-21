package com.posecoach.corepose

import android.content.Context
import android.graphics.Bitmap
import com.posecoach.corepose.models.PoseDetectionError
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.repository.FakePoseRepository
import com.posecoach.corepose.repository.PoseDetectionListener
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.mock
import kotlinx.coroutines.delay

/**
 * Integration test demonstrating the complete pose detection workflow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IntegrationTest {

    @Test
    fun `complete pose detection workflow should work end-to-end`() = runTest {
        // Setup
        val context = mock<Context>()
        val bitmap = mock<Bitmap>()
        val repository = FakePoseRepository()
        val results = mutableListOf<PoseLandmarkResult>()
        val errors = mutableListOf<PoseDetectionError>()

        val listener = object : PoseDetectionListener {
            override fun onPoseDetected(result: PoseLandmarkResult) {
                results.add(result)
            }

            override fun onPoseDetectionError(error: PoseDetectionError) {
                errors.add(error)
            }
        }

        // Execute workflow
        repository.init(context)
        repository.start(listener)

        // Process multiple frames
        repeat(5) { frame ->
            repository.detectAsync(bitmap, System.currentTimeMillis() + frame * 33)
            delay(10) // Small delay to allow async processing
        }

        // Allow final processing
        delay(200)

        // Verify results
        assertTrue("Should detect poses", results.isNotEmpty())
        assertEquals("Should process all frames", 5, results.size)
        assertTrue("Should have no errors", errors.isEmpty())

        // Verify pose structure
        results.forEach { result ->
            assertEquals("Should have 33 landmarks", 33, result.landmarks.size)
            assertEquals("Should have 33 world landmarks", 33, result.worldLandmarks.size)
            assertTrue("Should have positive inference time", result.inferenceTimeMs > 0)

            // Verify landmark structure
            result.landmarks.forEach { landmark ->
                assertTrue("X coordinate should be in range", landmark.x in 0f..1f)
                assertTrue("Y coordinate should be in range", landmark.y in 0f..1f)
                assertTrue("Visibility should be in range", landmark.visibility in 0f..1f)
                assertTrue("Presence should be in range", landmark.presence in 0f..1f)
            }
        }

        // Cleanup
        repository.stop()
        assertFalse("Repository should be stopped", repository.isRunning())
    }

    @Test
    fun `multi-person pose generation should work correctly`() {
        val repository = FakePoseRepository()
        val timestampMs = System.currentTimeMillis()

        // Test different person counts
        for (personCount in 1..5) {
            val results = repository.generateMultiPersonPose(timestampMs, personCount)

            assertEquals("Should generate correct number of poses", personCount, results.size)

            results.forEachIndexed { index, result ->
                assertEquals("Timestamp should match", timestampMs, result.timestampMs)
                assertEquals("Should have 33 landmarks", 33, result.landmarks.size)

                // Each person should have slightly different inference time
                val expectedInferenceTime = 18 + index * 2
                assertEquals("Inference time should be correct", expectedInferenceTime, result.inferenceTimeMs.toInt())
            }

            // Verify different people have different positions
            if (results.size >= 2) {
                val firstPersonNose = results[0].landmarks[PoseLandmarks.NOSE]
                val secondPersonNose = results[1].landmarks[PoseLandmarks.NOSE]
                assertNotEquals("Different people should have different positions",
                              firstPersonNose.x, secondPersonNose.x)
            }
        }
    }

    @Test
    fun `low visibility pose should have appropriate characteristics`() {
        val repository = FakePoseRepository()
        val timestampMs = System.currentTimeMillis()

        val lowVisibilityResult = repository.generateLowVisibilityPose(timestampMs)
        val normalResult = repository.generateStablePose(timestampMs)

        // Low visibility should have higher inference time
        assertTrue("Low visibility should take longer to process",
                  lowVisibilityResult.inferenceTimeMs > normalResult.inferenceTimeMs)

        // Low visibility should have lower visibility values
        val avgLowVisibility = lowVisibilityResult.landmarks.map { it.visibility }.average()
        val avgNormalVisibility = normalResult.landmarks.map { it.visibility }.average()

        assertTrue("Low visibility poses should have lower visibility values",
                  avgLowVisibility < avgNormalVisibility)
    }

    @Test
    fun `pose landmarks should follow anatomical structure`() {
        val repository = FakePoseRepository()
        val result = repository.generateStablePose(System.currentTimeMillis())
        val landmarks = result.landmarks

        // Test basic anatomical relationships
        // Head should be above shoulders
        assertTrue("Nose should be above left shoulder",
                  landmarks[PoseLandmarks.NOSE].y < landmarks[PoseLandmarks.LEFT_SHOULDER].y)
        assertTrue("Nose should be above right shoulder",
                  landmarks[PoseLandmarks.NOSE].y < landmarks[PoseLandmarks.RIGHT_SHOULDER].y)

        // Shoulders should be above hips
        assertTrue("Left shoulder should be above left hip",
                  landmarks[PoseLandmarks.LEFT_SHOULDER].y < landmarks[PoseLandmarks.LEFT_HIP].y)
        assertTrue("Right shoulder should be above right hip",
                  landmarks[PoseLandmarks.RIGHT_SHOULDER].y < landmarks[PoseLandmarks.RIGHT_HIP].y)

        // Hips should be above knees
        assertTrue("Left hip should be above left knee",
                  landmarks[PoseLandmarks.LEFT_HIP].y < landmarks[PoseLandmarks.LEFT_KNEE].y)
        assertTrue("Right hip should be above right knee",
                  landmarks[PoseLandmarks.RIGHT_HIP].y < landmarks[PoseLandmarks.RIGHT_KNEE].y)

        // Knees should be above ankles
        assertTrue("Left knee should be above left ankle",
                  landmarks[PoseLandmarks.LEFT_KNEE].y < landmarks[PoseLandmarks.LEFT_ANKLE].y)
        assertTrue("Right knee should be above right ankle",
                  landmarks[PoseLandmarks.RIGHT_KNEE].y < landmarks[PoseLandmarks.RIGHT_ANKLE].y)

        // Arms should extend from shoulders
        assertTrue("Left elbow should be away from left shoulder",
                  kotlin.math.abs(landmarks[PoseLandmarks.LEFT_ELBOW].x - landmarks[PoseLandmarks.LEFT_SHOULDER].x) > 0.05f)
        assertTrue("Right elbow should be away from right shoulder",
                  kotlin.math.abs(landmarks[PoseLandmarks.RIGHT_ELBOW].x - landmarks[PoseLandmarks.RIGHT_SHOULDER].x) > 0.05f)
    }

    @Test
    fun `performance tracker integration should work`() = runTest {
        val repository = FakePoseRepository()
        val context = mock<Context>()
        val bitmap = mock<Bitmap>()
        val listener = object : PoseDetectionListener {
            override fun onPoseDetected(result: PoseLandmarkResult) {
                // Verify inference time is reasonable
                assertTrue("Inference time should be positive", result.inferenceTimeMs > 0)
                assertTrue("Inference time should be reasonable", result.inferenceTimeMs < 100)
            }

            override fun onPoseDetectionError(error: PoseDetectionError) {
                fail("Should not have errors in this test")
            }
        }

        repository.init(context)
        repository.start(listener)

        // Process frames with timing
        val frameCount = 10
        val startTime = System.currentTimeMillis()

        repeat(frameCount) {
            repository.detectAsync(bitmap, System.currentTimeMillis())
            delay(5)
        }

        delay(100) // Allow processing to complete

        val totalTime = System.currentTimeMillis() - startTime
        val avgTimePerFrame = totalTime.toDouble() / frameCount

        // Verify performance is reasonable
        assertTrue("Average time per frame should be reasonable (${avgTimePerFrame}ms)",
                  avgTimePerFrame < 50) // Less than 50ms per frame

        repository.stop()
    }

    @Test
    fun `no pose detection scenario should be handled gracefully`() {
        val repository = FakePoseRepository()
        val timestampMs = System.currentTimeMillis()

        val result = repository.generateNoPoseDetected(timestampMs)

        assertNull("No pose detected should return null", result)
    }
}