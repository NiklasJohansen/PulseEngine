package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.annotations.TexRef
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.lighting.LightOccluder
import no.njoh.pulseengine.modules.lighting.NormalMapRenderer.Orientation
import no.njoh.pulseengine.modules.lighting.NormalMapped
import no.njoh.pulseengine.modules.physics.entities.Box

class Wall : Box(), LightOccluder, NormalMapped
{
    @TexRef
    var textureName: String = ""
    var color = Color(1f, 1f, 1f)

    override var normalMapName: String = ""
    override var normalMapIntensity = 1f
    override var normalMapOrientation = Orientation.NORMAL

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        surface.setDrawColor(color)
        surface.drawTexture(
            texture = engine.asset.getOrNull(textureName) ?: Texture.BLANK,
            x = xInterpolated(),
            y = yInterpolated(),
            width = width,
            height = height,
            angle = rotationInterpolated(),
            xOrigin = 0.5f,
            yOrigin = 0.5f
        )
    }
}