package no.njoh.pulseengine.modules.scene.systems.physics.shapes

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Creates a rectangle shape with champfered edges.
 * Suited for a platformer character.
 *   _____
 *  /     \
 *  |     |
 *  |     |
 *  \_____/
 */
class ChamferedRectangleShape(
    private var keepAxisAligned: Boolean = true,
    private val widthFraction: Float = 0.7f,
    private val heightFraction: Float = 0.8f
) : ConvexPolygonShape() {

    override val nBoundaryPoints = 8
    override val nStickConstraints = (nBoundaryPoints * (nBoundaryPoints - 1) / 2)
    override var points = FloatArray(N_POINT_FIELDS * nBoundaryPoints)
    override var constraints = FloatArray(nStickConstraints * N_STICK_CONSTRAINT_FIELDS)

    override fun build(x: Float, y: Float, width: Float, height: Float, rot: Float)
    {
        val w = width / 2
        val h = height / 2
        setPoint(0, x - w * widthFraction, y - h)
        setPoint(1, x + w * widthFraction, y - h)
        setPoint(2, x + w, y - h * heightFraction)
        setPoint(3, x + w, y + h * heightFraction)
        setPoint(4, x + w * widthFraction, y + h)
        setPoint(5, x - w * widthFraction, y + h)
        setPoint(6, x - w, y + h * heightFraction)
        setPoint(7, x - w, y - h * heightFraction)

        // Create stick constrains
        var count = 0
        for (i in 0 until nBoundaryPoints)
            for (j in i + 1 until nBoundaryPoints)
                setStickConstraint(count++, i, j, 1f)

        // Disable axis alignment while calculating initial angle offset
        val tmpKeepAxisAligned = keepAxisAligned
        keepAxisAligned = false

        // Update position, rotation and bounding box
        recalculateBoundingBox()
        recalculateRotation(angleOffset = rot)
        this.angleOffset = angle
        this.xCenterLast = xCenter
        this.yCenterLast = yCenter
        this.keepAxisAligned = tmpKeepAxisAligned
    }

    override fun recalculateRotation(angleOffset: Float)
    {
        super.recalculateRotation(angleOffset)

        if (keepAxisAligned && angle != 0f)
        {
            val xCenter = xCenter
            val yCenter = yCenter
            val targetAngle = -angle / 180f * PI.toFloat()
            val co = cos(targetAngle)
            val si = sin(targetAngle)
            forEachPoint(1) { i ->
                val x = this[i + X]
                val y = this[i + Y]
                val xDeltaPos = x - xCenter
                val yDeltaPos = y - yCenter
                val xNew = xCenter + (xDeltaPos * co - yDeltaPos * si)
                val yNew = yCenter + (xDeltaPos * si + yDeltaPos * co)

                this[i + X] = xNew
                this[i + Y] = yNew
                this[i + X_LAST] += xNew - x
                this[i + Y_LAST] += yNew - y
            }
        }
    }
}