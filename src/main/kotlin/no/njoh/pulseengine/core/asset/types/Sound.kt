package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.utils.Extensions.loadBytesFromDisk
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ShortBuffer

@Icon("MUSIC")
class Sound(filePath: String, name: String) : Asset(filePath, name)
{
    var id: Int = -1
        private set

    var buffer: ShortBuffer? = null
        private set

    var sampleRate: Int = 0
        private set

    override fun load()
    {
        STBVorbisInfo.malloc().use()
        {
            buffer = readVorbis(filePath, it)
            sampleRate = it.sample_rate()
        }
    }

    override fun unload()
    {
        buffer = null
    }

    private fun readVorbis(filePath: String, info: STBVorbisInfo): ShortBuffer
    {
        val bytes = filePath.loadBytesFromDisk() ?: run {
            Logger.error { "Failed to find and load Sound asset: $filePath" }
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

    fun finalize(id: Int)
    {
        this.id = id
    }
}