package no.njoh.pulseengine.core.widget

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFiltered

open class WidgetManagerImpl: WidgetManagerInternal()
{
    private var renderTimeMs = 0f
    private var updateTimeMs = 0f

    override fun init(engine: PulseEngine)
    {
        widgets.forEachFast { it.onCreate(engine) }
        engine.data.addMetric("WIDGETS UPDATE", "MS") { updateTimeMs }
        engine.data.addMetric("WIDGETS RENDER", "MS") { renderTimeMs }
    }

    override fun update(engine: PulseEngine)
    {
        val start = System.nanoTime()
        widgets.forEachFiltered({ it.isRunning }) { it.onUpdate(engine) }
        updateTimeMs = (System.nanoTime() - start) / 1000000f
    }

    override fun render(engine: PulseEngine)
    {
        val start = System.nanoTime()
        widgets.forEachFiltered({ it.isRunning }) { it.onRender(engine) }
        renderTimeMs = (System.nanoTime() - start) / 1000000f
    }

    override fun cleanUp(engine: PulseEngine)
    {
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