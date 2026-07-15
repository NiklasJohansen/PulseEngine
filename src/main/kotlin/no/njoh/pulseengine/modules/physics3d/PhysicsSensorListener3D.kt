package no.njoh.pulseengine.modules.physics3d

import no.njoh.pulseengine.core.PulseEngine

/**
 * Receives overlap events when another physics shape enters or leaves a sensor owned by the entity.
 */
interface PhysicsSensorListener3D
{
    fun onSensorEntered(engine: PulseEngine, otherEntityId: Long) { }

    fun onSensorExited(engine: PulseEngine, otherEntityId: Long) { }
}