package no.njoh.pulseengine.modules.scene.systems.physics.shapes

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

open class RectangleShape : ConvexPolygonShape()
{
    override val nBoundaryPoints = 4
    override var points = FloatArray(N_POINT_FIELDS * 4) // 4 points, one for each corner
    override var constraints = FloatArray(N_CONSTRAINT_FIELDS * 6) // 6 constrains

    override fun build(x: Float, y: Float, width: Float, height: Float, rot: Float)
    {
        val r = rot / 180f * PI.toFloat()
        val c = cos(r)
        val s = sin(r)
        val w = width * 0.5f
        val h = height * 0.5f
        val x0 = -w * c - h * s
        val y0 = -w * s + h * c
        val x1 =  w * c - h * s
        val y1 =  w * s + h * c

        // Set point positions
        setPoint(0, x + x0, y + y0) // Top, Left
        setPoint(1, x + x1, y + y1) // Top, Right
        setPoint(2, x - x0, y - y0) // Bottom, Right
        setPoint(3, x - x1, y - y1) // Bottom, Left

        // Set all point constrains
        setConstraint(0, 0, 1, 1f)
        setConstraint(1, 1, 2, 1f)
        setConstraint(2, 2, 3, 1f)
        setConstraint(3, 3, 0, 1f)
        setConstraint(4, 0, 2, 1f)
        setConstraint(5, 1, 3, 1f)

        // Update position, rotation and bounding box
        recalculateBoundingBox()
        recalculateRotation(angleOffset = rot)
        this.angleOffset = angle
        this.xCenterLast = xCenter
        this.yCenterLast = yCenter
    }
}