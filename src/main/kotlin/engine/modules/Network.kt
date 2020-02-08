package engine.modules

interface NetworkInterface
{
    fun startServer(address: String, port: String)
}

class Network : NetworkInterface
{
    override fun startServer(address: String, port: String)
    {
        println("Starting server on address: $address:$port")
    }
}

