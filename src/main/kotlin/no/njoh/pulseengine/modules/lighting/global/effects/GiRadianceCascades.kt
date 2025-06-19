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
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.lighting.global.GiSceneRenderer
import no.njoh.pulseengine.modules.lighting.global.GlobalIlluminationSystem
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11.glViewport
import kotlin.math.*

class GiRadianceCascades(
    private val localSceneSurfaceName: String,
    private val globalSceneSurfaceName: String,
    private val localSdfSurfaceName: String,
    private val globalSdfSurfaceName: String,
    private val normalMapSurfaceName: String,
    override val name: String = "gi_radiance_cascades",
    override val order: Int = 0
) : BaseEffect(
    TextureDescriptor(format = RGBA16F, filter = LINEAR),
    TextureDescriptor(format = RGBA16F, filter = LINEAR)
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
        val normalMapSurface = engine.gfx.getSurface(normalMapSurfaceName) ?: return inTextures

        val localSdfTex = localSdfSurface.getTexture()
        val globalSdfTex = globalSdfSurface.getTexture()
        val lightTexWidth = fbo.getTexture().width.toFloat()
        val lightTexHeight = fbo.getTexture().height.toFloat()
        val lightTexDiagonal = sqrt(lightTexWidth * lightTexWidth + lightTexHeight * lightTexHeight)
        val cascadeCount = min(ceil(log2(lightTexDiagonal) / log2(BASE_RAY_COUNT)).toInt() + 1, lightSystem.maxCascades)
        var cascadeIndex = cascadeCount - 1
        val worldScale = max(1f, lightSystem.worldScale)

        skyColor.setFrom(lightSystem.skyColor).multiplyRgb(if (lightSystem.skyLight) lightSystem.skyIntensity else 0f)
        sunColor.setFrom(lightSystem.sunColor).multiplyRgb(if (lightSystem.skyLight) lightSystem.sunIntensity else 0f)
        textureDescriptors.forEachFast { it.filter = lightSystem.lightTexFilter }

        if (outTextures.isEmpty())
            outTextures = mutableListOf(inTextures[0])

        program.bind()
        program.setUniform("resolution", lightTexWidth, lightTexHeight)
        program.setUniform("invResolution", 1f / lightTexWidth, 1f / lightTexHeight)
        program.setUniform("localSceneRes", localSdfTex.width.toFloat(), localSdfTex.height.toFloat())
        program.setUniform("globalSceneRes", globalSdfTex.width.toFloat(), globalSdfTex.height.toFloat())
        program.setUniform("localSceneScale", lightSystem.localSceneTexScale  / lightSystem.lightTexScale)
        program.setUniform("globalSceneScale", lightSystem.globalSceneTexScale / lightSystem.lightTexScale)
        program.setUniform("skyColor", skyColor)
        program.setUniform("sunColor", sunColor)
        program.setUniform("sunAngle", -lightSystem.sunAngle.toRadians())
        program.setUniform("sunDistance", lightSystem.sunDistance)
        program.setUniform("bilinearFix", lightSystem.bilinearFix)
        program.setUniform("intervalLength", lightSystem.intervalLength)
        program.setUniform("cascadeCount", cascadeCount.toFloat())
        program.setUniform("normalMapScale", lightSystem.normalMapScale)
        program.setUniform("worldScale", worldScale)
        program.setUniform("traceWorldRays", lightSystem.traceWorldRays)
        program.setUniform("mergeCascades", lightSystem.mergeCascades)
        program.setUniform("maxSteps", lightSystem.maxSteps)
        program.setUniform("camAngle", localSceneSurface.camera.rotation.z)
        program.setUniform("camScale", localSceneSurface.camera.scale.x)
        program.setUniform("uvSampleOffset", GiSceneRenderer.getUvSampleOffset(localSceneSurface, enabled = lightSystem.jitterFix))
        program.setUniform("localVPM", localSceneSurface.camera.viewProjectionMatrix)
        program.setUniform("localInvVPM", localSceneSurface.camera.viewProjectionMatrix.invert(invLocalVP))
        program.setUniform("globalVPM", globalSceneSurface.camera.viewProjectionMatrix)
        program.setUniform("globalInvVPM", globalSceneSurface.camera.viewProjectionMatrix.invert(invGlobalVP))
        program.setUniformSampler("localSceneTex", localSceneSurface.getTexture(0))
        program.setUniformSampler("localMetadataTex", localSceneSurface.getTexture(1))
        program.setUniformSampler("globalSceneTex", globalSceneSurface.getTexture(0))
        program.setUniformSampler("globalMetadataTex", globalSceneSurface.getTexture(1))
        program.setUniformSampler("localSdfTex", localSdfTex)
        program.setUniformSampler("globalSdfTex", globalSdfTex)
        program.setUniformSampler("normalMapTex", normalMapSurface.getTexture())

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
        const val BASE_RAY_COUNT = 4f
        private val invLocalVP = Matrix4f()
        private val invGlobalVP = Matrix4f()
    }
}