package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.NativeMipmapGenerator
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.api.TextureFilter.*
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.primitives.Color.Companion.WHITE
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFiltered
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import org.lwjgl.opengl.GL11.glBlendFunc
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max

class FrostedGlassEffect(
    override val name: String = "frosted_glass",
    override val order: Int = 0,
    var intensity: Float = 0.5f,
    var brightness: Float = 0.75f,
    var radius: Float = 2.5f,
    var zThreshold: Int = -50,
    var disableAfterNumInactiveFrames: Int = 10
) : BaseEffect(
    TextureDescriptor(), // Frost output
    TextureDescriptor(filter = LINEAR_MIPMAP, mipmapGenerator = NativeMipmapGenerator()) // Scene
) {
    private var inactiveFrames = 0

    override fun loadShaderPrograms(engine: PulseEngineInternal) = listOf(
        ShaderProgram.create(
            engine.asset.loadNow(VertexShader("/pulseengine/shaders/effects/frosted_glass.vert")),
            engine.asset.loadNow(FragmentShader("/pulseengine/shaders/effects/frosted_glass.frag"))
        ),
        ShaderProgram.create(
            engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/surface.vert")),
            engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/surface.frag"))
        )
    )

    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        if (disableAfterNumInactiveFrames > 0 && inactiveFrames++ > disableAfterNumInactiveFrames)
            return inTextures // Disable effect if not used

        val frostText = fbo.getTexture(0)
        val sceneTex = fbo.getTextures().last()
        val frostProgram = programs[0]
        val sceneProgram = programs[1]
        val frostRenderer = renderers[0]
        val sceneRenderer = renderers[1]

        // Draw scene
        fbo.bind()
        fbo.attachOutputTexture(sceneTex)
        fbo.clear()
        sceneProgram.bind()
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        engine.gfx.getAllSurfaces().forEachFiltered({ it.config.isVisible && it.config.zOrder > zThreshold })
        {
            sceneProgram.setUniformSampler("tex", it.getTexture())
            sceneRenderer.draw()
        }
        fbo.release()
        glDisable(GL_BLEND)

        // Draw frosted glass effect
        fbo.bind()
        fbo.attachOutputTexture(frostText)
        fbo.clear()
        frostProgram.bind()
        frostProgram.setUniform("intensity", intensity)
        frostProgram.setUniform("brightness", brightness)
        frostProgram.setUniform("radius", radius)
        frostProgram.setUniform("maxLod", floor(log2(max(sceneTex.width, sceneTex.height).toDouble())).toFloat())
        frostProgram.setUniformSampler("tex", sceneTex)
        frostRenderer.draw()
        fbo.release()

        return fbo.getTextures()
    }

    companion object
    {
        fun drawToTargetSurface(engine: PulseEngine, target: Surface, x: Float, y: Float, width: Float, height: Float, cornerRadius: Float = 0f)
        {
            val effectSurface = engine.gfx.getSurface("frosted_glass")
            if (effectSurface == null)
            {
                val newSurface = engine.gfx.createSurface("frosted_glass", textureScale = 0.5f, isVisible = false)
                newSurface.addPostProcessingEffect(FrostedGlassEffect(zThreshold = -80))
                return // Surface will be initialized next frame, return now
            }

            val uMin = x / effectSurface.config.width
            val vMin = y / effectSurface.config.height
            val uMax = (x + width) / effectSurface.config.width
            val vMax = (y + height) / effectSurface.config.height
            val tex = effectSurface.getTexture()
            target.setDrawColor(WHITE)
            target.drawTexture(tex, x, y, width, height, angle = 0f, xOrigin = 0f, yOrigin = 0f, cornerRadius, uMin, vMin, uMax, vMax)

            effectSurface.getPostProcessingEffect<FrostedGlassEffect>()?.inactiveFrames = 0
        }
    }
}