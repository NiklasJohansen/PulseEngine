package no.njoh.pulseengine.core.graphics.scene3d.view

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