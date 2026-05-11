package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureCompare
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.api.TextureFilter
import no.njoh.pulseengine.core.graphics.api.TextureFormat.*
import no.njoh.pulseengine.core.graphics.api.TextureWrapping.*
import no.njoh.pulseengine.core.graphics.renderers.CascadedShadowMapRenderer
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.shared.primitives.Color
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glViewport
import kotlin.math.max

/**
 * Volumetric sun/god-ray effect with raymarching in screen space. 
 * Renders the sun scattering in a separate pass, then applies a blur to smooth out the result and finally composes it with the scene color.
 */
class VolumetricSunEffect(
    override val name: String,
    override val order: Int,
    private val camera: Camera,
    downsampleFactor: Int = 2
) : BaseEffect(
    TextureDescriptor(RGBA16F, TextureFilter.LINEAR, CLAMP_TO_EDGE, scale = 1f),                              // 0: Output
    TextureDescriptor(RGB16F,  TextureFilter.LINEAR, CLAMP_TO_EDGE, scale = 1f / downsampleFactor.toFloat()), // 1: Volume (downsampled)
    TextureDescriptor(RGB16F,  TextureFilter.LINEAR, CLAMP_TO_EDGE, scale = 1f / downsampleFactor.toFloat()), // 2: Blur   (downsampled)
    TextureDescriptor(RGB16F,  TextureFilter.LINEAR, CLAMP_TO_EDGE, scale = 1f)                               // 3: Upsample (full resolution)
) {
    var shadowMapSurfaceName  = ""
    var sunColor              = Color(1f, 1f, 1f)
    var sunRadius             = 1f
    var intensity             = 1f
    var density               = 0.05f
    var anisotropy            = 0.7f
    var maxDistance           = 120f
    var stepCount             = 32
    var blurRadius            = 1.75f
    var blurDepthTolerance    = 6f
    var upsampleBlur          = 0.3f
    var upsampleEdgeTolerance = 1f
    var jitterStrength        = 1f
    var heightFogStart        = 0f
    var heightFogFalloff      = 0.03f
    var downsampleFactor      = downsampleFactor
        set(value) {
            field = value
            val scale = 1f / value.toFloat()
            textureDescriptors[1].scale = scale
            textureDescriptors[2].scale = scale
        }

    private val cameraPos = Vector3f()
    private val finalTextures = mutableListOf<RenderTexture>()

    override fun loadShaderPrograms(engine: PulseEngineInternal) = listOf(
        ShaderProgram.create(
            engine.asset.loadNow(VertexShader("/pulseengine/shaders/effects/volumetric_sun.vert")),
            engine.asset.loadNow(FragmentShader("/pulseengine/shaders/effects/volumetric_sun_raymarch.frag"))
        ),
        ShaderProgram.create(
            engine.asset.loadNow(VertexShader("/pulseengine/shaders/effects/volumetric_sun.vert")),
            engine.asset.loadNow(FragmentShader("/pulseengine/shaders/effects/volumetric_sun_blur.frag"))
        ),
        ShaderProgram.create(
            engine.asset.loadNow(VertexShader("/pulseengine/shaders/effects/volumetric_sun.vert")),
            engine.asset.loadNow(FragmentShader("/pulseengine/shaders/effects/volumetric_sun_upsample.frag"))
        ),
        ShaderProgram.create(
            engine.asset.loadNow(VertexShader("/pulseengine/shaders/effects/volumetric_sun.vert")),
            engine.asset.loadNow(FragmentShader("/pulseengine/shaders/effects/volumetric_sun_compose.frag"))
        )
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        val shadowMapSurface = engine.gfx.getSurface(shadowMapSurfaceName) ?: return inTextures
        val shadowMapRenderer = shadowMapSurface.getRenderer<CascadedShadowMapRenderer>() ?: return inTextures
        val shadowTex = shadowMapSurface.getTexture(final = false)
        val sceneTex = inTextures.firstOrNull { it.attachment.hasColor } ?: return inTextures
        val depthTex = inTextures.firstOrNull { it.attachment == DEPTH_TEXTURE } ?: return inTextures

        fbo.bind()
        fbo.clear()

        // Render scene to volume texture
        var outTex = renderScattering(depthTex, shadowTex, shadowMapRenderer)

        // Blur volume texture
        if (blurRadius > 0f)
            outTex = blurScattering(depthTex, outTex)

        // Upsample volume texture
        if (downsampleFactor > 1)
            outTex = upsampleScattering(depthTex, outTex)

        // Compose scene color with volume texture
        outTex = compose(sceneTex, outTex)

        fbo.release()

        glViewport(0, 0, sceneTex.width, sceneTex.height)

        return finalTextures.apply { clear(); add(outTex) }
    }

    private fun renderScattering(depthTex: RenderTexture, shadowTex: RenderTexture, shadowMapRenderer: CascadedShadowMapRenderer): RenderTexture = measure("scattering") 
    {
        val splitDist = shadowMapRenderer.getCascadeSplitDistances()
        val program = programs[0]
        val volumeTex = fbo.getTexture(1)

        camera.invViewMatrix.getTranslation(cameraPos)

        program.bind()
        program.setUniformSampler("uDepthTex", depthTex, TextureFilter.NEAREST)
        program.setUniformSampler("uShadowMapTex", shadowTex, TextureFilter.LINEAR, CLAMP_TO_BORDER, TextureCompare.LEQUAL, Color.WHITE)
        program.setUniform("uInvViewProjection", camera.invViewProjectionMatrix)
        program.setUniform("uView", camera.viewMatrix)
        program.setUniform("uCameraPos", cameraPos)
        program.setUniform("uSunDirection", shadowMapRenderer.getDirection())
        program.setUniform("uSunColor", sunColor)
        program.setUniform("uSunRadius", sunRadius)
        program.setUniform("uIntensity", intensity)
        program.setUniform("uDensity", density)
        program.setUniform("uAnisotropy", anisotropy)
        program.setUniform("uMaxDistance", maxDistance)
        program.setUniform("uStepCount", stepCount)
        program.setUniform("uJitterStrength", jitterStrength)
        program.setUniform("uHeightFogStart", heightFogStart)
        program.setUniform("uHeightFogFalloff", heightFogFalloff)
        program.setUniform("uShadowMapTexSize", shadowMapRenderer.resolution.toFloat())
        program.setUniform("uShadowViewProjections", shadowMapRenderer.getViewProjectionMatrices())
        program.setUniform("uShadowCascadeSplitDistances", splitDist[0], splitDist[1], splitDist[2], splitDist[3])

        glDisable(GL_DEPTH_TEST)
        glDisable(GL_BLEND)
        glViewport(0, 0, volumeTex.width, volumeTex.height)

        fbo.attachOutputTexture(volumeTex)
        renderer.draw()

        return volumeTex
    }

    private fun blurScattering(depthTex: RenderTexture, scatterTex: RenderTexture): RenderTexture = measure("blur scattering")
    {
        val program = programs[1]
        val blurTex = fbo.getTexture(2)
        val volumeTex = fbo.getTexture(1)
        val xTexelSize = 1f / scatterTex.width.toFloat()
        val yTexelSize = 1f / scatterTex.height.toFloat()

        program.bind()
        program.setUniformSampler("uDepthTex", depthTex, filter = TextureFilter.NEAREST)
        program.setUniform("uInvProjection", camera.invProjectionMatrix)
        program.setUniform("uBlurRadius", blurRadius)
        program.setUniform("uDepthTolerance", blurDepthTolerance)

        // Horizontal blur: scatterTex -> blurTex
        program.setUniformSampler("uVolumeTex", scatterTex)
        program.setUniform("uTexelSize", xTexelSize, yTexelSize)
        program.setUniform("uBlurDirection", 1f, 0f)

        glViewport(0, 0, blurTex.width, blurTex.height)
        
        fbo.attachOutputTexture(blurTex)
        renderer.draw()

        // Vertical blur: blurTex -> scatterTex
        program.setUniformSampler("uVolumeTex", blurTex)
        program.setUniform("uBlurDirection", 0f, 1f)

        glViewport(0, 0, volumeTex.width, volumeTex.height)
        
        fbo.attachOutputTexture(volumeTex)
        renderer.draw()

        return volumeTex
    }

    private fun upsampleScattering(depthTex: RenderTexture, volumeTex: RenderTexture): RenderTexture = measure("upsample")
    {
        val program = programs[2]
        val upsampleTex = fbo.getTexture(3)

        program.bind()
        program.setUniformSampler("uVolumeTex", volumeTex, filter = TextureFilter.NEAREST)
        program.setUniformSampler("uDepthTex", depthTex, filter = TextureFilter.NEAREST)
        program.setUniform("uResolution", upsampleTex.width, upsampleTex.height)
        program.setUniform("uVolumeTexSize", volumeTex.width, volumeTex.height)
        program.setUniform("uInvProjection", camera.invProjectionMatrix)
        program.setUniform("uDownsampleFactor", downsampleFactor)
        program.setUniform("uEdgeTolerance", upsampleEdgeTolerance)
        program.setUniform("uUpsampleBlur", upsampleBlur)
        program.setUniform("uBackgroundDepth", max(maxDistance, 1e-3f))

        glViewport(0, 0, upsampleTex.width, upsampleTex.height)
        
        fbo.attachOutputTexture(upsampleTex)
        renderer.draw()

        return upsampleTex
    }

    private fun compose(sceneColorTex: RenderTexture, volumeTex: RenderTexture): RenderTexture = measure("compose")
    {
        val program = programs[3]
        val outputTex = fbo.getTexture(0)

        program.bind()
        program.setUniformSampler("uSceneColorTex", sceneColorTex, filter = TextureFilter.LINEAR)
        program.setUniformSampler("uVolumeTex", volumeTex, filter = TextureFilter.LINEAR)

        glViewport(0, 0, outputTex.width, outputTex.height)
        fbo.attachOutputTexture(outputTex)
        renderer.draw()

        return outputTex
    }

    override fun getTexture(index: Int): RenderTexture? = finalTextures.getOrNull(index) ?: super.getTexture(index)
}
