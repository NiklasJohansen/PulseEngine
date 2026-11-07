package no.njoh.pulseengine.core.graphics.scene3d.lighting

import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderDecal
import no.njoh.pulseengine.core.graphics.scene3d.view.CameraRenderState
import no.njoh.pulseengine.core.shared.datastructures.StaticList

class ClusteredDecalGrid : ClusteredGrid()
{
    private var bounds = FloatArray(0) // x/y/z/radius

    fun build(state: CameraRenderState, decals: StaticList<RenderDecal>)
    {
        if (bounds.size < decals.size * 4) 
            bounds = FloatArray(maxOf(decals.size * 4, bounds.size * 2))

        var i = 0
        decals.forEach()
        {
            val visible = it.opacity > 0f && it.targetLayerMask != 0 && (it.fadeEndDistance <= 0f || state.cameraPosition.distanceSquared(it.position) < it.fadeEndDistance * it.fadeEndDistance)

            bounds[i + 0] = it.position.x
            bounds[i + 1] = it.position.y
            bounds[i + 2] = it.position.z
            bounds[i + 3] = if (visible) it.radius else -1f
            i += 4
        }
        
        build(state, bounds, decals.size)
    }
}
