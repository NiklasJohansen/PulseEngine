package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.modules.scene.systems.ConicalLight3D
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.interfaces.Rotatable3D
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import org.joml.Matrix4f
import org.joml.Vector3f

class SpotLight3D : SceneEntity(), Named, ConicalLight3D, Rotatable3D
{
    @Prop(i=0) override var name = "Spot Light"

    @Prop("Position [*P]", i=1) override var xPos=0f;   override var yPos=1f; override var zPos=0f
    @Prop("Rotation [*R]", i=2) override var xRot=-90f; override var yRot=0f; override var zRot=0f

    @Prop("Lighting", i=1)                            var color          = Color(1f, 1f, 1f)
    @Prop("Lighting", i=2, min=0f)                    var intensity      = 4f
    @Prop("Lighting", i=3, min=0f)           override var radius         = 10f
    @Prop("Lighting", i=4, min=0f, max=89f)  override var outerConeAngle = 40f
    @Prop("Lighting", i=5, min=0f, max=89f)           var innerConeAngle = 0f

    @Prop("Shadow", i=7)          var shadowEnabled    = false
    @Prop("Shadow", i=8, min=64f) var shadowResolution = 512
    @Prop("Shadow", i=9, min=0f)  var shadowBias       = 0.005f
    @Prop("Shadow", i=10, min=0f) var shadowImportance = 1f

    private val position       = Vector3f()
    private val direction      = Vector3f()
    private val rotation       = Matrix4f()
    private val intensityColor = Color()

    override fun onRenderLight(engine: PulseEngine, context: SceneRenderContext)
    {
        position.set(xPos, yPos, zPos)
        getDirection(direction)
        intensityColor.setFrom(color).multiplyRgb(intensity)
        val outerAngle = outerConeAngle.coerceIn(0f, 89f)
        val innerAngle = innerConeAngle.coerceIn(0f, outerAngle)
        context.submitSpotLight(position, direction, radius, intensityColor, innerAngle, outerAngle, shadowEnabled, shadowResolution, shadowBias, shadowImportance, id)
    }

    override fun getDirection(out: Vector3f): Vector3f
    {
        rotation.identity().rotateXYZ(xRot.toRadians(), yRot.toRadians(), zRot.toRadians())
        return rotation.transformDirection(out.set(0f, 0f, -1f)).normalize()
    }
}