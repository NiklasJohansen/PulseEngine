package no.njoh.pulseengine.core.audio

import no.njoh.pulseengine.core.asset.types.Sound

class NoOpAudio : AudioInternal
{
    override fun init() {}
    override fun update() {}
    override fun destroy() {}
    override fun uploadSound(sound: Sound) {}
    override fun deleteSound(sound: Sound) {}
    override fun createSource(sound: Sound, volume: Float, pitch: Float, looping: Boolean) = -1
    override fun getDefaultOutputDevice(): String = ""
    override fun getOutputDevices(): List<String> = emptyList()
    override fun getSources(): List<Int> = emptyList()
    override fun isSourcePaused(sourceId: Int) = false
    override fun isSourcePlaying(sourceId: Int) = false
    override fun pauseSource(sourceId: Int) {}
    override fun playSound(soundAssetName: String, volume: Float, pitch: Float, looping: Boolean) {}
    override fun playSound(sound: Sound, volume: Float, pitch: Float, looping: Boolean) {}
    override fun playSource(sourceId: Int) {}
    override fun setListenerOrientation(xAt: Float, yAt: Float, zAt: Float, xUp: Float, yUp: Float, zUp: Float) {}
    override fun enableInCurrentThread() {}
    override fun setListenerPosition(x: Float, y: Float, z: Float) {}
    override fun setListenerVelocity(x: Float, y: Float, z: Float) {}
    override fun setDistanceModel(model: DistanceModel) {}
    override fun setOnOutputDeviceChanged(callback: () -> Unit) {}
    override fun setOutputDevice(deviceName: String) {}
    override fun setSoundProvider(soundProvider: (String) -> Sound?) {}
    override fun setSourceLooping(sourceId: Int, looping: Boolean) {}
    override fun setSourcePitch(sourceId: Int, pitch: Float) {}
    override fun setSourcePosition(sourceId: Int, x: Float, y: Float, z: Float) {}
    override fun setSourceReferenceDistance(sourceId: Int, distance: Float) {}
    override fun setSourceMaxDistance(sourceId: Int, distance: Float) {}
    override fun setSourceRolloffFactor(sourceId: Int, factor: Float) {}
    override fun setSourceVolume(sourceId: Int, volume: Float) {}
    override fun stopAllSources() {}
    override fun stopSource(sourceId: Int) {}
}