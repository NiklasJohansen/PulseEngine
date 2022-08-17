package no.njoh.pulseengine.modules.lighting

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.systems.CustomRenderPassTarget
import no.njoh.pulseengine.core.shared.annotations.ScnProp
import no.njoh.pulseengine.modules.lighting.NormalMapRenderer.Orientation

/**
 * Rendered by the [LightingSystem] to a separate normal map [Surface2D] for deferred lighting calculations.
 */
interface NormalMapped : CustomRenderPassTarget
{
    @get:ScnProp("Lighting", 0, desc = "Name of the normal map [Texture] asset.")
    var normalMapName: String

    @get:ScnProp("Lighting", 1, desc = "The intensity/scale of the normals in the map.")
    var normalMapIntensity: Float

    @get:ScnProp("Lighting", 2, desc = "The orientation of the normals in the map.")
    var normalMapOrientation: Orientation

    override fun renderCustomPass(engine: PulseEngine, surface: Surface2D)
    {
        if (this is SceneEntity && normalMapName.isNotBlank())
        {
            surface.getRenderer(NormalMapRenderer::class)?.drawNormalMap(
                texture = engine.asset.getOrNull(normalMapName),
                x = x,
                y = y,
                w = width,
                h = height,
                rot = rotation,
                xOrigin = 0.5f,
                yOrigin = 0.5f,
                normalScale = normalMapIntensity,
                orientation = normalMapOrientation
            )
        }
    }
}