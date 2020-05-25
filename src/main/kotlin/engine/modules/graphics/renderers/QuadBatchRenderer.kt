package engine.modules.graphics.renderers

import engine.modules.graphics.*
import org.lwjgl.opengl.GL11

class QuadBatchRenderer(
    initialCapacity: Int,
    private val gfxState: GraphicsState
) : BatchRenderer {

    private val stride = 4 * java.lang.Float.BYTES
    private val bytes = 4L * initialCapacity * stride
    private var vertexCount = 0
    private var singleVertexCount = 0
    private lateinit var program: ShaderProgram
    private lateinit var vbo: FloatBufferObject
    private lateinit var ebo: IntBufferObject
    private lateinit var vao: VertexArrayObject

    override fun init()
    {
        if (!this::vao.isInitialized)
        {
            vao = VertexArrayObject.create()
            ebo = VertexBufferObject.createElementBuffer(bytes / 6)
            vbo = VertexBufferObject.create(bytes)
            program = ShaderProgram.create("/engine/shaders/default/default.vert", "/engine/shaders/default/default.frag").use()
        }
        else
        {
            vao.delete()
            vao = VertexArrayObject.create()
        }

        program.use()
        program.defineVertexAttributeArray("position", 3, GL11.GL_FLOAT, stride, 0)
        program.defineVertexAttributeArray("color",1, GL11.GL_FLOAT, stride, 3 * java.lang.Float.BYTES)
        vao.release()
    }

    fun quad(x: Float, y: Float, width: Float, height: Float)
    {
        val depth = gfxState.depth
        val rgba = gfxState.rgba

        vbo.put(
            x, y, depth, rgba,
            x, y+height, depth, rgba,
            x+width, y+height, depth, rgba,
            x+width, y, depth, rgba
        )

        ebo.put(
            vertexCount + 0,
            vertexCount + 1,
            vertexCount + 2,
            vertexCount + 2,
            vertexCount + 3,
            vertexCount + 0
        )

        vertexCount += 4
        gfxState.increaseDepth()
    }

    fun vertex(x: Float, y: Float)
    {
        vbo.put(x, y, gfxState.depth, gfxState.rgba)
        singleVertexCount++

        if (singleVertexCount == 4)
        {
            ebo.put(
                vertexCount + 0,
                vertexCount + 1,
                vertexCount + 2,
                vertexCount + 2,
                vertexCount + 3,
                vertexCount + 0
            )
            singleVertexCount = 0
            vertexCount += 4
            gfxState.increaseDepth()
        }
    }

    override fun render(camera: CameraEngineInterface)
    {
        if (vertexCount == 0)
            return

        vao.bind()

        program.use()
        program.setUniform("projection", gfxState.projectionMatrix)
        program.setUniform("view", camera.viewMatrix)
        program.setUniform("model", gfxState.modelMatrix)

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)

        vbo.flush()
        ebo.flush()
        ebo.draw(GL11.GL_TRIANGLES, 1)

        vertexCount = 0
        vao.release()
    }

    override fun cleanup()
    {
        vbo.delete()
        vao.delete()
        ebo.delete()
        program.delete()
    }
}