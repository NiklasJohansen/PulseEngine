package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.api.world.DrawPayload.EmptyDrawPayload
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode

class PreparedRenderPass(
    val drawPayload: DrawPayload,
    val cullViewCount: Int
) {
    fun cullView(index: Int): CullViewIndex
    {
        require(index in 0 until cullViewCount) { "Cull view index $index is outside prepared pass range 0 until $cullViewCount" }
        return CullViewIndex(index)
    }

    companion object
    {
        val EMPTY = PreparedRenderPass(EmptyDrawPayload, cullViewCount = 1)
    }
}

@JvmInline
value class CullViewIndex(val value: Int)

sealed interface DrawPayload
{
    data object EmptyDrawPayload : DrawPayload

    data class DirectDrawPayload(
        val instanceIndexMode: ModelInstanceIndexMode,
        val instanceIndexBuffer: StreamingIntBufferObject?
    ) : DrawPayload

    data class IndirectDrawPayload(
        val commandBuffer: StreamingIntBufferObject,
        val commandBaseIndex: Int,
        val cullViewCommandStride: Int,
        val useVisibleInstanceBuffer: Boolean,
        val visibleInstanceBuffer: StreamingIntBufferObject?,
        val instanceIndexMode: ModelInstanceIndexMode,
        val instanceIndexBuffer: StreamingIntBufferObject?
    ) : DrawPayload {

        fun getCommandByteOffset(cullView: CullViewIndex, commandStartIndex: Int): Long
        {
            val cullViewCommandOffset = cullViewCommandStride * cullView.value
            val commandIndex = commandBaseIndex + cullViewCommandOffset + commandStartIndex
            return commandBuffer.getSubmittedDataByteOffset() + commandIndex.toLong() * INDIRECT_COMMAND_STRIDE_BYTES
        }
    }

    companion object
    {
        const val INDIRECT_COMMAND_INTS = 5
        const val INDIRECT_COMMAND_STRIDE_BYTES = INDIRECT_COMMAND_INTS * Int.SIZE_BYTES
    }
}