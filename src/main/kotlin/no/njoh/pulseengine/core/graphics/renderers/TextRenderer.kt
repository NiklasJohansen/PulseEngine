package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Font.*
import no.njoh.pulseengine.core.asset.types.Font.Companion.MAX_CHAR_COUNT
import no.njoh.pulseengine.core.graphics.surface.SurfaceConfigInternal
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.api.TextureBank
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.*
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import org.joml.Math.PI
import org.joml.Math.cos
import org.lwjgl.opengl.ARBBaseInstance.glDrawArraysInstancedBaseInstance
import org.lwjgl.opengl.GL20.GL_FLOAT
import org.lwjgl.opengl.GL20.GL_TRIANGLE_STRIP
import org.lwjgl.stb.STBTTAlignedQuad
import org.lwjgl.stb.STBTruetype.*
import kotlin.math.max
import kotlin.math.sin

class TextRenderer(
    private val config: SurfaceConfigInternal,
    private val textureBank: TextureBank
) : BatchRenderer() {

    private lateinit var vao: VertexArrayObject
    private lateinit var vertexBuffer: StaticBufferObject
    private lateinit var instanceBuffer: DoubleBufferedFloatObject
    private lateinit var program: ShaderProgram

    private val xb = FloatArray(1) { 0f }
    private val yb = FloatArray(1) { 0f }
    private val quad = STBTTAlignedQuad.malloc()
    private val glyphData = FloatArray(GLYPH_STRIDE * 1024)

    override fun init()
    {
        if (!this::program.isInitialized)
        {
            instanceBuffer = DoubleBufferedFloatObject.createArrayBuffer()
            vertexBuffer = StaticBufferObject.createQuadVertexArrayBuffer()
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/default/glyph.vert",
                fragmentShaderFileName = "/pulseengine/shaders/default/glyph.frag"
            )
        }

        val vertexLayout = VertexAttributeLayout()
            .withAttribute("vertexPos", 2, GL_FLOAT)

        val instanceLayout = VertexAttributeLayout()
            .withAttribute("worldPos", 3, GL_FLOAT, 1)
            .withAttribute("size", 2, GL_FLOAT, 1)
            .withAttribute("rotation", 1, GL_FLOAT, 1)
            .withAttribute("uvMin", 2, GL_FLOAT, 1)
            .withAttribute("uvMax", 2, GL_FLOAT, 1)
            .withAttribute("color", 1, GL_FLOAT, 1)
            .withAttribute("textureHandle", 1, GL_FLOAT, 1)

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

    override fun onRenderBatch(surface: Surface, startIndex: Int, drawCount: Int)
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
        textureBank.bindAllTexturesTo(program)
        glDrawArraysInstancedBaseInstance(GL_TRIANGLE_STRIP, 0, 4, drawCount, startIndex)
        vao.release()
    }

    override fun destroy()
    {
        vertexBuffer.delete()
        instanceBuffer.delete()
        program.delete()
        vao.delete()
    }

    fun draw(text: CharSequence, x: Float, y: Float, font: Font, fontSize: Float, angle: Float, xOrigin: Float, yOrigin: Float)
    {
        if (text.isEmpty()) return

        val scale = fontSize / font.fontSize
        val width = font.charTexture.width
        val height = font.charTexture.height
        val uMax = font.charTexture.uMax
        val vMax = font.charTexture.vMax
        val texHandle = font.charTexture.handle.toFloat()
        val charData = font.charData
        xb[0] = -getLeftSideBearing(text, font, fontSize) // Compensate for space before first character
        yb[0] = 0f

        var i = 0
        var charIndex = 0
        while (charIndex < text.length)
        {
            val cp = CodePoint.of(text, charIndex)
            val charCode = cp.code - 32
            if (charCode >= 0 && charCode < MAX_CHAR_COUNT)
            {
                stbtt_GetBakedQuad(charData, width, height, charCode, xb, yb, quad, false)
                val x0 = quad.x0() * scale
                val y0 = quad.y0() * scale
                val x1 = quad.x1() * scale
                val y1 = quad.y1() * scale
                glyphData[X(i)] = x0
                glyphData[Y(i)] = y0
                glyphData[W(i)] = x1 - x0
                glyphData[H(i)] = y1 - y0
                glyphData[U_MIN(i)] = quad.s0() * uMax
                glyphData[V_MIN(i)] = quad.t0() * vMax
                glyphData[U_MAX(i)] = quad.s1() * uMax
                glyphData[V_MAX(i)] = quad.t1() * vMax
                i += GLYPH_STRIDE
            }
            charIndex += cp.advanceCount
        }

        // Find distance between start of first character and end of last
        val xFirst = glyphData[X(0)]
        val xLast = glyphData[X(text.lastIndex * GLYPH_STRIDE)]
        val wLast = glyphData[W(text.lastIndex * GLYPH_STRIDE)]
        val textWidth = xLast + wLast - xFirst

        // Find max height over baseline
        var textHeight = -glyphData[Y(0)]
        for (j in 1 until text.length)
            textHeight = max(textHeight, -glyphData[Y(j * GLYPH_STRIDE)])

        // Calculate position offsets
        val xOffset = textWidth * xOrigin
        val yOffset = textHeight * (1f - yOrigin)

        if (angle == 0f)
            drawAxisAlignedGlyphs(text.length, texHandle, x, y, xOffset, yOffset)
        else
            drawRotatedGlyphs(text.length, texHandle, x, y, xOffset, yOffset, angle)
    }

    private fun drawAxisAlignedGlyphs(spriteCount: Int, texHandle: Float, x: Float, y: Float, xOffset: Float, yOffset: Float)
    {
        val data = glyphData
        val color = config.currentDrawColor
        val depth = config.currentDepth
        val xPos = x - xOffset
        val yPos = y + yOffset
        val end = spriteCount * GLYPH_STRIDE
        var i = 0

        instanceBuffer.fill(spriteCount * 12)
        {
            while (i < end)
            {
                put(data[X(i)] + xPos)
                put(data[Y(i)] + yPos)
                put(depth)
                put(data[W(i)])
                put(data[H(i)])
                put(0f) // Rotation
                put(data[U_MIN(i)])
                put(data[V_MIN(i)])
                put(data[U_MAX(i)])
                put(data[V_MAX(i)])
                put(color)
                put(texHandle)
                i += GLYPH_STRIDE
            }
        }
        increaseBatchSize(spriteCount)
        config.increaseDepth()
    }

    private fun drawRotatedGlyphs(spriteCount: Int, texHandle: Float, x: Float, y: Float, xOffset: Float, yOffset: Float, angle: Float)
    {
        val data = glyphData
        val color = config.currentDrawColor
        val depth = config.currentDepth
        val angleRad = -angle.toRadians()
        val normalRad = angleRad + 0.5f * PI.toFloat()
        val c0 = cos(angleRad)
        val s0 = sin(angleRad)
        val c1 = cos(normalRad)
        val s1 = sin(normalRad)
        val xPos = x - (xOffset * c0 - yOffset * c1)
        val yPos = y - (xOffset * s0 - yOffset * s1)
        val end = spriteCount * GLYPH_STRIDE
        var i = 0

        instanceBuffer.fill(spriteCount * 12)
        {
            while (i < end)
            {
                val xGlyph = data[X(i)]
                val yGlyph = data[Y(i)]
                put(xPos + (xGlyph * c0) + (yGlyph * c1))
                put(yPos + (xGlyph * s0) + (yGlyph * s1))
                put(depth)
                put(data[W(i)])
                put(data[H(i)])
                put(angle)
                put(data[U_MIN(i)])
                put(data[V_MIN(i)])
                put(data[U_MAX(i)])
                put(data[V_MAX(i)])
                put(color)
                put(texHandle)
                i += GLYPH_STRIDE
            }
        }
        increaseBatchSize(spriteCount)
        config.increaseDepth()
    }

    private val advanceWidth = IntArray(1)
    private val leftSideBearing = IntArray(1)
    private fun getLeftSideBearing(text: CharSequence, font: Font, fontSize: Float): Float
    {
        stbtt_GetCodepointHMetrics(font.info, text[0].code, advanceWidth, leftSideBearing)
        return leftSideBearing[0] * stbtt_ScaleForPixelHeight(font.info, fontSize)
    }

    @Suppress("NOTHING_TO_INLINE", "FunctionName")
    companion object
    {
        private const val GLYPH_STRIDE = 8

        inline fun X(i: Int) = i + 0
        inline fun Y(i: Int) = i + 1
        inline fun W(i: Int) = i + 2
        inline fun H(i: Int) = i + 3
        inline fun U_MIN(i: Int) = i + 4
        inline fun V_MIN(i: Int) = i + 5
        inline fun U_MAX(i: Int) = i + 6
        inline fun V_MAX(i: Int) = i + 7
    }
}