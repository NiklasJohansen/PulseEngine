package no.njoh.pulseengine.core.audio

import no.njoh.pulseengine.core.asset.types.Sound

interface Audio
{
    // Play sound
    fun playSound(sound: Sound, volume: Float = 1f, pitch: Float = 1f, looping: Boolean = false)
    fun playSound(soundAssetName: String, volume: Float = 1f, pitch: Float = 1f, looping: Boolean = false)

    // Create, get and check status of sources
    fun createSource(sound: Sound, volume: Float = 1f, pitch: Float = 1f, looping: Boolean = false): Int
    fun getSources(): List<Int>
    fun isSourcePlaying(sourceId: Int): Boolean
    fun isSourcePaused(sourceId: Int): Boolean

    // Update sources
    fun playSource(sourceId: Int)
    fun pauseSource(sourceId: Int)
    fun stopSource(sourceId: Int)
    fun stopAllSources()
    fun setSourceVolume(sourceId: Int, volume: Float)
    fun setSourcePitch(sourceId: Int, pitch: Float)
    fun setSourceLooping(sourceId: Int, looping: Boolean)
    fun setSourcePosition(sourceId: Int, x: Float, y: Float, z: Float = -1.0f)

    // Listener properties
    fun setListenerPosition(x: Float, y: Float, z: Float = -1.0f)
    fun setListenerVelocity(x: Float, y: Float, z: Float = 0.0f)
    fun setListenerOrientation(x: Float, y: Float, z: Float)

    // Output devices
    fun getDefaultOutputDevice(): String
    fun getOutputDevices(): List<String>
    fun setOutputDevice(deviceName: String)
}

interface AudioInternal : Audio
{
    fun init()
    fun enableInCurrentThread()
    fun uploadSound(sound: Sound)
    fun deleteSound(sound: Sound)
    fun setOnOutputDeviceChanged(callback: () -> Unit)
    fun setSoundProvider(soundProvider: (String) -> Sound?)
    fun update()
    fun destroy()
}