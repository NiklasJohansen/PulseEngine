package engine.data

import de.matthiasmann.twl.utils.PNGDecoder
import engine.modules.Asset
import org.lwjgl.opengl.ARBFramebufferObject
import org.lwjgl.opengl.GL11
import java.nio.ByteBuffer

data class Image(override val name: String, val textureId: Int, val width: Int, val height: Int) : Asset(name)
{
    companion object
    {
        fun create(filename: String, assetName: String): Image
        {
            val decoder = PNGDecoder(Image::class.java.getResourceAsStream(filename))
            val buffer: ByteBuffer = ByteBuffer.allocateDirect(4 * decoder.width * decoder.height)
            decoder.decode(buffer, decoder.width * 4, PNGDecoder.Format.RGBA)
            buffer.flip()

            val id = GL11.glGenTextures()
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, decoder.width, decoder.height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer)
            ARBFramebufferObject.glGenerateMipmap(GL11.GL_TEXTURE_2D)

            return Image(assetName, id, decoder.width, decoder.height)
        }

        fun delete(image: Image) = GL11.glDeleteTextures(image.textureId)
    }
}