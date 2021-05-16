package no.njoh.pulseengine.modules.scene.systems.physics.shapes

import no.njoh.pulseengine.util.MathUtil.atan2
import org.joml.Vector2f
import kotlin.math.PI
import kotlin.math.sqrt

abstract class PolygonShape
{
    abstract val points: FloatArray // x, y, xLast, yLast, xAcc, yAcc
    abstract val constraints: FloatArray // index0, index1, length, stiffness
    abstract val nBoundaryPoints: Int // The number of points making up the outer edges of the shape
    abstract val nStickConstraints: Int

    // Position and rotation
    var xCenter = 0f
    var yCenter = 0f
    var xCenterLast = 0f
    var yCenterLast = 0f
    var angle = 0f
    var angleOffset = 0f

    // Bounding box
    var xMin = 0f
    var xMax = 0f
    var yMin = 0f
    var yMax = 0f

    // Sleep state
    var isSleeping = false
    var stepsAtRest = 0

    // Mass of the whole shape
    var mass = 1f

    abstract fun build(x: Float, y: Float, width: Float, height: Float, rot: Float, density: Float)

    fun setPoint(i: Int, x: Float, y: Float)
    {
        points[N_POINT_FIELDS * i + X] = x
        points[N_POINT_FIELDS * i + Y] = y
        points[N_POINT_FIELDS * i + X_LAST] = x
        points[N_POINT_FIELDS * i + Y_LAST] = y
    }

    fun setStickConstraint(i: Int, point0: Int, point1: Int, stiffness: Float)
    {
        val xDelta = points[N_POINT_FIELDS * point1 + X] - points[N_POINT_FIELDS * point0 + X]
        val yDelta = points[N_POINT_FIELDS * point1 + Y] - points[N_POINT_FIELDS * point0 + Y]

        constraints[N_STICK_CONSTRAINT_FIELDS * i + POINT_0] = point0.toFloat()
        constraints[N_STICK_CONSTRAINT_FIELDS * i + POINT_1] = point1.toFloat()
        constraints[N_STICK_CONSTRAINT_FIELDS * i + LENGTH] = sqrt(xDelta * xDelta + yDelta * yDelta)
        constraints[N_STICK_CONSTRAINT_FIELDS * i + STIFFNESS] = stiffness
    }

    open fun recalculateBoundingBox()
    {
        var xSum = points[X]
        var ySum = points[Y]
        xMin = xSum
        xMax = xSum
        yMin = ySum
        yMax = ySum

        forEachPoint(1) { i ->
            val x = this[i + X]
            val y = this[i + Y]
            if (x < xMin) xMin = x
            if (x > xMax) xMax = x
            if (y < yMin) yMin = y
            if (y > yMax) yMax = y
            xSum += x
            ySum += y
        }

        xCenterLast = xCenter
        yCenterLast = yCenter
        xCenter = xSum / (points.size / N_POINT_FIELDS)
        yCenter = ySum / (points.size / N_POINT_FIELDS)
    }

    open fun recalculateRotation(angleOffset: Float = this.angleOffset)
    {
        val xDelta = points[X] - xCenter
        val yDelta = points[Y] - yCenter
        angle = (atan2(yDelta, xDelta) / PI.toFloat() + 1.0f) * 180f - angleOffset
        if (angle < 0) angle += 360
        if (angle > 360) angle -= 360
    }

    fun applyAcceleration(xAcc: Float, yAcc: Float)
    {
        if (xAcc == 0f && yAcc == 0f)
            return

        forEachPoint { i ->
            this[i + X_ACC] += xAcc
            this[i + Y_ACC] += yAcc
        }

        isSleeping = false
        stepsAtRest = 0
    }

    fun applyAngularAcceleration(acc: Float)
    {
        if (acc == 0f)
            return

        forEachPoint { i ->
            val xNormal = yCenter - this[i + Y]
            val yNormal = this[i + X] - xCenter
            val length = 1.0f / sqrt(xNormal * xNormal + yNormal * yNormal)
            this[i + X_ACC] += xNormal * length * acc
            this[i + Y_ACC] += yNormal * length * acc
        }

        isSleeping = false
        stepsAtRest = 0
    }

    inline fun forEachPoint(startIndex: Int = 0, block: FloatArray.(Int) -> Unit)
    {
        val points = points
        val size = points.size
        var i = startIndex * N_POINT_FIELDS
        while (i < size)
        {
            block(points, i)
            i += N_POINT_FIELDS
        }
    }

    inline fun forEachBoundaryPoint(startIndex: Int = 0, block: FloatArray.(Int) -> Unit)
    {
        val points = points
        val size = nBoundaryPoints * N_POINT_FIELDS
        var i = startIndex * N_POINT_FIELDS
        while (i < size)
        {
            block(points, i)
            i += N_POINT_FIELDS
        }
    }

    inline fun forEachStickConstraint(block: FloatArray.(Int) -> Unit)
    {
        val constraints = constraints
        val size = nStickConstraints * N_STICK_CONSTRAINT_FIELDS
        var i = 0
        while (i < size)
        {
            block(constraints, i)
            i += N_STICK_CONSTRAINT_FIELDS
        }
    }

    fun isInside(x: Float, y: Float): Boolean
    {
        val p = points
        var j = p.size - N_POINT_FIELDS
        var inside = false
        forEachBoundaryPoint { i ->
            if (p[i + Y] > y != p[j + Y] > y && x < (p[j + X] - p[i + X]) * (y - p[i + Y]) / (p[j + Y] - p[i + Y]) + p[i + X])
                inside = !inside
            j = i
        }

        return inside
    }

    companion object
    {
        // Point indexes
        const val X = 0
        const val Y = 1
        const val X_LAST = 2
        const val Y_LAST = 3
        const val X_ACC = 4
        const val Y_ACC = 5
        const val N_POINT_FIELDS = 6

        // Stick constraint indexes
        const val POINT_0 = 0
        const val POINT_1 = 1
        const val LENGTH = 2
        const val STIFFNESS = 3
        const val N_STICK_CONSTRAINT_FIELDS = 4

        // Number of physics steps a body has to be at rest before it's put to sleep
        const val RESTING_STEPS_BEFORE_SLEEP = 90
        const val RESTING_MIN_VEL = 0.2

        // Cached vector object
        val reusableVector = Vector2f()
    }
}