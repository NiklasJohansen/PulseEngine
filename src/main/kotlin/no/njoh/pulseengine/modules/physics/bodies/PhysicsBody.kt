package no.njoh.pulseengine.modules.physics.bodies

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Physical
import no.njoh.pulseengine.core.shared.primitives.Shape
import no.njoh.pulseengine.modules.physics.BodyType
import no.njoh.pulseengine.modules.physics.ContactResult
import no.njoh.pulseengine.modules.physics.PhysicsEntity

/**
 * Base class for all physics bodies.
 * @see PolygonBody
 * @see CircleBody
 * @see PointBody
 */
interface PhysicsBody : PhysicsEntity, Physical
{
    /** Contains data about the physical shape of the body. */
    override val shape: Shape

    @get:Prop("Physics", 0, desc = "Determines how the body is affected by the physics system.")
    var bodyType: BodyType

    @get:Prop("Physics", 1, min = 0f, desc = "Bit mask where each bit represents a collision layer this body can be part of.")
    var layerMask: Int

    @get:Prop("Physics", 2, min = 0f, desc = "Bit mask where each bit represents a collision layer this body can collide with.")
    var collisionMask: Int

    @get:Prop("Physics", 3, min = 0f, max = 1f, desc = "Determines the amount of bounce in a collision. Range: 0.0 (inelastic) - 1.0 (perfectly elastic).")
    var restitution: Float

    @get:Prop("Physics", 4, min = 0f, desc = "Density along with the shape size affects the mass of the body.")
    var density: Float

    @get:Prop("Physics", 5, min = 0f, max = 1f, desc = "Determines how easily bodies slide of each other on contact. Range: 0.0 (slippery) - 1.0 (rough).")
    var friction: Float

    @get:Prop("Physics", 6, min = 0f, max = 1f,  desc = "Determines how much dampening the bodies experiences from the air. Range: 0.0 (no dampening) - 1.0 (full stop).")
    var drag: Float

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
     * Returns the total mass of the body.
     */
    fun getMass(): Float
}