package com.posecoach.corepose

class StablePoseGate(
    private val windowSec: Double = 1.5,
    private val posThreshold: Double = 0.01, // normalized units
    private val angleThresholdDeg: Double = 5.0
) {
    private var accTime = 0.0
    private var lastTimestamp: Double? = null

    /** Returns true exactly on the frame when stability window is reached. */
    fun update(posDelta: Double, angleDeltaDeg: Double, timestampSec: Double): Boolean {
        val last = lastTimestamp
        lastTimestamp = timestampSec
        if (last == null) {
            accTime = 0.0
            return false
        }
        val dt = (timestampSec - last).coerceAtLeast(1e-3)
        val stableNow = (posDelta <= posThreshold) && (angleDeltaDeg <= angleThresholdDeg)
        accTime = if (stableNow) (accTime + dt) else 0.0
        val justTriggered = accTime >= windowSec && (accTime - dt) < windowSec
        return justTriggered
    }
}
