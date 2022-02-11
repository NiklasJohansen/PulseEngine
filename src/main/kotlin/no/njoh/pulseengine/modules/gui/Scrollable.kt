package no.njoh.pulseengine.modules.gui

interface Scrollable
{
    var scrollFraction: Float
    var hideScrollbarOnEnoughSpaceAvailable: Boolean
    fun setScroll(fraction: Float)
    fun getUsedSpaceFraction(): Float
}