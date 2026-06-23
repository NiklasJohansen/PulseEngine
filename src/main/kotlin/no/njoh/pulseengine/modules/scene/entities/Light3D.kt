package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.modules.scene.systems.Scene3DLightSource
import org.joml.Vector3f

class Light3D : SceneEntity(), Scene3DLightSource, Named
{
    @Prop(i=0) override var name = "Light"

    @Prop("Position  [*P]", i=1) var xPos=0f;   var yPos=1f;   var zPos=0f
    @Prop("Direction [*D]", i=2) var xDir=0f;   var yDir=-1f;  var zDir=0f

    @Prop("Lighting", i=2)         var color            = Color(1f, 1f, 1f)
    @Prop("Lighting", i=3, min=0f) var intensity        = 4f
    @Prop("Lighting", i=4, min=0f) var radius           = 50f
    @Prop("Lighting", i=5, min=0f) var innerConeAngle   = 0f
    @Prop("Lighting", i=6, min=0f) var outerConeAngle   = 40f

    @Prop("Shadow", i=7)          var shadowEnabled    = false
    @Prop("Shadow", i=8, min=64f) var shadowResolution = 512
    @Prop("Shadow", i=0, min=0f)  var shadowBias       = 0.005f
    @Prop("Shadow", i=10,min=0f)  var shadowImportance = 1f

    private var position = Vector3f()
    private var direction = Vector3f()
    private val intensityColor = Color()

    override fun onRenderLight(engine: PulseEngine, context: SceneRenderContext)
    {
        intensityColor.setFrom(color).multiplyRgb(intensity)
        position.set(xPos, yPos, zPos)

        if (outerConeAngle > 180f)
        {
            context.submitPointLight(position, radius, intensityColor, shadowEnabled, shadowResolution, shadowBias, shadowImportance, id)
        }
        else
        {
            direction.set(xDir, yDir, zDir)
            context.submitSpotLight(position, direction, radius, intensityColor, innerConeAngle, outerConeAngle, shadowEnabled, shadowResolution, shadowBias, shadowImportance, id)
        }
    }
}