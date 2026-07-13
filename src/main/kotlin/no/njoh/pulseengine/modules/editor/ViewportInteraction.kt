package no.njoh.pulseengine.modules.editor

import no.njoh.pulseengine.core.PulseEngine

interface ViewportInteraction
{
    fun onCreate(engine: PulseEngine, context: ViewportContext) {}
    fun onEditorActivated(engine: PulseEngine, context: ViewportContext) {}
    fun onEditorDeactivated(engine: PulseEngine, context: ViewportContext) {}
    fun onUpdate(engine: PulseEngine, context: ViewportContext)
    fun onRender(engine: PulseEngine, context: ViewportContext)
    fun resetCamera(engine: PulseEngine, context: ViewportContext) {}
    fun reset(engine: PulseEngine, context: ViewportContext) {}
    fun onDestroy(engine: PulseEngine, context: ViewportContext) {}
}