package no.njoh.pulseengine.modules.gui

sealed interface Scrollable

interface VerticallyScrollable : Scrollable
{
    var verticalScrollbarVisibility: ScrollbarVisibility
    fun setVerticalScroll(fraction: Float)
    fun getVerticallyUsedSpaceFraction(): Float
}

interface HorizontallyScrollable : Scrollable
{
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