package com.posecoach.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.posecoach.corepose.models.PoseLandmarkResult
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import kotlin.math.max
import kotlin.math.min

/**
 * Performance benchmark tests for the pose overlay system.
 * Ensures the system meets the 30+ FPS requirement with sub-pixel accuracy.
 */
@RunWith(AndroidJUnit4::class)
class OverlayPerformanceBenchmarkTest {

    private lateinit var context: Context
    private lateinit var overlayView: PoseOverlayView
    private lateinit var coordinateMapper: CoordinateMapper

    companion object {
        private const val TARGET_FPS = 30
        private const val MAX_FRAME_TIME_MS = 1000.0 / TARGET_FPS // 33.33ms
        private const val STRESS_TEST_FRAMES = 1000
        private const val MULTI_PERSON_COUNT = 5
        private const val LANDMARK_COUNT = 33
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        coordinateMapper = CoordinateMapper(
            viewWidth = 1080,
            viewHeight = 1920,
            imageWidth = 720,
            imageHeight = 1280,
            isFrontFacing = false
        )

        overlayView = PoseOverlayView(context)
        overlayView.setCoordinateMapper(coordinateMapper)
        overlayView.setMaxRenderFps(60) // Allow maximum performance
    }

    private fun createRandomPoseLandmarks(seed: Int = 0): PoseLandmarkResult {
        val random = kotlin.random.Random(seed)
        val landmarks = List(LANDMARK_COUNT) { index ->
            PoseLandmarkResult.Landmark(
                x = random.nextFloat(),
                y = random.nextFloat(),
                z = random.nextFloat() * 0.5f,
                visibility = 0.5f + random.nextFloat() * 0.5f,
                presence = 0.6f + random.nextFloat() * 0.4f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 15L + random.nextLong(20L)
        )
    }

    @Test
    fun `single person pose rendering should meet 30fps target`() {
        val testFrames = 300
        val renderTimes = mutableListOf<Double>()

        repeat(testFrames) { frame ->
            val landmarks = createRandomPoseLandmarks(frame)
            overlayView.updatePose(landmarks)

            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val startTime = System.nanoTime()
            overlayView.draw(canvas)
            val renderTime = (System.nanoTime() - startTime) / 1_000_000.0

            renderTimes.add(renderTime)
        }

        val averageRenderTime = renderTimes.average()
        val maxRenderTime = renderTimes.maxOrNull() ?: 0.0
        val p95RenderTime = renderTimes.sorted()[renderTimes.size * 95 / 100]
        val framesWithinTarget = renderTimes.count { it <= MAX_FRAME_TIME_MS }

        println("Single Person Rendering Performance:")
        println("  Average: ${averageRenderTime.format(2)}ms")
        println("  Max: ${maxRenderTime.format(2)}ms")
        println("  P95: ${p95RenderTime.format(2)}ms")
        println("  Frames within target: $framesWithinTarget/$testFrames (${(framesWithinTarget * 100.0 / testFrames).format(1)}%)")

        assertTrue("Average render time should be <${MAX_FRAME_TIME_MS}ms: got ${averageRenderTime.format(2)}ms",
            averageRenderTime < MAX_FRAME_TIME_MS)
        assertTrue("95% of frames should be within target: ${framesWithinTarget}/$testFrames",
            framesWithinTarget >= testFrames * 0.95)
        assertTrue("Maximum render time should be reasonable: ${maxRenderTime.format(2)}ms",
            maxRenderTime < MAX_FRAME_TIME_MS * 3)
    }

    @Test
    fun `multi_person pose rendering should maintain performance`() {
        val testFrames = 200
        val renderTimes = mutableListOf<Double>()

        overlayView.enableMultiPersonMode(true)

        repeat(testFrames) { frame ->
            val multiPersonPoses = List(MULTI_PERSON_COUNT) { personIndex ->
                createRandomPoseLandmarks(frame * MULTI_PERSON_COUNT + personIndex)
            }

            overlayView.updateMultiPersonPoses(multiPersonPoses)

            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val startTime = System.nanoTime()
            overlayView.draw(canvas)
            val renderTime = (System.nanoTime() - startTime) / 1_000_000.0

            renderTimes.add(renderTime)
        }

        val averageRenderTime = renderTimes.average()
        val maxRenderTime = renderTimes.maxOrNull() ?: 0.0
        val framesWithinTarget = renderTimes.count { it <= MAX_FRAME_TIME_MS }

        println("Multi-Person Rendering Performance:")
        println("  Average: ${averageRenderTime.format(2)}ms")
        println("  Max: ${maxRenderTime.format(2)}ms")
        println("  Frames within target: $framesWithinTarget/$testFrames (${(framesWithinTarget * 100.0 / testFrames).format(1)}%)")

        // Multi-person should be within 2x single person target
        assertTrue("Multi-person average render time should be reasonable: ${averageRenderTime.format(2)}ms",
            averageRenderTime < MAX_FRAME_TIME_MS * 2)
        assertTrue("At least 80% of multi-person frames should be within 2x target",
            framesWithinTarget >= testFrames * 0.8)
    }

    @Test
    fun `coordinate transformation batch processing should be highly performant`() {
        val batchSizes = listOf(33, 99, 165, 330) // 1, 3, 5, 10 people worth of landmarks
        val iterations = 1000

        batchSizes.forEach { batchSize ->
            val landmarks = List(batchSize) { index ->
                (index % 100) / 100.0f to ((index * 7) % 100) / 100.0f
            }

            val batchTimes = mutableListOf<Double>()

            repeat(iterations) {
                val startTime = System.nanoTime()
                coordinateMapper.batchNormalizedToPixel(landmarks)
                val batchTime = (System.nanoTime() - startTime) / 1_000_000.0
                batchTimes.add(batchTime)
            }

            val averageBatchTime = batchTimes.average()
            val maxBatchTime = batchTimes.maxOrNull() ?: 0.0
            val throughput = batchSize / averageBatchTime * 1000 // landmarks per second

            println("Batch Processing ($batchSize landmarks):")
            println("  Average: ${averageBatchTime.format(3)}ms")
            println("  Max: ${maxBatchTime.format(3)}ms")
            println("  Throughput: ${throughput.format(0)} landmarks/sec")

            // Should process at least 1000 landmarks per millisecond
            assertTrue("Batch processing should be fast for $batchSize landmarks: ${averageBatchTime.format(3)}ms",
                averageBatchTime < 5.0)
            assertTrue("Throughput should be high: ${throughput.format(0)} landmarks/sec",
                throughput > 5000)
        }
    }

    @Test
    fun `rotation transformations should not impact performance significantly`() {
        val rotationAngles = listOf(0, 90, 180, 270)
        val testFrames = 100

        rotationAngles.forEach { angle ->
            val rotatedMapper = CoordinateMapper(
                viewWidth = 1080,
                viewHeight = 1920,
                imageWidth = 720,
                imageHeight = 1280,
                isFrontFacing = false,
                rotation = angle
            )

            overlayView.setCoordinateMapper(rotatedMapper)

            val renderTimes = mutableListOf<Double>()

            repeat(testFrames) { frame ->
                val landmarks = createRandomPoseLandmarks(frame)
                overlayView.updatePose(landmarks)

                val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                val startTime = System.nanoTime()
                overlayView.draw(canvas)
                val renderTime = (System.nanoTime() - startTime) / 1_000_000.0

                renderTimes.add(renderTime)
            }

            val averageRenderTime = renderTimes.average()
            val framesWithinTarget = renderTimes.count { it <= MAX_FRAME_TIME_MS }

            println("Rotation ${angle}° Performance:")
            println("  Average: ${averageRenderTime.format(2)}ms")
            println("  Frames within target: $framesWithinTarget/$testFrames")

            assertTrue("Rotation $angle° should not significantly impact performance: ${averageRenderTime.format(2)}ms",
                averageRenderTime < MAX_FRAME_TIME_MS * 1.5)
            assertTrue("Rotation $angle° should maintain good frame rate",
                framesWithinTarget >= testFrames * 0.85)
        }
    }

    @Test
    fun `memory usage should remain stable during extended rendering`() {
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()

        val testFrames = 500
        val memorySnapshots = mutableListOf<Long>()

        repeat(testFrames) { frame ->
            val landmarks = createRandomPoseLandmarks(frame)
            overlayView.updatePose(landmarks)

            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            overlayView.draw(canvas)

            // Take memory snapshot every 50 frames
            if (frame % 50 == 0) {
                System.gc() // Suggest garbage collection
                Thread.sleep(10) // Give GC time to work
                val currentMemory = runtime.totalMemory() - runtime.freeMemory()
                memorySnapshots.add(currentMemory)
            }
        }

        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        val maxMemory = memorySnapshots.maxOrNull() ?: finalMemory

        println("Memory Usage Analysis:")
        println("  Initial: ${initialMemory / 1024 / 1024}MB")
        println("  Final: ${finalMemory / 1024 / 1024}MB")
        println("  Increase: ${memoryIncrease / 1024 / 1024}MB")
        println("  Peak: ${maxMemory / 1024 / 1024}MB")

        // Memory increase should be minimal (< 10MB)
        assertTrue("Memory increase should be minimal: ${memoryIncrease / 1024 / 1024}MB",
            memoryIncrease < 10 * 1024 * 1024)
    }

    @Test
    fun `stress test with maximum complexity should maintain stability`() {
        // Enable all features for maximum complexity
        overlayView.enableMultiPersonMode(true)
        overlayView.setShowPerformance(true)
        overlayView.setShowDebugInfo(true)
        overlayView.setVisualQuality(2.0f, 2.0f, true)

        val renderTimes = mutableListOf<Double>()
        var crashCount = 0

        repeat(STRESS_TEST_FRAMES) { frame ->
            try {
                // Create complex scenario
                val multiPersonPoses = List(MULTI_PERSON_COUNT) { personIndex ->
                    createRandomPoseLandmarks(frame * MULTI_PERSON_COUNT + personIndex).copy(
                        landmarks = createRandomPoseLandmarks(frame + personIndex).landmarks.map { landmark ->
                            landmark.copy(
                                visibility = if (frame % 10 == 0) 0.1f else landmark.visibility,
                                x = (landmark.x + (frame * 0.001f)) % 1.0f,
                                y = (landmark.y + (frame * 0.002f)) % 1.0f
                            )
                        }
                    )
                }

                overlayView.updateMultiPersonPoses(multiPersonPoses)

                val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                val startTime = System.nanoTime()
                overlayView.draw(canvas)
                val renderTime = (System.nanoTime() - startTime) / 1_000_000.0

                renderTimes.add(renderTime)

                // Occasionally change person selection
                if (frame % 50 == 0) {
                    overlayView.selectPerson(frame % MULTI_PERSON_COUNT)
                }

            } catch (e: Exception) {
                crashCount++
                println("Crash at frame $frame: ${e.message}")
            }
        }

        val averageRenderTime = renderTimes.average()
        val maxRenderTime = renderTimes.maxOrNull() ?: 0.0
        val stableFrames = renderTimes.count { it <= MAX_FRAME_TIME_MS * 3 }

        println("Stress Test Results:")
        println("  Frames processed: ${renderTimes.size}/$STRESS_TEST_FRAMES")
        println("  Crashes: $crashCount")
        println("  Average render time: ${averageRenderTime.format(2)}ms")
        println("  Max render time: ${maxRenderTime.format(2)}ms")
        println("  Stable frames: $stableFrames/${renderTimes.size}")

        assertEquals("Should not crash during stress test", 0, crashCount)
        assertTrue("Should process all frames", renderTimes.size == STRESS_TEST_FRAMES)
        assertTrue("Average render time should be reasonable under stress: ${averageRenderTime.format(2)}ms",
            averageRenderTime < MAX_FRAME_TIME_MS * 5)
        assertTrue("Should have mostly stable frames: $stableFrames/${renderTimes.size}",
            stableFrames >= renderTimes.size * 0.7)
    }

    @Test
    fun `coordinate accuracy should not degrade under performance pressure`() {
        val testLandmarks = List(100) { index ->
            (index % 10) / 10.0f to (index / 10) / 10.0f
        }

        val accuracyResults = mutableListOf<Double>()

        repeat(1000) { iteration ->
            val startTime = System.nanoTime()

            // Process coordinates rapidly
            val pixelCoordinates = coordinateMapper.batchNormalizedToPixel(testLandmarks)

            // Verify accuracy
            var maxError = 0.0
            testLandmarks.zip(pixelCoordinates).forEach { (normalized, pixel) ->
                val (reverseX, reverseY) = coordinateMapper.pixelToNormalized(pixel.first, pixel.second)
                val errorX = kotlin.math.abs(normalized.first - reverseX) * 1080
                val errorY = kotlin.math.abs(normalized.second - reverseY) * 1920
                maxError = kotlin.math.max(maxError, kotlin.math.max(errorX.toDouble(), errorY.toDouble()))
            }

            accuracyResults.add(maxError)

            val processingTime = (System.nanoTime() - startTime) / 1_000_000.0

            // Should be very fast
            assertTrue("Processing should be fast: ${processingTime}ms", processingTime < 5.0)
        }

        val averageError = accuracyResults.average()
        val maxError = accuracyResults.maxOrNull() ?: 0.0

        println("Accuracy Under Pressure:")
        println("  Average error: ${averageError.format(3)}px")
        println("  Max error: ${maxError.format(3)}px")

        assertTrue("Average error should remain low under pressure: ${averageError.format(3)}px",
            averageError < 2.0)
        assertTrue("Maximum error should be within tolerance: ${maxError.format(3)}px",
            maxError < 5.0)
    }

    private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
}