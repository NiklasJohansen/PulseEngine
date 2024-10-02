package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.postprocessing.SinglePassEffect

class MultiplyEffect(
    override val name: String,
    private val surface: Surface
) : SinglePassEffect() {

    override fun loadShaderProgram(): ShaderProgram =
        ShaderProgram.create(
            vertexShaderFileName = "/pulseengine/shaders/effects/textureMultiplyBlend.vert",
            fragmentShaderFileName = "/pulseengine/shaders/effects/textureMultiplyBlend.frag"
        )

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        fbo.bind()
        fbo.clear()
        program.bind()
        renderer.drawTextures(inTextures[0], surface.getTexture())
        fbo.release()

        return fbo.getTextures()
    }
}