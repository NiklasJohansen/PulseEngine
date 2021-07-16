package no.njoh.pulseengine.modules.scene.systems.physics.shapes

import no.njoh.pulseengine.data.Shape
import org.joml.Vector2f

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