package no.njoh.pulseengine.core.graphics.api.world.views

import no.njoh.pulseengine.core.graphics.api.world.WorldRenderCommandBuilder
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderScene

interface WorldRenderView
{
    val viewId: Int

    fun update(scene: WorldRenderScene, builder: WorldRenderCommandBuilder)

    fun clear()
}

object ViewIds
{
    const val MAIN_CAMERA_VIEW   = 1 shl 0
    const val GLOBAL_SHADOW_VIEW = 1 shl 1
    const val LOCAL_SHADOW_VIEW  = 1 shl 2
}