package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.api.world.DrawPayload.EmptyDrawPayload
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode

class PreparedRenderPass(
    val drawPayload: DrawPayload,
    val cullViewCount: Int
) {
    companion object
    {
        val EMPTY = PreparedRenderPass(EmptyDrawPayload, cullViewCount = 1)
    }
}

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

        fun getCommandByteOffset(cullViewIndex: Int, commandStartIndex: Int): Long
        {
            val cullViewCommandOffset = cullViewCommandStride * cullViewIndex
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