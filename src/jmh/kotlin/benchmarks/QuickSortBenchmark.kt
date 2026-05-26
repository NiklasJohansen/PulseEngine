package benchmarks

import no.njoh.pulseengine.core.shared.utils.Extensions.quickSort
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import kotlin.random.Random

fun main() = runBenchmark<QuickSortBenchmark>(accuracy = BenchmarkAccuracy.LOW)

@State(Scope.Benchmark)
open class QuickSortBenchmark
{
    @Param("10", "100", "1000", "10000")
    private var size = 0

    private lateinit var sourceValues: List<Int>
    private lateinit var values: MutableList<Int>

    @Setup(Level.Trial)
    fun setUpTrial()
    {
        sourceValues = List(size) { it }.shuffled(Random(1337))
        values = ArrayList(sourceValues)
    }

    @Setup(Level.Invocation)
    fun setUpInvocation()
    {
        var i = 0
        while (i < size)
        {
            values[i] = sourceValues[i]
            i++
        }
    }

    @Benchmark
    fun quickSort(bh: Blackhole)
    {
        values.quickSort(INT_COMPARATOR)
        bh.consume(values)
    }

    @Benchmark
    fun kotlinSortWith(bh: Blackhole)
    {
        values.sortWith(INT_COMPARATOR)
        bh.consume(values)
    }

    companion object
    {
        private val INT_COMPARATOR = Comparator<Int> { left, right -> left.compareTo(right) }
    }
}
