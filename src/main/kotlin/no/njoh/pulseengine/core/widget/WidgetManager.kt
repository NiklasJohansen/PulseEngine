package no.njoh.pulseengine.core.widget

import no.njoh.pulseengine.core.PulseEngine

abstract class WidgetManager
{
    @PublishedApi
    internal val widgets = mutableListOf<Widget>()
    inline fun <reified T> get(): T? =
        widgets.firstOrNull { it is T } as T?

    abstract fun add(vararg widgets: Widget)
    abstract fun terminate(widget: Widget)
}

abstract class WidgetManagerInternal : WidgetManager()
{
    abstract fun init(engine: PulseEngine)
    abstract fun update(engine: PulseEngine)
    abstract fun fixedUpdate(engine: PulseEngine)
    abstract fun render(engine: PulseEngine)
    abstract fun cleanUp(engine: PulseEngine)
}