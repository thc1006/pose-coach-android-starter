package com.posecoach.coregeom

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.*
import kotlin.random.Random

/**
 * Property-based tests for OneEuroFilter to verify jitter reduction behavior
 * These tests verify the fundamental property: filtered output should be smoother than input
 */
class OneEuroFilterPropertyTest {

    @Test
    fun `filter should reduce jitter in high frequency noise`() {
        // Generate synthetic jittery input data simulating pose landmark noise
        val baseSignal = 100.0  // Stable pose position
        val jitterAmplitude = 10.0  // High-frequency noise amplitude
        val samples = 100
        val timestep = 1.0 / 30.0  // 30 FPS

        val filter = OneEuroFilter(
            minCutoff = 1.0,
            beta = 0.1,
            dCutoff = 1.0
        )

        val inputData = mutableListOf<Double>()
        val outputData = mutableListOf<Double>()

        // Generate jittery input signal
        for (i in 0 until samples) {
            val time = i * timestep
            // Add high-frequency jitter to base signal
            val jitter = jitterAmplitude * sin(20 * PI * time) * Random.nextDouble(-1.0, 1.0)
            val noisyValue = baseSignal + jitter
            inputData.add(noisyValue)

            val filteredValue = filter.filter(noisyValue, time)
            outputData.add(filteredValue)
        }

        // Property Test: Output should have lower variance than input
        val inputVariance = calculateVariance(inputData)
        val outputVariance = calculateVariance(outputData)

        // The filter should significantly reduce variance (jitter)
        assertThat(outputVariance).isLessThan(inputVariance * 0.5)

        // Output should also be closer to the base signal on average
        val inputDeviation = calculateMeanAbsoluteDeviation(inputData, baseSignal)
        val outputDeviation = calculateMeanAbsoluteDeviation(outputData, baseSignal)

        assertThat(outputDeviation).isLessThan(inputDeviation)
    }

    @Test
    fun `filter should maintain responsiveness to real changes`() {
        // Test that filter doesn't over-smooth legitimate pose changes
        val filter = OneEuroFilter(
            minCutoff = 1.0,
            beta = 0.5,  // Higher beta for more responsiveness
            dCutoff = 1.0
        )

        val timestep = 1.0 / 30.0
        var time = 0.0

        // Start with stable value
        val stableValue = 50.0
        repeat(10) {
            filter.filter(stableValue, time)
            time += timestep
        }

        // Make a legitimate large change (user moved)
        val newValue = 150.0
        val responses = mutableListOf<Double>()
        repeat(20) {
            val response = filter.filter(newValue, time)
            responses.add(response)
            time += timestep
        }

        // Filter should converge to new value within reasonable time
        val finalResponse = responses.last()
        val convergenceRatio = (finalResponse - stableValue) / (newValue - stableValue)

        // Should achieve at least 90% convergence to the new value
        assertThat(convergenceRatio).isAtLeast(0.9)
    }

    @Test
    fun `filter should handle step changes with appropriate settling time`() {
        // Test response to step input - fundamental control systems property
        val filter = OneEuroFilter(
            minCutoff = 2.0,
            beta = 0.1,
            dCutoff = 1.0
        )

        val timestep = 1.0 / 60.0  // 60 FPS for more resolution
        val stepAmplitude = 100.0

        // Step from 0 to stepAmplitude at time 0
        var time = 0.0
        val responses = mutableListOf<Double>()

        repeat(180) {  // 3 seconds at 60 FPS
            val response = filter.filter(stepAmplitude, time)
            responses.add(response)
            time += timestep
        }

        // Should reach 95% of step value within reasonable time (< 1 second)
        val targetValue = 0.95 * stepAmplitude
        val settlingIndex = responses.indexOfFirst { it >= targetValue }
        val settlingTime = settlingIndex * timestep

        // Settling time should be reasonable for pose tracking
        assertThat(settlingTime).isLessThan(1.0)  // Less than 1 second
        assertThat(settlingIndex).isGreaterThan(0)  // Should not be instantaneous
    }

    @Test
    fun `filter output should never exceed input bounds for stable signals`() {
        // Property: For bounded input, output should remain within reasonable bounds
        val filter = OneEuroFilter(
            minCutoff = 1.0,
            beta = 0.0,  // No adaptive cutoff for this test
            dCutoff = 1.0
        )

        val timestep = 1.0 / 30.0
        var time = 0.0

        val minInput = 10.0
        val maxInput = 90.0
        val inputs = mutableListOf<Double>()
        val outputs = mutableListOf<Double>()

        // Generate bounded random input
        repeat(100) {
            val input = Random.nextDouble(minInput, maxInput)
            val output = filter.filter(input, time)

            inputs.add(input)
            outputs.add(output)
            time += timestep
        }

        // After initial settling, output should stay within reasonable bounds
        val settledOutputs = outputs.drop(10)  // Skip initial transient
        val outputMin = settledOutputs.minOrNull() ?: 0.0
        val outputMax = settledOutputs.maxOrNull() ?: 0.0

        // Output bounds should not exceed input bounds by significant margin
        val tolerance = (maxInput - minInput) * 0.1  // 10% tolerance
        assertThat(outputMin).isAtLeast(minInput - tolerance)
        assertThat(outputMax).isAtMost(maxInput + tolerance)
    }

    private fun calculateVariance(data: List<Double>): Double {
        val mean = data.average()
        return data.map { (it - mean).pow(2) }.average()
    }

    private fun calculateMeanAbsoluteDeviation(data: List<Double>, target: Double): Double {
        return data.map { abs(it - target) }.average()
    }
}