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

class Compose(override val name: String = "compose") : SinglePassEffect(
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
        val lightSurface = lightSystem.getLightSurface(engine) ?: return inTextures

        val (xPixelOffset, yPixelOffset) = SceneRenderer.calculatePixelOffset(lightSurface)
        val xSampleOffset = xPixelOffset / lightSurface.config.width
        val ySampleOffset = yPixelOffset / lightSurface.config.height

        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("sampleOffset", xSampleOffset, ySampleOffset)
        program.setUniform("dithering", lightSystem.dithering)
        renderer.drawTextures(
            inTextures[0], // Albedo
            lightSurface.getTexture()
        )
        fbo.release()

        return fbo.getTextures()
    }
}