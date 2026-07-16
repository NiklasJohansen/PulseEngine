package no.njoh.pulseengine.modules.physics3d.box3d.joints

import no.njoh.box3d.raw.Box3DRaw.b3DistanceJoint_EnableLimit
import no.njoh.box3d.raw.Box3DRaw.b3DistanceJoint_EnableSpring
import no.njoh.box3d.raw.Box3DRaw.b3DistanceJoint_SetLength
import no.njoh.box3d.raw.Box3DRaw.b3DistanceJoint_SetLengthRange
import no.njoh.box3d.raw.Box3DRaw.b3DistanceJoint_SetSpringDampingRatio
import no.njoh.box3d.raw.Box3DRaw.b3DistanceJoint_SetSpringHertz
import no.njoh.pulseengine.modules.physics3d.PhysicsJointDefinition3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBody
import no.njoh.pulseengine.modules.physics3d.box3d.nonNegativeOr
import org.joml.Quaternionf
import org.joml.Vector3f
import java.lang.foreign.MemorySegment

/**
 * Box3D-backed fixed-distance joint.
 */
class Box3DDistanceJoint(
    nativeJointId: MemorySegment,
    bodyA: Box3DBody,
    bodyB: Box3DBody
) : Box3DJoint(nativeJointId, bodyA, bodyB) {

    override fun applyDefinition(definition: PhysicsJointDefinition3D)
    {
        require(definition is Box3DDistanceJointDefinition)
        definition.sanitize()
        requireUsable()

        b3DistanceJoint_SetLength(nativeJointId, definition.length)
        b3DistanceJoint_EnableSpring(nativeJointId, definition.enableSpring)
        b3DistanceJoint_SetSpringHertz(nativeJointId, definition.springHertz)
        b3DistanceJoint_SetSpringDampingRatio(nativeJointId, definition.springDampingRatio)
        b3DistanceJoint_EnableLimit(nativeJointId, definition.enableLimit)
        b3DistanceJoint_SetLengthRange(nativeJointId, definition.minLength, definition.maxLength)
    }
}

data class Box3DDistanceJointDefinition(
    override val worldPosA: Vector3f    = Vector3f(),
    override val worldRotA: Quaternionf = Quaternionf(),
    override val worldPosB: Vector3f    = Vector3f(),
    override val worldRotB: Quaternionf = Quaternionf(),
    override var collision: Boolean     = false,
    var length: Float                   = 1f,
    var enableSpring: Boolean           = false,
    var springHertz: Float              = 4f,
    var springDampingRatio: Float       = 0.7f,
    var enableLimit: Boolean            = false,
    var minLength: Float                = 0f,
    var maxLength: Float                = 1f
) : PhysicsJointDefinition3D {

    override fun sanitize()
    {
        length             = length.nonNegativeOr(1f)
        springHertz        = springHertz.nonNegativeOr(4f)
        springDampingRatio = springDampingRatio.nonNegativeOr(0.7f)
        minLength          = minLength.nonNegativeOr(0f)
        maxLength          = maxLength.nonNegativeOr(1f)
    }
}
