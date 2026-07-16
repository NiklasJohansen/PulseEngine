package no.njoh.pulseengine.modules.physics3d.box3d.joints

import no.njoh.box3d.raw.Box3DRaw.b3WeldJoint_SetAngularDampingRatio
import no.njoh.box3d.raw.Box3DRaw.b3WeldJoint_SetAngularHertz
import no.njoh.box3d.raw.Box3DRaw.b3WeldJoint_SetLinearDampingRatio
import no.njoh.box3d.raw.Box3DRaw.b3WeldJoint_SetLinearHertz
import no.njoh.pulseengine.modules.physics3d.PhysicsJointDefinition3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBody
import no.njoh.pulseengine.modules.physics3d.box3d.nonNegativeOr
import org.joml.Quaternionf
import org.joml.Vector3f
import java.lang.foreign.MemorySegment

/**
 * Box3D-backed weld joint.
 */
class Box3DWeldJoint(
    nativeJointId: MemorySegment,
    bodyA: Box3DBody,
    bodyB: Box3DBody
) : Box3DJoint(nativeJointId, bodyA, bodyB) {

    override fun applyDefinition(definition: PhysicsJointDefinition3D)
    {
        require(definition is Box3DWeldJointDefinition)
        definition.sanitize()
        requireUsable()

        b3WeldJoint_SetLinearHertz(nativeJointId, definition.linearHertz)
        b3WeldJoint_SetLinearDampingRatio(nativeJointId, definition.linearDampingRatio)
        b3WeldJoint_SetAngularHertz(nativeJointId, definition.angularHertz)
        b3WeldJoint_SetAngularDampingRatio(nativeJointId, definition.angularDampingRatio)
    }
}

data class Box3DWeldJointDefinition(
    override val worldPosA: Vector3f    = Vector3f(),
    override val worldRotA: Quaternionf = Quaternionf(),
    override val worldPosB: Vector3f    = Vector3f(),
    override val worldRotB: Quaternionf = Quaternionf(),
    override var collision: Boolean     = false,
    var linearHertz: Float              = 0f,
    var linearDampingRatio: Float       = 0.7f,
    var angularHertz: Float             = 0f,
    var angularDampingRatio: Float      = 0.7f
) : PhysicsJointDefinition3D {

    override fun sanitize()
    {
        linearHertz         = linearHertz.nonNegativeOr(0f)
        linearDampingRatio  = linearDampingRatio.nonNegativeOr(0.7f)
        angularHertz        = angularHertz.nonNegativeOr(0f)
        angularDampingRatio = angularDampingRatio.nonNegativeOr(0.7f)
    }
}