package no.njoh.pulseengine.core.widget

import no.njoh.pulseengine.core.PulseEngine

abstract class WidgetManager
{
    /**
     * Returns the first [Widget] of specified type [T] or null
     */
    inline fun <reified T> get(): T? = get(T::class.java)

    /**
     * Returns the first [Widget] of specified class type or null
     */
    abstract fun <T> get(type: Class<T>): T?

    /**
     * Adds the specified [Widget]s to the manager
     */
    abstract fun add(vararg widgets: Widget)

    /**
     * Removes the specified [Widget]s from the manager
     */
    abstract fun remove(widget: Widget)
}

abstract class WidgetManagerInternal : WidgetManager()
{
    abstract fun init(engine: PulseEngine)
    abstract fun update(engine: PulseEngine)
    abstract fun fixedUpdate(engine: PulseEngine)
    abstract fun render(engine: PulseEngine)
    abstract fun destroy(engine: PulseEngine)
}