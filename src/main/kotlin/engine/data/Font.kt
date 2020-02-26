package engine.data

import engine.modules.Asset
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTTPackContext
import org.lwjgl.stb.STBTTPackedchar
import org.lwjgl.stb.STBTruetype.*
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

data class Font(
    override val name: String,
    val characterImage: Image,
    val characterData: STBTTPackedchar.Buffer,
    val ttfBuffer: ByteBuffer,
    val info: STBTTFontinfo,
    val fontSizes: FloatArray
) : Asset(name) {
    companion object
    {
        const val TOTAL_CHAR_COUNT = 128
        private const val BITMAP_W = 512
        private const val BITMAP_H = 512
        private const val FIRST_CHAR = 32
        private const val CHAR_COUNT = TOTAL_CHAR_COUNT - FIRST_CHAR - 1

        fun create(fileName: String, assetName: String, fontSizes: FloatArray): Font
        {
            val fontData = Font::class.java.getResource(fileName).readBytes()

            STBTTPackContext.malloc().use { packContext ->
                val charData = STBTTPackedchar.malloc(fontSizes.size * 3 * TOTAL_CHAR_COUNT)
                val ttf = BufferUtils.createByteBuffer(fontData.size).put(fontData).flip()
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

                val fontTex = glGenTextures()
                glBindTexture(GL_TEXTURE_2D, fontTex)
                glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, BITMAP_W, BITMAP_H, 0, GL_ALPHA, GL_UNSIGNED_BYTE, bitmap)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)

                val characterImage = Image("char_tex", fontTex, BITMAP_W, BITMAP_H)
                return Font(assetName, characterImage, charData, ttf, info, fontSizes)
            }
        }
    }
}