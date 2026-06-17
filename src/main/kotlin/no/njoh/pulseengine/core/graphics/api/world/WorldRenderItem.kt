package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Model
import org.joml.Matrix4f

class WorldRenderItem(
    var mesh: Model.Mesh,
    var material: Material?,
    var transform: Matrix4f,
    var cullingBounds: Model.Aabb?,
    var boneMatrices: Array<Matrix4f>?,
    var visibilityMask: Int
) {
    var gpuInstanceIndex = -1
    var gpuCullItemIndex = -1

    fun isVisible(requiredVisibility: Int) = (visibilityMask and requiredVisibility) != 0
}