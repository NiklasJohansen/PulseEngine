package benchmarks

import no.njoh.pulseengine.core.shared.primitives.DynamicList
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

fun main()
{
    runBenchmark<DynamicListAddAllBenchmark>()
    runBenchmark<DynamicListRemoveBenchmark>()
}

@State(Scope.Benchmark)
open class DynamicListAddAllBenchmark
{
    @Param("10", "100", "1000", "10000")
    private var sourceSize = 0

    private lateinit var arrayListSource: List<String>
    private lateinit var dynamicListSource: DynamicList<String>
    private lateinit var arrayListDestination: ArrayList<String>
    private lateinit var dynamicListDestination: DynamicList<String>

    @Setup(Level.Trial)
    fun setUpTrial()
    {
        arrayListSource = (0..sourceSize).map(Int::toString)
        dynamicListSource = DynamicList(arrayListSource)
    }

    @Setup(Level.Invocation)
    fun setUpInvocation()
    {
        arrayListDestination = ArrayList()
        dynamicListDestination = DynamicList()
    }

    /////////////////////////////////////////////// addAll

    @Benchmark
    fun arrayListAddAll(bh: Blackhole)
    {
        arrayListDestination.addAll(arrayListSource)
        bh.consume(arrayListDestination)
    }

    @Benchmark
    fun dynamicListAddAllFromArrayList(bh: Blackhole)
    {
        dynamicListDestination += arrayListSource
        bh.consume(dynamicListDestination)
    }

    @Benchmark
    fun dynamicListAddAllFromDynamicList(bh: Blackhole)
    {
        dynamicListDestination += dynamicListSource
        bh.consume(dynamicListDestination)
    }
}

@State(Scope.Benchmark)
open class DynamicListRemoveBenchmark
{
    @Param("10", "100", "1000", "10000")
    private var sourceSize = 0

    private lateinit var arrayList: ArrayList<Int>
    private lateinit var dynamicList: DynamicList<Int>

    @Setup(Level.Invocation)
    fun setUpInvocation()
    {
        arrayList = (0..sourceSize).toMutableList() as ArrayList<Int>
        dynamicList = DynamicList(arrayList)
    }

    @Benchmark
    fun arrayListRemove(bh: Blackhole)
    {
        arrayList.removeAt(sourceSize / 2)
        arrayList.remove(sourceSize / 2 + 1)
        arrayList.removeIf { it % 2 == 0 }
        arrayList.removeLast()
        arrayList.removeFirst()
        bh.consume(arrayList)
    }

    @Benchmark
    fun dynamicListRemove(bh: Blackhole)
    {
        dynamicList.removeAtOrNull(sourceSize / 2)
        dynamicList.minusAssign(sourceSize / 2 + 1)
        dynamicList.removeIf { it % 2 == 0 }
        dynamicList.removeLastOrNull()
        dynamicList.removeFirstOrNull()
        bh.consume(dynamicList)
    }
}