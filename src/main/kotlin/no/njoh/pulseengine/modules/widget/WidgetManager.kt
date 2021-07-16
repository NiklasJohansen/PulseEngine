package no.njoh.pulseengine.modules.widget

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.util.forEachFiltered
import no.njoh.pulseengine.util.forEachFast

abstract class WidgetManager
{
    @PublishedApi
    internal val widgets = mutableListOf<Widget>()
    inline fun <reified T> get(): T? =
        widgets.firstOrNull { it is T } as T?

    abstract fun add(vararg widgets: Widget)
    abstract fun terminate(widget: Widget)
}

abstract class WidgetManagerEngineInterface : WidgetManager()
{
    abstract fun init(engine: PulseEngine)
    abstract fun update(engine: PulseEngine)
    abstract fun render(engine: PulseEngine)
    abstract fun cleanUp(engine: PulseEngine)
}

class WidgetManagerImpl: WidgetManagerEngineInterface()
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