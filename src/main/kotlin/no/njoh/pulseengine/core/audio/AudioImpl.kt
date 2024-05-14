package no.njoh.pulseengine.core.audio

import no.njoh.pulseengine.core.asset.types.Sound
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.removeWhen
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.BufferUtils
import org.lwjgl.openal.*
import org.lwjgl.openal.AL.createCapabilities
import org.lwjgl.openal.AL10.*
import org.lwjgl.openal.ALC.createCapabilities
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.IntBuffer
import org.lwjgl.openal.ALC11.*
import org.lwjgl.openal.ALUtil.*
import org.lwjgl.openal.EXTThreadLocalContext.alcSetThreadContext
import org.lwjgl.openal.SOFTHRTF.*

open class AudioImpl : AudioInternal
{
    private var device: Long = MemoryUtil.NULL
    private var context: Long = MemoryUtil.NULL
    private val sources = mutableListOf<Int>()
    private var outputChangedCallback: () -> Unit = { }
    private var soundProvider: (String) -> Sound? = { null }

    override fun init()
    {
        Logger.info("Initializing audio (${this::class.simpleName})")

        // Use default output device
        setupDevice(alcOpenDevice(null as ByteBuffer?))
    }

    override fun enableInCurrentThread()
    {
        alcSetThreadContext(context)
    }

    private fun setupDevice(device: Long)
    {
        check(device != MemoryUtil.NULL) { "Failed to open the default output device" }

        val deviceCaps = createCapabilities(device)
        check(deviceCaps.ALC_SOFT_HRTF) { "Error: ALC_SOFT_HRTF not supported" }

        context = alcCreateContext(device, null as IntBuffer?)
        check(context != MemoryUtil.NULL) { "Failed to create an OpenAL context" }

        enableInCurrentThread()
        createCapabilities(deviceCaps)

        val numHrtf = alcGetInteger(device, ALC_NUM_HRTF_SPECIFIERS_SOFT)
        check(numHrtf != 0) { "No HRTFs found" }

        val attributes = BufferUtils.createIntBuffer(10).put(ALC_HRTF_SOFT).put(ALC_TRUE)
        attributes.put(0)
        attributes.flip()

        if (!alcResetDeviceSOFT(device, attributes))
            Logger.error("Failed to reset device: ${ALC10.alcGetString(device, alcGetError(device))}")

        val hrtfState = alcGetInteger(device, ALC_HRTF_SOFT)
        if (hrtfState == 0)
            Logger.warn("HRTF not enabled")
        else
            Logger.debug("HRTF enabled, using ${ALC10.alcGetString(device, ALC_HRTF_SPECIFIER_SOFT)}")

        this.device = device
    }

    override fun playSound(sound: Sound, volume: Float, pitch: Float, looping: Boolean)
    {
        playSource(sourceId = createSource(sound, volume, pitch, looping))
    }

    override fun playSound(soundAssetName: String, volume: Float, pitch: Float, looping: Boolean)
    {
        val sound = soundProvider(soundAssetName)
        if (sound != null)
            playSound(sound, volume, pitch, looping)
        else
            Logger.error("Failed to play sound - no asset with name: $soundAssetName was found")
    }

    override fun createSource(sound: Sound, volume: Float, pitch: Float, looping: Boolean): Int
    {
        val sourceId = alGenSources()
        alSourcei(sourceId, AL_SOURCE_RELATIVE, AL_TRUE) // Research AL_SOURCE_ABSOLUTE
        alSourcei(sourceId, AL_BUFFER, sound.id)
        setSourceVolume(sourceId, volume)
        setSourcePitch(sourceId, pitch)
        setSourceLooping(sourceId, looping)
        sources.add(sourceId)
        return sourceId
    }

    override fun stopSource(sourceId: Int)
    {
        sources.remove(sourceId)
        alSourceStop(sourceId)
        alDeleteSources(sourceId)
    }

    override fun stopAllSources() = sources.toList().forEachFast { stopSource(it) }

    override fun playSource(sourceId: Int) = alSourcePlay(sourceId)

    override fun pauseSource(sourceId: Int) = alSourcePause(sourceId)

    override fun isSourcePlaying(sourceId: Int): Boolean = alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_PLAYING

    override fun isSourcePaused(sourceId: Int): Boolean = alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_PAUSED

    override fun setSourceVolume(sourceId: Int, volume: Float) = alSourcef(sourceId, AL_GAIN, java.lang.Float.max(0.0f, volume))

    override fun setSourcePitch(sourceId: Int, pitch: Float) = alSourcef(sourceId, AL_PITCH, pitch)

    override fun setSourceLooping(sourceId: Int, looping: Boolean) = alSourcei(sourceId, AL_LOOPING, if (looping) AL_TRUE else AL_FALSE)

    override fun setSourcePosition(sourceId: Int, x: Float, y: Float, z: Float) = alSource3f(sourceId, AL_POSITION, x, y, z)

    override fun setListenerPosition(x: Float, y: Float, z: Float) = alListener3f(AL_POSITION, x, y, z)

    override fun setListenerVelocity(x: Float, y: Float, z: Float) = alListener3f(AL_VELOCITY, x, y, z)

    override fun setListenerOrientation(x: Float, y: Float, z: Float) = alListener3f(AL_ORIENTATION, x, y, z)

    override fun getSources(): List<Int> = sources

    override fun getDefaultOutputDevice(): String = alcGetString(MemoryUtil.NULL, ALC_DEFAULT_DEVICE_SPECIFIER) ?: ""

    override fun getOutputDevices(): List<String> = getStringList(MemoryUtil.NULL, ALC_ALL_DEVICES_SPECIFIER) ?: emptyList()

    override fun setOutputDevice(deviceName: String)
    {
        Logger.info("Setting output device: $deviceName")
        val device = alcOpenDevice(deviceName)
        if (device == MemoryUtil.NULL)
            Logger.error("Failed to set output device: $deviceName")
        else
        {
            alcDestroyContext(context)
            alcCloseDevice(this.device)
            setupDevice(device)
            outputChangedCallback.invoke()
        }
    }

    override fun update()
    {
       sources.removeWhen()
       {
           val stopped = !isSourcePlaying(it) && !isSourcePaused(it)
           if (stopped)
               alDeleteSources(it)
           stopped
       }
    }

    override fun uploadSound(sound: Sound)
    {
        if (sound.buffer == null)
        {
            Logger.error("Failed to upload sound: ${sound.fileName} - buffer is null")
            return
        }

        val id = alGenBuffers()
        alBufferData(id, AL_FORMAT_MONO16, sound.buffer!!, sound.sampleRate)
        sound.finalize(id)
    }

    override fun deleteSound(sound: Sound)
    {
        alDeleteBuffers(sound.id)
    }

    override fun setOnOutputDeviceChanged(callback: () -> Unit)
    {
        this.outputChangedCallback = callback
    }

    override fun setSoundProvider(soundProvider: (String) -> Sound?)
    {
        this.soundProvider = soundProvider
    }

    override fun destroy()
    {
        Logger.info("Destroying audio (${this::class.simpleName})")
        sources.forEachFast { alDeleteSources(it) }
        alcSetThreadContext(MemoryUtil.NULL)
        alcDestroyContext(context)
        alcCloseDevice(device)
    }
}