package no.njoh.pulseengine.core.widget

import no.njoh.pulseengine.core.PulseEngine

interface Widget
{
    var isRunning: Boolean

    fun onCreate(engine: PulseEngine) { }
    fun onFixedUpdate(engine: PulseEngine) { }
    fun onUpdate(engine: PulseEngine) { }
    fun onRender(engine: PulseEngine) { }
    fun onDestroy(engine: PulseEngine) { }

    fun start(): Widget { isRunning = true; return this }
    fun stop(): Widget { isRunning = false; return this }
}