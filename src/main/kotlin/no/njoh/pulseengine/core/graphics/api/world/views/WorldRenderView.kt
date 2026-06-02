package no.njoh.pulseengine.core.graphics.api.world.views

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderScene
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItemCullingData
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItemGpuCuller

interface WorldRenderView
{
    val viewId: Int
    val culler: WorldRenderItemGpuCuller?

    fun update(
        engine: PulseEngineInternal,
        scene: WorldRenderScene,
        commandBufferStarIndex: Int,
        cullData: WorldRenderItemCullingData
    ): Int
    
    fun finish()

    fun clear()
    
    fun destroy()
}

object ViewIds
{
    const val MAIN_CAMERA_VIEW = 1
    const val SHADOW_VIEW      = 1 shl 1
}