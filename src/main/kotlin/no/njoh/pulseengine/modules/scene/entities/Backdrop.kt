package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.shared.annotations.AssetRef
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.lighting.NormalMapRenderer.Orientation
import no.njoh.pulseengine.modules.lighting.NormalMapped

open class Backdrop : StandardSceneEntity(), NormalMapped
{
    @AssetRef(Texture::class)
    var textureName: String = ""

    var color = Color(1f, 1f, 1f)
    var xTiling = 1f
    var yTiling = 1f

    override var normalMapName: String = ""
    override var normalMapIntensity = 1f
    override var normalMapOrientation = Orientation.NORMAL

    init { setNot(DISCOVERABLE) }

    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        surface.setDrawColor(color)
        surface.drawTexture(
            texture = engine.asset.getOrNull(textureName) ?: Texture.BLANK,
            x = x,
            y = y,
            width = width,
            height = height,
            rot = rotation,
            xOrigin = 0.5f,
            yOrigin = 0.5f,
            uTiling = xTiling,
            vTiling = yTiling
        )
    }
}