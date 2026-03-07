package no.njoh.pulseengine.core.shared.platform

import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

class PlatformEventBuffer
{
    @PublishedApi
    internal val buffer = ArrayList<PlatformEvent>()

    fun add(event: PlatformEvent) = buffer.add(event)

    inline fun forEachEvent(action: (PlatformEvent) -> Unit)
    {
        buffer.forEachFast(action)
    }

    fun clear()
    {
        buffer.forEachFast(PlatformEvent::release)
        buffer.clear()
    }
}