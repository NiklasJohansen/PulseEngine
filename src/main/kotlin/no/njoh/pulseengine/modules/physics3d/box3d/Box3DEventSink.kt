package no.njoh.pulseengine.modules.physics3d.box3d

import no.njoh.pulseengine.core.PulseEngine
import org.joml.Vector3f

interface Box3DEventSink
{
    fun onContactStarted(engine: PulseEngine, shapeA: Box3DShape, shapeB: Box3DShape)

    fun onContactEnded(engine: PulseEngine, shapeA: Box3DShape, shapeB: Box3DShape)

    fun onContactHit(
        engine: PulseEngine,
        shapeA: Box3DShape,
        shapeB: Box3DShape,
        point: Vector3f,
        normal: Vector3f,
        approachSpeed: Float
    )

    fun onSensorEntered(engine: PulseEngine, sensorShape: Box3DShape, visitorShape: Box3DShape)

    fun onSensorExited(engine: PulseEngine, sensorShape: Box3DShape, visitorShape: Box3DShape)
}