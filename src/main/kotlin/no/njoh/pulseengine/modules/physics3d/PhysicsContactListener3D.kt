package no.njoh.pulseengine.modules.physics3d

import no.njoh.pulseengine.core.PulseEngine
import org.joml.Vector3f

/**
 * Receives contact events for a [PhysicsBody3D]. Events are reported per colliding shape pair.
 */
interface PhysicsContactListener3D
{
    fun onContactStarted(engine: PulseEngine, otherEntityId: Long) { }

    fun onContactEnded(engine: PulseEngine, otherEntityId: Long) { }

    fun onContactHit(engine: PulseEngine, otherEntityId: Long, point: Vector3f, normal: Vector3f, approachSpeed: Float) { }
}