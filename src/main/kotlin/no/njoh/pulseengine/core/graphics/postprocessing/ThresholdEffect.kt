package no.njoh.pulseengine.core.graphics.postprocessing

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram

class ThresholdEffect(
    override val name: String,
    override val order: Int,
    var threshold: Float = 0.5f
) : BaseEffect() {

    override fun loadShaderProgram(engine: PulseEngineInternal) = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/pulseengine/shaders/effects/brightness_threshold.vert")),
        engine.asset.loadNow(FragmentShader("/pulseengine/shaders/effects/brightness_threshold.frag"))
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("threshold", threshold)
        program.setUniformSampler("tex", inTextures[0])
        fullscreenPass.draw()
        fbo.release()
        return fbo.getTextures()
    }
}