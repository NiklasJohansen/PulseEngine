package no.njoh.pulseengine.modules.lighting.globalillumination.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BaseEffect
import no.njoh.pulseengine.core.shared.utils.Extensions.component1
import no.njoh.pulseengine.core.shared.utils.Extensions.component2
import no.njoh.pulseengine.modules.lighting.globalillumination.GlobalIlluminationSystem
import no.njoh.pulseengine.modules.lighting.globalillumination.GiSceneRenderer

class GiCompose(
    private val localSceneSurfaceName: String,
    private val distanceFieldSurfaceName: String,
    private val lightSurfaceName: String,
    override val name: String = "compose"
) : BaseEffect(
    TextureDescriptor(filter = LINEAR, format = RGBA16F)
) {
    override fun loadShaderProgram() = ShaderProgram.create(
        vertexShaderFileName = "/pulseengine/shaders/gi/default.vert",
        fragmentShaderFileName = "/pulseengine/shaders/gi/compose.frag"
    )

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        val lightSystem = engine.scene.getSystemOfType<GlobalIlluminationSystem>() ?: return inTextures
        val lightSurface = engine.gfx.getSurface(lightSurfaceName) ?: return inTextures
        val distFieldSurface = engine.gfx.getSurface(distanceFieldSurfaceName) ?: return inTextures
        val sceneSurface = engine.gfx.getSurface(localSceneSurfaceName) ?: return inTextures

        val (xPixelOffset, yPixelOffset) = GiSceneRenderer.calculatePixelOffset(sceneSurface)
        val xSampleOffset = if (lightSystem.fixJitter) xPixelOffset / lightSurface.config.width else 0f
        val ySampleOffset = if (lightSystem.fixJitter) yPixelOffset / lightSurface.config.height else 0f

        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniform("sampleOffset", xSampleOffset, ySampleOffset)
        program.setUniform("dithering", lightSystem.dithering)
        program.setUniform("scale", sceneSurface.camera.scale.x)
        program.setUniform("sourceMultiplier", lightSystem.sourceMultiplier)
        program.setUniform("occluderAmbientLight", lightSystem.occluderAmbientLight)
        program.setUniformSampler("sceneTex", sceneSurface.getTexture(0, final = false), filter = LINEAR)
        program.setUniformSampler("sceneMetadataTex", sceneSurface.getTexture(1, final = false), filter = LINEAR)
        program.setUniformSampler("lightTex", lightSurface.getTexture())
        program.setUniformSampler("internalDistanceFieldTex", distFieldSurface.getTexture(1))
        renderer.draw()
        fbo.release()

        return fbo.getTextures()
    }
}