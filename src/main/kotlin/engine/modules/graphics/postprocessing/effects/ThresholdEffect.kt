package engine.modules.graphics.postprocessing.effects

import engine.data.Texture
import engine.modules.graphics.ShaderProgram
import engine.modules.graphics.postprocessing.SinglePassEffect

class ThresholdEffect(
    var brightnessThreshold: Float = 0.5f
) : SinglePassEffect() {

    override fun loadShaderProgram(): ShaderProgram =
        ShaderProgram.create(
            vertexShaderFileName = "/engine/shaders/effects/brightnessThreshold.vert",
            fragmentShaderFileName = "/engine/shaders/effects/brightnessThreshold.frag"
        )

    override fun applyEffect(texture: Texture): Texture
    {
        fbo.bind()
        program.use()
        program.setUniform("threshold", brightnessThreshold)
        renderer.render(texture)
        fbo.release()

        return fbo.texture
    }
}