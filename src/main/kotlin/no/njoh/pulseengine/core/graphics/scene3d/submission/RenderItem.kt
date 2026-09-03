package no.njoh.pulseengine.core.graphics.scene3d.submission

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.CullMode.BACK
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask
import no.njoh.pulseengine.core.shared.primitives.Mat4f
import org.joml.Matrix4f

class RenderItem
{
    var mesh           = TMP_MESH;                 private set
    var material       = null as Material?;        private set
    var transform      = Mat4f(0);                 private set
    var cullingBounds  = null as Model.Aabb?;      private set
    var boneMatrices   = null as Array<Matrix4f>?; private set
    var renderPassMask = RenderPassMask.EMPTY;     private set
    var renderId       = -1L;                      private set
    var batchSortKey   = -1;                       private set

    internal var gpuInstanceIndex = -1
    internal var gpuCullItemIndex = -1

    fun set(mesh: Model.Mesh, material: Material?, transform: Mat4f, cullingBounds: Model.Aabb?, boneMatrices: Array<Matrix4f>?, renderPassMask: RenderPassMask, renderId: Long = -1L)
    {
        this.mesh = mesh
        this.material = material
        this.transform = transform
        this.cullingBounds = cullingBounds
        this.boneMatrices = boneMatrices
        this.renderPassMask = renderPassMask
        this.renderId = renderId
        this.batchSortKey = mesh.getBatchSortKey(material?.cullMode ?: BACK)
    }

    fun isVisible(pass: RenderPassMask) = (renderPassMask.mask and pass.mask) != 0

    companion object
    {
        private val TMP_MESH = Model.Mesh(0, 0, 0, 0, 0, 0, 0, Model.Aabb())
    }
}