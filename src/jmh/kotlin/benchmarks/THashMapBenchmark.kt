package benchmarks

import gnu.trove.map.hash.THashMap
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import kotlin.random.Random

fun main() = runBenchmark<THashMapBenchmark>(accuracy = BenchmarkAccuracy.LOW)

@State(Scope.Benchmark)
open class THashMapBenchmark
{
    @Param("10", "100", "1000", "10000")
    private var size = 0

    private lateinit var keys: List<String>
    private lateinit var values: Array<Int>
    private lateinit var tHashMap: THashMap<String, Int>
    private lateinit var linkedHashMap: LinkedHashMap<String, Int>
    private lateinit var hashMap: HashMap<String, Int>
    private lateinit var tHashMapDestination: THashMap<String, Int>
    private lateinit var linkedHashMapDestination: LinkedHashMap<String, Int>
    private lateinit var hashMapDestination: HashMap<String, Int>

    @Setup(Level.Trial)
    fun setUpTrial()
    {
        val capacity = initialCapacity(size)
        keys = List(size) { "asset_$it" }.shuffled(Random(1337))
        values = Array(size) { it }
        tHashMap = THashMap(capacity, LOAD_FACTOR)
        linkedHashMap = LinkedHashMap(capacity, LOAD_FACTOR)
        hashMap = HashMap(capacity, LOAD_FACTOR)
        tHashMapDestination = THashMap(capacity, LOAD_FACTOR)
        linkedHashMapDestination = LinkedHashMap(capacity, LOAD_FACTOR)
        hashMapDestination = HashMap(capacity, LOAD_FACTOR)

        var i = 0
        while (i < size)
        {
            tHashMap[keys[i]] = values[i]
            linkedHashMap[keys[i]] = values[i]
            hashMap[keys[i]] = values[i]
            i++
        }
    }

    @Setup(Level.Invocation)
    fun setUpInvocation()
    {
        tHashMapDestination.clear()
        linkedHashMapDestination.clear()
        hashMapDestination.clear()
    }

    @Benchmark
    fun tHashMapGet(bh: Blackhole)
    {
        var sum = 0
        var i = 0
        while (i < size)
        {
            sum += tHashMap[keys[i]] ?: 0
            i++
        }
        bh.consume(sum)
    }

    @Benchmark
    fun linkedHashMapGet(bh: Blackhole)
    {
        var sum = 0
        var i = 0
        while (i < size)
        {
            sum += linkedHashMap[keys[i]] ?: 0
            i++
        }
        bh.consume(sum)
    }

    @Benchmark
    fun hashMapGet(bh: Blackhole)
    {
        var sum = 0
        var i = 0
        while (i < size)
        {
            sum += hashMap[keys[i]] ?: 0
            i++
        }
        bh.consume(sum)
    }

    @Benchmark
    fun tHashMapPut(bh: Blackhole)
    {
        var i = 0
        while (i < size)
        {
            tHashMapDestination[keys[i]] = values[i]
            i++
        }
        bh.consume(tHashMapDestination)
    }

    @Benchmark
    fun linkedHashMapPut(bh: Blackhole)
    {
        var i = 0
        while (i < size)
        {
            linkedHashMapDestination[keys[i]] = values[i]
            i++
        }
        bh.consume(linkedHashMapDestination)
    }

    @Benchmark
    fun hashMapPut(bh: Blackhole)
    {
        var i = 0
        while (i < size)
        {
            hashMapDestination[keys[i]] = values[i]
            i++
        }
        bh.consume(hashMapDestination)
    }

    companion object
    {
        private const val LOAD_FACTOR = 0.75f

        private fun initialCapacity(size: Int) = (size / LOAD_FACTOR).toInt() + 1
    }
}
