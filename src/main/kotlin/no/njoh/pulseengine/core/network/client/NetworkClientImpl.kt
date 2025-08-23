package no.njoh.pulseengine.core.network.client

import gnu.trove.map.hash.TLongFloatHashMap
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
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class NetworkClientImpl(
    @Volatile
    private var codec: NetworkCodec,
    private val maxTcpMessageSize: Int = 64 * 1024,
    private val maxMessageQueueSize: Int = 10_000
) : NetworkClientInternal() {

    override var id: Long = -1
    override var name: String = ""
    override val stats = NetworkStats()

    private var serverIp: String = ""
    private var serverPort: Int = -1
    private val pingTimesMs = TLongFloatHashMap(20)
    private val ongoingPings = AtomicLong()
    private var onDisconnectedCallback: () -> Unit = { }
    private var tcpOutgoingMessageQueue = ArrayBlockingQueue<Any>(1)

    private lateinit var tcpClientSocket: Socket
    private lateinit var udpClientSocket: DatagramSocket
    private lateinit var udpOutPacket: ThreadLocal<DatagramPacket>

    @Volatile private var isShuttingDown = false
    @Volatile private var isStarted = false

    @PublishedApi internal var incomingMessageQueue = ArrayBlockingQueue<Any>(1)

    override fun start(clientId: Long, clientName: String, serverIp: String, serverPort: Int, serverPassword: String?)
    {
        try
        {
            if (isConnected()) stop() // Ensure we are not already connected

            this.id = clientId
            this.name = clientName
            this.serverIp = serverIp
            this.serverPort = serverPort
            this.incomingMessageQueue = ArrayBlockingQueue<Any>(maxMessageQueueSize)
            this.tcpOutgoingMessageQueue = ArrayBlockingQueue<Any>(maxMessageQueueSize)
            this.tcpClientSocket = openTcpSocket(serverIp, serverPort)
            this.udpClientSocket = openUdpSocket()
            this.udpOutPacket = ThreadLocal.withInitial { DatagramPacket(ByteArray(0), 0, InetAddress.getByName(serverIp), serverPort) }
            this.isShuttingDown = false
            this.isStarted = true

            Logger.info { "NetworkClient: Starting on TCP port ${tcpClientSocket.localPort} and UDP port ${udpClientSocket.localPort}" }

            startTcpReadThread()
            startTcpWriteThread()
            startUdpReadThread()
            startPingThread()

            sendMessageToServer(ClientConnect(clientId, clientName, udpClientSocket.localPort, serverPassword), RELIABLE)
        }
        catch (e: Exception)
        {
            Logger.error(e) { "NetworkClient: Failed to connect to server on $serverIp:$serverPort" }
            stop()
        }
    }

    override fun stop()
    {
        if (!isStarted) return

        Logger.info { "NetworkClient: Stopping..." }
        isShuttingDown = true
        isStarted = false
        sendMessageToServer(ClientDisconnect(id), UNRELIABLE)
        runCatching { tcpClientSocket.close() }
        runCatching { udpClientSocket.close() }
        runCatching { onDisconnectedCallback() }
    }

    override fun sendMessageToServer(msg: Any, channel: NetworkChannel)
    {
        if (!isStarted)
        {
            Logger.debug { "NetworkClient: Ignoring message: ${msg::class.simpleName} to server - client is stopped" }
            return
        }
        when (channel)
        {
            RELIABLE -> stageTcpMessage(msg)
            UNRELIABLE -> writeUdpMessage(msg)
        }
    }

    override fun getPingTime(clientId: Long) = if (pingTimesMs.containsKey(clientId)) pingTimesMs[clientId] else -1f

    override fun isConnected() = isStarted && tcpClientSocket.isConnected && !tcpClientSocket.isClosed && !udpClientSocket.isClosed

    override fun drainIncomingMessagesTo(destination: MutableList<Any>) { incomingMessageQueue.drainTo(destination) }

    override fun setOnDisconnected(callback: () -> Unit) { onDisconnectedCallback = callback }

    override fun setCodec(codec: NetworkCodec) { this.codec = codec }

    // Threads ---------------------------------------------------------

    private fun startTcpWriteThread() = startThread("tcp-client-write")
    {
        try
        {
            val outputStream = DataOutputStream(BufferedOutputStream(tcpClientSocket.getOutputStream(), maxTcpMessageSize))
            while (!tcpClientSocket.isClosed)
            {
                writeTcpMessage(tcpOutgoingMessageQueue.poll(500, MILLISECONDS) ?: continue, outputStream) // Waits 500ms for messages to be available
                while (true)
                {
                    // Drain quickly without blocking
                    writeTcpMessage(tcpOutgoingMessageQueue.poll() ?: break, outputStream)
                }
                outputStream.flush()
            }
        }
        catch (e: Exception) { logNetFailure(e, "Writing TCP") }
        finally { stop() }
    }

    private fun startTcpReadThread() = startThread("tcp-client-read")
    {
        try
        {
            val inputStream = DataInputStream(BufferedInputStream(tcpClientSocket.getInputStream(), maxTcpMessageSize))
            val buffer = ByteArray(maxTcpMessageSize)
            while (!tcpClientSocket.isClosed)
            {
                val msgLength = inputStream.readInt()
                require(msgLength in 1 .. maxTcpMessageSize) { "Bad TCP frame length: $msgLength from server" }

                inputStream.readFully(buffer, 0, msgLength)
                val msg = codec.decode(buffer, 0, msgLength)
                handleIncomingMessage(msg)
                stats.inTcpByteCount.increase(msgLength)
                stats.inTcpPacketCount.increase()
            }
        }
        catch (e: Exception) { logNetFailure(e, "Reading TCP") }
        finally { stop() }
    }

    private fun startUdpReadThread() = startThread("udp-client-read")
    {
        val buffer = ByteArray(1400)
        val inPacket = DatagramPacket(buffer, buffer.size)
        while (!udpClientSocket.isClosed)
        {
            try
            {
                // Blocks while waiting for the next packet
                udpClientSocket.receive(inPacket)

                // Only accepts packets from the server
                if (inPacket.address == tcpClientSocket.inetAddress)
                {
                    val msg = codec.decode(inPacket.data, inPacket.offset, inPacket.length)
                    handleIncomingMessage(msg)
                    stats.inUdpByteCount.increase(inPacket.length)
                    stats.inUdpPacketCount.increase()
                }
            }
            catch (e: Exception) { logNetFailure(e, "Reading UDP") }
        }
    }

    private fun startPingThread() = startThread("udp-client-ping")
    {
        while (!udpClientSocket.isClosed)
        {
            sendMessageToServer(Ping(id, System.nanoTime(), pingTimesMs[id]), UNRELIABLE)
            Thread.sleep(1000)

            if (ongoingPings.incrementAndGet() >= 5)
            {
                if (!isShuttingDown)
                    Logger.error { "NetworkClient: Lost connection to server on $serverIp:$serverPort after 5 UDP pings without response" }
                stop()
                break
            }
        }
    }

    // Message output ---------------------------------------------------------

    private fun stageTcpMessage(msg: Any)
    {
        val wasEnqueued = tcpOutgoingMessageQueue.offer(msg)
        if (!wasEnqueued)
            Logger.warn { "NetworkClient: Outgoing TCP message queue is full (${tcpOutgoingMessageQueue.size}). Dropping ${msg::class.simpleName}" }
    }

    private fun writeTcpMessage(msg: Any, outputStream: DataOutputStream)
    {
        try
        {
            val binaryData = codec.encode(msg)
            outputStream.writeInt(binaryData.length)
            outputStream.write(binaryData.buffer, 0, binaryData.length)
            stats.outTcpByteCount.increase(binaryData.length)
            stats.outTcpPacketCount.increase()
        }
        catch (e: Exception) { logNetFailure(e, "Sending TCP message: ${msg::class.simpleName}") }
    }

    private fun writeUdpMessage(msg: Any)
    {
        try
        {
            val binaryData = codec.encode(msg)
            val packet = udpOutPacket.get()
            packet.setData(binaryData.buffer, 0, binaryData.length)
            if (packet.length > 1200)
                Logger.warn { "NetworkClient: UDP packet with size ${packet.length}b for message: ${msg::class.simpleName} exceeds the safe 1200 byte fragmentation threshold" }
            udpClientSocket.send(packet)
            stats.outUdpByteCount.increase(packet.length)
            stats.outUdpPacketCount.increase()
        }
        catch (e: Exception) { logNetFailure(e, "Sending UDP message: ${msg::class.simpleName}") }
    }

    // Handle incoming message ---------------------------------------------------------

    private fun handleIncomingMessage(msg: Any)
    {
        when (msg)
        {
            is ClientHandshake ->
            {
                if (msg.clientId == id) writeUdpMessage(msg) // Send the handshake received over tcp back over udp
            }
            is Ping ->
            {
                if (msg.clientId == id)
                {
                    pingTimesMs.put(msg.clientId, ((System.nanoTime() - msg.sentAtTimeNs) / 1_000_000.0).toFloat())
                    ongoingPings.set(0)
                }
                else pingTimesMs.put(msg.clientId, msg.lastPingMs)
            }
            else ->
            {
                val wasEnqueued = incomingMessageQueue.offer(msg)
                if (!wasEnqueued)
                    Logger.warn { "NetworkClient: Incoming message queue is full (${incomingMessageQueue.size}). Dropping ${msg::class.simpleName}" }
            }
        }
    }

    // Utils ---------------------------------------------------------

    private fun logNetFailure(e: Throwable, op: String)
    {
        if (isShuttingDown || e.isExpectedCloseError())
            Logger.info { "NetworkClient: $op ended (${e::class.simpleName}: ${e.message})" }
        else
            Logger.error(e) { "NetworkClient: $op failed (${e::class.simpleName}: ${e.message})" }
    }

    private fun startThread(name: String, block: () -> Unit) =
        thread(name = name, isDaemon = true) { try { block() } catch (e: Throwable) { logNetFailure(e, "Starting thread $name") } }

    private fun openUdpSocket(): DatagramSocket = DatagramSocket(0) // 0 = let OS pick an available port
        .apply { receiveBufferSize = 1 shl 20; sendBufferSize = 1 shl 20 }

    private fun openTcpSocket(serverIp: String, serverPort: Int): Socket
    {
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.connect(InetSocketAddress(serverIp, serverPort), 5_000)
        return socket
    }
}