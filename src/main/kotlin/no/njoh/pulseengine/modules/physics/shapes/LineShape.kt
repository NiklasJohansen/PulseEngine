package no.njoh.pulseengine.modules.physics.shapes

import no.njoh.pulseengine.core.shared.primitives.Shape
import org.joml.Vector2f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class LineShape(
    /** Current position */
    var x0: Float = 0f,
    var y0: Float = 0f,
    var x1: Float = 0f,
    var y1: Float = 0f,

    /** Last position, used to calculate velocity */
    var xLast0: Float = 0f,
    var yLast0: Float = 0f,
    var xLast1: Float = 0f,
    var yLast1: Float = 0f,

    /** Acceleration */
    var xAcc0: Float = 0f,
    var yAcc0: Float = 0f,
    var xAcc1: Float = 0f,
    var yAcc1: Float = 0f,

    /** Mass */
    var mass: Float = 1f,

    /** Initial length */
    var length: Float = 0f
) : Shape() {

    fun init(x: Float, y: Float, length: Float, rotAngle: Float)
    {
        val r = -rotAngle / 180f * PI.toFloat()
        val c = cos(r)
        val s = sin(r)
        val w = length * 0.5f
        x0 = x - w * c
        y0 = y - w * s
        x1 = x + w * c
        y1 = y + w * s
        xLast0 = x0
        yLast0 = y0
        xLast1 = x1
        yLast1 = y1
        this.length = length
    }

    override fun getPointCount() = 2

    override fun getRadius() = null

    override fun getPoint(index: Int): Vector2f =
        if (index == 0) reusableVector.set(x0, y0) else reusableVector.set(x1, y1)

    override fun setPoint(index: Int, x: Float, y: Float) =
        if (index == 0) { x0 = x; y0 = y } else { x1 = x; y1 = y }

    companion object
    {
        val reusableVector = Vector2f()
    }
}