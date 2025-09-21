package com.posecoach.app.multimodal.processors

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

/**
 * Test suite for AdvancedVisionAnalyzer
 */
@RunWith(RobolectricTestRunner::class)
class AdvancedVisionAnalyzerTest {

    private lateinit var context: Context
    private lateinit var visionAnalyzer: AdvancedVisionAnalyzer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        visionAnalyzer = AdvancedVisionAnalyzer(context)
    }

    @Test
    fun `test scene analysis with good lighting`() = runTest {
        // Given
        val brightImage = createTestImage(brightness = 150)

        // When
        val result = visionAnalyzer.analyzeScene(brightImage)

        // Then
        assertNotNull(result)
        assertEquals("good", result.lightingConditions)
        assertTrue(result.confidence > 0.5f)
    }

    @Test
    fun `test scene analysis with poor lighting`() = runTest {
        // Given
        val darkImage = createTestImage(brightness = 30)

        // When
        val result = visionAnalyzer.analyzeScene(darkImage)

        // Then
        assertNotNull(result)
        assertEquals("poor_dark", result.lightingConditions)
        assertTrue(result.confidence > 0.3f)
    }

    @Test
    fun `test object detection for fitness equipment`() = runTest {
        // Given
        val image = createTestImage()

        // When
        val result = visionAnalyzer.analyzeScene(image)

        // Then
        assertNotNull(result)
        assertTrue(result.detectedObjects.isNotEmpty())
        assertTrue(result.detectedObjects.any { it.objectType == "yoga_mat" })
    }

    @Test
    fun `test facial expression analysis with pose landmarks`() = runTest {
        // Given
        val image = createTestImage()
        val landmarks = createTestPoseLandmarks()

        // When
        val result = visionAnalyzer.analyzeImage(image, landmarks)

        // Then
        assertNotNull(result)
        assertNotNull(result.facialExpressions)
        assertTrue(result.facialExpressions!!.confidence > 0.5f)
    }

    @Test
    fun `test safety assessment`() = runTest {
        // Given
        val image = createTestImage()
        val landmarks = createTestPoseLandmarks()

        // When
        val result = visionAnalyzer.analyzeScene(image, landmarks)

        // Then
        assertNotNull(result)
        assertNotNull(result.safetyAssessment)
        assertTrue(result.safetyAssessment!!.clearanceSpace >= 0f)
        assertTrue(result.safetyAssessment!!.stabilityRisk >= 0f)
        assertTrue(result.safetyAssessment!!.stabilityRisk <= 1f)
    }

    @Test
    fun `test gesture recognition`() = runTest {
        // Given
        val image = createTestImage()
        val landmarks = createTestPoseLandmarksWithRaisedHands()

        // When
        val result = visionAnalyzer.analyzeScene(image, landmarks)

        // Then
        assertNotNull(result)
        assertTrue(result.gestureRecognition.isNotEmpty())
        assertTrue(result.gestureRecognition.any { it.gestureType == "hands_raised" })
    }

    @Test
    fun `test spatial layout analysis`() = runTest {
        // Given
        val spaciousImage = createTestImage(width = 1920, height = 1080)

        // When
        val result = visionAnalyzer.analyzeScene(spaciousImage)

        // Then
        assertNotNull(result)
        assertTrue(listOf("spacious", "moderate", "organized").contains(result.spatialLayout))
    }

    @Test
    fun `test scene type classification`() = runTest {
        // Given
        val homeImage = createTestImage()

        // When
        val result = visionAnalyzer.analyzeScene(homeImage)

        // Then
        assertNotNull(result)
        assertTrue(listOf("gym", "home", "outdoor", "studio", "indoor").contains(result.sceneType))
    }

    @Test
    fun `test confidence calculation with multiple factors`() = runTest {
        // Given
        val highQualityImage = createTestImage(brightness = 150, width = 1920, height = 1080)
        val landmarks = createTestPoseLandmarks()

        // When
        val result = visionAnalyzer.analyzeImage(highQualityImage, landmarks)

        // Then
        assertNotNull(result)
        assertTrue(result.confidence > 0.7f) // High quality image should have high confidence
    }

    @Test
    fun `test error handling with null inputs`() = runTest {
        // When
        val result = visionAnalyzer.analyzeScene(null, null)

        // Then - should handle gracefully and return minimal context
        assertNotNull(result)
        assertEquals("unknown", result.sceneType)
        assertTrue(result.detectedObjects.isEmpty())
        assertTrue(result.confidence < 0.2f)
    }

    @Test
    fun `test performance with large image`() = runTest {
        // Given
        val largeImage = createTestImage(width = 4096, height = 3072)
        val landmarks = createTestPoseLandmarks()

        // When
        val startTime = System.currentTimeMillis()
        val result = visionAnalyzer.analyzeImage(largeImage, landmarks)
        val processingTime = System.currentTimeMillis() - startTime

        // Then
        assertNotNull(result)
        assertTrue(processingTime < 2000L) // Should complete within 2 seconds
        assertTrue(result.confidence > 0.5f)
    }

    @Test
    fun `test backlighting detection`() = runTest {
        // Given
        val backlitImage = createBacklitImage()

        // When
        val result = visionAnalyzer.analyzeScene(backlitImage)

        // Then
        assertEquals("backlighted", result.lightingConditions)
    }

    @Test
    fun `test equipment relevance scoring`() = runTest {
        // Given
        val image = createTestImage()

        // When
        val result = visionAnalyzer.analyzeScene(image)

        // Then
        assertNotNull(result)
        val yogaMat = result.detectedObjects.find { it.objectType == "yoga_mat" }
        assertNotNull(yogaMat)
        assertTrue(yogaMat!!.relevanceToExercise > 0.5f) // Yoga mat should be highly relevant
    }

    @Test
    fun `test safety recommendations generation`() = runTest {
        // Given
        val hazardousImage = createHazardousSceneImage()
        val landmarks = createTestPoseLandmarks()

        // When
        val result = visionAnalyzer.analyzeScene(hazardousImage, landmarks)

        // Then
        assertNotNull(result.safetyAssessment)
        assertTrue(result.safetyAssessment!!.potentialHazards.isNotEmpty())
        assertTrue(result.safetyAssessment!!.recommendedModifications.isNotEmpty())
    }

    // Helper methods for creating test data

    private fun createTestImage(
        width: Int = 640,
        height: Int = 480,
        brightness: Int = 128
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        // Fill with test pattern
        for (i in pixels.indices) {
            val x = i % width
            val y = i / width
            val pattern = ((x / 50) + (y / 50)) % 2
            val color = if (pattern == 0) {
                Color.rgb(brightness, brightness, brightness)
            } else {
                Color.rgb(brightness + 20, brightness + 20, brightness + 20)
            }
            pixels[i] = color
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun createBacklitImage(): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(640 * 480)

        // Create backlighting effect (bright edges, dark center)
        for (i in pixels.indices) {
            val x = i % 640
            val y = i / 640

            val distanceFromCenter = kotlin.math.sqrt(
                ((x - 320).toFloat() / 320).pow(2) + ((y - 240).toFloat() / 240).pow(2)
            )

            val brightness = (distanceFromCenter * 200 + 50).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(brightness, brightness, brightness)
        }

        bitmap.setPixels(pixels, 0, 640, 0, 0, 640, 480)
        return bitmap
    }

    private fun createHazardousSceneImage(): Bitmap {
        // Create an image that should trigger safety warnings
        return createTestImage(brightness = 30) // Very dark = poor visibility hazard
    }

    private fun createTestPoseLandmarks(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.5f,
                y = 0.5f + (index * 0.02f),
                z = 0f,
                visibility = 0.9f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            confidence = 0.8f,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun createTestPoseLandmarksWithRaisedHands(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            when (index) {
                15, 16 -> // Left and right wrists
                    PoseLandmarkResult.Landmark(
                        x = 0.5f,
                        y = 0.3f, // Raised position
                        z = 0f,
                        visibility = 0.9f
                    )
                else ->
                    PoseLandmarkResult.Landmark(
                        x = 0.5f,
                        y = 0.5f + (index * 0.02f),
                        z = 0f,
                        visibility = 0.9f
                    )
            }
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            confidence = 0.8f,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun Float.pow(exp: Float): Float {
        return kotlin.math.pow(this, exp)
    }
}

/**
 * Performance tests for AdvancedVisionAnalyzer
 */
@RunWith(RobolectricTestRunner::class)
class AdvancedVisionAnalyzerPerformanceTest {

    private lateinit var context: Context
    private lateinit var visionAnalyzer: AdvancedVisionAnalyzer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        visionAnalyzer = AdvancedVisionAnalyzer(context)
    }

    @Test
    fun `test processing time with various image sizes`() = runTest {
        val imageSizes = listOf(
            Pair(320, 240),   // Small
            Pair(640, 480),   // Medium
            Pair(1280, 720),  // HD
            Pair(1920, 1080)  // Full HD
        )

        imageSizes.forEach { (width, height) ->
            val image = createTestImage(width, height)
            val landmarks = createTestPoseLandmarks()

            val startTime = System.currentTimeMillis()
            val result = visionAnalyzer.analyzeImage(image, landmarks)
            val processingTime = System.currentTimeMillis() - startTime

            assertNotNull(result)
            assertTrue(processingTime < 3000L, "Processing time for ${width}x${height} was ${processingTime}ms")
            println("${width}x${height}: ${processingTime}ms")
        }
    }

    @Test
    fun `test memory usage with multiple analyses`() = runTest {
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()

        repeat(10) {
            val image = createTestImage(1920, 1080)
            val landmarks = createTestPoseLandmarks()
            visionAnalyzer.analyzeImage(image, landmarks)
        }

        runtime.gc() // Suggest garbage collection
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = finalMemory - initialMemory

        // Memory increase should be reasonable (less than 50MB)
        assertTrue(memoryIncrease < 50 * 1024 * 1024, "Memory increase was ${memoryIncrease / 1024 / 1024}MB")
    }

    private fun createTestImage(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    private fun createTestPoseLandmarks(): PoseLandmarkResult {
        val landmarks = (0..32).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.5f,
                y = 0.5f + (index * 0.02f),
                z = 0f,
                visibility = 0.9f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            confidence = 0.8f,
            timestamp = System.currentTimeMillis()
        )
    }
}