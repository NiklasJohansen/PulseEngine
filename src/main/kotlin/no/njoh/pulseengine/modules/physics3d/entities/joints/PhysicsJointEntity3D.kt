package no.njoh.pulseengine.modules.physics3d.entities.joints

import no.njoh.pulseengine.core.scene.interfaces.Rotatable3D
import no.njoh.pulseengine.core.scene.interfaces.Translatable3D
import no.njoh.pulseengine.modules.physics3d.PhysicsJoint3D
import no.njoh.pulseengine.modules.physics3d.PhysicsJointDefinition3D
import org.joml.Quaternionfc
import org.joml.Vector3fc

/**
 * Scene-facing contract for a 3D joint.
 *
 * The physics system resolves the two bodies and their local joint frames. The implementation
 * supplies the joint-specific definition and may retain the runtime handle when it exposes
 * gameplay controls.
 */
interface PhysicsJointEntity3D : Translatable3D, Rotatable3D
{
    val bodyAEntityId: Long
    val bodyBEntityId: Long

    fun createPhysicsJointDefinition(localPosA: Vector3fc, localRotA: Quaternionfc, localPosB: Vector3fc, localRotB: Quaternionfc): PhysicsJointDefinition3D

    fun physicsPropertyHash(): Int

    fun bindPhysicsJoint(joint: PhysicsJoint3D) { }

    fun unbindPhysicsJoint() { }
}