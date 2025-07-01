package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.annotations.AssetRef
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.lighting.shared.NormalMapRenderer
import no.njoh.pulseengine.modules.lighting.shared.NormalMapRenderer.Orientation
import no.njoh.pulseengine.modules.lighting.shared.NormalMapped

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

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        surface.setDrawColor(color)
        surface.drawTexture(
            texture = engine.asset.getOrNull(textureName) ?: Texture.BLANK,
            x = x,
            y = y,
            width = width,
            height = height,
            angle = rotation,
            xOrigin = 0.5f,
            yOrigin = 0.5f,
            uTiling = xTiling,
            vTiling = yTiling
        )
    }

    override fun renderNormalMap(engine: PulseEngine, surface: Surface)
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
            uTiling = xTiling,
            vTiling = yTiling,
            normalScale = normalMapIntensity,
            orientation = normalMapOrientation
        )
    }
}