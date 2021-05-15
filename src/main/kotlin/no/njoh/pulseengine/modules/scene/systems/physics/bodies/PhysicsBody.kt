package no.njoh.pulseengine.modules.scene.systems.physics.bodies

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.systems.physics.BodyType
import no.njoh.pulseengine.modules.scene.systems.physics.ContactResult
import org.joml.Vector2f

interface PhysicsBody
{
    var bodyType: BodyType
    var friction: Float
    var restitution: Float
    var drag: Float
    var mass: Float

    /**
     * Called once when the physics system starts.
     */
    fun init()

    /**
     * Called by the physics system once every fixed time step.
     * Suitable for updating velocities, accelerations and positions.
     */
    fun beginStep(timeStep: Float, gravity: Float)

    /**
     * Called N times by the physics system every fixed time step.
     * Suitable for iteratively solving collisions and constraints.
     * @param iteration - the current iteration
     * @param totalIterations - the total number of iterations to be performed
     */
    fun iterateStep(iteration: Int, totalIterations: Int, engine: PulseEngine, worldWidth: Int, worldHeight: Int)

    /**
     * Called every frame if enabled by the physics system.
     */
    fun render(surface: Surface2D)

    /**
     * Called when two bodies collide.
     */
    fun onCollision(engine: PulseEngine, otherBody: Body, result: CollisionResult)

    /**
     * Returns true if the axis aligned bounding boxes of two bodies intersect.
     * Used as a fast way of checking for potential collisions.
     */
    fun hasOverlappingAABB(xMin: Float, yMin: Float, xMax: Float, yMax: Float): Boolean

    /**
     * Returns the coordinates of a specific body point.
     */
    fun getPoint(index: Int): Vector2f?

    /**
     * Sets the position of a specific body point.
     */
    fun setPoint(index: Int, x: Float, y: Float)

    /**
     * Returns the total amount of points the body is built up of.
     */
    fun getPointCount(): Int
}