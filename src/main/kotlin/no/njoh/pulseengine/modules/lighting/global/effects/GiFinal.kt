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
import no.njoh.pulseengine.modules.lighting.global.GlobalIlluminationSystem
import no.njoh.pulseengine.modules.lighting.global.GiSceneRenderer

class GiFinal(
    private val localSceneSurfaceName: String,
    private val exteriorLightSurfaceName: String,
    private val interiorLightSurfaceName: String,
    private val aoSurfaceName: String,
    override val name: String = "gi_final",
    override val order: Int = 0
) : BaseEffect(
    TextureDescriptor(filter = LINEAR, format = RGBA16F),
) {
    override fun loadShaderProgram(engine: PulseEngineInternal) = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/pulseengine/shaders/lighting/global/base.vert")),
        engine.asset.loadNow(FragmentShader("/pulseengine/shaders/lighting/global/final.frag"))
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        val lightSystem = engine.scene.getSystemOfType<GlobalIlluminationSystem>() ?: return inTextures
        val localSceneSurface = engine.gfx.getSurface(localSceneSurfaceName) ?: return inTextures
        val exteriorLightSurface = engine.gfx.getSurface(exteriorLightSurfaceName) ?: return inTextures
        val interiorLightSurface = engine.gfx.getSurface(interiorLightSurfaceName) ?: return inTextures
        val aoSurface = engine.gfx.getSurface(aoSurfaceName) ?: return inTextures
        val aoEnabled = lightSystem.aoRadius > 0f && lightSystem.aoStrength > 0f

        fbo.bind()
        program.bind()
        program.setUniform("uvSampleOffset", GiSceneRenderer.getUvSampleOffset(localSceneSurface, enabled = lightSystem.jitterFix))
        program.setUniform("dithering", lightSystem.dithering)
        program.setUniform("sourceIntensity", lightSystem.sourceIntensity)
        program.setUniform("ambientLight", lightSystem.ambientLight)
        program.setUniform("ambientInteriorLight", lightSystem.ambientInteriorLight)
        program.setUniform("exteriorLightTexUvMax", lightSystem.getExteriorLightTexUvMax(engine))
        program.setUniform("aoEnabled", aoEnabled)
        program.setUniformSampler("baseTex", inTextures[0])
        program.setUniformSampler("localSceneTex", localSceneSurface.getTexture(0, final = false), filter = LINEAR)
        program.setUniformSampler("localSceneMetadataTex", localSceneSurface.getTexture(1, final = false), filter = NEAREST)
        program.setUniformSampler("exteriorLightTex", exteriorLightSurface.getTexture(), filter = lightSystem.lightTexFilter)
        program.setUniformSampler("interiorLightTex", interiorLightSurface.getTexture())
        program.setUniformSampler("aoTex", aoSurface.getTexture())
        renderer.draw()
        fbo.release()

        return fbo.getTextures()
    }
}