package no.njoh.pulseengine.modules.lighting.global.effects

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.api.TextureFormat.*
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BaseEffect

class GiSdf(
    override val name: String = "sdf",
    override val order: Int
) : BaseEffect(
    TextureDescriptor(format = R16F, filter = NEAREST, attachment = COLOR_TEXTURE_0)
) {
    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/lighting/global/default.vert",
        fragmentShaderFileName = "/pulseengine/shaders/lighting/global/sdf.frag"
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<Texture>): List<Texture>
    {
        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniformSampler("jfaTex", inTextures[0])
        renderer.draw()
        fbo.release()
        return fbo.getTextures()
    }
}