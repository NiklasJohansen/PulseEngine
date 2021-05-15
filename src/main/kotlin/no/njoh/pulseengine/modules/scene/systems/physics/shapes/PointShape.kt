package no.njoh.pulseengine.modules.scene.systems.physics.shapes

import org.joml.Vector2f

data class PointShape(
    // Current position
    var x: Float = 0f,
    var y: Float = 0f,

    // Last position, used to calculate velocity
    var xLast: Float = 0f,
    var yLast: Float = 0f,

    // Actual last position (updated once per frame, and does not change when direction and velocity changes)
    var xLastActual: Float = 0f,
    var yLastActual: Float = 0f,

    // Current velocity, calculated from last position and does not change while resolving collisions
    var xVel: Float = 0f,
    var yVel: Float = 0f,

    // Acceleration
    var xAcc: Float = 0f,
    var yAcc: Float = 0f
) {
    companion object
    {
        val reusableVector = Vector2f()
    }
}