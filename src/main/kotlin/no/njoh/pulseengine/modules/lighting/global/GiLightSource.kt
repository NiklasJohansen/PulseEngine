package no.njoh.pulseengine.modules.lighting.global

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.annotations.TexRef
import no.njoh.pulseengine.core.shared.primitives.Color

/**
 * Defines the properties of a GI light source.
 * Handled by the [GlobalIlluminationSystem]
 */
interface GiLightSource
{
    @get:Prop("Lighting", 0, desc = "RGB-color of the light")
    var lightColor: Color

    @get:TexRef
    @get:Prop("Lighting", 1, desc = "Name of the light texture")
    var lightTexture: String

    @get:Prop("Lighting", 2, min = 0f, desc = "Light intensity multiplier")
    var intensity: Float

    @get:Prop("Lighting", 3, min = 0f, desc = "Max light reach. 0=infinite")
    var radius: Float

    @get:Prop("Lighting", 4, min = 0f, max = 360f, desc = "Determines the spread of the light beam in degrees")
    var coneAngle: Float

    /**
     * Default implementation for rendering a light source
     */
    fun onRenderLightSource(engine: PulseEngine, surface: Surface)
    {
        if ((this as? SceneEntity)?.isSet(HIDDEN) == true || intensity == 0f)
            return

        if (this is Spatial)
        {
            surface.setDrawColor(lightColor)
            surface.getRenderer<GiSceneRenderer>()?.drawLight(
                texture = engine.asset.getOrNull(lightTexture) ?: Texture.BLANK,
                x = xInterpolated(),
                y = yInterpolated(),
                w = width,
                h = height,
                angle = rotationInterpolated(),
                intensity = intensity,
                coneAngle = coneAngle,
                radius = radius
            )
        }
    }
}