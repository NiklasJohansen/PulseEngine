package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.shared.annotations.AssetRef
import no.njoh.pulseengine.modules.lighting.LightOccluder
import no.njoh.pulseengine.modules.lighting.NormalMapRenderer.Orientation
import no.njoh.pulseengine.modules.lighting.NormalMapped
import no.njoh.pulseengine.modules.physics.entities.Box

class Wall : Box(), LightOccluder, NormalMapped
{
    @AssetRef(Texture::class)
    var textureName: String = ""

    override var normalMapName: String = ""
    override var normalMapIntensity = 1f
    override var normalMapOrientation = Orientation.NORMAL
    override var castShadows: Boolean = true

    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        val x = if (xInterpolated.isNaN()) x else xInterpolated
        val y = if (yInterpolated.isNaN()) y else yInterpolated
        val r = if (rotInterpolated.isNaN()) rotation else rotInterpolated

        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(engine.asset.getOrNull(textureName) ?: Texture.BLANK, x, y, width, height, r, xOrigin = 0.5f, yOrigin = 0.5f)
    }
}