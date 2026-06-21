package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.shared.primitives.Mat4f
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
    fun setForViewProjection(vp: Matrix4f): Frustum
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
        
        return this
    }

    /**
     * Builds a single conservative frustum for two horizontally adjacent camera views.
     * The left and right projections provide the two outer horizontal planes.
     */
    fun setForSideBySideViewProjections(leftViewProjection: Matrix4f, rightViewProjection: Matrix4f): Frustum
    {
        val leftView = leftViewProjection
        val rightView = rightViewProjection

        left.a = leftView.m03() + leftView.m00()
        left.b = leftView.m13() + leftView.m10()
        left.c = leftView.m23() + leftView.m20()
        left.d = leftView.m33() + leftView.m30()
        left.normalize()

        right.a = rightView.m03() - rightView.m00()
        right.b = rightView.m13() - rightView.m10()
        right.c = rightView.m23() - rightView.m20()
        right.d = rightView.m33() - rightView.m30()
        right.normalize()

        bottom.a = leftView.m03() + leftView.m01()
        bottom.b = leftView.m13() + leftView.m11()
        bottom.c = leftView.m23() + leftView.m21()
        bottom.d = leftView.m33() + leftView.m31()
        bottom.normalize()

        top.a = leftView.m03() - leftView.m01()
        top.b = leftView.m13() - leftView.m11()
        top.c = leftView.m23() - leftView.m21()
        top.d = leftView.m33() - leftView.m31()
        top.normalize()

        near.a = leftView.m03() + leftView.m02()
        near.b = leftView.m13() + leftView.m12()
        near.c = leftView.m23() + leftView.m22()
        near.d = leftView.m33() + leftView.m32()
        near.normalize()

        far.a = leftView.m03() - leftView.m02()
        far.b = leftView.m13() - leftView.m12()
        far.c = leftView.m23() - leftView.m22()
        far.d = leftView.m33() - leftView.m32()
        far.normalize()
        
        return this
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
        fun intersectsAabb(aabb: Model.Aabb, transform: Mat4f): Boolean
        {
            val matrix = transform.data
            val offset = transform.offset
            val m00 = matrix[offset     ]; val m01 = matrix[offset +  1]; val m02 = matrix[offset +  2]
            val m10 = matrix[offset +  4]; val m11 = matrix[offset +  5]; val m12 = matrix[offset +  6]
            val m20 = matrix[offset +  8]; val m21 = matrix[offset +  9]; val m22 = matrix[offset + 10]
            val m30 = matrix[offset + 12]; val m31 = matrix[offset + 13]; val m32 = matrix[offset + 14]

            // Compute center and half-extents in local space
            val cx = (aabb.xMin + aabb.xMax) * 0.5f
            val cy = (aabb.yMin + aabb.yMax) * 0.5f
            val cz = (aabb.zMin + aabb.zMax) * 0.5f
            val hx = (aabb.xMax - aabb.xMin) * 0.5f
            val hy = (aabb.yMax - aabb.yMin) * 0.5f
            val hz = (aabb.zMax - aabb.zMin) * 0.5f

            // Transform center to world space
            val wcx = m00 * cx + m10 * cy + m20 * cz + m30
            val wcy = m01 * cx + m11 * cy + m21 * cz + m31
            val wcz = m02 * cx + m12 * cy + m22 * cz + m32

            // Compute world-space half-extents using absolute values of rotation/scale matrix
            // This creates an AABB that bounds the transformed OBB
            val whx = abs(m00) * hx + abs(m10) * hy + abs(m20) * hz
            val why = abs(m01) * hx + abs(m11) * hy + abs(m21) * hz
            val whz = abs(m02) * hx + abs(m12) * hy + abs(m22) * hz

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