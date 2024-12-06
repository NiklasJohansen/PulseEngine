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
    private const val RESET = "\u001B[0m"
    private const val RED = "\u001B[31m"
    private const val YELLOW = "\u001B[33m"
    private const val BLUE = "\u001B[34m"
    private const val WHITE = "\u001B[37m"
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SS")

    var logLevel = DEBUG
    var logTarget = STDOUT

    fun debug(text: String) = log(text, DEBUG)
    fun info(text: String) = log(text, INFO)
    fun warn(text: String) = log(text, WARN)
    fun error(text: String) = log(text, ERROR)

    private fun log(text: String, level: LogLevel)
    {
        if (level == OFF || level < logLevel)
            return

        when (logTarget)
        {
            STDOUT -> logToStandardOut(text, level)
            CONSOLE -> logToConsole(text, level)
        }
    }

    private fun logToConsole(text: String, level: LogLevel)
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

    private fun logToStandardOut(text: String, level: LogLevel)
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
        println("${levelColor}$levelText $WHITE[$time]$RESET  $text")
    }
}

enum class LogLevel
{
    DEBUG,
    INFO,
    WARN,
    ERROR,
    OFF
}

enum class LogTarget
{
    STDOUT,
    CONSOLE
}