package no.njoh.pulseengine.modules.physics3d

import org.joml.Quaternionfc
import org.joml.Vector3fc

interface PhysicsJointDefinition3D
{
    val worldPosA: Vector3fc
    val worldRotA: Quaternionfc
    val worldPosB: Vector3fc
    val worldRotB: Quaternionfc
    val collision: Boolean

    fun sanitize()
}