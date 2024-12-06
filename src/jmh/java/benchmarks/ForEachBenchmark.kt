package benchmarks

import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode.Throughput
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

@Fork(2)
@BenchmarkMode(Throughput)
@OutputTimeUnit(MILLISECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
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