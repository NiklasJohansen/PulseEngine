package no.njoh.pulseengine.modules.physics3d.entities.joints

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.annotations.EntityRef
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DRevoluteJointDefinition
import no.njoh.pulseengine.modules.physics3d.entities.bodies.PhysicsBodyEntity3D

/**
 * A one-axis revolute rotating around local Z, with optional limits, motor, and spring.
 */
@Name("3D Physics Revolute Joint")
@Icon("SHAPES", size = 24f, showInViewport = true)
open class RevoluteJoint3D : SceneEntity(), Named, PhysicsJointEntity3D
{
    @Prop(i=0) override var name = "Revolute Joint"

    @Prop("Position [*P]", i=1) override var xPos = 0f; override var yPos = 0f; override var zPos = 0f
    @Prop("Rotation [*R]", i=2) override var xRot = 0f; override var yRot = 0f; override var zRot = 0f

    @EntityRef(PhysicsBodyEntity3D::class)
    @Prop("Connection", i=1) var bodyAId = INVALID_ID

    @EntityRef(PhysicsBodyEntity3D::class)
    @Prop("Connection", i=2) var bodyBId = INVALID_ID

    @Prop("Connection", i=3) var collideConnected = false

    @Prop("Limits", i=1)                           var limitEnabled = false
    @Prop("Limits", i=2, min=-178.2f, max=178.2f) var lowerAngle = -90f
    @Prop("Limits", i=3, min=-178.2f, max=178.2f) var upperAngle = 90f

    @Prop("Motor", i=1)         var motorEnabled   = false
    @Prop("Motor", i=2)         var motorSpeed     = 0f
    @Prop("Motor", i=3, min=0f) var maxMotorTorque = 100f

    @Prop("Spring", i=1)         var springEnabled      = false
    @Prop("Spring", i=2)         var springTargetAngle  = 0f
    @Prop("Spring", i=3, min=0f) var springHertz        = 4f
    @Prop("Spring", i=4, min=0f) var springDampingRatio = 0.7f

    @get:JsonIgnore override val bodyAEntityId get() = bodyAId
    @get:JsonIgnore override val bodyBEntityId get() = bodyBId
    @JsonIgnore private val definition = Box3DRevoluteJointDefinition()

    override fun getPhysicsJointDefinition(): Box3DRevoluteJointDefinition
    {
        definition.worldPosA.set(xPos, yPos, zPos)
        definition.worldRotA.rotationXYZ(xRot.toRadians(), yRot.toRadians(), zRot.toRadians())
        definition.worldPosB.set(definition.worldPosA)
        definition.worldRotB.set(definition.worldRotA)
        definition.collision = collideConnected
        definition.enableMotor = motorEnabled
        definition.motorSpeed = motorSpeed
        definition.maxMotorTorque = maxMotorTorque
        definition.enableSpring = springEnabled
        definition.targetAngle = springTargetAngle.toRadians()
        definition.springHertz = springHertz
        definition.springDampingRatio = springDampingRatio
        definition.enableLimit = limitEnabled
        definition.lowerAngle = lowerAngle.toRadians()
        definition.upperAngle = upperAngle.toRadians()
        return definition
    }
}
