package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.asset.types.Model
import org.joml.Matrix4f
import org.joml.Vector3f
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
    val planeSet = FrustumPlaneSet.ofPlanes(arrayOf(left, right, bottom, top, near, far))

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
     * Combines the horizontal outer planes from both views and uses the left camera for
     * the remaining planes, which are expected to match in symmetric stereo projections:
     * - Left plane from the left camera (outer edge)
     * - Right plane from the right camera (outer edge)
     * - Top/bottom/near/far planes from the left camera (typically identical for symmetric stereo)
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

        // For top/bottom/near/far, use the left camera planes directly.
        // They are typically identical for symmetric stereo projections.
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
     * Tests if a sphere intersects the frustum.
     */
    fun intersectsSphere(x: Float, y: Float, z: Float, r: Float): Boolean
    {
        for (i in 0 until 6)
        {
            if (planeSet[i].distanceToPoint(x, y, z) < -r) return false
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

    /**
     * Reusable collection of inward-facing culling planes.
     *
     * A normal [Frustum] always has six planes, but some GPU culling paths need richer convex
     * volumes. Cascaded shadow maps use this to combine the shadow-map box with extra receiver
     * caster planes, so objects outside the conservative caster volume can be rejected.
     */
    class FrustumPlaneSet private constructor(private val planes: Array<FrustumPlane>)
    {
        var size = 0; private set
 
        fun clear() { size = 0 }

        /**
         * Adds a side culling plane from a receiver silhouette edge.
         *
         * [plane0] and [plane1] are expected to be adjacent receiver-frustum planes where exactly
         * one is back-facing to the light. Their intersection is then part of the receiver
         * silhouette. Extruding that edge along [lightDirection] adds a plane that rejects casters
         * whose shadows pass beside the receiver slice.
         */
        fun addLightExtrusionPlane(plane0: FrustumPlane, plane1: FrustumPlane, insidePoint: Vector3f, lightDirection: Vector3f)
        {
            // Computes the start point and the direction for the line where the two planes intersect
            // Direction is the cross-product of the plane normals
            val xDir = plane0.b * plane1.c - plane0.c * plane1.b
            val yDir = plane0.c * plane1.a - plane0.a * plane1.c
            val zDir = plane0.a * plane1.b - plane0.b * plane1.a

            val denominator = xDir * xDir + yDir * yDir + zDir * zDir
            if (denominator <= 0.000001f)
                return // Parallel or nearly parallel planes do not produce a stable line

            val cx = plane1.d * plane0.a - plane0.d * plane1.a
            val cy = plane1.d * plane0.b - plane0.d * plane1.b
            val cz = plane1.d * plane0.c - plane0.d * plane1.c

            // The line start point is the closest point on the intersection line to the origin
            val xLineStart = (cy * zDir - cz * yDir) / denominator
            val yLineStart = (cz * xDir - cx * zDir) / denominator
            val zLineStart = (cx * yDir - cy * xDir) / denominator

            val xLineEnd = xLineStart + xDir
            val yLineEnd = yLineStart + yDir
            val zLineEnd = zLineStart + zDir

            // Create a plane passing through the edge (lineStart - lineEnd) and extending along lightDirection
            val xEdge = xLineEnd - xLineStart
            val yEdge = yLineEnd - yLineStart
            val zEdge = zLineEnd - zLineStart
            var a = yEdge * lightDirection.z - zEdge * lightDirection.y
            var b = zEdge * lightDirection.x - xEdge * lightDirection.z
            var c = xEdge * lightDirection.y - yEdge * lightDirection.x

            val length = sqrt(a * a + b * b + c * c)
            if (length <= 0.00001f)
                return // The edge and light direction do not span a stable plane

            val invLength = 1f / length
            a *= invLength
            b *= invLength
            c *= invLength
            var d = -(a * xLineStart + b * yLineStart + c * zLineStart)

            // Orient the plane so insidePoint is on the positive side
            if (a * insidePoint.x + b * insidePoint.y + c * insidePoint.z + d < 0f)
            {
                a = -a
                b = -b
                c = -c
                d = -d
            }

            add(a, b, c, d)
        }

        fun addPlaneFacingPoint(plane: FrustumPlane, point: Vector3f)
        {
            // If the point is on the negative side, flip the plane before adding it.
            if (plane.distanceToPoint(point.x, point.y, point.z) >= 0f)
                add(plane)
            else
                add(-plane.a, -plane.b, -plane.c, -plane.d)
        }

        fun add(plane: FrustumPlane)
        {
            add(plane.a, plane.b, plane.c, plane.d)
        }

        fun add(a: Float, b: Float, c: Float, d: Float)
        {
            require(size < planes.size) { "Culling plane set capacity exceeded: ${planes.size}" }
            planes[size].a = a
            planes[size].b = b
            planes[size].c = c
            planes[size].d = d
            size++
        }

        operator fun get(index: Int) = planes[index]

        inline fun forEach(action: (FrustumPlane) -> Unit)
        {
            for (i in 0 until size) action(this[i])
        }

        /**
         * Tests if an AABB (transformed by the given matrix) intersects the frustum plane sets.
         * Uses a center/half-extent plane test for early rejection.
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
            for (i in 0 until size)
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

        companion object
        {
            fun ofCapacity(capacity: Int) = FrustumPlaneSet(Array(capacity) { FrustumPlane() })

            fun ofPlanes(planes: Array<FrustumPlane>) = FrustumPlaneSet(planes).apply { size = planes.size }
        }
    }
}