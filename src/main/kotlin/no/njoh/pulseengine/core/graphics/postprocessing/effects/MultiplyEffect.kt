package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.postprocessing.BaseEffect
import no.njoh.pulseengine.core.graphics.surface.Surface

class MultiplyEffect(
    override val name: String,
    private val surface: Surface
) : BaseEffect() {

    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/effects/textureMultiplyBlend.vert",
        fragmentShaderFileName = "/pulseengine/shaders/effects/textureMultiplyBlend.frag"
    )

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
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