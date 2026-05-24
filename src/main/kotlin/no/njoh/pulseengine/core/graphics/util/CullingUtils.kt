package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.graphics.api.DrawList.RenderItem
import no.njoh.pulseengine.core.graphics.api.Frustum

fun ArrayList<RenderItem>.addVisibleItems(source: List<RenderItem>, frustum: Frustum)
{
    for (i in source.indices)
    {
        val item = source[i]
        if (!item.cullable || frustum.intersectsAabb(item.cullingBounds, item.transform))
            this += item
    }
}