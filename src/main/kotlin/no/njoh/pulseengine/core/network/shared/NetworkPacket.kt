package no.njoh.pulseengine.core.network.shared

sealed class NetworkPacket

data class Ping(
    val clientId: Long,
    val sentAtTimeNs: Long,
    val lastPingMs: Float
): NetworkPacket()

data class ClientHandshake(
    val clientId: Long,
    val udpToken: Long
): NetworkPacket()

data class ClientConnect(
    val clientId: Long,
    val clientName: String,
    val clientUdpPort: Int,
    val serverPassword: String?
): NetworkPacket()

data class ClientDisconnect(val clientId: Long): NetworkPacket()