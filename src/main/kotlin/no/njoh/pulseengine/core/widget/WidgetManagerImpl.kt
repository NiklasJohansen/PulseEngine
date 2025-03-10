package no.njoh.pulseengine.core.widget

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFiltered
import no.njoh.pulseengine.core.shared.utils.Extensions.isNotIn
import no.njoh.pulseengine.core.shared.utils.Extensions.measureMillisTime
import no.njoh.pulseengine.core.shared.utils.Logger

open class WidgetManagerImpl: WidgetManagerInternal()
{
    private var widgets = mutableListOf<Widget>()
    private var renderTimeMs = 0f
    private var updateTimeMs = 0f
    private var fixedUpdateTimeMs = 0f

    override fun init(engine: PulseEngine)
    {
        Logger.info {"Initializing widgets (WidgetManagerImpl)" }

        // Initialize all added widgets
        widgets.forEachFast { it.onCreate(engine) }

        // Add metrics to measure widget performance
        engine.data.addMetric("WIDGETS UPDATE (MS)") { sample(updateTimeMs) }
        engine.data.addMetric("WIDGETS FIXED UPDATE (MS)") { sample(fixedUpdateTimeMs) }
        engine.data.addMetric("WIDGETS RENDER (MS)") { sample(renderTimeMs) }
    }

    override fun update(engine: PulseEngine)
    {
        updateTimeMs = measureMillisTime()
        {
            widgets.forEachFiltered({ it.isRunning }) { it.onUpdate(engine) }
        }
    }

    override fun fixedUpdate(engine: PulseEngine)
    {
        fixedUpdateTimeMs = measureMillisTime()
        {
            widgets.forEachFiltered({ it.isRunning }) { it.onFixedUpdate(engine) }
        }
    }

    override fun render(engine: PulseEngine)
    {
        renderTimeMs = measureMillisTime()
        {
            widgets.forEachFiltered({ it.isRunning }) { it.onRender(engine) }
        }
    }

    override fun destroy(engine: PulseEngine)
    {
        Logger.info { "Destroying widgets (${this::class.simpleName})" }
        widgets.forEachFast { it.onDestroy(engine) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(type: Class<T>): T?
    {
        return widgets.firstOrNullFast { it.javaClass == type } as? T?
    }

    override fun add(vararg widgets: Widget)
    {
        widgets.forEachFast { if (it isNotIn this.widgets) this.widgets.add(it) }
    }

    override fun remove(widget: Widget)
    {
        widgets.remove(widget)
    }
}