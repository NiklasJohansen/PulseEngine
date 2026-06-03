package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode

sealed interface WorldRenderDrawPayload
{
    data object EmptyDrawPayload : WorldRenderDrawPayload

    data class DirectDrawPayload(
        val instanceIndexMode: ModelInstanceIndexMode,
        val instanceIndexBuffer: StreamingIntBufferObject?
    ) : WorldRenderDrawPayload

    data class IndirectDrawPayload(
        val commandBuffer: StreamingIntBufferObject,
        val commandBaseIndex: Int,
        val commandSetStride: Int,
        val useVisibleInstanceBuffer: Boolean,
        val visibleInstanceBuffer: StreamingIntBufferObject?,
        val instanceIndexMode: ModelInstanceIndexMode,
        val instanceIndexBuffer: StreamingIntBufferObject?
    ) : WorldRenderDrawPayload {
        fun getCommandByteOffset(commandSetIndex: Int, commandStartIndex: Int): Long
        {
            val commandSetOffset = if (commandSetStride == 0) 0 else commandSetIndex * commandSetStride
            val commandIndex = commandBaseIndex + commandSetOffset + commandStartIndex
            return commandBuffer.getSubmittedDataByteOffset() + commandIndex.toLong() * INDIRECT_COMMAND_STRIDE_BYTES
        }
    }

    companion object
    {
        const val INDIRECT_COMMAND_INTS = 5
        const val INDIRECT_COMMAND_STRIDE_BYTES = INDIRECT_COMMAND_INTS * Int.SIZE_BYTES
    }
}