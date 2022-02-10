package no.njoh.pulseengine.modules.audio

import no.njoh.pulseengine.data.assets.Sound

interface Audio
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

interface AudioInternal : Audio
{
    fun init()
    fun setOnOutputDeviceChanged(callback: () -> Unit)
    fun cleanSources()
    fun cleanUp()
}