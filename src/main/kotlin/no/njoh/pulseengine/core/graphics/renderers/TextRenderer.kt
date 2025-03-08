package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Font.*
import no.njoh.pulseengine.core.asset.types.Font.Companion.MAX_CHAR_COUNT
import no.njoh.pulseengine.core.graphics.surface.SurfaceConfigInternal
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.api.TextureBank
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.*
import no.njoh.pulseengine.core.shared.primitives.FlatObjectBuffer
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import org.joml.Math.PI
import org.joml.Math.cos
import org.lwjgl.opengl.ARBBaseInstance.glDrawArraysInstancedBaseInstance
import org.lwjgl.opengl.GL20.GL_FLOAT
import org.lwjgl.opengl.GL20.GL_TRIANGLE_STRIP
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
    private val glyphBuffer = GlyphBuffer()

    override fun init()
    {
        if (!this::program.isInitialized)
        {
            instanceBuffer = DoubleBufferedFloatObject.createArrayBuffer()
            vertexBuffer = StaticBufferObject.createQuadVertexArrayBuffer()
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/renderers/glyph.vert",
                fragmentShaderFileName = "/pulseengine/shaders/renderers/glyph.frag"
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

    override fun onRenderBatch(engine: PulseEngineInternal, surface: Surface, startIndex: Int, drawCount: Int)
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
        program.setUniformSamplerArrays(textureBank.getTextureArrays())
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
        var xAdvance = -getLeftSideBearing(text[0], font, fontSize)
        val glyphs = glyphBuffer.clear()
        var charIndex = 0

        while (charIndex < text.length)
        {
            val cp = CodePoint.of(text, charIndex)
            val charCode = cp.code - 32
            if (charCode >= 0 && charCode < MAX_CHAR_COUNT)
            {
                val quad = font.getQuad(charCode)
                glyphs.x    = quad.x * scale + xAdvance
                glyphs.y    = quad.y * scale
                glyphs.w    = quad.w * scale
                glyphs.h    = quad.h * scale
                glyphs.uMin = quad.u0
                glyphs.vMin = quad.v0
                glyphs.uMax = quad.u1
                glyphs.vMax = quad.v1
                glyphs.next()
                xAdvance += quad.advance * scale
            }
            charIndex += cp.advanceCount
        }

        glyphs.flip()

        // Find distance between start of first character and end of last
        val xFirst = glyphs.first().x
        val xLast = glyphs.last().x
        val wLast = glyphs.last().w
        val textWidth = xLast + wLast - xFirst

        // Find max height over baseline
        var textHeight = Float.MIN_VALUE
        glyphs.forEach { glyph -> textHeight = max(textHeight, -glyph.y) }

        // Calculate position offsets
        val xOffset = textWidth * xOrigin
        val yOffset = textHeight * (1f - yOrigin)

        val texHandle = font.charTexture.handle.toFloat()
        when (angle)
        {
            0f -> drawAxisAlignedGlyphs(texHandle, x, y, xOffset, yOffset)
            else -> drawRotatedGlyphs(texHandle, x, y, xOffset, yOffset, angle)
        }
    }

    private fun drawAxisAlignedGlyphs(texHandle: Float, x: Float, y: Float, xOffset: Float, yOffset: Float)
    {
        val color = config.currentDrawColor
        val depth = config.currentDepth
        val xPos = x - xOffset
        val yPos = y + yOffset
        val count = glyphBuffer.size()

        instanceBuffer.fill(count * 12)
        {
            glyphBuffer.forEach()
            {
                put(it.x + xPos)
                put(it.y + yPos)
                put(depth)
                put(it.w, it.h)
                put(0f) // Rotation
                put(it.uMin, it.vMin)
                put(it.uMax, it.vMax)
                put(color)
                put(texHandle)
            }
        }
        increaseBatchSize(count)
        config.increaseDepth()
    }

    private fun drawRotatedGlyphs(texHandle: Float, x: Float, y: Float, xOffset: Float, yOffset: Float, angle: Float)
    {
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
        val count = glyphBuffer.size()

        instanceBuffer.fill(count * 12)
        {
            glyphBuffer.forEach()
            {
                val xGlyph = it.x
                val yGlyph = it.y
                put(xPos + (xGlyph * c0) + (yGlyph * c1))
                put(yPos + (xGlyph * s0) + (yGlyph * s1))
                put(depth)
                put(it.w, it.h)
                put(angle)
                put(it.uMin, it.vMin)
                put(it.uMax, it.vMax)
                put(color)
                put(texHandle)
            }
        }
        increaseBatchSize(count)
        config.increaseDepth()
    }

    private fun getLeftSideBearing(char: Char, font: Font, fontSize: Float): Float
    {
        val scale = fontSize / font.fontSize
        return font.getLeftSideBearing(char) * stbtt_ScaleForPixelHeight(font.info, fontSize) * scale
    }

    private class GlyphBuffer(capacity: Int = 1024) : FlatObjectBuffer<GlyphBuffer>(stride = 8)
    {
        @JvmField val data = FloatArray(capacity * stride)

        var x    by FloatRef(data, 0)
        var y    by FloatRef(data, 1)
        var w    by FloatRef(data, 2)
        var h    by FloatRef(data, 3)
        var uMin by FloatRef(data, 4)
        var vMin by FloatRef(data, 5)
        var uMax by FloatRef(data, 6)
        var vMax by FloatRef(data, 7)
    }
}