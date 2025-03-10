package no.njoh.pulseengine.core.shared.utils

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.console.MessageType
import no.njoh.pulseengine.core.shared.utils.LogLevel.*
import no.njoh.pulseengine.core.shared.utils.LogTarget.CONSOLE
import no.njoh.pulseengine.core.shared.utils.LogTarget.STDOUT
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object Logger
{
    @JvmField var LEVEL = DEBUG
    @JvmField var TARGET = STDOUT

    private const val RESET  = "\u001B[0m"
    private const val RED    = "\u001B[31m"
    private const val YELLOW = "\u001B[33m"
    private const val BLUE   = "\u001B[34m"
    private const val WHITE  = "\u001B[37m"
    private val TIME_FORMAT  = DateTimeFormatter.ofPattern("HH:mm:ss.SS")

    @PublishedApi
    internal val context = TextBuilderContext()

    inline fun debug(text: () -> CharSequence) { if (DEBUG.ordinal >= LEVEL.ordinal) logToTarget(DEBUG, text()) }
    inline fun info(text:  () -> CharSequence) { if (INFO.ordinal  >= LEVEL.ordinal) logToTarget(INFO, text())  }
    inline fun warn(text:  () -> CharSequence) { if (WARN.ordinal  >= LEVEL.ordinal) logToTarget(WARN, text())  }
    inline fun error(text: () -> CharSequence) { if (ERROR.ordinal >= LEVEL.ordinal) logToTarget(ERROR, text()) }

    inline fun log(level: LogLevel, text: TextBuilder)
    {
        if (level.ordinal >= LEVEL.ordinal) logToTarget(level, context.build(text))
    }

    @PublishedApi
    internal fun logToTarget(level: LogLevel, text: CharSequence) = when (TARGET)
    {
        STDOUT -> logToStandardOut(text, level)
        CONSOLE -> logToConsole(text, level)
    }

    @PublishedApi
    internal fun logToConsole(text: CharSequence, level: LogLevel)
    {
        val time = LocalTime.now().format(TIME_FORMAT)
        val levelText = "[${level}]".padEnd(7)
        val messageType = when (level)
        {
            DEBUG -> MessageType.INFO
            INFO -> MessageType.INFO
            WARN -> MessageType.WARN
            ERROR -> MessageType.ERROR
            else -> MessageType.INFO
        }
        PulseEngine.INSTANCE.console.log("$levelText [$time]  $text", messageType)
    }

    @PublishedApi
    internal fun logToStandardOut(text: CharSequence, level: LogLevel)
    {
        val time = LocalTime.now().format(TIME_FORMAT)
        val levelText = "[${level}]".padEnd(7)
        val levelColor = when (level)
        {
            DEBUG -> BLUE
            INFO -> RESET
            WARN -> YELLOW
            ERROR -> RED
            else -> RESET
        }
        println("${levelColor}$levelText $WHITE[$time]$RESET $text")
    }
}

enum class LogLevel
{
    DEBUG,
    INFO,
    WARN,
    ERROR,
    OFF;

    companion object
    {
        fun maxOf(a: LogLevel, b: LogLevel) = if (a > b) a else b
    }
}

enum class LogTarget
{
    STDOUT,
    CONSOLE
}