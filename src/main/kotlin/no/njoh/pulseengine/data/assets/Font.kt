package no.njoh.pulseengine.data.assets

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTTPackContext
import org.lwjgl.stb.STBTTPackedchar
import org.lwjgl.stb.STBTruetype.*
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

class Font(
    fileName: String,
    override val name: String,
    val fontSizes: FloatArray
) : Asset(name, fileName) {

    lateinit var charTexture: Texture
    lateinit var charData: STBTTPackedchar.Buffer
    lateinit var info: STBTTFontinfo
    private lateinit var ttfBuffer: ByteBuffer

    private val advanceWidth = IntArray(1)
    private val leftSideBearing = IntArray(1)
    private var textWidthString: String = ""
    private var textWidthLength: Float = 0f
    private var textWidthFontSize: Float = 0f

    override fun load()
    {
        val fontData = Font::class.java.getResource(fileName).readBytes()

        STBTTPackContext.malloc().use { packContext ->
            val charData = STBTTPackedchar.malloc(fontSizes.size * TOTAL_CHAR_COUNT)
            val ttf = BufferUtils.createByteBuffer(fontData.size).put(fontData).flip() as ByteBuffer
            val bitmap = BufferUtils.createByteBuffer(BITMAP_W * BITMAP_H)
            val info = STBTTFontinfo.create()
            if (!stbtt_InitFont(info, ttf))
                throw IllegalStateException("Failed to initialize font information.");

            stbtt_PackBegin(packContext, bitmap, BITMAP_W, BITMAP_H, 0, 1, MemoryUtil.NULL)
            fontSizes.sort()
            for (i in fontSizes.indices)
            {
                val p = i * TOTAL_CHAR_COUNT + FIRST_CHAR
                charData.limit(p + CHAR_COUNT)
                charData.position(p)
                stbtt_PackSetOversampling(packContext, 2, 2)
                stbtt_PackFontRange(packContext, ttf, 0, fontSizes[i], FIRST_CHAR, charData)
            }

            charData.clear()
            stbtt_PackEnd(packContext)

            this.charTexture = Texture("char_tex", "")
            this.charTexture.load(bitmap, BITMAP_W, BITMAP_H, GL_ALPHA)
            this.charData = charData
            this.ttfBuffer = ttf
            this.info = info
        }
    }

    override fun delete()
    {
        glDeleteTextures(charTexture.id)
        charData.free()
    }

    fun getWidth(text: String, fontSize: Float = fontSizes[0]): Float
    {
        if (text == textWidthString && fontSize == textWidthFontSize)
            return textWidthLength

        textWidthLength = 0f
        for (character in text)
        {
            stbtt_GetCodepointHMetrics(info, character.toInt(), advanceWidth, leftSideBearing)
            textWidthLength += advanceWidth[0].toFloat()
        }

        textWidthLength *= stbtt_ScaleForPixelHeight(info, fontSize)
        textWidthFontSize = fontSize
        textWidthString = text

        return textWidthLength
    }

    fun getCharacterWidths(text: String, fontSize: Float = fontSizes[0]): FloatArray
    {
        val scale = stbtt_ScaleForPixelHeight(info, fontSize)
        val widths = FloatArray(text.length)
        for (i in text.indices)
        {
            stbtt_GetCodepointHMetrics(info, text[i].toInt(), advanceWidth, leftSideBearing)
            widths[i] = advanceWidth[0].toFloat() * scale
        }
        return widths
    }

    companion object
    {
        const val TOTAL_CHAR_COUNT = 128
        private const val BITMAP_W = 1024
        private const val BITMAP_H = 1024
        private const val FIRST_CHAR = 32
        private const val CHAR_COUNT = TOTAL_CHAR_COUNT - FIRST_CHAR - 1
        val DEFAULT: Font = Font(
            fileName = "/pulseengine/assets/FiraSans-Regular.ttf",
            name = "default_font",
            fontSizes = floatArrayOf(24f, 48f, 96f)
        )
    }
}