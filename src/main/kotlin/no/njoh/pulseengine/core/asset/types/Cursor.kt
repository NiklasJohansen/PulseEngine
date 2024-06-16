package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.input.CursorType
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.utils.Extensions.loadBytes
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBImage.*
import java.nio.ByteBuffer

@Icon("CURSOR")
class Cursor(
    fileName: String,
    name: String,
    val type: CursorType,
    val xHotspot: Int,
    val yHotspot: Int
) : Asset(name, fileName) {

    var handle: Long = -1
        private set

    var width: Int = 0
        private set

    var height: Int = 0
        private set

    var pixelBuffer: ByteBuffer? = null
        private set

    override fun load()
    {
        try {
            val bytes = fileName.loadBytes() ?: throw RuntimeException("No such file")
            val buffer = BufferUtils.createByteBuffer(bytes.size).put(bytes).flip() as ByteBuffer
            val width = IntArray(1)
            val height = IntArray(1)
            val components = IntArray(1)

            stbi_info_from_memory(buffer, width, height, components)
            this.width = width[0]
            this.height = height[0]
            this.pixelBuffer = stbi_load_from_memory(buffer, width, height, components, STBI_rgb_alpha)
                ?: throw RuntimeException("Could not load image into memory: " + stbi_failure_reason())
        }
        catch (e: Exception)
        {
            Logger.error("Failed to load cursor $fileName: ${e.message}")
        }
    }

    override fun delete() { }

    fun finalize(handle: Long)
    {
        pixelBuffer?.let { stbi_image_free(it) }
        this.pixelBuffer = null
        this.handle = handle
    }
}