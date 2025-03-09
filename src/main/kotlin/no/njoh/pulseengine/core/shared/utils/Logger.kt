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
    var LEVEL = DEBUG
    var TARGET = STDOUT

    private const val RESET = "\u001B[0m"
    private const val RED = "\u001B[31m"
    private const val YELLOW = "\u001B[33m"
    private const val BLUE = "\u001B[34m"
    private const val WHITE = "\u001B[37m"
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SS")

    @PublishedApi
    internal val context = TextBuilderContext()

    fun debug(text: String) = log(DEBUG) { text }
    fun info(text: String)  = log(INFO)  { text }
    fun warn(text: String)  = log(WARN)  { text }
    fun error(text: String) = log(ERROR) { text }

    inline fun debug(text: TextBuilder) = log(DEBUG, text)
    inline fun info(text: TextBuilder)  = log(INFO,  text)
    inline fun warn(text: TextBuilder)  = log(WARN,  text)
    inline fun error(text: TextBuilder) = log(ERROR, text)

    inline fun log(level: LogLevel, text: TextBuilder)
    {
        if (level.value < LEVEL.value || LEVEL == OFF)
            return

        when (TARGET)
        {
            STDOUT -> logToStandardOut(context.build(text), level)
            CONSOLE -> logToConsole(context.build(text), level)
        }
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

enum class LogLevel(val value: Int)
{
    OFF(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4);

    companion object
    {
        fun maxOf(a: LogLevel, b: LogLevel) = if (a.value > b.value) a else b
    }
}

enum class LogTarget
{
    STDOUT,
    CONSOLE
}