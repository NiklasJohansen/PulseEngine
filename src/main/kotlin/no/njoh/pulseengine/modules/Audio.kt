package no.njoh.pulseengine.modules

import no.njoh.pulseengine.data.assets.Sound
import no.njoh.pulseengine.util.Logger
import org.lwjgl.BufferUtils
import org.lwjgl.openal.*
import org.lwjgl.openal.AL10.*
import org.lwjgl.openal.ALC10.alcGetError
import org.lwjgl.openal.ALC10.alcGetString
import org.lwjgl.openal.ALC11.*
import org.lwjgl.openal.EXTThreadLocalContext.alcSetThreadContext
import org.lwjgl.system.MemoryUtil.NULL
import java.lang.Float.max
import java.nio.ByteBuffer
import java.nio.IntBuffer

// Exposed to game code
interface AudioInterface
{
    fun createSource(sound: Sound, volume: Float = 1.0f, looping: Boolean = false): Int
    fun play(sourceId: Int)
    fun pause(sourceId: Int)
    fun stop(sourceId: Int)
    fun stopAll()
    fun isPlaying(sourceId: Int): Boolean
    fun isPaused(sourceId: Int): Boolean
    fun setVolume(sourceId: Int, volume: Float)
    fun setPitch(sourceId: Int, pitch: Float)
    fun setLooping(sourceId: Int, looping: Boolean)
    fun setPosition(sourceId: Int, x: Float, y: Float, z: Float = -1.0f)

    fun setListenerPosition(x: Float, y: Float, z: Float = -1.0f)
    fun setListenerVelocity(x: Float, y: Float, z: Float = 0.0f)
    fun setListenerOrientation(x: Float, y: Float, z: Float)

    fun getSources(): List<Int>
    fun getDefaultOutputDevice(): String
    fun getOutputDevices(): List<String>
    fun setOutputDevice(deviceName: String)
}

// Exposed to game engine
interface AudioEngineInterface : AudioInterface
{
    fun init()
    fun setOnOutputDeviceChanged(callback: () -> Unit)
    fun cleanSources()
    fun cleanUp()
}

class Audio : AudioEngineInterface
{
    private var device: Long = NULL
    private var context: Long = NULL
    private val sources = mutableListOf<Int>()
    private var outputChangedCallback: () -> Unit = { }

    override fun init()
    {
        Logger.info("Initializing audio...")

        // Use default output device
        setupDevice(alcOpenDevice(null as ByteBuffer?))
    }

    private fun setupDevice(device: Long)
    {
        check(device != NULL) { "Failed to open the default output device" }

        val deviceCaps = ALC.createCapabilities(device)
        check(deviceCaps.ALC_SOFT_HRTF) { "Error: ALC_SOFT_HRTF not supported" }

        context = alcCreateContext(device, null as IntBuffer?)
        check(context != NULL) { "Failed to create an OpenAL context" }

        alcSetThreadContext(context)
        AL.createCapabilities(deviceCaps)

        val numHrtf = ALC10.alcGetInteger(device, SOFTHRTF.ALC_NUM_HRTF_SPECIFIERS_SOFT)
        check(numHrtf != 0) { "No HRTFs found" }

        val attributes = BufferUtils.createIntBuffer(10).put(SOFTHRTF.ALC_HRTF_SOFT).put(ALC10.ALC_TRUE)
        attributes.put(0)
        attributes.flip()

        if (!SOFTHRTF.alcResetDeviceSOFT(device, attributes))
            Logger.error("Failed to reset device: ${ alcGetString(device, alcGetError(device)) }")

        val hrtfState = ALC10.alcGetInteger(device, SOFTHRTF.ALC_HRTF_SOFT)
        if (hrtfState == 0)
            Logger.warn("HRTF not enabled")
        else
            Logger.info("HRTF enabled, using ${ alcGetString(device, SOFTHRTF.ALC_HRTF_SPECIFIER_SOFT) }")

        this.device = device
    }

    override fun createSource(sound: Sound, volume: Float, looping: Boolean): Int
    {
        val sourceId = alGenSources()
        alSourcei(sourceId, AL_SOURCE_RELATIVE, AL_TRUE) // Research AL_SOURCE_ABSOLUTE
        alSourcei(sourceId, AL_BUFFER, sound.pointer)
        setVolume(sourceId, volume)
        setLooping(sourceId, looping)
        sources.add(sourceId)
        return sourceId
    }

    override fun stop(sourceId: Int)
    {
        sources.remove(sourceId)
        alSourceStop(sourceId)
        alDeleteSources(sourceId)
    }

    override fun stopAll() = sources.toList().forEach { stop(it) }

    override fun play(sourceId: Int) = alSourcePlay(sourceId)

    override fun pause(sourceId: Int) = alSourcePause(sourceId)

    override fun isPlaying(sourceId: Int): Boolean = alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_PLAYING

    override fun isPaused(sourceId: Int): Boolean = alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_PAUSED

    override fun setVolume(sourceId: Int, volume: Float) = alSourcef(sourceId, AL_GAIN, max(0.0f, volume))

    override fun setPitch(sourceId: Int, pitch: Float) = alSourcef(sourceId, AL_PITCH, pitch)

    override fun setLooping(sourceId: Int, looping: Boolean) = alSourcei(sourceId, AL_LOOPING, if (looping) AL_TRUE else AL_FALSE)

    override fun setPosition(sourceId: Int, x: Float, y: Float, z: Float) = alSource3f(sourceId, AL_POSITION, x, y, z)

    override fun setListenerPosition(x: Float, y: Float, z: Float) = alListener3f(AL_POSITION, x, y, z)

    override fun setListenerVelocity(x: Float, y: Float, z: Float) = alListener3f(AL_VELOCITY, x, y, z)

    override fun setListenerOrientation(x: Float, y: Float, z: Float) = alListener3f(AL_ORIENTATION, x, y, z)

    override fun getSources(): List<Int> = sources

    override fun getDefaultOutputDevice(): String = alcGetString(NULL, ALC_DEFAULT_DEVICE_SPECIFIER) ?: ""

    override fun getOutputDevices(): List<String> = ALUtil.getStringList(NULL, ALC_ALL_DEVICES_SPECIFIER) ?: emptyList()

    override fun setOutputDevice(deviceName: String)
    {
        Logger.info("Setting output device: $deviceName")
        val device = alcOpenDevice(deviceName)
        if (device == NULL)
            Logger.error("Failed to set output device: $deviceName")
        else
        {
            alcDestroyContext(context)
            alcCloseDevice(this.device)
            setupDevice(device)
            outputChangedCallback.invoke()
        }
    }

    override fun cleanSources()
    {
       sources.removeIf {
           val stopped = !isPlaying(it) && !isPaused(it)
           if (stopped)
               alDeleteSources(it)
           stopped
       }
    }

    override fun setOnOutputDeviceChanged(callback: () -> Unit)
    {
        this.outputChangedCallback = callback
    }

    override fun cleanUp()
    {
        sources.forEach { alDeleteSources(it) }
        alcSetThreadContext(NULL)
        alcDestroyContext(context)
        alcCloseDevice(device)
    }
}