package no.njoh.pulseengine.modules.window

import no.njoh.pulseengine.data.ScreenMode

interface Window
{
    var title: String
    val width: Int
    val height: Int
    val screenMode: ScreenMode
    val wasResized: Boolean

    fun updateScreenMode(mode: ScreenMode)
    fun close()
}

interface WindowInternal : Window
{
    override var wasResized: Boolean
    val windowHandle: Long

    fun init(initWidth: Int, initHeight: Int, screenMode: ScreenMode, gameName: String)
    fun cleanUp()
    fun setOnResizeEvent(callback: (width: Int, height: Int, windowRecreated: Boolean) -> Unit)
    fun swapBuffers()
    fun isOpen(): Boolean
}