package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.graphics.api.TextureFormat
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.utils.Extensions.loadBytes
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.BufferUtils
import org.lwjgl.stb.*
import org.lwjgl.stb.STBTruetype.*
import java.nio.ByteBuffer

@Icon("FONT")
class Font(
    fileName: String,
    override val name: String,
    val fontSize: Float
) : Asset(name, fileName) {

    lateinit var charTexture: Texture
    lateinit var charData: STBTTBakedChar.Buffer
    lateinit var info: STBTTFontinfo
    private lateinit var ttfBuffer: ByteBuffer

    private val advanceWidth = IntArray(1)
    private val leftSideBearing = IntArray(1)
    private val textWidthCache = mutableMapOf<String, MutableMap<Float, FloatArray>>()

    override fun load()
    {
        val fontData: ByteArray = fileName.loadBytes() ?: run {
            Logger.error("Failed to find and load Font asset: $fileName")
            return
        }

        // Need to keep this buffer in memory!
        ttfBuffer = BufferUtils.createByteBuffer(fontData.size).put(fontData).flip() as ByteBuffer
        info = STBTTFontinfo.create()
        if (!stbtt_InitFont(info, ttfBuffer))
            throw IllegalStateException("Failed to initialize font information.")

        charData = STBTTBakedChar.malloc(MAX_CHAR_COUNT)
        val alphaMask = BufferUtils.createByteBuffer(BITMAP_W * BITMAP_H)
        stbtt_BakeFontBitmap(ttfBuffer, fontSize, alphaMask, BITMAP_W, BITMAP_H, FIRST_CHAR_CODE, charData)

        val rgbaBuffer = BufferUtils.createByteBuffer(BITMAP_W * BITMAP_H * 4)
        for (i in 0 until BITMAP_W * BITMAP_H)
        {
            rgbaBuffer.put(0)
            rgbaBuffer.put(0)
            rgbaBuffer.put(0)
            rgbaBuffer.put(alphaMask.get(i))
        }
        rgbaBuffer.flip()

        charTexture = Texture("char_tex", "")
        charTexture.stage(rgbaBuffer, BITMAP_W, BITMAP_H, TextureFormat.RGBA8)
    }

    override fun delete()
    {
        // TODO: Fix deleting/freeing of texture slots
        // glDeleteTextures(charTexture.id)
        charData.free()
    }

    fun getWidth(text: String, fontSize: Float = this.fontSize): Float
    {
        var textWidthLength = 0f
        var i = 0
        while (i < text.length)
        {
            val cp = CodePoint.of(text, i)
            stbtt_GetCodepointHMetrics(info, cp.code, advanceWidth, leftSideBearing)
            textWidthLength += advanceWidth[0].toFloat()
            i += cp.advanceCount
        }
        return textWidthLength * stbtt_ScaleForPixelHeight(info, fontSize)
    }

    fun getCharacterWidths(text: String, fontSize: Float = this.fontSize, useCache: Boolean = false): FloatArray
    {
        if (useCache) textWidthCache[text]?.get(fontSize)?.let { return it }

        val scale = stbtt_ScaleForPixelHeight(info, fontSize)
        val widths = FloatArray(text.length)
        var i = 0
        while (i < text.length)
        {
            val cp = CodePoint.of(text, i)
            stbtt_GetCodepointHMetrics(info, cp.code, advanceWidth, leftSideBearing)
            widths[i] = advanceWidth[0].toFloat() * scale
            i += cp.advanceCount
        }

        if (useCache) textWidthCache.getOrPut(text) { mutableMapOf() }.putIfAbsent(fontSize, widths)

        return widths
    }

    object CodePoint {
        var code = 0
            private set
        var advanceCount = 0
            private set

        internal fun of(text: CharSequence, i: Int): CodePoint
        {
            val c0 = text[i]
            if (Character.isHighSurrogate(c0) && i + 1 < text.length)
            {
                val c1 = text[i + 1]
                if (Character.isLowSurrogate(c1))
                {
                    code = Character.toCodePoint(c0, c1)
                    advanceCount = 2
                    return this
                }
            }
            code = c0.code
            advanceCount = 1
            return this
        }
    }

    companion object
    {
        private const val BITMAP_W = 1024
        private const val BITMAP_H = 1024
        private const val FIRST_CHAR_CODE = 32
        const val MAX_CHAR_COUNT = 256
        val DEFAULT: Font = Font(
            fileName = "/pulseengine/assets/FiraSans-Regular.ttf",
            name = "default_font",
            fontSize = 80f
        )
    }
}