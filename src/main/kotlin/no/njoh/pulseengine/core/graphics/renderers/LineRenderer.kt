package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.DoubleBufferedFloatObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceConfigInternal
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawLineVertices
import org.lwjgl.opengl.GL11.*

class LineRenderer(private val config: SurfaceConfigInternal) : Renderer()
{
    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: DoubleBufferedFloatObject
    private lateinit var program: ShaderProgram
    private var vertices = 0

    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::program.isInitialized)
        {
            vbo = DoubleBufferedFloatObject.createArrayBuffer()
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/line.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/line.frag"))
            )
        }

        vao = VertexArrayObject.createAndBind()
        vbo.bind()
        program.bind()

        VertexAttributeLayout()
            .withAttribute("position",  3, GL_FLOAT)
            .withAttribute("rgbaColor", 1, GL_FLOAT)
            .bind(program)

        vao.release()
    }

    override fun onInitFrame()
    {
        vbo.swapBuffers()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex == 0)
        {
            vbo.bind()
            vbo.submit()
            vbo.release()
        }

        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        drawLineVertices(vao, startIndex * 2, drawCount * 2)
    }

    override fun destroy()
    {
        vbo.destroy()
        vao.destroy()
        program.destroy()
    }

    fun lineVertex(x: Float, y: Float)
    {
        vbo.fill(4)
        {
            put(x, config.height - y, config.currentDepth, config.currentDrawColor)
        }
        config.increaseDepth()
        vertices++
        if (vertices == 2)
        {
            vertices = 0
            increaseBatchSize()
        }
    }

    fun line(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        val depth = config.currentDepth
        val height = config.height
        val rgba = config.currentDrawColor
        vbo.fill(8)
        {
            put(x0, height - y0, depth, rgba)
            put(x1, height - y1, depth, rgba)
        }
        config.increaseDepth()
        increaseBatchSize()
    }
}