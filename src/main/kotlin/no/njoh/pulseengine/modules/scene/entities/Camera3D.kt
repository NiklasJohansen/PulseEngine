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
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.toDegrees
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Icon("CAMERA", size = 24f, showInViewport = true)
class Camera3D : SceneEntity(), Initiable, Updatable, Named, Translatable3D, Rotatable3D
{
    @Prop(i=0) override var name = "Camera"
    @Prop(i=1)          var active = true

    @Prop("Position [*P]", i=1) override var xPos = 0f; override var yPos = 1.5f; override var zPos = 5f
    @Prop("Rotation [*R]", i=2) override var xRot = 0f; override var yRot = 0f;   override var zRot = 0f

    @Prop("Projection", i=1, min=1f, max = 179f) var fov       = 90f
    @Prop("Projection", i=2, min=0.001f)         var nearPlane = 0.01f
    @Prop("Projection", i=3, min=0.002f)         var farPlane  = 200f

    @EntityRef
    @Prop("Tracking", i=1)                 var targetEntityId = INVALID_ID
    @Prop("Tracking", i=2)                 var trackRotation = false
    @Prop("Tracking", i=3, min=0f, max=1f) var smoothing     = 0.1f

    private var trackedEntityId = INVALID_ID
    private val quaternionRot = Quaternionf()
    private val eulerRot = Vector3f()

    override fun onStart(engine: PulseEngine)
    {
        updateCamera(engine)
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        updateCamera(engine)
    }

    override fun onUpdate(engine: PulseEngine) { }

    private fun updateCamera(engine: PulseEngine)
    {
        if (!active) return

        engine.scene.getEntityOfType<Translatable3D>(targetEntityId)?.let() 
        {
            updateTracking(it, engine.scene.getEntityOfType<Rotatable3D>(targetEntityId))
        }

        applyTo(engine.gfx.mainCamera, engine.window.width, engine.window.height)
    }

    internal fun applyTo(camera: Camera, width: Int, height: Int)
    {
        val near = nearPlane.coerceAtLeast(0.001f)
        val cameraRotation = quaternionRot
            .rotationXYZ(xRot.toRadians(), yRot.toRadians(), zRot.toRadians())
            .getEulerAnglesYXZ(eulerRot)
        camera.position.set(xPos, yPos, zPos)
        camera.rotation.set(cameraRotation)
        camera.origin.zero()
        camera.scale.set(1f)
        camera.fov = fov.coerceIn(1f, 179f)
        camera.nearPlane = near
        camera.farPlane = farPlane.coerceAtLeast(near + 0.001f)
        camera.updateProjection(width, height, PERSPECTIVE_3D)
    }

    internal fun updateTracking(target: Translatable3D, targetRotation: Rotatable3D? = null)
    {
        val snapToTarget = trackedEntityId != targetEntityId
        trackedEntityId = targetEntityId
        val factor = if (snapToTarget) 1f else smoothing.coerceIn(0f, 1f)

        xPos += (target.xPos - xPos) * factor
        yPos += (target.yPos - yPos) * factor
        zPos += (target.zPos - zPos) * factor

        if (trackRotation && targetRotation != null)
        {
            xRot = interpolateAngle(xRot, targetRotation.xRot, factor)
            yRot = interpolateAngle(yRot, targetRotation.yRot, factor)
            zRot = interpolateAngle(zRot, targetRotation.zRot, factor)
        }
    }

    private fun interpolateAngle(current: Float, target: Float, factor: Float): Float
    {
        val difference = (target - current).toRadians()
        return normalizeAngle(current + atan2(sin(difference), cos(difference)).toDegrees() * factor)
    }

    private fun normalizeAngle(angle: Float) = ((angle + 180f) % 360f + 360f) % 360f - 180f
}