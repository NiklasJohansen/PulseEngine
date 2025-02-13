package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram

class MultiplyEffect(
    override val name: String,
    private val surfaceName: String
) : BaseEffect() {

    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/effects/textureMultiplyBlend.vert",
        fragmentShaderFileName = "/pulseengine/shaders/effects/textureMultiplyBlend.frag"
    )

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        val surface = engine.gfx.getSurface(surfaceName) ?: return inTextures

        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniformSampler("tex0", inTextures[0])
        program.setUniformSampler("tex1", surface.getTexture())
        renderer.draw()
        fbo.release()
        return fbo.getTextures()
    }
}