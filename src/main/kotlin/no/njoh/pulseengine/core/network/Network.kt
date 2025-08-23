package no.njoh.pulseengine.core.network

import no.njoh.pulseengine.core.network.client.NetworkClient
import no.njoh.pulseengine.core.network.server.NetworkServer
import no.njoh.pulseengine.core.network.shared.NetworkCodec
import kotlin.reflect.KClass

/**
 * The Network interface provides access to both server and client functionalities,
 * allowing for the setup and management of network communication in a game or application.
 * It supports custom codecs for message encoding/decoding.
 */
interface Network
{
    /** The server instance responsible for handling client connections and communication */
    val server: NetworkServer

    /** The client instance responsible for server connection and communication */
    val client: NetworkClient

    /**
     * Sets the codec used for encoding and decoding network messages.
     */
    fun setCodec(codec: NetworkCodec)

    /**
     * Registers message types with the codec for smaller messages payloads.
     * If a sealed class is provided, all its subclasses should be registered as well.
     * The same types must be registered on both the server side and the client side.
     */
    fun registerMessageTypes(vararg types: KClass<*>)
}

interface NetworkInternal : Network
{
    fun init()
    fun destroy()
}