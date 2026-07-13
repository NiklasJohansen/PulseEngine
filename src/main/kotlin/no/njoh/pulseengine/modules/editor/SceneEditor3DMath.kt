package no.njoh.pulseengine.modules.editor

import no.njoh.pulseengine.core.graphics.camera.Camera
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tan

object SceneEditor3DMath
{
    private const val EPSILON = 1e-6f

    data class Ray(val origin: Vector3f = Vector3f(), val direction: Vector3f = Vector3f())

    fun createRay(camera: Camera, x: Float, y: Float, width: Int, height: Int, out: Ray): Boolean
    {
        if (width <= 0 || height <= 0) 
            return false
        val xNdc = 2f * x / width - 1f
        val yNdc = 1f - 2f * y / height
        val near = Vector4f(xNdc, yNdc, -1f, 1f).mul(camera.invViewProjectionMatrix)
        val far = Vector4f(xNdc, yNdc, 1f, 1f).mul(camera.invViewProjectionMatrix)
        if (abs(near.w) < EPSILON || abs(far.w) < EPSILON) 
            return false

        near.div(near.w)
        far.div(far.w)
        out.origin.set(near.x, near.y, near.z)
        out.direction.set(far.x - near.x, far.y - near.y, far.z - near.z)
        if (out.direction.lengthSquared() < EPSILON) 
            return false
        out.direction.normalize()
        return true
    }

    fun project(camera: Camera, world: Vector3f, width: Int, height: Int, out: Vector2f): Boolean
    {
        val clip = Vector4f(world, 1f).mul(camera.viewProjectionMatrix)
        if (clip.w <= EPSILON) 
            return false
        val invW = 1f / clip.w
        val xNdc = clip.x * invW
        val yNdc = clip.y * invW
        out.set((xNdc * 0.5f + 0.5f) * width, (1f - (yNdc * 0.5f + 0.5f)) * height)
        return xNdc in -2f..2f && yNdc in -2f..2f
    }

    /** 
     * Projects a world-space line after clipping it against the camera view volume. 
     */
    fun projectLine(
        camera: Camera,
        start: Vector3f,
        end: Vector3f,
        width: Int,
        height: Int,
        outStart: Vector2f,
        outEnd: Vector2f,
        clipDepth: Boolean = true
    ): Boolean {
        if (width <= 0 || height <= 0)
            return false

        val a = Vector4f(start, 1f).mul(camera.viewProjectionMatrix)
        val b = Vector4f(end, 1f).mul(camera.viewProjectionMatrix)
        var tMin = 0f
        var tMax = 1f

        fun clip(startDistance: Float, endDistance: Float): Boolean
        {
            if (startDistance < 0f && endDistance < 0f)
                return false
            if (startDistance >= 0f && endDistance >= 0f)
                return true

            val t = startDistance / (startDistance - endDistance)
            if (startDistance < 0f)
                tMin = max(tMin, t)
            else
                tMax = kotlin.math.min(tMax, t)
            return tMin <= tMax
        }

        if (!clip(a.x + a.w, b.x + b.w) || !clip(a.w - a.x, b.w - b.x) ||
            !clip(a.y + a.w, b.y + b.w) || !clip(a.w - a.y, b.w - b.y))
            return false

        if (clipDepth && (!clip(a.z + a.w, b.z + b.w) || !clip(a.w - a.z, b.w - b.z)))
            return false

        if (!clip(a.w - EPSILON, b.w - EPSILON))
            return false

        val clippedStart = Vector4f(a).lerp(b, tMin)
        val clippedEnd = Vector4f(a).lerp(b, tMax)
        if (abs(clippedStart.w) < EPSILON || abs(clippedEnd.w) < EPSILON)
            return false

        val startX = clippedStart.x / clippedStart.w
        val startY = clippedStart.y / clippedStart.w
        val endX = clippedEnd.x / clippedEnd.w
        val endY = clippedEnd.y / clippedEnd.w
        outStart.set((startX * 0.5f + 0.5f) * width, (1f - (startY * 0.5f + 0.5f)) * height)
        outEnd.set((endX * 0.5f + 0.5f) * width, (1f - (endY * 0.5f + 0.5f)) * height)
        return true
    }

    fun gizmoWorldSize(camera: Camera, pivot: Vector3f, viewportHeight: Int, pixelSize: Float = 90f): Float
    {
        val cameraPosition = camera.invViewMatrix.getTranslation(Vector3f())
        val distance = max(cameraPosition.distance(pivot), 0.01f)
        val worldHeight = 2f * distance * tan(Math.toRadians((camera.fov * 0.5f).toDouble())).toFloat()
        return max(worldHeight * pixelSize / max(viewportHeight, 1), 0.001f)
    }

    fun closestAxisParameter(ray: Ray, axisOrigin: Vector3f, axisDirection: Vector3f): Float?
    {
        val axis = Vector3f(axisDirection).normalize()
        val w0 = Vector3f(axisOrigin).sub(ray.origin)
        val b = axis.dot(ray.direction)
        val d = axis.dot(w0)
        val e = ray.direction.dot(w0)
        val denominator = 1f - b * b
        if (abs(denominator) < 1e-4f) 
            return null
        return (b * e - d) / denominator
    }

    fun intersectPlane(ray: Ray, point: Vector3f, normal: Vector3f, out: Vector3f): Boolean
    {
        val denominator = normal.dot(ray.direction)
        if (abs(denominator) < EPSILON) 
            return false
        val t = Vector3f(point).sub(ray.origin).dot(normal) / denominator
        if (!t.isFinite()) 
            return false
        out.set(ray.direction).mul(t).add(ray.origin)
        return true
    }

    fun signedAngleDegrees(from: Vector3f, to: Vector3f, axis: Vector3f): Float
    {
        val cross = Vector3f(from).cross(to)
        val sine = axis.dot(cross)
        val cosine = from.dot(to)
        return Math.toDegrees(atan2(sine.toDouble(), cosine.toDouble())).toFloat()
    }

    fun pointToSegmentDistance(point: Vector2f, a: Vector2f, b: Vector2f): Float
    {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 <= EPSILON) 
            return point.distance(a)
        val t = (((point.x - a.x) * dx + (point.y - a.y) * dy) / len2).coerceIn(0f, 1f)
        val x = a.x + dx * t
        val y = a.y + dy * t
        val xd = point.x - x
        val yd = point.y - y
        return sqrt(xd * xd + yd * yd)
    }

    fun pointInTriangle(point: Vector2f, a: Vector2f, b: Vector2f, c: Vector2f): Boolean
    {
        val area = cross(a, b, c)
        if (abs(area) < EPSILON) 
            return false

        val ab = cross(a, b, point)
        val bc = cross(b, c, point)
        val ca = cross(c, a, point)
        val hasNegative = ab < 0f || bc < 0f || ca < 0f
        val hasPositive = ab > 0f || bc > 0f || ca > 0f
        return !(hasNegative && hasPositive)
    }

    fun pointInQuad(point: Vector2f, a: Vector2f, b: Vector2f, c: Vector2f, d: Vector2f) =
        pointInTriangle(point, a, b, c) || pointInTriangle(point, a, c, d)

    fun distanceToTriangleEdges(point: Vector2f, a: Vector2f, b: Vector2f, c: Vector2f) = minOf(
        pointToSegmentDistance(point, a, b),
        pointToSegmentDistance(point, b, c),
        pointToSegmentDistance(point, c, a)
    )

    fun distanceToQuadEdges(point: Vector2f, a: Vector2f, b: Vector2f, c: Vector2f, d: Vector2f) = minOf(
        pointToSegmentDistance(point, a, b),
        pointToSegmentDistance(point, b, c),
        pointToSegmentDistance(point, c, d),
        pointToSegmentDistance(point, d, a)
    )

    fun triangleArea(a: Vector2f, b: Vector2f, c: Vector2f) = abs(cross(a, b, c)) * 0.5f

    fun triangleWinding(a: Vector2f, b: Vector2f, c: Vector2f) = cross(a, b, c)

    fun clampScale(value: Float): Float
    {
        if (!value.isFinite()) 
            return 1f
        if (abs(value) >= 0.001f) 
            return value
        return if (value < 0f) -0.001f else 0.001f
    }

    fun rotateAroundPivot(position: Vector3f, pivot: Vector3f, axis: Vector3f, angleDegrees: Float, out: Vector3f): Vector3f
    {
        out.set(position).sub(pivot)
        Quaternionf().fromAxisAngleRad(axis, Math.toRadians(angleDegrees.toDouble()).toFloat()).transform(out)
        return out.add(pivot)
    }

    fun scaleAroundPivot(position: Vector3f, pivot: Vector3f, factors: Vector3f, out: Vector3f): Vector3f =
        out.set(position).sub(pivot).mul(factors).add(pivot)

    fun cameraFacingAxisSigns(cameraPosition: Vector3f, pivot: Vector3f, out: Vector3f): Vector3f = out.set(
        if (cameraPosition.x < pivot.x) -1f else 1f,
        if (cameraPosition.y < pivot.y) -1f else 1f,
        if (cameraPosition.z < pivot.z) -1f else 1f
    )

    fun scrollTranslation(forward: Vector3f, scrollSteps: Float, unitsPerStep: Float, out: Vector3f): Vector3f
    {
        out.set(forward)
        if (out.lengthSquared() < EPSILON) 
            return out.zero()
        return out.normalize(scrollSteps * unitsPerStep)
    }

    private fun cross(a: Vector2f, b: Vector2f, c: Vector2f) = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
}