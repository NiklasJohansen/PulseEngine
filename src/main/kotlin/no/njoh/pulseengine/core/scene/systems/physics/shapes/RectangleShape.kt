package no.njoh.pulseengine.core.scene.systems.physics.shapes

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
        val r = -rot / 180f * PI.toFloat()
        val c = cos(r)
        val s = sin(r)
        val w = width * 0.5f
        val h = height * 0.5f
        val x0 = -w * c - h * s
        val y0 = -w * s + h * c
        val x1 =  w * c - h * s
        val y1 =  w * s + h * c

        // Set point positions
        createPoint(0, x + x0, y + y0) // Top, Left
        createPoint(1, x + x1, y + y1) // Top, Right
        createPoint(2, x - x0, y - y0) // Bottom, Right
        createPoint(3, x - x1, y - y1) // Bottom, Left

        // Set all stick constrains
        createStickConstraint(0, 0, 1, 1f)
        createStickConstraint(1, 1, 2, 1f)
        createStickConstraint(2, 2, 3, 1f)
        createStickConstraint(3, 3, 0, 1f)
        createStickConstraint(4, 0, 2, 1f)
        createStickConstraint(5, 1, 3, 1f)

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