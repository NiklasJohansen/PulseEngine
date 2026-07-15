package no.njoh.pulseengine.modules.physics3d.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.interfaces.Spatial3D
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBodyDefinition
import no.njoh.pulseengine.modules.physics3d.BodyType3D
import no.njoh.pulseengine.modules.physics3d.PhysicsBody3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DShapeDefinition
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc

interface PhysicsEntity3D : Spatial3D
{
    @get:Prop("Physics",   i=0)                 var bodyType: BodyType3D
    @get:Prop("Physics",   i=1, min=0f)         var density: Float
    @get:Prop("Physics",   i=2, min=0f)         var friction: Float
    @get:Prop("Physics",   i=3, min=0f, max=1f) var restitution: Float
    @get:Prop("Physics",   i=4, min=0f)         var linearDamping: Float
    @get:Prop("Physics",   i=5, min=0f)         var angularDamping: Float
    @get:Prop("Physics",   i=6)                 var gravityScale: Float
    @get:Prop("Physics",   i=7)                 var bullet: Boolean
    @get:Prop("Physics",   i=8)                 var fixedRotation: Boolean
    @get:Prop("Collision", i=1)                 var layerMask: Int
    @get:Prop("Collision", i=2)                 var collisionMask: Int
    @get:Prop("Collision", i=3)                 var sensor: Boolean
    
    fun onPhysicsBodyCreated(engine: PulseEngine, body: PhysicsBody3D)

    fun onPhysicsFixedUpdate(engine: PulseEngine, body: PhysicsBody3D) { }
    
    fun onPhysicsTransformUpdated(position: Vector3fc, rotation: Quaternionfc)

    fun onExternalTransformApplied(position: Vector3fc, rotation: Quaternionfc)

    fun hasPendingTransformChange(): Boolean

    fun getShapeDefinitions(engine: PulseEngine): List<Box3DShapeDefinition>

    fun getBodyDefinition() = Box3DBodyDefinition(
        type = bodyType,
        position = Vector3f(xPos, yPos, zPos),
        rotation = Quaternionf().rotationXYZ(xRot.toRadians(), yRot.toRadians(), zRot.toRadians()),
        linearDamping = linearDamping,
        angularDamping = angularDamping,
        gravityScale = gravityScale,
        bullet = bullet,
        fixedRotation = fixedRotation
    )

    fun getPhysicsPropertyHash(engine: PulseEngine): Int
    {
        var hash = bodyType.ordinal
        hash = 31 * hash + density.toBits()
        hash = 31 * hash + friction.toBits()
        hash = 31 * hash + restitution.toBits()
        hash = 31 * hash + linearDamping.toBits()
        hash = 31 * hash + angularDamping.toBits()
        hash = 31 * hash + gravityScale.toBits()
        hash = 31 * hash + bullet.hashCode()
        hash = 31 * hash + fixedRotation.hashCode()
        hash = 31 * hash + layerMask
        hash = 31 * hash + collisionMask
        hash = 31 * hash + sensor.hashCode()
        return hash
    }
}