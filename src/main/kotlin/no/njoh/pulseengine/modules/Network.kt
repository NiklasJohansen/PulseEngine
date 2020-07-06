package no.njoh.pulseengine.modules

import no.njoh.pulseengine.util.Logger

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
        Logger.info("Initializing network...")
    }

    override fun startServer(address: String, port: String)
    {
        Logger.info("Starting server on address: $address:$port")
    }

    override fun cleanUp()
    {
        Logger.info("Cleaning up network...")
    }
}

