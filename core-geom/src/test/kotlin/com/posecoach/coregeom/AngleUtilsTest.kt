package com.posecoach.coregeom

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AngleUtilsTest {
    @Test fun rightAngle_is90() {
        val deg = AngleUtils.angleDeg(0.0,0.0, 1.0,0.0, 1.0,1.0)
        assertThat(deg).isWithin(1e-6).of(90.0)
    }
    @Test fun degenerate_returnsNaN() {
        val deg = AngleUtils.angleDeg(0.0,0.0, 0.0,0.0, 1.0,1.0)
        assertThat(deg.isNaN()).isTrue()
    }
}
