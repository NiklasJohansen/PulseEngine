package no.njoh.pulseengine.util

import org.joml.Vector2f

object MathUtil
{
    private val reusableVector = Vector2f(0f, 0f)

    /**
     * https://stackoverflow.com/questions/30559799/function-for-finding-the-distance-between-a-point-and-an-edge-in-java/38290399
     */
    fun pointToLineDistance(x: Float, y: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float
    {
        val c = x1 - x0
        val e = y0 - y1
        val dot = (x - x0) * e + (y - y0) * c
        return dot * dot / (e * e + c * c)
    }

    /**
     * https://stackoverflow.com/questions/30559799/function-for-finding-the-distance-between-a-point-and-an-edge-in-java/38290399
     */
    fun pointToLineSegmentDistanceSquared(x: Float, y: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float
    {
        val c = x1 - x0
        val d = y1 - y0
        val dot = (x - x0) * c + (y - y0) * d
        val lengthSquared = c * c + d * d

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
                val dx = x - (x0 + param * c)
                val dy = y - (y0 + param * d)
                dx * dx + dy * dy
            }
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
        val t = (s2x * (y0 - y2) - s2y * (x0 - x2)) / (-s2x * s1y + s1x * s2y)

        return if (s >= 0 && s <= 1 && t >= 0 && t <= 1)
            reusableVector.set(x0 + t * s1x, y0 + t * s1y)
        else null
    }

    /**
     * https://math.stackexchange.com/questions/1098487/atan2-faster-approximation/1105038#answer-1105038
     */
    fun atan2(y: Float, x: Float): Float
    {
        val ax = if (x >= 0.0) x else -x
        val ay = if (y >= 0.0) y else -y
        val a = (if (ax < ay) ax else ay) / (if (ax > ay) ax else ay)
        val s = a * a
        var r = ((-0.0464964749f * s + 0.15931422f) * s - 0.327622764f) * s * a + a
        if (ay > ax) r = 1.57079637f - r
        if (x < 0.0) r = 3.14159274f - r
        return if (y >= 0) r else -r
    }
}