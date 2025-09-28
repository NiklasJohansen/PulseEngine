package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureHandle
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.*
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceConfigInternal
import org.lwjgl.opengl.GL20.*
import java.lang.Float.floatToRawIntBits

class RenderTextureRenderer(private val config: SurfaceConfigInternal) : BatchRenderer()
{
    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: StaticBufferObject
    private lateinit var program: ShaderProgram
    private lateinit var data: FloatArray

    private var readCount = 0
    private var writeCount = 0
    private var readOffset = 0
    private var writeOffset = 0
    private val capacity = 50
    private val stride = 15

    override fun init(engine: PulseEngineInternal)
    {
        if (!this::program.isInitialized)
        {
            readOffset = 0
            writeOffset = capacity * stride
            data = FloatArray(capacity * stride * 2)
            vbo = StaticBufferObject.createQuadVertexArrayBuffer()
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/render_texture.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/render_texture.frag"))
            )
        }

        val vertexLayout = VertexAttributeLayout()
            .withAttribute("vertexPos", 2, GL_FLOAT)

        vao = VertexArrayObject.createAndBind()
        vbo.bind()
        program.bind()
        program.setVertexAttributeLayout(vertexLayout)
        vao.release()
    }

    override fun onInitFrame()
    {
        readOffset = writeOffset.also { writeOffset = readOffset }
        readCount = writeCount.also { writeCount = 0 }
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: Surface, startIndex: Int, drawCount: Int)
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
            val base = readOffset + i * stride
            val x =            data[base + 0]
            val y =            data[base + 1]
            val z =            data[base + 2]
            val w =            data[base + 3]
            val h =            data[base + 4]
            val angle =        data[base + 5]
            val xOrigin =      data[base + 6]
            val yOrigin =      data[base + 7]
            val cornerRadius = data[base + 8]
            val uMin =         data[base + 9]
            val vMin =         data[base + 10]
            val uMax =         data[base + 11]
            val vMax =         data[base + 12]
            val rgba =         data[base + 13]
            val textureId =    data[base + 14].toInt()

            // Bind texture
            if (textureId != TextureHandle.NONE.textureIndex)
                program.setUniformSampler("tex", TextureHandle.create(0, textureId))

            // Set uniforms
            program.setUniform("position", x, y, z)
            program.setUniform("size", w, h)
            program.setUniform("origin", xOrigin, yOrigin)
            program.setUniform("angle", angle)
            program.setUniform("color", floatToRawIntBits(rgba))
            program.setUniform("cornerRadius", cornerRadius)
            program.setUniform("uvMinMax", uMin, vMin, uMax, vMax)
            program.setUniform("sampleTexture", textureId != TextureHandle.NONE.textureIndex)

            // Draw quad
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        }

        // Release VAO and reset count
        vao.release()
        readCount = 0
    }

    override fun destroy()
    {
        vbo.destroy()
        program.destroy()
        vao.destroy()
    }

    fun draw(texture: RenderTexture, x: Float, y: Float, w: Float, h: Float, angle: Float, xOrigin: Float, yOrigin: Float, cornerRadius: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float)
    {
        if (writeCount >= capacity)
            return

        val base = writeOffset + writeCount * stride
        data[base + 0] = x
        data[base + 1] = y
        data[base + 2] = config.currentDepth
        data[base + 3] = w
        data[base + 4] = h
        data[base + 5] = angle
        data[base + 6] = xOrigin
        data[base + 7] = yOrigin
        data[base + 8] = cornerRadius
        data[base + 9] = uMin
        data[base + 10] = vMin
        data[base + 11] = uMax
        data[base + 12] = vMax
        data[base + 13] = config.currentDrawColor
        data[base + 14] = texture.handle.textureIndex.toFloat()
        writeCount++
        config.increaseDepth()
        increaseBatchSize()
    }
}