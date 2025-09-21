package com.posecoach.coregeom

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.*

/**
 * TDD Edge Cases for AngleUtils - These tests should FAIL initially
 * Testing specific geometric edge cases for robust pose estimation
 */
class AngleUtilsEdgeCasesTest {

    @Test
    fun `perpendicular vectors should calculate exact 90 degrees`() {
        // Test Case: Two perfectly perpendicular vectors
        // Vector 1: (1,0) - (0,0) = (1,0) - horizontal
        // Vector 2: (0,1) - (0,0) = (0,1) - vertical
        val angle = AngleUtils.angleDeg(
            ax = 1.0, ay = 0.0,  // Point A
            bx = 0.0, by = 0.0,  // Vertex B
            cx = 0.0, cy = 1.0   // Point C
        )

        // This should be EXACTLY 90.0 degrees, not just within tolerance
        assertThat(angle).isEqualTo(90.0)
    }

    @Test
    fun `collinear parallel vectors should return 0 degrees`() {
        // Test Case: Two vectors pointing in same direction
        // Vector 1: (1,0) - (0,0) = (1,0)
        // Vector 2: (2,0) - (0,0) = (2,0) - same direction, different magnitude
        val angle = AngleUtils.angleDeg(
            ax = 1.0, ay = 0.0,   // Point A
            bx = 0.0, by = 0.0,   // Vertex B
            cx = 2.0, cy = 0.0    // Point C
        )

        // Parallel vectors should have 0 degree angle
        assertThat(angle).isEqualTo(0.0)
    }

    @Test
    fun `collinear anti-parallel vectors should return 180 degrees`() {
        // Test Case: Two vectors pointing in opposite directions
        // Vector 1: (1,0) - (0,0) = (1,0)
        // Vector 2: (-1,0) - (0,0) = (-1,0) - opposite direction
        val angle = AngleUtils.angleDeg(
            ax = 1.0, ay = 0.0,    // Point A
            bx = 0.0, by = 0.0,    // Vertex B
            cx = -1.0, cy = 0.0    // Point C
        )

        // Anti-parallel vectors should have 180 degree angle
        assertThat(angle).isEqualTo(180.0)
    }

    @Test
    fun `zero magnitude first vector should handle NaN gracefully`() {
        // Test Case: First vector has zero magnitude (A = B)
        val angle = AngleUtils.angleDeg(
            ax = 0.0, ay = 0.0,    // Point A = B (zero vector)
            bx = 0.0, by = 0.0,    // Vertex B
            cx = 1.0, cy = 1.0     // Point C
        )

        // Should return NaN and handle gracefully
        assertThat(angle.isNaN()).isTrue()
    }

    @Test
    fun `zero magnitude second vector should handle NaN gracefully`() {
        // Test Case: Second vector has zero magnitude (C = B)
        val angle = AngleUtils.angleDeg(
            ax = 1.0, ay = 1.0,    // Point A
            bx = 0.0, by = 0.0,    // Vertex B
            cx = 0.0, cy = 0.0     // Point C = B (zero vector)
        )

        // Should return NaN and handle gracefully
        assertThat(angle.isNaN()).isTrue()
    }

    @Test
    fun `both zero magnitude vectors should handle NaN gracefully`() {
        // Test Case: Both vectors have zero magnitude (A = B = C)
        val angle = AngleUtils.angleDeg(
            ax = 0.0, ay = 0.0,    // Point A = B = C
            bx = 0.0, by = 0.0,    // Vertex B
            cx = 0.0, cy = 0.0     // Point C
        )

        // Should return NaN and handle gracefully
        assertThat(angle.isNaN()).isTrue()
    }

    @Test
    fun `very small vectors near threshold should handle edge case`() {
        // Test Case: Vectors just at the numerical precision threshold
        val epsilon = 1e-6
        val angle = AngleUtils.angleDeg(
            ax = epsilon / 2, ay = 0.0,      // Very small vector
            bx = 0.0, by = 0.0,             // Vertex B
            cx = 0.0, cy = epsilon / 2       // Very small vector
        )

        // Should return NaN due to being below threshold
        assertThat(angle.isNaN()).isTrue()
    }
}