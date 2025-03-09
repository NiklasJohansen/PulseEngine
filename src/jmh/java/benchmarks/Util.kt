package benchmarks

import benchmarks.BenchmarkAccuracy.*
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.Mode.Throughput
import org.openjdk.jmh.profile.GCProfiler
import org.openjdk.jmh.results.format.ResultFormatType.JSON
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import org.openjdk.jmh.runner.options.TimeValue
import java.time.Instant

internal enum class BenchmarkAccuracy { LOW, MEDIUM, HIGH }

internal inline fun <reified T> runBenchmark(
    accuracy: BenchmarkAccuracy = MEDIUM,
    mode: Mode = Throughput,
    measureGc: Boolean = true,
    jsonResult: Boolean = true
) {
    val name                  = T::class.simpleName
    val forks                 = when (accuracy) { LOW -> 1; MEDIUM ->  2; HIGH ->  3 }
    val warmupIterations      = when (accuracy) { LOW -> 2; MEDIUM ->  5; HIGH -> 10 }
    val measurementIterations = when (accuracy) { LOW -> 3; MEDIUM -> 10; HIGH -> 20 }

    val options = OptionsBuilder()
        .include(name)
        .forks(forks)
        .threads(1)
        .mode(mode)
        .warmupIterations(warmupIterations)
        .warmupTime(TimeValue.seconds(2))
        .measurementIterations(measurementIterations)
        .measurementTime(TimeValue.seconds(2))

    if (measureGc) options.addProfiler(GCProfiler::class.java)

    if (jsonResult) options.resultFormat(JSON).result("$name-result-${Instant.now().toEpochMilli()}.json")

    Runner(options.build()).run()
}