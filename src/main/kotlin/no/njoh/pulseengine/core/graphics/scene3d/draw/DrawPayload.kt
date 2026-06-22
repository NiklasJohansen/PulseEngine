package no.njoh.pulseengine.core.graphics.scene3d.draw

import no.njoh.pulseengine.core.graphics.gpu.buffer.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode

sealed interface DrawPayload
{
    val cullViewCount: Int

    data object EmptyDrawPayload : DrawPayload
    {
        override val cullViewCount = 1
    }

    data class DirectDrawPayload(
        override val cullViewCount: Int,
        val instanceIndexMode: ModelInstanceIndexMode,
        val instanceIndexBuffer: StreamingIntBufferObject?
    ) : DrawPayload

    data class IndirectDrawPayload(
        override val cullViewCount: Int,
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