package no.njoh.pulseengine.modules.physics3d.entities.joints

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.annotations.EntityRef
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DDistanceJointDefinition
import no.njoh.pulseengine.modules.physics3d.entities.bodies.PhysicsBodyEntity3D
import no.njoh.pulseengine.modules.physics3d.entities.joints.PhysicsJointEntity3D
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * Connects anchor A at the entity origin to anchor B at an editable local offset.
 */
@Name("Distance Joint (3D)")
@Icon("SHAPES", size = 24f, showInViewport = true)
open class DistanceJoint3D : SceneEntity(), Named, PhysicsJointEntity3D
{
    @Prop(i=0) override var name = "Distance Joint"
    @Prop("Anchor A [*P]", i=1) override var xPos = 0f; override var yPos = 0f; override var zPos = 0f
    @Prop("Rotation [*R]", i=2) override var xRot = 0f; override var yRot = 0f; override var zRot = 0f

    @EntityRef(PhysicsBodyEntity3D::class)
    @Prop("Connection", i=1) var bodyAId = INVALID_ID

    @EntityRef(PhysicsBodyEntity3D::class)
    @Prop("Connection", i=2) var bodyBId = INVALID_ID
    @Prop("Connection", i=3) var collideConnected = false

    @Prop("Anchor B Offset", i=1) var xAnchorB = 0f
    @Prop("Anchor B Offset", i=2) var yAnchorB = 0f
    @Prop("Anchor B Offset", i=3) var zAnchorB = 1f

    @Prop("Distance", i=1, min=0.001f) var restLength = 1f

    @Prop("Limits", i=1)                 var limitEnabled = false
    @Prop("Limits", i=2, min=0f)         var minLength = 0f
    @Prop("Limits", i=3, min=0.001f)     var maxLength = 1f
    @Prop("Spring", i=1)                 var springEnabled = false
    @Prop("Spring", i=2, min=0f)         var springHertz = 4f
    @Prop("Spring", i=3, min=0f)         var springDampingRatio = 0.7f

    @get:JsonIgnore override val bodyAEntityId get() = bodyAId
    @get:JsonIgnore override val bodyBEntityId get() = bodyBId

    override fun createPhysicsJointDefinition(
        localPosA: Vector3fc,
        localRotA: Quaternionfc,
        localPosB: Vector3fc,
        localRotB: Quaternionfc
    ): Box3DDistanceJointDefinition {
        val anchorBOffset = localRotB.transform(Vector3f(xAnchorB, yAnchorB, zAnchorB))
        val anchorB = anchorBOffset.add(localPosB)
        return Box3DDistanceJointDefinition(
            localPositionA = localPosA,
            localRotationA = localRotA,
            localPositionB = anchorB,
            localRotationB = localRotB,
            collideConnected = collideConnected,
            length = restLength,
            enableSpring = springEnabled,
            springHertz = springHertz,
            springDampingRatio = springDampingRatio,
            enableLimit = limitEnabled,
            minLength = minLength,
            maxLength = maxLength
        )
    }

    override fun physicsPropertyHash(): Int
    {
        var hash = bodyAId.hashCode()
        hash = 31 * hash + bodyBId.hashCode(); hash = 31 * hash + collideConnected.hashCode()
        hash = 31 * hash + xPos.toBits(); hash = 31 * hash + yPos.toBits(); hash = 31 * hash + zPos.toBits()
        hash = 31 * hash + xRot.toBits(); hash = 31 * hash + yRot.toBits(); hash = 31 * hash + zRot.toBits()
        hash = 31 * hash + xAnchorB.toBits(); hash = 31 * hash + yAnchorB.toBits(); hash = 31 * hash + zAnchorB.toBits()
        hash = 31 * hash + restLength.toBits(); hash = 31 * hash + limitEnabled.hashCode()
        hash = 31 * hash + minLength.toBits(); hash = 31 * hash + maxLength.toBits()
        hash = 31 * hash + springEnabled.hashCode(); hash = 31 * hash + springHertz.toBits()
        hash = 31 * hash + springDampingRatio.toBits()
        return hash
    }
}
