package no.njoh.pulseengine.core.graphics.scene3d.view

import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.CAMERA
import java.util.concurrent.atomic.AtomicLong

/**
 * Identifies renderers that intentionally share equivalent view keys and prepared view state.
 * Each surface owns a unique group by default. Stereo surfaces explicitly share one group.
 */
@JvmInline
value class RenderViewGroup private constructor(private val id: Long)
{
    companion object
    {
        private val nextId = AtomicLong()
        fun create() = RenderViewGroup(nextId.incrementAndGet())
    }
}

/**
 * A view group for cameras with an optional render pass mask.
 */
class CameraRenderViewGroup(
    val renderPassMask: RenderPassMask = CAMERA
) {
    private val group = RenderViewGroup.create()

    fun createViewKey() = RenderViewKey(group) { CameraRenderView(renderPassMask = renderPassMask) }
}