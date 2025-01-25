package benchmarks

import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

fun main() = runBenchmark<ForEachBenchmark>()

@State(Scope.Benchmark)
open class ForEachBenchmark
{
    private var numbers = (0 .. 1000).shuffled()

    @Benchmark
    fun forEach(bh: Blackhole)
    {
        var sum = 0
        numbers.forEach { sum += it }
        bh.consume(sum)
    }

    @Benchmark
    fun forEachFast(bh: Blackhole)
    {
        var sum = 0
        numbers.forEachFast { sum += it }
        bh.consume(sum)
    }
}