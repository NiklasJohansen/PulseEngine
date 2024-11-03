package no.njoh.pulseengine.modules.lighting.globalillumination.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.postprocessing.SinglePassEffect
import no.njoh.pulseengine.core.shared.utils.Extensions.component1
import no.njoh.pulseengine.core.shared.utils.Extensions.component2
import no.njoh.pulseengine.modules.lighting.globalillumination.GlobalIlluminationSystem
import no.njoh.pulseengine.modules.lighting.globalillumination.SceneRenderer

class Compose(
    private val localSceneSurfaceName: String,
    private val lightSurfaceName: String,
    override val name: String = "compose"
) : SinglePassEffect(
    textureFilter = LINEAR,
    textureFormat = RGBA16F
) {
    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/gi/default.vert",
        fragmentShaderFileName = "/pulseengine/shaders/gi/compose.frag"
    )

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        val lightSystem = engine.scene.getSystemOfType<GlobalIlluminationSystem>() ?: return inTextures
        val lightSurface = engine.gfx.getSurface(lightSurfaceName) ?: return inTextures
        val sceneSurface = engine.gfx.getSurface(localSceneSurfaceName) ?: return inTextures

        val (xPixelOffset, yPixelOffset) = SceneRenderer.calculatePixelOffset(sceneSurface)
        val xSampleOffset = if (lightSystem.fixJitter) xPixelOffset / lightSurface.config.width else 0f
        val ySampleOffset = if (lightSystem.fixJitter) yPixelOffset / lightSurface.config.height else 0f

        textureFilter = lightSystem.textureFilter

        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("sampleOffset", xSampleOffset, ySampleOffset)
        program.setUniform("dithering", lightSystem.dithering)
        renderer.drawTextures(
            inTextures[0], // Albedo
            lightSurface.getTexture() // Lighting
        )
        fbo.release()

        return fbo.getTextures()
    }
}