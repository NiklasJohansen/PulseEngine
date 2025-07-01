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
import org.joml.Matrix4f

class GiInterior(
    private val localSceneSurfaceName: String,
    private val localSdfSurfaceName: String,
    private val exteriorLightSurfaceName: String,
    private val normalMapSurfaceName: String,
    override val name: String = "gi_interior",
    override val order: Int = 0
) : BaseEffect(
    TextureDescriptor(filter = LINEAR, format = RGBA16F), // Ping-pong textures
    TextureDescriptor(filter = LINEAR, format = RGBA16F)
) {
    private var readTexIndex  = 0
    private var writeTexIndex = 1
    private var lastViewProjectionMatrix = Matrix4f()

    override fun loadShaderProgram(engine: PulseEngineInternal) = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/pulseengine/shaders/lighting/global/base_reprojected.vert")),
        engine.asset.loadNow(FragmentShader("/pulseengine/shaders/lighting/global/interior.frag"))
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        val lightSystem = engine.scene.getSystemOfType<GlobalIlluminationSystem>() ?: return inTextures
        val exteriorLightSurface = engine.gfx.getSurface(exteriorLightSurfaceName) ?: return inTextures
        val normalSurface = engine.gfx.getSurface(normalMapSurfaceName) ?: return inTextures
        val localSceneSurface = engine.gfx.getSurface(localSceneSurfaceName) ?: return inTextures
        val localSdfSurface = engine.gfx.getSurface(localSdfSurfaceName) ?: return inTextures
        val localSdfTexture = localSdfSurface.getTexture()

        fbo.bind()
        fbo.attachOutputTexture(fbo.getTexture(writeTexIndex))
        program.bind()
        program.setUniform("localSdfRes", localSdfTexture.width.toFloat(), localSdfTexture.height.toFloat())
        program.setUniform("uvSampleOffset", GiSceneRenderer.getUvSampleOffset(localSceneSurface, enabled = lightSystem.jitterFix))
        program.setUniform("scale", localSceneSurface.camera.scale.x)
        program.setUniform("exteriorLightTexUvMax", lightSystem.getExteriorLightTexUvMax(engine))
        program.setUniform("normalMapScale", lightSystem.normalMapScale)
        program.setUniform("camAngle", exteriorLightSurface.camera.rotation.z)
        program.setUniform("lastViewProjectionMatrix", lastViewProjectionMatrix)
        program.setUniform("currentViewProjectionMatrix", exteriorLightSurface.camera.viewProjectionMatrix)
        program.setUniform("time", (System.currentTimeMillis() % 10_000L) / 10_000f)
        program.setUniformSampler("localSceneTex", localSceneSurface.getTexture(0, final = false), filter = LINEAR)
        program.setUniformSampler("localSceneMetadataTex", localSceneSurface.getTexture(1, final = false), filter = NEAREST)
        program.setUniformSampler("localSdfTex", localSdfTexture)
        program.setUniformSampler("normalMapTex", normalSurface.getTexture())
        program.setUniformSampler("exteriorLightTex", exteriorLightSurface.getTexture(), filter = lightSystem.lightTexFilter)
        program.setUniformSampler("lastInteriorTex", fbo.getTexture(readTexIndex))
        renderer.draw()
        fbo.release()

        writeTexIndex = readTexIndex.also { readTexIndex = writeTexIndex }
        lastViewProjectionMatrix.set(exteriorLightSurface.camera.viewProjectionMatrix)

        return fbo.getTextures()
    }

    override fun getTexture(index: Int) = frameBuffers.lastOrNull()?.getTextureOrNull(readTexIndex)
}