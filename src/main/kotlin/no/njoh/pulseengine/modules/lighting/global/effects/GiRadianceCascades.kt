package no.njoh.pulseengine.modules.lighting.global.effects

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.api.TextureFilter.*
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BaseEffect
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.PackedSize
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.lighting.global.GlobalIlluminationSystem
import org.lwjgl.opengl.GL11.glViewport
import kotlin.math.*

class GiRadianceCascades(
    private val localSceneSurfaceName: String,
    private val globalSceneSurfaceName: String,
    private val localSdfSurfaceName: String,
    private val globalSdfSurfaceName: String,
    override val name: String = "radiance_cascades",
    override val order: Int = 0
) : BaseEffect(
    TextureDescriptor(format = RGBA16F, filter = LINEAR, sizeFunc = ::textureSizeFunc),
    TextureDescriptor(format = RGBA16F, filter = LINEAR, sizeFunc = ::textureSizeFunc)
) {
    private var skyColor = Color()
    private var sunColor = Color()
    private var outTextures = mutableListOf<RenderTexture>()

    override fun loadShaderProgram(engine: PulseEngineInternal) = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/pulseengine/shaders/lighting/global/default.vert")),
        engine.asset.loadNow(FragmentShader( "/pulseengine/shaders/lighting/global/radiance_cascades.frag"))
    )

    override fun getTexture(index: Int) = outTextures.getOrNull(index)

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        val lightSystem = engine.scene.getSystemOfType<GlobalIlluminationSystem>() ?: return inTextures
        val localSceneSurface = engine.gfx.getSurface(localSceneSurfaceName) ?: return inTextures
        val globalSceneSurface = engine.gfx.getSurface(globalSceneSurfaceName) ?: return inTextures
        val localSdfSurface = engine.gfx.getSurface(localSdfSurfaceName) ?: return inTextures
        val globalSdfSurface = engine.gfx.getSurface(globalSdfSurfaceName) ?: return inTextures

        val lightTexWidth = fbo.getTexture().width.toFloat()
        val lightTexHeight = fbo.getTexture().height.toFloat()
        val lightTexDiagonal = sqrt(lightTexWidth * lightTexWidth + lightTexHeight * lightTexHeight)

        val localSdfTex = localSdfSurface.getTexture()
        val localSdfTexWidth = localSdfTex.width.toFloat()
        val localSdfTexHeight = localSdfTex.height.toFloat()
        val localSdfTexDiagonal = sqrt(localSdfTexWidth * localSdfTexWidth + localSdfTexHeight * localSdfTexHeight)
        val localSdfTexDistRatio  = floor(lightTexDiagonal) / floor(localSdfTexDiagonal)

        val globalSdfTex = globalSdfSurface.getTexture()
        val globalSdfTexWidth = globalSdfTex.width.toFloat()
        val globalSdfTexHeight = globalSdfTex.height.toFloat()
        val globalSdfTexDiagonal = sqrt(globalSdfTexWidth * globalSdfTexWidth + globalSdfTexHeight * globalSdfTexHeight)
        val globalSdfTexDistRatio = floor(lightTexDiagonal) / floor(globalSdfTexDiagonal)

        val cascadeCount = min(ceil(log2(lightTexDiagonal) / log2(BASE_RAY_COUNT)).toInt() + 1, lightSystem.maxCascades)
        var cascadeIndex = cascadeCount - 1

        val worldScale = max(1f, lightSystem.worldScale)
        val xCamOrigin = globalSceneSurface.camera.origin.x / globalSceneSurface.config.width
        val yCamOrigin = 1f - (globalSceneSurface.camera.origin.y / globalSceneSurface.config.height)

        skyColor.setFrom(lightSystem.skyColor).multiplyRgb(if (lightSystem.skyLight) lightSystem.skyIntensity else 0f)
        sunColor.setFrom(lightSystem.sunColor).multiplyRgb(if (lightSystem.skyLight) lightSystem.sunIntensity else 0f)
        maxCascades = lightSystem.maxCascades
        textureDescriptors.forEachFast()
        {
            it.scale  = lightSystem.lightTexScale
            it.filter = lightSystem.lightTexFilter
        }

        if (outTextures.isEmpty())
            outTextures = mutableListOf(inTextures[0])

        program.bind()
        program.setUniform("resolution", lightTexWidth, lightTexHeight)
        program.setUniform("invResolution", 1f / lightTexWidth, 1f / lightTexHeight)
        program.setUniform("localDistRatio", localSdfTexDistRatio)
        program.setUniform("globalDistRatio", globalSdfTexDistRatio)
        program.setUniform("skyColor", skyColor)
        program.setUniform("sunColor", sunColor)
        program.setUniform("sunAngle", -lightSystem.sunAngle.toRadians())
        program.setUniform("sunDistance", lightSystem.sunDistance)
        program.setUniform("bilinearFix", lightSystem.bilinearFix)
        program.setUniform("intervalLength", lightSystem.intervalLength)
        program.setUniform("cascadeCount", cascadeCount.toFloat())
        program.setUniform("worldScale", worldScale)
        program.setUniform("invWorldScale", 1f / worldScale)
        program.setUniform("traceWorldRays", lightSystem.traceWorldRays)
        program.setUniform("mergeCascades", lightSystem.mergeCascades)
        program.setUniform("maxSteps", lightSystem.maxSteps)
        program.setUniform("camAngle", localSceneSurface.camera.rotation.z)
        program.setUniform("camScale", localSceneSurface.camera.scale.x)
        program.setUniform("camOrigin", xCamOrigin, yCamOrigin)
        program.setUniformSampler("localSceneTex", localSceneSurface.getTexture(0))
        program.setUniformSampler("localMetadataTex", localSceneSurface.getTexture(1))
        program.setUniformSampler("globalSceneTex", globalSceneSurface.getTexture(0))
        program.setUniformSampler("globalMetadataTex", globalSceneSurface.getTexture(1))
        program.setUniformSampler("localSdfTex", localSdfTex)
        program.setUniformSampler("globalSdfTex", globalSdfTex)

        setViewportSizeToFit(fbo.getTexture())

        fbo.bind()
        fbo.clear()

        var i = 0
        while (cascadeIndex >= lightSystem.drawCascade)
        {
            GpuProfiler.measure({ "PASS #" plus i++ plus " (C" plus cascadeIndex plus ")" })
            {
                val currentTex = fbo.getTexture(cascadeIndex % 2)
                fbo.attachOutputTexture(currentTex)
                program.setUniform("cascadeIndex", cascadeIndex.toFloat())
                program.setUniformSampler("upperCascadeTex", outTextures[0])
                renderer.draw()
                outTextures[0] = currentTex
                cascadeIndex--
            }
        }

        fbo.release()

        return outTextures
    }

    private fun setViewportSizeToFit(texture: RenderTexture)
    {
        glViewport(0, 0, texture.width, texture.height)
    }

    companion object
    {
        private val BASE_RAY_COUNT = 4f
        private var maxCascades = 0

        fun textureSizeFunc(width: Int, height: Int, scale: Float): PackedSize
        {
            // Makes sure at least the height is a multiple of the max probe size
            val diagonal = sqrt((width * width + height * height).toFloat())
            val cascadeCount = min(ceil(log2(diagonal) / log2(BASE_RAY_COUNT)).toInt() + 1, max(1, maxCascades))
            val maxProbeSize = 2f.pow(cascadeCount)
            val hScaled = height * scale
            val h = hScaled - min(hScaled / 2, hScaled % maxProbeSize)
            val w = width * (h / height)
            return PackedSize(w, h)
        }
    }
}