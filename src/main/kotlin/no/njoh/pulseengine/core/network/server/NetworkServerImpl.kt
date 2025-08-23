package no.njoh.pulseengine.core.network.server

import no.njoh.pulseengine.core.network.shared.ClientConnect
import no.njoh.pulseengine.core.network.shared.ClientDisconnect
import no.njoh.pulseengine.core.network.shared.NetworkChannel
import no.njoh.pulseengine.core.network.shared.NetworkChannel.*
import no.njoh.pulseengine.core.network.shared.NetworkCodec
import no.njoh.pulseengine.core.network.shared.NetworkStats
import no.njoh.pulseengine.core.network.shared.NetworkUtils.isExpectedCloseError
import no.njoh.pulseengine.core.network.shared.Ping
import no.njoh.pulseengine.core.network.shared.ClientHandshake
import no.njoh.pulseengine.core.shared.utils.Logger
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.set
import kotlin.concurrent.thread

class NetworkServerImpl(
    @Volatile
    private var codec: NetworkCodec,
    private val maxTcpMessageSize: Int = 64 * 1024,
    private val maxMessageQueueSize: Int = 10_000
) : NetworkServerInternal() {

    override var port: Int = -1
    override val stats = NetworkStats()

    private var password = null as String?
    private var maxClients = 10
    private val connectionsByClientId = ConcurrentHashMap<Long, Connection>() // clientId -> Connection
    private val connectionsByIpAndPort = ConcurrentHashMap<IpPortKey, Connection>()
    private var onClientConnectedCallback: (clientId: Long, name: String) -> Unit = { _, _ -> }
    private var onClientDisconnectedCallback: (clientId: Long, name: String) -> Unit = { _, _ -> }
    private val secureRandom = SecureRandom()

    private lateinit var tcpServerSocket: ServerSocket
    private lateinit var udpServerSocket: DatagramSocket
    private lateinit var udpOutPacket: ThreadLocal<DatagramPacket>

    @Volatile private var isShuttingDown = false
    @Volatile private var isStarted = false

    @PublishedApi internal var incomingMessageQueue = ArrayBlockingQueue<Any>(1)

    override fun start(port: Int, password: String?, maxClients: Int)
    {
        try
        {
            if (isStarted) stop()

            this.port = port
            this.password = password
            this.maxClients = maxClients
            this.incomingMessageQueue = ArrayBlockingQueue<Any>(maxMessageQueueSize)
            this.tcpServerSocket = ServerSocket().apply { reuseAddress = true }
            this.tcpServerSocket.bind(InetSocketAddress(port), 128)
            this.udpServerSocket = DatagramSocket(port).apply { receiveBufferSize = 1 shl 20; sendBufferSize = 1 shl 20 }
            this.udpOutPacket = ThreadLocal.withInitial { DatagramPacket(ByteArray(0), 0, InetAddress.getLoopbackAddress(), port) }
            this.isShuttingDown = false
            this.isStarted = true

            Logger.info { "NetworkServer: Starting on port $port" }

            startTcpConnectionThread()
            startUdpReadThread()
            startDeadConnectionCleanUpThread()
        }
        catch (e: Exception)
        {
            Logger.error(e) { "NetworkServer: Failed to start on port $port" }
            stop()
        }
    }

    override fun stop()
    {
        if (!isStarted) return

        Logger.info { "NetworkServer: Stopping..." }
        isShuttingDown = true
        isStarted = false
        runCatching { udpServerSocket.close() }
        runCatching { tcpServerSocket.close() }
        connectionsByClientId.values.forEach { it.destroy() }
    }

    override fun sendMessageToClient(msg: Any, clientId: Long, channel: NetworkChannel)
    {
        if (!isStarted)
        {
            Logger.debug { "NetworkServer: Ignoring message to client - server is stopped" }
            return
        }
        val connection = connectionsByClientId[clientId] ?: return
        when (channel)
        {
            RELIABLE -> connection.stageTcpMessage(msg)
            UNRELIABLE -> connection.writeUdpMessage(msg)
        }
    }

    override fun sendMessageToAllClients(msg: Any, exceptClientId: Long, channel: NetworkChannel)
    {
        if (!isStarted)
        {
            Logger.debug { "NetworkServer: Ignoring message to all clients - server is stopped" }
            return
        }
        when (channel)
        {
            RELIABLE -> connectionsByClientId.forEach { (id, conn) -> if (id != exceptClientId) conn.stageTcpMessage(msg) }
            UNRELIABLE ->
            {
                val binaryData = codec.encode(msg)
                connectionsByClientId.forEach { (id, conn) -> if (id != exceptClientId) conn.writeUdpMessage(binaryData, msg) }
            }
        }
    }

    override fun getPingTime(clientId: Long) = connectionsByClientId[clientId]?.pingMs ?: -1f

    override fun drainIncomingMessagesTo(destination: MutableList<Any>) { incomingMessageQueue.drainTo(destination) }

    override fun setOnClientConnected(callback: (Long, String) -> Unit) { onClientConnectedCallback = callback }

    override fun setOnClientDisconnected(callback: (Long, String) -> Unit) { onClientDisconnectedCallback = callback }

    override fun setCodec(codec: NetworkCodec) { this.codec = codec }

    // Threads -----------------------------------------------------------------------------

    private fun startTcpConnectionThread() = startThread("tcp-server-connection")
    {
        while (!tcpServerSocket.isClosed)
        {
            try
            {
                val socket = tcpServerSocket.accept()
                if (connectionsByClientId.size >= maxClients)
                {
                    Logger.warn { "NetworkServer: Maximum number of clients ($maxClients) reached. Rejecting connection from ${socket.remoteSocketAddress}" }
                    socket.close()
                    continue
                }

                val input = DataInputStream(BufferedInputStream(socket.getInputStream(), maxTcpMessageSize))
                val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), maxTcpMessageSize))
                val connection = Connection(
                    clientId = -1L,
                    clientName = "",
                    tcpSocket = socket,
                    tcpInputStream = input,
                    tcpOutputStream = output,
                )
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.soTimeout = 5000 // Give client 5 seconds to send ClientConnect packet
                startTcpReadThread(connection)
                startTcpWriteThread(connection)
                Logger.debug { "NetworkServer: TCP connection accepted from ${socket.remoteSocketAddress}" }
            }
            catch (e: Exception) { logNetFailure(e, op = "Accepting TCP connection") }
        }
    }

    private fun startTcpReadThread(connection: Connection) = startThread("tcp-server-read-${connection.tcpSocket.remoteSocketAddress}")
    {
        try
        {
            val buffer = ByteArray(maxTcpMessageSize)
            while (!connection.tcpSocket.isClosed)
            {
                val msgLength = connection.tcpInputStream.readInt()
                require(msgLength in 1 .. maxTcpMessageSize) { "Bad TCP frame length: $msgLength from $connection" }

                connection.tcpInputStream.readFully(buffer, 0, msgLength)
                val msg = codec.decode(buffer, 0, msgLength)
                handleIncomingMessage(msg, connection)
                stats.inTcpByteCount.increase(msgLength)
                stats.inTcpPacketCount.increase()
            }
        }
        catch (e: Exception) { logNetFailure(e, "Reading TCP", connection) }
        finally { connection.destroy() }
    }

    private fun startTcpWriteThread(connection: Connection) = startThread("tcp-server-write-${connection.tcpSocket.remoteSocketAddress}")
    {
        try
        {
            while (!connection.tcpSocket.isClosed)
            {
                val msg = connection.tcpOutgoingMessageQueue.poll(500, MILLISECONDS) ?: continue // Waits 500ms for messages to be available
                connection.writeTcpMessage(msg)
                while (true)
                {
                    val msg = connection.tcpOutgoingMessageQueue.poll() ?: break // Drain quickly without blocking
                    connection.writeTcpMessage(msg)
                }
                connection.tcpOutputStream.flush()
            }
        }
        catch (e: Exception) { logNetFailure(e, "Writing TCP", connection) }
        finally { connection.destroy() }
    }

    private fun startUdpReadThread() = startThread("udp-server-read")
    {
        val buffer = ByteArray(1400)
        val inPacket = DatagramPacket(buffer, buffer.size)
        while (!udpServerSocket.isClosed)
        {
            try
            {
                udpServerSocket.receive(inPacket) // Blocks while waiting for next packet
                val msg = codec.decode(inPacket.data, inPacket.offset, inPacket.length)
                val connection = connectionsByIpAndPort[IpPortKey.getProbe(inPacket.address, inPacket.port)]

                if (connection != null)
                {
                    handleIncomingMessage(msg, connection)
                    stats.inUdpByteCount.increase(inPacket.length)
                    stats.inUdpPacketCount.increase()
                }
                else if (msg is ClientHandshake)
                {
                    val candidateConn = connectionsByClientId[msg.clientId]
                    if (candidateConn != null &&
                        msg.udpToken == candidateConn.udpToken && // Verify UDP token sent from server via TCP
                        inPacket.address == candidateConn.tcpSocket.inetAddress
                    ) {
                        connectionsByIpAndPort.remove(IpPortKey.getProbe(candidateConn.udpClientIp, candidateConn.udpClientPort))
                        connectionsByIpAndPort[IpPortKey(inPacket.address, inPacket.port)] = candidateConn
                        candidateConn.udpClientIp = inPacket.address
                        candidateConn.udpClientPort = inPacket.port
                        Logger.debug { "NetworkServer: Client handshake verified for ${candidateConn.clientId}" }
                        continue
                    }
                }
            }
            catch (e: Exception) { logNetFailure(e, "Reading UDP") }
        }
    }

    private fun startDeadConnectionCleanUpThread() = startThread("server-connection-cleanup")
    {
        while (!tcpServerSocket.isClosed)
        {
            Thread.sleep(5000) // Check every 5 seconds
            val currentTime = System.currentTimeMillis()
            for (connection in connectionsByClientId.values)
            {
                if (currentTime > connection.timeOfLastPing + 10_000) // If no ping received in 10 seconds
                {
                    Logger.info { "NetworkServer: No pings from connection $connection in 10 seconds - closing it" }
                    connection.destroy()
                }
            }
        }
    }

    // Message Handling ----------------------------------------------------------------------

    private fun handleIncomingMessage(msg: Any, connection: Connection)
    {
        when (msg)
        {
            is Ping ->
            {
                val binaryData = codec.encode(msg)
                connection.writeUdpMessage(binaryData, msg) // Respond to ping with UDP
                for ((clientId, conn) in connectionsByClientId)
                {
                    if (clientId == msg.clientId)
                    {
                        conn.pingMs = msg.lastPingMs
                        conn.timeOfLastPing = System.currentTimeMillis()
                    }
                    else conn.writeUdpMessage(binaryData, msg)
                }
            }
            is ClientConnect ->
            {
                if (connection.clientId == -1L && msg.serverPassword == password)
                {
                    connection.tcpSocket.soTimeout = 0 // Disable timeout after successful connection
                    connection.clientId = msg.clientId
                    connection.clientName = msg.clientName
                    connection.udpClientPort = msg.clientUdpPort
                    connection.udpToken = secureRandom.nextLong()
                    connection.stageTcpMessage(ClientHandshake(connection.clientId, connection.udpToken)) // Send handshake with UDP token over TCP
                    connectionsByClientId[connection.clientId] = connection
                    safeInvoke("onClientConnected") { onClientConnectedCallback(msg.clientId, msg.clientName) }
                    Logger.info { "NetworkServer: Client connected $connection" }
                }
                else
                {
                    val reason = when
                    {
                        connectionsByClientId[msg.clientId] != null -> "duplicate client id ${msg.clientId}"
                        msg.serverPassword != password -> "bad password"
                        else -> "invalid state"
                    }
                    Logger.warn { "NetworkServer: Rejecting connection $connection ($reason)" }
                    connection.destroy()
                }
            }
            is ClientDisconnect ->
            {
                connection.destroy()
            }
            else ->
            {
                // Process message only if clientId is set
                if (connection.clientId != -1L)
                {
                    val wasEnqueued = incomingMessageQueue.offer(msg)
                    if (!wasEnqueued)
                        Logger.warn { "NetworkServer: Incoming message queue is full: ${incomingMessageQueue.size}. Dropping ${msg::class.simpleName}" }
                }
            }
        }
    }

    // Connection ----------------------------------------------------------------------------------

    inner class Connection(
        var clientId: Long = -1L,
        var clientName: String = "",
        val tcpSocket: Socket,
        val tcpInputStream: DataInputStream,
        val tcpOutputStream: DataOutputStream,
        val tcpOutgoingMessageQueue: ArrayBlockingQueue<Any> = ArrayBlockingQueue(maxMessageQueueSize),
        var timeOfLastPing: Long = System.currentTimeMillis(),
        var pingMs: Float = 0f
    ){
        @Volatile var udpClientIp = tcpSocket.inetAddress
        @Volatile var udpClientPort = -1 // Will be set when the ClientConnect packet is received
        @Volatile var udpToken = 0L //

        fun stageTcpMessage(msg: Any)
        {
            val wasEnqueued = tcpOutgoingMessageQueue.offer(msg)
            if (!wasEnqueued)
                Logger.warn { "NetworkServer: Outgoing TCP message queue is full (${tcpOutgoingMessageQueue.size}). Dropping message: ${msg::class.simpleName}" }
        }

        fun writeTcpMessage(msg: Any)
        {
            try
            {
                val binaryData = codec.encode(msg)
                tcpOutputStream.writeInt(binaryData.length)
                tcpOutputStream.write(binaryData.buffer, 0, binaryData.length)
                stats.outTcpByteCount.increase(binaryData.length)
                stats.outTcpPacketCount.increase()
            }
            catch (e: Exception) { logNetFailure(e, "Sending TCP", this)  }
        }

        fun writeUdpMessage(msg: Any) = writeUdpMessage(codec.encode(msg), msg)

        fun writeUdpMessage(binaryData: NetworkCodec.BinaryData, msg: Any)
        {
            try
            {
                val packet = udpOutPacket.get()
                packet.setData(binaryData.buffer, 0, binaryData.length)
                packet.address = udpClientIp
                packet.port = udpClientPort
                if (packet.length > 1200)
                    Logger.warn { "NetworkServer: UDP packet with size ${packet.length}b for message: ${msg::class.simpleName} exceeds the safe 1200 byte fragmentation threshold" }
                udpServerSocket.send(packet)
                stats.outUdpByteCount.increase(packet.length)
                stats.outUdpPacketCount.increase()
            }
            catch (e: Exception) { logNetFailure(e, "Sending UDP", this) }
        }

        fun destroy()
        {
            if (connectionsByClientId.remove(clientId) != null)
            {
                connectionsByIpAndPort.remove(IpPortKey.getProbe(udpClientIp, udpClientPort))
                safeInvoke("onClientDisconnected") { onClientDisconnectedCallback(clientId, clientName) }
                Logger.info { "NetworkServer: Connection $this was closed" }
            }
            runCatching { tcpSocket.close() }
        }

        override fun toString(): String = "(id=$clientId, name='$clientName', address=${tcpSocket.remoteSocketAddress})"
    }

    // Look-up key ----------------------------------------------------------------------------------

    class IpPortKey(var ip: InetAddress, var port: Int, var hash: Int = createHash(ip, port))
    {
        override fun equals(other: Any?) = other is IpPortKey && ip == other.ip && port == other.port
        override fun hashCode() = hash
        companion object
        {
            private val PROBE = ThreadLocal.withInitial { IpPortKey(InetAddress.getLoopbackAddress(), 0) }
            fun createHash(ip: InetAddress, port: Int) = ip.hashCode() * 31 + port
            fun getProbe(ip: InetAddress, port: Int): IpPortKey = PROBE.get().also { it.port = port; it.ip = ip; it.hash = createHash(ip, port) }
        }
    }

    // Utils ---------------------------------------------------------

    private fun logNetFailure(e: Throwable, op: String, connection: Connection? = null)
    {
        val where = connection?.let { " for $it" } ?: ""
        if (isShuttingDown || e.isExpectedCloseError())
            Logger.debug { "NetworkServer: $op closed$where (${e::class.simpleName}: ${e.message})" }
        else
            Logger.error(e) { "NetworkServer: $op failed$where" }
    }

    private inline fun safeInvoke(op: String, crossinline block: () -> Unit) =
        try { block() } catch (e: Throwable) { logNetFailure(e, op) }

    private fun startThread(name: String, block: () -> Unit) =
        thread(name = name, isDaemon = true) { try { block() } catch (e: Throwable) { logNetFailure(e, "Starting thread $name") } }
}