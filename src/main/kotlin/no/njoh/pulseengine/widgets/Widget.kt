package no.njoh.pulseengine.widgets

import no.njoh.pulseengine.PulseEngine

interface Widget
{
    fun onCreate(engine: PulseEngine)
    fun onUpdate(engine: PulseEngine)
    fun onRender(engine: PulseEngine)
    fun onDestroy(engine: PulseEngine)
}