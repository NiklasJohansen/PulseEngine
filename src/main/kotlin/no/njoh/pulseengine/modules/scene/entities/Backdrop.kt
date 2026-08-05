package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.AssetHandle
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.lighting.shared.NormalMapRenderer
import no.njoh.pulseengine.modules.lighting.shared.NormalMapRenderer.Orientation
import no.njoh.pulseengine.modules.lighting.shared.NormalMapped2D

@Name("2D Backdrop")
open class Backdrop : Common2DSceneEntity(), NormalMapped2D
{
    var baseTexture = AssetHandle<Texture>()

    var color = Color(1f, 1f, 1f)
    var xTiling = 1f
    var yTiling = 1f

    override var normalMapTexture = AssetHandle<Texture>()
    override var normalMapIntensity = 1f
    override var normalMapOrientation = Orientation.NORMAL

    init { setNot(DISCOVERABLE) }

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        surface.setDrawColor(color)
        surface.drawTexture(
            texture = engine.asset.getOrNull(baseTexture) ?: Texture.BLANK,
            x = x,
            y = y,
            width = width,
            height = height,
            angle = zRotation,
            xOrigin = 0.5f,
            yOrigin = 0.5f,
            xTiling = xTiling,
            yTiling = yTiling
        )
    }

    override fun onRenderNormalMap(engine: PulseEngine, surface: Surface)
    {
        surface.getRenderer<NormalMapRenderer>()?.drawNormalMap(
            texture = engine.asset.getOrNull(normalMapTexture),
            x = x,
            y = y,
            w = width,
            h = height,
            rot = zRotation,
            xOrigin = 0.5f,
            yOrigin = 0.5f,
            xTiling = xTiling,
            yTiling = yTiling,
            normalScale = normalMapIntensity,
            orientation = normalMapOrientation
        )
    }
}