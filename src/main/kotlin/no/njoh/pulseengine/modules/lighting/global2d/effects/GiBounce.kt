package no.njoh.pulseengine.modules.lighting.global2d.effects

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.COLOR_TEXTURE_1
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.*
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureDescriptor
import no.njoh.pulseengine.core.graphics.postprocessing.BaseEffect
import no.njoh.pulseengine.modules.lighting.global2d.GlobalIlluminationSystem2D
import org.joml.Matrix4f

class GiBounce(
    private val exteriorLightSurfaceName: String,
    override val name: String = "gi_bounce",
    override val order: Int = 0
) : BaseEffect(
    TextureDescriptor(filter = NEAREST, format = RGBA16F, attachmentPoint = COLOR_TEXTURE_0), // Scene radiance
    TextureDescriptor(filter = NEAREST, format = RGBA16F, attachmentPoint = COLOR_TEXTURE_1)  // Scene metadata
) {
    private val lastViewProjectionMatrix = Matrix4f()

    override fun loadShaderProgram(engine: PulseEngineInternal) = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/pulseengine/shaders/lighting/global/base_reprojected.vert")),
        engine.asset.loadNow(FragmentShader("/pulseengine/shaders/lighting/global/bounce.frag"))
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        val lightSystem = engine.scene.getSystemOfType<GlobalIlluminationSystem2D>() ?: return inTextures
        val exteriorLightSurface = engine.gfx.getSurface(exteriorLightSurfaceName) ?: return inTextures
        val exteriorLightTexture = exteriorLightSurface.getTexture()

        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("lastViewProjectionMatrix", lastViewProjectionMatrix)
        program.setUniform("currentViewProjectionMatrix", exteriorLightSurface.camera.viewProjectionMatrix)
        program.setUniform("bounceAccumulation", lightSystem.bounceAccumulation)
        program.setUniform("bounceRadius", lightSystem.bounceRadius)
        program.setUniform("bounceEdgeFade", lightSystem.bounceEdgeFade)
        program.setUniform("resolution", exteriorLightTexture.width.toFloat(), exteriorLightTexture.height.toFloat())
        program.setUniform("scale", exteriorLightSurface.camera.scale.x)
        program.setUniform("exteriorLightTexUvMax", lightSystem.getExteriorLightTexUvMax(engine))
        program.setUniformSampler("sceneTex", inTextures[0])
        program.setUniformSampler("sceneMetaTex", inTextures[1])
        program.setUniformSampler("exteriorLightTex", exteriorLightTexture)
        fullscreenPass.draw()
        fbo.release()

        lastViewProjectionMatrix.set(exteriorLightSurface.camera.viewProjectionMatrix)

        return fbo.getTextures()
    }
}