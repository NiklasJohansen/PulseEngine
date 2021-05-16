package no.njoh.pulseengine.modules.scene.systems.physics.bodies

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.systems.physics.BodyType
import no.njoh.pulseengine.modules.scene.systems.physics.ContactResult
import org.joml.Vector2f

/**
 * Base class for all physics bodies.
 * @see PolygonBody
 * @see CircleBody
 * @see PointBody
 */
interface PhysicsBody
{
    /** Determines how the body is affected by the physics system. */
    var bodyType: BodyType

    /** Bit mask where each bit represents a collision layer this body can be part of. */
    var layerMask: Int

    /** Bit mask where each bit represents a collision layer this body can collide with. */
    var collisionMask: Int

    /** Determines the amount of bounce in a collision. Range: 0.0 (inelastic) - 1.0 (perfectly elastic). */
    var restitution: Float

    /** Density along with the shape size affects the mass of the body. */
    var density: Float

    /** Determines how easily bodies slide of each other on contact. Range: 0.0 (slippery) - 1.0 (rough). */
    var friction: Float

    /** Determines how much dampening the bodies experiences from the air. Range: 0.0 (no dampening) - 1.0 (full stop). */
    var drag: Float

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
    fun onCollision(engine: PulseEngine, otherBody: PhysicsBody, result: ContactResult)

    /**
     * Wakes up sleeping bodies.
     */
    fun wakeUp()

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