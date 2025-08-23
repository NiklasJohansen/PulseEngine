package no.njoh.pulseengine.core.network.shared

import java.util.Locale
import kotlin.text.format

class NetworkStats
{
    // TCP
    val inTcpByteCount    = MetricCounter()
    val inTcpPacketCount  = MetricCounter()
    val outTcpByteCount   = MetricCounter()
    val outTcpPacketCount = MetricCounter()

    // UDP
    val inUdpByteCount    = MetricCounter()
    val inUdpPacketCount  = MetricCounter()
    val outUdpByteCount   = MetricCounter()
    val outUdpPacketCount = MetricCounter()

    fun getSummary() =
        "IO-TCP-Packets [${inTcpPacketCount.countTotal} (${inTcpPacketCount.countPerSec}/S), ${outTcpPacketCount.countTotal} (${outTcpPacketCount.countPerSec}/S)] " +
        "IO-TCP-Bytes [${inTcpByteCount.countTotal.format()} (${inTcpByteCount.countPerSec.format()}/S), ${outTcpByteCount.countTotal.format()} (${outTcpByteCount.countPerSec.format()}/S)] " +
        "IO-UDP-Packets [${inUdpPacketCount.countTotal} (${inUdpPacketCount.countPerSec}/S), ${outUdpPacketCount.countTotal} (${outUdpPacketCount.countPerSec}/S)] " +
        "IO-UDP-Bytes [${inUdpByteCount.countTotal.format()} (${inUdpByteCount.countPerSec.format()}/S), ${outUdpByteCount.countTotal.format()} (${outUdpByteCount.countPerSec.format()}/S)]"

    private fun Long.format() =
        if (this < 1000) "${this}B"
        else if (this < 1_000_000) "%.1f".format(Locale.US, this / 1000f) + "KB"
        else if (this < 1_000_000_000) "%.1f".format(Locale.US, this / (1_000_000f)) + "MB"
        else "%.1f".format(Locale.US, this / (1_000_000_000f)) + "GB"

    class MetricCounter
    {
        var countTotal  = 0L; private set
        var countPerSec = 0L; private set
            get() = if (field != 0L && System.nanoTime() > lastMeasureTimeNs + SECOND_NS * 2) { field = 0L; 0L } else field

        private var currentCount = 0L
        private var lastMeasureTimeNs = 0L

        fun increase(amount: Int = 1) = synchronized(this)
        {
            val now = System.nanoTime()
            if (now > lastMeasureTimeNs + SECOND_NS)
            {
                lastMeasureTimeNs = now
                countPerSec = currentCount
                currentCount = 0
            }
            currentCount += amount
            countTotal += amount
        }

        companion object { private const val SECOND_NS = 1_000_000_000L }
    }
}