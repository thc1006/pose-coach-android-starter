package com.posecoach.coregeom

import kotlin.math.*

/**
 * Utility functions for angle calculations in pose estimation.
 * Provides robust geometric operations for 33-point pose landmarks.
 */
object AngleUtils {

    /**
     * Returns angle ABC in degrees, where B is the vertex.
     * Handles edge cases: collinear vectors, perpendicular vectors, and degenerate cases.
     */
    fun angleDeg(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Double {
        // Create vectors BA and BC using shared utilities
        val v1 = VectorUtils.vectorFromPoints(bx, by, ax, ay)  // B to A
        val v2 = VectorUtils.vectorFromPoints(bx, by, cx, cy)  // B to C

        // Guard against degenerate vectors
        if (v1.isZero(1e-6) || v2.isZero(1e-6)) return Double.NaN

        // Handle special geometric cases for exact results
        return when {
            VectorUtils.areCollinear(v1, v2) -> {
                // Check if vectors point in same or opposite directions
                val dot = v1.dot(v2)
                if (dot > 0) 0.0 else 180.0
            }
            VectorUtils.arePerpendicular(v1, v2) -> {
                90.0  // Exact perpendicular case
            }
            else -> {
                // General case: calculate angle using dot product
                val angleRad = v1.angleWith(v2)
                if (angleRad.isNaN()) Double.NaN else Math.toDegrees(angleRad)
            }
        }
    }

    /**
     * Calculate angle in radians for use in internal calculations
     */
    fun angleRad(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Double {
        return VectorUtils.angleBetweenVectors(ax, ay, bx, by, cx, cy)
    }

    /**
     * Check if three points form a right angle (90 degrees) within tolerance
     */
    fun isRightAngle(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double,
                    toleranceDeg: Double = 1.0): Boolean {
        val angle = angleDeg(ax, ay, bx, by, cx, cy)
        return !angle.isNaN() && abs(angle - 90.0) < toleranceDeg
    }

    /**
     * Check if three points are collinear within tolerance
     */
    fun areCollinear(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double,
                    toleranceDeg: Double = 1.0): Boolean {
        val angle = angleDeg(ax, ay, bx, by, cx, cy)
        return !angle.isNaN() && (abs(angle) < toleranceDeg || abs(angle - 180.0) < toleranceDeg)
    }
}
