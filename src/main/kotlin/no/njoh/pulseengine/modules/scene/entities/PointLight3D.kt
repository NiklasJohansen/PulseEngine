package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Light3D
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color
import org.joml.Vector3f

class PointLight3D : SceneEntity(), Light3D
{
    @Prop(i=0) override var name = "Point Light"

    @Prop("Position [*P]", i=1) override var xPos=0f; override var yPos=1f; override var zPos=0f

    @Prop("Lighting", i=2)         override var color     = Color(1f, 1f, 1f)
    @Prop("Lighting", i=3, min=0f) override var intensity = 4f
    @Prop("Lighting", i=4, min=0f) override var radius    = 10f

    @Prop("Shadow", i=7)           override var shadowEnabled    = false
    @Prop("Shadow", i=8, min=64f)  override var shadowResolution = 512
    @Prop("Shadow", i=9, min=0f)   override var shadowBias       = 0.005f
    @Prop("Shadow", i=10, min=0f)  override var shadowImportance = 1f

    private val position = Vector3f()
    private val intensityColor = Color()

    override fun onRenderLight(engine: PulseEngine, context: SceneRenderContext)
    {
        position.set(xPos, yPos, zPos)
        intensityColor.setFrom(color).multiplyRgb(intensity)
        context.submitPointLight(position, radius, intensityColor, shadowEnabled, shadowResolution, shadowBias, shadowImportance, id)
    }
}