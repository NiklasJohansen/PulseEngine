package no.njoh.pulseengine.modules.lighting.shared

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.core.scene.systems.CustomRenderPassTarget
import no.njoh.pulseengine.core.shared.annotations.AssetRef
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.modules.lighting.shared.NormalMapRenderer.Orientation

/**
 * Rendered by the [DirectLightingSystem] to a separate normal map [Surface] for deferred lighting calculations.
 */
interface NormalMapped : CustomRenderPassTarget
{
    @get:AssetRef(Texture::class)
    @get:Prop("Lighting", 0, desc = "Name of the normal map [Texture] asset.")
    var normalMapName: String

    @get:Prop("Lighting", 1, desc = "The intensity/scale of the normals in the map.")
    var normalMapIntensity: Float

    @get:Prop("Lighting", 2, desc = "The orientation of the normals in the map.")
    var normalMapOrientation: Orientation

    override fun renderCustomPass(engine: PulseEngine, surface: Surface)
    {
        if (this is SceneEntity && this is Spatial && normalMapName.isNotBlank())
        {
            surface.getRenderer<NormalMapRenderer>()?.drawNormalMap(
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