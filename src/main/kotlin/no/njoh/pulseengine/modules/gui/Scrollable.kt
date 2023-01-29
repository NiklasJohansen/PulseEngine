package no.njoh.pulseengine.modules.gui

sealed interface Scrollable
{
    fun onScroll(x: Float, y: Float)
    {
        if (this is HorizontallyScrollable) xScroll = x
        if (this is VerticallyScrollable) yScroll = y
    }
}

interface VerticallyScrollable : Scrollable
{
    var yScroll: Float
    var verticalScrollbarVisibility: ScrollbarVisibility
    fun setVerticalScroll(fraction: Float)
    fun getVerticallyUsedSpaceFraction(): Float
}

interface HorizontallyScrollable : Scrollable
{
    var xScroll: Float
    var horizontalScrollbarVisibility: ScrollbarVisibility
    fun setHorizontalScroll(fraction: Float)
    fun getHorizontallyUsedSpaceFraction(): Float
}

enum class ScrollDirection {
    VERTICAL, HORIZONTAL;
}

enum class ScrollbarVisibility
{
    ALWAYS_VISIBLE,
    ONLY_VISIBLE_WHEN_NEEDED,
    HIDDEN
}