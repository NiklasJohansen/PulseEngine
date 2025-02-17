package no.njoh.pulseengine.modules.lighting.globalillumination.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.api.TextureFormat.*
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BaseEffect

class GiSdf(override val name: String = "sdf") : BaseEffect(
    TextureDescriptor(format = R16F, filter = NEAREST, attachment = COLOR_TEXTURE_0)
) {
    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/gi/default.vert",
        fragmentShaderFileName = "/pulseengine/shaders/gi/sdf.frag"
    )

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
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