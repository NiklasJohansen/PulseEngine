package no.njoh.pulseengine.core.graphics.scene3d.view

/**
 * Typed identity and factory for a [RenderView]. Keys compare by [group], [type], and [qualifier],
 * allowing independently created equivalent keys to share one view.
 */
class RenderViewKey<T : RenderView>(
    val group: RenderViewGroup,
    val type: Class<T>,
    val qualifier: Any?,
    val create: () -> T
) {
    override fun equals(other: Any?): Boolean =
        other is RenderViewKey<*> && group == other.group && type == other.type && qualifier == other.qualifier

    override fun hashCode(): Int = 31 * (31 * group.hashCode() + type.hashCode()) + (qualifier?.hashCode() ?: 0)

    override fun toString(): String = buildString()
    {
        append(type.simpleName).append('@').append(group)
        if (qualifier != null) append('[').append(qualifier).append(']')
    }

    companion object
    {
        inline operator fun <reified T : RenderView> invoke(group: RenderViewGroup, qualifier: Any? = null, noinline create: () -> T) = 
            RenderViewKey(group, T::class.java, qualifier, create)
    }
}