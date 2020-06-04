package game.example

import engine.PulseEngine
import engine.modules.Game
import engine.data.Key
import engine.data.Mouse
import engine.data.Sound
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun main() = PulseEngine.run(AudioExample())

class AudioExample : Game()
{
    var angle = 0f
    var loopingSource = 0

    override fun init()
    {
        engine.window.title = "Audio example"
        engine.config.targetFps = 120

        // Get all available device names
        val devices = engine.audio.getOutputDevices()
        println("Devices:\n" + devices.joinToString("\n"))

        // Get name of default output device
        val defaultOutputDevice = engine.audio.getDefaultOutputDevice()
        println("Default output device: $defaultOutputDevice")

        // Set output device
        engine.audio.setOutputDevice(defaultOutputDevice)

        // Load sound files
        engine.asset.loadSound("/hollow.ogg", "hollow")
        val plane = engine.asset.loadSound("/plane.ogg", "plane")

        // Create new looping source
        loopingSource = engine.audio.createSource(plane, 1f, true)

        // Play looping source
        engine.audio.play(loopingSource)
    }

    override fun fixedUpdate()
    {
        angle += 0.5f * engine.data.fixedDeltaTime
        if(angle > PI * 2)
            angle = 0f
    }

    override fun update()
    {
        // Create new sound source
        if(engine.input.wasClicked(Mouse.LEFT))
        {
            val sound = engine.asset.get<Sound>("hollow")
            val sourceId = engine.audio.createSource(sound)
            engine.audio.setPitch(sourceId,1f)
            engine.audio.setVolume(sourceId, 0.8f)
            engine.audio.setLooping(sourceId, false)
            engine.audio.setPosition(sourceId, 0f, 0f)
            engine.audio.play(sourceId)
        }

        // Set output device to default device
        if(engine.input.wasClicked(Key.SPACE))
            engine.audio.setOutputDevice(engine.audio.getDefaultOutputDevice())

        // Pause looping sound
        if(engine.input.wasClicked(Key.P))
            engine.audio.pause(loopingSource)

        // Play looping sound
        if(engine.input.wasClicked(Key.S))
        {
            engine.audio.play(loopingSource)
            engine.audio.setLooping(loopingSource, true)
        }

        // Stop all audio sources
        if(engine.input.wasClicked(Key.BACKSPACE))
            engine.audio.stopAll()

        // Loop through sources and update position
        engine.audio.getSources().forEach { sourceId ->
            engine.audio.setPosition(sourceId, cos(angle) * 10,sin(angle) * 10)
        }
    }

    override fun render()
    {
        val xCenter = engine.window.width / 2f
        val yCenter = engine.window.height / 2f
        engine.gfx.mainSurface.setDrawColor(1f,1f, 1f)
        engine.gfx.mainSurface.drawQuad(xCenter + cos(angle) * xCenter, yCenter, 10f, 10f)
        engine.gfx.mainSurface.drawText("Sources:  ${engine.audio.getSources().size}", 20f, 30f)
    }

    override fun cleanup() { }
}