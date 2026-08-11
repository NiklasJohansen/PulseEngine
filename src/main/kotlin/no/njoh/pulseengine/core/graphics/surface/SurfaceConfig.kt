package no.njoh.pulseengine.core.graphics.surface

import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint
import no.njoh.pulseengine.core.graphics.gpu.texture.BlendFunction
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import java.lang.Float.intBitsToFloat

interface SurfaceConfig
{
    val name: String
    val width: Int
    val height: Int
    val zOrder: Int
    val isVisible: Boolean
    var drawPostEffects: Boolean
    var drawWireframe: Boolean
    val resolutionScale: Float
    val multisampling: Multisampling
    val sizeFunction: SurfaceSizeFunction
    val clearColor: Color?
    val blendFunction: BlendFunction
    val attachments: List<SurfaceAttachment>

    fun hasAttachment(attachmentPoint: AttachmentPoint) = getAttachment(attachmentPoint) != null

    fun getAttachment(attachmentPoint: AttachmentPoint): SurfaceAttachment?
    {
        attachments.forEachFast { if (it.attachmentPoint == attachmentPoint) return it }
        return null
    }
}

class SurfaceConfigInternal(
    override val name: String,
    override var width: Int,
    override var height: Int,
    override var zOrder: Int,
    override var isVisible: Boolean,
    override var drawPostEffects: Boolean,
    override var drawWireframe: Boolean,
    override var clearColor: Color?,
    override var blendFunction: BlendFunction,
    outputSpec: SurfaceOutputSpec,
) : SurfaceConfig {

    override var attachments     = outputSpec.attachments.toList(); internal set
    override var resolutionScale = outputSpec.resolutionScale; internal set
    override var multisampling   = outputSpec.multisampling; internal set
    override var sizeFunction    = outputSpec.sizeFunction; internal set

    val hasDepthAttachment get() = attachments.anyMatches { it.attachmentPoint.isDepth }

    var currentDrawColor = 0f
    var currentDepth     = 0f
    var hasDepthPrepass  = false

    init { setDrawColor(1f, 1f, 1f, 1f) }

    fun increaseDepth()
    {
        currentDepth += DEPTH_INC
    }

    fun resetDepth(value: Float)
    {
        currentDepth = value
        hasDepthPrepass = false
    }

    fun setDrawColor(r: Float, g: Float, b: Float, a: Float)
    {
        currentDrawColor = intBitsToFloat(((r * 255).toInt() shl 24) or ((g * 255).toInt() shl 16) or ((b * 255).toInt() shl 8) or (a * 255).toInt())
    }

    companion object
    {
        private const val DEPTH_INC = 0.000001f
    }
}