package benchmarks

import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.TextBuilderContext
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
open class TextBuilderBenchmark
{
    private var numbers = (0 .. 1000).shuffled()
    private var context = TextBuilderContext()

    @Benchmark
    fun benchmarkStringConcatenation(bh: Blackhole)
    {
        numbers.forEachFast()
        {
            bh.consume("Length: ${it}m, Area: ${it * it}m2")
        }
    }

    @Benchmark
    fun benchmarkTextBuilder(bh: Blackhole)
    {
        numbers.forEachFast()
        {
            bh.consume(context.build { "Length: " plus it plus "m, Area: " plus (it * it) plus "m2" })
        }
    }
}