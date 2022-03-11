package no.njoh.pulseengine.core.shared.utils

import org.joml.Vector2f
import kotlin.math.abs
import kotlin.math.sqrt

object MathUtil
{
    private val reusableVector = Vector2f(0f, 0f)

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
            param < 0f -> reusableVector.set(x0, y0)
            param > 1f -> reusableVector.set(x1, y1)
            else -> reusableVector.set(x0 + param * xLineVec, y0 + param * yLineVec)
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

        val s = (-s1y * (x0 - x2) + s1x * (y0 - y2)) / (-s2x * s1y + s1x * s2y)
        if (s < 0 || s > 1)
            return null

        val t = (s2x * (y0 - y2) - s2y * (x0 - x2)) / (-s2x * s1y + s1x * s2y)
        if (t < 0 || t > 1)
            return null

        return reusableVector.set(x0 + t * s1x, y0 + t * s1y)
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

        if (triangleHeight < radius)
        {
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
            reusableVector.x = x0 + xLineDir * (t - dt)
            reusableVector.y = y0 + yLineDir * (t - dt)
            return reusableVector
        }

        return null
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