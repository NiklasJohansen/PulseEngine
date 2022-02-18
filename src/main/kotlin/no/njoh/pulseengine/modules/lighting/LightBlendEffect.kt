package no.njoh.pulseengine.modules.lighting

import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.Surface
import no.njoh.pulseengine.core.graphics.postprocessing.SinglePassEffect

class LightBlendEffect(
    private val lightMapSurface: Surface,
    private val ambientColor: Color
) : SinglePassEffect() {

    /** Offsets the sampling coordinates of the light map */
    var xSamplingOffset = 0f
    var ySamplingOffset = 0f

    override fun loadShaderProgram(): ShaderProgram =
        ShaderProgram.create(
            vertexShaderFileName = "/pulseengine/shaders/effects/lightBlend.vert",
            fragmentShaderFileName = "/pulseengine/shaders/effects/lightBlend.frag"
        )

    override fun applyEffect(texture: Texture): Texture
    {
        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("ambientColor", ambientColor)
        program.setUniform("samplingOffset", xSamplingOffset, ySamplingOffset)
        program.setUniform("resolution", texture.width.toFloat(), texture.height.toFloat())
        renderer.render(texture, lightMapSurface.getTexture(0))
        fbo.release()

        return fbo.getTexture() ?: texture
    }
}