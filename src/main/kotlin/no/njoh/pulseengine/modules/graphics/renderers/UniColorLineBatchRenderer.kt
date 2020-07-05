package no.njoh.pulseengine.modules.graphics.renderers

import no.njoh.pulseengine.modules.graphics.*
import org.lwjgl.opengl.GL11.*

class UniColorLineBatchRenderer(
    private val initialCapacity: Int,
    private val gfxState: RenderState
) : LineRendererInterface, BatchRenderer {

    private var rgbaColor: Float = 0f

    private lateinit var program: ShaderProgram
    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: FloatBufferObject

    override fun init()
    {
        vao = VertexArrayObject.createAndBind()

        val layout = VertexAttributeLayout()
            .withAttribute("position",3, GL_FLOAT)

        if (!this::program.isInitialized)
        {
            vbo = BufferObject.createAndBind(initialCapacity * layout.stride *  2L)
            program = ShaderProgram.create("/pulseengine/shaders/default/lineUniColor.vert", "/pulseengine/shaders/default/line.frag").bind()
        }

        vbo.bind()
        program.bind()
        program.defineVertexAttributeArray(layout)
        vao.release()
    }

    override fun linePoint(x0: Float, y0: Float)
    {
        vbo.put(x0, y0, gfxState.depth)
        gfxState.increaseDepth()
    }

    override fun line(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        vbo.put(x0, y0, gfxState.depth, x1, y1, gfxState.depth)
        gfxState.increaseDepth()
    }

    override fun render(camera: CameraEngineInterface)
    {
        vao.bind()
        vbo.bind()
        program.bind()
        program.setUniform("projection", camera.projectionMatrix)
        program.setUniform("view", camera.viewMatrix)
        program.setUniform("model", camera.modelMatrix)
        program.setUniform("color", java.lang.Float.floatToIntBits(rgbaColor))

        vbo.flush()
        vbo.draw(GL_LINES, 3)

        vao.release()
    }

    fun setColor(rgba: Float)
    {
        this.rgbaColor = rgba
    }

    override fun cleanup()
    {
        vbo.delete()
        vao.delete()
        program.delete()
    }
}