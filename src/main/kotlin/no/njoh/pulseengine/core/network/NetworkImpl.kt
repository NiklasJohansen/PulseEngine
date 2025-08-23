package no.njoh.pulseengine.core.network

import no.njoh.pulseengine.core.network.client.NetworkClientImpl
import no.njoh.pulseengine.core.network.client.NetworkClientInternal
import no.njoh.pulseengine.core.network.server.NetworkServerImpl
import no.njoh.pulseengine.core.network.server.NetworkServerInternal
import no.njoh.pulseengine.core.network.shared.KryoNetworkCodec
import no.njoh.pulseengine.core.network.shared.NetworkCodec
import no.njoh.pulseengine.core.network.shared.NetworkPacket
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import kotlin.reflect.KClass

class NetworkImpl(
    private var codec: NetworkCodec = KryoNetworkCodec(NetworkPacket::class),
    override var server: NetworkServerInternal = NetworkServerImpl(codec),
    override var client: NetworkClientInternal = NetworkClientImpl(codec)
): NetworkInternal {

    override fun init()
    {
        setCodec(codec)
    }

    override fun destroy()
    {
        server.stop()
        client.stop()
    }

    override fun setCodec(codec: NetworkCodec)
    {
        this.codec = codec
        this.server.setCodec(codec)
        this.client.setCodec(codec)
    }

    override fun registerMessageTypes(vararg types: KClass<*>)
    {
        types.forEachFast { codec.registerType(it) }
    }
}