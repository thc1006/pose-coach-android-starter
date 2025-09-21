package com.posecoach.coregeom

/**
 * One Euro Filter implementation for smoothing landmark coordinates.
 * Reduces jitter while maintaining responsiveness to real changes.
 * Based on the algorithm from Casiez & Roussel (CHI 2012).
 */
class OneEuroFilter(
    private var minCutoff: Double = 1.0,
    private var beta: Double = 0.0,
    private var dCutoff: Double = 1.0
) {
    private var prevValue: Double? = null
    private var prevDeriv: Double = 0.0
    private var prevTime: Double? = null

    /**
     * Calculate the smoothing factor based on cutoff frequency and time step.
     * Higher cutoff = less smoothing (more responsive)
     * Lower cutoff = more smoothing (less responsive)
     */
    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2 * Math.PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    /**
     * Filter a new measurement and return the smoothed value.
     *
     * @param x The new measurement value
     * @param timestampSec The timestamp in seconds
     * @return The filtered (smoothed) value
     */
    fun filter(x: Double, timestampSec: Double): Double {
        val t0 = prevTime
        val dt = if (t0 == null) 1.0 / 60.0 else maxOf(1e-6, timestampSec - t0)
        prevTime = timestampSec

        // Calculate and filter the derivative (rate of change)
        val dx = if (prevValue == null) 0.0 else (x - prevValue!!) / dt
        val aD = alpha(dCutoff, dt)
        prevDeriv = aD * dx + (1 - aD) * prevDeriv

        // Adaptive cutoff: increase responsiveness when signal is changing rapidly
        // beta controls how much the derivative affects the cutoff frequency
        val adaptiveCutoff = minCutoff + beta * kotlin.math.abs(prevDeriv)

        // Ensure cutoff doesn't become too high (which would disable filtering)
        val cutoff = kotlin.math.min(adaptiveCutoff, 10.0)

        val a = alpha(cutoff, dt)
        val result = if (prevValue == null) {
            x  // First measurement, no filtering
        } else {
            // Exponential smoothing: blend current and previous values
            a * x + (1 - a) * prevValue!!
        }

        prevValue = result
        return result
    }

    /**
     * Reset the filter state (useful when tracking is lost and reacquired)
     */
    fun reset() {
        prevValue = null
        prevDeriv = 0.0
        prevTime = null
    }

    /**
     * Check if the filter has been initialized with at least one measurement
     */
    fun isInitialized(): Boolean = prevValue != null
}
