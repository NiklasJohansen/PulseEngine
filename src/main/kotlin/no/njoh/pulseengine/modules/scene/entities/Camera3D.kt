package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.camera.Camera
import no.njoh.pulseengine.core.graphics.camera.CameraProjectionType.PERSPECTIVE_3D
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Initiable
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.interfaces.Rotatable3D
import no.njoh.pulseengine.core.scene.interfaces.Translatable3D
import no.njoh.pulseengine.core.scene.interfaces.Updatable
import no.njoh.pulseengine.core.shared.annotations.EntityRef
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.toDegrees
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.scene.entities.Camera3D.RotationMode.*
import org.joml.Quaternionf
import org.joml.Vector3f

@Name("3D Camera")
@Icon("CAMERA", size = 24f, showInViewport = true)
open class Camera3D : SceneEntity(), Initiable, Updatable, Named, Translatable3D, Rotatable3D
{
    @Prop(i=0) override var name = "Camera"
    @Prop(i=1)          var active = true

    @Prop("Transform", i=1) override var position = Vector3f(0f, 1.5f, 5f)
    @Prop("Transform", i=2) override var rotation = Vector3f()
    @Prop("Rotation",  i=3) var rotationMode = XYZ

    @Prop("Projection", i=1, min=1f, max = 179f) var fov       = 90f
    @Prop("Projection", i=2, min=0.001f)         var nearPlane = 0.01f
    @Prop("Projection", i=3, min=0.002f)         var farPlane  = 500f

    @EntityRef
    @Prop("Tracking", i=1)                 var targetEntityId = INVALID_ID
    @Prop("Tracking", i=2)                 var trackRotation = false
    @Prop("Tracking", i=3, min=0f, max=1f) var smoothing     = 0.1f

    private var trackedEntityId = INVALID_ID
    private val tmpQuaternionRot = Quaternionf()
    private val tmpEulerRot = Vector3f()
    private val deltaPos = Vector3f()
    private val rotatedDeltaPos = Vector3f()
    private val rotationOffset = Quaternionf()
    private val currentTrackingRotation = Quaternionf()
    private val targetTrackingRotation = Quaternionf()
    private var positionOffsetTargetId = INVALID_ID
    private var rotationOffsetTargetId = INVALID_ID

    override fun onStart(engine: PulseEngine)
    {
        val targetPosition = engine.scene.getEntityOfType<Translatable3D>(targetEntityId)
        val targetRotation = engine.scene.getEntityOfType<Rotatable3D>(targetEntityId)

        positionOffsetTargetId = INVALID_ID
        targetPosition?.let { capturePositionOffset(it, targetRotation) }

        rotationOffset.identity()
        rotationOffsetTargetId = INVALID_ID
        targetRotation?.let(::captureRotationOffset)

        updateCamera(engine)
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        updateCamera(engine)
    }

    override fun onUpdate(engine: PulseEngine) { }

    private fun updateCamera(engine: PulseEngine)
    {
        if (!active || isSet(HIDDEN)) return

        engine.scene.getEntityOfType<Translatable3D>(targetEntityId)?.let() 
        {
            updateTracking(it, engine.scene.getEntityOfType<Rotatable3D>(targetEntityId))
        }

        applyTo(engine.gfx.mainCamera, engine.window.width, engine.window.height)
    }

    fun applyTo(camera: Camera, width: Int, height: Int)
    {
        val near = nearPlane.coerceAtLeast(0.001f)
        
        val eulerRotation = when (rotationMode)
        {
            XYZ -> tmpQuaternionRot.rotationXYZ(rotation.x.toRadians(), rotation.y.toRadians(), rotation.z.toRadians()).getEulerAnglesYXZ(tmpEulerRot)
            YAW_PITCH -> tmpEulerRot.set(rotation.x.toRadians(), rotation.y.toRadians(), rotation.z.toRadians())
        }

        camera.rotation.set(eulerRotation)
        camera.position.set(position)
        camera.origin.zero()
        camera.scale.set(1f)
        camera.fov = fov.coerceIn(1f, 179f)
        camera.nearPlane = near
        camera.farPlane = farPlane.coerceAtLeast(near + 0.001f)
        camera.updateProjection(width, height, PERSPECTIVE_3D)
    }

    open fun updateTracking(target: Translatable3D, targetRotation: Rotatable3D? = null)
    {
        val snapToTarget = trackedEntityId != targetEntityId
        trackedEntityId = targetEntityId
        val factor = if (snapToTarget) 1f else 1f - smoothing.coerceIn(0f, 1f)

        val positionOffset = if (positionOffsetTargetId == targetEntityId && targetRotation != null)
        {
            targetRotation.toQuaternion(targetTrackingRotation).transform(deltaPos, rotatedDeltaPos)
        }
        else deltaPos

        position.x += (target.position.x + positionOffset.x - position.x) * factor
        position.y += (target.position.y + positionOffset.y - position.y) * factor
        position.z += (target.position.z + positionOffset.z - position.z) * factor

        if (trackRotation && targetRotation != null)
        {
            targetRotation.toQuaternion(targetTrackingRotation)
            if (rotationOffsetTargetId == targetEntityId)
                targetTrackingRotation.mul(rotationOffset)

            cameraRotationTo(currentTrackingRotation)
            currentTrackingRotation.slerp(targetTrackingRotation, factor).normalize()
            applyCameraRotation(currentTrackingRotation)
        }
    }

    private fun capturePositionOffset(target: Translatable3D, targetRotation: Rotatable3D?)
    {
        deltaPos.set(position).sub(target.position)
        positionOffsetTargetId = INVALID_ID
        if (targetRotation != null)
        {
            targetRotation.toQuaternion(targetTrackingRotation).conjugate().transform(deltaPos)
            positionOffsetTargetId = targetEntityId
        }
    }

    private fun captureRotationOffset(targetRotation: Rotatable3D)
    {
        targetRotation.toQuaternion(targetTrackingRotation)
        cameraRotationTo(currentTrackingRotation)
        rotationOffset.set(targetTrackingRotation).conjugate().mul(currentTrackingRotation).normalize()
        rotationOffsetTargetId = targetEntityId
    }

    private fun Rotatable3D.toQuaternion(dst: Quaternionf) = dst.rotationXYZ(
        rotation.x.toRadians(), rotation.y.toRadians(), rotation.z.toRadians()
    )

    private fun cameraRotationTo(dst: Quaternionf) = when (rotationMode)
    {
        XYZ -> dst.rotationXYZ(rotation.x.toRadians(), rotation.y.toRadians(), rotation.z.toRadians())
        YAW_PITCH -> dst.rotationYXZ(rotation.y.toRadians(), rotation.x.toRadians(), rotation.z.toRadians())
    }

    private fun applyCameraRotation(rotation: Quaternionf)
    {
        when (rotationMode)
        {
            XYZ -> rotation.getEulerAnglesXYZ(tmpEulerRot)
            YAW_PITCH -> rotation.getEulerAnglesYXZ(tmpEulerRot)
        }
        this.rotation.set(
            normalizeAngle(tmpEulerRot.x.toDegrees()),
            normalizeAngle(tmpEulerRot.y.toDegrees()),
            normalizeAngle(tmpEulerRot.z.toDegrees())
        )
    }

    private fun normalizeAngle(angle: Float) = ((angle + 180f) % 360f + 360f) % 360f - 180f

    enum class RotationMode
    {
        XYZ,
        YAW_PITCH
    }
}
