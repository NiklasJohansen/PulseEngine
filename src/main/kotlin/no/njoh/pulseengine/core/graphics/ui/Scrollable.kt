package no.njoh.pulseengine.core.graphics.ui

interface Scrollable
{
    var scrollFraction: Float
    var hideScrollbarOnEnoughSpaceAvailable: Boolean
    fun setScroll(fraction: Float)
    fun getUsedSpaceFraction(): Float
}