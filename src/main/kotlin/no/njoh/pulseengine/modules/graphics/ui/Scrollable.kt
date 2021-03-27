package no.njoh.pulseengine.modules.graphics.ui

interface Scrollable
{
    var scrollFraction: Float
    var hideScrollbarOnEnoughSpaceAvailable: Boolean
    fun setScroll(fraction: Float)
    fun getUsedSpaceFraction(): Float
}