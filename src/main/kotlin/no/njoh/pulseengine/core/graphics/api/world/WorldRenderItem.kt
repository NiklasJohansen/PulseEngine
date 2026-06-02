package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Model
import org.joml.Matrix4f

data class WorldRenderItem(
    val model: Model,
    val subMesh: Model.SubMesh,
    val material: Material?,
    val transform: Matrix4f,
    val cullable: Boolean,
    val cullingBounds: Model.Aabb,
    val boneMatrices: Array<Matrix4f>?,
    val viewIds: Int
) {
    var gpuInstanceIndex = -1
    var gpuCullItemIndex = -1

    fun isInView(viewId: Int) = (viewIds and viewId) != 0
}