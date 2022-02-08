package no.njoh.pulseengine.modules.graphics.renderers

import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.*
import no.njoh.pulseengine.modules.graphics.objects.*
import org.lwjgl.opengl.GL20.*
import java.lang.Float.floatToRawIntBits

class TextureRenderer(
    private val initialCapacity: Int,
    private val renderState: RenderState
) : BatchRenderer {

    private lateinit var vao: VertexArrayObject
    private lateinit var program: ShaderProgram
    private lateinit var vertexBuffer: StaticBufferObject

    private lateinit var data: FloatArray
    private var count = 0
    private val stride = 10

    override fun init()
    {
        vao = VertexArrayObject.createAndBind()

        val vertexLayout = VertexAttributeLayout()
            .withAttribute("vertexPos", 2, GL_FLOAT)

        if (!this::program.isInitialized)
        {
            data = FloatArray(initialCapacity * stride)
            vertexBuffer = StaticBufferObject.createBuffer(floatArrayOf(
                0f, 0f, // Top-left vertex
                1f, 0f, // Top-right vertex
                0f, 1f, // Bottom-left vertex
                1f, 1f  // Bottom-right vertex
            ))
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/default/texture.vert",
                fragmentShaderFileName = "/pulseengine/shaders/default/texture.frag"
            )
        }

        program.bind()
        vertexBuffer.bind()
        program.defineVertexAttributeLayout(vertexLayout)
        vao.release()
    }

    fun drawTexture(texture: Texture, x: Float, y: Float, w: Float, h: Float, rot: Float, xOrigin: Float, yOrigin: Float)
    {
        if (count >= initialCapacity)
            return

        val base = count * stride
        data[base + 0] = x
        data[base + 1] = y
        data[base + 2] = renderState.depth
        data[base + 3] = w
        data[base + 4] = h
        data[base + 5] = rot
        data[base + 6] = xOrigin
        data[base + 7] = yOrigin
        data[base + 8] = renderState.rgba
        data[base + 9] = texture.id.toFloat()

        count++
        renderState.increaseDepth()
    }

    override fun render(surface: Surface2D)
    {
        if (count == 0)
            return

        // Bind VAO and shader program
        vao.bind()
        program.bind()

        // Set matrices
        program.setUniform("projection", surface.camera.projectionMatrix)
        program.setUniform("view", surface.camera.viewMatrix)

        // Set texture unit
        glActiveTexture(GL_TEXTURE0)

        // Draw each texture with separate draw call
        for (i in 0 until count)
        {
            val base = i * stride
            val x = data[base + 0]
            val y = data[base + 1]
            val z = data[base + 2]
            val w = data[base + 3]
            val h = data[base + 4]
            val rotation = data[base + 5]
            val xOrigin = data[base + 6]
            val yOrigin = data[base + 7]
            val rgba = data[base + 8]
            val textureId = data[base + 9].toInt()

            // Bind texture
            if (textureId >= 0)
                glBindTexture(GL_TEXTURE_2D, textureId)

            // Set uniforms
            program.setUniform("position", x, y, z)
            program.setUniform("size", w, h)
            program.setUniform("origin", xOrigin, yOrigin)
            program.setUniform("rotation", rotation)
            program.setUniform("color", floatToRawIntBits(rgba))
            program.setUniform("sampleTexture", textureId >= 0)

            // Draw quad
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        }

        // Release VAO and reset count
        vao.release()
        count = 0
    }

    override fun cleanUp()
    {
        vertexBuffer.delete()
        program.delete()
        vao.delete()
    }
}