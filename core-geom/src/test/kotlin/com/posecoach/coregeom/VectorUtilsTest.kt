package com.posecoach.coregeom

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.*

/**
 * Tests for VectorUtils shared utility functions
 * Ensures robust vector operations for pose estimation
 */
class VectorUtilsTest {

    @Test
    fun `Vector2D magnitude calculation`() {
        val v1 = VectorUtils.Vector2D(3.0, 4.0)
        assertThat(v1.magnitude()).isWithin(1e-9).of(5.0)

        val v2 = VectorUtils.Vector2D(0.0, 0.0)
        assertThat(v2.magnitude()).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `Vector2D normalization`() {
        val v = VectorUtils.Vector2D(3.0, 4.0)
        val normalized = v.normalized()

        assertThat(normalized.magnitude()).isWithin(1e-9).of(1.0)
        assertThat(normalized.x).isWithin(1e-9).of(0.6)
        assertThat(normalized.y).isWithin(1e-9).of(0.8)
    }

    @Test
    fun `Vector2D zero vector normalization`() {
        val zero = VectorUtils.Vector2D(0.0, 0.0)
        val normalized = zero.normalized()

        assertThat(normalized.x).isWithin(1e-9).of(0.0)
        assertThat(normalized.y).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `Vector2D dot product`() {
        val v1 = VectorUtils.Vector2D(1.0, 0.0)
        val v2 = VectorUtils.Vector2D(0.0, 1.0)
        val v3 = VectorUtils.Vector2D(1.0, 0.0)

        // Perpendicular vectors
        assertThat(v1.dot(v2)).isWithin(1e-9).of(0.0)

        // Parallel vectors
        assertThat(v1.dot(v3)).isWithin(1e-9).of(1.0)

        // Anti-parallel vectors
        val v4 = VectorUtils.Vector2D(-1.0, 0.0)
        assertThat(v1.dot(v4)).isWithin(1e-9).of(-1.0)
    }

    @Test
    fun `Vector2D angle calculation`() {
        val v1 = VectorUtils.Vector2D(1.0, 0.0)
        val v2 = VectorUtils.Vector2D(0.0, 1.0)

        val angle = v1.angleWith(v2)
        assertThat(angle).isWithin(1e-9).of(PI / 2)
    }

    @Test
    fun `collinearity detection`() {
        val v1 = VectorUtils.Vector2D(1.0, 0.0)
        val v2 = VectorUtils.Vector2D(2.0, 0.0)  // Same direction
        val v3 = VectorUtils.Vector2D(-1.0, 0.0)  // Opposite direction
        val v4 = VectorUtils.Vector2D(0.0, 1.0)  // Perpendicular

        assertThat(VectorUtils.areCollinear(v1, v2)).isTrue()
        assertThat(VectorUtils.areCollinear(v1, v3)).isTrue()
        assertThat(VectorUtils.areCollinear(v1, v4)).isFalse()
    }

    @Test
    fun `perpendicularity detection`() {
        val v1 = VectorUtils.Vector2D(1.0, 0.0)
        val v2 = VectorUtils.Vector2D(0.0, 1.0)
        val v3 = VectorUtils.Vector2D(2.0, 0.0)

        assertThat(VectorUtils.arePerpendicular(v1, v2)).isTrue()
        assertThat(VectorUtils.arePerpendicular(v1, v3)).isFalse()
    }

    @Test
    fun `vector from points creation`() {
        val vector = VectorUtils.vectorFromPoints(1.0, 2.0, 4.0, 6.0)

        assertThat(vector.x).isWithin(1e-9).of(3.0)
        assertThat(vector.y).isWithin(1e-9).of(4.0)
    }

    @Test
    fun `angle between vectors from three points`() {
        // Right angle: (0,0) -> (1,0) -> (1,1)
        val angle = VectorUtils.angleBetweenVectors(
            0.0, 0.0,  // Point A
            1.0, 0.0,  // Vertex B
            1.0, 1.0   // Point C
        )

        assertThat(angle).isWithin(1e-9).of(PI / 2)
    }

    @Test
    fun `distance calculations`() {
        val distance = VectorUtils.distance(0.0, 0.0, 3.0, 4.0)
        assertThat(distance).isWithin(1e-9).of(5.0)

        val distanceSquared = VectorUtils.distanceSquared(0.0, 0.0, 3.0, 4.0)
        assertThat(distanceSquared).isWithin(1e-9).of(25.0)
    }

    @Test
    fun `utility functions`() {
        // Clamp
        assertThat(VectorUtils.clamp(5.0, 0.0, 10.0)).isWithin(1e-9).of(5.0)
        assertThat(VectorUtils.clamp(-1.0, 0.0, 10.0)).isWithin(1e-9).of(0.0)
        assertThat(VectorUtils.clamp(15.0, 0.0, 10.0)).isWithin(1e-9).of(10.0)

        // Lerp
        assertThat(VectorUtils.lerp(0.0, 10.0, 0.5)).isWithin(1e-9).of(5.0)

        // Approximate equality
        assertThat(VectorUtils.isApproximatelyEqual(1.0, 1.0000000001)).isTrue()
        assertThat(VectorUtils.isApproximatelyEqual(1.0, 1.1)).isFalse()
    }

    @Test
    fun `vector arithmetic operations`() {
        val v1 = VectorUtils.Vector2D(1.0, 2.0)
        val v2 = VectorUtils.Vector2D(3.0, 4.0)

        val sum = v1 + v2
        assertThat(sum.x).isWithin(1e-9).of(4.0)
        assertThat(sum.y).isWithin(1e-9).of(6.0)

        val diff = v2 - v1
        assertThat(diff.x).isWithin(1e-9).of(2.0)
        assertThat(diff.y).isWithin(1e-9).of(2.0)

        val scaled = v1 * 2.0
        assertThat(scaled.x).isWithin(1e-9).of(2.0)
        assertThat(scaled.y).isWithin(1e-9).of(4.0)
    }
}