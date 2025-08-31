package no.njoh.pulseengine.modules.lighting.global

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureFilter.*
import no.njoh.pulseengine.core.graphics.api.TextureWrapping.*
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.*
import no.njoh.pulseengine.core.graphics.renderers.BatchRenderer
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceConfigInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawInstancedQuads
import org.joml.Vector2f
import org.lwjgl.opengl.GL20.*

class GiSceneRenderer(private val config: SurfaceConfigInternal) : BatchRenderer()
{
    private lateinit var vao: VertexArrayObject
    private lateinit var vertexBuffer: StaticBufferObject
    private lateinit var instanceBuffer: DoubleBufferedFloatObject
    private lateinit var instanceLayout: VertexAttributeLayout
    private lateinit var program: ShaderProgram

    var upscaleSmallSources = false
    var jitterFix = false
    var globalWorldScale = 1f

    override fun init(engine: PulseEngineInternal)
    {
        if (!this::program.isInitialized)
        {
            vertexBuffer = StaticBufferObject.createQuadVertexArrayBuffer()
            instanceBuffer = DoubleBufferedFloatObject.createArrayBuffer()
            instanceLayout = VertexAttributeLayout()
                .withAttribute("worldPos",      3, GL_FLOAT, 1)
                .withAttribute("size",          2, GL_FLOAT, 1)
                .withAttribute("angle",         1, GL_FLOAT, 1)
                .withAttribute("cornerRadius",  1, GL_FLOAT, 1)
                .withAttribute("intensity",     1, GL_FLOAT, 1)
                .withAttribute("coneAngle",     1, GL_FLOAT, 1)
                .withAttribute("radius",        1, GL_FLOAT, 1)
                .withAttribute("uvMin",         2, GL_FLOAT, 1)
                .withAttribute("uvMax",         2, GL_FLOAT, 1)
                .withAttribute("tiling",        2, GL_FLOAT, 1)
                .withAttribute("color",         1, GL_UNSIGNED_INT, 1)
                .withAttribute("texHandle",     1, GL_UNSIGNED_INT, 1)

            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/lighting/global/scene.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/lighting/global/scene.frag"))
            )
        }

        val vertexLayout = VertexAttributeLayout()
            .withAttribute("vertexPos", 2, GL_FLOAT)

        vao = VertexArrayObject.createAndBind()
        program.bind()
        vertexBuffer.bind()
        program.setVertexAttributeLayout(vertexLayout)
        instanceBuffer.bind()
        program.setVertexAttributeLayout(instanceLayout)
        vao.release()
    }

    override fun onInitFrame()
    {
        instanceBuffer.swapBuffers()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: Surface, startIndex: Int, drawCount: Int)
    {
        if (startIndex == 0)
        {
            instanceBuffer.bind()
            instanceBuffer.submit()
            instanceBuffer.release()
        }

        vao.bind()
        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        program.setUniform("uvDrawOffset", getUvSampleOffset(surface, enabled = jitterFix))
        program.setUniform("resolution", surface.config.width.toFloat() * surface.config.textureScale, surface.config.height.toFloat() * surface.config.textureScale)
        program.setUniform("camScale", surface.camera.scale.x)
        program.setUniform("globalWorldScale", globalWorldScale)
        program.setUniform("upscaleSmallSources", upscaleSmallSources)
        program.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays(), wrapping = CLAMP_TO_EDGE, filter = LINEAR)
        drawInstancedQuads(instanceBuffer, instanceLayout, program, drawCount, startIndex)
        vao.release()
    }

    override fun destroy()
    {
        vertexBuffer.destroy()
        instanceBuffer.destroy()
        program.destroy()
        vao.destroy()
    }

    fun drawLight(texture: Texture, x: Float, y: Float, w: Float, h: Float, angle: Float, intensity: Float, coneAngle: Float, radius: Float, cornerRadius: Float = 0f, xTiling: Float = 1f, yTiling: Float = 1f)
    {
        instanceBuffer.fill(18)
        {
            put(x, y, config.currentDepth)
            put(w, h)
            put(angle)
            put(cornerRadius)
            put(intensity)
            put(coneAngle)
            put(radius)
            put(texture.uMin, texture.vMin)
            put(texture.uMax, texture.vMax)
            put(xTiling, yTiling)
            put(config.currentDrawColor)
            put(texture.handle.toFloat())
        }
        increaseBatchSize()
        config.increaseDepth()
    }

    fun drawOccluder(texture: Texture, x: Float, y: Float, w: Float, h: Float, angle: Float, edgeLight: Float, cornerRadius: Float = 0f, xTiling: Float = 1f, yTiling: Float = 1f)
    {
        instanceBuffer.fill(18)
        {
            put(x, y, config.currentDepth)
            put(w, h)
            put(angle)
            put(cornerRadius)
            put(0f)
            put(360f)
            put(edgeLight)
            put(texture.uMin, texture.vMin)
            put(texture.uMax, texture.vMax)
            put(xTiling, yTiling)
            put(config.currentDrawColor)
            put(texture.handle.toFloat())
        }
        increaseBatchSize()
        config.increaseDepth()
    }

    companion object
    {
        private val OFFSET = Vector2f()

        fun getUvSampleOffset(surface: Surface, enabled: Boolean): Vector2f
        {
            if (!enabled) return OFFSET.set(0f, 0f)

            val pixelSize = 1f / surface.config.textureScale
            val viewMatrix = surface.camera.viewMatrix
            val xTranslation = -viewMatrix.m30()
            val yTranslation = viewMatrix.m31()
            val xPixelOffset = xTranslation % pixelSize
            val yPixelOffset = yTranslation % pixelSize

            return OFFSET.set(xPixelOffset / surface.config.width, yPixelOffset / surface.config.height)
        }
    }
}