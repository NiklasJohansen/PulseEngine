package no.njoh.pulseengine.modules.lighting.global

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.annotations.TexRef
import no.njoh.pulseengine.core.shared.primitives.Color

/**
 * Will occlude and reflect light in the scene. Handled by the [GlobalIlluminationSystem].
 */
interface GiOccluder
{
    @get:TexRef
    @get:Prop("Lighting", 0, desc = "The name of the occluder texture")
    var occluderTexture: String

    @get:Prop("Lighting", 1, desc = "The color of the occluder")
    var bounceColor: Color

    @get:Prop("Lighting", 2, desc = "Whether or not shadow casting is enabled for this occluder")
    var castShadows: Boolean

    @get:Prop("Lighting", 3, desc = "The strength of the occluder edge lighting")
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
                texture = engine.asset.getOrNull(occluderTexture) ?: Texture.BLANK,
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