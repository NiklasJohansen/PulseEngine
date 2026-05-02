package no.njoh.pulseengine.core.asset.types

import org.joml.Quaternionf
import org.joml.Vector3f

class Animation(
    filePath: String,
    name: String,
    val modelName: String,
    val index: Int,
    val durationTicks: Double,
    val ticksPerSecond: Double,
    val channels: List<NodeAnimation>
) : Asset(filePath, name) {

    val channelsByNodeName = channels.associateBy { it.nodeName }
    val durationSeconds: Double
        get() = if (ticksPerSecond > 0.0) durationTicks / ticksPerSecond else durationTicks

    override fun load() {}

    override fun unload() {}

    fun wrapTimeSecondsToTicks(timeSeconds: Double): Double
    {
        if (durationTicks <= 0.0)
            return 0.0

        val rawTimeTicks = timeSeconds * ticksPerSecond
        var wrappedTimeTicks = rawTimeTicks % durationTicks
        if (wrappedTimeTicks < 0.0)
            wrappedTimeTicks += durationTicks

        return wrappedTimeTicks
    }

    data class NodeAnimation(
        val nodeName: String,
        val positionKeys: List<VectorKey>,
        val rotationKeys: List<QuaternionKey>,
        val scalingKeys: List<VectorKey>,
        val preState: Int,
        val postState: Int
    )

    data class VectorKey(
        val time: Double,
        val value: Vector3f,
        val interpolation: Int
    )

    data class QuaternionKey(
        val time: Double,
        val value: Quaternionf,
        val interpolation: Int
    )
}