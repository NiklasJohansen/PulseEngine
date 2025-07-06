package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.annotations.TexRef
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.lighting.direct.DirectLightOccluder
import no.njoh.pulseengine.modules.lighting.global.GiOccluder
import no.njoh.pulseengine.modules.lighting.global.GiSceneRenderer
import no.njoh.pulseengine.modules.lighting.shared.NormalMapRenderer
import no.njoh.pulseengine.modules.lighting.shared.NormalMapRenderer.Orientation
import no.njoh.pulseengine.modules.lighting.shared.NormalMapped
import no.njoh.pulseengine.modules.physics.entities.Box

class Wall : Box(), DirectLightOccluder, GiOccluder, NormalMapped
{
    @TexRef
    var baseTexture = ""
    var color = Color(1f, 1f, 1f)
    var xTiling = 1f
    var yTiling = 1f

    override var occluderTexture = ""
    override var bounceColor = Color(1f, 1f, 1f)
    override var castShadows = true
    override var edgeLight = 100f

    override var normalMapTexture = ""
    override var normalMapIntensity = 1f
    override var normalMapOrientation = Orientation.NORMAL

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        surface.setDrawColor(color)
        surface.drawTexture(
            texture = engine.asset.getOrNull(baseTexture) ?: Texture.BLANK,
            x = xInterpolated(),
            y = yInterpolated(),
            width = width,
            height = height,
            angle = rotationInterpolated(),
            xOrigin = 0.5f,
            yOrigin = 0.5f,
            xTiling = xTiling,
            yTiling = yTiling
        )
    }

    override fun onRenderNormalMap(engine: PulseEngine, surface: Surface)
    {
        surface.getRenderer<NormalMapRenderer>()?.drawNormalMap(
            texture = engine.asset.getOrNull(normalMapTexture) ?: Texture.BLANK,
            x = x,
            y = y,
            w = width,
            h = height,
            rot = rotation,
            xOrigin = 0.5f,
            yOrigin = 0.5f,
            xTiling = xTiling,
            yTiling = yTiling,
            normalScale = normalMapIntensity,
            orientation = normalMapOrientation
        )
    }

    override fun onRenderOccluder(engine: PulseEngine, surface: Surface)
    {
        if (!castShadows) return

        surface.setDrawColor(bounceColor)
        surface.getRenderer<GiSceneRenderer>()?.drawOccluder(
            texture = engine.asset.getOrNull(occluderTexture) ?: Texture.BLANK,
            x = xInterpolated(),
            y = yInterpolated(),
            w = width,
            h = height,
            angle = rotationInterpolated(),
            edgeLight = edgeLight,
            xTiling = xTiling,
            yTiling = yTiling
        )
    }
}