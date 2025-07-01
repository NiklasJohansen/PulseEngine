package no.njoh.pulseengine.modules.lighting.global.effects

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.api.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.api.TextureFormat.R16F
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BaseEffect
import no.njoh.pulseengine.modules.lighting.global.GlobalIlluminationSystem
import org.joml.Matrix4f

class GiAo(
    private val localSceneSurfaceName: String,
    private val localSdfSurfaceName: String,
    override val name: String = "gi_ao",
    override val order: Int = 0
) : BaseEffect(
    TextureDescriptor(filter = LINEAR, format = R16F),
    TextureDescriptor(filter = LINEAR, format = R16F),
) {
    private var readTexIndex  = 0
    private var writeTexIndex = 1
    private val lastViewProjectionMatrix = Matrix4f()

    override fun loadShaderProgram(engine: PulseEngineInternal) = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/pulseengine/shaders/lighting/global/base_reprojected.vert")),
        engine.asset.loadNow(FragmentShader("/pulseengine/shaders/lighting/global/ao.frag"))
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        val lightSystem = engine.scene.getSystemOfType<GlobalIlluminationSystem>() ?: return inTextures
        val localSceneSurface = engine.gfx.getSurface(localSceneSurfaceName) ?: return inTextures
        val localSdfSurface = engine.gfx.getSurface(localSdfSurfaceName) ?: return inTextures

        if (lightSystem.aoRadius == 0f || lightSystem.aoStrength == 0f)
            return inTextures // No AO to apply

        fbo.bind()
        fbo.attachOutputTexture(fbo.getTexture(writeTexIndex))
        program.bind()
        program.setUniform("aoStrength", lightSystem.aoStrength)
        program.setUniform("aoRadius", lightSystem.aoRadius)
        program.setUniform("camScale", localSceneSurface.camera.scale.x)
        program.setUniform("time", (System.currentTimeMillis() % 10_000L) / 10_000f)
        program.setUniform("localSdfTexRes", localSdfSurface.getTexture().width.toFloat(), localSdfSurface.getTexture().height.toFloat())
        program.setUniform("lastViewProjectionMatrix", lastViewProjectionMatrix)
        program.setUniform("currentViewProjectionMatrix", localSceneSurface.camera.viewProjectionMatrix)
        program.setUniformSampler("localSdfTex", localSdfSurface.getTexture())
        program.setUniformSampler("localSceneTex", localSceneSurface.getTexture(0, final = false), filter = LINEAR)
        program.setUniformSampler("localSceneMetadataTex", localSceneSurface.getTexture(1, final = false), filter = NEAREST)
        program.setUniformSampler("lastAoTex", fbo.getTexture(readTexIndex))
        renderer.draw()
        fbo.release()

        writeTexIndex = readTexIndex.also { readTexIndex = writeTexIndex }
        lastViewProjectionMatrix.set(localSceneSurface.camera.viewProjectionMatrix)

        return fbo.getTextures()
    }

    override fun getTexture(index: Int) = frameBuffers.lastOrNull()?.getTextureOrNull(readTexIndex)
}