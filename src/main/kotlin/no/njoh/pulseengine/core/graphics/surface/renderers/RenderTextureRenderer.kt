package no.njoh.pulseengine.core.graphics.surface.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureHandle
import no.njoh.pulseengine.core.graphics.gpu.shader.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.gpu.buffer.StaticBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.VertexArrayObject
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.DEPTH_TEXTURE
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureAlphaMode.PREMULTIPLIED
import no.njoh.pulseengine.core.graphics.surface.SurfaceConfigInternal
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawTriangleStripVertices
import no.njoh.pulseengine.core.shared.primitives.Border
import no.njoh.pulseengine.core.shared.primitives.CornerRadius
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.GL20.*
import java.lang.Float.floatToRawIntBits

class RenderTextureRenderer(
    private val config: SurfaceConfigInternal,
    override val order: Int = 50
) : Renderer() {

    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: StaticBufferObject
    private lateinit var program: ShaderProgram
    private lateinit var data: FloatArray

    private var readCount   = 0
    private var writeCount  = 0
    private var readOffset  = 0
    private var writeOffset = 0
    private val capacity    = 100
    private val stride      = 20

    override fun init(engine: PulseEngineInternal, surface: SurfaceInternal)
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

        vao = VertexArrayObject.createAndBind()
        vbo.bind()
        program.bind()
        VertexAttributeLayout().withAttribute("vertexPos", 2, GL_FLOAT).bind(program)
        vao.release()
    }

    override fun onInitFrame(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        readOffset = writeOffset.also { writeOffset = readOffset }
        readCount = writeCount.also { writeCount = 0 }
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)

        // Set texture unit
        glActiveTexture(GL_TEXTURE0)

        // TODO: Batch draw calls
        // Draw each texture with separate draw call
        for (i in startIndex until startIndex + drawCount)
        {
            val base          = readOffset + i * stride
            val x             = data[base + 0]
            val y             = data[base + 1]
            val z             = data[base + 2]
            val w             = data[base + 3]
            val h             = data[base + 4]
            val angle         = data[base + 5]
            val xOrigin       = data[base + 6]
            val yOrigin       = data[base + 7]
            val cornerRadius0 = data[base + 8]
            val cornerRadius1 = data[base + 9]
            val border0       = data[base + 10]
            val border1       = data[base + 11]
            val uMin          = data[base + 12]
            val vMin          = data[base + 13]
            val uMax          = data[base + 14]
            val vMax          = data[base + 15]
            val rgba          = data[base + 16]
            val textureId     = data[base + 17].toInt()
            val isDepth       = data[base + 18]
            val alphaMode     = data[base + 19]

            // Bind texture
            if (textureId != TextureHandle.NONE.textureIndex)
                program.setUniformSampler("tex", TextureHandle.create(0, textureId))

            // Set uniforms
            program.setUniform("position", x, config.height - y, z)
            program.setUniform("size", w, h)
            program.setUniform("origin", xOrigin, 1f - yOrigin)
            program.setUniform("angle", -angle)
            program.setUniform("color", floatToRawIntBits(rgba))
            program.setUniform("cornerRadiusPacked", cornerRadius0, cornerRadius1)
            program.setUniform("borderPacked", border0, border1)
            program.setUniform("uvMinMax", uMin, vMin, uMax, vMax)
            program.setUniform("sampleTexture", textureId != TextureHandle.NONE.textureIndex)
            program.setUniform("isDepthTexture", isDepth > 0)
            program.setUniform("isPremultipliedAlpha", alphaMode > 0)

            // Draw quad
            drawTriangleStripVertices(vao, 0, 4)
        }

        // Release VAO and reset count
        readCount = 0
    }

    override fun destroy(engine: PulseEngineInternal)
    {
        vbo.destroy()
        program.destroy()
        vao.destroy()
    }

    fun draw(texture: RenderTexture, x: Float, y: Float, w: Float, h: Float, angle: Float, xOrigin: Float, yOrigin: Float, cornerRadius: CornerRadius, uMin: Float, vMin: Float, uMax: Float, vMax: Float, border: Border)
    {
        if (writeCount >= capacity)
        {
            Logger.warn { "RenderTextureRenderer: Capacity of $capacity reached, dropping texture: ${texture.name}" }
            return
        }

        val base = writeOffset + writeCount * stride
        data[base +  0] = x
        data[base +  1] = y
        data[base +  2] = config.currentDepth
        data[base +  3] = w
        data[base +  4] = h
        data[base +  5] = angle
        data[base +  6] = xOrigin
        data[base +  7] = yOrigin
        data[base +  8] = cornerRadius.packedFloat0
        data[base +  9] = cornerRadius.packedFloat1
        data[base + 10] = border.packedFloat0
        data[base + 11] = border.packedFloat1
        data[base + 12] = uMin
        data[base + 13] = vMin
        data[base + 14] = uMax
        data[base + 15] = vMax
        data[base + 16] = config.currentDrawColor
        data[base + 17] = texture.handle.textureIndex.toFloat()
        data[base + 18] = if (texture.attachmentPoint == DEPTH_TEXTURE) 1f else 0f
        data[base + 19] = if (texture.alphaMode == PREMULTIPLIED) 1f else 0f
        writeCount++
        config.increaseDepth()
        increaseBatchSize()
    }
}
