package benchmarks

import no.njoh.pulseengine.core.shared.primitives.FlatObjectBuffer
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

fun main() = runBenchmark<BufferBenchmark>(measureGc = false)

@State(Scope.Benchmark)
open class BufferBenchmark
{
    private val numbers = (0 until 100_000).shuffled().map(Int::toFloat)

    private val floatArray = FloatArray(numbers.size * 8)
    private val flatBufferDelegate = FlatBufferDelegate(numbers.size)
    private val flatBuffer = FlatBuffer(numbers.size)
    private val objectArray = Array(numbers.size) { TestObject() }

    @Benchmark
    fun flatBuffer(bh: Blackhole)
    {
        var value = 1f
        val buffer = flatBuffer.clear()
        numbers.forEachFast()
        {
            buffer.a = value + it
            buffer.b = value + buffer.a
            buffer.c = value + buffer.b
            buffer.d = value + buffer.c
            buffer.e = value + buffer.d
            buffer.f = value + buffer.e
            buffer.g = value + buffer.f
            buffer.h = value + buffer.g
            value = buffer.h
            buffer.next()
        }
        buffer.flip()

        var sum = 0f
        buffer.forEach()
        {
            sum += it.a
            sum += it.b
            sum += it.c
            sum += it.d
            sum += it.e
            sum += it.f
            sum += it.g
            sum += it.h
        }
        bh.consume(sum)
    }

    @Benchmark
    fun flatBufferDelegate(bh: Blackhole)
    {
        var value = 1f
        val buffer = flatBufferDelegate
        buffer.clear()
        numbers.forEachFast()
        {
            buffer.a = value + it
            buffer.b = value + buffer.a
            buffer.c = value + buffer.b
            buffer.d = value + buffer.c
            buffer.e = value + buffer.d
            buffer.f = value + buffer.e
            buffer.g = value + buffer.f
            buffer.h = value + buffer.g
            value = buffer.h
            buffer.next()
        }
        buffer.flip()

        var sum = 0f
        buffer.forEach()
        {
            sum += it.a
            sum += it.b
            sum += it.c
            sum += it.d
            sum += it.e
            sum += it.f
            sum += it.g
            sum += it.h
        }
        bh.consume(sum)
    }

    @Benchmark
    fun objectArray(bh: Blackhole)
    {
        var i = 0
        var value = 1f
        val array = objectArray
        numbers.forEachFast()
        {
            val obj = array[i++]
            obj.a = value + it
            obj.b = value + obj.a
            obj.c = value + obj.b
            obj.d = value + obj.c
            obj.e = value + obj.d
            obj.f = value + obj.e
            obj.g = value + obj.f
            obj.h = value + obj.g
            value = obj.h
        }

        var sum = 0f
        array.forEachFast()
        {
            sum += it.a
            sum += it.b
            sum += it.c
            sum += it.d
            sum += it.e
            sum += it.f
            sum += it.g
            sum += it.h
        }
        bh.consume(sum)
    }

    @Benchmark
    fun floatArray(bh: Blackhole)
    {
        var i = 0
        var value = 1f
        val array = floatArray
        numbers.forEachFast()
        {
            array[i + 0] = value + it
            array[i + 1] = value + array[i + 0]
            array[i + 2] = value + array[i + 1]
            array[i + 3] = value + array[i + 2]
            array[i + 4] = value + array[i + 3]
            array[i + 5] = value + array[i + 4]
            array[i + 6] = value + array[i + 5]
            array[i + 7] = value + array[i + 6]
            value = array[i + 7]
            i += 8
        }

        i = 0
        var sum = 0f
        val size = array.size
        while (i < size)
        {
            sum += array[i + 0]
            sum += array[i + 1]
            sum += array[i + 2]
            sum += array[i + 3]
            sum += array[i + 4]
            sum += array[i + 5]
            sum += array[i + 6]
            sum += array[i + 7]
            i += 8
        }
        bh.consume(sum)
    }

    private class FlatBuffer(capacity: Int) : FlatObjectBuffer<FlatBuffer>(stride = 8)
    {
        @JvmField
        val data = FloatArray(capacity * stride)

        inline var a get() = data[pos + 0]; set(v) { data[pos + 0] = v }
        inline var b get() = data[pos + 1]; set(v) { data[pos + 1] = v }
        inline var c get() = data[pos + 2]; set(v) { data[pos + 2] = v }
        inline var d get() = data[pos + 3]; set(v) { data[pos + 3] = v }
        inline var e get() = data[pos + 4]; set(v) { data[pos + 4] = v }
        inline var f get() = data[pos + 5]; set(v) { data[pos + 5] = v }
        inline var g get() = data[pos + 6]; set(v) { data[pos + 6] = v }
        inline var h get() = data[pos + 7]; set(v) { data[pos + 7] = v }
    }

    private class FlatBufferDelegate(capacity: Int) : FlatObjectBuffer<FlatBufferDelegate>(stride = 8)
    {
        @JvmField
        val data = FloatArray(capacity * stride)

        var a by FloatRef(data, 0)
        var b by FloatRef(data, 1)
        var c by FloatRef(data, 2)
        var d by FloatRef(data, 3)
        var e by FloatRef(data, 4)
        var f by FloatRef(data, 5)
        var g by FloatRef(data, 6)
        var h by FloatRef(data, 7)
    }

    private data class TestObject(
        var a: Float = 0f,
        var b: Float = 0f,
        var c: Float = 0f,
        var d: Float = 0f,
        var e: Float = 0f,
        var f: Float = 0f,
        var g: Float = 0f,
        var h: Float = 0f
    )
}