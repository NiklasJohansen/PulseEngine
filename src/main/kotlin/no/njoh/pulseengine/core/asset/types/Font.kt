package no.njoh.pulseengine.core.asset.types

import gnu.trove.map.hash.TFloatObjectHashMap
import gnu.trove.map.hash.THashMap
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.*
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.RGBA8
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping.CLAMP_TO_EDGE
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.utils.Extensions.loadBytesFromPath
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype.*
import java.nio.ByteBuffer

@Icon("FONT")
class Font(
    filePath: String,
    name: String,
    val fontSize: Float = 80f
) : Asset(filePath, name) {

    lateinit var charTexture: Texture
    lateinit var info: STBTTFontinfo
    private lateinit var ttfBuffer: ByteBuffer

    private val advanceWidth = IntArray(1)
    private val leftSideBearing = IntArray(1)
    private val leftSideBearingCache = IntArray(MAX_CHAR_COUNT) { -1 }
    private val textWidthCache = THashMap<CharSequence, TFloatObjectHashMap<FloatArray>>()
    private val advanceCache = FloatArray(MAX_CHAR_COUNT)
    private val quadCache = FloatArray(QUAD_STRIDE * MAX_CHAR_COUNT)
    private val quad = Quad(quadCache)

    override fun load()
    {
        val fontData: ByteArray = filePath.loadBytesFromPath() ?: run {
            Logger.error { "Failed to find and load Font asset: $filePath" }
            return
        }

        // Need to keep this buffer in memory!
        ttfBuffer = BufferUtils.createByteBuffer(fontData.size).put(fontData).flip() as ByteBuffer
        info = STBTTFontinfo.create()
        if (!stbtt_InitFont(info, ttfBuffer))
            throw IllegalStateException("Failed to initialize font information.")

        leftSideBearingCache.fill(-1)
        textWidthCache.clear()
        advanceCache.fill(0f)
        quadCache.fill(0f)

        val glyphs = createSdfGlyphs()
        try
        {
            val atlasSize = packGlyphs(glyphs)
            val rgbaBuffer = createAtlas(glyphs, atlasSize)
            glyphs.forEachIndexed { i, glyph ->
                advanceCache[i] = glyph.advance
                glyph.writeQuad(quadCache, i * QUAD_STRIDE, atlasSize)
            }
            charTexture = Texture(filePath = "", name = "char_tex_$name", filter = LINEAR, wrapping = CLAMP_TO_EDGE, format = RGBA8, maxMipLevels = 1)
            charTexture.loadFrom(rgbaBuffer, atlasSize, atlasSize, false)
        }
        finally
        {
            glyphs.forEach { glyph -> glyph.sdf?.let(::stbtt_FreeSDF) }
        }
    }

    private fun createSdfGlyphs(): Array<GeneratedGlyph>
    {
        val scale = stbtt_ScaleForPixelHeight(info, fontSize)
        return Array(MAX_CHAR_COUNT) { charIndex ->
            val codePoint = FIRST_CHAR_CODE + charIndex
            val width = IntArray(1)
            val height = IntArray(1)
            val xOffset = IntArray(1)
            val yOffset = IntArray(1)

            stbtt_GetCodepointHMetrics(info, codePoint, advanceWidth, leftSideBearing)
            val sdf = stbtt_GetCodepointSDF(
                info,
                scale,
                codePoint,
                SDF_PADDING,
                SDF_EDGE_VALUE.toByte(),
                SDF_EDGE_VALUE.toFloat() / SDF_PADDING,
                width,
                height,
                xOffset,
                yOffset
            )
            GeneratedGlyph(sdf, width[0], height[0], xOffset[0], yOffset[0], advanceWidth[0] * scale)
        }
    }

    private fun packGlyphs(glyphs: Array<GeneratedGlyph>): Int
    {
        var atlasSize = MIN_ATLAS_SIZE
        while (atlasSize <= MAX_ATLAS_SIZE)
        {
            var x = ATLAS_GUTTER
            var y = ATLAS_GUTTER
            var rowHeight = 0
            var fits = true

            glyphs.forEach { glyph ->
                if (!fits || glyph.sdf == null || glyph.width == 0 || glyph.height == 0)
                    return@forEach

                if (glyph.width + ATLAS_GUTTER * 2 > atlasSize || glyph.height + ATLAS_GUTTER * 2 > atlasSize)
                {
                    fits = false
                    return@forEach
                }

                if (x + glyph.width + ATLAS_GUTTER > atlasSize)
                {
                    x = ATLAS_GUTTER
                    y += rowHeight + ATLAS_GUTTER
                    rowHeight = 0
                }

                if (y + glyph.height + ATLAS_GUTTER > atlasSize)
                {
                    fits = false
                    return@forEach
                }

                glyph.atlasX = x
                glyph.atlasY = y
                x += glyph.width + ATLAS_GUTTER
                rowHeight = maxOf(rowHeight, glyph.height)
            }

            if (fits) return atlasSize
            atlasSize *= 2
        }

        throw IllegalStateException("Unable to pack SDF glyphs for font '$name' into a ${MAX_ATLAS_SIZE}x$MAX_ATLAS_SIZE atlas.")
    }

    private fun createAtlas(glyphs: Array<GeneratedGlyph>, atlasSize: Int): ByteBuffer
    {
        val atlas = BufferUtils.createByteBuffer(atlasSize * atlasSize * 4)
        while (atlas.hasRemaining()) atlas.put(0)
        atlas.clear()

        glyphs.forEach { glyph ->
            val sdf = glyph.sdf ?: return@forEach
            for (row in 0 until glyph.height)
            {
                for (column in 0 until glyph.width)
                {
                    val sourceIndex = row * glyph.width + column
                    val atlasIndex = ((glyph.atlasY + row) * atlasSize + glyph.atlasX + column) * 4
                    atlas.put(atlasIndex + 3, sdf.get(sourceIndex))
                }
            }
        }
        return atlas
    }

    override fun unload() { }

    fun getQuad(charCode: Int): Quad
    {
        require(charCode in 0 until MAX_CHAR_COUNT) {
            "Character index $charCode is outside the baked font range."
        }
        quad.i = charCode * QUAD_STRIDE
        return quad
    }

    fun getWidth(text: String, fontSize: Float = this.fontSize): Float =
        calculateCharacterAdvances(text, fontSize)

    fun getCharacterWidths(text: CharSequence, fontSize: Float = this.fontSize, useCache: Boolean = false): FloatArray
    {
        if (useCache) textWidthCache[text]?.get(fontSize)?.let { return it }

        val widths = FloatArray(text.length)
        calculateCharacterAdvances(text, fontSize, widths)

        if (useCache) textWidthCache.getOrPut(text) { TFloatObjectHashMap() }.putIfAbsent(fontSize, widths)

        return widths
    }

    /** 
     * Fills [widths], when supplied, and returns the total without allocating for width-only queries. 
     */
    private fun calculateCharacterAdvances(text: CharSequence, fontSize: Float, widths: FloatArray? = null): Float
    {
        val bakedScale = fontSize / this.fontSize
        var nativeScale = 0f
        var hasNativeScale = false
        var totalWidth = 0f
        var i = 0
        while (i < text.length)
        {
            val cp = CodePoint.of(text, i)
            val charIndex = cp.code - FIRST_CHAR_CODE
            val width = if (charIndex in 0 until MAX_CHAR_COUNT)
            {
                advanceCache[charIndex] * bakedScale
            }
            else
            {
                if (!hasNativeScale)
                {
                    nativeScale = stbtt_ScaleForPixelHeight(info, fontSize)
                    hasNativeScale = true
                }
                stbtt_GetCodepointHMetrics(info, cp.code, advanceWidth, leftSideBearing)
                advanceWidth[0] * nativeScale
            }
            widths?.set(i, width)
            totalWidth += width
            i += cp.advanceCount
        }
        return totalWidth
    }

    fun getLeftSideBearing(char: Char): Int
    {
        val charCode = char.code - 32
        if (charCode < 0 || charCode >= MAX_CHAR_COUNT)
            return 0

        val cachedValue = leftSideBearingCache[charCode]
        if (cachedValue != -1)
            return cachedValue

        stbtt_GetCodepointHMetrics(info, char.code, advanceWidth, leftSideBearing)
        val lsb = leftSideBearing[0]
        leftSideBearingCache[charCode] = lsb
        return lsb
    }

    object CodePoint
    {
        var code = 0;         private set
        var advanceCount = 0; private set

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

    class Quad(private val data: FloatArray, var i: Int = 0)
    {
        val x;        get() = data[i + 0]
        val y;        get() = data[i + 1]
        val w;        get() = data[i + 2]
        val h;        get() = data[i + 3]
        val xContent; get() = data[i + 4]
        val yContent; get() = data[i + 5]
        val wContent; get() = data[i + 6]
        val hContent; get() = data[i + 7]
        val u0;       get() = data[i + 8]
        val v0;       get() = data[i + 9]
        val u1;       get() = data[i + 10]
        val v1;       get() = data[i + 11]
        val advance;  get() = data[i + 12]
    }

    private class GeneratedGlyph(
        val sdf: ByteBuffer?,
        val width: Int,
        val height: Int,
        val xOffset: Int,
        val yOffset: Int,
        val advance: Float,
        var atlasX: Int = 0,
        var atlasY: Int = 0
    ) {
        fun writeQuad(data: FloatArray, offset: Int, atlasSize: Int)
        {
            val atlasScale = 1f / atlasSize
            data[offset + 0] = xOffset.toFloat()
            data[offset + 1] = yOffset.toFloat()
            data[offset + 2] = width.toFloat()
            data[offset + 3] = height.toFloat()
            data[offset + 4] = if (sdf == null) 0f else (xOffset + SDF_PADDING).toFloat()
            data[offset + 5] = if (sdf == null) 0f else (yOffset + SDF_PADDING).toFloat()
            data[offset + 6] = if (sdf == null) 0f else (width - SDF_PADDING * 2).coerceAtLeast(0).toFloat()
            data[offset + 7] = if (sdf == null) 0f else (height - SDF_PADDING * 2).coerceAtLeast(0).toFloat()
            data[offset + 8] = atlasX * atlasScale
            data[offset + 9] = atlasY * atlasScale
            data[offset + 10] = (atlasX + width) * atlasScale
            data[offset + 11] = (atlasY + height) * atlasScale
            data[offset + 12] = advance
        }
    }

    companion object
    {
        private const val FIRST_CHAR_CODE = 32
        private const val SDF_PADDING = 8
        private const val SDF_EDGE_VALUE = 128
        private const val ATLAS_GUTTER = 2
        private const val MIN_ATLAS_SIZE = 1024
        private const val MAX_ATLAS_SIZE = 8192
        private const val QUAD_STRIDE = 13
        const val MAX_CHAR_COUNT = 256
        val DEFAULT: Font = Font(
            filePath = "/pulseengine/assets/FiraSans-Regular.ttf",
            name = "default_font",
            fontSize = 80f
        )
    }
}