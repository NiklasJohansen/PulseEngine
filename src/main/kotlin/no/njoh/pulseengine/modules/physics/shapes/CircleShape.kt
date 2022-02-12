package no.njoh.pulseengine.modules.physics.shapes

import no.njoh.pulseengine.core.shared.primitives.Shape
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import org.joml.Vector2f
import kotlin.math.PI

data class CircleShape(
    var x: Float = 0f,
    var y: Float = 0f,
    var xLast: Float = 0f,
    var yLast: Float = 0f,
    var xAcc: Float = 0f,
    var yAcc: Float = 0f,
    var rot: Float = 0f,
    var rotLast: Float = 0f,
    var lastRotVel: Float = 0f,
    var radius: Float = 50f,
    var mass: Float = 1f,
    var isSleeping: Boolean = false,
    var stepsAtRest: Int = 0

) : Shape() {

    fun init(x: Float, y: Float, radius: Float, rotation: Float, density: Float)
    {
        this.x = x
        this.y = y
        this.xLast = x
        this.yLast = y
        this.rot = rotation.toRadians()
        this.rotLast = rotation.toRadians()
        this.radius = radius
        this.mass = density * PI.toFloat() * radius * radius
    }

    fun applyAngularAcceleration(acc: Float)
    {
        this.rotLast += acc
    }

    override fun getPointCount() = 1
    override fun getRadius() = radius
    override fun getPoint(index: Int): Vector2f = reusableVector.set(x, y)
    override fun setPoint(index: Int, x: Float, y: Float)
    {
        this.x = x
        this.y = y
    }

    companion object
    {
        // Number of physics steps a body has to be at rest before it's put to sleep
        const val RESTING_STEPS_BEFORE_SLEEP = 90
        const val RESTING_MIN_VEL = 0.2

        // Cached vector object
        val reusableVector = Vector2f()
    }
}