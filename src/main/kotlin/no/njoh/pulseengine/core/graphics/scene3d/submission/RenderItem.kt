package no.njoh.pulseengine.core.graphics.scene3d.submission

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.CullMode
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet.ShaderVariant
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet.ShaderVariant.SKINNED
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet.ShaderVariant.STATIC
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
    var objectId: Long = -1L
) {
    var gpuInstanceIndex = -1
    var gpuCullItemIndex = -1
    var batchSortKey = createBatchSortKey(mesh, material)
        private set

    fun setBatchState(mesh: Model.Mesh, material: Material?)
    {
        this.mesh = mesh
        this.material = material
        this.batchSortKey = createBatchSortKey(mesh, material)
    }

    fun isVisible(pass: RenderPassMask) = (renderPassMask.mask and pass.mask) != 0

    private fun createBatchSortKey(mesh: Model.Mesh, material: Material?): Long
    {
        val shaderVariant = if (mesh.skinningBounds != null) SKINNED else STATIC
        val cullMode = material?.cullMode ?: CullMode.BACK
        return (mesh.gpuMetadataIndex.toLong() shl 2) or
               (shaderVariant.ordinal.toLong() shl 1) or 
                cullMode.ordinal.toLong()
    }
}