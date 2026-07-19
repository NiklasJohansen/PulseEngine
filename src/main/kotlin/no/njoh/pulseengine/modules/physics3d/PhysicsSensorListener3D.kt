package no.njoh.pulseengine.modules.physics3d

import no.njoh.pulseengine.core.PulseEngine

/**
 * Receives overlap events when another physics shape enters or leaves a sensor owned by the entity.
 * Events are reported per overlapping sensor/visitor shape pair. Sensors are discrete and do not
 * report shapes that pass completely through them within one fixed physics step.
 */
interface PhysicsSensorListener3D
{
    fun onSensorEntered(engine: PulseEngine, otherEntityId: Long) { }

    fun onSensorExited(engine: PulseEngine, otherEntityId: Long) { }
}