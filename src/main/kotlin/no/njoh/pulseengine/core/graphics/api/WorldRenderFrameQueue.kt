package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.shared.primitives.DynamicList

class WorldRenderFrameQueue(val worldRenderState: WorldRenderState) 
{
    @PublishedApi internal var readFrames = DynamicList<WorldRenderFrame>()
    @PublishedApi internal var writeFrames = DynamicList<WorldRenderFrame>()

    fun initFrame() 
    {
        writeFrames = readFrames.also { readFrames = writeFrames }
        writeFrames.clear()
        readFrames.forEach(worldRenderState::registerUse)
    }

    fun submit(frame: WorldRenderFrame) 
    {
        writeFrames += frame
    }

    inline fun forEachReadable(block: (WorldRenderFrame) -> Unit) 
    {
        readFrames.forEach { block(it) }
    }
}