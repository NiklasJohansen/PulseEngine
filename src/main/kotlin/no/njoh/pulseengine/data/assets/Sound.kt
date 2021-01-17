package no.njoh.pulseengine.data.assets

import no.njoh.pulseengine.util.Logger
import no.njoh.pulseengine.util.loadBytes
import org.lwjgl.BufferUtils
import org.lwjgl.openal.AL10
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ShortBuffer

class Sound(fileName: String, override val name: String) : Asset(name, fileName)
{
    private lateinit var buffer: ShortBuffer
    private var format: Int = AL10.AL_FORMAT_MONO16
    var pointer: Int = -1
        private set

    override fun load()
    {
        STBVorbisInfo.malloc().use { info ->
            pointer = AL10.alGenBuffers()
            format = AL10.AL_FORMAT_MONO16
            buffer = readVorbis(fileName, info)
            AL10.alBufferData(pointer, format, buffer, info.sample_rate())
        }
    }

    override fun delete()
    {
        AL10.alDeleteBuffers(pointer)
    }

    private fun readVorbis(fileName: String, info: STBVorbisInfo): ShortBuffer
    {
        val bytes = fileName.loadBytes() ?: run {
            Logger.error("Failed to find and load Sound asset: ${this.fileName}")
            return ShortBuffer.allocate(0)
        }

        val byteBuffer = BufferUtils.createByteBuffer(bytes.size).put(bytes).flip() as ByteBuffer
        val error = IntArray(1)
        val decoder: Long = STBVorbis.stb_vorbis_open_memory(byteBuffer, error, null)

        if (decoder == MemoryUtil.NULL)
            throw RuntimeException("Failed to open Ogg Vorbis file. Error: " + error[0])

        STBVorbis.stb_vorbis_get_info(decoder, info)

        val channels = info.channels()
        val pcm = BufferUtils.createShortBuffer(STBVorbis.stb_vorbis_stream_length_in_samples(decoder) * channels)

        STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm)
        STBVorbis.stb_vorbis_close(decoder)

        return pcm
    }

    fun reloadBuffer()
    {
        STBVorbisInfo.malloc().use { info ->
            pointer = AL10.alGenBuffers()
            AL10.alBufferData(pointer, format, buffer, info.sample_rate())
        }
    }
}