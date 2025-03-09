package benchmarks

import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.TextBuilderContext
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

fun main() = runBenchmark<TextBuilderBenchmark>()

@State(Scope.Benchmark)
open class TextBuilderBenchmark
{
    private var numbers = (0 .. 1000).shuffled()
    private var context = TextBuilderContext()

    @Benchmark
    fun stringConcatenation(bh: Blackhole)
    {
        numbers.forEachFast()
        {
            bh.consume("Length: ${it}m, Area: ${it * it}m2")
        }
    }

    @Benchmark
    fun textBuilder(bh: Blackhole)
    {
        numbers.forEachFast()
        {
            bh.consume(context.build { "Length: " plus it plus "m, Area: " plus (it * it) plus "m2" })
        }
    }
}