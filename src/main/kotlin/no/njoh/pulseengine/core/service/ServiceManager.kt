package no.njoh.pulseengine.core.service

import no.njoh.pulseengine.core.PulseEngine

abstract class ServiceManager
{
    /**
     * Returns the first [Service] of specified type [T] or null
     */
    inline fun <reified T> get(): T? = get(T::class.java)

    /**
     * Returns the first [Service] of specified class type or null
     */
    abstract fun <T> get(type: Class<T>): T?

    /**
     * Returns a list of all [Service]s.
     */
    abstract fun getAll(): List<Service>

    /**
     * Adds the specified [Service]s to the manager
     */
    abstract fun add(vararg services: Service)

    /**
     * Removes the specified [Service]s from the manager
     */
    abstract fun remove(service: Service)
}

abstract class ServiceManagerInternal : ServiceManager()
{
    abstract fun init(engine: PulseEngine)
    abstract fun update(engine: PulseEngine)
    abstract fun fixedUpdate(engine: PulseEngine)
    abstract fun render(engine: PulseEngine)
    abstract fun destroy(engine: PulseEngine)
}