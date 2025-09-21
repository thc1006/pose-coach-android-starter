package com.posecoach.coregeom

import kotlin.math.*

/**
 * Utility functions for 2D vector operations commonly used in pose estimation
 * and geometric calculations. Optimized for real-time pose landmark processing.
 */
object VectorUtils {

    /**
     * Represents a 2D vector with x and y components
     */
    data class Vector2D(val x: Double, val y: Double) {
        /** Calculate the magnitude (length) of this vector */
        fun magnitude(): Double = hypot(x, y)

        /** Calculate the squared magnitude (avoids sqrt for performance) */
        fun magnitudeSquared(): Double = x * x + y * y

        /** Normalize this vector to unit length */
        fun normalized(): Vector2D {
            val mag = magnitude()
            return if (mag < 1e-14) Vector2D(0.0, 0.0) else Vector2D(x / mag, y / mag)
        }

        /** Check if this vector is effectively zero (below numerical threshold) */
        fun isZero(threshold: Double = 1e-6): Boolean = magnitude() < threshold

        /** Calculate dot product with another vector */
        fun dot(other: Vector2D): Double = x * other.x + y * other.y

        /** Calculate the angle with another vector in radians */
        fun angleWith(other: Vector2D): Double {
            val mag1 = magnitude()
            val mag2 = other.magnitude()

            // Guard against degenerate vectors
            if (mag1 < 1e-14 || mag2 < 1e-14) return Double.NaN

            val cosAngle = dot(other) / (mag1 * mag2)
            return acos(cosAngle.coerceIn(-1.0, 1.0))
        }

        operator fun plus(other: Vector2D) = Vector2D(x + other.x, y + other.y)
        operator fun minus(other: Vector2D) = Vector2D(x - other.x, y - other.y)
        operator fun times(scalar: Double) = Vector2D(x * scalar, y * scalar)
    }

    /**
     * Create a vector from point A to point B
     */
    fun vectorFromPoints(ax: Double, ay: Double, bx: Double, by: Double): Vector2D {
        return Vector2D(bx - ax, by - ay)
    }

    /**
     * Calculate the angle between two vectors formed by three points A-B-C.
     * Returns the angle at vertex B between vectors BA and BC.
     *
     * @return Angle in radians, or NaN if vectors are degenerate
     */
    fun angleBetweenVectors(
        ax: Double, ay: Double,  // Point A
        bx: Double, by: Double,  // Vertex B
        cx: Double, cy: Double   // Point C
    ): Double {
        val v1 = vectorFromPoints(bx, by, ax, ay)  // Vector B->A
        val v2 = vectorFromPoints(bx, by, cx, cy)  // Vector B->C

        return v1.angleWith(v2)
    }

    /**
     * Check if two vectors are collinear (parallel or anti-parallel)
     *
     * @param threshold The numerical threshold for collinearity check
     * @return true if vectors are collinear within the threshold
     */
    fun areCollinear(v1: Vector2D, v2: Vector2D, threshold: Double = 1e-12): Boolean {
        if (v1.isZero() || v2.isZero()) return true

        val normalizedDot = abs(v1.dot(v2) / (v1.magnitude() * v2.magnitude()))
        return abs(normalizedDot - 1.0) < threshold
    }

    /**
     * Check if two vectors are perpendicular
     *
     * @param threshold The numerical threshold for perpendicularity check
     * @return true if vectors are perpendicular within the threshold
     */
    fun arePerpendicular(v1: Vector2D, v2: Vector2D, threshold: Double = 1e-12): Boolean {
        if (v1.isZero() || v2.isZero()) return false

        val normalizedDot = abs(v1.dot(v2) / (v1.magnitude() * v2.magnitude()))
        return normalizedDot < threshold
    }

    /**
     * Calculate the distance between two points
     */
    fun distance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        return hypot(x2 - x1, y2 - y1)
    }

    /**
     * Calculate squared distance between two points (avoids sqrt for performance)
     */
    fun distanceSquared(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return dx * dx + dy * dy
    }

    /**
     * Clamp a value between min and max bounds
     */
    fun clamp(value: Double, min: Double, max: Double): Double {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }

    /**
     * Linear interpolation between two values
     */
    fun lerp(a: Double, b: Double, t: Double): Double {
        return a + t * (b - a)
    }

    /**
     * Check if a value is approximately equal to another within a tolerance
     */
    fun isApproximatelyEqual(a: Double, b: Double, tolerance: Double = 1e-9): Boolean {
        return abs(a - b) < tolerance
    }
}