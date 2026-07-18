package no.njoh.pulseengine.modules.physics3d.entities.bodies

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.interfaces.Spatial3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBodyDefinition
import no.njoh.pulseengine.modules.physics3d.PhysicsBody3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DShapeDefinition
import org.joml.Quaternionfc
import org.joml.Vector3fc

interface PhysicsBodyEntity3D : Spatial3D
{
    fun onPhysicsBodyCreated(engine: PulseEngine, body: PhysicsBody3D)

    fun onPhysicsFixedUpdate(engine: PulseEngine, body: PhysicsBody3D) { }
    
    fun onPhysicsTransformUpdated(position: Vector3fc, rotation: Quaternionfc)

    fun onExternalTransformApplied(position: Vector3fc, rotation: Quaternionfc)

    @JsonIgnore
    fun hasPendingTransformChange(): Boolean

    @JsonIgnore
    fun getPhysicsBodyDefinition(): Box3DBodyDefinition

    @JsonIgnore
    fun getPhysicsShapeDefinitions(engine: PulseEngine): List<Box3DShapeDefinition>
}