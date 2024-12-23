package no.njoh.pulseengine.core.window

import no.njoh.pulseengine.core.config.ConfigurationInternal

interface Window
{
    /** The text displayed in the title bar */
    val title: String

    /** The horizontal pixel width of the window */
    val width: Int

    /** The vertical pixel height of the window */
    val height: Int

    /** The current content scale set by the OS (250% = 2.5 scale) */
    val contentScale: Float

    /** The current screen mode of the window */
    val screenMode: ScreenMode

    /** True if the window is focused */
    val isFocused: Boolean

    /** True if the window was resized since last frame */
    val wasResized: Boolean

    /**
     * Sets a callback that is called when the screen scale is changed. Will happen if the window
     * is moved to a screen with a different scale or the screen scale is changed manually in the OS settings.
     */
    fun setOnContentScaleChanged(callback: (scale: Float) -> Unit)

    /**
     * Updates the current screen mode of the window.
     */
    fun updateScreenMode(mode: ScreenMode)

    /**
     * Updates the window title text.
     */
    fun updateTitle(title: String)

    /**
     * Closes the window and the whole application.
     */
    fun close()
}

interface WindowInternal : Window
{
    override var wasResized: Boolean
    val windowHandle: Long
    val cursorPosScale: Float

    fun init(config: ConfigurationInternal)
    fun setOnResizeEvent(callback: (width: Int, height: Int, windowRecreated: Boolean) -> Unit)
    fun swapBuffers()
    fun isOpen(): Boolean
    fun destroy()
}