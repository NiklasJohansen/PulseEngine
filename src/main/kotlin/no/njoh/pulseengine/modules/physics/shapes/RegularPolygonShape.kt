package no.njoh.pulseengine.modules.physics.shapes

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class RegularPolygonShape(edges: Int) : PolygonShape()
{
    override val nBoundaryPoints = edges
    override val nStickConstraints = (edges * (edges - 1) / 2)
    override var points = FloatArray(N_POINT_FIELDS * edges)
    override var constraints = FloatArray(N_STICK_CONSTRAINT_FIELDS * nStickConstraints)

    override fun build(x: Float, y: Float, width: Float, height: Float, rot: Float, density: Float)
    {
        setPoints(x, y,  width * 0.5f, height * 0.5f)
        setConstraints()
        recalculateBoundingBox()
        recalculateRotation(angleOffset = rot)

        val r = (width + height) * 0.25f
        this.mass = density * (r * r * nBoundaryPoints * sin(2f * PI.toFloat() / nBoundaryPoints) * 0.5f)
        this.angleLast = rot
        this.angleOffset = angle
        this.xCenterLast = xCenter
        this.yCenterLast = yCenter
    }

    private fun setPoints(x: Float, y: Float, width: Float, height: Float)
    {
        for (i in 0 until nBoundaryPoints)
        {
            val angle = i.toFloat() / nBoundaryPoints * 2 * PI + PI / nBoundaryPoints
            val x0 = x + width * cos(-angle).toFloat()
            val y0 = y + height * sin(-angle).toFloat()
            createPoint(i, x0, y0)
        }
    }

    private fun setConstraints()
    {
        var count = 0
        for (i in 0 until nBoundaryPoints)
            for (j in i + 1 until nBoundaryPoints)
                createStickConstraint(count++, i, j, 1f)
    }
}