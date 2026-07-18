package no.njoh.pulseengine.modules.physics3d.entities.bodies

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.CursorMode.GRABBED
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.scene.interfaces.Initiable
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.interfaces.Updatable
import no.njoh.pulseengine.core.shared.annotations.EntityRef
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.toDegrees
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.physics3d.PhysicsBodyType3D
import no.njoh.pulseengine.modules.physics3d.PhysicsBodyType3D.DYNAMIC
import no.njoh.pulseengine.modules.physics3d.CapsuleGeometry3D
import no.njoh.pulseengine.modules.physics3d.PhysicsBody3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DShapeDefinition
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBodyDefinition
import no.njoh.pulseengine.modules.scene.entities.Camera3D
import no.njoh.pulseengine.modules.scene.entities.Camera3D.RotationMode.YAW_PITCH
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * Dynamic capsule character with keyboard, mouse-look, sprinting, and grounded jumping.
 */
@Name("3D Physics Character")
@Icon("PERSON", size = 24f, showInViewport = true)
class PhysicsCharacter3D : SceneEntity(), Initiable, Updatable, PhysicsBodyEntity3D, Named
{
    override var name = "Physics Character"
    var enabled = true

    @Prop("Capsule", i=1, min=.01f) var capsuleRadius = 0.4f
    @Prop("Capsule", i=2, min=.02f) var capsuleHeight = 1.8f

    @Prop("Position [*P]", i=1)         override var xPos   = 0f; override var yPos   = 1f; override var zPos   = 0f
    @Prop("Rotation [*R]", i=2)         override var xRot   = 0f; override var yRot   = 0f; override var zRot   = 0f
    @Prop("Scale [*S]", i=3, min=.001f) override var xScale = 1f; override var yScale = 1f; override var zScale = 1f

    @Prop("Physics",   i=0)                 var bodyType       = DYNAMIC
    @Prop("Physics",   i=1, min=0f)         var density        = 1f
    @Prop("Physics",   i=2, min=0f)         var friction       = 0.6f
    @Prop("Physics",   i=3, min=0f, max=1f) var restitution    = 0f
    @Prop("Physics",   i=4, min=0f)         var linearDamping  = 0f
    @Prop("Physics",   i=5, min=0f)         var angularDamping = 0f
    @Prop("Physics",   i=6)                 var gravityScale   = 1f

    @Prop("Collision", i=0) var collisionMask = -1
    @Prop("Collision", i=1) var layerMask     = 1
    @Prop("Collision", i=2) var sensor        = false

    @EntityRef(Camera3D::class)
    @Prop("Camera", i=1)         var cameraId         = INVALID_ID
    @Prop("Camera", i=2)         var eyeOffset        = 0.7f
    @Prop("Camera", i=3, min=0f) var mouseSensitivity = 0.1f

    @Prop("Movement", i=1, min=0f)          var walkSpeed          = 5f
    @Prop("Movement", i=2, min=0f)          var sprintSpeed        = 8f
    @Prop("Movement", i=3, min=0f)          var groundAcceleration = 50f
    @Prop("Movement", i=4, min=0f)          var airAcceleration    = 12f
    @Prop("Movement", i=5, min=0f)          var jumpVelocity       = 5f
    @Prop("Movement", i=6, min=0f, max=89f) var maxGroundAngle     = 50f

    @Prop("Jumping", i=1, min=0f) var jumpBufferTime = 0.1f
    @Prop("Jumping", i=2, min=0f) var coyoteTime     = 0.1f

    @Prop("Input", i=1) var forwardKey  = Key.W
    @Prop("Input", i=2) var backwardKey = Key.S
    @Prop("Input", i=3) var leftKey     = Key.A
    @Prop("Input", i=4) var rightKey    = Key.D
    @Prop("Input", i=5) var sprintKey   = Key.LEFT_SHIFT
    @Prop("Input", i=6) var jumpKey     = Key.SPACE

    private val tmpDesiredVelocity  = Vector3f()
    private val tmpCurrentVelocity  = Vector3f()
    private val tmpCameraPosition   = Vector3f()
    private val tmpEulerRotation    = Vector3f()
    private var jumpBufferRemaining = 0f
    private var coyoteRemaining     = 0f
    private var grounded            = false
    private var pitch               = 0f
    private var yaw                 = 0f

    private val currentPosition         = Vector3f()
    private val currentRotation         = Quaternionf()
    private val previousPosition        = Vector3f()
    private val previousRotation        = Quaternionf()
    private val synchronizedPosition    = Vector3f()
    private val synchronizedRotation    = Vector3f()
    private val physicsBodyDefinition   = Box3DBodyDefinition()
    private val physicsCapsuleGeometry  = CapsuleGeometry3D(radius = capsuleRadius)
    private val physicsShapeDefinition  = Box3DShapeDefinition(physicsCapsuleGeometry)
    private val physicsShapeDefinitions = listOf(physicsShapeDefinition)

    override fun onStart(engine: PulseEngine)
    {
        updateCamera(engine)
    }

    override fun onUpdate(engine: PulseEngine)
    {
        if (!enabled) return

        jumpBufferRemaining = max(0f, jumpBufferRemaining - engine.data.deltaTime)
        coyoteRemaining = max(0f, coyoteRemaining - engine.data.deltaTime)

        engine.input.setCursorMode(GRABBED)

        yaw -= engine.input.xdMouse * mouseSensitivity
        pitch = (pitch - engine.input.ydMouse * mouseSensitivity).coerceIn(-89f, 89f)

        var moveX = 0f
        var moveZ = 0f
        if (engine.input.isPressed(forwardKey))  moveZ += 1f
        if (engine.input.isPressed(backwardKey)) moveZ -= 1f
        if (engine.input.isPressed(rightKey))    moveX += 1f
        if (engine.input.isPressed(leftKey))     moveX -= 1f

        val length = sqrt(moveX * moveX + moveZ * moveZ)
        if (length > 1f)
        {
            moveX /= length
            moveZ /= length
        }

        val speed = if (engine.input.isPressed(sprintKey)) sprintSpeed else walkSpeed
        val yawRadians = yaw.toRadians()
        val sinYaw = sin(yawRadians)
        val cosYaw = cos(yawRadians)

        tmpDesiredVelocity.set(
            (moveX * cosYaw - moveZ * sinYaw) * speed,
            0f,
            (-moveX * sinYaw - moveZ * cosYaw) * speed
        )

        if (engine.input.wasClicked(jumpKey))
            jumpBufferRemaining = jumpBufferTime

        updateCamera(engine)
    }

    override fun onFixedUpdate(engine: PulseEngine) { }

    override fun onPhysicsFixedUpdate(engine: PulseEngine, body: PhysicsBody3D)
    {
        grounded = body.hasContactAlong(DOWN, cos(maxGroundAngle.toRadians()))
        if (grounded)
            coyoteRemaining = coyoteTime
        
        val acceleration = if (grounded) groundAcceleration else airAcceleration
        val maxVelocityChange = acceleration * engine.data.fixedDeltaTime

        body.getLinearVelocity(tmpCurrentVelocity)
        tmpCurrentVelocity.x = moveTowards(tmpCurrentVelocity.x, tmpDesiredVelocity.x, maxVelocityChange)
        tmpCurrentVelocity.z = moveTowards(tmpCurrentVelocity.z, tmpDesiredVelocity.z, maxVelocityChange)

        if (jumpBufferRemaining > 0f && (grounded || coyoteRemaining > 0f))
        {
            tmpCurrentVelocity.y = jumpVelocity
            jumpBufferRemaining = 0f
            coyoteRemaining = 0f
            grounded = false
        }

        body.setLinearVelocity(tmpCurrentVelocity)
    }

    override fun onPhysicsBodyCreated(engine: PulseEngine, body: PhysicsBody3D)
    {
        currentPosition.set(xPos, yPos, zPos)
        currentRotation.rotationXYZ(xRot.toRadians(), yRot.toRadians(), zRot.toRadians())
        previousPosition.set(currentPosition)
        previousRotation.set(currentRotation)

        recordSynchronizedTransform()
    }

    override fun onPhysicsTransformUpdated(position: Vector3fc, rotation: Quaternionfc)
    {
        previousPosition.set(currentPosition)
        previousRotation.set(currentRotation)

        currentPosition.set(position)
        currentRotation.set(rotation)
        currentRotation.getEulerAnglesXYZ(tmpEulerRotation)

        xPos = currentPosition.x
        yPos = currentPosition.y
        zPos = currentPosition.z
        xRot = tmpEulerRotation.x.toDegrees()
        yRot = tmpEulerRotation.y.toDegrees()
        zRot = tmpEulerRotation.z.toDegrees()

        recordSynchronizedTransform()
    }

    override fun onExternalTransformApplied(position: Vector3fc, rotation: Quaternionfc)
    {
        previousPosition.set(position)
        currentPosition.set(position)
        previousRotation.set(rotation)
        currentRotation.set(rotation)

        recordSynchronizedTransform()
    }

    override fun hasPendingTransformChange() =
        xPos != synchronizedPosition.x || yPos != synchronizedPosition.y || zPos != synchronizedPosition.z ||
        xRot != synchronizedRotation.x || yRot != synchronizedRotation.y || zRot != synchronizedRotation.z

    override fun getPhysicsBodyDefinition() = physicsBodyDefinition.also()
    {
        it.position.set(xPos, yPos, zPos)
        it.rotation.rotationXYZ(xRot.toRadians(), yRot.toRadians(), zRot.toRadians())
        it.type               = bodyType
        it.linearDamping      = linearDamping
        it.angularDamping     = angularDamping
        it.gravityScale       = gravityScale
        it.lockYRotation      = true
    }

    override fun getPhysicsShapeDefinitions(engine: PulseEngine): List<Box3DShapeDefinition>
    {
        val scaledRadius = capsuleRadius * max(abs(xScale), abs(zScale))
        val scaledHeight = max(capsuleHeight * abs(yScale), scaledRadius * 2f)
        val segmentHalfHeight = scaledHeight * 0.5f - scaledRadius

        physicsCapsuleGeometry.point1.set(0f, -segmentHalfHeight, 0f)
        physicsCapsuleGeometry.point2.set(0f,  segmentHalfHeight, 0f)
        physicsCapsuleGeometry.radius = scaledRadius

        physicsShapeDefinition.density = if (bodyType == PhysicsBodyType3D.STATIC) 0f else density
        physicsShapeDefinition.friction = friction
        physicsShapeDefinition.restitution = restitution
        physicsShapeDefinition.categoryBits = layerMask.toLong() and 0xffffffffL
        physicsShapeDefinition.maskBits = collisionMask.toLong() and 0xffffffffL
        physicsShapeDefinition.sensor = sensor

        return physicsShapeDefinitions
    }

    private fun updateCamera(engine: PulseEngine)
    {
        val camera = engine.scene.getEntityOfType<Camera3D>(cameraId)
            ?: engine.scene.getFirstEntityOfType<Camera3D> { it.active && it.isNot(HIDDEN) }
            ?: return

        camera.targetEntityId = INVALID_ID
        camera.rotationMode   = YAW_PITCH

        if (engine.scene.state == SceneState.RUNNING)
        {
            val i = engine.data.interpolation
            previousPosition.lerp(currentPosition, i, tmpCameraPosition)
        }
        else tmpCameraPosition.set(xPos, yPos, zPos)

        camera.xPos = tmpCameraPosition.x
        camera.yPos = tmpCameraPosition.y + eyeOffset * abs(yScale)
        camera.zPos = tmpCameraPosition.z
        camera.xRot = pitch
        camera.yRot = yaw
        camera.zRot = 0f
        camera.applyTo(engine.gfx.mainCamera, engine.window.width, engine.window.height)
    }

    private fun moveTowards(current: Float, target: Float, maxChange: Float): Float
    {
        val difference = target - current
        return if (abs(difference) <= maxChange) target else current + maxChange * if (difference < 0f) -1f else 1f
    }

    private fun recordSynchronizedTransform()
    {
        synchronizedPosition.set(xPos, yPos, zPos)
        synchronizedRotation.set(xRot, yRot, zRot)
    }

    companion object
    {
        private val DOWN = Vector3f(0f, -1f, 0f)
    }
}