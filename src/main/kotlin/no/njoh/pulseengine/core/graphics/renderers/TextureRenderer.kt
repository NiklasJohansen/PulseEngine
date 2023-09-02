package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.*
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.*
import org.lwjgl.opengl.GL20.*
import java.lang.Float.floatToRawIntBits

class TextureRenderer(
    private val initialCapacity: Int,
    private val context: RenderContextInternal
) : BatchRenderer() {

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
        program.setVertexAttributeLayout(vertexLayout)
        vao.release()
    }

    fun drawTexture(texture: Texture, x: Float, y: Float, w: Float, h: Float, rot: Float, xOrigin: Float, yOrigin: Float)
    {
        if (count >= initialCapacity)
            return

        val base = count * stride
        data[base + 0] = x
        data[base + 1] = y
        data[base + 2] = context.depth
        data[base + 3] = w
        data[base + 4] = h
        data[base + 5] = rot
        data[base + 6] = xOrigin
        data[base + 7] = yOrigin
        data[base + 8] = context.drawColor
        data[base + 9] = texture.handle.textureIndex.toFloat()

        count++
        context.increaseDepth()
        increaseBatchSize()
    }

    override fun onRenderBatch(surface: Surface2D, startIndex: Int, drawCount: Int)
    {
        // Bind VAO and shader program
        vao.bind()
        program.bind()

        // Set matrices
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)

        // Set texture unit
        glActiveTexture(GL_TEXTURE0)

        // Draw each texture with separate draw call
        for (i in startIndex until startIndex + drawCount)
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
            if (textureId != 1000) // See TextureHandle.NONE
                glBindTexture(GL_TEXTURE_2D, textureId)

            // Set uniforms
            program.setUniform("position", x, y, z)
            program.setUniform("size", w, h)
            program.setUniform("origin", xOrigin, yOrigin)
            program.setUniform("rotation", rotation)
            program.setUniform("color", floatToRawIntBits(rgba))
            program.setUniform("sampleTexture", textureId != 1000)

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