package benchmarks

import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Benchmark)
open class ForEachBenchmark
{
    private var numbers = (0 .. 1000).shuffled()

    @Benchmark
    fun benchmarkForEachFast(bh: Blackhole)
    {
        var sum = 0
        numbers.forEachFast { sum += it }
        bh.consume(sum)
    }

    @Benchmark
    fun benchmarkForEach(bh: Blackhole)
    {
        var sum = 0
        numbers.forEach { sum += it }
        bh.consume(sum)
    }
}