package no.njoh.pulseengine.modules.lighting.direct

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureHandle
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BaseEffect
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.max

class DirectLightBlendEffect(
    override val name: String,
    override val order: Int,
    private val ambientColor: Color,
    private val camera: Camera
) : BaseEffect() {

    /** Handle to the light map texture */
    var lightMapTextureHandle: TextureHandle? = null

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

    override fun loadShaderProgram(engine: PulseEngineInternal) = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/pulseengine/shaders/lighting/direct/lighting_blend.vert")),
        engine.asset.loadNow(FragmentShader("/pulseengine/shaders/lighting/direct/lighting_blend.frag"))
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        val albedoTexture = inTextures[0]
        val lightMapTextureHandle = lightMapTextureHandle ?: return inTextures

        camPos.set(1f).mul(camera.viewProjectionMatrix)
        camera.viewMatrix.getScale(camScale)

        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("ambientColor", ambientColor)
        program.setUniform("samplingOffset", xSamplingOffset, ySamplingOffset)
        program.setUniform("resolution", albedoTexture.width.toFloat(), albedoTexture.height.toFloat())
        program.setUniform("enableFxaa", enableFxaa)
        program.setUniform("dithering", dithering)
        program.setUniform("fogIntensity", fogIntensity)
        program.setUniform("fogScale", max(0.01f, fogScale) * camScale.x)
        program.setUniform("camPos", camPos.x, camPos.y)
        program.setUniform("time", 0.001f * fogTurbulence * time++)
        program.setUniformSampler("baseTex", albedoTexture.handle)
        program.setUniformSampler("lightTex", lightMapTextureHandle)
        renderer.draw()
        fbo.release()

        return fbo.getTextures()
    }

    private var time = 0f
}