package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderContext
import no.njoh.pulseengine.modules.scene.systems.WorldLightSource
import org.joml.Vector3f

class WorldLight : SceneEntity(), WorldLightSource, Named
{
    @Prop(i=0) override var name             = "Light"
    @Prop(i=1)          var color            = Color(1f, 1f, 1f)
    @Prop(i=2,min=0f)   var intensity        = 4f
    @Prop(i=3,min=0f)   var radius           = 50f
    @Prop(i=4)          var xPos             = 0f
    @Prop(i=5)          var yPos             = 1f
    @Prop(i=6)          var zPos             = 0f
    @Prop(i=7)          var xDir             = 0f
    @Prop(i=8)          var yDir             = -1f
    @Prop(i=9)          var zDir             = 0f
    @Prop(i=10, min=0f) var innerConeAngle   = 0f
    @Prop(i=11, min=0f) var outerConeAngle   = 40f
    @Prop(i=12)         var shadowEnabled    = false
    @Prop(i=13,min=64f) var shadowResolution = 512
    @Prop(i=14,min=0f)  var shadowBias       = 0.005f
    @Prop(i=15,min=0f)  var shadowImportance = 1f

    private var position = Vector3f()
    private var direction = Vector3f()
    private val intensityColor = Color()

    override fun onRenderLight(engine: PulseEngine, context: WorldRenderContext)
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