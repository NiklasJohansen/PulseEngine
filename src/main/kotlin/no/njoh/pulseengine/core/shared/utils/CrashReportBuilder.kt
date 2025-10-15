package no.njoh.pulseengine.core.shared.utils

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.GraphicsInternal
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import java.io.File
import java.lang.management.ManagementFactory
import java.time.ZonedDateTime

object CrashReportBuilder
{
    fun buildAndOpen(history: List<String>)
    {
        val report = StringBuilder()
        report.appendLine("PulseEngine Crash Report")
        report.appendLine("Time: ${ZonedDateTime.now()}")
        report.appendLine("Thread: ${Thread.currentThread().name}")
        report.appendLine("\nRuntime details:")
        collectRuntimeDetails().forEach { (key, value) -> report.appendLine("$key: $value") }
        report.appendLine("\nLast 100 log lines:")
        history.forEachFast { report.appendLine(it) }
        try {
            val file = File("crash_report_${ZonedDateTime.now().toEpochSecond()}.txt")
            file.createNewFile()
            file.writeText(report.toString())
            openFile(file.absolutePath)
            println("Crash report written to ${file.absolutePath}")
        } catch (e: Exception) {
            println("Failed to write crash report: ${e.message}")
        }
    }

    fun collectRuntimeDetails(): Map<String, Any?>
    {
        val runtime = Runtime.getRuntime()
        return mapOf(
            "osName" to (System.getProperty("os.name") ?: "?"),
            "osVersion" to (System.getProperty("os.version") ?: "?"),
            "osArch" to (System.getProperty("os.arch") ?: "?"),
            "availableProcessors" to runtime.availableProcessors(),
            "jvmName" to (System.getProperty("java.vm.name") ?: "?"),
            "jvmVersion" to (System.getProperty("java.version") ?: "?"),
            "jvmVendor" to (System.getProperty("java.vendor") ?: "?"),
            "jvmArgs" to runCatching { ManagementFactory.getRuntimeMXBean().inputArguments }.getOrNull(),
            "maxMemory" to runtime.maxMemory(),
            "totalMemory" to runtime.totalMemory(),
            "freeMemory" to runtime.freeMemory(),
            "appDir" to (System.getProperty("user.dir") ?: "?"),
            "gpu" to (PulseEngine.INSTANCE.gfx as GraphicsInternal).gpuName
        )
    }
}