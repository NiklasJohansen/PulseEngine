package no.njoh.pulseengine.core.shared.utils

import no.njoh.pulseengine.core.shared.primitives.Shape
import no.njoh.pulseengine.core.shared.utils.Extensions.component1
import no.njoh.pulseengine.core.shared.utils.Extensions.component2
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.*

object MathUtil
{
    private val reusableVector2f = Vector2f(0f, 0f)
    private val reusableVector3f = Vector3f(0f, 0f, 0f)

    /**
     * https://stackoverflow.com/questions/30559799/function-for-finding-the-distance-between-a-point-and-an-edge-in-java/38290399
     */
    fun pointToLineDistanceSquared(x: Float, y: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float
    {
        val xLineVec = x1 - x0
        val yLineVec = y0 - y1
        val dot = (x - x0) * yLineVec + (y - y0) * xLineVec
        return dot * dot / (yLineVec * yLineVec + xLineVec * xLineVec)
    }

    /**
     * https://stackoverflow.com/questions/30559799/function-for-finding-the-distance-between-a-point-and-an-edge-in-java/38290399
     */
    fun pointToLineSegmentDistanceSquared(x: Float, y: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float
    {
        val xLineVec = x1 - x0
        val yLineVec = y1 - y0
        val dot = (x - x0) * xLineVec + (y - y0) * yLineVec
        val lengthSquared = xLineVec * xLineVec + yLineVec * yLineVec

        var param = -1f
        if (lengthSquared != 0f) // in case of 0 length line
            param = dot / lengthSquared

        return when
        {
            param < 0 ->
            {
                val dx = x - x0
                val dy = y - y0
                dx * dx + dy * dy
            }
            param > 1 ->
            {
                val dx = x - x1
                val dy = y - y1
                dx * dx + dy * dy
            }
            else ->
            {
                val dx = x - (x0 + param * xLineVec)
                val dy = y - (y0 + param * yLineVec)
                dx * dx + dy * dy
            }
        }
    }

    /**
     * https://stackoverflow.com/questions/30559799/function-for-finding-the-distance-between-a-point-and-an-edge-in-java/38290399
     */
    fun closestPointOnLineSegment(x: Float, y: Float, x0: Float, y0: Float, x1: Float, y1: Float): Vector2f
    {
        val xLineVec = x1 - x0
        val yLineVec = y1 - y0
        val dot = (x - x0) * xLineVec + (y - y0) * yLineVec
        val lengthSquared = xLineVec * xLineVec + yLineVec * yLineVec

        var param = -1f
        if (lengthSquared != 0f) // in case of 0 length line
            param = dot / lengthSquared

        return when
        {
            param < 0f -> reusableVector2f.set(x0, y0)
            param > 1f -> reusableVector2f.set(x1, y1)
            else -> reusableVector2f.set(x0 + param * xLineVec, y0 + param * yLineVec)
        }
    }


    /**
     * https://stackoverflow.com/questions/563198/how-do-you-detect-where-two-line-segments-intersect
     */
    fun getLineSegmentIntersection(
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, x3: Float, y3: Float
    ): Vector2f? {
        val s1x = x1 - x0
        val s1y = y1 - y0
        val s2x = x3 - x2
        val s2y = y3 - y2
        val d = -s2x * s1y + s1x * s2y
        val s = (-s1y * (x0 - x2) + s1x * (y0 - y2)) / d
        val t = (s2x * (y0 - y2) - s2y * (x0 - x2)) / d
        return if (s < 0 || s > 1 || t < 0 || t > 1) null else reusableVector2f.set(x0 + t * s1x, y0 + t * s1y)
    }

    /**
     * https://stackoverflow.com/questions/1073336/circle-line-segment-collision-detection-algorithm
     */
    fun getLineSegmentCircleIntersection(x: Float, y: Float, radius: Float, x0: Float, y0: Float, x1: Float, y1: Float): Vector2f?
    {
        val xLineVec = x1 - x0
        val yLineVec = y1 - y0
        val lineLength = sqrt(xLineVec * xLineVec + yLineVec * yLineVec)
        val triangleArea2 = abs(xLineVec * (y - y0) - yLineVec * (x - x0)) // Triangle area times 2
        val triangleHeight = triangleArea2 / lineLength
        if (triangleHeight >= radius)
            return null

        // Compute the line direction
        val xLineDir = xLineVec / lineLength
        val yLineDir = yLineVec / lineLength

        // Distance along the line to the closest point of (x, y)
        val t = xLineDir * (x - x0) + yLineDir * (y - y0)

        // Compute intersection point distance from t
        val dt = sqrt(radius * radius - triangleHeight * triangleHeight)

        // Limit intersection point to line segment
        if (t - dt < 0 || t - dt > lineLength)
            return null

        // Compute first intersection point
        return reusableVector2f.set(x0 + xLineDir * (t - dt), y0 + yLineDir * (t - dt))
    }

    /**
     * Returns the first intersection point between the line and rectangle, as well as the squared distance
     * between the first line point and the intersection point. Returns null if noe intersection was found.
     */
    fun getLineRectIntersection(
        x0: Float, y0: Float, x1: Float, y1: Float,
        xRect: Float, yRect: Float, width: Float, height: Float, rot: Float
    ): Vector3f? {
        val c = cos(-rot)
        val s = sin(-rot)
        val w = width * 0.5f
        val h = height * 0.5f
        val xd0 = -w * c - h * s
        val yd1 = -w * s + h * c
        val xd2 = w * c - h * s
        val yd2 = w * s + h * c
        var xHit = 0f
        var yHit = 0f
        var minDist = Float.MAX_VALUE

        getLineSegmentIntersection(x0, y0, x1, y1, xRect + xd0, yRect + yd1, xRect + xd2, yRect + yd2)?.let()
        {
            val dist = (it.x - x0) * (it.x - x0) + (it.y - y0) * (it.y - y0)
            if (dist < minDist) { minDist = dist; xHit = it.x; yHit = it.y }
        }

        getLineSegmentIntersection(x0, y0, x1, y1, xRect + xd2, yRect + yd2, xRect - xd0, yRect - yd1)?.let()
        {
            val dist = (it.x - x0) * (it.x - x0) + (it.y - y0) * (it.y - y0)
            if (dist < minDist) { minDist = dist; xHit = it.x; yHit = it.y }
        }

        getLineSegmentIntersection(x0, y0, x1, y1, xRect - xd0, yRect - yd1, xRect - xd2, yRect - yd2)?.let()
        {
            val dist = (it.x - x0) * (it.x - x0) + (it.y - y0) * (it.y - y0)
            if (dist < minDist) { minDist = dist; xHit = it.x; yHit = it.y }
        }

        getLineSegmentIntersection(x0, y0, x1, y1, xRect - xd2, yRect - yd2, xRect + xd0, yRect + yd1)?.let()
        {
            val dist = (it.x - x0) * (it.x - x0) + (it.y - y0) * (it.y - y0)
            if (dist < minDist) { minDist = dist; xHit = it.x; yHit = it.y }
        }

        return if (minDist != Float.MAX_VALUE) reusableVector3f.set(xHit, yHit, minDist) else null
    }

    /**
     * Returns the first intersection point between the line and the [Shape], as well as the squared distance
     * between the first line point and the intersection point. Returns null if noe intersection was found.
     */
    fun getLineShapeIntersection(x0: Float, y0: Float, x1: Float, y1: Float, shape: Shape): Vector3f?
    {
        val nPoints = shape.getPointCount()
        if (nPoints == 1) // Point/circle shape
        {
            val radius = shape.getRadius() ?: 0f
            val center = shape.getPoint(0)
            val p = getLineSegmentCircleIntersection(center.x, center.y, radius, x0, y0, x1, y1)
            if (p != null) return reusableVector3f.set(p.x, p.y, (p.x - x0) * (p.x - x0) + (p.y - y0) * (p.y - y0))
        }
        else if (nPoints == 2) // Line shape
        {
            val p0 = shape.getPoint(0)
            val p0x = p0.x
            val p0y = p0.y
            val p1 = shape.getPoint(1)
            val p = getLineSegmentIntersection(x0, y0, x1, y1, p0x, p0y, p1.x, p1.y)
            if (p != null) return reusableVector3f.set(p.x, p.y, (p.x - x0) * (p.x - x0) + (p.y - y0) * (p.y - y0))
        }
        else // Polygon shape
        {
            val lastPoint = shape.getPoint(nPoints - 1)
            var xLast = lastPoint.x
            var yLast = lastPoint.y
            var xClosest = 0f
            var yClosest = 0f
            var minDist = Float.MAX_VALUE
            for (i in 0 until nPoints)
            {
                val point = shape.getPoint(i)
                val p = getLineSegmentIntersection(x0, y0, x1, y1, xLast, yLast, point.x, point.y)
                if (p != null)
                {
                    val squaredDist = (p.x - x0) * (p.x - x0) + (p.y - y0) * (p.y - y0)
                    if (squaredDist < minDist)
                    {
                        minDist = squaredDist
                        xClosest = p.x
                        yClosest = p.y
                    }
                }
                xLast = point.x
                yLast = point.y
            }
            if (minDist != Float.MAX_VALUE) return reusableVector3f.set(xClosest, yClosest, minDist)
        }

        return null // No intersection
    }

    /**
     * Returns true if the (x, y) point is inside the [Shape].
     * https://wrfranklin.org/Research/Short_Notes/pnpoly.html
     */
    fun isPointInsideShape(x: Float, y: Float, shape: Shape): Boolean
    {
        val nPoints = shape.getPointCount()
        if (nPoints == 1) // Point/circle shape
        {
            val radius = shape.getRadius() ?: 0f
            val center = shape.getPoint(0)
            val xd = center.x - x
            val yd = center.y - y
            return xd * xd + yd * yd <= radius * radius
        }
        else if (nPoints == 2) // Line shape
        {
            return false // A line cannot contain a point
        }
        else // Polygon shape
        {
            var i = 0
            var j = nPoints - 1
            var inside = false
            while (i < nPoints)
            {
                val (xi, yi) = shape.getPoint(i)
                val (xj, yj) = shape.getPoint(j)
                if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi))
                    inside = !inside
                j = i++
            }
            return inside
        }
    }

    /**
     * Returns true if the (x, y) point is inside the rotated rectangle.
     */
    fun isPointInsideRect(x: Float, y: Float, xRect: Float, yRect: Float, width: Float, height: Float, angleRad: Float): Boolean
    {
        val xTranslated = x - xRect
        val yTranslated = y - yRect
        val cosTheta = cos(-angleRad)
        val sinTheta = sin(-angleRad)
        val xRotated = xTranslated * cosTheta + yTranslated * sinTheta
        val yRotated = -xTranslated * sinTheta + yTranslated * cosTheta
        return xRotated >= -width * 0.5f && xRotated <= width * 0.5f && yRotated >= -height * 0.5f && yRotated <= height * 0.5f
    }

    /**
     * https://math.stackexchange.com/questions/1098487/atan2-faster-approximation/1105038#answer-1105038
     */
    fun atan2(y: Float, x: Float): Float
    {
        val ax = if (x >= 0.0) x else - x
        val ay = if (y >= 0.0) y else - y
        val a = (if (ax < ay) ax else ay) / (if (ax > ay) ax else ay)
        val s = a * a
        var r = ((-0.0464964749f * s + 0.15931422f) * s - 0.327622764f) * s * a + a
        if (ay > ax) r = 1.57079637f - r
        if (x < 0.0) r = 3.14159274f - r
        return if (y >= 0) r else - r
    }
}