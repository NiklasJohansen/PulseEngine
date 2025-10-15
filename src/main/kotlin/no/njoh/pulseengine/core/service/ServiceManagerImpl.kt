package no.njoh.pulseengine.core.service

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFiltered
import no.njoh.pulseengine.core.shared.utils.Extensions.isNotIn
import no.njoh.pulseengine.core.shared.utils.Extensions.measureMillisTime
import no.njoh.pulseengine.core.shared.utils.Logger

open class ServiceManagerImpl: ServiceManagerInternal()
{
    private var services = mutableListOf<Service>()
    private var renderTimeMs = 0f
    private var updateTimeMs = 0f
    private var fixedUpdateTimeMs = 0f

    override fun init(engine: PulseEngine)
    {
        Logger.info { "Initializing services (ServiceManagerImpl)" }

        // Initialize all added services
        services.forEachFast { it.onCreate(engine) }

        // Add metrics to measure service performance
        engine.data.addMetric("SERVICE UPDATE (MS)") { sample(updateTimeMs) }
        engine.data.addMetric("SERVICE FIXED UPDATE (MS)") { sample(fixedUpdateTimeMs) }
        engine.data.addMetric("SERVICE RENDER (MS)") { sample(renderTimeMs) }
    }

    override fun update(engine: PulseEngine)
    {
        updateTimeMs = measureMillisTime()
        {
            services.forEachFiltered({ it.isRunning }) { it.onUpdate(engine) }
        }
    }

    override fun fixedUpdate(engine: PulseEngine)
    {
        fixedUpdateTimeMs = measureMillisTime()
        {
            services.forEachFiltered({ it.isRunning }) { it.onFixedUpdate(engine) }
        }
    }

    override fun render(engine: PulseEngine)
    {
        renderTimeMs = measureMillisTime()
        {
            services.forEachFiltered({ it.isRunning }) { it.onRender(engine) }
        }
    }

    override fun destroy(engine: PulseEngine)
    {
        Logger.info { "Destroying services (${this::class.simpleName})" }
        services.forEachFast { it.onDestroy(engine) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(type: Class<T>) = services.firstOrNullFast { it.javaClass == type } as? T?

    override fun getAll(): List<Service> = services

    override fun add(vararg services: Service)
    {
        services.forEachFast { if (it isNotIn this.services) this.services.add(it) }
    }

    override fun remove(service: Service)
    {
        services.remove(service)
    }
}