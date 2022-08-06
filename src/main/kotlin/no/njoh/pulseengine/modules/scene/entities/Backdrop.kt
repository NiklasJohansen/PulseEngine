package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.shared.annotations.Property
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.lighting.NormalMapped

open class Backdrop : SceneEntity(), NormalMapped
{
    var color = Color(1f, 1f, 1f)
    var textureName: String = ""
    var xTiling = 1f
    var yTiling = 1f

    @Property("Lighting", order = 1) override var normalMapName: String = ""
    @Property("Lighting", order = 2) override var normalMapIntensity = 1f

    init { setNot(DISCOVERABLE) }

    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        surface.setDrawColor(color)
        surface.drawTexture(engine.asset.getOrNull(textureName) ?: Texture.BLANK, x, y, width, height, rotation, 0.5f, 0.5f, uTiling = xTiling, vTiling = yTiling)
    }

    override fun renderCustomPass(engine: PulseEngine, surface: Surface2D)
    {
        val normalMap = engine.asset.getOrNull<Texture>(normalMapName)
        val dir = if (normalMap != null) 1.0f else 0.5f
        surface.setDrawColor(dir, dir, 1f)
        surface.drawTexture(normalMap ?: Texture.BLANK, x, y, width, height, rotation, 0.5f, 0.5f, uTiling = xTiling, vTiling = yTiling)
    }
}