package no.njoh.pulseengine.modules.lighting

import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Camera
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.Surface
import no.njoh.pulseengine.core.graphics.postprocessing.SinglePassEffect
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.max

class LightBlendEffect(
    private val lightMapSurface: Surface,
    private val ambientColor: Color,
    private val camera: Camera
) : SinglePassEffect() {

    /** Offsets the sampling coordinates of the light map */
    var xSamplingOffset = 0f
    var ySamplingOffset = 0f

    /** Enables FXAA anti aliasing when sampling the light map */
    var enableFxaa = true

    /** Introduces noise to prevent color banding */
    var dithering = 0.7f

    /** Determines the amount of fog to introduce to the lighting */
    var fogIntensity = 0.7f

    /** Determines the amount animated turbulence in the fog */
    var fogTurbulence = 1f

    /** Determines the scale of the noise used to generate the fog  */
    var fogScale = 1f

    private var camPos = Vector4f()
    private var camScale = Vector3f()

    override fun loadShaderProgram(): ShaderProgram =
        ShaderProgram.create(
            vertexShaderFileName = "/pulseengine/shaders/effects/lighting_blend.vert",
            fragmentShaderFileName = "/pulseengine/shaders/effects/lighting_blend.frag"
        )

    override fun applyEffect(texture: Texture): Texture
    {
        camPos.set(1f).mul(camera.viewProjectionMatrix)
        camera.viewMatrix.getScale(camScale)

        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("ambientColor", ambientColor)
        program.setUniform("samplingOffset", xSamplingOffset, ySamplingOffset)
        program.setUniform("resolution", texture.width.toFloat(), texture.height.toFloat())
        program.setUniform("enableFxaa", enableFxaa)
        program.setUniform("dithering", dithering)
        program.setUniform("fogIntensity", fogIntensity)
        program.setUniform("fogScale", max(0.01f, fogScale) * camScale.x)
        program.setUniform("camPos", camPos.x, camPos.y)
        program.setUniform("time", 0.001f * fogTurbulence * time++)

        renderer.render(texture, lightMapSurface.getTexture(0))
        fbo.release()

        return fbo.getTexture() ?: texture
    }

    private var time = 0f
}