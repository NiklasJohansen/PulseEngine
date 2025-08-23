package no.njoh.pulseengine.core.network.server

import no.njoh.pulseengine.core.network.shared.NetworkChannel
import no.njoh.pulseengine.core.network.shared.NetworkChannel.*
import no.njoh.pulseengine.core.network.shared.NetworkCodec
import no.njoh.pulseengine.core.network.shared.NetworkStats
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

abstract class NetworkServer
{
    /** The port the server is currently running on - set when starting the server */
    abstract val port: Int

    /** Network statistics for the server */
    abstract val stats: NetworkStats

    /**
     * Starts the server on the specified port with optional password protection and maximum number of clients.
     */
    abstract fun start(
        port: Int = 25565,
        password: String? = "_!default!_",
        maxClients: Int = 50
    )

    /**
     * Stops the server and disconnects all connected clients.
     */
    abstract fun stop()

    /**
     * Sends a message to a specific connected client over the specified channel.
     */
    abstract fun sendMessageToClient(msg: Any, clientId: Long, channel: NetworkChannel = RELIABLE)

    /**
     * Sends a message to all connected clients over the specified channel. Optionally exclude a specific client by ID.
     */
    abstract fun sendMessageToAllClients(msg: Any, exceptClientId: Long = -1L, channel: NetworkChannel = RELIABLE)

    /**
     * Drains all incoming messages received by the server and appends them to the given destination.
     */
    abstract fun drainIncomingMessagesTo(destination: MutableList<Any>)

    /**
     * Sets a callback function to be invoked when a client connects to the server.
     * Replaces any previously set callback.
     */
    abstract fun setOnClientConnected(callback: (clientId: Long, name: String) -> Unit)

    /**
     * Sets a callback function to be invoked when a client disconnects from the server.
     * Replaces any previously set callback.
     */
    abstract fun setOnClientDisconnected(callback: (clientId: Long, name: String) -> Unit)

    /**
     * Returns the current ping time in milliseconds for the specified client ID. Returns -1 if the client is not found.
     */
    abstract fun getPingTime(clientId: Long): Float

    /**
     * Iterates over each incoming message received by the server and invokes the provided action function for each message.
     * The messages are drained from the message queue and cleared after processing.
     */
    inline fun forEachIncomingMessage(action: (msg: Any) -> Unit)
    {
        val sink = sink.get()
        drainIncomingMessagesTo(sink)
        sink.forEachFast(action)
        sink.clear()
    }

    @PublishedApi
    internal val sink = ThreadLocal.withInitial { mutableListOf<Any>() }
}

abstract class NetworkServerInternal : NetworkServer()
{
    /**
     * Sets the codec used for serializing and deserializing network messages.
     */
    abstract fun setCodec(codec: NetworkCodec)
}