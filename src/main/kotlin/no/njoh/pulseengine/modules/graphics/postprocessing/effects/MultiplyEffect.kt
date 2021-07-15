package no.njoh.pulseengine.modules.graphics.postprocessing.effects

import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.ShaderProgram
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

        return fbo.texture
    }
}