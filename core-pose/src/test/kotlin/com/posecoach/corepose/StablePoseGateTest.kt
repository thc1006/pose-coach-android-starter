package com.posecoach.corepose

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StablePoseGateTest {
    @Test fun triggers_after_window() {
        val gate = StablePoseGate(windowSec = 1.0, posThreshold = 0.02, angleThresholdDeg = 5.0)
        var t = 0.0
        var triggered = false
        while (t < 1.2) {
            triggered = gate.update(posDelta = 0.0, angleDeltaDeg = 0.0, timestampSec = t)
            t += 0.1
        }
        assertThat(triggered).isTrue()
    }

    @Test fun resets_on_instability() {
        val gate = StablePoseGate(windowSec = 0.5, posThreshold = 0.02, angleThresholdDeg = 5.0)
        var t = 0.0
        repeat(3) {
            gate.update(0.0, 0.0, t); t += 0.1
        }
        // spike
        gate.update(0.1, 20.0, t); t += 0.1
        var ok = false
        repeat(10) {
            ok = gate.update(0.0, 0.0, t); t += 0.1
        }
        assertThat(ok).isTrue()
    }
}
