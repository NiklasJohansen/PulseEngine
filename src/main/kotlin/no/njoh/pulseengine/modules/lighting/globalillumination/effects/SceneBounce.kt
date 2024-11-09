package no.njoh.pulseengine.modules.lighting.globalillumination.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.Attachment.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.api.Attachment.COLOR_TEXTURE_1
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.api.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.postprocessing.BaseEffect
import no.njoh.pulseengine.modules.lighting.globalillumination.GlobalIlluminationSystem
import org.joml.Matrix4f

class SceneBounce(
    private val lightSurfaceName: String,
    override val name: String = "bounce"
) : BaseEffect(
    TextureDescriptor(filter = NEAREST, format = RGBA16F, attachment = COLOR_TEXTURE_0), // Scene radiance
    TextureDescriptor(filter = NEAREST, format = RGBA16F, attachment = COLOR_TEXTURE_1)  // Scene metadata
) {
    private var lastViewProjectionMatrix = Matrix4f()

    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/gi/bounce.vert",
        fragmentShaderFileName = "/pulseengine/shaders/gi/bounce.frag"
    )

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        val lightSystem = engine.scene.getSystemOfType<GlobalIlluminationSystem>() ?: return inTextures
        val lightSurface = engine.gfx.getSurface(lightSurfaceName) ?: return inTextures

        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("lastViewProjectionMatrix", lastViewProjectionMatrix)
        program.setUniform("currentViewProjectionMatrix", lightSurface.camera.viewProjectionMatrix)
        program.setUniform("bounceAccumulation", lightSystem.bounceAccumulation)
        program.setUniform("resolution", fbo.width.toFloat(), fbo.height.toFloat())
        program.setUniform("scale", lightSurface.camera.scale.x)
        program.setUniformSampler("sceneTex", inTextures[0])
        program.setUniformSampler("sceneMetaTex", inTextures[1])
        program.setUniformSampler("lightTex", lightSurface.getTexture())
        renderer.draw()
        fbo.release()

        lastViewProjectionMatrix.set(lightSurface.camera.viewProjectionMatrix)

        return fbo.getTextures()
    }
}