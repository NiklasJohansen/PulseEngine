package engine.modules.graphics.renderers

import engine.modules.graphics.*
import org.lwjgl.opengl.ARBInternalformatQuery2.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.glBindTexture

class FrameBufferRenderer
{
    private val stride = 4 * java.lang.Float.BYTES
    private val bytes = 6L * stride // 6 vertices per quad

    private lateinit var program: ShaderProgram
    private lateinit var vbo: FloatBufferObject
    private lateinit var vao: VertexArrayObject
    private var initialized = false

    fun init()
    {
        vao = VertexArrayObject.create()

        if(!initialized)
        {
            vbo = VertexBufferObject.create(bytes)
            program = ShaderProgram.create("/engine/shaders/default/frameBuffer.vert", "/engine/shaders/default/frameBuffer.frag").use()
            initialized = true
        }

        vbo.bind()
        program.use()
        program.defineVertexAttributeArray("position", 2, GL11.GL_FLOAT, stride, 0)
        program.defineVertexAttributeArray("texCoord", 2, GL11.GL_FLOAT, stride, 2 * java.lang.Float.BYTES)
    }

    fun render(x: Float, y: Float, w: Float, h: Float, frameBufferObject: FrameBufferObject)
    {
        vbo.put(
            x, y, 0f, 0f,       // v1
            x, y+h, 0f, 1f,     // v2
            x+w, y+h, 1f, 1f,   // v3
            x+w, y+h, 1f, 1f,   // v3
            x+w, y, 1f, 0f,     // v4
            x, y, 0f, 0f        // v1
        )

        program.use()
        vao.bind()
        glBindTexture(GL_TEXTURE_2D,  frameBufferObject.textureId)
        vbo.draw(GL_TRIANGLES, 4)
    }

    fun cleanup()
    {
        vbo.delete()
        vao.delete()
        program.delete()
    }
}