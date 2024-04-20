package no.njoh.pulseengine.core.window

interface Window
{
    var title: String
    val width: Int
    val height: Int
    val scale: Float
    val screenMode: ScreenMode
    val wasResized: Boolean

    fun setOnScaleChanged(callback: (scale: Float) -> Unit)
    fun updateScreenMode(mode: ScreenMode)
    fun close()
}

interface WindowInternal : Window
{
    override var wasResized: Boolean
    val windowHandle: Long

    fun init(initWidth: Int, initHeight: Int, screenMode: ScreenMode, gameName: String)
    fun destroy()
    fun setOnResizeEvent(callback: (width: Int, height: Int, windowRecreated: Boolean) -> Unit)
    fun swapBuffers()
    fun isOpen(): Boolean
}