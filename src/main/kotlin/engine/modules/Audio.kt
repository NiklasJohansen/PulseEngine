package engine.modules

// Exposed to game code
interface AudioInterface
{
    fun playSound(soundAssetName: String)
}

// Exposed to game engine
interface AudioEngineInterface : AudioInterface
{
    fun init()
    fun cleanUp()
}

class Audio : AudioEngineInterface
{
    override fun init()
    {
        println("Initializing audio...")
    }

    override fun playSound(soundAssetName: String)
    {
        println("Playing sound: $soundAssetName")
    }

    override fun cleanUp()
    {
        println("Cleaning up audio...")
    }
}


