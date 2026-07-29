package no.njoh.pulseengine.modules.editor

import no.njoh.pulseengine.core.PulseEngine

abstract class ViewportInteraction(val mode: EditorMode)
{
    open fun onCreate(engine: PulseEngine, context: ViewportContext) {}
    open fun onEditorActivated(engine: PulseEngine, context: ViewportContext) {}
    open fun onEditorDeactivated(engine: PulseEngine, context: ViewportContext) {}
    open fun onUpdate(engine: PulseEngine, context: ViewportContext) {}
    open fun onRender(engine: PulseEngine, context: ViewportContext) {}
    open fun onDestroy(engine: PulseEngine, context: ViewportContext) {}

    abstract fun captureCameraState(context: ViewportContext): CameraState?
    abstract fun restoreCameraState(engine: PulseEngine, context: ViewportContext, state: CameraState?)
    abstract fun resetCamera(engine: PulseEngine, context: ViewportContext)
    abstract fun reset(engine: PulseEngine, context: ViewportContext)
}

enum class EditorMode
{
    MODE_2D,
    MODE_3D
}