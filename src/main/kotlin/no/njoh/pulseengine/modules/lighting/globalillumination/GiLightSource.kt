package no.njoh.pulseengine.modules.lighting.globalillumination

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color
import kotlin.math.min

/**
 * Defines the properties of a GI light source.
 * Handled by the [GlobalIlluminationSystem]
 */
interface GiLightSource
{
    @get:Prop("Lighting", 0, desc = "RGB-color of the light")
    var lightColor: Color

    @get:Prop("Lighting", 1, min = 0f, desc = "Light intensity multiplier")
    var intensity: Float

    @get:Prop("Lighting", 2, min = 0f, desc = "Max light reach. 0=infinite")
    var radius: Float

    @get:Prop("Lighting", 3, min = 0f, max = 360f, desc = "Determines the spread of the light beam in degrees")
    var coneAngle: Float

    /**
     * Default implementation for drawing a light source
     */
    fun drawLightSource(engine: PulseEngine, surface: Surface)
    {
        if ((this as? SceneEntity)?.isSet(HIDDEN) == true)
            return

        if (this is Spatial)
        {
            surface.setDrawColor(lightColor)
            surface.getRenderer<GiSceneRenderer>()?.drawLight(
                x = xInterpolated(),
                y = yInterpolated(),
                w = width,
                h = height,
                angle = rotationInterpolated(),
                cornerRadius = min(width, height) * 0.5f,
                intensity = intensity,
                coneAngle = coneAngle,
                radius = radius
            )
        }
    }
}