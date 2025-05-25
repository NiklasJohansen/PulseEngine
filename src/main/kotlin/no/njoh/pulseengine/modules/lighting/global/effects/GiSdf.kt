package no.njoh.pulseengine.modules.lighting.global.effects

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.api.TextureFormat.*
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BaseEffect

class GiSdf(
    private val isSigned: Boolean,
    override val name: String = "sdf",
    override val order: Int = 3
) : BaseEffect(
    TextureDescriptor(format = R16F, filter = NEAREST)
) {
    override fun loadShaderProgram(engine: PulseEngineInternal) = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/pulseengine/shaders/lighting/global/default.vert")),
        engine.asset.loadNow(FragmentShader( "/pulseengine/shaders/lighting/global/sdf.frag"))
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<Texture>): List<Texture>
    {
        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("isSigned", isSigned)
        program.setUniformSampler("jfaTex", inTextures[0])
        renderer.draw()
        fbo.release()
        return fbo.getTextures()
    }
}