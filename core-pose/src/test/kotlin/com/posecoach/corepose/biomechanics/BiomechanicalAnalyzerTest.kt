package com.posecoach.corepose.biomechanics

import com.posecoach.corepose.PoseLandmarks
import com.posecoach.corepose.biomechanics.models.*
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.corepose.utils.PerformanceTracker
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.*

/**
 * Comprehensive test suite for biomechanical analysis validation
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BiomechanicalAnalyzerTest {

    private lateinit var analyzer: BiomechanicalAnalyzer
    private lateinit var performanceTracker: PerformanceTracker

    @BeforeEach
    fun setup() {
        performanceTracker = PerformanceTracker()
        analyzer = BiomechanicalAnalyzer(performanceTracker)
    }

    @Test
    fun `test basic pose analysis completes within time limit`() = runBlocking {
        val landmarks = createValidPoseLandmarks()

        val startTime = System.currentTimeMillis()
        val result = analyzer.analyzePose(landmarks)
        val processingTime = System.currentTimeMillis() - startTime

        // Verify performance requirement
        assertTrue(processingTime < 50, "Processing took ${processingTime}ms, should be under 50ms")

        // Verify result structure
        assertNotNull(result)
        assertEquals(landmarks.timestampMs, result.timestamp)
        assertTrue(result.processingTimeMs > 0)
        assertNotNull(result.jointAngles)
        assertNotNull(result.asymmetryAnalysis)
        assertNotNull(result.posturalAnalysis)
        assertNotNull(result.kineticChainAnalysis)
        assertNotNull(result.movementQuality)
    }

    @Test
    fun `test joint angle calculation accuracy`() = runBlocking {
        val landmarks = createSquatPositionPose()
        val result = analyzer.analyzePose(landmarks)

        val jointAngles = result.jointAngles

        // Verify key joints are calculated
        assertTrue(jointAngles.containsKey("left_knee"))
        assertTrue(jointAngles.containsKey("right_knee"))
        assertTrue(jointAngles.containsKey("left_hip"))
        assertTrue(jointAngles.containsKey("right_hip"))

        // Verify angle ranges are reasonable for squat position
        val leftKnee = jointAngles["left_knee"]!!
        assertTrue(leftKnee.angle in 45f..135f, "Left knee angle ${leftKnee.angle} not in expected squat range")
        assertTrue(leftKnee.isWithinNormalRange)

        val rightKnee = jointAngles["right_knee"]!!
        assertTrue(rightKnee.angle in 45f..135f, "Right knee angle ${rightKnee.angle} not in expected squat range")
    }

    @Test
    fun `test asymmetry detection sensitivity`() = runBlocking {
        val asymmetricLandmarks = createAsymmetricPose()
        val result = analyzer.analyzePose(asymmetricLandmarks)

        val asymmetryAnalysis = result.asymmetryAnalysis

        // Should detect significant asymmetry
        assertTrue(asymmetryAnalysis.overallAsymmetryScore > 0.2f,
                  "Should detect asymmetry, score: ${asymmetryAnalysis.overallAsymmetryScore}")
        assertTrue(asymmetryAnalysis.recommendations.isNotEmpty())

        // Test symmetric pose
        val symmetricLandmarks = createSymmetricPose()
        val symmetricResult = analyzer.analyzePose(symmetricLandmarks)

        assertTrue(symmetricResult.asymmetryAnalysis.overallAsymmetryScore < 0.1f,
                  "Should detect low asymmetry in symmetric pose")
    }

    @Test
    fun `test postural assessment accuracy`() = runBlocking {
        // Test good posture
        val goodPosture = createGoodPosturePose()
        val goodResult = analyzer.analyzePose(goodPosture)

        assertTrue(goodResult.posturalAnalysis.overallPostureScore > 0.8f,
                  "Good posture should score high: ${goodResult.posturalAnalysis.overallPostureScore}")
        assertEquals(PosturalStatus.EXCELLENT, goodResult.posturalAnalysis.headPosition.status)

        // Test poor posture
        val poorPosture = createPoorPosturePose()
        val poorResult = analyzer.analyzePose(poorPosture)

        assertTrue(poorResult.posturalAnalysis.overallPostureScore < 0.6f,
                  "Poor posture should score low: ${poorResult.posturalAnalysis.overallPostureScore}")
        assertTrue(poorResult.posturalAnalysis.posturalDeviations.isNotEmpty())
    }

    @Test
    fun `test kinetic chain analysis`() = runBlocking {
        val landmarks = createValidPoseLandmarks()
        val result = analyzer.analyzePose(landmarks)

        val kineticChain = result.kineticChainAnalysis

        // Verify all chains are analyzed
        assertNotNull(kineticChain.leftArmChain)
        assertNotNull(kineticChain.rightArmChain)
        assertNotNull(kineticChain.leftLegChain)
        assertNotNull(kineticChain.rightLegChain)
        assertNotNull(kineticChain.coreStability)

        // Verify efficiency scores are reasonable
        assertTrue(kineticChain.leftArmChain.efficiency in 0f..1f)
        assertTrue(kineticChain.rightArmChain.efficiency in 0f..1f)
        assertTrue(kineticChain.overallEfficiency in 0f..1f)
    }

    @Test
    fun `test movement quality scoring`() = runBlocking {
        val landmarks = createValidPoseLandmarks()
        val result = analyzer.analyzePose(landmarks)

        val movementQuality = result.movementQuality

        // Verify all quality components are scored
        assertTrue(movementQuality.overallScore in 0f..100f)
        assertTrue(movementQuality.rangeOfMotionScore in 0f..100f)
        assertTrue(movementQuality.symmetryScore in 0f..100f)
        assertTrue(movementQuality.posturalScore in 0f..100f)
        assertTrue(movementQuality.coordinationScore in 0f..100f)

        // Verify recommendations are provided for low scores
        if (movementQuality.overallScore < 70f) {
            assertTrue(movementQuality.recommendations.isNotEmpty())
        }
    }

    @Test
    fun `test confidence scoring`() = runBlocking {
        // High visibility landmarks
        val highVisibilityLandmarks = createHighVisibilityPose()
        val highVisResult = analyzer.analyzePose(highVisibilityLandmarks)
        assertTrue(highVisResult.confidenceScore > 0.8f)

        // Low visibility landmarks
        val lowVisibilityLandmarks = createLowVisibilityPose()
        val lowVisResult = analyzer.analyzePose(lowVisibilityLandmarks)
        assertTrue(lowVisResult.confidenceScore < 0.5f)
    }

    @Test
    fun `test temporal analysis with multiple poses`() = runBlocking {
        // Simulate movement sequence
        val poses = createMovementSequence()

        var lastResult: BiomechanicalAnalysisResult? = null
        for (pose in poses) {
            val result = analyzer.analyzePose(pose)

            // Later poses should have movement pattern analysis
            if (lastResult != null) {
                // Verify temporal consistency
                val angleDifference = kotlin.math.abs(
                    result.jointAngles["left_knee"]?.angle ?: 0f -
                    lastResult.jointAngles["left_knee"]?.angle ?: 0f
                )
                // Angles shouldn't change drastically between frames
                assertTrue(angleDifference < 30f, "Angle change too large: $angleDifference")
            }

            lastResult = result
        }
    }

    @Test
    fun `test fatigue detection over time`() = runBlocking {
        // Simulate fatigue by creating poses with decreasing quality
        val fatigueSequence = createFatigueSequence()

        var lastFatigueScore = 0f
        for ((index, pose) in fatigueSequence.withIndex()) {
            val result = analyzer.analyzePose(pose)

            if (index > 15) { // After enough data for fatigue detection
                val currentFatigueScore = result.fatigueIndicators.overallFatigueScore

                // Fatigue score should generally increase over time
                if (index > 20) {
                    assertTrue(currentFatigueScore >= lastFatigueScore * 0.8f,
                              "Fatigue should be detected over time")
                }

                lastFatigueScore = currentFatigueScore
            }
        }
    }

    @Test
    fun `test error handling with invalid landmarks`() = runBlocking {
        val invalidLandmarks = createInvalidPoseLandmarks()

        // Should not throw exception, but handle gracefully
        assertDoesNotThrow {
            val result = analyzer.analyzePose(invalidLandmarks)
            assertNotNull(result)
            assertTrue(result.confidenceScore < 0.3f)
        }
    }

    @Test
    fun `test performance consistency under load`() = runBlocking {
        val landmarks = createValidPoseLandmarks()
        val processingTimes = mutableListOf<Long>()

        // Run multiple analyses to test consistency
        repeat(20) {
            val startTime = System.currentTimeMillis()
            analyzer.analyzePose(landmarks)
            val processingTime = System.currentTimeMillis() - startTime
            processingTimes.add(processingTime)
        }

        // Verify performance consistency
        val avgTime = processingTimes.average()
        val maxTime = processingTimes.maxOrNull() ?: 0L

        assertTrue(avgTime < 40.0, "Average processing time too high: ${avgTime}ms")
        assertTrue(maxTime < 60L, "Max processing time too high: ${maxTime}ms")

        // Verify performance doesn't degrade significantly
        val variance = processingTimes.map { (it - avgTime) * (it - avgTime) }.average()
        assertTrue(variance < 100.0, "Processing time variance too high: $variance")
    }

    @Test
    fun `test memory efficiency`() = runBlocking {
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // Process many poses to test memory usage
        repeat(100) {
            val landmarks = createValidPoseLandmarks()
            analyzer.analyzePose(landmarks)
        }

        // Force garbage collection
        System.gc()
        Thread.sleep(100)

        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryIncrease = finalMemory - initialMemory

        // Memory increase should be reasonable (less than 50MB)
        assertTrue(memoryIncrease < 50 * 1024 * 1024,
                  "Memory usage increased by ${memoryIncrease / 1024 / 1024}MB")
    }

    // Helper functions to create test pose data
    private fun createValidPoseLandmarks(): PoseLandmarkResult {
        val landmarks = (0 until 33).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.5f + (index % 3 - 1) * 0.1f,
                y = 0.5f + (index / 11) * 0.1f,
                z = 0f,
                visibility = 0.9f,
                presence = 0.95f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 10L
        )
    }

    private fun createSquatPositionPose(): PoseLandmarkResult {
        val landmarks = createValidPoseLandmarks().landmarks.toMutableList()

        // Adjust landmarks to simulate squat position
        // Lower hips
        landmarks[PoseLandmarks.LEFT_HIP] = landmarks[PoseLandmarks.LEFT_HIP].copy(y = 0.6f)
        landmarks[PoseLandmarks.RIGHT_HIP] = landmarks[PoseLandmarks.RIGHT_HIP].copy(y = 0.6f)

        // Bend knees
        landmarks[PoseLandmarks.LEFT_KNEE] = landmarks[PoseLandmarks.LEFT_KNEE].copy(y = 0.75f)
        landmarks[PoseLandmarks.RIGHT_KNEE] = landmarks[PoseLandmarks.RIGHT_KNEE].copy(y = 0.75f)

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 10L
        )
    }

    private fun createAsymmetricPose(): PoseLandmarkResult {
        val landmarks = createValidPoseLandmarks().landmarks.toMutableList()

        // Create obvious asymmetry - raise left shoulder
        landmarks[PoseLandmarks.LEFT_SHOULDER] = landmarks[PoseLandmarks.LEFT_SHOULDER].copy(y = 0.4f)
        landmarks[PoseLandmarks.LEFT_ELBOW] = landmarks[PoseLandmarks.LEFT_ELBOW].copy(y = 0.45f)

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 10L
        )
    }

    private fun createSymmetricPose(): PoseLandmarkResult {
        val landmarks = createValidPoseLandmarks().landmarks.toMutableList()

        // Ensure perfect symmetry
        landmarks[PoseLandmarks.LEFT_SHOULDER] = landmarks[PoseLandmarks.LEFT_SHOULDER].copy(y = 0.45f)
        landmarks[PoseLandmarks.RIGHT_SHOULDER] = landmarks[PoseLandmarks.RIGHT_SHOULDER].copy(y = 0.45f)
        landmarks[PoseLandmarks.LEFT_HIP] = landmarks[PoseLandmarks.LEFT_HIP].copy(y = 0.55f)
        landmarks[PoseLandmarks.RIGHT_HIP] = landmarks[PoseLandmarks.RIGHT_HIP].copy(y = 0.55f)

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 10L
        )
    }

    private fun createGoodPosturePose(): PoseLandmarkResult {
        val landmarks = createValidPoseLandmarks().landmarks.toMutableList()

        // Create ideal posture alignment
        landmarks[PoseLandmarks.NOSE] = landmarks[PoseLandmarks.NOSE].copy(x = 0.5f, y = 0.2f)
        landmarks[PoseLandmarks.LEFT_SHOULDER] = landmarks[PoseLandmarks.LEFT_SHOULDER].copy(x = 0.4f, y = 0.35f)
        landmarks[PoseLandmarks.RIGHT_SHOULDER] = landmarks[PoseLandmarks.RIGHT_SHOULDER].copy(x = 0.6f, y = 0.35f)
        landmarks[PoseLandmarks.LEFT_HIP] = landmarks[PoseLandmarks.LEFT_HIP].copy(x = 0.42f, y = 0.55f)
        landmarks[PoseLandmarks.RIGHT_HIP] = landmarks[PoseLandmarks.RIGHT_HIP].copy(x = 0.58f, y = 0.55f)

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 10L
        )
    }

    private fun createPoorPosturePose(): PoseLandmarkResult {
        val landmarks = createValidPoseLandmarks().landmarks.toMutableList()

        // Create poor posture - forward head, rounded shoulders
        landmarks[PoseLandmarks.NOSE] = landmarks[PoseLandmarks.NOSE].copy(x = 0.6f, y = 0.2f)
        landmarks[PoseLandmarks.LEFT_SHOULDER] = landmarks[PoseLandmarks.LEFT_SHOULDER].copy(x = 0.45f, y = 0.38f)
        landmarks[PoseLandmarks.RIGHT_SHOULDER] = landmarks[PoseLandmarks.RIGHT_SHOULDER].copy(x = 0.65f, y = 0.36f)

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 10L
        )
    }

    private fun createHighVisibilityPose(): PoseLandmarkResult {
        val landmarks = (0 until 33).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.5f + (index % 3 - 1) * 0.1f,
                y = 0.5f + (index / 11) * 0.1f,
                z = 0f,
                visibility = 0.95f,
                presence = 0.98f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 10L
        )
    }

    private fun createLowVisibilityPose(): PoseLandmarkResult {
        val landmarks = (0 until 33).map { index ->
            PoseLandmarkResult.Landmark(
                x = 0.5f + (index % 3 - 1) * 0.1f,
                y = 0.5f + (index / 11) * 0.1f,
                z = 0f,
                visibility = 0.3f,
                presence = 0.4f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 10L
        )
    }

    private fun createMovementSequence(): List<PoseLandmarkResult> {
        val baseTime = System.currentTimeMillis()
        return (0 until 20).map { index ->
            val landmarks = createValidPoseLandmarks().landmarks.toMutableList()

            // Simulate gradual movement - progressive squat
            val progress = index / 19f
            val hipY = 0.5f + progress * 0.15f // Gradually lower hips
            val kneeY = 0.6f + progress * 0.15f // Gradually lower knees

            landmarks[PoseLandmarks.LEFT_HIP] = landmarks[PoseLandmarks.LEFT_HIP].copy(y = hipY)
            landmarks[PoseLandmarks.RIGHT_HIP] = landmarks[PoseLandmarks.RIGHT_HIP].copy(y = hipY)
            landmarks[PoseLandmarks.LEFT_KNEE] = landmarks[PoseLandmarks.LEFT_KNEE].copy(y = kneeY)
            landmarks[PoseLandmarks.RIGHT_KNEE] = landmarks[PoseLandmarks.RIGHT_KNEE].copy(y = kneeY)

            PoseLandmarkResult(
                landmarks = landmarks,
                worldLandmarks = landmarks,
                timestampMs = baseTime + index * 33, // ~30fps
                inferenceTimeMs = 10L
            )
        }
    }

    private fun createFatigueSequence(): List<PoseLandmarkResult> {
        val baseTime = System.currentTimeMillis()
        return (0 until 30).map { index ->
            val landmarks = createValidPoseLandmarks().landmarks.toMutableList()

            // Simulate fatigue - increasing postural decline and movement variability
            val fatigue = index / 29f
            val noise = fatigue * 0.02f // Increasing noise

            // Add increasing variability to simulate fatigue
            landmarks.forEachIndexed { landmarkIndex, landmark ->
                if (landmarkIndex in listOf(PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER,
                                          PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP)) {
                    landmarks[landmarkIndex] = landmark.copy(
                        x = landmark.x + (Math.random().toFloat() - 0.5f) * noise,
                        y = landmark.y + (Math.random().toFloat() - 0.5f) * noise,
                        visibility = landmark.visibility - fatigue * 0.2f
                    )
                }
            }

            PoseLandmarkResult(
                landmarks = landmarks,
                worldLandmarks = landmarks,
                timestampMs = baseTime + index * 33,
                inferenceTimeMs = 10L
            )
        }
    }

    private fun createInvalidPoseLandmarks(): PoseLandmarkResult {
        val landmarks = (0 until 33).map { index ->
            PoseLandmarkResult.Landmark(
                x = Float.NaN,
                y = Float.NaN,
                z = Float.NaN,
                visibility = 0f,
                presence = 0f
            )
        }

        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = System.currentTimeMillis(),
            inferenceTimeMs = 10L
        )
    }
}