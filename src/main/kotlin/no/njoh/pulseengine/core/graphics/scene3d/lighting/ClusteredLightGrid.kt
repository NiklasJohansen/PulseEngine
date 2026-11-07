package no.njoh.pulseengine.core.graphics.scene3d.lighting

import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderLight
import no.njoh.pulseengine.core.graphics.scene3d.view.CameraRenderState
import no.njoh.pulseengine.core.shared.datastructures.StaticList

class ClusteredLightGrid : ClusteredGrid()
{
    private var bounds = FloatArray(0) // x/y/z/radius

    fun build(state: CameraRenderState, lights: StaticList<RenderLight>)
    {
        if (bounds.size < lights.size * 4) 
            bounds = FloatArray(maxOf(lights.size * 4, bounds.size * 2))

        var i = 0
        lights.forEach()
        {
            bounds[i + 0] = it.position.x
            bounds[i + 1] = it.position.y
            bounds[i + 2] = it.position.z
            bounds[i + 3] = it.range
            i += 4
        }

        build(state, bounds, lights.size)
    }
}