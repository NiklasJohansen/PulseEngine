package no.njoh.pulseengine.modules.widget

import no.njoh.pulseengine.PulseEngine

interface Widget
{
    var isRunning: Boolean

    fun onCreate(engine: PulseEngine)
    fun onUpdate(engine: PulseEngine)
    fun onRender(engine: PulseEngine)
    fun onDestroy(engine: PulseEngine)

    fun start(): Widget { isRunning = true; return this }
    fun stop(): Widget { isRunning = false; return this }
}