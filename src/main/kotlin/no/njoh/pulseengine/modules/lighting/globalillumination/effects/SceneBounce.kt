package no.njoh.pulseengine.modules.lighting.globalillumination.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.Attachment.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.api.Attachment.COLOR_TEXTURE_1
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.postprocessing.SinglePassEffect
import no.njoh.pulseengine.modules.lighting.globalillumination.GlobalIlluminationSystem
import org.joml.Matrix4f

class SceneBounce(
    private val lightSurfaceName: String,
    override val name: String = "bounce"
) : SinglePassEffect(
    textureFilter = NEAREST,
    textureFormat = RGBA16F,
    attachments = listOf(COLOR_TEXTURE_0, COLOR_TEXTURE_1) // Scene radiance, Scene metadata
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
        program.setUniform("lightBounce", lightSystem.lightBounce)
        program.setUniform("lastViewProjectionMatrix", lastViewProjectionMatrix)
        program.setUniform("currentViewProjectionMatrix", lightSurface.camera.viewProjectionMatrix)
        program.setUniform("resolution", fbo.width.toFloat(), fbo.height.toFloat())
        renderer.drawTextures(
            inTextures[0], // Radiance
            inTextures[1], // Metadata
            lightSurface.getTexture(), // Light from previous frame
        )
        fbo.release()

        lastViewProjectionMatrix.set(lightSurface.camera.viewProjectionMatrix)

        return fbo.getTextures()
    }
}