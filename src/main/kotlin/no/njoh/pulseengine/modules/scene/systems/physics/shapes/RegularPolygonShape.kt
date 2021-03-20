package no.njoh.pulseengine.modules.scene.systems.physics.shapes

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class RegularPolygonShape(edges: Int) : ConvexPolygonShape()
{
    override val nBoundaryPoints = edges
    override var points = FloatArray(N_POINT_FIELDS * edges)
    override var constraints = FloatArray(N_CONSTRAINT_FIELDS * (edges * (edges - 1) / 2))

    override fun build(x: Float, y: Float, width: Float, height: Float, rot: Float)
    {
        setPoints(x, y,  min(width, height) / 2f)
        setConstraints()
        recalculateBoundingBox()
        recalculateRotation(angleOffset = rot)
        this.angleOffset = angle
        this.xCenterLast = xCenter
        this.yCenterLast = yCenter
    }

    private fun setPoints(x: Float, y: Float, radius: Float)
    {
        for (i in 0 until nBoundaryPoints)
        {
            val angle = i.toFloat() / nBoundaryPoints * 2 * PI
            val x0 = x + radius * cos(angle).toFloat()
            val y0 = y + radius * sin(angle).toFloat()
            setPoint(i, x0, y0)
        }
    }

    private fun setConstraints()
    {
        var nConstraints = 0
        val addedConstraints = mutableSetOf<String>()
        for (i in 0 until nBoundaryPoints)
        {
            for (j in 1 until nBoundaryPoints)
            {
                val p1 = i
                val p2 = (i + j) % nBoundaryPoints

                if ("$p1,$p2" !in addedConstraints)
                {
                    setConstraint(nConstraints++, p1, p2, 1f)
                    addedConstraints.addAll(listOf("$p1,$p2", "$p2,$p1"))
                }
            }
        }
    }
}