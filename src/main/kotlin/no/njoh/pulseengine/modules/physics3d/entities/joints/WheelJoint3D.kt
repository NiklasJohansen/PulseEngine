package no.njoh.pulseengine.modules.physics3d.entities.joints

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.interfaces.Rotatable3D
import no.njoh.pulseengine.core.scene.interfaces.Translatable3D
import no.njoh.pulseengine.core.shared.annotations.EntityRef
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DWheelJointDefinition
import no.njoh.pulseengine.modules.physics3d.entities.bodies.PhysicsBodyEntity3D
import org.joml.Vector3f

@Name("3D Physics Wheel Joint")
@Icon("SHAPES", size = 24f, showInViewport = true)
open class WheelJoint3D : SceneEntity(), Named, Translatable3D, Rotatable3D, PhysicsJointEntity3D
{
    @Prop(i=0) override var name = "Wheel Joint"

    @Prop("Transform", i=1) override var position = Vector3f()
    @Prop("Transform", i=2) override var rotation = Vector3f()

    @Prop("Connection", i=1) @EntityRef(PhysicsBodyEntity3D::class) var chassisBodyId = INVALID_ID
    @Prop("Connection", i=2) @EntityRef(PhysicsBodyEntity3D::class) var wheelBodyId   = INVALID_ID
    @Prop("Connection", i=3) var collideConnected = false

    @Prop("Suspension", i=1)         var suspensionEnabled      = true
    @Prop("Suspension", i=2, min=0f) var suspensionHertz        = 4f
    @Prop("Suspension", i=3, min=0f) var suspensionDampingRatio = 0.7f
    @Prop("Suspension", i=4)         var suspensionLimitEnabled = false
    @Prop("Suspension", i=5)         var lowerSuspensionLimit   = -0.25f
    @Prop("Suspension", i=6)         var upperSuspensionLimit   = 0.25f

    @Prop("Drive", i=1)              var motorEnabled  = false
    @Prop("Drive", i=2)              var motorSpeed    = 0f
    @Prop("Drive", i=3, min=0f)      var maxMotorTorque= 300f

    @Prop("Steering", i=1)           var steeringEnabled      = false
    @Prop("Steering", i=2, min=0f)   var steeringHertz        = 5f
    @Prop("Steering", i=3, min=0f)   var steeringDampingRatio = 0.7f
    @Prop("Steering", i=4)           var targetSteeringAngle  = 0f
    @Prop("Steering", i=5, min=0f)   var maxSteeringTorque    = 300f
    @Prop("Steering", i=6)           var steeringLimitEnabled = false
    @Prop("Steering", i=7)           var lowerSteeringLimit   = -35f
    @Prop("Steering", i=8)           var upperSteeringLimit   = 35f

    @JsonIgnore private val definition = Box3DWheelJointDefinition()

    @get:JsonIgnore override val bodyAEntityId get() = chassisBodyId
    @get:JsonIgnore override val bodyBEntityId get() = wheelBodyId

    override fun getPhysicsJointDefinition(): Box3DWheelJointDefinition
    {
        definition.worldPosA.set(position)
        definition.worldRotA.rotationXYZ(rotation.x.toRadians(), rotation.y.toRadians(), rotation.z.toRadians())
        definition.worldPosB.set(definition.worldPosA)
        definition.worldRotB.set(definition.worldRotA)
        definition.collision = collideConnected
        definition.enableSuspension = suspensionEnabled
        definition.suspensionHertz = suspensionHertz
        definition.suspensionDampingRatio = suspensionDampingRatio
        definition.enableSuspensionLimit = suspensionLimitEnabled
        definition.lowerSuspensionLimit = lowerSuspensionLimit
        definition.upperSuspensionLimit = upperSuspensionLimit
        definition.enableSpinMotor = motorEnabled
        definition.maxSpinTorque = maxMotorTorque
        definition.spinSpeed = motorSpeed
        definition.enableSteering = steeringEnabled
        definition.steeringHertz = steeringHertz
        definition.steeringDampingRatio = steeringDampingRatio
        definition.targetSteeringAngle = targetSteeringAngle.toRadians()
        definition.maxSteeringTorque = maxSteeringTorque
        definition.enableSteeringLimit = steeringLimitEnabled
        definition.lowerSteeringLimit = lowerSteeringLimit.toRadians()
        definition.upperSteeringLimit = upperSteeringLimit.toRadians()
        return definition
    }
}