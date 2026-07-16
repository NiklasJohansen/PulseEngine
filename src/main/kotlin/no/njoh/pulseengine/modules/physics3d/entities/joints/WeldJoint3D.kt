package no.njoh.pulseengine.modules.physics3d.entities.joints

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.annotations.EntityRef
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DWeldJointDefinition
import no.njoh.pulseengine.modules.physics3d.entities.bodies.PhysicsBodyEntity3D

/**
 * Locks two physics bodies together, with optional linear and angular softness.
 */
@Name("3D Physics Weld Joint")
@Icon("SHAPES", size = 24f, showInViewport = true)
open class WeldJoint3D : SceneEntity(), Named, PhysicsJointEntity3D
{
    @Prop(i=0) override var name = "Weld Joint"
    @Prop("Position [*P]", i=1) override var xPos = 0f; override var yPos = 0f; override var zPos = 0f
    @Prop("Rotation [*R]", i=2) override var xRot = 0f; override var yRot = 0f; override var zRot = 0f

    @EntityRef(PhysicsBodyEntity3D::class)
    @Prop("Connection", i=1) var bodyAId = INVALID_ID

    @EntityRef(PhysicsBodyEntity3D::class)
    @Prop("Connection", i=2) var bodyBId = INVALID_ID

    @Prop("Connection", i=3) var collideConnected = false
    @Prop("Softness", i=1)         var softnessEnabled     = false
    @Prop("Softness", i=2, min=0f) var linearHertz         = 4f
    @Prop("Softness", i=3, min=0f) var linearDampingRatio  = 0.7f
    @Prop("Softness", i=4, min=0f) var angularHertz        = 4f
    @Prop("Softness", i=5, min=0f) var angularDampingRatio = 0.7f

    @get:JsonIgnore override val bodyAEntityId get() = bodyAId
    @get:JsonIgnore override val bodyBEntityId get() = bodyBId
    @JsonIgnore private val definition = Box3DWeldJointDefinition()

    override fun getPhysicsJointDefinition(): Box3DWeldJointDefinition
    {
        definition.worldPosA.set(xPos, yPos, zPos)
        definition.worldRotA.rotationXYZ(xRot.toRadians(), yRot.toRadians(), zRot.toRadians())
        definition.worldPosB.set(definition.worldPosA)
        definition.worldRotB.set(definition.worldRotA)
        definition.collision = collideConnected
        definition.linearHertz = if (softnessEnabled) linearHertz else 0f
        definition.linearDampingRatio = linearDampingRatio
        definition.angularHertz = if (softnessEnabled) angularHertz else 0f
        definition.angularDampingRatio = angularDampingRatio
        return definition
    }
}