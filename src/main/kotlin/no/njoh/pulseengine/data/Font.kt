package no.njoh.pulseengine.data

import no.njoh.pulseengine.modules.Asset
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

    override fun load()
    {
        val fontData = Font::class.java.getResource(fileName).readBytes()

        STBTTPackContext.malloc().use { packContext ->
            val charData = STBTTPackedchar.malloc(fontSizes.size * 3 * TOTAL_CHAR_COUNT)
            val ttf = BufferUtils.createByteBuffer(fontData.size).put(fontData).flip() as ByteBuffer
            val bitmap = BufferUtils.createByteBuffer(BITMAP_W * BITMAP_H)
            val info = STBTTFontinfo.create()
            if (!stbtt_InitFont(info, ttf))
                throw IllegalStateException("Failed to initialize font information.");

            stbtt_PackBegin(packContext, bitmap, BITMAP_W, BITMAP_H, 0, 1, MemoryUtil.NULL)
            fontSizes.sort()
            for (i in fontSizes.indices)
            {
                var p = (i * 3 + 0) * TOTAL_CHAR_COUNT + FIRST_CHAR
                charData.limit(p + CHAR_COUNT)
                charData.position(p)
                stbtt_PackSetOversampling(packContext, 1, 1)
                stbtt_PackFontRange(packContext, ttf, 0, fontSizes[i], FIRST_CHAR, charData)

                p = (i * 3 + 1) * TOTAL_CHAR_COUNT + FIRST_CHAR
                charData.limit(p + CHAR_COUNT)
                charData.position(p)
                stbtt_PackSetOversampling(packContext, 2, 2)
                stbtt_PackFontRange(packContext, ttf, 0, fontSizes[i], FIRST_CHAR, charData)

                p = (i * 3 + 2) * TOTAL_CHAR_COUNT + FIRST_CHAR
                charData.limit(p + CHAR_COUNT)
                charData.position(p)
                stbtt_PackSetOversampling(packContext, 3, 1)
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

    companion object
    {
        const val TOTAL_CHAR_COUNT = 128
        private const val BITMAP_W = 512
        private const val BITMAP_H = 512
        private const val FIRST_CHAR = 32
        private const val CHAR_COUNT = TOTAL_CHAR_COUNT - FIRST_CHAR - 1
    }
}