package com.posecoach.corepose.repository

import android.content.Context
import android.graphics.Bitmap
import com.posecoach.corepose.models.PoseDetectionError
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.mock
import kotlinx.coroutines.delay

@OptIn(ExperimentalCoroutinesApi::class)
class FakePoseRepositoryTest {

    private lateinit var repository: FakePoseRepository
    private lateinit var mockContext: Context
    private lateinit var mockBitmap: Bitmap
    private lateinit var testListener: TestPoseDetectionListener

    @Before
    fun setup() {
        repository = FakePoseRepository()
        mockContext = mock()
        mockBitmap = mock()
        testListener = TestPoseDetectionListener()
    }

    @Test
    fun `init should complete successfully`() = runTest {
        repository.init(mockContext)
        assertTrue(true)
    }

    @Test
    fun `start should set running state to true`() {
        assertFalse(repository.isRunning())
        repository.start(testListener)
        assertTrue(repository.isRunning())
    }

    @Test
    fun `stop should set running state to false`() {
        repository.start(testListener)
        assertTrue(repository.isRunning())
        repository.stop()
        assertFalse(repository.isRunning())
    }

    @Test
    fun `detectAsync should return pose landmarks when running`() = runTest {
        repository.init(mockContext)
        repository.start(testListener)

        val timestampMs = System.currentTimeMillis()
        repository.detectAsync(mockBitmap, timestampMs)

        delay(50)

        assertNotNull(testListener.lastResult)
        val result = testListener.lastResult!!
        assertEquals(33, result.landmarks.size)
        assertEquals(33, result.worldLandmarks.size)
        assertEquals(timestampMs, result.timestampMs)
        assertTrue(result.inferenceTimeMs > 0)
    }

    @Test
    fun `detectAsync should not process when not running`() = runTest {
        repository.init(mockContext)

        val timestampMs = System.currentTimeMillis()
        repository.detectAsync(mockBitmap, timestampMs)

        delay(50)

        assertNull(testListener.lastResult)
    }

    @Test
    fun `generateStablePose should return valid landmarks`() {
        val timestampMs = System.currentTimeMillis()
        val result = repository.generateStablePose(timestampMs)

        assertEquals(33, result.landmarks.size)
        assertEquals(33, result.worldLandmarks.size)
        assertEquals(timestampMs, result.timestampMs)
        assertEquals(15L, result.inferenceTimeMs)

        result.landmarks.forEach { landmark ->
            assertTrue(landmark.x in 0f..1f)
            assertTrue(landmark.y in 0f..1f)
            assertEquals(0f, landmark.z)
            assertEquals(0.99f, landmark.visibility)
            assertEquals(0.99f, landmark.presence)
        }
    }

    @Test
    fun `landmark visibility and presence should be in valid range`() = runTest {
        repository.init(mockContext)
        repository.start(testListener)

        repository.detectAsync(mockBitmap, System.currentTimeMillis())
        delay(50)

        val result = testListener.lastResult!!
        result.landmarks.forEach { landmark ->
            assertTrue(landmark.visibility in 0f..1f)
            assertTrue(landmark.presence in 0f..1f)
        }
    }

    @Test
    fun `generateMultiPersonPose should create multiple poses`() {
        val timestampMs = System.currentTimeMillis()
        val results = repository.generateMultiPersonPose(timestampMs, 3)

        assertEquals(3, results.size)
        results.forEachIndexed { index, result ->
            assertEquals(timestampMs, result.timestampMs)
            assertEquals(33, result.landmarks.size)
            assertEquals(33, result.worldLandmarks.size)
            assertTrue(result.inferenceTimeMs > 0)
            assertEquals(18 + index * 2, result.inferenceTimeMs.toInt())
        }
    }

    @Test
    fun `generateNoPoseDetected should return null`() {
        val timestampMs = System.currentTimeMillis()
        val result = repository.generateNoPoseDetected(timestampMs)
        assertNull(result)
    }

    @Test
    fun `generateLowVisibilityPose should create pose with lower visibility`() {
        val timestampMs = System.currentTimeMillis()
        val result = repository.generateLowVisibilityPose(timestampMs)

        assertEquals(timestampMs, result.timestampMs)
        assertEquals(33, result.landmarks.size)
        assertEquals(33, result.worldLandmarks.size)
        assertTrue(result.inferenceTimeMs >= 25)

        // Check that landmarks have lower visibility values
        result.landmarks.forEach { landmark ->
            assertTrue(landmark.visibility < 0.7f)
            assertTrue(landmark.presence < 0.8f)
        }
    }

    @Test
    fun `realistic pose landmarks should have proper body structure`() {
        val timestampMs = System.currentTimeMillis()
        val result = repository.generateStablePose(timestampMs)
        val landmarks = result.landmarks

        // Verify basic anatomical relationships
        // Nose should be higher than shoulders
        assertTrue(landmarks[0].y < landmarks[11].y) // NOSE < LEFT_SHOULDER
        assertTrue(landmarks[0].y < landmarks[12].y) // NOSE < RIGHT_SHOULDER

        // Shoulders should be higher than hips
        assertTrue(landmarks[11].y < landmarks[23].y) // LEFT_SHOULDER < LEFT_HIP
        assertTrue(landmarks[12].y < landmarks[24].y) // RIGHT_SHOULDER < RIGHT_HIP

        // Hips should be higher than knees
        assertTrue(landmarks[23].y < landmarks[25].y) // LEFT_HIP < LEFT_KNEE
        assertTrue(landmarks[24].y < landmarks[26].y) // RIGHT_HIP < RIGHT_KNEE

        // Knees should be higher than ankles
        assertTrue(landmarks[25].y < landmarks[27].y) // LEFT_KNEE < LEFT_ANKLE
        assertTrue(landmarks[26].y < landmarks[28].y) // RIGHT_KNEE < RIGHT_ANKLE
    }

    @Test
    fun `multi person poses should have different positions`() {
        val timestampMs = System.currentTimeMillis()
        val results = repository.generateMultiPersonPose(timestampMs, 2)

        assertEquals(2, results.size)

        val firstPersonNose = results[0].landmarks[0]
        val secondPersonNose = results[1].landmarks[0]

        // Different people should have different x positions
        assertNotEquals(firstPersonNose.x, secondPersonNose.x)
    }

    private class TestPoseDetectionListener : PoseDetectionListener {
        var lastResult: PoseLandmarkResult? = null
        var lastError: PoseDetectionError? = null

        override fun onPoseDetected(result: PoseLandmarkResult) {
            lastResult = result
        }

        override fun onPoseDetectionError(error: PoseDetectionError) {
            lastError = error
        }
    }
}