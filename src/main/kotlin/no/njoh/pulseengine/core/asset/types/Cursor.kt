package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.shared.annotations.ScnIcon
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.loadStream
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.glfwDestroyCursor
import org.lwjgl.glfw.GLFWImage
import java.nio.ByteBuffer
import javax.imageio.ImageIO

@ScnIcon("CURSOR")
class Cursor(
    fileName: String,
    name: String,
    private val xHotspot: Int,
    private val yHotspot: Int
) : Asset(name, fileName) {

    var handle: Long = -1
        private set

    override fun load()
    {
        val stream = fileName.loadStream() ?: run {
            Logger.error("Failed to find and load Cursor asset: $fileName")
            return
        }
        val image = ImageIO.read(stream)
        val width = image.width
        val height = image.height

        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)

        val buffer = BufferUtils.createByteBuffer(width * height * 4) as ByteBuffer
        for (y in 0 until height)
        {
            for (x in 0 until width)
            {
                val pixel = pixels[y * width + x]
                buffer.put((pixel shr 16 and 0xFF).toByte()) // red
                buffer.put((pixel shr 8 and 0xFF).toByte())  // green
                buffer.put((pixel and 0xFF).toByte())        // blue
                buffer.put((pixel shr 24 and 0xFF).toByte()) // alpha
            }
        }
        buffer.flip()

        val cursorImg = GLFWImage.create()
        cursorImg.width(width)
        cursorImg.height(height)
        cursorImg.pixels(buffer)

        handle = GLFW.glfwCreateCursor(cursorImg, xHotspot, yHotspot)
    }

    override fun delete()
    {
        glfwDestroyCursor(handle)
    }

    companion object
    {
        fun createWithHandle(handle: Long): Cursor =
            Cursor("", "standard_cursor", 0, 0)
                .apply { this.handle = handle }
    }
}