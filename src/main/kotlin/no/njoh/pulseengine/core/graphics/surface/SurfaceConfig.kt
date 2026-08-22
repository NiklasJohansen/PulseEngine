package no.njoh.pulseengine.core.graphics.surface

import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint
import no.njoh.pulseengine.core.graphics.gpu.texture.BlendFunction
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling
import no.njoh.pulseengine.core.shared.primitives.PackedSize
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import java.lang.Float.intBitsToFloat

typealias SurfaceSizeFunction = (windowWidth: Int, windowHeight: Int) -> PackedSize

interface SurfaceConfig
{
    val name: String
    val zOrder: Int
    val width: Int
    val height: Int
    val sizeFunction: SurfaceSizeFunction
    val renderWidth: Int
    val renderHeight: Int
    val renderScale: Float
    val isVisible: Boolean
    var drawPostEffects: Boolean
    var drawWireframe: Boolean
    val clearColor: Color?
    val blendFunction: BlendFunction
    val multisampling: Multisampling
    val attachments: List<SurfaceAttachment>

    fun hasAttachment(attachmentPoint: AttachmentPoint) = getAttachment(attachmentPoint) != null

    fun getAttachment(attachmentPoint: AttachmentPoint) = 
        attachments.firstOrNullFast { it.attachmentPoint == attachmentPoint }
}

class SurfaceConfigInternal(
    override val name: String,
    override var zOrder: Int,
    override var width: Int,
    override var height: Int,
    override val sizeFunction: SurfaceSizeFunction,
    override var isVisible: Boolean,
    override var drawPostEffects: Boolean,
    override var drawWireframe: Boolean,
    override var clearColor: Color?,
    override var blendFunction: BlendFunction,
    outputSpec: SurfaceOutputSpec,
) : SurfaceConfig {

    override var attachments   = outputSpec.attachments.toList(); internal set
    override var renderScale   = outputSpec.renderScale;          internal set
    override var multisampling = outputSpec.multisampling;        internal set
    override var renderWidth   = width;                           internal set
    override var renderHeight  = height;                          internal set

    val hasDepthAttachment get() = attachments.anyMatches { it.attachmentPoint.isDepth }

    var currentDrawColor = 0f
    var currentDepth     = 0f
    var hasDepthPrepass  = false

    private var renderSizeFunction = outputSpec.renderSizeFunction

    init { setDrawColor(1f, 1f, 1f, 1f) }

    fun updateSize(windowWidth: Int, windowHeight: Int)
    {
        val size = sizeFunction(windowWidth, windowHeight)
        require(size.width > 0 && size.height > 0) { "Surface size must be positive" }
        width = size.width
        height = size.height
        updateRenderSize()
    }

    fun updateRenderSize()
    {
        val size = renderSizeFunction(width, height, renderScale)
        require(size.width > 0 && size.height > 0) { "Surface render size must be positive" }
        renderWidth = size.width
        renderHeight = size.height
    }

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