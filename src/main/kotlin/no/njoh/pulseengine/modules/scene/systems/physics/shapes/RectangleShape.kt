package no.njoh.pulseengine.modules.scene.systems.physics.shapes

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class RectangleShape : PolygonShape()
{
    override val nBoundaryPoints = 4  // 4 points, one for each corner
    override val nStickConstraints = 6 // 4 edge sticks and 2 crossover sticks
    override var points = FloatArray(N_POINT_FIELDS * nBoundaryPoints)
    override var constraints = FloatArray(nStickConstraints * N_STICK_CONSTRAINT_FIELDS)

    override fun build(x: Float, y: Float, width: Float, height: Float, rot: Float, density: Float)
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

        // Set all stick constrains
        setStickConstraint(0, 0, 1, 1f)
        setStickConstraint(1, 1, 2, 1f)
        setStickConstraint(2, 2, 3, 1f)
        setStickConstraint(3, 3, 0, 1f)
        setStickConstraint(4, 0, 2, 1f)
        setStickConstraint(5, 1, 3, 1f)

        // Update position, rotation and bounding box
        recalculateBoundingBox()
        recalculateRotation(angleOffset = rot)
        this.angleLast = rot
        this.angleOffset = angle
        this.xCenterLast = xCenter
        this.yCenterLast = yCenter
        this.mass = density * width * height
    }
}