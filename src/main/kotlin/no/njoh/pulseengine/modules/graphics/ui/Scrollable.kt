package no.njoh.pulseengine.modules.graphics.ui

interface Scrollable
{
    var scrollFraction: Float
    fun setScroll(fraction: Float)
    fun getUsedSpaceFraction(): Float
}