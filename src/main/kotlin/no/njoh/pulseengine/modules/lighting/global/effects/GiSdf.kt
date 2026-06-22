package no.njoh.pulseengine.modules.lighting.global.effects

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.*
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureDescriptor
import no.njoh.pulseengine.core.graphics.postprocessing.BaseEffect

class GiSdf(
    private val isSigned: Boolean,
    override val name: String = "gi_sdf",
    override val order: Int = 3
) : BaseEffect(
    TextureDescriptor(format = R16F, filter = NEAREST)
) {
    override fun loadShaderProgram(engine: PulseEngineInternal) = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/pulseengine/shaders/lighting/global/base.vert")),
        engine.asset.loadNow(FragmentShader( "/pulseengine/shaders/lighting/global/sdf.frag"))
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("isSigned", isSigned)
        program.setUniformSampler("jfaTex", inTextures[0])
        fullscreenPass.draw()
        fbo.release()
        return fbo.getTextures()
    }
}