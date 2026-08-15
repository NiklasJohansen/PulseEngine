package no.njoh.pulseengine.core.window

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.config.ConfigurationInternal
import no.njoh.pulseengine.core.shared.platform.PlatformEventBuffer

class NoOpWindow : WindowInternal
{
    override val height = 0
    override val width = 0
    override val contentScale = 1f
    override val cursorPosScale = 1f
    override var title = "NO_WINDOW"
    override val screenMode = ScreenMode.WINDOWED
    override val isFocused = false
    override var wasResized = false
    override val windowHandle = -1L
    private var isOpen = true
    override fun close() { isOpen = false }
    override fun destroy() {}
    override fun init(config: ConfigurationInternal) { }
    override fun initFrame(engineInternal: PulseEngineInternal) {}
    override fun pollIncomingPlatformEvents(buffer: PlatformEventBuffer) {}
    override fun handleOutgoingPlatformEvents(buffer: PlatformEventBuffer) {}
    override fun isOpen() = isOpen
    override fun setOnContentScaleChanged(callback: (scale: Float) -> Unit) {}
    override fun swapBuffers() {}
    override fun setOnResizeEvent(callback: (width: Int, height: Int, windowRecreated: Boolean) -> Unit) {}
    override fun setOnFileDropped(callback: (String) -> Unit) {}
    override fun updateScreenMode(mode: ScreenMode) {}
    override fun updateTitle(title: String) {}
}
