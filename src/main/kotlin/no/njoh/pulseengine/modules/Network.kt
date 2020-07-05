package no.njoh.pulseengine.modules

// Exposed to game code
interface NetworkInterface
{
    fun startServer(address: String, port: String)
}

// Exposed to game engine
interface NetworkEngineInterface : NetworkInterface
{
    fun init()
    fun cleanUp()
}

class Network : NetworkEngineInterface
{
    override fun init()
    {
        println("Initializing network...")
    }

    override fun startServer(address: String, port: String)
    {
        println("Starting server on address: $address:$port")
    }

    override fun cleanUp()
    {
        println("Cleaning up network...")
    }
}

