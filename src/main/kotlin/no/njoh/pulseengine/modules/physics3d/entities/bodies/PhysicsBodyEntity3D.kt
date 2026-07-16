package no.njoh.pulseengine.modules.physics3d.entities.bodies

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.interfaces.Spatial3D
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBodyDefinition
import no.njoh.pulseengine.modules.physics3d.PhysicsBodyType3D
import no.njoh.pulseengine.modules.physics3d.PhysicsBody3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DShapeDefinition
import org.joml.Quaternionfc
import org.joml.Vector3fc

interface PhysicsBodyEntity3D : Spatial3D
{
    @get:Prop("Physics",   i=0)                 var bodyType: PhysicsBodyType3D
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

    @JsonIgnore
    fun getPhysicsBodyDefinition(): Box3DBodyDefinition

    @JsonIgnore
    fun getPhysicsShapeDefinitions(engine: PulseEngine): List<Box3DShapeDefinition>
}

fun Box3DBodyDefinition.updateFrom(entity: PhysicsBodyEntity3D): Box3DBodyDefinition
{
    type = entity.bodyType
    position.set(entity.xPos, entity.yPos, entity.zPos)
    rotation.rotationXYZ(entity.xRot.toRadians(), entity.yRot.toRadians(), entity.zRot.toRadians())
    linearDamping = entity.linearDamping
    angularDamping = entity.angularDamping
    gravityScale = entity.gravityScale
    bullet = entity.bullet
    fixedRotation = entity.fixedRotation
    return this
}