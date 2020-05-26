package engine.modules.graphics.postprocessing.effects

import engine.data.Texture
import engine.modules.graphics.ShaderProgram
import engine.modules.graphics.postprocessing.SinglePassEffect

class BloomEffect(
    var threshold: Float = 0.5f,
    var exposure: Float = 1f,
    var blurRadius: Float = 0.4f,
    var blurPasses: Int = 1
) : SinglePassEffect() {

    private val blurEffect = BlurEffect(blurRadius, blurPasses)
    private val thresholdEffect = ThresholdEffect(threshold)

    override fun init()
    {
        super.init()
        blurEffect.init()
        thresholdEffect.init()
    }

    override fun acquireShaderProgram(): ShaderProgram =
        ShaderProgram.create(
            vertexShaderFileName = "/engine/shaders/effects/textureAddBlend.vert",
            fragmentShaderFileName = "/engine/shaders/effects/textureAddBlend.frag"
        )

    override fun applyEffect(texture: Texture): Texture
    {
        thresholdEffect.brightnessThreshold = threshold
        val brightTexture = thresholdEffect.process(texture)

        blurEffect.blurPasses = blurPasses
        blurEffect.radius = blurRadius
        val blurredBrightPass = blurEffect.process(brightTexture)

        fbo.bind()
        program.use()
        program.setUniform("exposure", exposure)
        renderer.render(texture, blurredBrightPass)
        fbo.release()

        return fbo.texture
    }
}