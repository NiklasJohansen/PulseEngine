package no.njoh.pulseengine.core.graphics.surface.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.shader.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.gpu.buffer.DoubleBufferedFloatObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.StaticBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.VertexArrayObject
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceConfigInternal
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawInstancedQuads
import org.lwjgl.opengl.GL20.*

class TextureRenderer(
    private val config: SurfaceConfigInternal,
    override val order: Int = 70,
    var alphaDiscardThreshold: Float = 0.4f
) : Renderer() {

    private lateinit var vao: VertexArrayObject
    private lateinit var vertexBuffer: StaticBufferObject
    private lateinit var instanceBuffer: DoubleBufferedFloatObject
    private lateinit var instanceLayout: VertexAttributeLayout
    private lateinit var program: ShaderProgram

    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::program.isInitialized)
        {
            vertexBuffer = StaticBufferObject.createQuadVertexArrayBuffer()
            instanceBuffer = DoubleBufferedFloatObject.createArrayBuffer()
            instanceLayout = VertexAttributeLayout()
                .withAttribute("worldPos",     3, GL_FLOAT, 1)
                .withAttribute("size",         2, GL_FLOAT, 1)
                .withAttribute("origin",       2, GL_FLOAT, 1)
                .withAttribute("angle",        1, GL_FLOAT, 1)
                .withAttribute("cornerRadius", 1, GL_FLOAT, 1)
                .withAttribute("uvMin",        2, GL_FLOAT, 1)
                .withAttribute("uvMax",        2, GL_FLOAT, 1)
                .withAttribute("tiling",       2, GL_FLOAT, 1)
                .withAttribute("color",        1, GL_UNSIGNED_INT, 1)
                .withAttribute("texHandle",    1, GL_UNSIGNED_INT, 1)

            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/texture.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/texture.frag"))
            )
        }

        vao = VertexArrayObject.createAndBind()
        program.bind()
        vertexBuffer.bind()
        VertexAttributeLayout().withAttribute("vertexPos", 2, GL_FLOAT).bind(program)
        instanceBuffer.bind()
        instanceLayout.bind(program)
        vao.release()
    }

    override fun onInitFrame(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        instanceBuffer.swapBuffers()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex == 0)
        {
            instanceBuffer.bind()
            instanceBuffer.submit()
            instanceBuffer.release()
        }

        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        program.setUniform("alphaDiscardThreshold", alphaDiscardThreshold)
        program.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
        drawInstancedQuads(vao, instanceBuffer, instanceLayout, program, startIndex, drawCount)
    }

    override fun destroy(engine: PulseEngineInternal)
    {
        vertexBuffer.destroy()
        instanceBuffer.destroy()
        program.destroy()
        vao.destroy()
    }

    fun draw(texture: Texture, x: Float, y: Float, w: Float, h: Float, angle: Float, xOrigin: Float, yOrigin: Float, cornerRadius: Float)
    {
        instanceBuffer.fill(17)
        {
            put(x, config.height - y, config.currentDepth)
            put(w, h)
            put(xOrigin, 1f - yOrigin)
            put(-angle)
            put(cornerRadius)
            put(texture.uMin, texture.vMin)
            put(texture.uMax, texture.vMax)
            put(1f, 1f) // U/V Tiling
            put(config.currentDrawColor)
            put(texture.handle.toFloat())
        }
        increaseBatchSize()
        config.increaseDepth()
    }

    fun draw(
        texture: Texture,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        angle: Float,
        xOrigin: Float,
        yOrigin: Float,
        cornerRadius: Float,
        uMin: Float,
        vMin: Float,
        uMax: Float,
        vMax: Float,
        xTiling: Float,
        yTiling: Float
    ) {
        instanceBuffer.fill(17)
        {
            put(x, config.height - y, config.currentDepth)
            put(w, h)
            put(xOrigin, 1f - yOrigin)
            put(-angle)
            put(cornerRadius)
            put(texture.uMax * uMin, texture.vMax * vMin)
            put(texture.uMax * uMax, texture.vMax * vMax)
            put(xTiling, yTiling)
            put(config.currentDrawColor)
            put(texture.handle.toFloat())
        }
        increaseBatchSize()
        config.increaseDepth()
    }
}