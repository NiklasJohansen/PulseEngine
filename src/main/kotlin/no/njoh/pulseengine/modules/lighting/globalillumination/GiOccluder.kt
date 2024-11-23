package no.njoh.pulseengine.modules.lighting.globalillumination

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color

/**
 * Will occlude and reflect light in the scene. Handled by the [GlobalIlluminationSystem].
 */
interface GiOccluder
{
    @get:Prop("Lighting", desc = "The color of the occluder")
    var bounceColor: Color

    @get:Prop("Lighting", desc = "Whether or not shadow casting is enabled for this occluder")
    var castShadows: Boolean

    @get:Prop("Lighting", desc = "The strength of the occluder edge lighting")
    var edgeLight: Float

    /**
     * Default implementation for drawing an occluder
     */
    fun drawOccluder(engine: PulseEngine, surface: Surface)
    {
        if (!castShadows) return

        if (this is Spatial)
        {
            surface.setDrawColor(bounceColor)
            surface.getRenderer<GiSceneRenderer>()?.drawOccluder(
                x = xInterpolated(),
                y = yInterpolated(),
                w = width,
                h = height,
                angle = rotationInterpolated(),
                cornerRadius = 0f,
                edgeLight = edgeLight
            )
        }
    }
}