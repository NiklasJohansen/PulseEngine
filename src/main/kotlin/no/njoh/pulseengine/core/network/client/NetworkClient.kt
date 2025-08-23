package no.njoh.pulseengine.core.network.client

import no.njoh.pulseengine.core.network.shared.NetworkChannel
import no.njoh.pulseengine.core.network.shared.NetworkChannel.*
import no.njoh.pulseengine.core.network.shared.NetworkCodec
import no.njoh.pulseengine.core.network.shared.NetworkStats
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import java.security.SecureRandom

abstract class NetworkClient
{
    /** The current client id - set when starting the client */
    abstract val id: Long

    /** The client name - set when starting the client */
    abstract val name: String

    /** Network statistics for the client */
    abstract val stats: NetworkStats

    /**
     * Starts the network client and connects to the specified server.
     */
    abstract fun start(
        clientId: Long = secureRandom.nextLong(),
        clientName: String = "unknown",
        serverIp: String = "127.0.0.1",
        serverPort: Int = 25565,
        serverPassword: String? = "_!default!_"
    )

    /**
     * Stops the network client and disconnects from the server.
     */
    abstract fun stop()

    /**
     * Sends a message to the connected server.
     */
    abstract fun sendMessageToServer(msg: Any, channel: NetworkChannel = RELIABLE)

    /**
     * Drains all incoming messages received from the server and appends them to the given destination.
     */
    abstract fun drainIncomingMessagesTo(destination: MutableList<Any>)

    /**
     * Returns true if the client is connected to a server.
     */
    abstract fun isConnected(): Boolean

    /**
     * Returns the ping time in milliseconds to the server or -1 if no ping time is available.
     * @param clientId The id of the client to get the ping time for. Default is the current client id.
     */
    abstract fun getPingTime(clientId: Long = id): Float

    /**
     * Sets a callback to be invoked when the client gets disconnected from the server.
     * Replaces any previously set callback.
     */
    abstract fun setOnDisconnected(callback: () -> Unit)

    /**
     * Iterates through all incoming messages received from the server and invokes the given action for each message.
     * The messages are drained to a temporary list and cleared afterward.
     */
    inline fun forEachIncomingMessage(action: (msg: Any) -> Unit)
    {
        val sink = sink.get()
        drainIncomingMessagesTo(sink)
        sink.forEachFast(action)
        sink.clear()
    }

    @PublishedApi
    internal val sink = ThreadLocal.withInitial { ArrayList<Any>() }
    private val secureRandom = SecureRandom()
}

abstract class NetworkClientInternal : NetworkClient()
{
    /**
     * Sets the codec used for serializing and deserializing network messages.
     */
    abstract fun setCodec(codec: NetworkCodec)
}