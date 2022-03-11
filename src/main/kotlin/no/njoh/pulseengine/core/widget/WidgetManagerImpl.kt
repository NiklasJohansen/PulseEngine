package no.njoh.pulseengine.core.widget

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFiltered
import no.njoh.pulseengine.core.shared.utils.Extensions.measureMillisTime
import no.njoh.pulseengine.core.shared.utils.Logger

open class WidgetManagerImpl: WidgetManagerInternal()
{
    private var renderTimeMs = 0f
    private var updateTimeMs = 0f

    override fun init(engine: PulseEngine)
    {
        Logger.info("Initializing widgets (${this::class.simpleName})")

        // Initialize all added widgets
        widgets.forEachFast { it.onCreate(engine) }

        // Add metrics to measure widget performance
        engine.data.addMetric("WIDGETS UPDATE", "MS") { updateTimeMs }
        engine.data.addMetric("WIDGETS RENDER", "MS") { renderTimeMs }
    }

    override fun update(engine: PulseEngine)
    {
        updateTimeMs = measureMillisTime()
        {
            widgets.forEachFiltered({ it.isRunning }) { it.onUpdate(engine) }
        }
    }

    override fun render(engine: PulseEngine)
    {
        renderTimeMs = measureMillisTime()
        {
            widgets.forEachFiltered({ it.isRunning }) { it.onRender(engine) }
        }
    }

    override fun cleanUp(engine: PulseEngine)
    {
        Logger.info("Cleaning up widgets (${this::class.simpleName})")
        widgets.forEachFast { it.onDestroy(engine) }
    }

    override fun add(vararg widgets: Widget)
    {
        widgets.forEach {
            if (it !in this.widgets)
                this.widgets.add(it)
        }
    }

    override fun terminate(widget: Widget)
    {
        widgets.remove(widget)
    }
}