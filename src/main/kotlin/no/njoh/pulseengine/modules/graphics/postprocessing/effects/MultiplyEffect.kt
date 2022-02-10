package no.njoh.pulseengine.modules.graphics.postprocessing.effects

import no.njoh.pulseengine.modules.asset.types.Texture
import no.njoh.pulseengine.modules.graphics.api.ShaderProgram
import no.njoh.pulseengine.modules.graphics.Surface
import no.njoh.pulseengine.modules.graphics.postprocessing.SinglePassEffect

class MultiplyEffect(
    private val surface: Surface
) : SinglePassEffect() {
    override fun loadShaderProgram(): ShaderProgram =
        ShaderProgram.create(
            vertexShaderFileName = "/pulseengine/shaders/effects/textureMultiplyBlend.vert",
            fragmentShaderFileName = "/pulseengine/shaders/effects/textureMultiplyBlend.frag"
        )

    override fun applyEffect(texture: Texture): Texture
    {
        fbo.bind()
        fbo.clear()
        program.bind()
        renderer.render(texture, surface.getTexture())
        fbo.release()

        return fbo.getTexture() ?: texture
    }
}