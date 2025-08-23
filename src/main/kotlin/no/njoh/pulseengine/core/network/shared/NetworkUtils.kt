package no.njoh.pulseengine.core.network.shared

import java.io.EOFException
import java.net.SocketException
import java.nio.channels.ClosedChannelException
import kotlin.collections.any
import kotlin.let
import kotlin.text.contains
import kotlin.text.lowercase

object NetworkUtils
{
    private val EXPECTED_SOCKET_MESSAGES = arrayOf(
        "socket closed",
        "connection reset",
        "broken pipe",
        "connection abort",
        "forcibly closed",
        "closed channel",
        "timed out"
    )

    fun Throwable.isExpectedCloseError(): Boolean = when (this)
    {
        is ClosedChannelException -> true
        is EOFException -> true
        is SocketException -> message?.lowercase()?.let { msg -> EXPECTED_SOCKET_MESSAGES.any { it in msg } } ?: false
        else -> cause?.isExpectedCloseError() == true
    }
}