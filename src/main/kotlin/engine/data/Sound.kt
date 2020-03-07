package engine.data

import engine.modules.Asset
import org.lwjgl.BufferUtils
import org.lwjgl.openal.AL10
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryUtil
import java.nio.ShortBuffer

class Sound(override val name: String) : Asset(name)
{
    private lateinit var buffer: ShortBuffer
    private var format: Int = AL10.AL_FORMAT_MONO16
    var pointer: Int = -1
        private set

    companion object
    {
        fun create(filename: String, assetName: String): Sound
             = Sound(assetName).also { sound ->
                STBVorbisInfo.malloc().use { info ->
                    sound.pointer = AL10.alGenBuffers()
                    sound.format = AL10.AL_FORMAT_MONO16
                    sound.buffer = readVorbis(filename, info)
                    AL10.alBufferData(sound.pointer, sound.format, sound.buffer, info.sample_rate())
                }
            }

        fun delete(sound: Sound)
            = AL10.alDeleteBuffers(sound.pointer)

        fun reloadBuffer(sound: Sound)
            = STBVorbisInfo.malloc().use { info ->
                sound.pointer = AL10.alGenBuffers()
                AL10.alBufferData(sound.pointer, sound.format, sound.buffer, info.sample_rate())
            }

        private fun readVorbis(resource: String?, info: STBVorbisInfo): ShortBuffer
        {
            val bytes = Font::class.java.getResource(resource).readBytes()
            val byteBuffer = BufferUtils.createByteBuffer(bytes.size).put(bytes).flip()

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
    }
}