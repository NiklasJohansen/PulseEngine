package engine.modules

interface AudioInterface
{
    fun playSound(soundAssetName: String)
}

class Audio : AudioInterface
{
    override fun playSound(soundAssetName: String)
    {
        println("Playing sound: $soundAssetName")
    }

    fun cleanUp()
    {
        println("Cleaning up audio")
    }
}


