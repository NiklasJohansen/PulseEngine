package no.njoh.pulseengine.core.graphics.scene3d.view

@JvmInline
value class RenderPassMask(val mask: Int)
{
    companion object
    {
        val EMPTY         = RenderPassMask(0)
        val CAMERA        = RenderPassMask(1 shl 0)
        val GLOBAL_SHADOW = RenderPassMask(1 shl 1)
        val LOCAL_SHADOW  = RenderPassMask(1 shl 2)
    }

    infix fun or(other: RenderPassMask) = RenderPassMask(mask or other.mask)

    fun takeIf(condition: Boolean) = if (condition) this else EMPTY
}