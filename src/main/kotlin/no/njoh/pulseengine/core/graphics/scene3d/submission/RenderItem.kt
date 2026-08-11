package no.njoh.pulseengine.core.graphics.scene3d.submission

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.CullMode.BACK
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask
import no.njoh.pulseengine.core.shared.primitives.Mat4f
import org.joml.Matrix4f

class RenderItem(
    var mesh: Model.Mesh,
    var material: Material?,
    var transform: Mat4f,
    var cullingBounds: Model.Aabb?,
    var boneMatrices: Array<Matrix4f>?,
    var renderPassMask: RenderPassMask,
    var renderId: Long = -1L
) {
    var gpuInstanceIndex = -1
    var gpuCullItemIndex = -1
    var batchSortKey = mesh.getBatchSortKey(material?.cullMode ?: BACK)
        private set

    fun setBatchState(mesh: Model.Mesh, material: Material?)
    {
        this.mesh = mesh
        this.material = material
        this.batchSortKey = mesh.getBatchSortKey(material?.cullMode ?: BACK)
    }

    fun isVisible(pass: RenderPassMask) = (renderPassMask.mask and pass.mask) != 0
}