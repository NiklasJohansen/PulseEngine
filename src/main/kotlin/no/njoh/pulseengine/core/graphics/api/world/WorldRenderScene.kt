package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.shared.primitives.DynamicList

class WorldRenderScene
{
    val opaqueItems  = DynamicList<WorldRenderItem>(1024)
    val maskedItems  = DynamicList<WorldRenderItem>(512)
    val blendedItems = DynamicList<WorldRenderItem>(256)

    fun add(item: WorldRenderItem)
    {
        when (item.material?.blendMode ?: Material.BlendMode.OPAQUE)
        {
            Material.BlendMode.OPAQUE -> opaqueItems  += item
            Material.BlendMode.MASK -> maskedItems  += item
            Material.BlendMode.BLEND -> blendedItems += item
        }
    }

    fun clear()
    {
        opaqueItems.clear()
        maskedItems.clear()
        blendedItems.clear()
    }
 
    fun hasAnyItems() = opaqueItems.isNotEmpty() || maskedItems.isNotEmpty() || blendedItems.isNotEmpty()
}