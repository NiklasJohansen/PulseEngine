package no.njoh.pulseengine.modules.lighting.global.effects

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.api.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BaseEffect
import no.njoh.pulseengine.core.shared.utils.Extensions.component1
import no.njoh.pulseengine.core.shared.utils.Extensions.component2
import no.njoh.pulseengine.modules.lighting.global.GlobalIlluminationSystem
import no.njoh.pulseengine.modules.lighting.global.GiSceneRenderer

class GiCompose(
    private val localSceneSurfaceName: String,
    private val localSdfSurfaceName: String,
    private val lightSurfaceName: String,
    private val normalMapSurfaceName: String,
    override val name: String = "compose",
    override val order: Int = 0
) : BaseEffect(
    TextureDescriptor(filter = LINEAR, format = RGBA16F)
) {
    override fun loadShaderProgram(engine: PulseEngineInternal) = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/pulseengine/shaders/lighting/global/default.vert")),
        engine.asset.loadNow(FragmentShader("/pulseengine/shaders/lighting/global/compose.frag"))
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        val lightSystem = engine.scene.getSystemOfType<GlobalIlluminationSystem>() ?: return inTextures
        val lightSurface = engine.gfx.getSurface(lightSurfaceName) ?: return inTextures
        val normalSurface = engine.gfx.getSurface(normalMapSurfaceName) ?: return inTextures
        val localSceneSurface = engine.gfx.getSurface(localSceneSurfaceName) ?: return inTextures
        val localSdfSurface = engine.gfx.getSurface(localSdfSurfaceName) ?: return inTextures
        val localSdfTexture = localSdfSurface.getTexture()

        val (uMax, vMax) = lightSystem.getLightTexUvMax(engine)
        val (xPixelOffset, yPixelOffset) = GiSceneRenderer.calculatePixelOffset(localSceneSurface)
        val xSampleOffset = if (lightSystem.jitterFix) xPixelOffset / lightSurface.config.width else 0f
        val ySampleOffset = if (lightSystem.jitterFix) yPixelOffset / lightSurface.config.height else 0f

        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("localSdfRes", localSdfTexture.width.toFloat(), localSdfTexture.height.toFloat())
        program.setUniform("sampleOffset", xSampleOffset, ySampleOffset)
        program.setUniform("dithering", lightSystem.dithering)
        program.setUniform("scale", localSceneSurface.camera.scale.x)
        program.setUniform("sourceMultiplier", lightSystem.sourceMultiplier)
        program.setUniform("ambientLight", lightSystem.ambientLight)
        program.setUniform("ambientOccluderLight", lightSystem.ambientOccluderLight)
        program.setUniform("lightTexUvMax", uMax, vMax)
        program.setUniform("normalMapScale", lightSystem.normalMapScale)
        program.setUniformSampler("localSceneTex", localSceneSurface.getTexture(0, final = false), filter = LINEAR)
        program.setUniformSampler("localSceneMetadataTex", localSceneSurface.getTexture(1, final = false), filter = NEAREST)
        program.setUniformSampler("lightTex", lightSurface.getTexture(), filter = lightSystem.lightTexFilter)
        program.setUniformSampler("localSdfTex", localSdfTexture)
        program.setUniformSampler("normalMapTex", normalSurface.getTexture())
        renderer.draw()
        fbo.release()

        return fbo.getTextures()
    }
}