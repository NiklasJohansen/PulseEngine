package no.njoh.pulseengine.core.network.shared

import kotlin.reflect.KClass
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Logger
import org.objenesis.strategy.StdInstantiatorStrategy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A codec interface for encoding and decoding objects to and from binary data.
 * This interface is used for network communication, allowing objects to be serialized
 * into a binary format for transmission and deserialized back into objects.
 */
interface NetworkCodec
{
    /**
     * Encodes an object to a [BinaryData]. This instance may be reused, so don't hold on to it!
     */
    fun encode(obj: Any): BinaryData

    /**
     * Decodes a [BinaryData] to an object.
     */
    fun decode(data: BinaryData): Any

    /**
     * Decodes a byte array to an object.
     */
    fun decode(data: ByteArray, offset: Int, length: Int): Any

    /**
     * Registers a type with the codec. If the type is a sealed class, all its subclasses should be registered as well.
     */
    fun registerType(type: KClass<*>)

    /**
     * Represents a binary data buffer with its length.
     */
    class BinaryData(var buffer: ByteArray, var length: Int)
}

/**
 * A binary codec for encoding and decoding objects using Kryo.
 * @param types The classes to register with Kryo. If a sealed class is provided, all its subclasses will be registered.
 * @param initialBufferSizeBytes The initial size of the output buffer in bytes.
 * @param maxBufferSizeBytes The maximum size of the output buffer in bytes.
 * @param compatibilityMode If true, decoding has better backwards compatibility with older model/schema versions.
 * @param requireClassRegistration If true, requires all classes to be registered with Kryo before encoding/decoding.
 */
class KryoNetworkCodec(
    vararg types: KClass<*>,
    val initialBufferSizeBytes: Int = 512,
    val maxBufferSizeBytes: Int = 4096,
    val compatibilityMode: Boolean = false,
    val requireClassRegistration: Boolean = true,
    val typeIdRange: Int = 1_000_000
): NetworkCodec {

    private val typeRegistry    = ConcurrentHashMap.newKeySet<KClass<*>>()
    private val registryVersion = AtomicInteger(0)
    private val localRegVersion = ThreadLocal.withInitial { 0 }

    private val data   = ThreadLocal.withInitial { NetworkCodec.BinaryData(ByteArray(0), 0) }
    private val input  = ThreadLocal.withInitial { Input(1) }
    private val output = ThreadLocal.withInitial { Output(initialBufferSizeBytes, maxBufferSizeBytes) }
    private val kryo   = ThreadLocal.withInitial()
    {
        Kryo().apply()
        {
            references = false
            isRegistrationRequired = requireClassRegistration
            instantiatorStrategy = DefaultInstantiatorStrategy(StdInstantiatorStrategy())
            setDefaultSerializer(if (compatibilityMode) CompatibleFieldSerializer::class.java else FieldSerializer::class.java)
            syncWithTypeRegistry()
        }
    }

    // Initial registration of provided types
    init { types.forEachFast { addTypeToRegistry(it) } }

    /**
     * Registers a type with Kryo. If the type is a sealed class, all its subclasses are registered as well.
     * The type is registered with a unique ID based on its qualified name to avoid having the full type name
     * in the serialized data.
     */
    override fun registerType(type: KClass<*>)
    {
        addTypeToRegistry(type)
        kryo.get().syncWithTypeRegistry()
    }

    /**
     * Encodes an object to a [BinaryData].
     * This instance is reused, so don't hold on to it!
     */
    override fun encode(obj: Any): NetworkCodec.BinaryData
    {
        val kryo = kryo.get()
        val output = output.get().apply { reset() }
        kryo.syncWithTypeRegistry()
        kryo.writeClassAndObject(output, obj)
        return data.get().apply { buffer = output.buffer; length = output.position() }
    }

    /**
     * Decodes a [BinaryData] to an object of type [T].
     */
    override fun decode(data: NetworkCodec.BinaryData): Any = decode(data.buffer, 0, data.length)

    /**
     * Decodes a byte array to an object of type [T].
     */
    override fun decode(data: ByteArray, offset: Int, length: Int): Any
    {
        val kryo = kryo.get()
        kryo.syncWithTypeRegistry()
        return kryo.readClassAndObject(input.get().apply { setBuffer(data, offset, length) })
    }

    // Utils ------------------------------------------------------------------------

    /**
     * Adds a type and its subclasses (if it's a sealed class) to the registry.
     * Increments the registry version if new types are added.
     */
    private fun addTypeToRegistry(type: KClass<*>) =
        findSubClasses(type).forEach { if (typeRegistry.add(it)) registryVersion.incrementAndGet() }

    /**
     * Synchronizes the local Kryo instance with the global type registry if there are new types to register.
     */
    private fun Kryo.syncWithTypeRegistry()
    {
        val target = registryVersion.get()
        if (localRegVersion.get() == target)
            return // Already in sync
        typeRegistry.forEach { registerType(it) }
        localRegVersion.set(target)
    }

    /**
     * Registers a type with Kryo using a unique ID based on its qualified name.
     * If the ID is already in use, a warning is logged and the type is not registered.
     */
    private fun Kryo.registerType(type: KClass<*>)
    {
        if (this.classResolver.getRegistration(type.java) != null)
            return // Already registered
        val key = type.qualifiedName ?: type.java.name
        val id = 100 + (key.hashCode() and 0x7FFFFFFF) % typeIdRange
        val reg = getRegistration(id)
        if (reg == null) register(type.java, id)
        else Logger.error { "ID clash in BinaryCodec! id=$id is already used by ${reg.type.name} and can not be used by $key. Try tweaking the typeIdRange." }
    }

    /**
     * Recursively finds all subclasses of a sealed class, including the class itself.
     */
    private fun findSubClasses(root: KClass<*>, classes: MutableList<KClass<*>> = mutableListOf()): List<KClass<*>>
    {
        root.sealedSubclasses.let { if (it.isEmpty()) classes += root else it.forEachFast { c -> findSubClasses(c, classes) } }
        return classes
    }
}