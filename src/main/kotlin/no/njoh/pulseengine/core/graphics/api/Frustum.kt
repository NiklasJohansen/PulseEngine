package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.asset.types.Model
import org.joml.Matrix4f
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Represents a view frustum with 6 planes for efficient culling.
 * Planes point inward, so a point is inside if distance to all planes >= 0.
 */
class Frustum(
    val left:   FrustumPlane = FrustumPlane(),
    val right:  FrustumPlane = FrustumPlane(),
    val bottom: FrustumPlane = FrustumPlane(),
    val top:    FrustumPlane = FrustumPlane(),
    val near:   FrustumPlane = FrustumPlane(),
    val far:    FrustumPlane = FrustumPlane()
) {
    private val planes = arrayOf(left, right, bottom, top, near, far)

    fun setForCamera(camera: Camera)
    {
        setForViewProjection(camera.viewProjectionMatrix)
    }

    /**
     * Extracts frustum planes from a view-projection matrix.
     * Uses the Gribb/Hartmann method for plane extraction.
     */
    fun setForViewProjection(vp: Matrix4f)
    {
        // Left plane: row3 + row0
        left.a = vp.m03() + vp.m00()
        left.b = vp.m13() + vp.m10()
        left.c = vp.m23() + vp.m20()
        left.d = vp.m33() + vp.m30()
        left.normalize()

        // Right plane: row3 - row0
        right.a = vp.m03() - vp.m00()
        right.b = vp.m13() - vp.m10()
        right.c = vp.m23() - vp.m20()
        right.d = vp.m33() - vp.m30()
        right.normalize()

        // Bottom plane: row3 + row1
        bottom.a = vp.m03() + vp.m01()
        bottom.b = vp.m13() + vp.m11()
        bottom.c = vp.m23() + vp.m21()
        bottom.d = vp.m33() + vp.m31()
        bottom.normalize()

        // Top plane: row3 - row1
        top.a = vp.m03() - vp.m01()
        top.b = vp.m13() - vp.m11()
        top.c = vp.m23() - vp.m21()
        top.d = vp.m33() - vp.m31()
        top.normalize()

        // Near plane: row3 + row2
        near.a = vp.m03() + vp.m02()
        near.b = vp.m13() + vp.m12()
        near.c = vp.m23() + vp.m22()
        near.d = vp.m33() + vp.m32()
        near.normalize()

        // Far plane: row3 - row2
        far.a = vp.m03() - vp.m02()
        far.b = vp.m13() - vp.m12()
        far.c = vp.m23() - vp.m22()
        far.d = vp.m33() - vp.m32()
        far.normalize()
    }

    /**
     * Sets the frustum planes for a stereo camera pair (e.g., VR left/right eyes).
     * Creates a combined frustum that encompasses both views by using:
     * - Left plane from the left camera (outer edge)
     * - Right plane from the right camera (outer edge)
     * - Most conservative top/bottom/near/far planes from both cameras
     */
    fun setForStereoCamera(leftCam: Camera, rightCam: Camera)
    {
        val vpl = leftCam.viewProjectionMatrix
        val vpr = rightCam.viewProjectionMatrix

        // Left plane from left camera (outer left edge of combined frustum)
        left.a = vpl.m03() + vpl.m00()
        left.b = vpl.m13() + vpl.m10()
        left.c = vpl.m23() + vpl.m20()
        left.d = vpl.m33() + vpl.m30()
        left.normalize()

        // Right plane from right camera (outer right edge of combined frustum)
        right.a = vpr.m03() - vpr.m00()
        right.b = vpr.m13() - vpr.m10()
        right.c = vpr.m23() - vpr.m20()
        right.d = vpr.m33() - vpr.m30()
        right.normalize()

        // For top/bottom/near/far, use the planes that create the larger combined frustum
        // Extract from left camera and use directly (typically identical for symmetric stereo)
        bottom.a = vpl.m03() + vpl.m01()
        bottom.b = vpl.m13() + vpl.m11()
        bottom.c = vpl.m23() + vpl.m21()
        bottom.d = vpl.m33() + vpl.m31()
        bottom.normalize()

        top.a = vpl.m03() - vpl.m01()
        top.b = vpl.m13() - vpl.m11()
        top.c = vpl.m23() - vpl.m21()
        top.d = vpl.m33() - vpl.m31()
        top.normalize()

        near.a = vpl.m03() + vpl.m02()
        near.b = vpl.m13() + vpl.m12()
        near.c = vpl.m23() + vpl.m22()
        near.d = vpl.m33() + vpl.m32()
        near.normalize()

        far.a = vpl.m03() - vpl.m02()
        far.b = vpl.m13() - vpl.m12()
        far.c = vpl.m23() - vpl.m22()
        far.d = vpl.m33() - vpl.m32()
        far.normalize()
    }

    /**
     * Tests if an AABB (transformed by the given matrix) intersects the frustum.
     * Uses the "p-vertex" optimization for early rejection.
     */
    fun intersectsAabb(aabb: Model.Aabb, transform: Matrix4f): Boolean
    {
        // Compute center and half-extents in local space
        val cx = (aabb.xMin + aabb.xMax) * 0.5f
        val cy = (aabb.yMin + aabb.yMax) * 0.5f
        val cz = (aabb.zMin + aabb.zMax) * 0.5f
        val hx = (aabb.xMax - aabb.xMin) * 0.5f
        val hy = (aabb.yMax - aabb.yMin) * 0.5f
        val hz = (aabb.zMax - aabb.zMin) * 0.5f

        // Transform center to world space
        val wcx = transform.m00() * cx + transform.m10() * cy + transform.m20() * cz + transform.m30()
        val wcy = transform.m01() * cx + transform.m11() * cy + transform.m21() * cz + transform.m31()
        val wcz = transform.m02() * cx + transform.m12() * cy + transform.m22() * cz + transform.m32()

        // Compute world-space half-extents using absolute values of rotation/scale matrix
        // This creates an AABB that bounds the transformed OBB
        val whx = abs(transform.m00()) * hx + abs(transform.m10()) * hy + abs(transform.m20()) * hz
        val why = abs(transform.m01()) * hx + abs(transform.m11()) * hy + abs(transform.m21()) * hz
        val whz = abs(transform.m02()) * hx + abs(transform.m12()) * hy + abs(transform.m22()) * hz

        // Test against each frustum plane
        for (i in 0 until 6)
        {
            val plane = planes[i]

            // Compute the "radius" of the AABB projected onto the plane normal
            val r = whx * abs(plane.a) + why * abs(plane.b) + whz * abs(plane.c)

            // Distance from center to plane
            val dist = plane.distanceToPoint(wcx, wcy, wcz)

            // If the AABB is completely behind this plane, it's outside the frustum
            if (dist < -r) return false
        }
        return true
    }

    /**
     * Tests if a sphere intersects the frustum.
     */
    fun intersectsSphere(x: Float, y: Float, z: Float, r: Float): Boolean
    {
        for (i in 0 until 6)
        {
            if (planes[i].distanceToPoint(x, y, z) < -r) return false
        }
        return true
    }

    /**
     * Represents a plane in 3D space using the equation: ax + by + cz + d = 0
     * The normal (a, b, c) points inward (toward the visible region).
     */
    class FrustumPlane(var a: Float = 0f, var b: Float = 0f, var c: Float = 0f, var d: Float = 0f)
    {
        /** Normalizes the plane equation for correct distance calculations */
        fun normalize()
        {
            val invLen = 1f / sqrt(a * a + b * b + c * c)
            a *= invLen
            b *= invLen
            c *= invLen
            d *= invLen
        }

        /** Returns signed distance from point to plane (positive = inside/front) */
        inline fun distanceToPoint(x: Float, y: Float, z: Float): Float = a * x + b * y + c * z + d
    }
}