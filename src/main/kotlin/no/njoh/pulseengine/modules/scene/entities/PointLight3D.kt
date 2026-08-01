package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.modules.scene.systems.Light3D
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color
import org.joml.Vector3f

@Name("3D Point Light")
class PointLight3D : SceneEntity(), Light3D, Named
{
    @Prop(i=0) override var name = "Point Light"

    @Prop("Transform", i=1) override var position = Vector3f(0f, 1f, 0f)

    @Prop("Lighting", i=1)                  var color     = Color(1f, 1f, 1f)
    @Prop("Lighting", i=2, min=0f)          var intensity = 4f
    @Prop("Lighting", i=3, min=0f) override var radius    = 10f

    @Prop("Shadow", i=1)          var shadowEnabled    = false
    @Prop("Shadow", i=2, min=64f) var shadowResolution = 512
    @Prop("Shadow", i=3, min=0f)  var shadowBias       = 0.005f
    @Prop("Shadow", i=4, min=0f)  var shadowImportance = 1f

    private val intensityColor = Color()

    override fun onRenderLight(engine: PulseEngine, context: SceneRenderContext)
    {
        intensityColor.setFrom(color).multiplyRgb(intensity)
        context.submitPointLight(position, radius, intensityColor, shadowEnabled, shadowResolution, shadowBias, shadowImportance, id)
    }
}