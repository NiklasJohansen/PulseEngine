package no.njoh.pulseengine.core.graphics.scene3d.submission

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.shared.primitives.Mobility
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.EMPTY
import org.joml.Matrix4f

class ModelItem
{
    val transform      = Matrix4f()
    var model          = TMP_MODEL;           private set
    var material       = null as Material?;   private set
    var lodThresholds  = null as FloatArray?; private set
    var lodHysteresis  = 0f;                  private set
    var lodKey         = 0L;                  private set
    var renderId       = -1L;                 private set
    var renderPassMask = EMPTY;               private set
    var mobility       = Mobility.DYNAMIC;    private set

    fun set(model: Model, transform: Matrix4f, material: Material?, renderPassMask: RenderPassMask, mobility: Mobility, lodThresholds: FloatArray?, lodHysteresis: Float, lodKey: Long, renderId: Long)
    {
        this.transform.set(transform)
        this.model          = model
        this.material       = material
        this.renderPassMask = renderPassMask
        this.mobility       = mobility
        this.lodThresholds  = lodThresholds
        this.lodHysteresis  = lodHysteresis
        this.lodKey         = lodKey
        this.renderId       = renderId
    }

    companion object
    {
        private val TMP_MODEL = Model("tmp", "tmp")
    }
}