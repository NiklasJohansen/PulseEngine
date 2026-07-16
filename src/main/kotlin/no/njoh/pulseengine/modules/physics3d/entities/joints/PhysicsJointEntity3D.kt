package no.njoh.pulseengine.modules.physics3d.entities.joints

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.scene.interfaces.Rotatable3D
import no.njoh.pulseengine.core.scene.interfaces.Translatable3D
import no.njoh.pulseengine.modules.physics3d.PhysicsJointDefinition3D

interface PhysicsJointEntity3D : Translatable3D, Rotatable3D
{
    val bodyAEntityId: Long
    val bodyBEntityId: Long

    @JsonIgnore
    fun getPhysicsJointDefinition(): PhysicsJointDefinition3D
}