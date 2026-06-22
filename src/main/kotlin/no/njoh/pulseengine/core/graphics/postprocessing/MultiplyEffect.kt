package no.njoh.pulseengine.core.graphics.postprocessing

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram

class MultiplyEffect(
    override val name: String,
    override val order: Int,
    private val surfaceName: String,
    var minReflectance: Float = 0f
) : BaseEffect() {

    override fun loadShaderProgram(engine: PulseEngineInternal) = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/pulseengine/shaders/effects/texture_multiply_blend.vert")),
        engine.asset.loadNow(FragmentShader("/pulseengine/shaders/effects/texture_multiply_blend.frag"))
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        val surface = engine.gfx.getSurface(surfaceName) ?: return inTextures

        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("minReflectance", minReflectance)
        program.setUniformSampler("tex0", inTextures[0])
        program.setUniformSampler("tex1", surface.getTexture())
        fullscreenPass.draw()
        fbo.release()
        return fbo.getTextures()
    }
}