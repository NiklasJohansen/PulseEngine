package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.shared.annotations.ScnIcon
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.loadBytes
import org.lwjgl.BufferUtils
import org.lwjgl.openal.AL10
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ShortBuffer

@ScnIcon("MUSIC")
class Sound(fileName: String, override val name: String) : Asset(name, fileName)
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
            buffer = readVorbis(fileName, it)
            sampleRate = it.sample_rate()
        }
    }

    override fun delete()
    {
        buffer = null
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

    fun finalize(id: Int)
    {
        this.id = id
    }
}