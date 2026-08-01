package no.njoh.pulseengine.modules.physics3d.entities.joints

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.annotations.EntityRef
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DDistanceJointDefinition
import no.njoh.pulseengine.modules.physics3d.entities.bodies.PhysicsBodyEntity3D
import org.joml.Vector3f

/**
 * Connects anchor A at the entity origin to anchor B at an editable local offset.
 */
@Name("3D Physics Distance Joint")
@Icon("SHAPES", size = 24f, showInViewport = true)
open class DistanceJoint3D : SceneEntity(), Named, PhysicsJointEntity3D
{
    @Prop(i=0) override var name = "Distance Joint"
    @Prop("Transform", i=1) override var position = Vector3f()
    @Prop("Transform", i=2) override var rotation = Vector3f()

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
    @JsonIgnore private val definition = Box3DDistanceJointDefinition()

    override fun getPhysicsJointDefinition(): Box3DDistanceJointDefinition
    {
        definition.worldPosA.set(position)
        definition.worldRotA.rotationXYZ(rotation.x.toRadians(), rotation.y.toRadians(), rotation.z.toRadians())
        definition.worldPosB.set(xAnchorB, yAnchorB, zAnchorB)
        definition.worldRotA.transform(definition.worldPosB)
        definition.worldPosB.add(definition.worldPosA)
        definition.worldRotB.set(definition.worldRotA)
        definition.collision = collideConnected
        definition.length = restLength
        definition.enableSpring = springEnabled
        definition.springHertz = springHertz
        definition.springDampingRatio = springDampingRatio
        definition.enableLimit = limitEnabled
        definition.minLength = minLength
        definition.maxLength = maxLength
        return definition
    }
}
